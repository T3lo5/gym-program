"""Geração e normalização de planos de treino.

O backend tenta usar a IA. Se a IA falhar, demorar demais ou devolver IDs que
não existem no catálogo, este módulo monta um plano local válido usando os 876
exercícios do catálogo. Assim o app sempre recebe um treino utilizável.
"""

import random
import re
from datetime import date, timedelta
from difflib import get_close_matches
from typing import Any, Dict, List, Optional, Tuple

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
    """Tenta corrigir IDs devolvidos pela IA para IDs reais do catálogo."""
    by_id, lookup = catalog_index(catalog)
    unresolved: List[str] = []

    for day in plan.get("days", []):
        for exercise in day.get("exercises", []):
            raw = str(exercise.get("exerciseId", "")).strip()
            if raw in by_id:
                continue

            candidate = lookup.get(normalize(raw))
            if not candidate and normalize(raw):
                matches = get_close_matches(
                    normalize(raw), list(lookup.keys()), n=1, cutoff=0.84
                )
                if matches:
                    candidate = lookup[matches[0]]

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
        if exercise["id"] not in used
        and muscle in [str(m).lower() for m in exercise.get("primaryMuscles", [])]
    ]
    if not candidates:
        candidates = [exercise for exercise in pool if exercise["id"] not in used]
    if not candidates:
        return None
    return rng.choice(candidates)


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
    return enrich_plan(plan, catalog)
