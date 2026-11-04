import os
import tempfile

from fastapi.testclient import TestClient

from app import database as db
from app import main


def setup_test_db():
    fd, path = tempfile.mkstemp(suffix=".db")
    os.close(fd)
    db.DB_PATH = path
    db.init_db()
    return path


def test_payload_v1_sem_campos_novos():
    setup_test_db()
    client = TestClient(main.app)
    payload = {
        "deviceId": "d1",
        "syncedAt": "2026-01-01T00:00:00Z",
        "plans": [
            {
                "localId": 1,
                "name": "P",
                "description": "d",
                "goal": "hypertrophy",
                "daysPerWeek": 4,
                "isActive": True,
                "generatedByAi": False,
                "createdAt": 1,
                "updatedAt": 2,
                "version": 1,
                "nextReviewAt": None,
                "reviewAfterWeeks": 4,
                "source": "local",
                "days": [
                    {
                        "localId": 10,
                        "name": "A",
                        "dayOrder": 0,
                        "notes": "",
                        "isRestDay": False,
                        "exercises": [
                            {
                                "exerciseId": "e1",
                                "exerciseOrder": 0,
                                "sets": 3,
                                "targetReps": "8-12",
                                "targetRpe": None,
                                "restSeconds": 90,
                                "notes": "",
                                "isSupersetWithPrevious": False,
                                "exerciseName": "Ex",
                                "primaryMuscles": ["peito"],
                                "equipment": "barra",
                            }
                        ],
                    }
                ],
            }
        ],
        "sessions": [
            {
                "localId": 50,
                "planLocalId": 1,
                "dayLocalId": 10,
                "planName": "P",
                "dayName": "A",
                "status": "completed",
                "startedAt": 1,
                "endedAt": 10,
                "totalDurationSeconds": 9,
                "notes": "",
                "exercises": [],
            }
        ],
    }
    r = client.post("/v1/sync", json=payload)
    assert r.status_code == 200
    assert r.json()["ok"] is True


def test_payload_v2_com_campos_novos():
    setup_test_db()
    client = TestClient(main.app)
    payload = {
        "deviceId": "d2",
        "syncedAt": "2026-01-01T00:00:00Z",
        "plans": [
            {
                "localId": 1,
                "name": "P",
                "description": "d",
                "goal": "hypertrophy",
                "daysPerWeek": 4,
                "isActive": True,
                "generatedByAi": False,
                "createdAt": 1,
                "updatedAt": 2,
                "version": 1,
                "nextReviewAt": None,
                "reviewAfterWeeks": 4,
                "source": "local",
                "days": [
                    {
                        "localId": 10,
                        "name": "A",
                        "dayOrder": 0,
                        "notes": "",
                        "isRestDay": False,
                        "modality": "run",
                        "weekday": 1,
                        "exercises": [
                            {
                                "exerciseId": "e1",
                                "exerciseOrder": 0,
                                "sets": 1,
                                "targetReps": "30m",
                                "targetRpe": None,
                                "restSeconds": 60,
                                "notes": "",
                                "isSupersetWithPrevious": False,
                                "exerciseName": "Corrida",
                                "primaryMuscles": ["pernas"],
                                "equipment": "nenhum",
                                "metricType": "duration",
                                "targetDurationSeconds": 1800,
                            }
                        ],
                    }
                ],
            }
        ],
        "sessions": [
            {
                "localId": 50,
                "planLocalId": 0,
                "dayLocalId": 0,
                "planName": "",
                "dayName": "",
                "status": "completed",
                "startedAt": 1,
                "endedAt": 60,
                "totalDurationSeconds": 59,
                "notes": "treino avulso",
                "modality": "swim",
                "source": "manual",
                "title": "Natação",
                "totalDistanceMeters": 1500.0,
                "poolLengthMeters": 25,
                "stroke": "crawl",
                "sessionRpe": 7.0,
                "exercises": [],
            }
        ],
    }
    r = client.post("/v1/sync", json=payload)
    assert r.status_code == 200
    assert r.json()["ok"] is True


