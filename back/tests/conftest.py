"""Configuração dos testes do backend.

Define variáveis de ambiente antes de importar `app.main`, porque o módulo
resolve caminhos e configuração no momento do import.
"""

import os

# Atribuição (não setdefault): `back/.env` traz uma chave real e `load_dotenv`
# roda no import de `app.main`. Sem zerar a chave aqui, os testes que exigem o
# plano local determinístico podem chamar a IA e mudar de resultado a cada run.
os.environ["SENSENOVA_API_KEY"] = ""

os.environ.setdefault("DATABASE_PATH", "/tmp/treinos-test.db")
os.environ.setdefault("MDNS_ENABLED", "false")
os.environ.setdefault("PYTHONDONTWRITEBYTECODE", "1")

import pytest

from app import main


@pytest.fixture(autouse=True)
def spawned_jobs(monkeypatch):
    """H3-40: o TestClient não mantém o event loop entre requests — o spawn
    real (asyncio.create_task) é desligado e os testes dirigem
    `run_generation_job` explicitamente (job_helpers). As chamadas de spawn
    ficam registradas na lista retornada, para inspeção nos testes de submit.
    """
    calls = []
    monkeypatch.setattr(main, "_spawn_generation_job", lambda job_id, request: calls.append(job_id))
    return calls
