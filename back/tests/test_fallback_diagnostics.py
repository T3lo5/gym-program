"""Diagnóstico do fallback da IA (H3-21).

Cada tipo de falha da geração por IA vira um código fechado de
`fallbackReason`, visível na resposta do endpoint e no log `[fallback]`.
Os testes de `parse_ai_plan` são puros (sem rede); os testes de endpoint
usam um cliente HTTP falso para forçar cada tipo de falha sem chamar a
SenseNova. Sem chave (conftest zera `SENSENOVA_API_KEY`), o endpoint
responde `no_api_key`.
"""

import json
import os
import tempfile

import pytest
from fastapi.testclient import TestClient

from app import database as db
from app import main


def setup_test_db():
    fd, path = tempfile.mkstemp(suffix=".db")
    os.close(fd)
    db.DB_PATH = path
    db.init_db()
    return path


def _client():
    setup_test_db()
    return TestClient(main.app)


def _payload(**overrides):
    payload = {
        "deviceId": "d1",
        "goal": "hypertrophy",
        "daysPerWeek": 2,
        "availableEquipment": ["barbell", "dumbbell"],
        "targetMuscles": ["chest", "back"],
        "sessionDurationMinutes": 60,
    }
    payload.update(overrides)
    return payload


class _FakeResponse:
    def __init__(self, content, status_code=200, text=""):
        self.status_code = status_code
        self.text = text
        self._content = content

    def json(self):
        return {"choices": [{"message": {"content": self._content}}]}


class _FakeClient:
    """Substitui `httpx.AsyncClient` no teste; devolve o `payload` da classe."""

    payload = None
    error = None

    def __init__(self, *args, **kwargs):
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    async def post(self, *args, **kwargs):
        error = type(self).error
        if error is not None:
            raise error("falha forçada no teste")
        return _FakeResponse(type(self).payload)


def _plano_valido():
    exercise_id = main.EXERCISE_CATALOG[0]["id"]
    return {
        "name": "Plano da IA",
        "description": "",
        "goal": "hypertrophy",
        "daysPerWeek": 1,
        "days": [
            {
                "name": "Dia 1",
                "dayOrder": 0,
                "exercises": [
                    {
                        "exerciseId": exercise_id,
                        "exerciseOrder": 0,
                        "sets": 3,
                        "targetReps": "8-12",
                    }
                ],
            }
        ],
    }


def test_sem_chave_responde_no_api_key(monkeypatch):
    monkeypatch.setattr(main, "SENSENOVA_API_KEY", "")
    response = _client().post("/v1/generate-workout", json=_payload())

    assert response.status_code == 200
    body = response.json()
    assert body["source"] == "local"
    assert body["fallbackReason"] == "no_api_key"
    # Plano local válido de sempre (não vazio).
    assert body["workout"]["days"]


def test_json_invalido_vira_json_parse(monkeypatch):
    monkeypatch.setattr(main, "SENSENOVA_API_KEY", "chave-de-teste")
    monkeypatch.setattr(_FakeClient, "payload", "isto não é JSON")
    monkeypatch.setattr(main.httpx, "AsyncClient", _FakeClient)

    # Função pura lança o código fechado...
    with pytest.raises(main.AiFailure) as excinfo:
        main.parse_ai_plan("isto não é JSON", main.EXERCISE_CATALOG)
    assert excinfo.value.reason == "json_parse"

    # ...e o endpoint responde `json_parse` com plano local no lugar.
    response = _client().post("/v1/generate-workout", json=_payload())
    assert response.status_code == 200
    body = response.json()
    assert body["source"] == "local"
    assert body["fallbackReason"] == "json_parse"
    assert body["workout"]["days"]


def test_ids_inexistentes_vira_unresolved_ids(monkeypatch):
    plano = _plano_valido()
    fake_ids = [f"ZZZ_Fake_{i}" for i in range(7)]
    plano["days"][0]["exercises"] = [
        {"exerciseId": fake_id, "exerciseOrder": 0, "sets": 3, "targetReps": "8-12"}
        for fake_id in fake_ids
    ]
    content = json.dumps(plano)

    with pytest.raises(main.AiFailure) as excinfo:
        main.parse_ai_plan(content, main.EXERCISE_CATALOG)
    assert excinfo.value.reason == "unresolved_ids"
    # O detail lista até 5 IDs.
    for fake_id in fake_ids[:5]:
        assert fake_id in excinfo.value.detail
    assert fake_ids[5] not in excinfo.value.detail

    # Endpoint: mesmo código, plano local no lugar.
    monkeypatch.setattr(main, "SENSENOVA_API_KEY", "chave-de-teste")
    monkeypatch.setattr(_FakeClient, "payload", content)
    monkeypatch.setattr(main.httpx, "AsyncClient", _FakeClient)
    response = _client().post("/v1/generate-workout", json=_payload())
    assert response.status_code == 200
    body = response.json()
    assert body["source"] == "local"
    assert body["fallbackReason"] == "unresolved_ids"


def test_resposta_valida_nao_tem_fallback_reason(monkeypatch):
    content = json.dumps(_plano_valido())

    # Função pura: plano válido passa sem lançar.
    raw_plan = main.parse_ai_plan(content, main.EXERCISE_CATALOG)
    assert raw_plan["days"]

    monkeypatch.setattr(main, "SENSENOVA_API_KEY", "chave-de-teste")
    monkeypatch.setattr(_FakeClient, "payload", content)
    monkeypatch.setattr(main.httpx, "AsyncClient", _FakeClient)
    response = _client().post("/v1/generate-workout", json=_payload())
    assert response.status_code == 200
    body = response.json()
    assert body["source"] == "ai"
    assert body["fallbackReason"] == ""


def test_erro_inesperado_vira_unknown(monkeypatch):
    # Exceção fora dos tipos mapeados cai no código fechado "unknown".
    monkeypatch.setattr(main, "SENSENOVA_API_KEY", "chave-de-teste")
    monkeypatch.setattr(_FakeClient, "payload", None)
    monkeypatch.setattr(_FakeClient, "error", RuntimeError)
    monkeypatch.setattr(main.httpx, "AsyncClient", _FakeClient)

    response = _client().post("/v1/generate-workout", json=_payload())
    assert response.status_code == 200
    body = response.json()
    assert body["source"] == "local"
    assert body["fallbackReason"] == "unknown"


def test_detail_truncado_em_300():
    linha = main.fallback_log_line("json_parse", "x" * 1000)
    assert linha.startswith("[fallback] reason=json_parse detail=")
    detail = linha.split(" detail=", 1)[1]
    assert 0 < len(detail) <= 300

    # Quebras de linha viram espaço: a linha continua única e grep-ável.
    linha_multiline = main.fallback_log_line("unknown", "a\nb\tc")
    assert "\n" not in linha_multiline
    assert "\t" not in linha_multiline
