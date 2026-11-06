"""Testes do plano complementar (H3-16): foco exclusivo e casa/peso corporal.

Rodam contra o FastAPI com TestClient e banco SQLite temporário. Sem
SENSENOVA_API_KEY definido (conftest), a geração cai no fallback local —
que é exatamente o caminho que precisa respeitar foco e equipamento.
"""

import os
import tempfile

from fastapi.testclient import TestClient

from app import database as db
from app import main
from app.plan_builder import enforce_complementary_plan


def setup_test_db():
    fd, path = tempfile.mkstemp(suffix=".db")
    os.close(fd)
    db.DB_PATH = path
    db.init_db()
    return path


def _client():
    setup_test_db()
    return TestClient(main.app)


def _complementary_payload(**overrides):
    payload = {
        "deviceId": "d1",
        "goal": "hypertrophy",
        "environment": "bodyweight",
        "daysPerWeek": 2,
        "sessionDurationMinutes": 30,
        "availableEquipment": ["body only"],
        "focusMuscles": ["abs"],
        "modalities": [
            {
                "type": "strength",
                "sessionsPerWeek": 2,
                "sessionDurationMinutes": 30,
                "availableEquipment": ["body only"],
            }
        ],
        "reviewAfterWeeks": 4,
    }
    payload.update(overrides)
    return payload


def _strength_days(workout):
    return [d for d in workout["days"] if d.get("modality", "strength") == "strength"]


# ---------------- Foco exclusivo no fallback ----------------


def test_foco_abs_exclusivo_no_fallback():
    """Todo exercício do plano tem abdômen como músculo primário."""
    client = _client()
    r = client.post("/v1/generate-workout", json=_complementary_payload())
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    assert workout["name"].startswith("Complementar"), workout["name"]
    days = _strength_days(workout)
    assert len(days) == 2
    for day in days:
        assert day["modality"] == "strength"
        for ex in day["exercises"]:
            assert ex["metricType"] == "reps_weight"
            assert "abdominals" in [m.lower() for m in ex["primaryMuscles"]], ex


def test_bodyweight_so_peso_corporal():
    """Com environment=bodyweight, nenhum exercício usa equipamento de carga."""
    client = _client()
    r = client.post("/v1/generate-workout", json=_complementary_payload())
    assert r.status_code == 200, r.text
    for day in _strength_days(r.json()["workout"]):
        for ex in day["exercises"]:
            assert ex["equipment"].strip().lower() in {"body only", "none"}, ex


def test_home_respeita_available_equipment():
    """Com environment=home, o pool fica restrito ao informado + peso corporal."""
    payload = _complementary_payload(
        environment="home",
        availableEquipment=["dumbbell", "bands"],
        focusMuscles=["chest"],
    )
    payload["modalities"][0]["availableEquipment"] = ["dumbbell", "bands"]
    client = _client()
    r = client.post("/v1/generate-workout", json=payload)
    assert r.status_code == 200, r.text
    allowed = {"dumbbell", "bands", "body only", "none"}
    for day in _strength_days(r.json()["workout"]):
        for ex in day["exercises"]:
            assert ex["equipment"].strip().lower() in allowed, ex
            assert "chest" in [m.lower() for m in ex["primaryMuscles"]], ex


def test_pool_vazio_422():
    """Foco sem candidato suficiente no ambiente ⇒ 422, nunca plano fora do foco.

    `calves` tem apenas 1 exercício sem equipamento no catálogo (mínimo é 3):
    com environment=bodyweight o pool efetivo é insuficiente e o erro é
    explícito, em vez de um plano cheio de exercício de academia.
    """
    client = _client()
    r = client.post(
        "/v1/generate-workout",
        json=_complementary_payload(focusMuscles=["calves"]),
    )
    assert r.status_code == 422, r.text
    detail = r.json()["detail"]
    detail_text = detail if isinstance(detail, str) else str(detail)
    assert "foco" in detail_text.lower()


# ---------------- Duração e dias ----------------


def test_duracao_15_tres_exercicios():
    """15 min + 2 dias ⇒ 3 exercícios por sessão, sem 422."""
    payload = _complementary_payload(
        sessionDurationMinutes=15,
        modalities=[
            {
                "type": "strength",
                "sessionsPerWeek": 2,
                "sessionDurationMinutes": 15,
                "availableEquipment": ["body only"],
            }
        ],
    )
    client = _client()
    r = client.post("/v1/generate-workout", json=payload)
    assert r.status_code == 200, r.text
    days = _strength_days(r.json()["workout"])
    assert len(days) == 2
    for day in days:
        assert len(day["exercises"]) == 3, day["name"]


def test_um_dia_por_semana():
    """daysPerWeek=1 é aceito apenas com foco exclusivo."""
    payload = _complementary_payload(
        daysPerWeek=1,
        modalities=[
            {
                "type": "strength",
                "sessionsPerWeek": 1,
                "sessionDurationMinutes": 30,
                "availableEquipment": ["body only"],
            }
        ],
    )
    client = _client()
    r = client.post("/v1/generate-workout", json=payload)
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    assert workout["daysPerWeek"] == 1
    assert len(_strength_days(workout)) == 1


# ---------------- Validações 422 ----------------


def test_foco_mais_run_422():
    """Plano complementar é só de força: run/mobility junto do foco ⇒ 422."""
    payload = _complementary_payload(
        modalities=[
            {"type": "strength", "sessionsPerWeek": 2, "sessionDurationMinutes": 30},
            {"type": "run", "sessionsPerWeek": 1, "sessionDurationMinutes": 30},
        ]
    )
    client = _client()
    r = client.post("/v1/generate-workout", json=payload)
    assert r.status_code == 422, r.text


