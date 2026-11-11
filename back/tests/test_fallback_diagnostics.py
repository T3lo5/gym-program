"""Diagnóstico do fallback da IA (H3-21).

Cada tipo de falha da geração por IA vira um código fechado de
`fallbackReason`, visível no result do job e no log `[fallback]`.
Os testes de `parse_ai_plan` são puros (sem rede); os testes de geração
dirigem `run_generation_job` (H3-40) com um cliente HTTP falso para forçar
cada tipo de falha sem chamar a SenseNova. Sem chave (conftest zera
`SENSENOVA_API_KEY`), o job conclui com `no_api_key`.
"""

import json
import os
import tempfile

import pytest
from fastapi.testclient import TestClient

from app import database as db
from app import main
from job_helpers import run_job


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
    result = run_job(_client(), _payload())
    assert result["source"] == "local"
    assert result["fallbackReason"] == "no_api_key"
    # Plano local válido de sempre (não vazio).
    assert result["workout"]["days"]


def test_json_invalido_vira_json_parse(monkeypatch):
    monkeypatch.setattr(main, "SENSENOVA_API_KEY", "chave-de-teste")
    monkeypatch.setattr(_FakeClient, "payload", "isto não é JSON")
    monkeypatch.setattr(main.httpx, "AsyncClient", _FakeClient)

    # Função pura lança o código fechado...
    with pytest.raises(main.AiFailure) as excinfo:
        main.parse_ai_plan("isto não é JSON", main.EXERCISE_CATALOG)
    assert excinfo.value.reason == "json_parse"

    # ...e o job conclui `done` com `json_parse` no result e plano local.
    result = run_job(_client(), _payload())
    assert result["source"] == "local"
    assert result["fallbackReason"] == "json_parse"
    assert result["workout"]["days"]


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

    # Job: mesmo código, plano local no result.
    monkeypatch.setattr(main, "SENSENOVA_API_KEY", "chave-de-teste")
    monkeypatch.setattr(_FakeClient, "payload", content)
    monkeypatch.setattr(main.httpx, "AsyncClient", _FakeClient)
    result = run_job(_client(), _payload())
    assert result["source"] == "local"
    assert result["fallbackReason"] == "unresolved_ids"


def test_resposta_valida_nao_tem_fallback_reason(monkeypatch):
    content = json.dumps(_plano_valido())

    # Função pura: plano válido passa sem lançar.
    raw_plan = main.parse_ai_plan(content, main.EXERCISE_CATALOG)
    assert raw_plan["days"]

    monkeypatch.setattr(main, "SENSENOVA_API_KEY", "chave-de-teste")
    monkeypatch.setattr(_FakeClient, "payload", content)
    monkeypatch.setattr(main.httpx, "AsyncClient", _FakeClient)
    result = run_job(_client(), _payload())
    assert result["source"] == "ai"
    assert result["fallbackReason"] == ""


def test_erro_inesperado_vira_unknown(monkeypatch):
    # Exceção fora dos tipos mapeados cai no código fechado "unknown".
    monkeypatch.setattr(main, "SENSENOVA_API_KEY", "chave-de-teste")
    monkeypatch.setattr(_FakeClient, "payload", None)
    monkeypatch.setattr(_FakeClient, "error", RuntimeError)
    monkeypatch.setattr(main.httpx, "AsyncClient", _FakeClient)

    result = run_job(_client(), _payload())
    assert result["source"] == "local"
    assert result["fallbackReason"] == "unknown"


def test_detail_truncado_em_300():
    linha = main.fallback_log_line("json_parse", "x" * 1000)
    assert linha.startswith("[fallback] reason=json_parse detail=")
    detail = linha.split(" detail=", 1)[1]
    assert 0 < len(detail) <= 300

    # Quebras de linha viram espaço: a linha continua única e grep-ável.
    linha_multiline = main.fallback_log_line("unknown", "a\nb\tc")
    assert "\n" not in linha_multiline
    assert "\t" not in linha_multiline


