"""Testes das regras de perfil (IMC, sugestão de carga, prompt)."""

from app.main import build_user_prompt
from app.plan_builder import profile_adjustments, suggest_initial_load


def _base_plan():
    return {
        "name": "Plano teste",
        "goal": "hypertrophy",
        "daysPerWeek": 3,
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
                    },
                    {
                        "exerciseId": "Barbell_Back_Squat",
                        "exerciseOrder": 1,
                        "sets": 3,
                        "targetReps": "8-12",
                        "targetRpe": 8.0,
                        "restSeconds": 90,
                        "notes": "",
                        "isSupersetWithPrevious": False,
                        "exerciseName": "Agachamento",
                        "primaryMuscles": ["quadriceps"],
                        "equipment": "barbell",
                    },
                    {
                        "exerciseId": "Push_Ups",
                        "exerciseOrder": 2,
                        "sets": 3,
                        "targetReps": "8-12",
                        "targetRpe": 8.0,
                        "restSeconds": 90,
                        "notes": "",
                        "isSupersetWithPrevious": False,
                        "exerciseName": "Flexão",
                        "primaryMuscles": ["chest"],
                        "equipment": "body only",
                    },
                ],
            }
        ],
    }


def test_sem_perfil_nao_altera_plano():
    plan = _base_plan()
    result = profile_adjustments(plan, None, None)
    assert result == plan
    ex = result["days"][0]["exercises"][0]
    assert ex["restSeconds"] == 90
    assert ex["targetRpe"] == 8.0
    assert ex["sets"] == 3


def test_imc_baixo_aumenta_descanso_reduz_rpe():
    # peso 50 kg, altura 180 cm -> IMC = 15.4 (< 18.5)
    plan = _base_plan()
    result = profile_adjustments(plan, 50.0, 180)
    ex = result["days"][0]["exercises"][0]
    assert ex["restSeconds"] == 105  # 90 + 15
    assert ex["targetRpe"] == 7.5  # 8.0 - 0.5
    assert ex["sets"] == 3


def test_imc_alto_aumenta_descanso_reduz_series():
    # peso 100 kg, altura 180 cm -> IMC = 30.9 (>= 30.0)
    plan = _base_plan()
    result = profile_adjustments(plan, 100.0, 180)
    ex = result["days"][0]["exercises"][0]
    assert ex["restSeconds"] == 120  # 90 + 30
    assert ex["sets"] == 2  # 3 - 1 (min 2)


def test_imc_baixo_rpe_minimo_5():
    # peso 40 kg, altura 200 cm -> IMC = 10.0 (< 18.5), RPE 5.0 já é min
    plan = _base_plan()
    for ex in plan["days"][0]["exercises"]:
        ex["targetRpe"] = 5.0
    result = profile_adjustments(plan, 40.0, 200)
    ex = result["days"][0]["exercises"][0]
    assert ex["targetRpe"] == 5.0  # não desce abaixo de 5.0


def test_imc_alto_series_minimo_2():
    # peso 150 kg, altura 150 cm -> IMC = 66.7 (>= 30.0), sets 1 vira 2
    plan = _base_plan()
    for ex in plan["days"][0]["exercises"]:
        ex["sets"] = 1
    result = profile_adjustments(plan, 150.0, 150)
    ex = result["days"][0]["exercises"][0]
    assert ex["sets"] == 2  # não desce abaixo de 2


def test_sugestao_carga_superior():
    # 80 kg, peito (superior, barra) -> 0.25 * 80 = 20.0 kg
    plan = _base_plan()
    result = suggest_initial_load(plan, 80.0)
    ex = result["days"][0]["exercises"][0]
    assert "Carga inicial sugerida: 20.0 kg" in ex["notes"]


def test_sugestao_carga_inferior():
    # 80 kg, pernas (inferior, barra) -> 0.5 * 80 = 40.0 kg
    plan = _base_plan()
    result = suggest_initial_load(plan, 80.0)
    ex = result["days"][0]["exercises"][1]
    assert "Carga inicial sugerida: 40.0 kg" in ex["notes"]


def test_peso_corporal_sem_sugestao():
    # peso corporal não sugere carga
    plan = _base_plan()
    result = suggest_initial_load(plan, 80.0)
    ex = result["days"][0]["exercises"][2]  # Push_Ups, body only
    assert "Carga inicial sugerida" not in ex["notes"]


def test_sugestao_concatena_notes_existentes():
    plan = _base_plan()
    plan["days"][0]["exercises"][0]["notes"] = "Aquecer bem os ombros."
    result = suggest_initial_load(plan, 80.0)
    ex = result["days"][0]["exercises"][0]
    assert ex["notes"] == "Aquecer bem os ombros. Carga inicial sugerida: 20.0 kg (ajuste conforme percepção)."


