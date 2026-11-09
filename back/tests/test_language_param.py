"""Testes do parâmetro `language` na geração (H3-36).

`language="pt"` ⇒ planos (IA ou fallback local) nascem com nomes/instruções
em português, com o mesmo `exerciseId`. Ausente ⇒ "en" (comportamento
anterior preservado). `id_name_mismatches` aceita o nome EN **ou** PT do
mesmo ID (mapa duplo), sem falso positivo para plano em PT.
"""

import os
import tempfile

from fastapi.testclient import TestClient

from app import database as db
from app import main
from app.plan_builder import id_name_mismatches


def setup_test_db():
    fd, path = tempfile.mkstemp(suffix=".db")
    os.close(fd)
    db.DB_PATH = path
    db.init_db()
    return path


def _client():
    setup_test_db()
    return TestClient(main.app)


def _payload(language=None):
    payload = {
        "deviceId": "d1",
        "goal": "hypertrophy",
        "daysPerWeek": 3,
        "availableEquipment": ["barbell", "dumbbell"],
        "targetMuscles": [],
        "currentLevel": "intermediate",
        "sessionDurationMinutes": 60,
        "reviewAfterWeeks": 4,
    }
    if language is not None:
        payload["language"] = language
    return payload


def _catalog_names():
    return {ex["id"]: ex for ex in main.EXERCISE_CATALOG}


def test_language_pt_fallback_nomes_pt():
    client = _client()
    r = client.post("/v1/generate-workout", json=_payload("pt"))
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    names = _catalog_names()
    exercise_ids = [
        ex["exerciseId"]
        for day in workout["days"]
        for ex in day["exercises"]
        if not str(ex["exerciseId"]).startswith(("run_", "mobility_"))
    ]
    assert exercise_ids, "plano sem exercícios de força"
    for ex_id in exercise_ids:
        source = names[ex_id]
        expected_name = source.get("namePt") or source["name"]
        # O nome do plano é exatamente o do catálogo no idioma pedido.
        assert any(
            ex["exerciseName"] == expected_name
            for day in workout["days"]
            for ex in day["exercises"]
            if ex["exerciseId"] == ex_id
        ), f"{ex_id}: esperado '{expected_name}'"
        if source.get("instructionsPt"):
            assert any(
                ex["instructions"] == source["instructionsPt"]
                for day in workout["days"]
                for ex in day["exercises"]
                if ex["exerciseId"] == ex_id
            ), f"{ex_id}: instruções não estão em português"


def test_language_ausente_en():
    client = _client()
    r = client.post("/v1/generate-workout", json=_payload())
    assert r.status_code == 200, r.text
    workout = r.json()["workout"]
    names = _catalog_names()
    for day in workout["days"]:
        for ex in day["exercises"]:
            source = names.get(ex["exerciseId"])
            if source:
                assert ex["exerciseName"] == source["name"], (
                    f"{ex['exerciseId']}: sem language o nome deve continuar EN"
                )
                assert ex["instructions"] == source["instructions"]


def test_language_invalido_cai_para_en():
    # Literal["pt", "en"] rejeita outro valor — 422, nunca plano em idioma
    # indefinido.
    client = _client()
    r = client.post("/v1/generate-workout", json=_payload("es"))
    assert r.status_code == 422, r.text


def test_mismatch_aceita_nome_pt():
    names = _catalog_names()
    sit_up = names["3_4_Sit-Up"]
    bench = names["Barbell_Bench_Press_-_Medium_Grip"]
    assert sit_up["namePt"] == "Abdominal 3/4"
    assert bench["namePt"] == "Supino Reto com Barra - Pegada Média"

    plan = {
        "days": [
            {
                "exercises": [
                    # Nome PT oficial do ID: NÃO é divergência (mapa duplo).
                    {"exerciseId": "3_4_Sit-Up", "exerciseName": sit_up["namePt"]},
                    {"exerciseId": bench["id"], "exerciseName": bench["namePt"]},
                    # Nome EN oficial continua aceito (comportamento H3-22).
                    {"exerciseId": "3_4_Sit-Up", "exerciseName": sit_up["name"]},
                    # Nome que não corresponde a EN nem PT: segue divergente.
                    {"exerciseId": "3_4_Sit-Up", "exerciseName": "Exercício Totalmente Errado"},
                ]
            }
        ]
    }
    mismatches = id_name_mismatches(plan, main.EXERCISE_CATALOG)
    flagged = {(m["exerciseId"], m["declared"]) for m in mismatches}
    assert ("3_4_Sit-Up", "Exercício Totalmente Errado") in flagged
    assert ("3_4_Sit-Up", sit_up["namePt"]) not in flagged
    assert (bench["id"], bench["namePt"]) not in flagged
    assert ("3_4_Sit-Up", sit_up["name"]) not in flagged
