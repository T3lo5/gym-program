"""Persistência SQLite do backend.

O banco guarda:
- cada plano gerado pelo backend (histórico de gerações);
- o snapshot enviado pelo app quando o usuário clica em "Sincronizar".

O banco é local e fica em back/data/treinos.db por padrão. No Docker, o
docker-compose.yml monta esse diretório em ./back/data.
"""

import json
import os
import sqlite3
import threading
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

DEFAULT_DB_PATH = Path(__file__).resolve().parent.parent / "data" / "treinos.db"
DB_PATH = Path(os.getenv("DATABASE_PATH", str(DEFAULT_DB_PATH)))
_lock = threading.Lock()


def _get_db_path() -> Path:
    return Path(DB_PATH) if not isinstance(DB_PATH, Path) else DB_PATH


SCHEMA = """
CREATE TABLE IF NOT EXISTS plan_generations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    request_json TEXT NOT NULL,
    plan_json TEXT NOT NULL,
    model_used TEXT NOT NULL,
    source TEXT NOT NULL,
    next_review_at TEXT,
    review_after_weeks INTEGER NOT NULL DEFAULT 4,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS devices (
    device_id TEXT PRIMARY KEY,
    last_sync_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS synced_plans (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    local_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    goal TEXT NOT NULL DEFAULT '',
    days_per_week INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 0,
    generated_by_ai INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 1,
    next_review_at TEXT,
    review_after_weeks INTEGER NOT NULL DEFAULT 4,
    source TEXT NOT NULL DEFAULT 'local',
    synced_at TEXT NOT NULL,
    UNIQUE(device_id, local_id)
);

CREATE TABLE IF NOT EXISTS synced_plan_days (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_id INTEGER NOT NULL REFERENCES synced_plans(id) ON DELETE CASCADE,
    local_id INTEGER NOT NULL,
    name TEXT NOT NULL DEFAULT '',
    day_order INTEGER NOT NULL DEFAULT 0,
    notes TEXT NOT NULL DEFAULT '',
    is_rest_day INTEGER NOT NULL DEFAULT 0,
    modality TEXT NOT NULL DEFAULT 'strength',
    weekday INTEGER,
    UNIQUE(plan_id, local_id)
);

CREATE TABLE IF NOT EXISTS synced_plan_exercises (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    day_id INTEGER NOT NULL REFERENCES synced_plan_days(id) ON DELETE CASCADE,
    exercise_id TEXT NOT NULL,
    exercise_order INTEGER NOT NULL DEFAULT 0,
    sets INTEGER NOT NULL DEFAULT 3,
    target_reps TEXT NOT NULL DEFAULT '',
    target_rpe REAL,
    rest_seconds INTEGER NOT NULL DEFAULT 90,
    notes TEXT NOT NULL DEFAULT '',
    is_superset_with_previous INTEGER NOT NULL DEFAULT 0,
    exercise_name TEXT NOT NULL DEFAULT '',
    primary_muscles TEXT NOT NULL DEFAULT '[]',
    equipment TEXT NOT NULL DEFAULT '',
    metric_type TEXT NOT NULL DEFAULT 'reps_weight',
    target_duration_seconds INTEGER,
    target_distance_meters REAL,
    target_pace_seconds_per_km INTEGER,
    target_hr_zone TEXT,
    UNIQUE(day_id, exercise_order)
);

CREATE TABLE IF NOT EXISTS synced_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    local_id INTEGER NOT NULL,
    plan_local_id INTEGER NOT NULL DEFAULT 0,
    day_local_id INTEGER NOT NULL DEFAULT 0,
    plan_name TEXT NOT NULL DEFAULT '',
    day_name TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT '',
    started_at INTEGER NOT NULL DEFAULT 0,
    ended_at INTEGER,
    total_duration_seconds INTEGER NOT NULL DEFAULT 0,
    notes TEXT NOT NULL DEFAULT '',
    synced_at TEXT NOT NULL,
    modality TEXT NOT NULL DEFAULT 'strength',
    source TEXT NOT NULL DEFAULT 'plan',
    title TEXT NOT NULL DEFAULT '',
    total_distance_meters REAL,
    pool_length_meters INTEGER,
    stroke TEXT,
    session_rpe REAL,
    UNIQUE(device_id, local_id)
);

CREATE TABLE IF NOT EXISTS synced_session_exercises (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id INTEGER NOT NULL REFERENCES synced_sessions(id) ON DELETE CASCADE,
    local_id INTEGER NOT NULL,
    exercise_id TEXT NOT NULL,
    exercise_name TEXT NOT NULL DEFAULT '',
    exercise_order INTEGER NOT NULL DEFAULT 0,
    planned_sets INTEGER NOT NULL DEFAULT 0,
    completed_sets INTEGER NOT NULL DEFAULT 0,
    started_at INTEGER NOT NULL DEFAULT 0,
    ended_at INTEGER,
    UNIQUE(session_id, local_id)
);

CREATE TABLE IF NOT EXISTS synced_set_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_exercise_id INTEGER NOT NULL
        REFERENCES synced_session_exercises(id) ON DELETE CASCADE,
    local_id INTEGER NOT NULL,
    set_number INTEGER NOT NULL DEFAULT 0,
    reps INTEGER,
    weight_kg REAL,
    rpe REAL,
    duration_seconds INTEGER,
    distance_meters REAL,
    is_completed INTEGER NOT NULL DEFAULT 0,
    is_warmup INTEGER NOT NULL DEFAULT 0,
    rest_taken_seconds INTEGER,
    completed_at INTEGER,
    UNIQUE(session_exercise_id, local_id)
);

CREATE INDEX IF NOT EXISTS idx_synced_plans_device
    ON synced_plans(device_id);
CREATE INDEX IF NOT EXISTS idx_synced_sessions_device
    ON synced_sessions(device_id);
CREATE INDEX IF NOT EXISTS idx_generations_device
    ON plan_generations(device_id);
"""


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


