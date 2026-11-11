"""Geração e normalização de planos de treino.

O backend tenta usar a IA. Se a IA falhar, demorar demais ou devolver IDs que
não existem no catálogo, este módulo monta um plano local válido usando os 876
exercícios do catálogo. Assim o app sempre recebe um treino utilizável.
"""

import random
import re
from collections import defaultdict
from datetime import date, timedelta
from difflib import get_close_matches
from typing import Any, Dict, Iterable, List, Optional, Set, Tuple

from .run_templates import template_for

MUSCLE_ALIASES: Dict[str, List[str]] = {
    "back": ["lats", "middle back", "traps"],
    "legs": ["quadriceps", "hamstrings", "glutes", "calves"],
    "arms": ["biceps", "triceps"],
    "abs": ["abdominals"],
    "chest": ["chest"],
    "shoulders": ["shoulders"],
    "biceps": ["biceps"],
    "triceps": ["triceps"],
    "glutes": ["glutes"],
    "calves": ["calves"],
    "quadriceps": ["quadriceps"],
    "hamstrings": ["hamstrings"],
    "abdominals": ["abdominals"],
}

DAY_TEMPLATES: Dict[str, List[List[str]]] = {
    "full_body": [
        ["chest", "lats", "quadriceps", "shoulders", "abdominals"],
        ["lats", "chest", "hamstrings", "shoulders", "abdominals"],
        ["quadriceps", "chest", "lats", "glutes", "abdominals"],
        ["shoulders", "lats", "quadriceps", "biceps", "triceps"],
    ],
    "upper_lower": [
        ["chest", "lats", "shoulders", "biceps", "triceps"],
        ["quadriceps", "hamstrings", "glutes", "calves", "abdominals"],
    ],
    "push_pull_legs": [
        ["chest", "shoulders", "triceps"],
        ["lats", "middle back", "biceps"],
        ["quadriceps", "hamstrings", "glutes", "calves"],
    ],
    "bro_split": [
        ["chest"],
        ["lats", "middle back"],
        ["quadriceps", "hamstrings", "glutes", "calves"],
        ["shoulders", "traps"],
        ["biceps", "triceps"],
    ],
}

GOAL_LABELS = {
    "hypertrophy": "Hipertrofia",
    "strength": "Força",
    "endurance": "Resistência",
    "power": "Potência",
    "fat_loss": "Perda de gordura",
    "general": "Condicionamento geral",
}

REPS_BY_GOAL = {
    "hypertrophy": "8-12",
    "strength": "3-5",
    "endurance": "12-20",
    "power": "3-6",
    "fat_loss": "10-15",
    "general": "8-15",
}
RPE_BY_GOAL = {
    "hypertrophy": 8.0,
    "strength": 8.5,
    "endurance": 7.0,
    "power": 7.5,
    "fat_loss": 7.5,
    "general": 7.5,
}
REST_BY_GOAL = {
    "hypertrophy": 90,
    "strength": 150,
    "endurance": 60,
    "power": 120,
    "fat_loss": 60,
    "general": 90,
}
SETS_BY_GOAL = {
    "hypertrophy": 3,
    "strength": 4,
    "endurance": 3,
    "power": 4,
    "fat_loss": 3,
    "general": 3,
}

UPPER_BODY_MUSCLES = {"chest", "shoulders", "biceps", "triceps", "lats", "middle back", "traps"}
LOWER_BODY_MUSCLES = {"quadriceps", "hamstrings", "glutes", "calves"}
LOAD_EQUIPMENT = {"barbell", "dumbbell", "machine", "cable"}

STRENGTH_CATEGORIES = {"strength", "powerlifting", "olympic weightlifting"}
NON_STRENGTH_CATEGORIES = {"stretching", "cardio"}

# Número de exercícios de musculação por duração (min) -> (mín, máx).
STRENGTH_EXERCISE_COUNT = {
    30: 4,
    45: 5,
    60: 6,
    90: 8,
}

MODALITY_RANK = {"strength": 0, "run": 1, "mobility": 2}

# Split de força tradicional aplicado ciclo entre as sessões da semana:
# A = empurrar (peito/ombros/tríceps), B = puxar (costas/bíceps/traps),
# C = pernas e core (quadriceps/isquiotibiais/glutos/panturrilha/abdomen).
# Quando o usuário treina 3x força, cada sessão cai em um split diferente.
# Foco de mobilidade rotacionado quando o usuário não especifica um.
MOBILITY_FOCUS_ROTATION: List[str] = [
    "full", "back", "hips", "post_run",
]

# Split de força tradicional aplicado ciclo entre as sessões da semana:
# A = empurrar (peito/ombros/tríceps), B = puxar (costas/bíceps/traps),
# C = pernas e core (quadriceps/isquiotibiais/glutos/panturrilha/abdomen).
# Quando o usuário treina 3x força, cada sessão cai em um split diferente.
STRENGTH_SPLITS: List[List[str]] = [
    ["chest", "shoulders", "triceps"],      # A — empurrar
    ["lats", "middle back", "biceps", "traps"],  # B — puxar
    ["quadriceps", "hamstrings", "glutes", "calves", "abdominals"],  # C — pernas/core
]

# ---------------------------------------------------------------------------
# Plano complementar (H3-16): foco exclusivo e ambiente casa/peso corporal.
#
# O plano complementar é SÓ DE FORÇA (decisão de produto de 2026-11-06):
# corrida e mobilidade continuam no plano principal via híbrido (H3-11).
# `focusMuscles` presente ⇒ plano complementar: nenhum grupo de template entra
# junto e o filtro de equipamento nunca é ignorado por falta de candidatos.
# ---------------------------------------------------------------------------

# Equipamentos aceitos como "sem equipamento" no catálogo (case-insensitive).
BODYWEIGHT_EQUIPMENT: Set[str] = {"body only", "none"}

# Nº de exercícios por sessão no complementar, pela duração (15→3 … ≥45→6).
COMPLEMENTARY_MIN_EXERCISES = 3
COMPLEMENTARY_MAX_EXERCISES = 6

# Gatilho do plano B (H3-22 §"Plano B condicionado a medição"): quando o resumo
# Tier A ultrapassar este limite de chars, o Tier B passa a ser só IDs agrupados
# por músculo (auto-descritivos), cortando ~50% do prompt sem perder nenhum ID
# elegível. Medido em 2026-11-10: request amplo (8 equipamentos + 8 músculos)
# gerava 23.019 chars / 5.754 tokens e a IA levava ~74,5s contra timeout de 45s
# (fallback reason=timeout, generationTimeMs=74500). Limite conservador.
# Gatilho do plano B (H3-22 §"Plano B condicionado a medição"): quando o resumo
# Tier A ultrapassar este limite de chars, o Tier B passa a ser só IDs agrupados
# por músculo (auto-descritivos), cortando ~50% do prompt sem perder nenhum ID
# elegível. Medido em 2026-11-10: request amplo (8 equipamentos + 8 músculos)
# gerava 23.019 chars / 5.754 tokens e a IA levava ~74,5s contra timeout de 45s
# (fallback reason=timeout, generationTimeMs=74500). Limite conservador.
CATALOG_PROMPT_TIER_B_CHARS = 22000

# Rótulos pt-BR dos grupos de MUSCLE_ALIASES, para nomes de plano e sessão.
MUSCLE_LABELS_PT: Dict[str, str] = {
    "back": "Costas",
    "legs": "Pernas",
    "arms": "Braços",
    "abs": "Abdômen",
    "chest": "Peito",
    "shoulders": "Ombros",
    "biceps": "Bíceps",
    "triceps": "Tríceps",
    "glutes": "Glúteos",
    "calves": "Panturrilhas",
    "quadriceps": "Quadríceps",
    "hamstrings": "Posterior de coxa",
    "abdominals": "Abdômen",
}

ENVIRONMENT_LABELS_PT: Dict[str, str] = {
    "gym": "Academia",
    "home": "Casa",
    "bodyweight": "Casa",
}


def complementary_exercise_count(duration_minutes: int) -> int:
    """Nº de exercícios por sessão no plano complementar (3 a 6).

    15 min → 3, 20 min → 4, 30 min → 5, ≥ 45 min → 6.
    """
    if duration_minutes < 20:
        return 3
    if duration_minutes < 30:
        return 4
    if duration_minutes < 45:
        return 5
    return COMPLEMENTARY_MAX_EXERCISES


def is_complementary_request(request: Any) -> bool:
    """Plano complementar = `focusMuscles` presente (lista não vazia)."""
    return bool(list(getattr(request, "focusMuscles", []) or []))


def complementary_equipment_filter(request: Any) -> Optional[Set[str]]:
    """Conjunto estrito de equipamentos do modo complementar (H3-16).

    Retorna `None` quando o filtro estrito não se aplica (comportamento atual,
    com a regra dos ≥ 20 candidatos). Regras:
    - environment = "bodyweight": apenas {"body only", "none"};
    - environment = "home": availableEquipment ∪ {"body only", "none"};
    - foco presente (gym ou sem ambiente): segue o availableEquipment informado.
    """
    focus = list(getattr(request, "focusMuscles", []) or [])
    environment = str(getattr(request, "environment", "") or "").strip().lower()
    equipment = {
        str(item).strip().lower()
        for item in (getattr(request, "availableEquipment", []) or [])
        if str(item).strip()
    }
    if environment == "bodyweight":
        return set(BODYWEIGHT_EQUIPMENT)
    if environment == "home":
        return equipment | BODYWEIGHT_EQUIPMENT
    if focus:
        return equipment or None
    return None


def complementary_focus_label(request: Any) -> str:
    """Rótulo pt-BR do foco exclusivo (ex.: "Abdômen", "Abdômen e Glúteos")."""
    focus = list(getattr(request, "focusMuscles", []) or [])
    labels = [
        MUSCLE_LABELS_PT.get(str(muscle).strip().lower(), str(muscle))
        for muscle in focus
        if str(muscle).strip()
    ]
    return " e ".join(labels) if labels else ""