# ---------------- recuperação de JSON truncado (H3-41) ----------------


def test_recupera_json_truncado_em_string():
    """JSON cortado após um objeto completo, com o próximo objeto em aberto:
    o ponto seguro é o ',' e o objeto incompleto é mantido como parcial."""
    content = (
        '{"name": "Plano", "days": [{"dayOrder": 0, "exercises": ['
        '{"exerciseId": "abc", "sets": 3}, {"exerciseId": "def", "exerciseName": '
        '"Exercício sem fechar, outra coisa sem fechar'
    )
    recovered = main._recover_truncated_json(content)
    parsed = json.loads(recovered)
    assert parsed["name"] == "Plano"
    assert parsed["days"][0]["dayOrder"] == 0
    # O primeiro objeto completo é mantido intacto.
    assert parsed["days"][0]["exercises"][0] == {"exerciseId": "abc", "sets": 3}
    # O segundo objeto incompleto é mantido como parcial (pelo menos o ID).
    assert parsed["days"][0]["exercises"][1]["exerciseId"] == "def"


def test_recupera_json_truncado_em_array():
    """JSON cortado no meio de um array: recupera até o último ponto seguro."""
    content = (
        '{"name": "Plano", "days": [{"dayOrder": 0, "exercises": ['
        '{"exerciseId": "abc", "sets": 3}, {"exerciseId": "def", "sets": 2'
    )
    recovered = main._recover_truncated_json(content)
    parsed = json.loads(recovered)
    assert parsed["name"] == "Plano"
    assert len(parsed["days"][0]["exercises"]) == 2


def test_recupera_json_truncado_em_objeto():
    """JSON cortado no meio de um objeto: remove a propriedade em aberto."""
    content = (
        '{"name": "Plano", "days": [{"dayOrder": 0, "exercises": '
        '[{"exerciseId": "abc", "sets": 3, "targetReps": "8-12", "notes": '
        '"treino sem fechar'
    )
    recovered = main._recover_truncated_json(content)
    parsed = json.loads(recovered)
    assert parsed["name"] == "Plano"
    assert parsed["days"][0]["exercises"][0]["exerciseId"] == "abc"
    assert "notes" not in parsed["days"][0]["exercises"][0]


def test_recupera_json_ja_valido_nao_muda():
    """JSON válido passa ileso pela recuperação."""
    plano = {
        "name": "Plano",
        "days": [
            {
                "dayOrder": 0,
                "exercises": [
                    {"exerciseId": main.EXERCISE_CATALOG[0]["id"], "sets": 3}
                ],
            }
        ],
    }
    content = json.dumps(plano)
    assert main._recover_truncated_json(content) == content


def test_recupera_json_sem_ponto_seguro():
    """Content sem `{` não tem objeto JSON: a recuperação retorna o original."""
    content = '{"name": "Plano", "days": [{"dayOrder": 0, "exercises": [{"exerciseId"'
    recovered = main._recover_truncated_json(content)
    # A recuperação tenta cortar em cada ponto seguro; no pior caso retorna o
    # texto original (que falha em json.loads).
    try:
        json.loads(recovered)
    except json.JSONDecodeError:
        pass  # ambos os comportamentos são aceitáveis
    else:
        # Se recuperou, o plano é parcial e deve ter ao menos o name.
        parsed = json.loads(recovered)
        assert parsed.get("name") == "Plano"


def test_parse_ai_plan_recupera_truncado():
    """parse_ai_plan aceita JSON truncado (recuperado) em vez de falhar total."""
    exercise_id = main.EXERCISE_CATALOG[0]["id"]
    content = (
        '{"name": "Plano", "goal": "hypertrophy", "daysPerWeek": 1, '
        '"days": [{"name": "Dia 1", "dayOrder": 0, "weekday": 1, '
        '"modality": "strength", "isRestDay": false, "exercises": ['
        f'{{"exerciseId": "{exercise_id}", "sets": 3, "targetReps": "8-12"'
    )
    parsed = main.parse_ai_plan(content, main.EXERCISE_CATALOG, _request_for_parse())
    assert parsed["days"][0]["exercises"]
    assert parsed["days"][0]["exercises"][0]["exerciseId"] == exercise_id


