"""Helpers dos testes de jobs de geração (H3-40).

O TestClient não mantém o event loop entre requests: o conftest desliga o
spawn real (asyncio.create_task) e o teste dirige `run_generation_job` com
asyncio.run — cliente/clock falsos quando necessário, sem timing de thread.
"""

import asyncio

from app import main


def submit(client, payload):
    """POST → 202; devolve o jobId."""
    response = client.post("/v1/generate-workout", json=payload)
    assert response.status_code == 202, response.text
    return response.json()["jobId"]


def drive(client, payload, job_id):
    """Roda o runner do job até o estado terminal; devolve o body do GET status."""
    asyncio.run(main.run_generation_job(job_id, main.GenerateRequest(**payload)))
    response = client.get(f"/v1/generate-workout/{job_id}")
    assert response.status_code == 200, response.text
    return response.json()


def run_job(client, payload):
    """Submit + runner + GET done; devolve o `result` (dict do GenerateResponse)
    para as asserções de plano/source/fallbackReason."""
    job_id = submit(client, payload)
    body = drive(client, payload, job_id)
    assert body["status"] == "done", body
    return body["result"]