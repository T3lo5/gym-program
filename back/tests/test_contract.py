"""Valida o contrato JSON compartilhado `contracts/generate_response.json` no modelo Pydantic.

O mesmo contrato é lido pelo app (Kotlin) via `contracts/generate_response.json`.
Aqui usamos o mesmo JSON inline para validar no backend sem depender do arquivo
físico no container de CI (que monta apenas `back/`).
"""

from app.main import GenerateResponse

FIXTURE = {
    "workout": {
        "name": "Full Body A",
        "description": "Plano de contrato",
        "goal": "hypertrophy",
        "daysPerWeek": 2,
        "days": [
            {
                "name": "Treino A",
                "dayOrder": 0,
                "isRestDay": False,
                "exercises": [
                    {
                        "exerciseId": "Barbell_Bench_Press",
                        "exerciseOrder": 0,
                        "sets": 3,
                        "targetReps": "8-12",
                        "targetRpe": 8.0,
                        "restSeconds": 90,
                        "notes": "",
                        "isSupersetWithPrevious": False,
                        "exerciseName": "Supino reto",
                        "primaryMuscles": ["chest"],
                        "equipment": "barbell",
                    }
                ],
            }
        ],
    },
    "generationTimeMs": 1200,
    "modelUsed": "contrato",
    "planId": 42,
    "nextReviewAt": "2026-12-01",
    "reviewAfterWeeks": 4,
    "source": "local",
    "fallbackReason": "no_api_key",
}


def test_fixture_e_um_generate_response_valido():
    response = GenerateResponse.model_validate(FIXTURE)
    assert response.planId == 42
    assert response.source == "local"
    assert response.fallbackReason == "no_api_key"
    assert response.workout.name == "Full Body A"
    assert response.workout.daysPerWeek == 2


def test_fallback_reason_padrao_vazio():
    # Campo aditivo (H3-21): sem a chave, o modelo usa "" — que é o valor
    # esperado quando source == "ai".
    data = {key: value for key, value in FIXTURE.items() if key != "fallbackReason"}
    response = GenerateResponse.model_validate(data)
    assert response.fallbackReason == ""


def test_campos_obrigatorios_presentes():
    data = FIXTURE
    # Campos obrigatórios do GenerateResponse
    assert "workout" in data
    assert "nextReviewAt" in data
    assert "reviewAfterWeeks" in data

    # Campos obrigatórios do workout
    workout = data["workout"]
    assert "name" in workout
    assert "goal" in workout
    assert "daysPerWeek" in workout
    assert "days" in workout

    # Campos obrigatórios do dia
    day = workout["days"][0]
    assert "name" in day
    assert "dayOrder" in day
    assert "exercises" in day

    # Campos obrigatórios do exercício
    exercise = day["exercises"][0]
    assert "exerciseId" in exercise
    assert "exerciseOrder" in exercise
    assert "sets" in exercise
    assert "targetReps" in exercise
    assert "restSeconds" in exercise


def test_source_permitido():
    response = GenerateResponse.model_validate(FIXTURE)
    assert response.source in {"ai", "local"}