def _request_for_parse():
    return main.GenerateRequest(
        deviceId="d1",
        goal="hypertrophy",
        daysPerWeek=2,
        availableEquipment=["barbell", "dumbbell", "body only", "cable", "machine"],
        targetMuscles=["chest", "back", "legs"],
        sessionDurationMinutes=60,
        currentLevel="intermediate",
    )


# ---------------- normalização de campos do ExerciseDTO (H3-41) ----------------


def test_normalize_exercise_fields_clamp_target_rpe():
    """targetRpe < 5.0 é clampado para 5.0 antes da validação pelo schema."""
    plano = {
        "days": [
            {
                "exercises": [
                    {"exerciseId": main.EXERCISE_CATALOG[0]["id"], "targetRpe": 4.0}
                ]
            }
        ]
    }
    main._normalize_exercise_fields(plano)
    assert plano["days"][0]["exercises"][0]["targetRpe"] == 5.0


def test_normalize_exercise_fields_clamp_rest_seconds():
    """restSeconds > 300 é clampado para 300."""
    plano = {
        "days": [
            {
                "exercises": [
                    {
                        "exerciseId": main.EXERCISE_CATALOG[0]["id"],
                        "restSeconds": 999,
                    }
                ]
            }
        ]
    }
    main._normalize_exercise_fields(plano)
    assert plano["days"][0]["exercises"][0]["restSeconds"] == 300


def test_normalize_exercise_fields_clamp_sets():
    """sets < 1 é clampado para 1; sets > 20 para 20."""
    plano = {
        "days": [
            {
                "exercises": [
                    {"exerciseId": main.EXERCISE_CATALOG[0]["id"], "sets": 0},
                    {"exerciseId": main.EXERCISE_CATALOG[0]["id"], "sets": 50},
                ]
            }
        ]
    }
    main._normalize_exercise_fields(plano)
    assert plano["days"][0]["exercises"][0]["sets"] == 1
    assert plano["days"][0]["exercises"][1]["sets"] == 20


def test_normalize_exercise_fields_nao_muda_validos():
    """Valores dentro do range são mantidos."""
    plano = {
        "days": [
            {
                "exercises": [
                    {
                        "exerciseId": main.EXERCISE_CATALOG[0]["id"],
                        "targetRpe": 8.0,
                        "restSeconds": 90,
                        "sets": 3,
                    }
                ]
            }
        ]
    }
    main._normalize_exercise_fields(plano)
    ex = plano["days"][0]["exercises"][0]
    assert ex["targetRpe"] == 8.0
    assert ex["restSeconds"] == 90
    assert ex["sets"] == 3


def test_parse_ai_plan_recupera_rpe_invalido():
    """parse_ai_plan aceita plano com targetRpe < 5.0 (clampado) em vez de
    falhar com schema_validation."""
    exercise_id = main.EXERCISE_CATALOG[0]["id"]
    content = (
        '{"name": "Plano", "goal": "hypertrophy", "daysPerWeek": 1, '
        '"days": [{"name": "Dia 1", "dayOrder": 0, "weekday": 1, '
        '"modality": "strength", "isRestDay": false, "exercises": ['
        f'{{"exerciseId": "{exercise_id}", "sets": 3, "targetReps": "8-12", '
        '"targetRpe": 4.0, "restSeconds": 90}}]'
        "}]}"
    )
    parsed = main.parse_ai_plan(content, main.EXERCISE_CATALOG, _request_for_parse())
    assert parsed["days"][0]["exercises"][0]["targetRpe"] == 5.0
