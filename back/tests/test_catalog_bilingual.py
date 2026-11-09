"""Testes do catálogo bilíngue PT/EN (H3-36).

Validam o `back/app/exercise_catalog.json` COMITADO (sem rede): paridade de
IDs com o dataset original, os 3 exercícios sem tradução como `null` e os
campos de filtro ainda em inglês (`_build_pool`, `MUSCLE_ALIASES` e o filtro
do complementar comparam valores em EN).
"""

import json
from pathlib import Path

CATALOG_PATH = Path(__file__).resolve().parents[1] / "app" / "exercise_catalog.json"

EXPECTED_TOTAL = 876
EXPECTED_WITH_PT = 873
# Faltam no fork PT-BR (spec H3-36): nunca inventar tradução.
EXPECTED_NULL_IDS = {
    "Kettlebell_Halo",
    "Kettlebell_Halo_With_Overhead_Extension",
    "Kettlebell_Overhead_Triceps_Extension",
}

# Vocabulário canônico do free-exercise-db (EN). Tradução aqui quebraria
# silenciosamente o pool e os filtros do complementar (H3-16).
EQUIPMENT_EN = {
    "bands",
    "barbell",
    "body only",
    "cable",
    "dumbbell",
    "e-z curl bar",
    "exercise ball",
    "foam roll",
    "kettlebells",
    "machine",
    "medicine ball",
    "other",
}
LEVEL_EN = {"beginner", "intermediate", "expert"}
MUSCLES_EN = {
    "abdominals",
    "abductors",
    "adductors",
    "biceps",
    "calves",
    "chest",
    "forearms",
    "glutes",
    "hamstrings",
    "lats",
    "lower back",
    "middle back",
    "neck",
    "quadriceps",
    "shoulders",
    "traps",
    "triceps",
}


def _catalog():
    return json.loads(CATALOG_PATH.read_text(encoding="utf-8"))


def test_paridade_876_ids():
    catalog = _catalog()
    assert len(catalog) == EXPECTED_TOTAL
    ids = [ex["id"] for ex in catalog]
    assert len(set(ids)) == EXPECTED_TOTAL, "IDs duplicados no catálogo"
    with_pt = [ex for ex in catalog if ex.get("namePt")]
    assert len(with_pt) == EXPECTED_WITH_PT
    # namePt presente ⇒ instructionsPt também (o merge grava os dois juntos
    # quando o fork traz passos; instruções vazias no fork ficam null).
    for ex in with_pt:
        assert isinstance(ex["namePt"], str) and ex["namePt"].strip()
        instructions_pt = ex.get("instructionsPt")
        assert instructions_pt is None or (
            isinstance(instructions_pt, list) and instructions_pt
        )


def test_tres_sem_traducao_null():
    catalog = _catalog()
    by_id = {ex["id"]: ex for ex in catalog}
    assert set(by_id) - {ex["id"] for ex in catalog if ex.get("namePt")} == EXPECTED_NULL_IDS
    for ex_id in EXPECTED_NULL_IDS:
        ex = by_id[ex_id]
        assert ex.get("namePt") is None, f"{ex_id}: tradução inventada"
        assert ex.get("instructionsPt") is None, f"{ex_id}: instruções inventadas"
        # EN canônico preservado — o app cai nele quando namePt é null.
        assert ex.get("name"), f"{ex_id}: sem nome EN"


def test_campos_de_filtro_em_ingles():
    for ex in _catalog():
        # `equipment: null` existe no dataset original (alongamentos) — o
        # fallback do plano trata como "body only"; tradução é que não pode.
        equipment = ex.get("equipment")
        assert equipment is None or equipment in EQUIPMENT_EN, (
            f"{ex['id']}: equipment traduzido"
        )
        assert ex.get("level") in LEVEL_EN, f"{ex['id']}: level traduzido"
        for muscle in ex.get("primaryMuscles", []) + ex.get("secondaryMuscles", []):
            assert muscle in MUSCLES_EN, f"{ex['id']}: músculo traduzido ({muscle})"