@contextmanager
def get_connection():
    """Abre uma conexão por operação (seguro para o uvicorn/threads)."""
    db_path = _get_db_path()
    db_path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(db_path, timeout=30)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def _ensure_columns(conn: sqlite3.Connection, table: str, columns: Dict[str, str]) -> None:
    existing = {row["name"] for row in conn.execute(f"PRAGMA table_info({table})").fetchall()}
    for name, definition in columns.items():
        if name not in existing:
            conn.execute(f"ALTER TABLE {table} ADD COLUMN {name} {definition}")


def init_db() -> None:
    with _lock, get_connection() as conn:
        conn.executescript(SCHEMA)
        _ensure_columns(
            conn,
            "synced_plan_days",
            {
                "modality": "TEXT NOT NULL DEFAULT 'strength'",
                "weekday": "INTEGER",
            },
        )
        _ensure_columns(
            conn,
            "synced_plan_exercises",
            {
                "metric_type": "TEXT NOT NULL DEFAULT 'reps_weight'",
                "target_duration_seconds": "INTEGER",
                "target_distance_meters": "REAL",
                "target_pace_seconds_per_km": "INTEGER",
                "target_hr_zone": "TEXT",
            },
        )
        _ensure_columns(
            conn,
            "synced_sessions",
            {
                "modality": "TEXT NOT NULL DEFAULT 'strength'",
                "source": "TEXT NOT NULL DEFAULT 'plan'",
                "title": "TEXT NOT NULL DEFAULT ''",
                "total_distance_meters": "REAL",
                "pool_length_meters": "INTEGER",
                "stroke": "TEXT",
                "session_rpe": "REAL",
            },
        )


