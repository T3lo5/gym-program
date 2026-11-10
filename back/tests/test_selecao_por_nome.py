"""Testes de seleção por nome canônico e resolução determinística (H3-25).

Cobrem: resolução nome→ID, formato compacto do catálogo, cobertura do pool,
fallback por nomes não resolvíveis, preservação de IDs sintéticos.
"""

import json
import os
import tempfile

import pytest
from fastapi.testclient import TestClient

from app import database as db
from app import main
from app.plan_builder import (
    catalog_summary_for_request,
    catalog_summary_metrics,
    resolve_exercise_names,
    resolve_plan_ids,
    id_name_mismatches,
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


def _payload(**overrides):
    payload = {
        "deviceId": "d1",
        "goal": "hypertrophy",
        "daysPerWeek": 2,
        "availableEquipment": ["barbell", "dumbbell", "body only", "cable", "machine"],
        "targetMuscles": ["chest", "back", "legs", "shoulders"],
        "sessionDurationMinutes": 60,
        "currentLevel": "intermediate",
    }
    payload.update(overrides)
    return payload


def _request_for_parse(**overrides):
    data = {
        "deviceId": "d1",
        "goal": "hypertrophy",
        "daysPerWeek": 2,
        "availableEquipment": ["barbell", "dumbbell", "body only", "cable", "machine"],
        "targetMuscles": ["chest", "back", "legs", "shoulders"],
        "sessionDurationMinutes": 60,
        "currentLevel": "intermediate",
    }
    data.update(overrides)
    return main.GenerateRequest(**data)


def _valid_exercise_ids(n=6, muscles=None):
    """Retorna IDs reais do catálogo de força, distintos, opcionalmente filtrados por músculos."""
    ids = []
    seen = set()
    target_muscles = set(muscles) if muscles else None
    for ex in main.EXERCISE_CATALOG:
        ex_id = ex.get("id")
        if not ex_id or ex_id in seen:
            continue
        cat = str(ex.get("category", "")).lower()
        if cat in {"stretching", "cardio"}:
            continue
        if target_muscles:
            primaries = {str(m).lower() for m in ex.get("primaryMuscles", [])}
            if not primaries & target_muscles:
                continue
        ids.append(ex_id)
        seen.add(ex_id)
        if len(ids) >= n:
            break
    return ids


def _find_exercise_by_name(catalog, name_pt):
    """Encontra exercício pelo namePt."""
    for ex in catalog:
        if ex.get("namePt") == name_pt:
            return ex
    return None


# ---------------- Resolução por nome canônico ----------------


def test_nome_canonico_exato_preenche_exercise_id():
    """Dado exerciseName canônico listado e exerciseId vazio/ausente, preenche ID correto."""
    req = _request_for_parse()
    ex = _find_exercise_by_name(main.EXERCISE_CATALOG, "Supino Reto com Barra - Pegada Média")
    assert ex is not None, "Exercício de teste não encontrado no catálogo"
    canonical_name = ex["namePt"]
    ex_id = ex["id"]

    plan = {
        "name": "Teste",
        "goal": "hypertrophy",
        "daysPerWeek": 1,
        "days": [
            {
                "name": "Dia 1",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "isRestDay": False,
                "exercises": [
                    {
                        "exerciseName": canonical_name,
                        "exerciseId": "",
                        "exerciseOrder": 0,
                        "sets": 3,
                        "targetReps": "8-12",
                        "targetRpe": 8.0,
                        "restSeconds": 90,
                        "notes": "",
                        "isSupersetWithPrevious": False,
                        "metricType": "reps_weight",
                    }
                ],
            }
        ],
    }

    resolved_plan, drops = resolve_exercise_names(plan, main.EXERCISE_CATALOG, req)
    assert not drops, f"Não deveria ter drops: {drops}"
    got_id = resolved_plan["days"][0]["exercises"][0]["exerciseId"]
    assert got_id == ex_id, f"Esperado {ex_id}, obtido {got_id}"


def test_nome_variacao_acentuacao_case_resolve_deterministico():
    """Variações de acento/caixa/pontuação/espaço resolvem para o mesmo ID (ordem exato→fuzzy→substring)."""
    req = _request_for_parse()
    ex = _find_exercise_by_name(main.EXERCISE_CATALOG, "Supino Reto com Barra - Pegada Média")
    assert ex is not None
    ex_id = ex["id"]
    canonical = ex["namePt"]

    # Variações que devem resolver para o mesmo ID
    variations = [
        canonical.lower(),  # caixa diferente
        canonical.replace("á", "a").replace("é", "e").replace("ç", "c"),  # sem acentos
        "Supino Reto com Barra Pegada Media",  # sem pontuação/hífens
        " supino reto com barra pegada media ",  # espaços extras
    ]

    for var in variations:
        plan = {
            "name": "Teste",
            "goal": "hypertrophy",
            "daysPerWeek": 1,
            "days": [
                {
                    "name": "Dia 1",
                    "dayOrder": 0,
                    "weekday": 1,
                    "modality": "strength",
                    "isRestDay": False,
                    "exercises": [
                        {
                            "exerciseName": var,
                            "exerciseId": "",
                            "exerciseOrder": 0,
                            "sets": 3,
                            "targetReps": "8-12",
                            "targetRpe": 8.0,
                            "restSeconds": 90,
                            "notes": "",
                            "isSupersetWithPrevious": False,
                            "metricType": "reps_weight",
                        }
                    ],
                }
            ],
        }
        resolved_plan, drops = resolve_exercise_names(plan, main.EXERCISE_CATALOG, req)
        assert not drops, f"Variação '{var}' não deveria drop: {drops}"
        got_id = resolved_plan["days"][0]["exercises"][0]["exerciseId"]
        assert got_id == ex_id, f"Variação '{var}': esperado {ex_id}, obtido {got_id}"

    # Determinismo: rodar duas vezes dá mesmo resultado
    plan2 = {
        "name": "Teste",
        "goal": "hypertrophy",
        "daysPerWeek": 1,
        "days": [
            {
                "name": "Dia 1",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "isRestDay": False,
                "exercises": [
                    {
                        "exerciseName": canonical.lower(),
                        "exerciseId": "",
                        "exerciseOrder": 0,
                        "sets": 3,
                        "targetReps": "8-12",
                        "targetRpe": 8.0,
                        "restSeconds": 90,
                        "notes": "",
                        "isSupersetWithPrevious": False,
                        "metricType": "reps_weight",
                    }
                ],
            }
        ],
    }
    r1, _ = resolve_exercise_names(plan, main.EXERCISE_CATALOG, req)
    r2, _ = resolve_exercise_names(plan2, main.EXERCISE_CATALOG, req)
    assert r1["days"][0]["exercises"][0]["exerciseId"] == r2["days"][0]["exercises"][0]["exerciseId"]


def test_resolve_considera_name_namept_e_canonico_listado():
    """Resolução casa contra name (EN), namePt (PT) e canônico listado no idioma do request."""
    req_en = _request_for_parse(language="en")
    req_pt = _request_for_parse(language="pt")

    ex = _find_exercise_by_name(main.EXERCISE_CATALOG, "Supino Reto com Barra - Pegada Média")
    assert ex is not None
    ex_id = ex["id"]
    name_en = ex["name"]
    name_pt = ex["namePt"]

    # Request em EN: deve resolver pelo name EN
    plan_en = {
        "name": "Teste",
        "goal": "hypertrophy",
        "daysPerWeek": 1,
        "days": [
            {
                "name": "Dia 1",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "isRestDay": False,
                "exercises": [
                    {
                        "exerciseName": name_en,
                        "exerciseId": "",
                        "exerciseOrder": 0,
                        "sets": 3,
                        "targetReps": "8-12",
                        "targetRpe": 8.0,
                        "restSeconds": 90,
                        "notes": "",
                        "isSupersetWithPrevious": False,
                        "metricType": "reps_weight",
                    }
                ],
            }
        ],
    }
    resolved_en, drops_en = resolve_exercise_names(plan_en, main.EXERCISE_CATALOG, req_en)
    assert not drops_en
    assert resolved_en["days"][0]["exercises"][0]["exerciseId"] == ex_id

    # Request em PT: deve resolver pelo namePt
    plan_pt = {
        "name": "Teste",
        "goal": "hypertrophy",
        "daysPerWeek": 1,
        "days": [
            {
                "name": "Dia 1",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "isRestDay": False,
                "exercises": [
                    {
                        "exerciseName": name_pt,
                        "exerciseId": "",
                        "exerciseOrder": 0,
                        "sets": 3,
                        "targetReps": "8-12",
                        "targetRpe": 8.0,
                        "restSeconds": 90,
                        "notes": "",
                        "isSupersetWithPrevious": False,
                        "metricType": "reps_weight",
                    }
                ],
            }
        ],
    }
    resolved_pt, drops_pt = resolve_exercise_names(plan_pt, main.EXERCISE_CATALOG, req_pt)
    assert not drops_pt
    assert resolved_pt["days"][0]["exercises"][0]["exerciseId"] == ex_id


def test_nome_nao_resolvivel_remove_e_reconstroi_sessao():
    """Nome inexistente entre exercícios válidos → item removido, sessão reconstruída se < 3."""
    req = _request_for_parse(target_muscles=["chest", "back", "legs"])
    valid_ids = _valid_exercise_ids(6, muscles=["chest", "back", "legs"])
    assert len(valid_ids) >= 3

    plan = {
        "name": "Teste",
        "goal": "hypertrophy",
        "daysPerWeek": 1,
        "days": [
            {
                "name": "Dia 1",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "isRestDay": False,
                "exercises": [
                    {"exerciseName": "Exercício Inexistente XYZ", "exerciseId": "", "exerciseOrder": 0, "sets": 3, "targetReps": "8-12", "targetRpe": 8.0, "restSeconds": 90, "notes": "", "isSupersetWithPrevious": False, "metricType": "reps_weight"},
                    {"exerciseName": "Outro Inexistente ABC", "exerciseId": "", "exerciseOrder": 1, "sets": 3, "targetReps": "8-12", "targetRpe": 8.0, "restSeconds": 90, "notes": "", "isSupersetWithPrevious": False, "metricType": "reps_weight"},
                    {"exerciseId": valid_ids[0], "exerciseOrder": 2, "sets": 3, "targetReps": "8-12", "targetRpe": 8.0, "restSeconds": 90, "notes": "", "isSupersetWithPrevious": False, "metricType": "reps_weight"},
                ],
            }
        ],
    }

    resolved_plan, drops = resolve_exercise_names(plan, main.EXERCISE_CATALOG, req)
    assert len(drops) == 2
    assert all(d["reason"] == "unresolved_name" for d in drops)

    # Após parse_ai_plan (que chama salvage), a sessão deve ter ≥ 3 exercícios válidos
    parsed = main.parse_ai_plan(json.dumps(resolved_plan), main.EXERCISE_CATALOG, req)
    assert len(parsed["days"][0]["exercises"]) >= 3
    catalog_ids = {ex["id"] for ex in main.EXERCISE_CATALOG}
    for ex in parsed["days"][0]["exercises"]:
        assert ex["exerciseId"] in catalog_ids


def test_todos_nomes_invalidos_fallback_unresolved_ids():
    """Todos os nomes não resolvíveis E pool vazio → fallback total com unresolved_ids.

    Se há exercícios elegíveis no pool, o salvamento parcial reconstrói a sessão.
    Fallback total só ocorre quando o pool elegível está vazio.
    """
    req = _request_for_parse()
    plan = {
        "name": "Teste",
        "goal": "hypertrophy",
        "daysPerWeek": 1,
        "days": [
            {
                "name": "Dia 1",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "isRestDay": False,
                "exercises": [
                    {"exerciseName": "Inexistente 1", "exerciseId": "", "exerciseOrder": 0, "sets": 3, "targetReps": "8-12", "targetRpe": 8.0, "restSeconds": 90, "notes": "", "isSupersetWithPrevious": False, "metricType": "reps_weight"},
                    {"exerciseName": "Inexistente 2", "exerciseId": "", "exerciseOrder": 1, "sets": 3, "targetReps": "8-12", "targetRpe": 8.0, "restSeconds": 90, "notes": "", "isSupersetWithPrevious": False, "metricType": "reps_weight"},
                ],
            }
        ],
    }

    resolved_plan, drops = resolve_exercise_names(plan, main.EXERCISE_CATALOG, req)
    assert len(drops) == 2
    assert all(d["reason"] == "unresolved_name" for d in drops)

    # Com pool não-vazio, o salvamento parcial reconstrói a sessão → não há fallback total
    parsed = main.parse_ai_plan(json.dumps(resolved_plan), main.EXERCISE_CATALOG, req)
    assert len(parsed["days"][0]["exercises"]) >= 3
    catalog_ids = {ex["id"] for ex in main.EXERCISE_CATALOG}
    for ex in parsed["days"][0]["exercises"]:
        assert ex["exerciseId"] in catalog_ids


def test_fallback_total_unresolved_ids_quando_pool_vazio():
    """Pool elegível vazio + nomes inválidos → fallback total unresolved_ids."""
    # Request com equipamento que não existe no catálogo → pool vazio
    # Precisa de focusMuscles para permitir daysPerWeek=1 (modo complementar)
    req = main.GenerateRequest(
        deviceId="d1",
        goal="hypertrophy",
        daysPerWeek=1,
        availableEquipment=["equipamento_inexistente_xyz"],
        targetMuscles=["chest"],
        focusMuscles=["chest"],  # modo complementar permite 1 dia
        sessionDurationMinutes=60,
        currentLevel="intermediate",
    )
    plan = {
        "name": "Teste",
        "goal": "hypertrophy",
        "daysPerWeek": 1,
        "days": [
            {
                "name": "Dia 1",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "isRestDay": False,
                "exercises": [
                    {"exerciseName": "Inexistente 1", "exerciseId": "", "exerciseOrder": 0, "sets": 3, "targetReps": "8-12", "targetRpe": 8.0, "restSeconds": 90, "notes": "", "isSupersetWithPrevious": False, "metricType": "reps_weight"},
                ],
            }
        ],
    }

    resolved_plan, drops = resolve_exercise_names(plan, main.EXERCISE_CATALOG, req)
    assert len(drops) == 1
    assert drops[0]["reason"] == "unresolved_name"

    # Pool vazio → salvage falha → fallback total
    with pytest.raises(main.AiFailure) as excinfo:
        main.parse_ai_plan(json.dumps(resolved_plan), main.EXERCISE_CATALOG, req)
    assert excinfo.value.reason == "unresolved_ids"


def test_id_ausente_com_nome_resolvivel_valida_no_dto():
    """exerciseId ausente/vazio + exerciseName resolvível → preenche ID e valida no DTO."""
    req = _request_for_parse()
    ex = _find_exercise_by_name(main.EXERCISE_CATALOG, "Supino Reto com Barra - Pegada Média")
    assert ex is not None
    canonical_name = ex["namePt"]
    ex_id = ex["id"]

    plan = {
        "name": "Teste",
        "goal": "hypertrophy",
        "daysPerWeek": 1,
        "days": [
            {
                "name": "Dia 1",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "isRestDay": False,
                "exercises": [
                    {
                        "exerciseName": canonical_name,
                        "exerciseId": "",
                        "exerciseOrder": 0,
                        "sets": 3,
                        "targetReps": "8-12",
                        "targetRpe": 8.0,
                        "restSeconds": 90,
                        "notes": "",
                        "isSupersetWithPrevious": False,
                        "metricType": "reps_weight",
                    }
                ],
            }
        ],
    }

    resolved_plan, drops = resolve_exercise_names(plan, main.EXERCISE_CATALOG, req)
    assert not drops
    assert resolved_plan["days"][0]["exercises"][0]["exerciseId"] == ex_id

    # Deve validar no WorkoutPlanDTO (via parse_ai_plan)
    parsed = main.parse_ai_plan(json.dumps(resolved_plan), main.EXERCISE_CATALOG, req)
    assert parsed["days"][0]["exercises"][0]["exerciseId"] == ex_id
    # Verifica que o DTO aceita
    dto = main.WorkoutPlanDTO(**parsed)
    assert dto.days[0].exercises[0].exerciseId == ex_id


def test_id_valido_nome_divergente_mantem_id_name_mismatch():
    """exerciseId válido mas exerciseName claramente divergente → id_name_mismatch (remoção/reconstrução)."""
    req = _request_for_parse()
    valid_ids = _valid_exercise_ids(4, muscles=["chest", "back"])
    assert len(valid_ids) >= 2
    target_id = valid_ids[0]

    # Pega o nome oficial desse ID
    catalog_by_id = {ex["id"]: ex for ex in main.EXERCISE_CATALOG}
    official_name = catalog_by_id[target_id]["name"]

    plan = {
        "name": "Teste",
        "goal": "hypertrophy",
        "daysPerWeek": 1,
        "days": [
            {
                "name": "Dia 1",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "isRestDay": False,
                "exercises": [
                    {
                        "exerciseId": target_id,
                        "exerciseName": "Completely Different Exercise XYZ",
                        "exerciseOrder": 0,
                        "sets": 3,
                        "targetReps": "8-12",
                        "targetRpe": 8.0,
                        "restSeconds": 90,
                        "notes": "",
                        "isSupersetWithPrevious": False,
                        "metricType": "reps_weight",
                    },
                    {
                        "exerciseId": valid_ids[1],
                        "exerciseOrder": 1,
                        "sets": 3,
                        "targetReps": "8-12",
                        "targetRpe": 8.0,
                        "restSeconds": 90,
                        "notes": "",
                        "isSupersetWithPrevious": False,
                        "metricType": "reps_weight",
                    },
                ],
            }
        ],
    }

    # Primeiro resolve nomes (não deve afetar pois já tem ID)
    resolved_plan, drops = resolve_exercise_names(plan, main.EXERCISE_CATALOG, req)
    assert not drops  # resolve_exercise_names preserva ID válido existente

    # id_name_mismatches deve detectar a divergência
    mismatches = id_name_mismatches(resolved_plan, main.EXERCISE_CATALOG)
    assert any(m["exerciseId"] == target_id and m["reason"] == "id_name_mismatch" for m in mismatches)

    # parse_ai_plan deve remover o exercício divergente e reconstruir
    parsed = main.parse_ai_plan(json.dumps(resolved_plan), main.EXERCISE_CATALOG, req)
    got_ids = [ex["exerciseId"] for ex in parsed["days"][0]["exercises"]]
    assert target_id not in got_ids
    assert len(got_ids) >= 3  # Reconstrução garante mínimo


def test_resolucao_nao_escolhe_exercicio_fora_do_pool():
    """Resolução restrita ao pool elegível: nunca resolve para exercício inelegível.

    Se o nome casaria com item inelegível mas também com elegível, resolve para o elegível.
    Se só casaria com inelegível, não resolve (drop).
    """
    # Request sem 'machine' no equipamento
    req = _request_for_parse(availableEquipment=["barbell", "dumbbell", "body only"])

    # IDs do pool elegível (força + equipamento permitido)
    from app.plan_builder import _build_pool, _is_strength_exercise
    equipment = ["barbell", "dumbbell", "body only"]
    level = "intermediate"
    strict_equipment = None
    pool = _build_pool(main.EXERCISE_CATALOG, equipment, level, strict_equipment=strict_equipment)
    strength_pool = [e for e in pool if _is_strength_exercise(e)]
    eligible_ids = {ex["id"] for ex in strength_pool}

    # Testa com vários nomes de machine - verifica que se resolve, é para elegível
    machine_exercises = [ex for ex in main.EXERCISE_CATALOG if (ex.get("equipment") or "").lower() == "machine" and ex.get("namePt")]
    
    for machine_ex in machine_exercises[:5]:  # Testa primeiros 5
        plan = {
            "name": "Teste",
            "goal": "hypertrophy",
            "daysPerWeek": 1,
            "days": [
                {
                    "name": "Dia 1",
                    "dayOrder": 0,
                    "weekday": 1,
                    "modality": "strength",
                    "isRestDay": False,
                    "exercises": [
                        {
                            "exerciseName": machine_ex["namePt"],
                            "exerciseId": "",
                            "exerciseOrder": 0,
                            "sets": 3,
                            "targetReps": "8-12",
                            "targetRpe": 8.0,
                            "restSeconds": 90,
                            "notes": "",
                            "isSupersetWithPrevious": False,
                            "metricType": "reps_weight",
                        }
                    ],
                }
            ],
        }

        resolved_plan, drops = resolve_exercise_names(plan, main.EXERCISE_CATALOG, req)
        # Se resolveu (não dropou), o ID deve ser do pool elegível
        if not drops:
            resolved_id = resolved_plan["days"][0]["exercises"][0]["exerciseId"]
            assert resolved_id in eligible_ids, f"Nome '{machine_ex['namePt']}' resolveu para ID inelegível: {resolved_id}"
        # Se dropou, é unresolved_name (correto - não casou com elegível)
        else:
            assert drops[0]["reason"] == "unresolved_name"
    # Se não encontrou machine-only único, o comportamento correto é que
    # nomes compartilhados resolvem para o elegível — teste passa.


def test_sinteticos_run_mobility_nao_resolvidos_por_nome_catalogo():
    """IDs sintéticos run_*/mobility_* são ignorados pela resolução de nome do catálogo."""
    req = _request_for_parse()

    plan = {
        "name": "Teste Híbrido",
        "goal": "general",
        "daysPerWeek": 2,
        "days": [
            {
                "name": "Corrida",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "run",
                "isRestDay": False,
                "exercises": [
                    {
                        "exerciseId": "run_easy",
                        "exerciseName": "Corrida Leve",
                        "exerciseOrder": 0,
                        "sets": 1,
                        "targetReps": "",
                        "targetRpe": 7.0,
                        "restSeconds": 0,
                        "notes": "",
                        "isSupersetWithPrevious": False,
                        "metricType": "distance_duration",
                        "targetDistanceMeters": 5000,
                    }
                ],
            },
            {
                "name": "Mobilidade",
                "dayOrder": 1,
                "weekday": 1,
                "modality": "mobility",
                "isRestDay": False,
                "exercises": [
                    {
                        "exerciseId": "mobility_full",
                        "exerciseName": "Mobilidade Completa",
                        "exerciseOrder": 0,
                        "sets": 1,
                        "targetReps": "",
                        "targetRpe": 5.0,
                        "restSeconds": 0,
                        "notes": "",
                        "isSupersetWithPrevious": False,
                        "metricType": "duration",
                        "targetDurationSeconds": 600,
                    }
                ],
            },
        ],
    }

    resolved_plan, drops = resolve_exercise_names(plan, main.EXERCISE_CATALOG, req)
    assert not drops
    # IDs sintéticos preservados
    assert resolved_plan["days"][0]["exercises"][0]["exerciseId"] == "run_easy"
    assert resolved_plan["days"][1]["exercises"][0]["exerciseId"] == "mobility_full"


# ---------------- Catálogo compacto ----------------


def test_catalog_summary_compacto_lista_todos_elegiveis_sem_id_inline():
    """Formato compacto: linha ' - <nome canônico> (<eq>)', sem ID inline, todos elegíveis."""
    req = _request_for_parse(
        availableEquipment=["barbell", "dumbbell", "body only"],
        targetMuscles=["chest"],
    )
    summary = catalog_summary_for_request(req, main.EXERCISE_CATALOG)

    lines = [l for l in summary.splitlines() if l.strip()]
    # Cabeçalho com instrução de cópia exata
    assert "copie o nome exatamente como listado" in lines[0]
    assert "o backend resolve o ID" in lines[0]
    assert "não invente nomes" in lines[0]

    # Linhas de exercícios: formato ' - <nome> (<eq>)' SEM ID
    exercise_lines = [l for l in lines if l.strip().startswith("- ")]
    assert exercise_lines, "Deveria ter linhas de exercícios"
    for line in exercise_lines:
        # Linha DEVE começar com "- " (formato compacto), mas NÃO deve conter ID do catálogo
        # IDs típicos: Barbell_Bench_Press_-_Medium_Grip (underscores, maiúsculas)
        # O nome canônico não tem underscores no formato "Nome do Exercicio"
        assert "(" in line and ")" in line, f"Formato inesperado: {line}"
        # Não deve conter ID do catálogo (que tem underscores e maiúsculas no padrão ID)
        assert "_" not in line.split("(")[0].strip().split()[-1], f"Parece conter ID: {line}"

    # Deve ter mais de 8 itens (sem cap por grupo)
    chest_lines = [l for l in exercise_lines if "CHEST:" in summary.split("CHEST:")[0] + "CHEST:" or "CHEST" in summary.split(l.split("(")[0])[0]]
    # Contagem robusta: itens entre CHEST: e próximo cabeçalho
    in_chest = False
    chest_count = 0
    for l in lines:
        if l.strip() == "CHEST:":
            in_chest = True
            continue
        if in_chest and l.strip().endswith(":"):
            break
        if in_chest and l.strip().startswith("- "):
            chest_count += 1
    assert chest_count > 8, f"Esperava > 8 exercícios de chest, got {chest_count}"


def test_catalog_summary_instrui_nome_canonico_e_nao_lista_equipamento_fora():
    """Cabeçalho instrui nome canônico; equipamento não informado não aparece."""
    # Request só com body only
    req = _request_for_parse(
        availableEquipment=["body only"],
        targetMuscles=["abs"],
    )
    summary = catalog_summary_for_request(req, main.EXERCISE_CATALOG)

    # Não deve listar barbell, dumbbell, machine, cable
    for forbidden in ["(barbell", "(dumbbell", "(machine", "(cable", "(kettlebell", "(bands"]:
        assert forbidden not in summary.lower(), f"Equipamento não informado apareceu: {forbidden}"

    # Complementar (bodyweight) - equipamento estrito
    comp_req = main.GenerateRequest(
        environment="bodyweight",
        focusMuscles=["abs"],
        daysPerWeek=2,
        sessionDurationMinutes=30,
        currentLevel="intermediate",
    )
    comp_summary = catalog_summary_for_request(comp_req, main.EXERCISE_CATALOG)
    for forbidden in ["(barbell", "(dumbbell", "(machine", "(cable", "(kettlebell", "(bands"]:
        assert forbidden not in comp_summary.lower(), f"Equipamento fora do bodyweight apareceu: {forbidden}"


def test_catalog_summary_metrics_baseline():
    """Métricas do resumo compatíveis com baseline do experimento (~32k chars / ~8k tok)."""
    req = _request_for_parse()
    metrics = catalog_summary_metrics(req, main.EXERCISE_CATALOG)
    # Baseline do experimento T1: 37.819 chars / 12.809 tokens (com IDs inline)
    # Novo formato compacto deve ser menor
    assert metrics["chars"] > 0
    assert metrics["linhas"] > 0
    assert metrics["tokens_aprox"] == metrics["chars"] // 4
    # Novo formato deve ser mais compacto que o antigo com IDs
    assert metrics["chars"] < 37819, f"Resumo ainda muito grande: {metrics['chars']} chars"