def test_download_round_trip():
    setup_test_db()
    client = TestClient(main.app)
    payload = {
        "deviceId": "d3",
        "syncedAt": "2026-01-01T00:00:00Z",
        "plans": [
            {
                "localId": 1,
                "name": "P",
                "description": "d",
                "goal": "hypertrophy",
                "daysPerWeek": 4,
                "isActive": True,
                "generatedByAi": False,
                "createdAt": 1,
                "updatedAt": 2,
                "version": 1,
                "nextReviewAt": None,
                "reviewAfterWeeks": 4,
                "source": "local",
                "days": [
                    {
                        "localId": 10,
                        "name": "A",
                        "dayOrder": 0,
                        "notes": "",
                        "isRestDay": False,
                        "modality": "strength",
                        "weekday": None,
                        "exercises": [
                            {
                                "exerciseId": "e1",
                                "exerciseOrder": 0,
                                "sets": 3,
                                "targetReps": "8-12",
                                "targetRpe": None,
                                "restSeconds": 90,
                                "notes": "",
                                "isSupersetWithPrevious": False,
                                "exerciseName": "Ex",
                                "primaryMuscles": ["peito"],
                                "equipment": "barra",
                                "metricType": "reps_weight",
                            }
                        ],
                    }
                ],
            }
        ],
        "sessions": [
            {
                "localId": 50,
                "planLocalId": 0,
                "dayLocalId": 0,
                "planName": "",
                "dayName": "",
                "status": "completed",
                "startedAt": 1,
                "endedAt": 60,
                "totalDurationSeconds": 59,
                "notes": "",
                "modality": "swim",
                "source": "manual",
                "title": "Natação",
                "totalDistanceMeters": 1500.0,
                "poolLengthMeters": 25,
                "stroke": "crawl",
                "sessionRpe": 7.0,
                "exercises": [],
            }
        ],
    }
    r = client.post("/v1/sync", json=payload)
    assert r.status_code == 200
    d = client.get("/v1/sync/download", params={"deviceId": "d3"})
    assert d.status_code == 200
    data = d.json()
    assert len(data["sessions"]) == 1
    session = data["sessions"][0]
    assert session["modality"] == "swim"
    assert session["source"] == "manual"
    assert session["title"] == "Natação"
    assert session["poolLengthMeters"] == 25
    assert session["stroke"] == "crawl"
    assert session["totalDistanceMeters"] == 1500.0
    assert session["sessionRpe"] == 7.0


def test_download_dias_com_local_id_diferente_do_id():
    setup_test_db()
    client = TestClient(main.app)
    payload = {
        "deviceId": "d4",
        "syncedAt": "2026-01-01T00:00:00Z",
        "plans": [
            {
                "localId": 99,
                "name": "P",
                "description": "d",
                "goal": "hypertrophy",
                "daysPerWeek": 4,
                "isActive": True,
                "generatedByAi": False,
                "createdAt": 1,
                "updatedAt": 2,
                "version": 1,
                "nextReviewAt": None,
                "reviewAfterWeeks": 4,
                "source": "local",
                "days": [
                    {
                        "localId": 7,
                        "name": "A",
                        "dayOrder": 0,
                        "notes": "",
                        "isRestDay": False,
                        "modality": "strength",
                        "weekday": None,
                        "exercises": [
                            {
                                "exerciseId": "e1",
                                "exerciseOrder": 0,
                                "sets": 3,
                                "targetReps": "8-12",
                                "targetRpe": None,
                                "restSeconds": 90,
                                "notes": "",
                                "isSupersetWithPrevious": False,
                                "exerciseName": "Ex",
                                "primaryMuscles": ["peito"],
                                "equipment": "barra",
                                "metricType": "reps_weight",
                            }
                        ],
                    }
                ],
            }
        ],
        "sessions": [],
    }
    r = client.post("/v1/sync", json=payload)
    assert r.status_code == 200
    d = client.get("/v1/sync/download", params={"deviceId": "d4"})
    assert d.status_code == 200
    data = d.json()
    assert len(data["days"]) == 1
    assert len(data["planExercises"]) == 1
    day = data["days"][0]
    assert day["id"] == 7
    assert day["localId"] == 7
    assert day["planId"] == 99
    assert day["modality"] == "strength"
    exercise = data["planExercises"][0]
    assert exercise["dayId"] == 7
    assert exercise["metricType"] == "reps_weight"


