"""Testes do plano híbrido (H3-11): geração e revisão determinística.

Rodam contra o FastAPI com TestClient e banco SQLite temporário. Sem
SENSENOVA_API_KEY definido, a geração cai no fallback local híbrido.
"""

import os
import tempfile

from fastapi.testclient import TestClient

from app import database as db
from app import main
from app.plan_builder import (
    review_plan,
    build_hybrid_plan,
    resolve_plan_ids,
    normalize_hybrid_plan,
)


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


# ---------------- Novos testes de invariantes (H3-11) ----------------


def test_cada_dia_tem_uma_unica_modalidade():
    client = _client()
    r = client.post("/v1/generate-workout", json=_hybrid_payload())
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    for day in workout["days"]:
        metric_types = {ex.get("metricType") for ex in day["exercises"]}
        assert len(metric_types) == 1, f"dia mistura metricTypes: {day['name']}"


def test_musculacao_tem_4_a_8_exercicios():
    client = _client()
    r = client.post("/v1/generate-workout", json=_hybrid_payload())
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    for day in workout["days"]:
        if day["modality"] == "strength":
            assert 4 <= len(day["exercises"]) <= 8, day["name"]


def test_musculacao_academia_usa_equipamentos_de_academia():
    payload = _hybrid_payload()
    payload["environment"] = "gym"
    payload["modalities"][0]["availableEquipment"] = [
        "barbell", "dumbbell", "machine", "cable", "body only"
    ]
    client = _client()
    r = client.post("/v1/generate-workout", json=payload)
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    load_eq = {"barbell", "dumbbell", "machine", "cable"}
    strength_days = [d for d in workout["days"] if d["modality"] == "strength"]
    assert strength_days, "esperava pelo menos um dia de musculação"
    count_equip = 0
    for day in strength_days:
        for ex in day["exercises"]:
            if ex.get("equipment", "").strip().lower() in load_eq:
                count_equip += 1
    assert count_equip >= 3, f"apenas {count_equip} exercícios de equipamento de academia"


def test_musculacao_nao_usa_alongamento_ou_cardio():
    client = _client()
    r = client.post("/v1/generate-workout", json=_hybrid_payload())
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    for day in workout["days"]:
        if day["modality"] == "strength":
            for ex in day["exercises"]:
                assert ex.get("metricType") == "reps_weight", ex["exerciseId"]


def test_musculacao_3x_nao_usa_dias_consecutivos():
    client = _client()
    r = client.post("/v1/generate-workout", json=_hybrid_payload())
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    strength_weekdays = sorted(
        d["weekday"] for d in workout["days"] if d["modality"] == "strength"
    )
    for i in range(len(strength_weekdays) - 1):
        assert abs(strength_weekdays[i] - strength_weekdays[i + 1]) != 1, (
            f"musculação em dias consecutivos: {strength_weekdays}"
        )


def test_modalidades_diferentes_podem_compartilhar_weekday():
    client = _client()
    r = client.post("/v1/generate-workout", json=_hybrid_payload())
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    by_weekday = {}
    for day in workout["days"]:
        by_weekday.setdefault(day["weekday"], set()).add(day["modality"])
    compartilham = [wd for wd, mods in by_weekday.items() if len(mods) > 1]
    # Com 7 sessões em 7 dias e musculação não-consecutiva, é esperado que
    # haja compartilhamento de weekday.
    assert len(compartilham) >= 1, "esperava pelo menos um weekday com múltiplas modalidades"


def test_sessoes_no_mesmo_dia_nao_sao_mescladas():
    """Cada day tem exatamente uma modality, mesmo quando vários dias caem no mesmo weekday."""
    client = _client()
    r = client.post("/v1/generate-workout", json=_hybrid_payload())
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    for day in workout["days"]:
        modalities = {ex.get("metricType") for ex in day["exercises"]}
        assert len(modalities) == 1, f"day mistura modalidades: {day['name']}"


def test_tres_modalidades_no_mesmo_dia_sao_tres_dias():
    """Força 3 modalidades no mesmo weekday: resultam em 3 days separados."""
    payload = _hybrid_payload()
    payload["modalities"] = [
        {"type": "strength", "sessionsPerWeek": 1, "goal": "hypertrophy", "level": "intermediate"},
        {"type": "run", "sessionsPerWeek": 1, "goal": "endurance", "level": "beginner"},
        {"type": "mobility", "sessionsPerWeek": 1, "focus": "back"},
    ]
    payload["sessionDurationMinutes"] = 60
    client = _client()
    r = client.post("/v1/generate-workout", json=payload)
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    assert len(workout["days"]) == 3
    modalities = {d["modality"] for d in workout["days"]}
    assert modalities == {"strength", "run", "mobility"}


def test_duracao_por_modalidade_limita_volume():
    """Mobilidade dura 5-15 min; corrida escala sets pela duração."""
    payload = _hybrid_payload()
    payload["modalities"] = [
        {"type": "mobility", "sessionsPerWeek": 1, "focus": "back", "sessionDurationMinutes": 10},
    ]
    payload["sessionDurationMinutes"] = 60
    client = _client()
    r = client.post("/v1/generate-workout", json=payload)
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    mob = [d for d in workout["days"] if d["modality"] == "mobility"][0]
    ex = mob["exercises"][0]
    dur = ex.get("targetDurationSeconds") or 0
    assert 300 <= dur <= 900, f"duração da mobilidade fora da faixa: {dur}"


