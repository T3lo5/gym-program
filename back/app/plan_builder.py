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
    """Tenta corrigir IDs devolvidos pela IA para IDs reais do catálogo."""
    by_id, lookup = catalog_index(catalog)
    unresolved: List[str] = []
    lookup_keys = list(lookup.keys())

    for day in plan.get("days", []):
        for exercise in day.get("exercises", []):
            raw = str(exercise.get("exerciseId", "")).strip()
            if raw in by_id:
                continue

            normalized = normalize(raw)
            candidate = lookup.get(normalized) if normalized else None

            if not candidate and normalized:
                # 1) Fuzzy match com cutoff relaxado
                matches = get_close_matches(normalized, lookup_keys, n=1, cutoff=0.72)
                if matches:
                    candidate = lookup[matches[0]]

            if not candidate and normalized:
                # 2) Substring: o ID da IA está contido em algum ID/nome do catálogo (ou vice-versa)
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
    used.add(chosen.get("id"))
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


MODALITY_RANK = {"strength": 0, "run": 1, "mobility": 2}


def _pick_strength_exercise(
    pool: List[Dict[str, Any]],
    muscles: List[str],
    goal: str,
    rng: random.Random,
    used: set,
) -> Optional[Dict[str, Any]]:
    """Escolhe um exercício de musculação do catálogo, sem repetir IDs."""
    groups = _expand_muscles(muscles) if muscles else ["chest", "lats", "quadriceps"]
    for group in groups:
        candidate = _pick_exercise(pool, group, rng, used)
        if candidate:
            return candidate
    return _pick_exercise(pool, "chest", rng, used)


def build_hybrid_plan(request: Any, catalog: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Monta uma semana híbrida (musculação, corrida, mobilidade) sem IA.

    Distribui as sessões das modalidades em weekdays (1 = segunda ... 7 =
    domingo) em round-robin, permitindo mais de uma sessão no mesmo dia. A soma
    de sessionsPerWeek pode exceder 7.
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
    rng = random.Random()

    pool = _build_pool(catalog, equipment, level)
    if len(pool) < 10:
        pool = list(catalog)

    # Um slot por sessão planejada de cada modalidade.
    slots: List[str] = []
    modality_cfg: Dict[str, Dict[str, Any]] = {}
    for m in modalities:
        mtype = str(m.get("type", "strength")).lower()
        modality_cfg[mtype] = m
        sessions = max(1, int(m.get("sessionsPerWeek", 1)))
        for _ in range(sessions):
            slots.append(mtype)

    # Round-robin pelos dias da semana (1..7).
    by_weekday: Dict[int, List[tuple[int, str]]] = defaultdict(list)
    for i, mtype in enumerate(slots):
        wd = (i % 7) + 1
        by_weekday[wd].append((i, mtype))

    days: List[Dict[str, Any]] = []
    used: set = set()
    day_index = 0

    for wd in sorted(by_weekday):
        entries = by_weekday[wd]
        entries.sort(key=lambda e: (MODALITY_RANK.get(e[1], 9), e[0]))

        day_exercises: List[Dict[str, Any]] = []
        day_order = 0
        primary_modality = entries[0][1]

        for _, mtype in entries:
            if mtype == "run":
                run_cfg = modality_cfg.get("run", {})
                block = template_for(run_cfg.get("level", level), run_cfg.get("recent5kSeconds"))
                exercise = {
                    "exerciseId": block["exerciseId"],
                    "exerciseOrder": day_order,
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
                }
            elif mtype == "mobility":
                mob_cfg = modality_cfg.get("mobility", {})
                focus = mob_cfg.get("focus")
                exercise = {
                    "exerciseId": f"mobility_{focus or 'full'}",
                    "exerciseOrder": day_order,
                    "exerciseName": "Mobilidade"
                    + (f" - {focus}" if focus else ""),
                    "metricType": "duration",
                    "sets": 1,
                    "targetReps": "",
                    "targetRpe": 5.0,
                    "restSeconds": 0,
                    "notes": (
                        f"Rotina de mobilidade{(' para ' + focus) if focus else ''} "
                        f"({session_duration} min)."
                    ),
                    "isSupersetWithPrevious": False,
                    "primaryMuscles": [],
                    "equipment": "body only",
                    "targetDurationSeconds": session_duration * 60,
                    "targetDistanceMeters": None,
                    "targetPaceSecondsPerKm": None,
                    "targetHrZone": None,
                }
            else:
                exercise = _pick_strength_exercise(pool, muscles, goal, rng, used)
                if exercise is None:
                    continue
                # O catálogo armazena o ID sob `id`; o plano espera `exerciseId`.
                exercise["exerciseId"] = exercise.get("id")
                exercise["exerciseOrder"] = day_order
                exercise["sets"] = SETS_BY_GOAL.get(goal, 3)
                exercise["targetReps"] = REPS_BY_GOAL.get(goal, "8-12")
                exercise["targetRpe"] = RPE_BY_GOAL.get(goal, 8.0)
                exercise["restSeconds"] = REST_BY_GOAL.get(goal, 90)
                exercise["notes"] = ""
                exercise["isSupersetWithPrevious"] = False
                exercise["metricType"] = "reps_weight"
                exercise["targetDurationSeconds"] = None
                exercise["targetDistanceMeters"] = None
                exercise["targetPaceSecondsPerKm"] = None
                exercise["targetHrZone"] = None

            day_exercises.append(exercise)
            day_order += 1

        if not day_exercises:
            continue

        modality_names = {"strength": "Musculação", "run": "Corrida", "mobility": "Mobilidade"}
        if len({e["exerciseId"].split("_")[0] for e in day_exercises}) == 1:
            day_name = modality_names.get(primary_modality, "Treino")
        else:
            day_name = "Treino híbrido"

        days.append(
            {
                "name": day_name,
                "dayOrder": day_index,
                "weekday": wd,
                "modality": primary_modality,
                "isRestDay": False,
                "exercises": day_exercises,
            }
        )
        day_index += 1

    if not days:
        return build_local_plan(request, catalog)

    plan = {
        "name": "Semana híbrida",
        "description": (
            "Plano semanal com musculação, corrida e mobilidade gerado localmente. "
            "Ajuste cargas e ritmo conforme sua evolução."
        ),
        "goal": goal,
        "daysPerWeek": len(days),
        "days": days,
    }
    plan = enrich_plan(plan, catalog)

    body_weight_kg = getattr(request, "bodyWeightKg", None)
    height_cm = getattr(request, "heightCm", None)
    if body_weight_kg is not None or height_cm is not None:
        plan = profile_adjustments(plan, body_weight_kg, height_cm)
        plan = suggest_initial_load(plan, body_weight_kg)

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