def test_sessao_manual_round_trip():
    """Registro avulso (H2-5) viaja sem plano nem dia: planLocalId = dayLocalId = 0."""
    setup_test_db()
    client = TestClient(main.app)
    payload = {
        "deviceId": "d5",
        "syncedAt": "2026-11-04T12:00:00Z",
        "plans": [],
        "sessions": [
            {
                "localId": 70,
                "planLocalId": 0,
                "dayLocalId": 0,
                "planName": "",
                "dayName": "Natação",
                "status": "completed",
                "startedAt": 1760000000000,
                "endedAt": 1760002700000,
                "totalDurationSeconds": 2700,
                "notes": "8 x 100 m",
                "modality": "swim",
                "source": "manual",
                "title": "Natação",
                "totalDistanceMeters": 1500.0,
                "poolLengthMeters": 25,
                "stroke": "crawl",
                "sessionRpe": 7.0,
                "exercises": [],
            }
        ],
    }

    r = client.post("/v1/sync", json=payload)
    assert r.status_code == 200
    assert r.json()["plans"] == 0
    assert r.json()["sessions"] == 1

    d = client.get("/v1/sync/download", params={"deviceId": "d5"})
    assert d.status_code == 200
    data = d.json()
    assert data["plans"] == []
    assert len(data["sessions"]) == 1

    session = data["sessions"][0]
    assert session["localId"] == 70
    assert session["planLocalId"] == 0
    assert session["dayLocalId"] == 0
    assert session["modality"] == "swim"
    assert session["source"] == "manual"
    assert session["title"] == "Natação"
    assert session["dayName"] == "Natação"
    assert session["totalDistanceMeters"] == 1500.0
    assert session["poolLengthMeters"] == 25
    assert session["stroke"] == "crawl"
    assert session["sessionRpe"] == 7.0
    assert session["totalDurationSeconds"] == 2700
    assert session["notes"] == "8 x 100 m"
    assert data["sessionExercises"] == []
    assert data["setLogs"] == []


def test_migracao_aditiva_preserva_dados():
    setup_test_db()
    import sqlite3

    conn = sqlite3.connect(str(db.DB_PATH))
    conn.execute("DROP TABLE IF EXISTS synced_set_logs")
    conn.execute("DROP TABLE IF EXISTS synced_session_exercises")
    conn.execute("DROP TABLE IF EXISTS synced_sessions")
    conn.execute("DROP TABLE IF EXISTS synced_plan_exercises")
    conn.execute("DROP TABLE IF EXISTS synced_plan_days")
    conn.execute("DROP TABLE IF EXISTS synced_plans")
    conn.execute(
        """
        CREATE TABLE synced_plans (
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
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE synced_plan_days (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            plan_id INTEGER NOT NULL,
            local_id INTEGER NOT NULL,
            name TEXT NOT NULL DEFAULT '',
            day_order INTEGER NOT NULL DEFAULT 0,
            notes TEXT NOT NULL DEFAULT '',
            is_rest_day INTEGER NOT NULL DEFAULT 0,
            UNIQUE(plan_id, local_id)
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE synced_plan_exercises (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            day_id INTEGER NOT NULL,
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
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE synced_sessions (
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
        )
        """
    )
    conn.execute(
        "INSERT INTO synced_plans (device_id, local_id, name, synced_at) VALUES ('old',1,'P','2026-01-01T00:00:00Z')"
    )
    conn.commit()
    conn.close()

    db.init_db()
    conn = sqlite3.connect(str(db.DB_PATH))
    row = conn.execute(
        "SELECT name FROM synced_plans WHERE device_id = 'old' AND local_id = 1"
    ).fetchone()
    assert row is not None and row[0] == "P"

    expected_columns = {
        "synced_plan_days": {"modality", "weekday"},
        "synced_plan_exercises": {
            "metric_type",
            "target_duration_seconds",
            "target_distance_meters",
            "target_pace_seconds_per_km",
            "target_hr_zone",
        },
        "synced_sessions": {
            "modality",
            "source",
            "title",
            "total_distance_meters",
            "pool_length_meters",
            "stroke",
            "session_rpe",
        },
    }
    for table, columns in expected_columns.items():
        names = {r[1] for r in conn.execute(f"PRAGMA table_info({table})")}
        assert columns <= names, f"{table} sem {columns - names}"

    conn.execute(
        "INSERT INTO synced_plan_days (plan_id, local_id, name) VALUES (1, 7, 'A')"
    )
    conn.execute(
        "INSERT INTO synced_sessions (device_id, local_id, synced_at) "
        "VALUES ('old', 50, '2026-01-01T00:00:00Z')"
    )
    day = conn.execute(
        "SELECT modality FROM synced_plan_days WHERE local_id = 7"
    ).fetchone()
    session = conn.execute(
        "SELECT modality, source, title FROM synced_sessions WHERE local_id = 50"
    ).fetchone()
    assert day == ("strength",)
    assert session == ("strength", "plan", "")
    conn.close()