def complementary_plan_name(request: Any) -> str:
    """Nome do plano complementar: "Complementar — <foco>" ou "Complementar — Casa"."""
    label = complementary_focus_label(request)
    if label:
        return f"Complementar — {label}"
    environment = str(getattr(request, "environment", "") or "").strip().lower()
    return f"Complementar — {ENVIRONMENT_LABELS_PT.get(environment, 'Casa')}"


def _focus_muscle_set(request: Any) -> Set[str]:
    """Foco expandido por MUSCLE_ALIASES (vazio quando não há foco)."""
    return set(_expand_muscles(list(getattr(request, "focusMuscles", []) or [])))

# IDs sintéticos que a IA costuma devolver com nomes próximos. Mapeia para o
# ID estável usado pelos templates e pelo normalize_hybrid_plan.
RUN_MOBILITY_SYNONYMS: Dict[str, str] = {
    "run_easy": "run_easy",
    "run_5k_easy": "run_easy",
    "run_easy_5k": "run_easy",
    "run_walk": "run_walk",
    "run_interval": "run_interval",
    "run_intervals": "run_interval",
    "run_intervals_400m": "run_interval",
    "run_interval_400m": "run_interval",
    "run_tempo": "run_tempo",
    "run_5k_tempo": "run_tempo",
    "run_tempo_5k": "run_tempo",
    "run_long": "run_long",
    "run_long_run": "run_long",
    "mobility_full": "mobility_full",
    "mobility_full_body": "mobility_full",
    "mobility_hips": "mobility_hips",
    "mobility_back": "mobility_back",
    "mobility_post_run": "mobility_post_run",
    "mobility_postrun": "mobility_post_run",
}


def strength_exercise_count(duration_minutes: int) -> int:
    """Retorna o número de exercícios de musculação para a duração (sempre 4-8)."""
    if duration_minutes <= 30:
        return 4
    if duration_minutes <= 45:
        return 5
    if duration_minutes <= 60:
        return 6
    if duration_minutes <= 75:
        return 7
    return 8


def _is_strength_exercise(exercise: Dict[str, Any]) -> bool:
    category = str(exercise.get("category", "")).lower()
    equipment = str(exercise.get("equipment", "")).strip().lower()
    if category in STRENGTH_CATEGORIES:
        return True
    if category in NON_STRENGTH_CATEGORIES:
        return False
    return equipment in LOAD_EQUIPMENT


def _build_pool(
    catalog: List[Dict[str, Any]],
    equipment: List[str],
    level: str,
    category: Optional[str] = None,
) -> List[Dict[str, Any]]:
    pool = list(catalog)
    selected_equipment = {item.strip().lower() for item in equipment if item.strip()}
    if selected_equipment:
        filtered = [
            exercise
            for exercise in pool
            if (exercise.get("equipment") or "").strip().lower() in selected_equipment
        ]
        if len(filtered) >= 20:
            pool = filtered

    if category:
        key = category.strip().lower()
        filtered = [
            exercise
            for exercise in pool
            if str(exercise.get("category", "")).lower() == key
        ]
        if len(filtered) >= 10:
            pool = filtered

    if level == "beginner":
        filtered = [
            exercise
            for exercise in pool
            if (exercise.get("level") or "beginner").lower() in ("beginner", "intermediate")
        ]
        if len(filtered) >= 20:
            pool = filtered

    return pool


def profile_adjustments(
    plan: Dict[str, Any],
    body_weight_kg: Optional[float],
    height_cm: Optional[int],
) -> Dict[str, Any]:
    """Aplica ajustes determinísticos de IMC ao plano local.

    Regras:
    - IMC < 18.5: descanso +15s, RPE -0.5 (mín. 5.0)
    - 18.5 <= IMC < 25.0: sem mudança
    - 25.0 <= IMC < 30.0: descanso +15s
    - IMC >= 30.0: descanso +30s, -1 série (mín. 2)

    Apenas exercícios de musculação (metricType == "reps_weight") são ajustados.
    Corrida e mobilidade mantêm sets, descanso e distância inalterados.
    """
    if body_weight_kg is None or height_cm is None:
        return plan

    height_m = height_cm / 100.0
    bmi = body_weight_kg / (height_m * height_m)

    rest_adjustment = 0
    rpe_adjustment = 0.0
    sets_adjustment = 0

    if bmi < 18.5:
        rest_adjustment = 15
        rpe_adjustment = -0.5
    elif bmi >= 30.0:
        rest_adjustment = 30
        sets_adjustment = -1
    elif bmi >= 25.0:
        rest_adjustment = 15

    for day in plan.get("days", []):
        for exercise in day.get("exercises", []):
            if exercise.get("metricType", "reps_weight") != "reps_weight":
                continue
            exercise["restSeconds"] = exercise.get("restSeconds", 90) + rest_adjustment
            if rpe_adjustment != 0:
                current_rpe = exercise.get("targetRpe", 8.0)
                exercise["targetRpe"] = max(5.0, current_rpe + rpe_adjustment)
            if sets_adjustment != 0:
                current_sets = exercise.get("sets", 3)
                exercise["sets"] = max(2, current_sets + sets_adjustment)

    return plan


def suggest_initial_load(
    plan: Dict[str, Any],
    body_weight_kg: Optional[float],
) -> Dict[str, Any]:
    """Acrescenta sugestão de carga inicial nos notes dos exercícios compatíveis."""
    if body_weight_kg is None:
        return plan

    for day in plan.get("days", []):
        for exercise in day.get("exercises", []):
            equipment = (exercise.get("equipment") or "").strip().lower()
            if equipment not in LOAD_EQUIPMENT:
                continue

            muscles = [str(m).lower() for m in exercise.get("primaryMuscles", [])]
            multiplier: Optional[float] = None
            if any(m in UPPER_BODY_MUSCLES for m in muscles):
                multiplier = 0.25
            elif any(m in LOWER_BODY_MUSCLES for m in muscles):
                multiplier = 0.5

            if multiplier is None:
                continue

            load_kg = max(2.5, round(body_weight_kg * multiplier / 2.5) * 2.5)
            suggestion = f"Carga inicial sugerida: {load_kg} kg (ajuste conforme percepção)."
            existing = exercise.get("notes", "")
            exercise["notes"] = f"{existing} {suggestion}".strip() if existing else suggestion

    return plan


def normalize(value: str) -> str:
    return re.sub(r"[^a-z0-9]", "", (value or "").lower())


def catalog_index(catalog: List[Dict[str, Any]]) -> Tuple[Dict[str, Dict[str, Any]], Dict[str, str]]:
    by_id = {exercise["id"]: exercise for exercise in catalog}
    lookup: Dict[str, str] = {}
    for exercise in catalog:
        lookup.setdefault(normalize(exercise["id"]), exercise["id"])
        lookup.setdefault(normalize(exercise.get("name", "")), exercise["id"])
    return by_id, lookup


def _build_name_resolution_index(
    catalog: List[Dict[str, Any]],
    language: str,
    eligible_pool: Optional[List[Dict[str, Any]]] = None,
) -> Tuple[Dict[str, str], Dict[str, int]]:
    """Constrói índice para resolução nome→ID.

    Chaves normalizadas: nome canônico listado no idioma do request, name, namePt.
    Valor: exerciseId. Ordem estável do catálogo para tie-break (índice menor = prioridade).
    Se `eligible_pool` fornecido, restringe ao pool elegível do request.
    """
    pool = eligible_pool if eligible_pool is not None else catalog
    eligible_ids = {ex["id"] for ex in pool}
    index: Dict[str, str] = {}
    order: Dict[str, int] = {}

    for idx, exercise in enumerate(catalog):
        ex_id = exercise["id"]
        if ex_id not in eligible_ids:
            continue
        # Ordem estável: primeiro a aparecer no catálogo ganha no empate
        if ex_id not in order:
            order[ex_id] = idx

        # Nome canônico listado no idioma do request
        canonical = _localized(exercise, "namePt", exercise.get("name", ex_id), language)
        for key in (canonical, exercise.get("name", ""), exercise.get("namePt", "")):
            if key:
                nkey = normalize(key)
                if nkey and nkey not in index:
                    index[nkey] = ex_id

    return index, order


