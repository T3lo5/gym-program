"""Valida o contrato v2 de geração/revisão (`contracts/generate_response.json`)
contra os modelos Pydantic do backend, garantindo compatibilidade com v1."""

from app.main import GenerateResponse, GenerateReviewRequest, GenerateReviewResponse

FIXTURE_V2 = {
    "workout": {
        "name": "Semana híbrida",
        "description": "Plano semanal com musculação, corrida e mobilidade.",
        "goal": "general",
        "daysPerWeek": 3,
        "days": [
            {
                "name": "Musculação A",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
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
                        "metricType": "reps_weight",
                        "targetDurationSeconds": None,
                        "targetDistanceMeters": None,
                        "targetPaceSecondsPerKm": None,
                        "targetHrZone": None,
                    }
                ],
            },
            {
                "name": "5 x 1 km",
                "dayOrder": 1,
                "weekday": 2,
                "modality": "run",
                "isRestDay": False,
                "exercises": [
                    {
                        "exerciseId": "run_interval",
                        "exerciseOrder": 0,
                        "sets": 5,
                        "targetReps": "",
                        "targetRpe": 8.0,
                        "restSeconds": 120,
                        "notes": "Aquecimento 10 min + 5 x 1 km forte + 2 min trote",
                        "isSupersetWithPrevious": False,
                        "exerciseName": "5 x 1 km",
                        "primaryMuscles": ["legs"],
                        "equipment": "nenhum",
                        "metricType": "distance_duration",
                        "targetDurationSeconds": None,
                        "targetDistanceMeters": 1000,
                        "targetPaceSecondsPerKm": 330,
                        "targetHrZone": None,
                    }
                ],
            },
        ],
    },
    "generationTimeMs": 1200,
    "modelUsed": "contrato",
    "planId": 42,
    "nextReviewAt": "2026-12-01",
    "reviewAfterWeeks": 4,
    "source": "local",
    "changes": [],
}

# Fixture v1 (sem campos novos): deve continuar válida.
FIXTURE_V1 = {
    "workout": {
        "name": "Full Body A",
        "description": "Plano de contrato",
        "goal": "hypertrophy",
        "daysPerWeek": 2,
        "days": [
            {
                "name": "Treino A",
                "dayOrder": 0,
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
}


def test_fixture_v2_valido():
    response = GenerateResponse.model_validate(FIXTURE_V2)
    assert response.source == "local"
    assert response.workout.name == "Semana híbrida"
    assert response.workout.daysPerWeek == 3
    run_day = response.workout.days[1]
    assert run_day.modality == "run"
    assert run_day.weekday == 2
    ex = run_day.exercises[0]
    assert ex.metricType == "distance_duration"
    assert ex.targetDistanceMeters == 1000
    assert ex.targetPaceSecondsPerKm == 330


def test_review_response_valido():
    response = GenerateReviewResponse.model_validate(
        {
            "workout": FIXTURE_V2["workout"],
            "changes": ["Volume reduzido 20% por adesão baixa."],
            "source": "local",
        }
    )
    assert response.source == "local"
    assert len(response.changes) == 1


def test_review_request_valido():
    request = GenerateReviewRequest.model_validate(
        {
            "deviceId": "d1",
            "plan": FIXTURE_V2["workout"],
            "history": {"weeks": 4, "adherence": 0.75, "sessions": []},
        }
    )
    assert request.deviceId == "d1"
    assert request.history["adherence"] == 0.75


def test_v1_continua_valido():
    response = GenerateResponse.model_validate(FIXTURE_V1)
    assert response.source == "local"
    assert response.workout.name == "Full Body A"
    # Campos novos têm defaults e não quebram o v1.
    assert response.workout.days[0].modality == "strength"
    assert response.workout.days[0].weekday is None
