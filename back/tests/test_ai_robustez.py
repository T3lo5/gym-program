"""Testes de robustez da geração por IA (H3-22).

Funções puras: parse tolerante, salvamento parcial, resumo por request,
check ID↔nome. Testes de endpoint usam cliente HTTP falso (sem SenseNova).
"""

import json
import os
import tempfile

import pytest
from fastapi.testclient import TestClient

from app import database as db
from app import main
from app.plan_builder import catalog_summary_for_request, id_name_mismatches, resolve_plan_ids


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
        "availableEquipment": ["barbell", "dumbbell", "body only"],
        "targetMuscles": ["chest", "back"],
        "sessionDurationMinutes": 60,
        "currentLevel": "intermediate",
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


class _SequenceClient:
    """httpx.AsyncClient falso com sequência de respostas/erros por chamada."""

    steps = []

    def __init__(self, *args, **kwargs):
        type(self).calls = getattr(type(self), "calls", 0)

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    async def post(self, *args, **kwargs):
        idx = min(type(self).calls, len(type(self).steps) - 1)
        type(self).calls += 1
        step = type(self).steps[idx]
        if isinstance(step, Exception):
            raise step
        if isinstance(step, tuple):
            content, status, text = step
            return _FakeResponse(content, status_code=status, text=text)
        return _FakeResponse(step)


def _strength_plan(exercise_ids, target_muscles=None):
    target_muscles = target_muscles or ["chest", "back"]
    return {
        "name": "Plano da IA",
        "description": "",
        "goal": "hypertrophy",
        "daysPerWeek": 1,
        "days": [
            {
                "name": "Dia 1",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "isRestDay": False,
                "exercises": [
                    {
                        "exerciseId": ex_id,
                        "exerciseOrder": i,
                        "sets": 3,
                        "targetReps": "8-12",
                        "targetRpe": 8.0,
                        "restSeconds": 90,
                        "notes": "",
                        "isSupersetWithPrevious": False,
                        "metricType": "reps_weight",
                    }
                    for i, ex_id in enumerate(exercise_ids)
                ],
            }
        ],
    }


def _valid_ids(n=6):
    # IDs reais do catálogo, filtrados por força, distintos.
    ids = []
    seen = set()
    for ex in main.EXERCISE_CATALOG:
        ex_id = ex.get("id")
        if not ex_id or ex_id in seen:
            continue
        cat = str(ex.get("category", "")).lower()
        if cat in {"stretching", "cardio"}:
            continue
        ids.append(ex_id)
        seen.add(ex_id)
        if len(ids) >= n:
            break
    return ids


def _request_for_parse(target_muscles=None, equipment=None):
    return main.GenerateRequest(
        deviceId="d1",
        goal="hypertrophy",
        daysPerWeek=2,
        availableEquipment=equipment or ["barbell", "dumbbell", "body only", "cable", "machine"],
        targetMuscles=target_muscles or ["chest", "back", "legs"],
        sessionDurationMinutes=60,
        currentLevel="intermediate",
    )


# ---------------- parse tolerante ----------------


def test_parse_remove_cercas_de_codigo():
    plano = _strength_plan(_valid_ids(4))
    content = "```json\n" + json.dumps(plano) + "\n```"
    parsed = main.parse_ai_plan(content, main.EXERCISE_CATALOG, _request_for_parse())
    assert parsed["days"][0]["exercises"]


def test_parse_extrai_objeto_balanceado():
    plano = _strength_plan(_valid_ids(4))
    content = "Aqui está o plano:\n" + json.dumps(plano) + "\nObrigado."
    parsed = main.parse_ai_plan(content, main.EXERCISE_CATALOG, _request_for_parse())
    assert parsed["name"] == "Plano da IA"
    assert parsed["days"]


# ---------------- salvamento parcial ----------------


def test_id_invalido_e_removido_e_sessao_mantida():
    ids = _valid_ids(6)
    ids[2] = "ZZZ_Fake_Exercise"
    plano = _strength_plan(ids)
    parsed = main.parse_ai_plan(json.dumps(plano), main.EXERCISE_CATALOG, _request_for_parse())
    got_ids = [ex["exerciseId"] for ex in parsed["days"][0]["exercises"]]
    assert "ZZZ_Fake_Exercise" not in got_ids
    assert len(got_ids) == 5


def test_sessao_com_menos_de_tres_e_reconstruida():
    ids = _valid_ids(6)
    # 4 inválidos => sobram 2 => reconstrução local completa a sessão.
    for i in range(4):
        ids[i] = f"ZZZ_Fake_{i}"
    plano = _strength_plan(ids)
    req = _request_for_parse(target_muscles=["chest", "back", "legs"])
    parsed = main.parse_ai_plan(json.dumps(plano), main.EXERCISE_CATALOG, req)
    assert len(parsed["days"][0]["exercises"]) >= 3
    # Todos os exercícios finais existem no catálogo.
    catalog_ids = {ex["id"] for ex in main.EXERCISE_CATALOG}
    for ex in parsed["days"][0]["exercises"]:
        assert ex["exerciseId"] in catalog_ids


def test_todos_invalidos_vira_fallback_total():
    plano = _strength_plan([f"ZZZ_Fake_{i}" for i in range(6)])
    with pytest.raises(main.AiFailure) as excinfo:
        main.parse_ai_plan(json.dumps(plano), main.EXERCISE_CATALOG, _request_for_parse())
    assert excinfo.value.reason == "unresolved_ids"


# ---------------- resumo por request ----------------


