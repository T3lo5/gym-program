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
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH, timeout=30)
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


def init_db() -> None:
    with _lock, get_connection() as conn:
        conn.executescript(SCHEMA)


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
                plan_id, local_id, name, day_order, notes, is_rest_day
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                plan_id,
                int(day.get("localId", 0)),
                day.get("name", ""),
                int(day.get("dayOrder", 0)),
                day.get("notes", ""),
                1 if day.get("isRestDay") else 0,
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
                    primary_muscles, equipment
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                ),
            )
    return plan_id


def _upsert_session(conn: sqlite3.Connection, device_id: str, session: Dict[str, Any], synced_at: str) -> int:
    conn.execute(
        """
        INSERT INTO synced_sessions (
            device_id, local_id, plan_local_id, day_local_id, plan_name,
            day_name, status, started_at, ended_at, total_duration_seconds,
            notes, synced_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            synced_at = excluded.synced_at
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
            SELECT local_id, name, description, goal, days_per_week, is_active,
                   generated_by_ai, created_at, updated_at, version, next_review_at,
                   review_after_weeks, source, synced_at
            FROM synced_plans WHERE device_id = ?
            """,
            (device_id,),
        ).fetchall()

        plan_rows = [dict(r) for r in plans]
        plan_ids = [str(r["local_id"]) for r in plan_rows]

        days: List[Dict[str, Any]] = []
        day_ids: List[str] = []
        if plan_ids:
            placeholders = ",".join("?" for _ in plan_ids)
            day_rows = conn.execute(
                f"""
                SELECT id, plan_id, local_id, name, day_order, notes, is_rest_day
                FROM synced_plan_days
                WHERE plan_id IN ({placeholders})
                """,
                [int(pid) for pid in plan_ids],
            ).fetchall()
            days = [dict(r) for r in day_rows]
            day_ids = [str(r["local_id"]) for r in days]

        plan_exercises: List[Dict[str, Any]] = []
        if day_ids:
            placeholders = ",".join("?" for _ in day_ids)
            day_row_ids = [str(d["id"]) for d in days]
            ex_rows = conn.execute(
                f"""
                SELECT id, day_id, exercise_id, exercise_order, sets, target_reps,
                       target_rpe, rest_seconds, notes, is_superset_with_previous,
                       exercise_name, primary_muscles, equipment
                FROM synced_plan_exercises
                WHERE day_id IN ({placeholders})
                ORDER BY day_id, exercise_order
                """,
                day_row_ids,
            ).fetchall()
            plan_exercises = [dict(r) for r in ex_rows]

        sessions: List[Dict[str, Any]] = []
        session_ids: List[str] = []
        sess_rows = conn.execute(
            """
            SELECT id, device_id, local_id, plan_local_id, day_local_id, plan_name,
                   day_name, status, started_at, ended_at, total_duration_seconds,
                   notes, synced_at
            FROM synced_sessions WHERE device_id = ?
            """,
            (device_id,),
        ).fetchall()
        sessions = [dict(r) for r in sess_rows]
        session_ids = [str(r["local_id"]) for r in sessions]

        session_exercises: List[Dict[str, Any]] = []
        if session_ids:
            placeholders = ",".join("?" for _ in session_ids)
            sess_ex_rows = conn.execute(
                f"""
                SELECT id, session_id, local_id, exercise_id, exercise_name,
                       exercise_order, planned_sets, completed_sets, started_at, ended_at
                FROM synced_session_exercises
                WHERE session_id IN ({placeholders})
                ORDER BY session_id, exercise_order
                """,
                [str(r["id"]) for r in sessions],
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

    return {
        "deviceId": device_id,
        "plans": plan_rows,
        "days": days,
        "planExercises": plan_exercises,
        "sessions": sessions,
        "sessionExercises": session_exercises,
        "setLogs": set_logs,
    }
