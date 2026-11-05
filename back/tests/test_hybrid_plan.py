"""Testes do plano híbrido (H2-6): geração e revisão determinística.

Rodam contra o FastAPI com TestClient e banco SQLite temporário. Sem
SENSENOVA_API_KEY definido, a geração cai no fallback local híbrido.
"""

import os
import tempfile

from fastapi.testclient import TestClient

from app import database as db
from app import main
from app.plan_builder import review_plan, build_hybrid_plan


def setup_test_db():
    fd, path = tempfile.mkstemp(suffix=".db")
    os.close(fd)
    db.DB_PATH = path
    db.init_db()
    return path


def _client():
    setup_test_db()
    return TestClient(main.app)


def _hybrid_payload():
    return {
        "deviceId": "d1",
        "modalities": [
            {"type": "strength", "sessionsPerWeek": 3, "goal": "hypertrophy", "level": "intermediate"},
            {"type": "run", "sessionsPerWeek": 2, "goal": "endurance", "level": "beginner"},
            {"type": "mobility", "sessionsPerWeek": 2, "focus": "back"},
        ],
        "sessionDurationMinutes": 60,
        "reviewAfterWeeks": 4,
    }


def test_plano_hibrido_distribui_modalidades():
    client = _client()
    r = client.post("/v1/generate-workout", json=_hybrid_payload())
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["source"] == "local"
    workout = data["workout"]
    # 3 + 2 + 2 = 7 sessões distribuídas por weekday (1..7), uma por dia.
    assert len(workout["days"]) == 7
    weekdays = [d["weekday"] for d in workout["days"]]
    assert sorted(weekdays) == [1, 2, 3, 4, 5, 6, 7]
    modalities = [d["modality"] for d in workout["days"]]
    assert modalities.count("strength") == 3
    assert modalities.count("run") == 2
    assert modalities.count("mobility") == 2


def test_permite_duas_sessoes_no_mesmo_dia():
    client = _client()
    payload = _hybrid_payload()
    # 4 strength + 2 run + 2 mobility = 8 slots → round-robin coloca 2 no weekday 1.
    payload["modalities"][0]["sessionsPerWeek"] = 4
    r = client.post("/v1/generate-workout", json=payload)
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    dias_duplos = [d for d in workout["days"] if len(d["exercises"]) > 1]
    assert len(dias_duplos) >= 1


def test_corrida_iniciante_usa_caminhada():
    client = _client()
    payload = _hybrid_payload()
    payload["modalities"] = [
        {"type": "run", "sessionsPerWeek": 1, "level": "beginner"}
    ]
    r = client.post("/v1/generate-workout", json=payload)
    assert r.status_code == 200, r.text
    ex = r.json()["workout"]["days"][0]["exercises"][0]
    assert ex["exerciseId"] == "run_walk"
    # Sem benchmark → pace None (usa RPE).
    assert ex["targetPaceSecondsPerKm"] is None
    assert ex["metricType"] == "distance_duration"


def test_corrida_com_benchmark_calcula_paces():
    client = _client()
    payload = _hybrid_payload()
    payload["modalities"] = [
        {"type": "run", "sessionsPerWeek": 1, "level": "intermediate", "recent5kSeconds": 1800}
    ]
    r = client.post("/v1/generate-workout", json=payload)
    assert r.status_code == 200, r.text
    ex = r.json()["workout"]["days"][0]["exercises"][0]
    # 1800s / 5km = 360s/km; progressão 5% → 342s/km.
    assert ex["targetPaceSecondsPerKm"] == 342


def test_fallback_sem_ia():
    client = _client()
    r = client.post("/v1/generate-workout", json=_hybrid_payload())
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["source"] == "local"
    assert data["workout"]["name"] == "Semana híbrida"
    assert data["workout"]["daysPerWeek"] == 7


def test_revisao_reduz_volume_com_adesao_baixa():
    plan = {
        "name": "P",
        "goal": "general",
        "daysPerWeek": 3,
        "days": [
            {
                "name": "A",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "exercises": [
                    {
                        "exerciseId": "e1",
                        "exerciseOrder": 0,
                        "sets": 4,
                        "targetReps": "8-12",
                        "targetRpe": 8.0,
                        "restSeconds": 90,
                    }
                ],
            }
        ],
    }
    history = {"weeks": 4, "adherence": 0.4, "sessions": []}
    result = review_plan(plan, history)
    assert result["changes"], result
    ex = result["workout"]["days"][0]["exercises"][0]
    assert ex["sets"] == 3  # 4 * 0.8 = 3.2 → 3


def test_revisao_deload_com_rpe_alto():
    plan = {
        "name": "P",
        "goal": "general",
        "daysPerWeek": 1,
        "days": [
            {
                "name": "A",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "exercises": [
                    {
                        "exerciseId": "e1",
                        "exerciseOrder": 0,
                        "sets": 4,
                        "targetReps": "8-12",
                        "targetRpe": 8.0,
                        "restSeconds": 90,
                    }
                ],
            }
        ],
    }
    sessions = [
        {"modality": "strength", "completed": True, "rpe": 9.0},
        {"modality": "strength", "completed": True, "rpe": 9.0},
    ]
    history = {"weeks": 2, "adherence": 1.0, "sessions": sessions}
    result = review_plan(plan, history)
    assert any("Deload" in c for c in result["changes"]), result["changes"]
    ex = result["workout"]["days"][0]["exercises"][0]
    assert ex["sets"] == 3  # 4 - 1


def test_revisao_sem_historico_nao_muda():
    plan = {"name": "P", "goal": "general", "daysPerWeek": 1, "days": []}
    result = review_plan(plan, {"weeks": 0, "adherence": 1.0, "sessions": []})
    assert result["changes"] == []
    assert result["source"] == "local"
    assert result["workout"] == plan


def test_natacao_nao_vira_plano():
    plan = {
        "name": "P",
        "goal": "general",
        "daysPerWeek": 1,
        "days": [
            {
                "name": "A",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "exercises": [],
            }
        ],
    }
    sessions = [
        {"modality": "swim", "completed": True, "notes": "dor no ombro", "rpe": 6.0}
    ]
    history = {"weeks": 2, "adherence": 1.0, "sessions": sessions}
    result = review_plan(plan, history)
    # Natação não gera bloco: nenhum dia com modality swim.
    assert all(d["modality"] != "swim" for d in result["workout"]["days"])
    assert any("Natação" in c for c in result["changes"]), result["changes"]
