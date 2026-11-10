"""Jobs assíncronos de geração (H3-40): submit/status/cancel/sweep/orçamento.

O TestClient não mantém o event loop entre requests: o conftest desliga o
spawn real e os testes dirigem `run_generation_job` com asyncio.run (cliente
e clock falsos quando preciso), sem depender de timing de thread.
"""

import asyncio
import json
import os
import tempfile
import uuid

import httpx
from fastapi.testclient import TestClient

from app import database as db
from app import main
from job_helpers import drive, run_job, submit


def setup_test_db():
    fd, path = tempfile.mkstemp(suffix=".db")
    os.close(fd)
    db.DB_PATH = path
    db.init_db()
    return path


def _client():
    setup_test_db()
    return TestClient(main.app)


def _payload(**overrides):
    payload = {
        "deviceId": "d1",
        "goal": "hypertrophy",
        "daysPerWeek": 2,
        "availableEquipment": ["barbell", "dumbbell", "body only"],
        "targetMuscles": ["chest", "back"],
        "sessionDurationMinutes": 60,
        "currentLevel": "intermediate",
    }
    payload.update(overrides)
    return payload


class _FakeResponse:
    def __init__(self, content):
        self.status_code = 200
        self.text = content
        self._content = content

    def json(self):
        return {"choices": [{"message": {"content": self._content}}]}


class _AIClient:
    """httpx.AsyncClient falso que devolve o `content` da classe."""

    content = ""

    def __init__(self, *args, **kwargs):
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    async def post(self, *args, **kwargs):
        return _FakeResponse(type(self).content)


class _FakeClock:
    def __init__(self):
        self.now = 0.0

    def __call__(self):
        return self.now


class _HangingClient:
    """IA que nunca responde: cada post queima todo o read timeout no relógio."""

    clock = None

    def __init__(self, *args, **kwargs):
        self._read = kwargs["timeout"].read

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    async def post(self, *args, **kwargs):
        type(self).clock.now += self._read
        raise httpx.ReadTimeout("IA não respondeu dentro do read timeout")


def _plano_ia_valido():
    return {
        "name": "Plano da IA",
        "description": "",
        "goal": "hypertrophy",
        "daysPerWeek": 1,
        "days": [
            {
                "name": "Dia 1",
                "dayOrder": 0,
                "exercises": [
                    {
                        "exerciseId": main.EXERCISE_CATALOG[0]["id"],
                        "exerciseOrder": 0,
                        "sets": 3,
                        "targetReps": "8-12",
                    }
                ],
            }
        ],
    }


def test_submit_responde_202_imediato_com_job_id(spawned_jobs):
    client = _client()
    response = client.post("/v1/generate-workout", json=_payload())
    assert response.status_code == 202, response.text
    body = response.json()
    assert body["status"] == "pending"
    uuid.UUID(body["jobId"])
    assert spawned_jobs == [body["jobId"]]
    # A IA não rodou no caminho da resposta: o job segue pending no SQLite.
    status = client.get(f"/v1/generate-workout/{body['jobId']}").json()
    assert status["status"] == "pending"
    assert status["result"] is None
    assert status["error"] is None
    assert status["createdAt"]


def test_job_conclui_com_ia_e_persiste_historico(monkeypatch):
    monkeypatch.setattr(main, "SENSENOVA_API_KEY", "chave-de-teste")
    monkeypatch.setattr(_AIClient, "content", json.dumps(_plano_ia_valido()))
    monkeypatch.setattr(main.httpx, "AsyncClient", _AIClient)

    result = run_job(_client(), _payload())
    assert result["source"] == "ai"
    assert result["fallbackReason"] == ""
    assert result["planId"] > 0
    with db.get_connection() as conn:
        row = conn.execute(
            "SELECT source, model_used FROM plan_generations WHERE id = ?",
            (result["planId"],),
        ).fetchone()
    assert row is not None
    assert row["source"] == "ai"
    assert row["model_used"] == main.MODEL_ID


def test_timeout_da_ia_vira_fallback_local_dentro_do_job(monkeypatch):
    """IA pendurada esgota o orçamento do job (600s) e cai no fallback local:
    o job termina done com fallbackReason=timeout, sem erro para o usuário."""
    monkeypatch.setattr(main, "SENSENOVA_API_KEY", "chave-de-teste")
    monkeypatch.delenv("AI_TIMEOUT_TOTAL_SECONDS", raising=False)
    clock = _FakeClock()
    monkeypatch.setattr(main, "monotonic", clock)
    _HangingClient.clock = clock
    monkeypatch.setattr(main.httpx, "AsyncClient", _HangingClient)

    result = run_job(_client(), _payload())
    assert result["source"] == "local"
    assert result["fallbackReason"] == "timeout"
    assert result["workout"]["days"]
    # Timeout na 1ª esgota o orçamento: sem retry, e o orçamento é o do job
    # (600s) — a invariante síncrona "< 90s do app" deixou de existir.
    assert clock.now >= 600.0