def test_corrida_varia_templates_por_sessao():
    """Com 3 sessões de corrida, os templates rotacionam (não repetem o primeiro)."""
    payload = _hybrid_payload()
    payload["modalities"] = [
        {"type": "run", "sessionsPerWeek": 3, "level": "intermediate"},
    ]
    payload["sessionDurationMinutes"] = 40
    client = _client()
    r = client.post("/v1/generate-workout", json=payload)
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    run_days = [d for d in workout["days"] if d["modality"] == "run"]
    ids = [d["exercises"][0]["exerciseId"] for d in run_days]
    assert len(set(ids)) > 1, f"templates não rotacionaram: {ids}"


def test_corrida_escala_distancia_pela_duracao():
    """Duração maior → distância maior (clamp entre 1 km e 21 km)."""
    payload_curto = _hybrid_payload()
    payload_curto["modalities"] = [
        {"type": "run", "sessionsPerWeek": 1, "level": "intermediate", "sessionDurationMinutes": 30},
    ]
    payload_longo = _hybrid_payload()
    payload_longo["modalities"] = [
        {"type": "run", "sessionsPerWeek": 1, "level": "intermediate", "sessionDurationMinutes": 90},
    ]
    client = _client()
    c = client.post("/v1/generate-workout", json=payload_curto).json()["workout"]
    l = client.post("/v1/generate-workout", json=payload_longo).json()["workout"]
    dc = c["days"][0]["exercises"][0].get("targetDistanceMeters") or 0
    dl = l["days"][0]["exercises"][0].get("targetDistanceMeters") or 0
    assert dl >= dc, f"longa ({dl}) deveria ser >= curta ({dc})"


def test_mobilidade_respeita_foco_e_duracao():
    payload = _hybrid_payload()
    payload["modalities"] = [
        {"type": "mobility", "sessionsPerWeek": 1, "focus": "hips", "sessionDurationMinutes": 8},
    ]
    payload["sessionDurationMinutes"] = 60
    client = _client()
    r = client.post("/v1/generate-workout", json=payload)
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    mob = [d for d in workout["days"] if d["modality"] == "mobility"][0]
    ex = mob["exercises"][0]
    assert "hips" in (ex.get("notes") or ""), ex.get("notes")
    dur = ex.get("targetDurationSeconds") or 0
    assert 300 <= dur <= 900


def test_normaliza_ia_com_modalidades_misturadas():
    """Um day com musculação+corrida misturadas é dividido em dois days separados."""
    plan = {
        "name": "P",
        "goal": "general",
        "daysPerWeek": 2,
        "days": [
            {
                "name": "Dia misto",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "exercises": [
                    {
                        "exerciseId": "Barbell_Bench_Press",
                        "exerciseOrder": 0,
                        "sets": 3,
                        "targetReps": "8-12",
                        "targetRpe": 8.0,
                        "restSeconds": 90,
                        "metricType": "reps_weight",
                    },
                    {
                        "exerciseId": "run_interval",
                        "exerciseOrder": 1,
                        "sets": 5,
                        "targetReps": "",
                        "targetRpe": 8.0,
                        "restSeconds": 120,
                        "metricType": "distance_duration",
                    },
                ],
            }
        ],
    }
    result = normalize_hybrid_plan(plan)
    days = result["days"]
    assert len(days) == 2, f"esperava 2 days, obteve {len(days)}: {days}"
    modalities = [d["modality"] for d in days]
    assert "strength" in modalities
    assert "run" in modalities
    for d in days:
        assert all(
            ex.get("metricType") == ("reps_weight" if d["modality"] == "strength" else "distance_duration")
            for ex in d["exercises"]
        )


def test_resolve_ids_ignora_corrida_e_mobilidade():
    """IDs sintéticos run_*/mobility_* não são sinalizados como pendentes."""
    plan = {
        "days": [
            {
                "dayOrder": 0,
                "weekday": 1,
                "modality": "run",
                "exercises": [{"exerciseId": "run_interval", "exerciseOrder": 0}],
            },
            {
                "dayOrder": 1,
                "weekday": 1,
                "modality": "mobility",
                "exercises": [{"exerciseId": "mobility_back", "exerciseOrder": 0}],
            },
            {
                "dayOrder": 2,
                "weekday": 2,
                "modality": "strength",
                "exercises": [{"exerciseId": "Barbell_Bench_Press", "exerciseOrder": 0}],
            },
        ]
    }
    catalog = [
        {"id": "Barbell_Bench_Press", "name": "Supino", "primaryMuscles": ["chest"], "equipment": "barbell", "category": "strength"}
    ]
    result, unresolved = resolve_plan_ids(plan, catalog)
    assert unresolved == [], f"IDs sintéticos não deveriam ser pendentes: {unresolved}"


def test_sets_de_corrida_aceitam_intervalos_ate_20():
    """Sets de corrida podem ir até 20 (escala por duração longa)."""
    payload = _hybrid_payload()
    payload["modalities"] = [
        {"type": "run", "sessionsPerWeek": 1, "level": "advanced", "sessionDurationMinutes": 120},
    ]
    payload["sessionDurationMinutes"] = 120
    client = _client()
    r = client.post("/v1/generate-workout", json=payload)
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    run = [d for d in workout["days"] if d["modality"] == "run"][0]
    sets = run["exercises"][0]["sets"]
    assert 3 <= sets <= 20, f"sets de corrida fora da faixa: {sets}"


def test_fallback_sem_ia():
    client = _client()
    r = client.post("/v1/generate-workout", json=_hybrid_payload())
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["source"] == "local"
    assert data["workout"]["daysPerWeek"] == 7  # 3+2+2 sessões


# ---------------- Testes de revisão (mantidos) ----------------


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
    assert all(d["modality"] != "swim" for d in result["workout"]["days"])
    assert any("Natação" in c for c in result["changes"]), result["changes"]