def resolve_exercise_names(
    plan: Dict[str, Any],
    catalog: List[Dict[str, Any]],
    request: Any,
) -> Tuple[Dict[str, Any], List[Dict[str, Any]]]:
    """Resolve exerciseName → exerciseId usando o nome canônico listado.

    Ordem de resolução (determinística):
    1. Match exato normalizado contra canônico listado / name / namePt do pool elegível.
    2. Fuzzy (get_close_matches, cutoff 0.72) sobre chaves normalizadas.
    3. Substring normalizada (containment).
    Empate → primeira ocorrência na ordem estável do catálogo.

    Retorna (plano_atualizado, drops) onde drops = lista de dicts com
    exerciseName, exerciseId (se havia), reason (unresolved_name), declared.
    IDs sintéticos run_*/mobility_* são ignorados (não passam por resolução de nome).
    """
    language = request_language(request)
    equipment = list(getattr(request, "availableEquipment", []) or [])
    level = str(getattr(request, "currentLevel", "intermediate") or "intermediate")
    strict_equipment = complementary_equipment_filter(request)
    pool = _build_pool(catalog, equipment, level, strict_equipment=strict_equipment)
    strength_pool = [e for e in pool if _is_strength_exercise(e)]

    index, order = _build_name_resolution_index(catalog, language, strength_pool)
    index_keys = list(index.keys())

    drops: List[Dict[str, Any]] = []

    for day in plan.get("days", []):
        kept: List[Dict[str, Any]] = []
        for exercise in day.get("exercises", []):
            ex_id = str(exercise.get("exerciseId", "")).strip()
            ex_name = str(exercise.get("exerciseName", "")).strip()

            low = ex_id.lower()
            if low.startswith("run_") or low.startswith("mobility_"):
                kept.append(exercise)
                continue

            # Se já tem ID válido no catálogo completo, preserva (id_name_mismatch
            # e validação de pool são feitas depois em resolve_plan_ids/id_name_mismatches)
            by_id_catalog = {ex["id"]: ex for ex in catalog if _is_strength_exercise(ex)}
            if ex_id and ex_id in by_id_catalog:
                kept.append(exercise)
                continue

            # Tenta resolver pelo exerciseName (apenas se ID ausente/inválido no catálogo)
            # Restringe resolução ao pool elegível do request
            resolved_id: Optional[str] = None
            if ex_name:
                nname = normalize(ex_name)
                if nname:
                    # 1) Match exato normalizado
                    resolved_id = index.get(nname)

                if not resolved_id and nname:
                    # 2) Fuzzy cutoff 0.72
                    matches = get_close_matches(nname, index_keys, n=1, cutoff=0.72)
                    if matches:
                        resolved_id = index[matches[0]]

                if not resolved_id and nname:
                    # 3) Substring normalizada
                    for key in index_keys:
                        if nname in key or key in nname:
                            resolved_id = index[key]
                            break

            if resolved_id:
                exercise["exerciseId"] = resolved_id
                kept.append(exercise)
            else:
                # Não resolvível → drop com motivo
                # Só dropa se não tinha ID válido no catálogo (já tratado acima)
                drops.append({
                    "exerciseName": ex_name,
                    "exerciseId": ex_id or None,
                    "reason": "unresolved_name",
                    "declared": ex_name,
                })
                # Não adiciona ao kept (remove do plano)

        day["exercises"] = kept

    return plan, drops


def resolve_plan_ids(
    plan: Dict[str, Any], catalog: List[Dict[str, Any]]
) -> Tuple[Dict[str, Any], List[str]]:
    """Tenta corrigir IDs devolvidos pela IA para IDs reais do catálogo.

    IDs sintéticos de corrida (`run_*`) e mobilidade (`mobility_*`) são aceitos
    como estão: não existem no catálogo e não devem ser resolvidos nem
    sinalizados como pendentes. Apenas exercícios de musculação são validados.

    H3-22: exercício não resolvível é REMOVIDO do dia (salvamento parcial) e seu
    ID original entra em `dropped`. Não é mais fatal — o chamador decide se o
    plano ainda é aproveitável.
    """
    by_id, lookup = catalog_index(catalog)
    dropped: List[str] = []
    lookup_keys = list(lookup.keys())

    for day in plan.get("days", []):
        kept: List[Dict[str, Any]] = []
        for exercise in day.get("exercises", []):
            raw = str(exercise.get("exerciseId", "")).strip()
            if not raw:
                continue

            # IDs sintéticos de corrida/mobilidade não existem no catálogo:
            # aceitar como estão (case-insensitive) e mapear variantes próximas
            # da IA para o ID estável (ex.: Run_5K_Easy → run_easy).
            low = raw.lower()
            if low.startswith("run_") or low.startswith("mobility_"):
                exercise["exerciseId"] = RUN_MOBILITY_SYNONYMS.get(low, low)
                kept.append(exercise)
                continue

            if raw in by_id:
                kept.append(exercise)
                continue

            normalized = normalize(raw)
            candidate = lookup.get(normalized) if normalized else None

            if not candidate and normalized:
                matches = get_close_matches(normalized, lookup_keys, n=1, cutoff=0.72)
                if matches:
                    candidate = lookup[matches[0]]

            if not candidate and normalized:
                for key in lookup_keys:
                    if normalized in key or key in normalized:
                        candidate = lookup[key]
                        break

            if candidate:
                exercise["exerciseId"] = candidate
                kept.append(exercise)
            else:
                dropped.append(raw)
                # H3-22: remove o exercício inválido (salvamento parcial).

        day["exercises"] = kept

    return plan, dropped


def request_language(request: Any) -> str:
    """Idioma do conteúdo do plano (H3-36): 'pt' só quando o request pede;
    ausente/inválido ⇒ 'en' (comportamento atual preservado)."""
    return "pt" if str(getattr(request, "language", None) or "").strip().lower() == "pt" else "en"


def _localized(
    source: Dict[str, Any],
    pt_key: str,
    en_value: Any,
    language: str,
) -> Any:
    """Valor no idioma do request (H3-36). Tradução ausente/vazia nunca é
    inventada: `namePt`/`instructionsPt` null caem para o EN canônico."""
    if language == "pt":
        pt_value = source.get(pt_key)
        if pt_value:
            return pt_value
    return en_value


def enrich_plan(
    plan: Dict[str, Any],
    catalog: List[Dict[str, Any]],
    language: str = "en",
) -> Dict[str, Any]:
    """Adiciona nome, músculos e equipamento para o app não precisar do catálogo.

    `language="pt"` (H3-36): `exerciseName`/`instructions` vêm de
    `namePt`/`instructionsPt` quando existem; sem tradução, EN. O
    `exerciseId` nunca muda — é a chave em todo o sistema.
    """
    by_id, _ = catalog_index(catalog)
    for day in plan.get("days", []):
        for exercise in day.get("exercises", []):
            source = by_id.get(exercise.get("exerciseId", ""))
            if source:
                exercise["exerciseName"] = _localized(
                    source, "namePt", source.get("name", exercise.get("exerciseId", "")), language
                )
                exercise["primaryMuscles"] = source.get("primaryMuscles", [])
                exercise["equipment"] = source.get("equipment") or "body only"
                exercise["instructions"] = _localized(
                    source, "instructionsPt", source.get("instructions", []), language
                )
                exercise["images"] = source.get("images", [])
            else:
                exercise.setdefault("exerciseName", exercise.get("exerciseId", ""))
                exercise.setdefault("primaryMuscles", [])
                exercise.setdefault("equipment", "body only")
                exercise.setdefault("instructions", [])
                exercise.setdefault("images", [])
    return plan


def catalog_summary_for_request(request: Any, catalog: List[Dict[str, Any]]) -> str:
    """Resumo do catálogo para o prompt da IA, filtrado pelo request (H3-25/H3-22-B).

    Formato compacto por nome canônico listado no idioma do request:
      - <nome canônico> (<eq>)
    Sem ID inline. Cabeçalho instruindo a IA a copiar o nome exatamente como listado;
    o backend resolve o ID. Mantém TODOS os exercícios do pool elegível, sem cap por
    grupo e sem equipamento não informado. Ordem por relevância (foco/target primeiro).

    Plano B (dois níveis, H3-22 §"Plano B condicionado a medição"): quando o resumo
    Tier A ultrapassar `CATALOG_PROMPT_TIER_B_CHARS`, o Tier B passa a ser só IDs
    agrupados por músculo (auto-descritivos), com instrução explícita de preferir o
    Tier A. Propriedade obrigatória: **nenhum ID elegível sai do prompt** — o que
    muda é o nível de detalhe, nunca a cobertura. O gatilho disparou em 2026-11-10
    (request amplo: 23.019 chars, IA levava ~74,5s contra timeout de 45s).
    """
    equipment = list(getattr(request, "availableEquipment", []) or [])
    level = str(getattr(request, "currentLevel", "intermediate") or "intermediate")
    strict_equipment = complementary_equipment_filter(request)
    pool = _build_pool(catalog, equipment, level, strict_equipment=strict_equipment)
    strength_pool = [e for e in pool if _is_strength_exercise(e)]

    focus = list(getattr(request, "focusMuscles", []) or [])
    target = list(getattr(request, "targetMuscles", []) or [])
    weights: Dict[str, int] = dict(getattr(request, "exerciseWeights", None) or {})

    # Agrupa por músculo primário (primeiro primaryMuscle relevante).
    by_muscle: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
    for exercise in strength_pool:
        primaries = [str(m).lower() for m in exercise.get("primaryMuscles", [])]
        if not primaries:
            by_muscle["other"].append(exercise)
            continue
        for muscle in primaries:
            by_muscle[muscle].append(exercise)

    if focus:
        # Complementar (H3-16): foco exclusivo — só grupos do foco.
        ordered_muscles = [m for m in _expand_muscles(focus) if m in by_muscle]
    else:
        # Principal: grupos do target/foco primeiro, demais grupos depois.
        ordered_muscles: List[str] = []
        for m in _expand_muscles(target):
            if m in by_muscle and m not in ordered_muscles:
                ordered_muscles.append(m)
        for m in sorted(by_muscle.keys()):
            if m not in ordered_muscles:
                ordered_muscles.append(m)

    language = request_language(request)

    # --- Tier A (detalhado): nomes canônicos + equipamento --------------------
    # Sem cap inicial: montamos o Tier A completo (todos os exercícios do pool
    # elegível, cada um em TODOS os grupos que atende, como um personal faria).
    # Só depois verificamos se ultrapassou o limite confortável do modelo
    # (gatilho do plano B, H3-22): em caso positivo, recortamos para o cap por
    # músculo e o restante vai para o Tier B como IDs. Propriedade obrigatória:
    # NENHUM ID elegível sai do prompt.
    tier_a_lines: List[str] = []
    tier_a_ids: Set[str] = set()

    def _equip_priority(ex: Dict[str, Any]) -> int:
        return 0 if str(ex.get("equipment", "")).strip().lower() in LOAD_EQUIPMENT else 1

    def _weight_sort_key(ex: Dict[str, Any]) -> tuple:
        # Peso de uso primeiro (maior = mais relevante), depois equipamento.
        return (-weights.get(ex["id"], 0), _equip_priority(ex))

    for muscle in ordered_muscles:
        exercises = by_muscle.get(muscle) or []
        if not exercises:
            continue
        tier_a_lines.append(f"\n{muscle.upper()}:")
        for ex in sorted(exercises, key=_weight_sort_key):
            if ex["id"] in tier_a_ids:
                continue
            eq = (ex.get("equipment") or "body only").strip()
            name = _localized(ex, "namePt", ex.get("name", ex["id"]), language)
            tier_a_lines.append(f"  - {name} ({eq})")
            tier_a_ids.add(ex["id"])

    tier_a = "\n".join(tier_a_lines)
    tier_a_chars = len(tier_a)

    header = (
        "CATÁLOGO DE EXERCÍCIOS VÁLIDOS (copie o nome exatamente como listado; "
        "o backend resolve o ID; não invente nomes):"
    )