def save_generation(
    device_id: str,
    request_payload: Dict[str, Any],
    plan_payload: Dict[str, Any],
    model_used: str,
    source: str,
    next_review_at: str,
    review_after_weeks: int,
) -> int:
    """Registra uma geração de plano feita pelo backend."""
    with _lock, get_connection() as conn:
        cursor = conn.execute(
            """
            INSERT INTO plan_generations (
                device_id, request_json, plan_json, model_used, source,
                next_review_at, review_after_weeks, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                device_id,
                json.dumps(request_payload, ensure_ascii=False),
                json.dumps(plan_payload, ensure_ascii=False),
                model_used,
                source,
                next_review_at,
                review_after_weeks,
                _now_iso(),
            ),
        )
        return int(cursor.lastrowid)


def _upsert_plan(conn: sqlite3.Connection, device_id: str, plan: Dict[str, Any], synced_at: str) -> int:
    conn.execute(
        """
        INSERT INTO synced_plans (
            device_id, local_id, name, description, goal, days_per_week,
            is_active, generated_by_ai, created_at, updated_at, version,
            next_review_at, review_after_weeks, source, synced_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(device_id, local_id) DO UPDATE SET
            name = excluded.name,
            description = excluded.description,
            goal = excluded.goal,
            days_per_week = excluded.days_per_week,
            is_active = excluded.is_active,
            generated_by_ai = excluded.generated_by_ai,
            created_at = excluded.created_at,
            updated_at = excluded.updated_at,
            version = excluded.version,
            next_review_at = excluded.next_review_at,
            review_after_weeks = excluded.review_after_weeks,
            source = excluded.source,
            synced_at = excluded.synced_at
        """,
        (
            device_id,
            int(plan.get("localId", 0)),
            plan.get("name", ""),
            plan.get("description", ""),
            plan.get("goal", ""),
            int(plan.get("daysPerWeek", 0)),
            1 if plan.get("isActive") else 0,
            1 if plan.get("generatedByAi") else 0,
            int(plan.get("createdAt", 0)),
            int(plan.get("updatedAt", 0)),
            int(plan.get("version", 1)),
            plan.get("nextReviewAt"),
            int(plan.get("reviewAfterWeeks", 4)),
            plan.get("source", "local"),
            synced_at,
        ),
    )
    row = conn.execute(
        "SELECT id FROM synced_plans WHERE device_id = ? AND local_id = ?",
        (device_id, int(plan.get("localId", 0))),
    ).fetchone()
    plan_id = int(row["id"])

    conn.execute("DELETE FROM synced_plan_days WHERE plan_id = ?", (plan_id,))
    for day in plan.get("days", []):
        cursor = conn.execute(
            """
            INSERT INTO synced_plan_days (
                plan_id, local_id, name, day_order, notes, is_rest_day, modality, weekday
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                plan_id,
                int(day.get("localId", 0)),
                day.get("name", ""),
                int(day.get("dayOrder", 0)),
                day.get("notes", ""),
                1 if day.get("isRestDay") else 0,
                day.get("modality", "strength"),
                day.get("weekday"),
            ),
        )
        day_id = int(cursor.lastrowid)
        for exercise in day.get("exercises", []):
            conn.execute(
                """
                INSERT INTO synced_plan_exercises (
                    day_id, exercise_id, exercise_order, sets, target_reps,
                    target_rpe, rest_seconds, notes,
                    is_superset_with_previous, exercise_name,
                    primary_muscles, equipment, metric_type,
                    target_duration_seconds, target_distance_meters,
                    target_pace_seconds_per_km, target_hr_zone
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    day_id,
                    exercise.get("exerciseId", ""),
                    int(exercise.get("exerciseOrder", 0)),
                    int(exercise.get("sets", 3)),
                    exercise.get("targetReps", ""),
                    exercise.get("targetRpe"),
                    int(exercise.get("restSeconds", 90)),
                    exercise.get("notes", ""),
                    1 if exercise.get("isSupersetWithPrevious") else 0,
                    exercise.get("exerciseName", ""),
                    json.dumps(exercise.get("primaryMuscles", []), ensure_ascii=False),
                    exercise.get("equipment", ""),
                    exercise.get("metricType", "reps_weight"),
                    exercise.get("targetDurationSeconds"),
                    exercise.get("targetDistanceMeters"),
                    exercise.get("targetPaceSecondsPerKm"),
                    exercise.get("targetHrZone"),
                ),
            )
    return plan_id


def _upsert_session(conn: sqlite3.Connection, device_id: str, session: Dict[str, Any], synced_at: str) -> int:
    conn.execute(
        """
        INSERT INTO synced_sessions (
            device_id, local_id, plan_local_id, day_local_id, plan_name,
            day_name, status, started_at, ended_at, total_duration_seconds,
            notes, synced_at, modality, source, title,
            total_distance_meters, pool_length_meters, stroke, session_rpe
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(device_id, local_id) DO UPDATE SET
            plan_local_id = excluded.plan_local_id,
            day_local_id = excluded.day_local_id,
            plan_name = excluded.plan_name,
            day_name = excluded.day_name,
            status = excluded.status,
            started_at = excluded.started_at,
            ended_at = excluded.ended_at,
            total_duration_seconds = excluded.total_duration_seconds,
            notes = excluded.notes,
            synced_at = excluded.synced_at,
            modality = excluded.modality,
            source = excluded.source,
            title = excluded.title,
            total_distance_meters = excluded.total_distance_meters,
            pool_length_meters = excluded.pool_length_meters,
            stroke = excluded.stroke,
            session_rpe = excluded.session_rpe
        """,
        (
            device_id,
            int(session.get("localId", 0)),
            int(session.get("planLocalId", 0)),
            int(session.get("dayLocalId", 0)),
            session.get("planName", ""),
            session.get("dayName", ""),
            session.get("status", ""),
            int(session.get("startedAt", 0)),
            session.get("endedAt"),
            int(session.get("totalDurationSeconds", 0)),
            session.get("notes", ""),
            synced_at,
            session.get("modality", "strength"),
            session.get("source", "plan"),
            session.get("title", ""),
            session.get("totalDistanceMeters"),
            session.get("poolLengthMeters"),
            session.get("stroke"),
            session.get("sessionRpe"),
        ),
    )
    row = conn.execute(
        "SELECT id FROM synced_sessions WHERE device_id = ? AND local_id = ?",
        (device_id, int(session.get("localId", 0))),
    ).fetchone()
    session_id = int(row["id"])

    conn.execute(
        "DELETE FROM synced_session_exercises WHERE session_id = ?",
        (session_id,),
    )
    for exercise in session.get("exercises", []):
        cursor = conn.execute(
            """
            INSERT INTO synced_session_exercises (
                session_id, local_id, exercise_id, exercise_name, exercise_order,
                planned_sets, completed_sets, started_at, ended_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                session_id,
                int(exercise.get("localId", 0)),
                exercise.get("exerciseId", ""),
                exercise.get("exerciseName", ""),
                int(exercise.get("exerciseOrder", 0)),
                int(exercise.get("plannedSets", 0)),
                int(exercise.get("completedSets", 0)),
                int(exercise.get("startedAt", 0)),
                exercise.get("endedAt"),
            ),
        )
        exercise_id = int(cursor.lastrowid)
        for set_log in exercise.get("sets", []):
            conn.execute(
                """
                INSERT INTO synced_set_logs (
                    session_exercise_id, local_id, set_number, reps, weight_kg,
                    rpe, duration_seconds, distance_meters, is_completed,
                    is_warmup, rest_taken_seconds, completed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    exercise_id,
                    int(set_log.get("localId", 0)),
                    int(set_log.get("setNumber", 0)),
                    set_log.get("reps"),
                    set_log.get("weightKg"),
                    set_log.get("rpe"),
                    set_log.get("durationSeconds"),
                    set_log.get("distanceMeters"),
                    1 if set_log.get("isCompleted") else 0,
                    1 if set_log.get("isWarmup") else 0,
                    set_log.get("restTakenSeconds"),
                    set_log.get("completedAt"),
                ),
            )
    return session_id


def sync_snapshot(payload: Dict[str, Any]) -> Dict[str, Any]:
    """Substitui os dados do dispositivo pelo snapshot atual do app."""
    device_id = str(payload.get("deviceId") or "unknown")
    synced_at = payload.get("syncedAt") or _now_iso()
    plans: List[Dict[str, Any]] = payload.get("plans", [])
    sessions: List[Dict[str, Any]] = payload.get("sessions", [])
    set_log_count = 0

    with _lock, get_connection() as conn:
        conn.execute(
            """
            INSERT INTO devices (device_id, last_sync_at, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(device_id) DO UPDATE SET
                last_sync_at = excluded.last_sync_at,
                updated_at = excluded.updated_at
            """,
            (device_id, synced_at, _now_iso()),
        )
        for plan in plans:
            _upsert_plan(conn, device_id, plan, synced_at)
        for session in sessions:
            _upsert_session(conn, device_id, session, synced_at)
            for exercise in session.get("exercises", []):
                set_log_count += len(exercise.get("sets", []))

    return {
        "ok": True,
        "plans": len(plans),
        "sessions": len(sessions),
        "setLogs": set_log_count,
        "syncedAt": synced_at,
    }


def sync_status(device_id: str) -> Dict[str, Any]:
    with get_connection() as conn:
        plan_count = conn.execute(
            "SELECT COUNT(*) AS n FROM synced_plans WHERE device_id = ?",
            (device_id,),
        ).fetchone()["n"]
        session_count = conn.execute(
            "SELECT COUNT(*) AS n FROM synced_sessions WHERE device_id = ?",
            (device_id,),
        ).fetchone()["n"]
        set_count = conn.execute(
            """
            SELECT COUNT(*) AS n
            FROM synced_set_logs sl
            JOIN synced_session_exercises se ON se.id = sl.session_exercise_id
            JOIN synced_sessions s ON s.id = se.session_id
            WHERE s.device_id = ?
            """,
            (device_id,),
        ).fetchone()["n"]
        device = conn.execute(
            "SELECT last_sync_at FROM devices WHERE device_id = ?",
            (device_id,),
        ).fetchone()
    return {
        "deviceId": device_id,
        "plans": int(plan_count),
        "sessions": int(session_count),
        "setLogs": int(set_count),
        "lastSyncAt": device["last_sync_at"] if device else None,
    }


def list_synced_plans(device_id: str) -> List[Dict[str, Any]]:
    with get_connection() as conn:
        rows = conn.execute(
            """
            SELECT local_id, name, goal, days_per_week, is_active,
                   next_review_at, updated_at, synced_at
            FROM synced_plans
            WHERE device_id = ?
            ORDER BY updated_at DESC
            """,
            (device_id,),
        ).fetchall()
    return [dict(row) for row in rows]


def get_sync_download_data(device_id: str) -> Dict[str, Any]:
    """Retorna todos os dados sincronizados de um dispositivo para download/restauração."""
    with get_connection() as conn:
        plans = conn.execute(
            """
            SELECT id, local_id, name, description, goal, days_per_week, is_active,
                   generated_by_ai, created_at, updated_at, version, next_review_at,
                   review_after_weeks, source, synced_at
            FROM synced_plans WHERE device_id = ?
            """,
            (device_id,),
        ).fetchall()

        plan_rows = [dict(r) for r in plans]
        plan_ids_internal = [r["id"] for r in plan_rows]

        days: List[Dict[str, Any]] = []
        day_ids_internal: List[str] = []
        if plan_ids_internal:
            placeholders = ",".join("?" for _ in plan_ids_internal)
            day_rows = conn.execute(
                f"""
                SELECT id, plan_id, local_id, name, day_order, notes, is_rest_day, modality, weekday
                FROM synced_plan_days
                WHERE plan_id IN ({placeholders})
                """,
                plan_ids_internal,
            ).fetchall()
            days = [dict(r) for r in day_rows]
            day_ids_internal = [str(r["id"]) for r in days]

        plan_exercises: List[Dict[str, Any]] = []
        if day_ids_internal:
            placeholders = ",".join("?" for _ in day_ids_internal)
            ex_rows = conn.execute(
                f"""
                SELECT id, day_id, exercise_id, exercise_order, sets, target_reps,
                       target_rpe, rest_seconds, notes, is_superset_with_previous,
                       exercise_name, primary_muscles, equipment, metric_type,
                       target_duration_seconds, target_distance_meters,
                       target_pace_seconds_per_km, target_hr_zone
                FROM synced_plan_exercises
                WHERE day_id IN ({placeholders})
                ORDER BY day_id, exercise_order
                """,
                day_ids_internal,
            ).fetchall()
            plan_exercises = [dict(r) for r in ex_rows]

        sessions: List[Dict[str, Any]] = []
        session_ids_internal: List[str] = []
        sess_rows = conn.execute(
            """
            SELECT id, device_id, local_id, plan_local_id, day_local_id, plan_name,
                   day_name, status, started_at, ended_at, total_duration_seconds,
                   notes, synced_at, modality, source, title,
                   total_distance_meters, pool_length_meters, stroke, session_rpe
            FROM synced_sessions WHERE device_id = ?
            """,
            (device_id,),
        ).fetchall()
        sessions = [dict(r) for r in sess_rows]
        session_ids_internal = [str(r["id"]) for r in sessions]

        session_exercises: List[Dict[str, Any]] = []
        if session_ids_internal:
            placeholders = ",".join("?" for _ in session_ids_internal)
            sess_ex_rows = conn.execute(
                f"""
                SELECT id, session_id, local_id, exercise_id, exercise_name,
                       exercise_order, planned_sets, completed_sets, started_at, ended_at
                FROM synced_session_exercises
                WHERE session_id IN ({placeholders})
                ORDER BY session_id, exercise_order
                """,
                session_ids_internal,
            ).fetchall()
            session_exercises = [dict(r) for r in sess_ex_rows]

        set_logs: List[Dict[str, Any]] = []
        if session_exercises:
            placeholders = ",".join("?" for _ in session_exercises)
            se_ids = [str(r["id"]) for r in session_exercises]
            set_rows = conn.execute(
                f"""
                SELECT id, session_exercise_id, local_id, set_number, reps, weight_kg,
                       rpe, duration_seconds, distance_meters, is_completed,
                       is_warmup, rest_taken_seconds, completed_at
                FROM synced_set_logs
                WHERE session_exercise_id IN ({placeholders})
                ORDER BY session_exercise_id, local_id
                """,
                se_ids,
            ).fetchall()
            set_logs = [dict(r) for r in set_rows]

    plan_local_by_id = {row["id"]: row["local_id"] for row in plan_rows}
    day_local_by_id = {row["id"]: row["local_id"] for row in days}
    session_local_by_id = {row["id"]: row["local_id"] for row in sessions}

    def _bool(value: Any) -> bool:
        return bool(value)

    return {
        "deviceId": device_id,
        "plans": [
            {
                "localId": row["local_id"],
                "name": row["name"],
                "description": row["description"],
                "goal": row["goal"],
                "daysPerWeek": row["days_per_week"],
                "isActive": _bool(row["is_active"]),
                "generatedByAi": _bool(row["generated_by_ai"]),
                "createdAt": row["created_at"],
                "updatedAt": row["updated_at"],
                "version": row["version"],
                "nextReviewAt": row["next_review_at"],
                "reviewAfterWeeks": row["review_after_weeks"],
                "source": row["source"],
                "syncedAt": row["synced_at"],
            }
            for row in plan_rows
        ],
        "days": [
            {
                "id": row["local_id"],
                "planId": plan_local_by_id[row["plan_id"]],
                "localId": row["local_id"],
                "name": row["name"],
                "dayOrder": row["day_order"],
                "notes": row["notes"],
                "isRestDay": _bool(row["is_rest_day"]),
                "modality": row["modality"],
                "weekday": row["weekday"],
            }
            for row in days
        ],
        "planExercises": [
            {
                "id": row["id"],
                "dayId": day_local_by_id[row["day_id"]],
                "exerciseId": row["exercise_id"],
                "exerciseOrder": row["exercise_order"],
                "sets": row["sets"],
                "targetReps": row["target_reps"],
                "targetRpe": row["target_rpe"],
                "restSeconds": row["rest_seconds"],
                "notes": row["notes"],
                "isSupersetWithPrevious": _bool(row["is_superset_with_previous"]),
                "exerciseName": row["exercise_name"],
                "primaryMuscles": row["primary_muscles"],
                "equipment": row["equipment"],
                "metricType": row["metric_type"],
                "targetDurationSeconds": row["target_duration_seconds"],
                "targetDistanceMeters": row["target_distance_meters"],
                "targetPaceSecondsPerKm": row["target_pace_seconds_per_km"],
                "targetHrZone": row["target_hr_zone"],
            }
            for row in plan_exercises
        ],
        "sessions": [
            {
                "id": row["id"],
                "deviceId": row["device_id"],
                "localId": row["local_id"],
                "planLocalId": row["plan_local_id"],
                "dayLocalId": row["day_local_id"],
                "planName": row["plan_name"],
                "dayName": row["day_name"],
                "status": row["status"],
                "startedAt": row["started_at"],
                "endedAt": row["ended_at"],
                "totalDurationSeconds": row["total_duration_seconds"],
                "notes": row["notes"],
                "syncedAt": row["synced_at"],
                "modality": row["modality"],
                "source": row["source"],
                "title": row["title"],
                "totalDistanceMeters": row["total_distance_meters"],
                "poolLengthMeters": row["pool_length_meters"],
                "stroke": row["stroke"],
                "sessionRpe": row["session_rpe"],
            }
            for row in sessions
        ],
        "sessionExercises": [
            {
                "id": row["id"],
                "sessionId": session_local_by_id[row["session_id"]],
                "localId": row["local_id"],
                "exerciseId": row["exercise_id"],
                "exerciseName": row["exercise_name"],
                "exerciseOrder": row["exercise_order"],
                "plannedSets": row["planned_sets"],
                "completedSets": row["completed_sets"],
                "startedAt": row["started_at"],
                "endedAt": row["ended_at"],
            }
            for row in session_exercises
        ],
        "setLogs": [
            {
                "id": row["id"],
                "sessionExerciseId": row["session_exercise_id"],
                "localId": row["local_id"],
                "setNumber": row["set_number"],
                "reps": row["reps"],
                "weightKg": row["weight_kg"],
                "rpe": row["rpe"],
                "durationSeconds": row["duration_seconds"],
                "distanceMeters": row["distance_meters"],
                "isCompleted": _bool(row["is_completed"]),
                "isWarmup": _bool(row["is_warmup"]),
                "restTakenSeconds": row["rest_taken_seconds"],
                "completedAt": row["completed_at"],
            }
            for row in set_logs
        ],
    }