def test_grupo_invalido_422():
    """Grupo fora de MUSCLE_ALIASES ⇒ 422 citando o valor inválido."""
    client = _client()
    r = client.post(
        "/v1/generate-workout",
        json=_complementary_payload(focusMuscles=["nao-existe"]),
    )
    assert r.status_code == 422, r.text
    assert "nao-existe" in r.text


def test_lista_vazia_422():
    """focusMuscles=[] é entrada inválida: o app omite o campo quando não há foco."""
    client = _client()
    r = client.post(
        "/v1/generate-workout",
        json=_complementary_payload(focusMuscles=[]),
    )
    assert r.status_code == 422, r.text


# ---------------- Normalização pós-IA ----------------


def _abs_request(**overrides):
    data = {
        "deviceId": "d1",
        "environment": "bodyweight",
        "daysPerWeek": 1,
        "sessionDurationMinutes": 30,
        "availableEquipment": ["body only"],
        "focusMuscles": ["abs"],
        "goal": "hypertrophy",
        "currentLevel": "intermediate",
    }
    data.update(overrides)
    return main.GenerateRequest(**data)


def _exercise(exercise_id, muscles, equipment, order=0):
    return {
        "exerciseId": exercise_id,
        "exerciseOrder": order,
        "sets": 3,
        "targetReps": "8-12",
        "targetRpe": 8.0,
        "restSeconds": 90,
        "notes": "",
        "isSupersetWithPrevious": False,
        "metricType": "reps_weight",
        "primaryMuscles": muscles,
        "equipment": equipment,
    }


def _plan_with_days(days):
    return {
        "name": "Plano rebelde da IA",
        "description": "",
        "goal": "hypertrophy",
        "daysPerWeek": len(days),
        "days": days,
    }


def test_normalizacao_remove_exercicio_fora_do_foco():
    """IA rebelde devolve peito num plano de abdômen ⇒ removido na normalização."""
    plan = _plan_with_days(
        [
            {
                "name": "Treino A",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "isRestDay": False,
                "exercises": [
                    _exercise("Sit_Up", ["abdominals"], "body only", 0),
                    _exercise("Barbell_Bench_Press", ["chest"], "barbell", 1),
                    _exercise("Crunch", ["abdominals"], "body only", 2),
                    _exercise("Leg_Raise", ["abdominals"], "body only", 3),
                ],
            }
        ]
    )
    result = enforce_complementary_plan(plan, _abs_request(), main.EXERCISE_CATALOG)
    assert len(result["days"]) == 1
    ids = [ex["exerciseId"] for ex in result["days"][0]["exercises"]]
    assert "Barbell_Bench_Press" not in ids
    assert len(ids) == 3
    assert result["daysPerWeek"] == 1


def test_normalizacao_remove_equipamento_proibido():
    """IA devolve exercício de barra num plano de peso corporal ⇒ removido."""
    plan = _plan_with_days(
        [
            {
                "name": "Treino A",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "isRestDay": False,
                "exercises": [
                    _exercise("Sit_Up", ["abdominals"], "body only", 0),
                    _exercise("Crunch", ["abdominals"], "body only", 1),
                    _exercise("Leg_Raise", ["abdominals"], "body only", 2),
                    _exercise("Barbell_Squat", ["quadriceps"], "barbell", 3),
                ],
            }
        ]
    )
    result = enforce_complementary_plan(plan, _abs_request(), main.EXERCISE_CATALOG)
    ids = [ex["exerciseId"] for ex in result["days"][0]["exercises"]]
    assert "Barbell_Squat" not in ids
    for ex in result["days"][0]["exercises"]:
        assert ex["equipment"].strip().lower() in {"body only", "none"}


def test_sessao_insuficiente_reconstruida():
    """Sessão que sobra com < 3 exercícios é reconstruída pelo fallback local."""
    plan = _plan_with_days(
        [
            {
                "name": "Treino A",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "isRestDay": False,
                "exercises": [
                    _exercise("Sit_Up", ["abdominals"], "body only", 0),
                    _exercise("Barbell_Bench_Press", ["chest"], "barbell", 1),
                ],
            }
        ]
    )
    result = enforce_complementary_plan(plan, _abs_request(), main.EXERCISE_CATALOG)
    day = result["days"][0]
    assert len(day["exercises"]) >= 3
    for ex in day["exercises"]:
        assert "abdominals" in [m.lower() for m in ex["primaryMuscles"]]
        assert ex["equipment"].strip().lower() in {"body only", "none"}
    assert result["daysPerWeek"] == 1


# ---------------- Regressão do plano principal ----------------


def test_sem_foco_comportamento_inalterado():
    """Sem focusMuscles, o híbrido segue idêntico ao pré-H3-16."""
    payload = {
        "deviceId": "d1",
        "modalities": [
            {"type": "strength", "sessionsPerWeek": 3, "goal": "hypertrophy", "level": "intermediate"},
            {"type": "run", "sessionsPerWeek": 2, "goal": "endurance", "level": "beginner"},
            {"type": "mobility", "sessionsPerWeek": 2, "focus": "back"},
        ],
        "sessionDurationMinutes": 60,
        "reviewAfterWeeks": 4,
    }
    client = _client()
    r = client.post("/v1/generate-workout", json=payload)
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    assert workout["name"] == "Semana híbrida"
    modalities = {day["modality"] for day in workout["days"]}
    assert {"strength", "run", "mobility"} <= modalities
    strength_days = _strength_days(workout)
    assert len(strength_days) == 3
    for day in strength_days:
        assert 4 <= len(day["exercises"]) <= 8, day["name"]
    # Rotação de splits segue de pé (H3-13: título varia com o split).
    names = " | ".join(day["name"] for day in strength_days)
    for label in ("Empurrar", "Puxar", "Pernas e Core"):
        assert label in names, names