def test_resumo_prioriza_foco_e_lista_pool_completo():
    req = main.GenerateRequest(
        targetMuscles=["abs"],
        availableEquipment=["body only", "dumbbell"],
        daysPerWeek=2,
        sessionDurationMinutes=45,
        currentLevel="intermediate",
    )
    summary = catalog_summary_for_request(req, main.EXERCISE_CATALOG)
    lines = [l for l in summary.splitlines() if l.strip()]
    # Primeiro grupo de músculo listado (após header) deve ser o foco expandido.
    first_group = next((l for l in lines[1:] if l.endswith(":")), "")
    assert first_group in {"ABDOMINALS:", "ABS:"}
    # Sem cap fixo por grupo: abdominals tem bem mais que 8 entradas no pool.
    abd_count = sum(1 for l in lines if l.strip().startswith("- ") and "ABDOMINALS" in summary.split(first_group)[0] + first_group)
    # Contagem robusta: linhas entre ABDOMINALS: e próximo cabeçalho.
    in_abd = False
    abd_items = 0
    for l in lines:
        if l.strip() == "ABDOMINALS:":
            in_abd = True
            continue
        if in_abd and l.endswith(":"):
            break
        if in_abd and l.strip().startswith("- "):
            abd_items += 1
    assert abd_items > 8


def test_resumo_nao_lista_equipamento_fora_do_request():
    req = main.GenerateRequest(
        targetMuscles=["chest"],
        availableEquipment=["body only"],
        daysPerWeek=2,
        sessionDurationMinutes=45,
        currentLevel="intermediate",
    )
    summary = catalog_summary_for_request(req, main.EXERCISE_CATALOG)
    # Com body only estrito? No principal, _build_pool só filtra se >=20; mas
    # o resumo expõe o pool filtrado. Barbell aparece em IDs do catálogo, porém
    # como equipamento entre parênteses só deve aparecer body only quando o pool
    # foi filtrado. Checamos que o resumo foi gerado e não contém machine/cable
    # se o pool do request não os inclui — usamos assert fraco e determinístico:
    # nenhum item pode ter equipamento fora de {body only, none} se a filtragem
    # estrita se aplicar; no modo principal não-estrito isso depende do pool.
    # Garantia forte: strict equipment do complementar não vaza.
    comp = main.GenerateRequest(
        environment="bodyweight",
        focusMuscles=["abs"],
        daysPerWeek=2,
        sessionDurationMinutes=30,
        currentLevel="intermediate",
    )
    comp_summary = catalog_summary_for_request(comp, main.EXERCISE_CATALOG)
    for forbidden in ["(barbell", "(dumbbell", "(machine", "(cable", "(kettlebells", "(bands"]:
        assert forbidden not in comp_summary.lower()


# ---------------- retry 429 ----------------


def test_retry_unico_em_429(monkeypatch):
    monkeypatch.setattr(main, "SENSENOVA_API_KEY", "chave-de-teste")
    _SequenceClient.calls = 0
    _SequenceClient.steps = [
        ("", 429, "rate limited"),
        json.dumps(_strength_plan(_valid_ids(4))),
    ]
    monkeypatch.setattr(main.httpx, "AsyncClient", _SequenceClient)

    response = _client().post("/v1/generate-workout", json=_payload())
    assert response.status_code == 200
    body = response.json()
    assert body["source"] == "ai"
    assert body["fallbackReason"] == ""
    assert _SequenceClient.calls == 2


# ---------------- check ID↔nome ----------------


def test_par_id_nome_divergente_e_removido():
    ids = _valid_ids(6)
    plano = _strength_plan(ids)
    catalog_by_id = {ex["id"]: ex for ex in main.EXERCISE_CATALOG}
    # Força divergência clara: nome declarado nada a ver com o oficial.
    target = ids[0]
    plano["days"][0]["exercises"][0]["exerciseId"] = target
    plano["days"][0]["exercises"][0]["exerciseName"] = "Completely Different Exercise XYZ"
    # Garante que o nome oficial não é esse.
    assert catalog_by_id[target]["name"] != "Completely Different Exercise XYZ"

    parsed = main.parse_ai_plan(json.dumps(plano), main.EXERCISE_CATALOG, _request_for_parse())
    got_ids = [ex["exerciseId"] for ex in parsed["days"][0]["exercises"]]
    assert target not in got_ids
    assert len(got_ids) >= 3


def test_nome_aproximado_nao_e_divergencia():
    ids = _valid_ids(4)
    plano = _strength_plan(ids)
    catalog_by_id = {ex["id"]: ex for ex in main.EXERCISE_CATALOG}
    target = ids[0]
    official = catalog_by_id[target]["name"]
    # Nome abreviado/aproximado: usa parte do oficial.
    plano["days"][0]["exercises"][0]["exerciseName"] = official.split()[0] if official.split() else official
    # Se abreviação ficar vazia/igual, usa lowercase do oficial.
    if not plano["days"][0]["exercises"][0]["exerciseName"]:
        plano["days"][0]["exercises"][0]["exerciseName"] = official.lower()

    mismatches = id_name_mismatches(plano, main.EXERCISE_CATALOG)
    mismatch_ids = {m["exerciseId"] for m in mismatches}
    assert target not in mismatch_ids

    parsed = main.parse_ai_plan(json.dumps(plano), main.EXERCISE_CATALOG, _request_for_parse())
    got_ids = [ex["exerciseId"] for ex in parsed["days"][0]["exercises"]]
    assert target in got_ids
