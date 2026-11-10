"""Valida o contrato v2 de geração/revisão (`contracts/generate_response.json`)
contra os modelos Pydantic do backend, garantindo compatibilidade com v1."""

from app.main import (
    GenerateJobAck,
    GenerateJobStatus,
    GenerateResponse,
    GenerateReviewRequest,
    GenerateReviewResponse,
    GenerateRequest,
)

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


# ---------------- Novos testes de contrato v2 (H3-11) ----------------


def test_dia_hibrido_tem_uma_modalidade():
    """Cada day do plano híbrido tem exatamente uma modality."""
    for day in FIXTURE_V2["workout"]["days"]:
        metric_types = {ex.get("metricType") for ex in day["exercises"]}
        assert len(metric_types) == 1, f"day mistura modalidades: {day['name']}"
    assert FIXTURE_V2["workout"]["days"][0]["modality"] == "strength"
    assert FIXTURE_V2["workout"]["days"][1]["modality"] == "run"


def test_request_com_environment_e_duracao_por_modalidade():
    """GenerateRequest aceita environment e modalities com duração por modalidade."""
    from app.main import GenerateRequest, ModalityRequest

    req = GenerateRequest(
        deviceId="d1",
        environment="gym",
        modalities=[
            ModalityRequest(
                type="strength",
                sessionsPerWeek=3,
                sessionDurationMinutes=60,
                availableEquipment=["barbell", "dumbbell"],
            ),
            ModalityRequest(
                type="run",
                sessionsPerWeek=2,
                sessionDurationMinutes=40,
                recent5kSeconds=1800,
            ),
        ],
        reviewAfterWeeks=4,
    )
    assert req.environment == "gym"
    assert req.modalities[0].type == "strength"
    assert req.modalities[0].sessionDurationMinutes == 60
    assert req.modalities[1].type == "run"
    assert req.modalities[1].recent5kSeconds == 1800
    # Request v1 (sem modalities) continua válido.
    req_v1 = GenerateRequest(deviceId="d1")
    assert req_v1.modalities == []
    assert req_v1.environment is None


# ---------------- Novos testes de contrato v2 (H3-16) ----------------


def test_generate_com_focus_muscles():
    """Fixture do plano complementar valida contra GenerateRequest (H3-16).

    Mesmo padrão do `test_contract.py`: o JSON é inline porque o container de
    CI do backend monta apenas `back/`. O arquivo físico
    `contracts/generate_request_complementary.json` é lido pelo app
    (`ContractTest.kt`) e precisa manter o mesmo conteúdo.
    """
    fixture = {
        "deviceId": "contrato",
        "goal": "hypertrophy",
        "environment": "bodyweight",
        "daysPerWeek": 2,
        "availableEquipment": ["body only"],
        "targetMuscles": [],
        "injuriesOrLimitations": "",
        "currentLevel": "intermediate",
        "sessionDurationMinutes": 15,
        "reviewAfterWeeks": 4,
        "focusMuscles": ["abs"],
        "modalities": [
            {
                "type": "strength",
                "sessionsPerWeek": 2,
                "goal": "hypertrophy",
                "level": "intermediate",
                "availableEquipment": ["body only"],
                "targetMuscles": [],
                "sessionDurationMinutes": 15,
            }
        ],
    }

    request = GenerateRequest.model_validate(fixture)
    assert request.focusMuscles == ["abs"]
    # Modo complementar aceita duração/dias fora dos limites do plano principal.
    assert request.sessionDurationMinutes == 15
    assert request.daysPerWeek == 2
    assert request.environment == "bodyweight"
    assert [m.type for m in request.modalities] == ["strength"]


def test_focus_muscles_omitido_continua_valido():
    """Sem focusMuscles o request do plano principal continua válido (v1)."""
    request = GenerateRequest(deviceId="d1", daysPerWeek=4, sessionDurationMinutes=60)
    assert request.focusMuscles is None


def test_focus_muscles_vazio_422():
    """Lista vazia é entrada inválida: o app omite o campo quando não há foco."""
    import pytest
    from pydantic import ValidationError

    with pytest.raises(ValidationError) as exc_info:
        GenerateRequest(deviceId="d1", focusMuscles=[])
    assert "vazia" in str(exc_info.value)


