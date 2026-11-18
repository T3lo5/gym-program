import asyncio
import os
import tempfile

from app import database as db
from app import main
from app.job_worker import backoff_seconds, lease_expired


def setup_test_db():
    fd, path = tempfile.mkstemp(suffix=".db")
    os.close(fd)
    db.DB_PATH = path
    db.init_db()


def test_running_com_lease_expirado_vira_pending():
    setup_test_db()
    db.create_generation_job("job-1", "d1", {"goal": "hypertrophy"})
    assert db.mark_generation_job_running("job-1", "worker-a", 30)
    db.requeue_expired_generation_jobs("2099-01-01T00:00:00+00:00")

    row = db.get_generation_job("job-1")
    assert row["status"] == "pending"
    assert row["lease_owner"] is None
    assert row["lease_expires_at"] is None


def test_dois_claims_nao_executam_mesmo_job():
    setup_test_db()
    db.create_generation_job("job-1", "d1", {})

    assert db.mark_generation_job_running("job-1", "worker-a", 30)
    assert not db.mark_generation_job_running("job-1", "worker-b", 30)


def test_cancel_vence_conclusao_da_tentativa():
    setup_test_db()
    db.create_generation_job("job-1", "d1", {})
    assert db.mark_generation_job_running("job-1", "worker-a", 30)
    assert db.finish_generation_job("job-1", "cancelled")

    assert not db.finish_generation_job(
        "job-1",
        "done",
        result={"ok": True},
        worker_id="worker-a",
    )
    assert db.get_generation_job("job-1")["status"] == "cancelled"


def test_retry_limitado_requeue_e_failed():
    setup_test_db()
    db.create_generation_job("job-1", "d1", {})
    assert db.mark_generation_job_running("job-1", "worker-a", 30)
    assert db.requeue_generation_job("job-1", "erro temporario")

    row = db.get_generation_job("job-1")
    assert row["status"] == "pending"
    assert row["attempt"] == 1

    assert db.mark_generation_job_running("job-1", "worker-b", 30)
    assert db.requeue_generation_job("job-1", "erro persistente")
    row = db.get_generation_job("job-1")
    assert row["status"] == "failed"
    assert row["attempt"] == 2


def test_lease_expired_e_backoff_puro():
    assert lease_expired("2026-01-01T00:00:00+00:00", "2026-01-01T00:01:00+00:00")
    assert not lease_expired("2026-01-01T00:02:00+00:00", "2026-01-01T00:01:00+00:00")
    assert backoff_seconds(1) == 5
    assert backoff_seconds(2) == 30


def test_startup_pending_e_reprocessado(spawned_jobs):
    setup_test_db()
    db.create_generation_job("job-pending", "d1", {"goal": "hypertrophy"})

    async def _restart():
        async with main.lifespan(main.app):
            pass

    asyncio.run(_restart())
    assert "job-pending" in spawned_jobs
