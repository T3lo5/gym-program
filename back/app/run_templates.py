"""Templates de corrida por nível e objetivo.

Usado pelo fallback híbrido local (sem IA) e como base da geração de
sessões de corrida. Cada bloco define:
- `exerciseId`: ID estável do bloco (não precisa existir no catálogo);
- `exerciseName`: nome exibido ao usuário;
- `sets`: repetições do bloco;
- `targetDistanceMeters`: distância por repetição (m);
- `targetPaceSecondsPerKm`: pace alvo (s/km), ou None se usar RPE;
- `targetRpe`: RPE alvo;
- `restSeconds`: descanso entre repetições (s);
- `notes`: instrução curta.
"""

from typing import Optional

RUN_TEMPLATES: dict[str, list[dict]] = {
    "beginner": [
        {
            "exerciseId": "run_easy",
            "exerciseName": "Corrida leve",
            "metricType": "distance_duration",
            "sets": 1,
            "targetReps": "",
            "targetDistanceMeters": 3000,
            "targetPaceSecondsPerKm": None,
            "targetRpe": 6.0,
            "restSeconds": 0,
            "notes": "Aquecimento 5 min + corrida leve contínua + alongamento 5 min.",
        },
        {
            "exerciseId": "run_walk",
            "exerciseName": "Caminhada e corrida",
            "metricType": "distance_duration",
            "sets": 6,
            "targetReps": "",
            "targetDistanceMeters": 800,
            "targetPaceSecondsPerKm": None,
            "targetRpe": 5.5,
            "restSeconds": 120,
            "notes": "1 km: 4 min corrida leve + 2 min caminhada, repetir 6x.",
        },
    ],
    "intermediate": [
        {
            "exerciseId": "run_interval",
            "exerciseName": "5 x 1 km",
            "metricType": "distance_duration",
            "sets": 5,
            "targetReps": "",
            "targetDistanceMeters": 1000,
            "targetPaceSecondsPerKm": None,
            "targetRpe": 8.0,
            "restSeconds": 120,
            "notes": "Aquecimento 10 min + 5 x 1 km forte + 2 min trote entre blocos.",
        },
        {
            "exerciseId": "run_tempo",
            "exerciseName": "Ritmo de prova",
            "metricType": "distance_duration",
            "sets": 1,
            "targetReps": "",
            "targetDistanceMeters": 5000,
            "targetPaceSecondsPerKm": None,
            "targetRpe": 7.5,
            "restSeconds": 0,
            "notes": "Aquecimento 10 min + 5 km em ritmo constante + 5 min alongamento.",
        },
        {
            "exerciseId": "run_long",
            "exerciseName": "Longão",
            "metricType": "distance_duration",
            "sets": 1,
            "targetReps": "",
            "targetDistanceMeters": 10000,
            "targetPaceSecondsPerKm": None,
            "targetRpe": 6.5,
            "restSeconds": 0,
            "notes": "10 km em ritmo leve e constante, foco em resistência aeróbia.",
        },
    ],
    "advanced": [
        {
            "exerciseId": "run_interval",
            "exerciseName": "10 x 1 km",
            "metricType": "distance_duration",
            "sets": 10,
            "targetReps": "",
            "targetDistanceMeters": 1000,
            "targetPaceSecondsPerKm": None,
            "targetRpe": 8.5,
            "restSeconds": 90,
            "notes": "Aquecimento 15 min + 10 x 1 km forte + 90 s trote entre blocos.",
        },
        {
            "exerciseId": "run_long",
            "exerciseName": "Longão",
            "metricType": "distance_duration",
            "sets": 1,
            "targetReps": "",
            "targetDistanceMeters": 15000,
            "targetPaceSecondsPerKm": None,
            "targetRpe": 6.5,
            "restSeconds": 0,
            "notes": "15 km em ritmo leve e constante, foco em resistência aeróbia.",
        },
    ],
}

TEMPLATE_ORDER = ["run_easy", "run_interval", "run_tempo", "run_long"]


def pace_from_5k(recent5k_seconds: Optional[float]) -> Optional[int]:
    """Converte o benchmark de 5 km em pace alvo (s/km).

    Retorna None quando o benchmark é inválido (0, negativo, > 3 h), para que
    o caller use RPE em vez de pace fixo.
    """
    if recent5k_seconds is None:
        return None
    try:
        value = float(recent5k_seconds)
    except (TypeError, ValueError):
        return None
    if value <= 0.0 or value > 10800.0:
        return None
    pace = value / 5.0
    return int(round(pace))


def _rotate_template(templates: list[dict], session_index: int) -> dict:
    """Rotaciona os templates por sessão (leve, intervalado, ritmo, longão)."""
    if not templates:
        return {}
    return dict(templates[session_index % len(templates)])


def _scale_sets(base_sets: int, duration_minutes: int) -> int:
    """Escala o número de intervalos pela duração, entre 3 e 20."""
    import math

    scaled = int(round(base_sets * duration_minutes / 60.0))
    return max(3, min(20, scaled))


def _scale_distance(base_meters: Optional[int], duration_minutes: int) -> Optional[float]:
    """Escala a distância proporcionalmente, entre 1 km e 21 km."""
    if base_meters is None:
        return None
    meters = float(base_meters) * duration_minutes / 60.0
    meters = max(1000.0, min(21000.0, meters))
    return meters


def template_for(
    level: str,
    recent5k_seconds: Optional[float] = None,
    session_index: int = 0,
    duration_minutes: int = 40,
) -> dict:
    """Escolhe um template de corrida de acordo com o nível e o benchmark.

    Rotaciona os templates por `session_index` (leve, intervalado, ritmo,
    longão) em vez de repetir sempre o primeiro. Escala `sets` e distância pela
    duração da sessão.

    Quando `recent5k_seconds` é válido, ajusta `targetPaceSecondsPerKm` com
    base no pace real do usuário (ligeiramente mais rápido para progressão).
    Iniciantes sem benchmark caminham/correm (`run_walk`) em vez de correr
    contínuo.
    """
    level_key = (level or "beginner").lower()
    templates = RUN_TEMPLATES.get(level_key, RUN_TEMPLATES["beginner"])
    block = _rotate_template(templates, session_index)

    if level_key == "beginner" and recent5k_seconds is None:
        for t in templates:
            if t["exerciseId"] == "run_walk":
                block = dict(t)
                break

    duration = max(5, min(120, duration_minutes))

    if block.get("exerciseId", "").startswith("run_interval"):
        block["sets"] = _scale_sets(int(block.get("sets", 5)), duration)
    else:
        block["sets"] = max(1, int(block.get("sets", 1)))

    block["targetDistanceMeters"] = _scale_distance(
        block.get("targetDistanceMeters"), duration
    )

    pace = pace_from_5k(recent5k_seconds)
    if pace is not None:
        # Progredir levemente acima do pace atual (menos segundos = mais rápido).
        block["targetPaceSecondsPerKm"] = max(180, int(pace * 0.95))
        block["targetRpe"] = block.get("targetRpe", 7.0)
    return block