def test_prompt_inclui_perfil():
    class Req:
        goal = "hypertrophy"
        daysPerWeek = 3
        availableEquipment = ["barbell"]
        targetMuscles = ["chest"]
        currentLevel = "intermediate"
        sessionDurationMinutes = 60
        preferredSplit = None
        injuriesOrLimitations = ""
        bodyWeightKg = 82.5
        heightCm = 178
        sex = None

    prompt = build_user_prompt(Req())
    assert "- Peso corporal: 82.5 kg" in prompt
    assert "- Altura: 178 cm" in prompt
    assert "- IMC: 26.0" in prompt
    assert "Use o perfil para calibrar descanso" in prompt
    assert "não prescrição médica" in prompt


def test_prompt_sem_perfil_nao_menciona_peso():
    class Req:
        goal = "hypertrophy"
        daysPerWeek = 3
        availableEquipment = ["barbell"]
        targetMuscles = ["chest"]
        currentLevel = "intermediate"
        sessionDurationMinutes = 60
        preferredSplit = None
        injuriesOrLimitations = ""
        bodyWeightKg = None
        heightCm = None
        sex = None

    prompt = build_user_prompt(Req())
    assert "Peso corporal" not in prompt
    assert "Altura:" not in prompt
    assert "IMC:" not in prompt


def test_limites_de_validacao():
    # pydantic rejeita valores fora da faixa; aqui apenas verificamos que
    # valores na borda são aceitos pelo campo.
    from app.main import GenerateRequest

    r = GenerateRequest(bodyWeightKg=30.0, heightCm=120)
    assert r.bodyWeightKg == 30.0
    assert r.heightCm == 120

    r = GenerateRequest(bodyWeightKg=300.0, heightCm=250)
    assert r.bodyWeightKg == 300.0
    assert r.heightCm == 250


# ---------------- Sexo opcional (H3-7) ----------------


def _prompt_request(**kwargs):
    defaults = {
        "goal": "hypertrophy",
        "daysPerWeek": 3,
        "availableEquipment": ["barbell"],
        "targetMuscles": ["chest"],
        "currentLevel": "intermediate",
        "sessionDurationMinutes": 60,
        "preferredSplit": None,
        "injuriesOrLimitations": "",
        "bodyWeightKg": None,
        "heightCm": None,
        "sex": None,
    }
    defaults.update(kwargs)

    class Req:
        pass

    req = Req()
    for key, value in defaults.items():
        setattr(req, key, value)
    return req


def test_sexo_ignorado_no_plano_local():
    """H3-7 decisão de escopo: sexo não pode alterar nenhum número do plano local."""
    import inspect

    from app.main import GenerateRequest

    # O campo existe no contrato, mas com default None: request antigo continua válido.
    assert "sex" in GenerateRequest.model_fields
    assert GenerateRequest(deviceId="d1").sex is None

    # A única função que ajusta descanso/RPE/séries não recebe o campo.
    assert "sex" not in inspect.signature(profile_adjustments).parameters
    assert "sex" not in inspect.signature(suggest_initial_load).parameters

    com_sexo = GenerateRequest(bodyWeightKg=82.5, heightCm=178, sex="female")
    sem_sexo = GenerateRequest(bodyWeightKg=82.5, heightCm=178)
    assert profile_adjustments(_base_plan(), com_sexo.bodyWeightKg, com_sexo.heightCm) == profile_adjustments(
        _base_plan(), sem_sexo.bodyWeightKg, sem_sexo.heightCm
    )
    # Sexo informado sem peso/altura também não muda nada.
    assert profile_adjustments(_base_plan(), None, None) == _base_plan()


def test_prompt_inclui_sexo():
    prompt = build_user_prompt(_prompt_request(bodyWeightKg=82.5, heightCm=178, sex="female"))

    assert "- Sexo biológico: feminino" in prompt
    assert "- Peso corporal: 82.5 kg" in prompt
    assert "- Altura: 178 cm" in prompt
    assert "- IMC: 26.0" in prompt


def test_prompt_omite_sexo_null():
    assert "Sexo" not in build_user_prompt(_prompt_request(sex=None))
    assert "Sexo" not in build_user_prompt(_prompt_request(bodyWeightKg=82.5, heightCm=178))


def test_sexo_fora_do_enum_da_422():
    from fastapi.testclient import TestClient

    from app.main import app

    with TestClient(app) as client:
        response = client.post("/v1/generate-workout", json={"deviceId": "d1", "sex": "qualquer-coisa"})

    assert response.status_code == 422
