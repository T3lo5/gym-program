"""Diagnóstico agregado e redigido do backend."""

import sqlite3
from datetime import datetime, timedelta, timezone
from typing import Any, Dict


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _generation_counts(conn: sqlite3.Connection, cutoff: str) -> Dict[str, int]:
    pending = conn.execute(
        "SELECT COUNT(*) AS n FROM generation_jobs WHERE status = 'pending'"
    ).fetchone()["n"]
    running = conn.execute(
        "SELECT COUNT(*) AS n FROM generation_jobs WHERE status = 'running'"
    ).fetchone()["n"]
    failed = conn.execute(
        "SELECT COUNT(*) AS n FROM generation_jobs "
        "WHERE status = 'failed' AND updated_at >= ?",
        (cutoff,),
    ).fetchone()["n"]
    return {
        "pending": int(pending),
        "running": int(running),
        "failed24h": int(failed),
    }


def _sync_counts(conn: sqlite3.Connection, cutoff: str) -> Dict[str, Any]:
    last = conn.execute(
        "SELECT MAX(synced_at) AS last_accepted_at FROM sync_mutations"
    ).fetchone()["last_accepted_at"]
    mutations = conn.execute(
        "SELECT COUNT(*) AS n FROM sync_mutations WHERE synced_at >= ?",
        (cutoff,),
    ).fetchone()["n"]
    return {
        "lastAcceptedAt": last,
        "mutations24h": int(mutations),
    }


def build_diagnostics(conn: sqlite3.Connection, catalog_size: int) -> Dict[str, Any]:
    cutoff = (datetime.now(timezone.utc) - timedelta(hours=24)).isoformat()
    database_writable = True
    try:
        conn.execute("PRAGMA quick_check").fetchone()
        conn.execute(
            "CREATE TABLE IF NOT EXISTS diagnostics_write_probe "
            "(id INTEGER PRIMARY KEY)"
        )
    except sqlite3.DatabaseError:
        database_writable = False

    try:
        generation = _generation_counts(conn, cutoff)
        sync = _sync_counts(conn, cutoff)
    except sqlite3.DatabaseError:
        generation = {"pending": 0, "running": 0, "failed24h": 0}
        sync = {"lastAcceptedAt": None, "mutations24h": 0}
        database_writable = False

    status = "healthy" if database_writable else "degraded"
    return {
        "status": status,
        "timestamp": utc_now(),
        "catalogLoaded": catalog_size > 0,
        "databaseWritable": database_writable,
        "generation": generation,
        "sync": sync,
    }
