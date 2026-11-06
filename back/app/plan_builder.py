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
from typing import Any, Dict, List, Optional, Tuple

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
STRENGTH_SPLITS: List[List[str]] = [
    ["chest", "shoulders", "triceps"],      # A — empurrar
    ["lats", "middle back", "biceps", "traps"],  # B — puxar
    ["quadriceps", "hamstrings", "glutes", "calves", "abdominals"],  # C — pernas/core
]

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


def resolve_plan_ids(
    plan: Dict[str, Any], catalog: List[Dict[str, Any]]
) -> Tuple[Dict[str, Any], List[str]]:
    """Tenta corrigir IDs devolvidos pela IA para IDs reais do catálogo.

    IDs sintéticos de corrida (`run_*`) e mobilidade (`mobility_*`) são aceitos
    como estão: não existem no catálogo e não devem ser resolvidos nem
    sinalizados como pendentes. Apenas exercícios de musculação são validados.
    """
    by_id, lookup = catalog_index(catalog)
    unresolved: List[str] = []
    lookup_keys = list(lookup.keys())

    for day in plan.get("days", []):
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
                continue

            if raw in by_id:
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
            else:
                unresolved.append(raw)

    return plan, unresolved


def enrich_plan(plan: Dict[str, Any], catalog: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Adiciona nome, músculos e equipamento para o app não precisar do catálogo."""
    by_id, _ = catalog_index(catalog)
    for day in plan.get("days", []):
        for exercise in day.get("exercises", []):
            source = by_id.get(exercise.get("exerciseId", ""))
            if source:
                exercise["exerciseName"] = source.get("name", exercise.get("exerciseId", ""))
                exercise["primaryMuscles"] = source.get("primaryMuscles", [])
                exercise["equipment"] = source.get("equipment") or "body only"
            else:
                exercise.setdefault("exerciseName", exercise.get("exerciseId", ""))
                exercise.setdefault("primaryMuscles", [])
                exercise.setdefault("equipment", "body only")
    return plan


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
    catalog: List[Dict[str, Any]], equipment: List[str], level: str
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
) -> Optional[Dict[str, Any]]:
    candidates = [
        exercise
        for exercise in pool
        if exercise.get("id") not in used
        and muscle in [str(m).lower() for m in exercise.get("primaryMuscles", [])]
    ]
    if not candidates:
        candidates = [exercise for exercise in pool if exercise.get("id") not in used]
    if not candidates:
        return None
    chosen = rng.choice(candidates)
    return chosen


def _day_groups(
    request: Any, day_index: int, total_days: int
) -> List[str]:
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
    days_per_week = max(2, min(6, int(getattr(request, "daysPerWeek", 4))))
    goal = getattr(request, "goal", "hypertrophy")
    level = getattr(request, "currentLevel", "intermediate")
    equipment = list(getattr(request, "availableEquipment", []) or [])
    rng = random.Random()

    pool = _build_pool(catalog, equipment, level)
    if len(pool) < 10:
        pool = list(catalog)

    days: List[Dict[str, Any]] = []
    day_letters = "ABCDEF"

    for day_index in range(days_per_week):
        groups = _day_groups(request, day_index, days_per_week)
        used: set = set()
        exercises: List[Dict[str, Any]] = []

        for group in groups:
            exercise = _pick_exercise(pool, group, rng, used)
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

        # Garante pelo menos 3 exercícios por dia.
        while len(exercises) < 3:
            exercise = _pick_exercise(pool, "chest", rng, used)
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

        days.append(
            {
                "name": f"Treino {day_letters[day_index % len(day_letters)]}",
                "dayOrder": day_index,
                "exercises": exercises,
                "isRestDay": False,
            }
        )

    plan = {
        "name": f"Plano de {GOAL_LABELS.get(goal, goal)} — {days_per_week}x/semana",
        "description": (
            "Plano gerado localmente a partir do catálogo de exercícios. "
            "Ajuste cargas e repetições conforme sua evolução."
        ),
        "goal": goal,
        "daysPerWeek": days_per_week,
        "days": days,
    }
    plan = enrich_plan(plan, catalog)

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
) -> Optional[Dict[str, Any]]:
    """Constrói uma única sessão de musculação com 4 a 8 exercícios.

    Filtra o pool por categoria de força (ignora stretching/cardio), respeita o
    ambiente (academia/home/bodyweight) e o nível, e nunca repete o mesmo
    exerciseId dentro da sessão.

    `focus_muscles`, quando fornecido, sobrepõe `target_muscles` para aplicar
    split por sessão (ex.: apenas empurrar, apenas puxar, apenas pernas/core).
    """
    count = strength_exercise_count(duration_minutes)
    selected_equipment = {item.strip().lower() for item in getattr(modality_cfg, "availableEquipment", []) or [] if item.strip()}

    # Pool apenas de força: filtra por categoria quando disponível.
    strength_pool = [
        exercise
        for exercise in pool
        if _is_strength_exercise(exercise)
    ]
    if not strength_pool:
        strength_pool = list(pool)

    if environment == "gym" and selected_equipment and "body only" not in selected_equipment:
        strength_pool = [
            exercise
            for exercise in strength_pool
            if str(exercise.get("equipment", "")).strip().lower() in LOAD_EQUIPMENT
        ]
        if len(strength_pool) < 10:
            strength_pool = [e for e in pool if _is_strength_exercise(e)]
            if not strength_pool:
                strength_pool = list(pool)

    muscles = list(focus_muscles) if focus_muscles else list(getattr(modality_cfg, "targetMuscles", []) or target_muscles)
    if not muscles:
        muscles = ["chest", "lats", "quadriceps"]

    groups = _expand_muscles(muscles) if muscles else ["chest", "lats", "quadriceps"]

    exercises: List[Dict[str, Any]] = []
    attempts = 0

    # 1) Garante pelo menos 1 exercício por grupo muscular solicitado, se houver
    #    no pool. Assim o usuário recebe peito, costas e pernas (e não só peito).
    for group in groups:
        if len(exercises) >= count:
            break
        candidate = _pick_exercise(strength_pool, group, rng, used)
        if candidate is None:
            continue
        exercise_id = candidate.get("id")
        if exercise_id in used:
            continue
        used.add(exercise_id)
        exercises.append(_build_strength_exercise_dict(candidate, exercise_id, len(exercises), goal))

    # 2) Preenche o restante ciclando pelos grupos, sem repetir IDs.
    group_idx = 0
    while len(exercises) < count and attempts < count * 6:
        attempts += 1
        group = groups[group_idx % len(groups)]
        group_idx += 1
        candidate = _pick_exercise(strength_pool, group, rng, used)
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

    name = _session_name_for_modality("strength", len(exercises), day_order)
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


def build_hybrid_plan(request: Any, catalog: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Monta uma semana híbrida (musculação, corrida, mobilidade) sem IA.

    Cada modalidade vira sessões independentes (uma por day), com musculação de
    4-8 exercícios, corrida com rotação de templates e mobilidade com rotina
    própria. Várias modalidades podem cair no mesmo weekday como days separados.
    """
    modalities = getattr(request, "modalities", None) or []
    if not modalities:
        return build_local_plan(request, catalog)

    session_duration = int(getattr(request, "sessionDurationMinutes", 60))
    review_after_weeks = int(getattr(request, "reviewAfterWeeks", 4))
    goal = getattr(request, "goal", "general")
    level = getattr(request, "currentLevel", "intermediate")
    equipment = list(getattr(request, "availableEquipment", []) or [])
    muscles = list(getattr(request, "targetMuscles", []) or [])
    environment = str(getattr(request, "environment", "") or "").strip().lower() or "bodyweight"
    rng = random.Random()

    pool = _build_pool(catalog, equipment, level)
    if len(pool) < 10:
        pool = list(catalog)

    # Cria uma sessão independente por sessão planejada de cada modalidade.
    sessions: List[Dict[str, Any]] = []
    sessions_per_modality: Dict[str, int] = {}
    strength_session_index = 0
    run_session_index = 0
    mobility_session_index = 0

    for m in modalities:
        mtype = str(getattr(m, "type", "strength")).lower()
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
                # Aplica split tradicional em ciclo: push -> pull -> legs -> push...
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
                )
                if session is None:
                    continue
                sessions.append(session)

    if not sessions:
        return build_local_plan(request, catalog)

    days = assign_weekdays(sessions, sessions_per_modality)

    plan = {
        "name": "Semana híbrida",
        "description": (
            "Plano semanal com sessões independentes por modalidade "
            "(musculação, corrida, mobilidade). Ajuste cargas e ritmo conforme "
            "sua evolução."
        ),
        "goal": goal,
        "daysPerWeek": len(days),
        "days": days,
    }
    plan = enrich_plan(plan, catalog)
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