# Gatilho do plano B (H3-22 §"Plano B condicionado a medição"): o Tier B
    # é exibido quando houver exercícios além do cap por músculo (rest) ou
    # quando o Tier A sozinho estourar o limite confortável do modelo (medido
    # em 2026-11-10: request amplo gerava 23.019 chars e a IA levava ~74,5s
    # contra timeout de 45s). Propriedade obrigatória: **nenhum ID elegível
    # sai do prompt** — o que muda é o nível de detalhe, nunca a cobertura.
    if tier_a_chars > CATALOG_PROMPT_TIER_B_CHARS:
        # Recorta o Tier A para 8 detalhados por músculo e o restante vai para
        # o Tier B como IDs.
        tier_a_per_muscle = 8
        tier_a_lines: List[str] = []
        tier_a_ids: Set[str] = set()
        tier_b_lines: List[str] = []
        seen_b: Set[str] = set()

        for muscle in ordered_muscles:
            exercises = by_muscle.get(muscle) or []
            if not exercises:
                continue
            tier_a_lines.append(f"\n{muscle.upper()}:")
            for ex in sorted(exercises, key=_weight_sort_key)[:tier_a_per_muscle]:
                if ex["id"] in tier_a_ids:
                    continue
                tier_a_ids.add(ex["id"])
                eq = (ex.get("equipment") or "body only").strip()
                name = _localized(ex, "namePt", ex.get("name", ex["id"]), language)
                tier_a_lines.append(f"  - {name} ({eq})")
            rest = sorted(exercises, key=_weight_sort_key)[tier_a_per_muscle:]
            if rest:
                tier_b_lines.append(f"\n{muscle.upper()}:")
                for ex in rest:
                    if ex["id"] in tier_a_ids or ex["id"] in seen_b:
                        continue
                    seen_b.add(ex["id"])
                    tier_b_lines.append(f"  - {ex['id']}")

        # --- Tier B (índice): TODO o restante do pool elegível, só IDs -----------
        # Construído a partir do strength_pool completo (não de by_muscle), para
        # cobrir também exercícios cujos músculos primários não entraram em
        # ordered_muscles (ex.: equipamento não informado que `_build_pool` incluiu
        # pela regra dos ≥20 candidatos). Cada ID aparece UMA única vez, no
        # primeiro grupo relevante. Propriedade obrigatória do plano B: NENHUM ID
        # elegível sai do prompt.
        rest_pool = [e for e in strength_pool if e["id"] not in tier_a_ids]
        if rest_pool:
            by_muscle_rest: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
            for ex in rest_pool:
                if ex["id"] in seen_b:
                    continue
                seen_b.add(ex["id"])
                primaries = [str(m).lower() for m in ex.get("primaryMuscles", [])]
                keys = [p for p in primaries if p in ordered_muscles] or (
                    [primaries[0]] if primaries else ["other"]
                )
                by_muscle_rest[keys[0]].append(ex)
            for muscle in ordered_muscles:
                exercises = by_muscle_rest.get(muscle) or []
                if not exercises:
                    continue
                tier_b_lines.append(f"\n{muscle.upper()}:")
                for ex in sorted(exercises, key=lambda e: e["id"]):
                    tier_b_lines.append(f"  - {ex['id']}")
            # Grupos que não estavam em ordered_muscles (ex.: "other").
            for muscle in sorted(by_muscle_rest.keys()):
                if muscle in ordered_muscles:
                    continue
                exercises = by_muscle_rest.get(muscle) or []
                if not exercises:
                    continue
                tier_b_lines.append(f"\n{muscle.upper()}:")
                for ex in sorted(exercises, key=lambda e: e["id"]):
                    tier_b_lines.append(f"  - {ex['id']}")

        tier_a = "\n".join(tier_a_lines)
        if tier_b_lines:
            tier_b = "\n".join(tier_b_lines)
            tier_b_header = (
                "\n\nÍNDICE DE EXERCÍCIOS COMPLEMENTARES (use como menu de variedade do "
                "split; prefira sempre os nomes do bloco acima quando o músculo estiver "
                "listado lá. Estes IDs são auto-descritivos e também válidos):"
            )
            return f"{header}{tier_a}{tier_b_header}{tier_b}"

    return f"{header}{tier_a}"


def catalog_summary_metrics(request: Any, catalog: List[Dict[str, Any]]) -> Dict[str, int]:
    """Métricas do resumo do catálogo para log/observabilidade.

    Retorna dict com: chars, linhas, tokens_aprox (chars/4).
    """
    summary = catalog_summary_for_request(request, catalog)
    return {
        "chars": len(summary),
        "linhas": len(summary.splitlines()),
        "tokens_aprox": len(summary) // 4,
    }


def build_shortlist(
    request: Any,
    catalog: List[Dict[str, Any]],
    per_muscle: int = 12,
) -> List[Dict[str, Any]]:
    """Constrói shortlist determinística de ~per_muscle exercícios por grupo muscular.

    Usa o pool elegível do request (equipamento, nível, foco/complementar) e seleciona
    até `per_muscle` exercícios por primaryMuscle, priorizando:
    1. Exercícios do foco/target do request
    2. Equipamentos de carga (barbell, dumbbell, machine, cable) > body only
    3. Nível beginner/intermediate > advanced
    4. Ordem estável do catálogo (tie-break)

    Retorna lista plana de exercícios (cada exercício aparece uma vez, no primeiro
    músculo primário que o qualifica).
    """
    equipment = list(getattr(request, "availableEquipment", []) or [])
    level = str(getattr(request, "currentLevel", "intermediate") or "intermediate")
    strict_equipment = complementary_equipment_filter(request)
    pool = _build_pool(catalog, equipment, level, strict_equipment=strict_equipment)
    strength_pool = [e for e in pool if _is_strength_exercise(e)]

    focus = list(getattr(request, "focusMuscles", []) or [])
    target = list(getattr(request, "targetMuscles", []) or [])

    # Agrupa por músculo primário (primeiro primaryMuscle relevante).
    by_muscle: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
    for exercise in strength_pool:
        primaries = [str(m).lower() for m in exercise.get("primaryMuscles", [])]
        if not primaries:
            by_muscle["other"].append(exercise)
            continue
        for muscle in primaries:
            by_muscle[muscle].append(exercise)

    # Ordem dos grupos: foco/target primeiro, depois alfabético.
    if focus:
        ordered_muscles = [m for m in _expand_muscles(focus) if m in by_muscle]
    else:
        ordered_muscles = []
        for m in _expand_muscles(target):
            if m in by_muscle and m not in ordered_muscles:
                ordered_muscles.append(m)
        for m in sorted(by_muscle.keys()):
            if m not in ordered_muscles:
                ordered_muscles.append(m)

    # Prioridade de equipamento: carga > body only
    def equip_priority(ex: Dict[str, Any]) -> int:
        eq = str(ex.get("equipment", "")).strip().lower()
        if eq in LOAD_EQUIPMENT:
            return 0
        return 1

    # Prioridade de nível: beginner/intermediate > advanced
    def level_priority(ex: Dict[str, Any]) -> int:
        lvl = str(ex.get("level", "intermediate")).lower()
        if lvl in ("beginner", "intermediate"):
            return 0
        return 1

    shortlist: List[Dict[str, Any]] = []
    used_ids: Set[str] = set()

    for muscle in ordered_muscles:
        exercises = by_muscle.get(muscle) or []
        if not exercises:
            continue
        # Ordena por prioridade (equip, nível, ordem catálogo)
        exercises_sorted = sorted(
            exercises,
            key=lambda ex: (
                equip_priority(ex),
                level_priority(ex),
                catalog.index(ex) if ex in catalog else 999999,
            ),
        )
        count = 0
        for ex in exercises_sorted:
            if ex["id"] in used_ids:
                continue
            shortlist.append(ex)
            used_ids.add(ex["id"])
            count += 1
            if count >= per_muscle:
                break

    return shortlist


def shortlist_summary_for_request(
    request: Any,
    catalog: List[Dict[str, Any]],
    per_muscle: int = 12,
) -> str:
    """Resumo da shortlist para o prompt da IA (T7).

    Formato compacto com ID + nome canônico + equipamento:
      - <ID> — <nome canônico> (<eq>)
    Cabeçalho instruindo a IA a usar APENAS estes IDs.
    """
    shortlist = build_shortlist(request, catalog, per_muscle)
    language = request_language(request)

    # Agrupa por músculo primário para exibição
    by_muscle: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
    for exercise in shortlist:
        primaries = [str(m).lower() for m in exercise.get("primaryMuscles", [])]
        if not primaries:
            by_muscle["other"].append(exercise)
            continue
        by_muscle[primaries[0]].append(exercise)

    focus = list(getattr(request, "focusMuscles", []) or [])
    target = list(getattr(request, "targetMuscles", []) or [])

    if focus:
        ordered_muscles = [m for m in _expand_muscles(focus) if m in by_muscle]
    else:
        ordered_muscles = []
        for m in _expand_muscles(target):
            if m in by_muscle and m not in ordered_muscles:
                ordered_muscles.append(m)
        for m in sorted(by_muscle.keys()):
            if m not in ordered_muscles:
                ordered_muscles.append(m)

    lines = [
        "SHORTLIST DE EXERCÍCIOS VÁLIDOS (use APENAS estes IDs exatos; "
        "não invente IDs nem nomes fora desta lista):"
    ]
    for muscle in ordered_muscles:
        exercises = by_muscle.get(muscle) or []
        if not exercises:
            continue
        lines.append(f"\n{muscle.upper()}:")
        for ex in exercises:
            eq = (ex.get("equipment") or "body only").strip()
            name = _localized(ex, "namePt", ex.get("name", ex["id"]), language)
            lines.append(f"  - {ex['id']} — {name} ({eq})")
    return "\n".join(lines)