def test_pool_vazio_marca_job_failed_com_mensagem():
    """Complementar sem candidato suficiente (calves + bodyweight): nem o
    fallback é possível ⇒ job failed com a mensagem do ValueError (H3-16)."""
    client = _client()
    payload = _payload(
        environment="bodyweight",
        focusMuscles=["calves"],
        availableEquipment=["body only"],
        sessionDurationMinutes=30,
        modalities=[
            {
                "type": "strength",
                "sessionsPerWeek": 2,
                "sessionDurationMinutes": 30,
                "availableEquipment": ["body only"],
            }
        ],
    )
    job_id = submit(client, payload)
    body = drive(client, payload, job_id)
    assert body["status"] == "failed", body
    assert body["result"] is None
    assert "foco" in body["error"].lower()


def test_status_de_job_desconhecido_e_404():
    client = _client()
    assert client.get("/v1/generate-workout/nao-existe").status_code == 404
    assert client.post("/v1/generate-workout/nao-existe/cancel").status_code == 404


def test_novo_submit_cancela_job_em_andamento():
    """Last-write-wins: novo POST do mesmo device cancela o job anterior."""
    client = _client()
    first = submit(client, _payload())
    second = submit(client, _payload())
    assert first != second
    assert client.get(f"/v1/generate-workout/{first}").json()["status"] == "cancelled"
    assert client.get(f"/v1/generate-workout/{second}").json()["status"] == "pending"


def test_cancel_idempotente_em_job_done():
    client = _client()
    payload = _payload()
    job_id = submit(client, payload)
    assert drive(client, payload, job_id)["status"] == "done"
    for _ in range(2):
        response = client.post(f"/v1/generate-workout/{job_id}/cancel")
        assert response.status_code == 200
        body = response.json()
        assert body["status"] == "done"
        assert body["result"]["workout"]["days"]


def test_cancel_de_job_running_marca_cancelled():
    client = _client()
    payload = _payload()
    job_id = submit(client, payload)
    assert db.mark_generation_job_running(job_id)
    response = client.post(f"/v1/generate-workout/{job_id}/cancel")
    assert response.status_code == 200
    assert response.json()["status"] == "cancelled"
    # Runner dirigido depois do cancel não gera nada: a transição pending →
    # running é guardada e o job já está terminal.
    body = drive(client, payload, job_id)
    assert body["status"] == "cancelled"
    assert body["result"] is None


def test_startup_sweep_marca_jobs_interrompidos_como_failed():
    setup_test_db()
    db.create_generation_job("job-pending", "d1", {})
    db.create_generation_job("job-running", "d1", {})
    assert db.mark_generation_job_running("job-running")
    db.create_generation_job("job-done", "d1", {})
    assert db.finish_generation_job("job-done", "done", result={"ok": True})

    async def _restart():
        async with main.lifespan(main.app):
            pass

    asyncio.run(_restart())

    for job_id in ("job-pending", "job-running"):
        row = db.get_generation_job(job_id)
        assert row["status"] == "failed"
        assert row["error"] == "interrupted"
    assert db.get_generation_job("job-done")["status"] == "done"


def test_orcamento_default_600s_e_env_override(monkeypatch):
    monkeypatch.delenv("AI_TIMEOUT_TOTAL_SECONDS", raising=False)
    assert main.AI_TIMEOUT_DEFAULT_SECONDS == 600.0
    assert main._ai_timeout("AI_TIMEOUT_TOTAL_SECONDS", main.AI_TIMEOUT_DEFAULT_SECONDS) == 600.0
    monkeypatch.setenv("AI_TIMEOUT_TOTAL_SECONDS", "120")
    assert main._ai_timeout("AI_TIMEOUT_TOTAL_SECONDS", main.AI_TIMEOUT_DEFAULT_SECONDS) == 120.0
    # Mínimo de 3s; valor inválido cai no default.
    monkeypatch.setenv("AI_TIMEOUT_TOTAL_SECONDS", "1")
    assert main._ai_timeout("AI_TIMEOUT_TOTAL_SECONDS", main.AI_TIMEOUT_DEFAULT_SECONDS) == 3.0
    monkeypatch.setenv("AI_TIMEOUT_TOTAL_SECONDS", "abc")
    assert main._ai_timeout("AI_TIMEOUT_TOTAL_SECONDS", main.AI_TIMEOUT_DEFAULT_SECONDS) == 600.0