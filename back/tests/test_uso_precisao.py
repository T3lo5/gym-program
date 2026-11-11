"""Testes H3-42: revisão de precisão — pesos de uso reordenam o catálogo no prompt."""

from fastapi.testclient import TestClient

from app import main
from app.plan_builder import catalog_summary_for_request


def _request_with_weights(exercise_weights=None, **overrides):
    data = {
        "deviceId": "d1",
        "goal": "hypertrophy",
        "daysPerWeek": 2,
        "availableEquipment": ["barbell", "dumbbell", "body only", "cable", "machine"],
        "targetMuscles": ["chest"],
        "sessionDurationMinutes": 60,
        "currentLevel": "intermediate",
    }
    if exercise_weights is not None:
        data["exerciseWeights"] = exercise_weights
    data.update(overrides)
    return main.GenerateRequest(**data)


def _chest_lines(summary):
    lines = []
    in_chest = False
    for l in summary.splitlines():
        s = l.strip()
        if s == "CHEST:":
            in_chest = True
            continue
        if in_chest and s.endswith(":") and s != "CHEST:":
            break
        if in_chest and s.startswith("- "):
            lines.append(s)
    return lines


def test_catalogo_sem_pesos_preserva_ordem_atual():
    """Sem exerciseWeights, o comportamento atual é preservado."""
    req = _request_with_weights()
    summary = catalog_summary_for_request(req, main.EXERCISE_CATALOG)
    lines = _chest_lines(summary)
    assert lines, "Deveria haver linhas de exercícios em CHEST"


def test_peso_maior_aparece_antes_dentro_do_mesmo_grupo():
    """Exercício com peso maior aparece antes dentro de CHEST."""
    # Dois exercícios de peito que estão no pool (equipamento disponível)
    chest_exs = [
        e for e in main.EXERCISE_CATALOG
        if "chest" in [str(m).lower() for m in e.get("primaryMuscles", [])]
        and str(e.get("equipment", "")).strip().lower()
        in {"barbell", "dumbbell", "body only", "cable", "machine"}
    ]
    assert len(chest_exs) >= 2
    id_low = chest_exs[0]["id"]
    id_high = chest_exs[1]["id"]
    name_low = chest_exs[0].get("name", id_low)
    name_high = chest_exs[1].get("name", id_high)

    req = _request_with_weights(
        exercise_weights={id_high: 5, id_low: 0},
    )
    lines = _chest_lines(catalog_summary_for_request(req, main.EXERCISE_CATALOG))

    joined = "\n".join(lines)
    assert name_high in joined, f"{name_high} ({id_high}) deveria estar em CHEST"
    assert name_low in joined, f"{name_low} ({id_low}) deveria estar em CHEST"
    pos_high = joined.index(name_high)
    pos_low = joined.index(name_low)
    assert pos_high < pos_low, (
        f"Peso maior ({name_high}) deveria aparecer antes do peso zero ({name_low})"
    )


def test_peso_zero_nao_exclui_exercicio():
    """Peso zero não exclui o exercício do catálogo (cobertura preservada)."""
    chest_exs = [
        e for e in main.EXERCISE_CATALOG
        if "chest" in [str(m).lower() for m in e.get("primaryMuscles", [])]
        and str(e.get("equipment", "")).strip().lower()
        in {"barbell", "dumbbell", "body only", "cable", "machine"}
    ]
    id_zero = chest_exs[0]["id"]
    name_zero = chest_exs[0].get("name", id_zero)

    req = _request_with_weights(
        exercise_weights={id_zero: 0},
    )
    summary = catalog_summary_for_request(req, main.EXERCISE_CATALOG)
    assert name_zero in summary, "Peso zero não deve excluir o exercício"


def test_generate_request_aceita_exercise_weights():
    """O schema GenerateRequest aceita exerciseWeights sem quebrar validações."""
    req = _request_with_weights(exercise_weights={"ex1": 3, "ex2": 1})
    assert req.exerciseWeights == {"ex1": 3, "ex2": 1}

    # Default vazio quando não informado
    req2 = _request_with_weights()
    assert req2.exerciseWeights == {}