def shortlist_summary_metrics(
    request: Any, catalog: List[Dict[str, Any]], per_muscle: int = 12
) -> Dict[str, int]:
    """Métricas do resumo da shortlist para log/observabilidade."""
    summary = shortlist_summary_for_request(request, catalog, per_muscle)
    return {
        "chars": len(summary),
        "linhas": len(summary.splitlines()),
        "tokens_aprox": len(summary) // 4,
        "exercises": len(build_shortlist(request, catalog, per_muscle)),
    }


def id_name_mismatches(plan: Dict[str, Any], catalog: List[Dict[str, Any]]) -> List[Dict[str, str]]:
    """Detecta pares exerciseId↔exerciseName divergentes (H3-22).

    Para cada exercício com ID resolvido e nome declarado pela IA, confere o par
    contra o catálogo. Só marca divergência quando o nome declarado NÃO
    corresponde a nenhuma variação próxima do nome oficial do ID (mesma
    normalização/fuzzy de `resolve_plan_ids`); nome abreviado/traduzido que faça
    match tolerante não é divergência. Retorna lista de dicts com
    `exerciseId`, `declared`, `official`, `reason=id_name_mismatch`.
    IDs sintéticos run_/mobility_ são ignorados.
    """
    by_id, _ = catalog_index(catalog)
    _, lookup = catalog_index(catalog)
    lookup_keys = list(lookup.keys())
    mismatches: List[Dict[str, str]] = []

    for day in plan.get("days", []):
        for exercise in day.get("exercises", []):
            ex_id = str(exercise.get("exerciseId", "")).strip()
            declared = str(exercise.get("exerciseName", "")).strip()
            if not ex_id or not declared:
                continue
            low = ex_id.lower()
            if low.startswith("run_") or low.startswith("mobility_"):
                continue
            source = by_id.get(ex_id)
            if not source:
                continue
            # H3-36: mapa duplo — o nome declarado pode ser o EN canônico OU o
            # PT do mesmo ID; sem isso, todo plano em PT viraria falso positivo.
            official_names = [str(source.get("name", "")).strip()]
            name_pt = str(source.get("namePt") or "").strip()
            if name_pt and name_pt not in official_names:
                official_names.append(name_pt)
            if not any(official_names):
                continue

            n_declared = normalize(declared)
            if not n_declared:
                continue
            # Match tolerante por candidato: igual normalizado, contido, ou
            # fuzzy próximo.
            matched = False
            for official in official_names:
                n_official = normalize(official)
                if not n_official:
                    continue
                if n_declared == n_official or n_declared in n_official or n_official in n_declared:
                    matched = True
                    break
                if get_close_matches(n_declared, [n_official], n=1, cutoff=0.72):
                    matched = True
                    break
            if matched:
                continue
            # Também aceita se o nome declarado resolve para o MESMO ID no índice
            # global (ex.: variação/tradução que o fuzzy do catálogo já conhece).
            resolved_declared = lookup.get(n_declared)
            if not resolved_declared and lookup_keys:
                matches = get_close_matches(n_declared, lookup_keys, n=1, cutoff=0.72)
                if matches:
                    resolved_declared = lookup[matches[0]]
            if resolved_declared == ex_id:
                continue

            mismatches.append(
                {
                    "exerciseId": ex_id,
                    "declared": declared,
                    "official": official_names[0],
                    "reason": "id_name_mismatch",
                }
            )
    return mismatches


def review_date(review_after_weeks: int) -> str:
    return (date.today() + timedelta(weeks=review_after_weeks)).isoformat()


def _expand_muscles(muscles: List[str]) -> List[str]:
    expanded: List[str] = []
    for muscle in muscles:
        key = muscle.strip().lower()
        for item in MUSCLE_ALIASES.get(key, [key]):
            if item not in expanded:
                expanded.append(item)
    return expanded


def _build_pool(
    catalog: List[Dict[str, Any]],
    equipment: List[str],
    level: str,
    strict_equipment: Optional[Set[str]] = None,
) -> List[Dict[str, Any]]:
    """Pool de exercícios do catálogo.

    `strict_equipment` (modo complementar, H3-16): quando informado, o filtro de
    equipamento é APLICADO SEMPRE — a regra dos ≥ 20 candidatos nunca o ignora.
    Sem ele, vale o comportamento histórico do plano principal.
    """
    pool = list(catalog)
    if strict_equipment is not None:
        selected = {str(item).strip().lower() for item in strict_equipment if str(item).strip()}
        if selected:
            pool = [
                exercise
                for exercise in pool
                if (exercise.get("equipment") or "").strip().lower() in selected
            ]
    else:
        selected_equipment = {item.strip().lower() for item in equipment if item.strip()}
        if selected_equipment:
            filtered = [
                exercise
                for exercise in pool
                if (exercise.get("equipment") or "").strip().lower() in selected_equipment
            ]
            if len(filtered) >= 20:
                pool = filtered

    if level == "beginner":
        filtered = [
            exercise
            for exercise in pool
            if (exercise.get("level") or "beginner").lower() in ("beginner", "intermediate")
        ]
        if len(filtered) >= 20:
            pool = filtered

    return pool


def _pick_exercise(
    pool: List[Dict[str, Any]],
    muscle: str,
    rng: random.Random,
    used: set,
    strict: bool = False,
) -> Optional[Dict[str, Any]]:
    candidates = [
        exercise
        for exercise in pool
        if exercise.get("id") not in used
        and muscle in [str(m).lower() for m in exercise.get("primaryMuscles", [])]
    ]
    # Modo estrito (foco exclusivo, H3-16): sem candidato do grupo ⇒ nenhum
    # exercício fora do foco para "completar" a sessão.
    if not candidates and not strict:
        candidates = [exercise for exercise in pool if exercise.get("id") not in used]
    if not candidates:
        return None
    chosen = rng.choice(candidates)
    return chosen


def _day_groups(
    request: Any, day_index: int, total_days: int
) -> List[str]:
    # Foco exclusivo (H3-16): os grupos são exatamente o foco expandido —
    # nenhum grupo de template do split entra junto.
    focus = _expand_muscles(list(getattr(request, "focusMuscles", []) or []))
    if focus:
        return focus

    preferred = getattr(request, "preferredSplit", None) or "full_body"
    templates = DAY_TEMPLATES.get(preferred, DAY_TEMPLATES["full_body"])
    template = templates[day_index % len(templates)]

    groups = _expand_muscles(list(getattr(request, "targetMuscles", []) or []))
    for group in template:
        if group not in groups:
            groups.append(group)

    # Mantém o treino curto: no máximo 6 grupos por dia.
    return groups[:6]