def test_focus_muscles_com_run_422():
    """Complementar é só de força: run junto do foco é rejeitado."""
    import pytest
    from pydantic import ValidationError

    with pytest.raises(ValidationError) as exc_info:
        GenerateRequest(
            deviceId="d1",
            focusMuscles=["abs"],
            modalities=[
                {"type": "strength", "sessionsPerWeek": 1},
                {"type": "run", "sessionsPerWeek": 1},
            ],
        )
    assert "força" in str(exc_info.value)


def test_duracao_15_sem_foco_422():
    """15 min só é aceito no modo complementar; sem foco, mínimo 30."""
    import pytest
    from pydantic import ValidationError

    with pytest.raises(ValidationError):
        GenerateRequest(deviceId="d1", sessionDurationMinutes=15)
    # Com foco, 15 min é aceito.
    request = GenerateRequest(deviceId="d1", focusMuscles=["abs"], sessionDurationMinutes=15)
    assert request.sessionDurationMinutes == 15


# ---------------- Novos testes de contrato v2 (H3-30) ----------------


def test_goal_modalidade_forca_aceito():
    """`modalities[].goal` aceita o objetivo do chip (H3-30) e o prompt o
    carrega — o app envia o goal escolhido na modalidade de força em vez do
    "hypertrophy" fixo; sem mudança de schema, o campo já existia."""
    from app.main import ModalityRequest, build_hybrid_user_prompt

    req = GenerateRequest(
        deviceId="d1",
        goal="strength",
        modalities=[
            ModalityRequest(type="strength", sessionsPerWeek=4, goal="strength"),
            ModalityRequest(type="run", sessionsPerWeek=2, goal="endurance"),
        ],
    )
    assert req.modalities[0].goal == "strength"
    assert req.modalities[1].goal == "endurance"

    # O prompt da IA recebe o objetivo coerente por modalidade.
    prompt = build_hybrid_user_prompt(req)
    assert "objetivo=strength" in prompt
    assert "objetivo=endurance" in prompt


def test_goal_modalidade_forca_valores_fechados():
    """Os seis valores fechados do contrato são aceitos em modalities[].goal."""
    from app.main import ModalityRequest

    valores = ["hypertrophy", "strength", "endurance", "power", "fat_loss", "general"]
    for valor in valores:
        req = GenerateRequest(
            deviceId="d1",
            modalities=[ModalityRequest(type="strength", goal=valor)],
        )
        assert req.modalities[0].goal == valor


def test_goal_modalidade_fora_da_lista_422():
    """Valor fora da lista fechada é rejeitado (contrato v2)."""
    import pytest
    from pydantic import ValidationError

    from app.main import ModalityRequest

    with pytest.raises(ValidationError):
        GenerateRequest(
            deviceId="d1",
            modalities=[ModalityRequest(type="strength", goal="hipertrofia")],
        )


# ---------------- Novos testes de contrato v2 (H3-40) ----------------


def test_job_ack_round_trip():
    """GenerateJobAck aceita o payload do contrato generate_job_ack."""
    ack = GenerateJobAck.model_validate(
        {"jobId": "b8f1c2d4-0000-4000-8000-000000000001", "status": "pending"}
    )
    assert ack.jobId == "b8f1c2d4-0000-4000-8000-000000000001"
    assert ack.status == "pending"


def test_job_status_done_round_trip():
    """GenerateJobStatus aceita o payload do contrato generate_job_status."""
    status = GenerateJobStatus.model_validate(
        {
            "jobId": "b8f1c2d4-0000-4000-8000-000000000001",
            "status": "done",
            "createdAt": "2026-11-10T12:00:00+00:00",
            "result": {
                "workout": {
                    "name": "Full Body A",
                    "description": "Plano de contrato",
                    "goal": "hypertrophy",
                    "daysPerWeek": 2,
                    "days": [
                        {
                            "name": "Treino A",
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
            },
            "error": None,
        }
    )
    assert status.jobId == "b8f1c2d4-0000-4000-8000-000000000001"
    assert status.status == "done"
    assert status.createdAt == "2026-11-10T12:00:00+00:00"
    assert status.result is not None
    assert status.result.source == "local"
    assert status.result.fallbackReason == "no_api_key"
    assert status.error is None