def build_local_plan(request: Any, catalog: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Monta um plano válido usando somente o catálogo, sem IA."""
    focus = _expand_muscles(list(getattr(request, "focusMuscles", []) or []))
    days_per_week = int(getattr(request, "daysPerWeek", 4))
    if focus:
        days_per_week = max(1, min(6, days_per_week))
    else:
        days_per_week = max(2, min(6, days_per_week))
    goal = getattr(request, "goal", "hypertrophy")
    level = getattr(request, "currentLevel", "intermediate")
    equipment = list(getattr(request, "availableEquipment", []) or [])
    duration = int(getattr(request, "sessionDurationMinutes", 60))
    rng = random.Random()

    strict_equipment = complementary_equipment_filter(request) if focus else None
    pool = _build_pool(catalog, equipment, level, strict_equipment=strict_equipment)
    if strict_equipment is None and len(pool) < 10:
        pool = list(catalog)

    if focus:
        # Pool efetivo do complementar: força ∩ foco. Vazio ⇒ erro explícito
        # (o endpoint converte em 422), nunca um plano cheio de exercício fora do foco.
        focus_set = set(focus)
        pool = [
            exercise
            for exercise in pool
            if _is_strength_exercise(exercise)
            and focus_set & {str(m).lower() for m in exercise.get("primaryMuscles", [])}
        ]
        if len(pool) < COMPLEMENTARY_MIN_EXERCISES:
            raise ValueError(
                "Nenhum exercício compatível com o foco/ambiente informado. "
                "Ajuste o foco ou o ambiente e tente de novo."
            )

    days: List[Dict[str, Any]] = []
    day_letters = "ABCDEF"

    for day_index in range(days_per_week):
        groups = _day_groups(request, day_index, days_per_week)
        used: set = set()
        exercises: List[Dict[str, Any]] = []
        # Complementar: nº de exercícios pela duração (3 a 6). Sem foco, um por grupo.
        target_count = (
            min(complementary_exercise_count(duration), len(pool))
            if focus
            else len(groups)
        )

        for group in groups:
            if len(exercises) >= target_count:
                break
            exercise = _pick_exercise(pool, group, rng, used, strict=bool(focus))
            if not exercise:
                continue
            used.add(exercise["id"])
            exercises.append(
                {
                    "exerciseId": exercise["id"],
                    "exerciseOrder": len(exercises),
                    "sets": SETS_BY_GOAL.get(goal, 3),
                    "targetReps": REPS_BY_GOAL.get(goal, "8-12"),
                    "targetRpe": RPE_BY_GOAL.get(goal, 8.0),
                    "restSeconds": REST_BY_GOAL.get(goal, 90),
                    "notes": "",
                    "isSupersetWithPrevious": False,
                }
            )

        # Garante pelo menos 3 exercícios por dia. Com foco, completa apenas com
        # exercícios do próprio foco (modo estrito); sem foco, vale o peito.
        while len(exercises) < 3:
            fill_group = groups[0] if focus else "chest"
            exercise = _pick_exercise(pool, fill_group, rng, used, strict=bool(focus))
            if not exercise:
                break
            used.add(exercise["id"])
            exercises.append(
                {
                    "exerciseId": exercise["id"],
                    "exerciseOrder": len(exercises),
                    "sets": SETS_BY_GOAL.get(goal, 3),
                    "targetReps": REPS_BY_GOAL.get(goal, "8-12"),
                    "targetRpe": RPE_BY_GOAL.get(goal, 8.0),
                    "restSeconds": REST_BY_GOAL.get(goal, 90),
                    "notes": "",
                    "isSupersetWithPrevious": False,
                }
            )

        if focus and len(exercises) < COMPLEMENTARY_MIN_EXERCISES:
            raise ValueError(
                "Nenhum exercício compatível com o foco/ambiente informado. "
                "Ajuste o foco ou o ambiente e tente de novo."
            )

        days.append(
            {
                "name": f"Treino {day_letters[day_index % len(day_letters)]}",
                "dayOrder": day_index,
                "exercises": exercises,
                "isRestDay": False,
            }
        )

    plan_name = (
        complementary_plan_name(request)
        if focus
        else f"Plano de {GOAL_LABELS.get(goal, goal)} — {days_per_week}x/semana"
    )
    plan = {
        "name": plan_name,
        "description": (
            "Plano gerado localmente a partir do catálogo de exercícios. "
            "Ajuste cargas e repetições conforme sua evolução."
        ),
        "goal": goal,
        "daysPerWeek": days_per_week,
        "days": days,
    }
    plan = enrich_plan(plan, catalog, language=request_language(request))

    body_weight_kg = getattr(request, "bodyWeightKg", None)
    height_cm = getattr(request, "heightCm", None)
    if body_weight_kg is not None or height_cm is not None:
        plan = profile_adjustments(plan, body_weight_kg, height_cm)
        plan = suggest_initial_load(plan, body_weight_kg)

    return plan


def _build_strength_exercise_dict(
    candidate: Dict[str, Any],
    exercise_id: str,
    order: int,
    goal: str,
) -> Dict[str, Any]:
    """Monta o dicionário do exercício de musculação com campos padronizados."""
    exercise = dict(candidate)
    exercise["exerciseId"] = exercise_id
    exercise["exerciseOrder"] = order
    exercise["sets"] = SETS_BY_GOAL.get(goal, 3)
    exercise["targetReps"] = REPS_BY_GOAL.get(goal, "8-12")
    exercise["targetRpe"] = RPE_BY_GOAL.get(goal, 8.0)
    exercise["restSeconds"] = REST_BY_GOAL.get(goal, 90)
    exercise["notes"] = ""
    exercise["isSupersetWithPrevious"] = order > 0 and False
    exercise["metricType"] = "reps_weight"
    exercise["targetDurationSeconds"] = None
    exercise["targetDistanceMeters"] = None
    exercise["targetPaceSecondsPerKm"] = None
    exercise["targetHrZone"] = None
    return exercise


def build_strength_session(
    pool: List[Dict[str, Any]],
    modality_cfg: Dict[str, Any],
    goal: str,
    level: str,
    environment: str,
    duration_minutes: int,
    target_muscles: List[str],
    rng: random.Random,
    used: set,
    day_order: int,
    focus_muscles: Optional[List[str]] = None,
    complementary: bool = False,
    user_target_muscles: Optional[List[str]] = None,
) -> Optional[Dict[str, Any]]:
    """Constrói uma única sessão de musculação com 4 a 8 exercícios.

    Filtra o pool por categoria de força (ignora stretching/cardio), respeita o
    ambiente (academia/home/bodyweight) e o nível, e nunca repete o mesmo
    exerciseId dentro da sessão.

    `focus_muscles`, quando fornecido, sobrepõe `target_muscles` para aplicar
    split por sessão (ex.: apenas empurrar, apenas puxar, apenas pernas/core).

    `complementary=True` (H3-16): foco EXCLUSIVO — só exercícios cujos
    primaryMuscles intersectam o foco, nº de exercícios pela duração (3 a 6) e
    pool insuficiente devolve None (o endpoint converte em 422).

    `user_target_muscles` (H3-23): grupos pedidos pelo usuário no PLANO
    PRINCIPAL. Foco ali é PRIORIDADE, não exclusividade: os grupos pedidos
    entram primeiro em cada sessão (≥ 1 exercício por grupo, quando existir no
    pool) e o split do ciclo completa o restante. No complementar não se aplica.
    """
    selected_equipment = {item.strip().lower() for item in getattr(modality_cfg, "availableEquipment", []) or [] if item.strip()}

    # Pool apenas de força: filtra por categoria quando disponível.
    strength_pool = [
        exercise
        for exercise in pool
        if _is_strength_exercise(exercise)
    ]

    muscles = list(focus_muscles) if focus_muscles else list(getattr(modality_cfg, "targetMuscles", []) or target_muscles)
    if not muscles:
        muscles = ["chest", "lats", "quadriceps"]

    groups = _expand_muscles(muscles) if muscles else ["chest", "lats", "quadriceps"]

    # H3-23: no plano principal, o foco do usuário é PRIORIDADE — os grupos
    # pedidos entram primeiro na sessão e o split do ciclo completa o restante.
    # Dedup mantendo a ordem: pedidos primeiro, depois os grupos do split.
    user_groups: List[str] = []
    if not complementary and focus_muscles and user_target_muscles:
        user_groups = [
            g for g in _expand_muscles(list(user_target_muscles)) if g not in groups
        ]
        if user_groups:
            groups = user_groups + groups

    if complementary:
        # Foco exclusivo: interseção com o foco é obrigatória; sem fallback para
        # o pool completo e sem reposição por exercícios fora do foco.
        focus_set = set(groups)
        strength_pool = [
            exercise
            for exercise in strength_pool
            if focus_set & {str(m).lower() for m in exercise.get("primaryMuscles", [])}
        ]
        if len(strength_pool) < COMPLEMENTARY_MIN_EXERCISES:
            return None
        count = complementary_exercise_count(duration_minutes)
        count = max(COMPLEMENTARY_MIN_EXERCISES, min(count, len(strength_pool)))
    else:
        count = strength_exercise_count(duration_minutes)
        if not strength_pool:
            strength_pool = list(pool)

    if not complementary and environment == "gym" and selected_equipment and "body only" not in selected_equipment:
        strength_pool = [
            exercise
            for exercise in strength_pool
            if str(exercise.get("equipment", "")).strip().lower() in LOAD_EQUIPMENT
        ]
        if len(strength_pool) < 10:
            strength_pool = [e for e in pool if _is_strength_exercise(e)]
            if not strength_pool:
                strength_pool = list(pool)

    exercises: List[Dict[str, Any]] = []
    attempts = 0

    # 1) Garante pelo menos 1 exercício por grupo muscular solicitado, se houver
    #    no pool. Assim o usuário recebe peito, costas e pernas (e não só peito).
    #    H3-23: grupo do usuário sem candidato no pool não consome slot — a
    #    sessão segue com o split, sem inventar exercício fora do catálogo.
    for idx, group in enumerate(groups):
        if len(exercises) >= count:
            break
        strict = complementary or idx < len(user_groups)
        candidate = _pick_exercise(strength_pool, group, rng, used, strict=strict)
        if candidate is None:
            continue
        exercise_id = candidate.get("id")
        if exercise_id in used:
            continue
        used.add(exercise_id)
        exercises.append(_build_strength_exercise_dict(candidate, exercise_id, len(exercises), goal))

    # 2) Preenche o restante ciclando pelos grupos, sem repetir IDs.
    #    H3-23: grupos do usuário também são estritos aqui — sem candidato, a
    #    vez deles não consome slot com exercício aleatório.
    group_idx = 0
    while len(exercises) < count and attempts < count * 6:
        attempts += 1
        group = groups[group_idx % len(groups)]
        group_idx += 1
        candidate = _pick_exercise(
            strength_pool,
            group,
            rng,
            used,
            strict=complementary or group in user_groups,
        )
        if candidate is None:
            continue
        exercise_id = candidate.get("id")
        if exercise_id in used:
            continue
        used.add(exercise_id)
        exercises.append(_build_strength_exercise_dict(candidate, exercise_id, len(exercises), goal))

    if not exercises:
        return None

    # Primeiro exercício é composto (usa equipamento de carga quando disponível).
    first = exercises[0]
    if environment == "gym" and selected_equipment:
        for ex in exercises:
            eq = str(ex.get("equipment", "")).strip().lower()
            if eq in LOAD_EQUIPMENT:
                exercises.remove(ex)
                exercises.insert(0, ex)
                break

    if complementary:
        # Título na mesma família do helper da H3-13 ("Treino X — rótulo"),
        # com o rótulo do foco exclusivo no lugar do split.
        labels = [
            MUSCLE_LABELS_PT.get(str(m).strip().lower(), str(m))
            for m in focus_muscles or []
            if str(m).strip()
        ]
        focus_label = " e ".join(labels) if labels else "Complementar"
        name = f"Treino {chr(65 + (day_order % 6))} — {focus_label}"
    else:
        split_idx = day_order % len(STRENGTH_SPLITS)
        split_labels = ["Empurrar", "Puxar", "Pernas e Core"]
        name = f"Treino {chr(65 + split_idx)} — {split_labels[split_idx]}"
    return {
        "name": name,
        "dayOrder": day_order,
        "weekday": None,  # preenchido por assign_weekdays
        "modality": "strength",
        "isRestDay": False,
        "exercises": exercises,
    }


def _session_name_for_modality(modality: str, index: int, day_order: int) -> str:
    names = {
        "strength": ["Academia A — Superiores", "Academia B — Inferiores", "Academia C — Total"],
        "run": ["Corrida leve", "Corrida intervalado", "Ritmo de prova", "Longão"],
        "mobility": ["Mobilidade — costas", "Mobilidade — quadris", "Mobilidade — pós-corrida", "Mobilidade — corpo inteiro"],
    }
    options = names.get(modality, ["Sessão"])
    return options[index % len(options)]


def assign_weekdays(
    sessions: List[Dict[str, Any]],
    sessions_per_modality: Dict[str, int],
) -> List[Dict[str, Any]]:
    """Distribui as sessões pelos weekdays (1..7).

    Musculação nunca fica em dias consecutivos. Máximo 3 sessões por weekday e
    no máximo uma de cada modalidade por dia. Várias modalidades podem compartilhar
    o mesmo weekday, cada uma como um `day` separado (dayOrder diferente).
    """
    days: List[Dict[str, Any]] = []
    # weekday -> set de modalidades já alocadas naquele dia
    weekday_modalities: Dict[int, set] = defaultdict(set)
    # weekday -> quantidade de sessões alocadas naquele dia
    weekday_count: Dict[int, int] = defaultdict(int)
    # último weekday usado por musculação (para não usar dias consecutivos)
    last_strength_wd: Optional[int] = None

    ordered = sorted(sessions, key=lambda s: MODALITY_RANK.get(s["modality"], 9))

    for session in ordered:
        modality = session["modality"]
        placed = False

        # Tenta colocar em cada weekday, começando pelo 1.
        for wd in range(1, 8):
            if modality in weekday_modalities[wd]:
                continue
            if weekday_count[wd] >= 3:
                continue
            if modality == "strength" and last_strength_wd is not None and abs(wd - last_strength_wd) == 1:
                continue

            session = dict(session)
            session["weekday"] = wd
            days.append(session)
            weekday_modalities[wd].add(modality)
            weekday_count[wd] += 1
            if modality == "strength":
                last_strength_wd = wd
            placed = True
            break

        if not placed:
            # Força a colocação em qualquer weekday com espaço (último recurso).
            for wd in range(1, 8):
                if weekday_count[wd] < 3 and modality not in weekday_modalities[wd]:
                    session = dict(session)
                    session["weekday"] = wd
                    days.append(session)
                    weekday_modalities[wd].add(modality)
                    weekday_count[wd] += 1
                    if modality == "strength":
                        last_strength_wd = wd
                    placed = True
                    break

    days.sort(key=lambda d: (d["weekday"], d["dayOrder"]))
    for i, day in enumerate(days):
        day["dayOrder"] = i
    return days


def normalize_hybrid_plan(plan: Dict[str, Any]) -> Dict[str, Any]:
    """Garante as invariantes do plano híbrido depois da IA ou do fallback.

    1. Um day = uma sessão = uma única modalidade (todos os exercícios do dia
       com o mesmo metricType e mesma família de modality).
    2. Musculação: 4 a 8 exercícios, metricType reps_weight, categoria de força.
    3. Corrida: exatamente 1 bloco por day, metricType distance_duration.
    4. Mobilidade: exatamente 1 bloco por day, metricType duration, duração
       300-900 s.
    5. daysPerWeek = len(days) (total de sessões).
    6. Várias sessões no mesmo weekday são days separados, nunca misturadas.
    7. dayOrder único e crescente; ordena por weekday e depois dayOrder.
    8. No máximo 3 sessões por weekday, no máximo uma de cada modalidade.
    """
    days = plan.get("days", [])
    normalized_days: List[Dict[str, Any]] = []

    for day in days:
        weekday = day.get("weekday", 1)
        exercises = list(day.get("exercises", []))

        # Agrupa exercícios por metricType para separar days mistos.
        by_metric: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
        for ex in exercises:
            by_metric[str(ex.get("metricType", "reps_weight"))].append(ex)

        for metric_type, exs in by_metric.items():
            if metric_type == "reps_weight":
                strength_exs = [ex for ex in exs if ex.get("metricType", "reps_weight") == "reps_weight"][:8]
                if not strength_exs:
                    continue
                new_day = dict(day)
                new_day["modality"] = "strength"
                new_day["exercises"] = strength_exs
                new_day["weekday"] = weekday
            elif metric_type == "distance_duration":
                run_exs = [ex for ex in exs if ex.get("metricType") == "distance_duration"][:1]
                if not run_exs:
                    continue
                new_day = dict(day)
                new_day["modality"] = "run"
                new_day["exercises"] = run_exs
                new_day["weekday"] = weekday
            elif metric_type == "duration":
                mob_exs = [ex for ex in exs if ex.get("metricType") == "duration"][:1]
                if not mob_exs:
                    continue
                new_day = dict(day)
                new_day["modality"] = "mobility"
                new_day["exercises"] = mob_exs
                new_day["weekday"] = weekday
            else:
                continue

            normalized_days.append(new_day)

    # Ordena por weekday e depois dayOrder original; reindexa dayOrder.
    normalized_days.sort(key=lambda d: (d.get("weekday", 1), d.get("dayOrder", 0)))
    for i, day in enumerate(normalized_days):
        day["dayOrder"] = i

    plan = dict(plan)
    plan["days"] = normalized_days
    plan["daysPerWeek"] = len(normalized_days)
    return plan


def enforce_complementary_plan(
    plan: Dict[str, Any],
    request: Any,
    catalog: List[Dict[str, Any]],
) -> Dict[str, Any]:
    """Aplica as garantias do plano complementar (H3-16) depois da IA.

    1. Só sessões de força (dias de corrida/mobilidade são removidos).
    2. Remove exercícios fora do foco exclusivo ou do equipamento permitido.
    3. Sessão que sobrar com menos de 3 exercícios é reconstruída pelo fallback
       local com as mesmas restrições.
    4. Pool efetivo (força ∩ foco ∩ equipamento) com menos de 3 exercícios
       levanta ValueError — o endpoint converte em 422.

    Chamada após `enrich_plan` (precisa de primaryMuscles/equipment preenchidos)
    e após `normalize_hybrid_plan`. Sem `focusMuscles` no request, devolve o
    plano inalterado.
    """
    focus = _expand_muscles(list(getattr(request, "focusMuscles", []) or []))
    if not focus:
        return plan

    focus_set = set(focus)
    allowed_equipment = complementary_equipment_filter(request)
    level = str(getattr(request, "currentLevel", "intermediate") or "intermediate")
    equipment = list(getattr(request, "availableEquipment", []) or [])
    duration = int(getattr(request, "sessionDurationMinutes", 60) or 60)
    environment = str(getattr(request, "environment", "") or "").strip().lower() or "bodyweight"
    goal = getattr(request, "goal", "general")

    pool = _build_pool(catalog, equipment, level, strict_equipment=allowed_equipment)
    effective_pool = [
        exercise
        for exercise in pool
        if _is_strength_exercise(exercise)
        and focus_set & {str(m).lower() for m in exercise.get("primaryMuscles", [])}
    ]
    if len(effective_pool) < COMPLEMENTARY_MIN_EXERCISES:
        raise ValueError(
            "Nenhum exercício compatível com o foco/ambiente informado. "
            "Ajuste o foco ou o ambiente e tente de novo."
        )

    days = [
        day
        for day in plan.get("days", [])
        if str(day.get("modality", "strength")) == "strength" and not day.get("isRestDay")
    ]

    enforced_days: List[Dict[str, Any]] = []
    for day in days:
        kept = []
        for exercise in day.get("exercises", []):
            if str(exercise.get("metricType", "reps_weight")) != "reps_weight":
                continue
            muscles = {str(m).lower() for m in exercise.get("primaryMuscles", [])}
            if not (muscles & focus_set):
                continue
            eq = str(exercise.get("equipment", "")).strip().lower()
            if allowed_equipment is not None and eq not in allowed_equipment:
                continue
            kept.append(exercise)

        if len(kept) >= COMPLEMENTARY_MIN_EXERCISES:
            day = dict(day)
            day["exercises"] = kept[:COMPLEMENTARY_MAX_EXERCISES]
            day["modality"] = "strength"
            enforced_days.append(day)
            continue

        # Sessão insuficiente: reconstrói localmente com as mesmas restrições.
        rng = random.Random()
        session = build_strength_session(
            pool,
            request,
            goal,
            level,
            environment,
            duration,
            [],
            rng,
            set(),
            len(enforced_days),
            focus,
            complementary=True,
        )
        if session is None:
            raise ValueError(
                "Nenhum exercício compatível com o foco/ambiente informado. "
                "Ajuste o foco ou o ambiente e tente de novo."
            )
        session["weekday"] = day.get("weekday", 1) or 1
        enforced_days.append(session)

    enforced_days.sort(key=lambda d: (d.get("weekday", 1), d.get("dayOrder", 0)))
    for i, day in enumerate(enforced_days):
        day["dayOrder"] = i

    plan = dict(plan)
    plan["days"] = enforced_days
    plan["daysPerWeek"] = len(enforced_days)
    plan["name"] = complementary_plan_name(request)
    return plan


def build_hybrid_plan(request: Any, catalog: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Monta uma semana híbrida (musculação, corrida, mobilidade) sem IA.

    Cada modalidade vira sessões independentes (uma por day), com musculação de
    4-8 exercícios, corrida com rotação de templates e mobilidade com rotina
    própria. Várias modalidades podem cair no mesmo weekday como days separados.
    """
    modalities = getattr(request, "modalities", None) or []
    if not modalities:
        return build_local_plan(request, catalog)

    # Plano complementar (H3-16): focusMuscles presente ⇒ só força, foco
    # exclusivo e filtro de equipamento estrito (nunca ignorado por falta de
    # candidatos).
    focus_request = list(getattr(request, "focusMuscles", []) or [])
    is_complementary = bool(focus_request)
    expanded_focus = _expand_muscles(focus_request)

    session_duration = int(getattr(request, "sessionDurationMinutes", 60))
    review_after_weeks = int(getattr(request, "reviewAfterWeeks", 4))
    goal = getattr(request, "goal", "general")
    level = getattr(request, "currentLevel", "intermediate")
    equipment = list(getattr(request, "availableEquipment", []) or [])
    muscles = list(getattr(request, "targetMuscles", []) or [])
    environment = str(getattr(request, "environment", "") or "").strip().lower() or "bodyweight"
    rng = random.Random()

    strict_equipment = complementary_equipment_filter(request)
    pool = _build_pool(catalog, equipment, level, strict_equipment=strict_equipment)
    # Com filtro estrito o pool NUNCA volta ao catálogo completo: isso esconderia
    # a incompatibilidade que precisa virar 422.
    if strict_equipment is None and len(pool) < 10:
        pool = list(catalog)

    # Cria uma sessão independente por sessão planejada de cada modalidade.
    sessions: List[Dict[str, Any]] = []
    sessions_per_modality: Dict[str, int] = {}
    strength_session_index = 0
    run_session_index = 0
    mobility_session_index = 0

    for m in modalities:
        mtype = str(getattr(m, "type", "strength")).lower()
        if is_complementary and mtype != "strength":
            # Complementar é só de força (decisão de produto); o validador do
            # endpoint já bloqueia, aqui é defesa em profundidade.
            continue
        m_sessions = max(1, int(getattr(m, "sessionsPerWeek", 1)))
        m_duration = int(getattr(m, "sessionDurationMinutes", session_duration))
        m_duration = max(5, min(120, m_duration))
        sessions_per_modality[mtype] = m_sessions

        for _ in range(m_sessions):
            if mtype == "run":
                run_cfg = m
                block = template_for(
                    getattr(run_cfg, "level", level),
                    getattr(run_cfg, "recent5kSeconds", None),
                    run_session_index,
                    m_duration,
                )
                run_session_index += 1
                sessions.append({
                    "name": block.get("exerciseName", "Corrida"),
                    "dayOrder": len(sessions),
                    "weekday": None,
                    "modality": "run",
                    "isRestDay": False,
                    "exercises": [{
                        "exerciseId": block["exerciseId"],
                        "exerciseOrder": 0,
                        "sets": block["sets"],
                        "targetReps": block.get("targetReps", ""),
                        "targetRpe": block.get("targetRpe", 7.0),
                        "restSeconds": block.get("restSeconds", 60),
                        "notes": block.get("notes", ""),
                        "isSupersetWithPrevious": False,
                        "exerciseName": block["exerciseName"],
                        "primaryMuscles": ["legs"],
                        "equipment": "nenhum",
                        "metricType": "distance_duration",
                        "targetDurationSeconds": None,
                        "targetDistanceMeters": block.get("targetDistanceMeters"),
                        "targetPaceSecondsPerKm": block.get("targetPaceSecondsPerKm"),
                        "targetHrZone": None,
                    }],
                })
            elif mtype == "mobility":
                mob_cfg = m
                focus = getattr(mob_cfg, "focus", None)
                mobility_session_index += 1
                # Quando o usuário não especifica foco, rota entre focos para não
                # repetir sempre "corpo inteiro".
                if not focus:
                    focus = MOBILITY_FOCUS_ROTATION[
                        (mobility_session_index - 1) % len(MOBILITY_FOCUS_ROTATION)
                    ]
                sessions.append({
                    "name": f"Mobilidade — {focus or 'corpo inteiro'}",
                    "dayOrder": len(sessions),
                    "weekday": None,
                    "modality": "mobility",
                    "isRestDay": False,
                    "exercises": [{
                        "exerciseId": f"mobility_{focus or 'full'}",
                        "exerciseOrder": 0,
                        "exerciseName": "Mobilidade" + (f" — {focus}" if focus else ""),
                        "metricType": "duration",
                        "sets": 1,
                        "targetReps": "",
                        "targetRpe": 5.0,
                        "restSeconds": 0,
                        "notes": f"Rotina de mobilidade{(' para ' + focus) if focus else ''} ({m_duration} min).",
                        "isSupersetWithPrevious": False,
                        "primaryMuscles": [],
                        "equipment": "body only",
                        "targetDurationSeconds": m_duration * 60,
                        "targetDistanceMeters": None,
                        "targetPaceSecondsPerKm": None,
                        "targetHrZone": None,
                    }],
                })
            else:
                # strength
                strength_session_index += 1
                if is_complementary:
                    # Foco exclusivo: sem rotação de STRENGTH_SPLITS — a sessão
                    # inteira é o foco pedido (H3-16).
                    session = build_strength_session(
                        pool,
                        m,
                        goal,
                        level,
                        environment,
                        m_duration,
                        muscles,
                        rng,
                        set(),
                        strength_session_index - 1,
                        expanded_focus,
                        complementary=True,
                    )
                    if session is None:
                        raise ValueError(
                            "Nenhum exercício compatível com o foco/ambiente "
                            "informado. Ajuste o foco ou o ambiente e tente de novo."
                        )
                else:
                    # Aplica split tradicional em ciclo: push -> pull -> legs -> push...
                    # H3-23: o foco do usuário (targetMuscles) é prioridade em
                    # cada sessão — os grupos pedidos entram antes do split.
                    focus_muscles = STRENGTH_SPLITS[(strength_session_index - 1) % len(STRENGTH_SPLITS)]
                    session = build_strength_session(
                        pool,
                        m,
                        goal,
                        level,
                        environment,
                        m_duration,
                        muscles,
                        rng,
                        set(),
                        strength_session_index - 1,
                        focus_muscles,
                        user_target_muscles=_expand_muscles(muscles),
                    )
                if session is None:
                    continue
                sessions.append(session)

    if not sessions:
        return build_local_plan(request, catalog)

    days = assign_weekdays(sessions, sessions_per_modality)

    plan = {
        "name": (
            complementary_plan_name(request)
            if is_complementary
            else "Semana híbrida"
        ),
        "description": (
            "Plano complementar de força com foco exclusivo, gerado a partir do "
            "catálogo de exercícios. Ajuste cargas e repetições conforme sua evolução."
            if is_complementary
            else
            "Plano semanal com sessões independentes por modalidade "
            "(musculação, corrida, mobilidade). Ajuste cargas e ritmo conforme "
            "sua evolução."
        ),
        "goal": goal,
        "daysPerWeek": len(days),
        "days": days,
    }
    plan = enrich_plan(plan, catalog, language=request_language(request))
    plan = normalize_hybrid_plan(plan)

    body_weight_kg = getattr(request, "bodyWeightKg", None)
    height_cm = getattr(request, "heightCm", None)
    if body_weight_kg is not None or height_cm is not None:
        plan = profile_adjustments(plan, body_weight_kg, height_cm)
        plan = suggest_initial_load(plan, body_weight_kg)
        plan = normalize_hybrid_plan(plan)

    return plan


def _session_stats(sessions: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Agrega RPE médio, flag de dor/limitação e natação das sessões manuais."""
    rpes = [float(s["rpe"]) for s in sessions if s.get("rpe") is not None]
    avg_rpe = (sum(rpes) / len(rpes)) if rpes else None
    pain = any("dor" in str(s.get("notes", "")).lower() for s in sessions)
    swim = any(str(s.get("modality", "")) == "swim" for s in sessions)
    return {"avgRpe": avg_rpe, "pain": pain, "swim": swim}


def review_plan(
    plan: Dict[str, Any], history: Optional[Dict[str, Any]] = None
) -> Dict[str, Any]:
    """Revisa um plano com base no histórico real.

    Retorna um dict com `workout` (plano proposto), `changes` (lista de strings
    legíveis) e `source`. Sem histórico (weeks == 0) devolve o plano atual com
    changes vazio. Todas as mudanças são proposições: o app só salva se o
    usuário confirmar.
    """
    changes: List[str] = []
    history = history or {}
    sessions = history.get("sessions") or []
    weeks = int(history.get("weeks", 0))
    # A adesão vem do histórico quando informada; sem sessions ela ainda pode
    # ser conhecida (ex.: 4 semanas, 40% de adesão, sem lista de sessões).
    adherence = float(history.get("adherence", 1.0))

    if weeks == 0:
        return {
            "workout": plan,
            "changes": [],
            "source": "local",
        }

    stats = _session_stats(sessions)

    # Regra: adesão baixa → não aumentar volume; reduzir 20% das séries.
    if adherence < 0.60:
        changed = False
        for day in plan.get("days", []):
            for exercise in day.get("exercises", []):
                if exercise.get("metricType", "reps_weight") != "reps_weight":
                    continue
                current_sets = int(exercise.get("sets", 3))
                new_sets = max(2, int(current_sets * 0.8))
                if new_sets != current_sets:
                    exercise["sets"] = new_sets
                    changed = True
        if changed:
            changes.append("Volume reduzido 20% por adesão baixa nas últimas semanas.")
        return {"workout": plan, "changes": changes, "source": "local"}

    # Regra: RPE médio alto por duas semanas → deload (menos séries).
    if stats["avgRpe"] is not None and stats["avgRpe"] > 8.5:
        changed = False
        for day in plan.get("days", []):
            for exercise in day.get("exercises", []):
                if exercise.get("metricType", "reps_weight") != "reps_weight":
                    continue
                current_sets = int(exercise.get("sets", 3))
                new_sets = max(2, current_sets - 1)
                if new_sets != current_sets:
                    exercise["sets"] = new_sets
                    changed = True
        if changed:
            changes.append("Deload: redução de séries por RPE médio acima de 8,5.")

    # Regra: dor/limitação nas notas → não aumentar volume; sugerir mobilidade.
    if stats["pain"]:
        changes.append(
            "Não aumentar volume devido a relatos de dor/limitação; "
            "considere adicionar mobilidade."
        )

    # Natação registrada entra como contexto de fadiga, sem gerar bloco de natação.
    if stats["swim"]:
        changes.append("Natação registrada considerada como volume/fadiga (sem bloco de natação).")

    return {"workout": plan, "changes": changes, "source": "local"}

