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


def test_diagnostics_redigido(monkeypatch):
    setup_test_db()
    monkeypatch.setattr(main, "API_TOKEN", "abc")
    client = TestClient(main.app)

    response = client.get(
        "/v1/diagnostics",
        headers={"Authorization": "Bearer abc"},
    )
    assert response.status_code == 200
    body = response.json()
    assert set(body) == {
        "status",
        "timestamp",
        "catalogLoaded",
        "databaseWritable",
        "generation",
        "sync",
    }
    assert "deviceId" not in str(body)
    assert "requestJson" not in str(body)
    assert "apiToken" not in str(body)


def test_diagnostics_requires_token(monkeypatch):
    setup_test_db()
    monkeypatch.setattr(main, "API_TOKEN", "abc")
    client = TestClient(main.app)

    assert client.get("/v1/diagnostics").status_code == 401
    assert client.get(
        "/v1/diagnostics",
        headers={"Authorization": "Bearer errado"},
    ).status_code == 401


def test_health_mantem_payload_minimo(monkeypatch):
    setup_test_db()
    monkeypatch.setattr(main, "API_TOKEN", "abc")
    client = TestClient(main.app)
    body = client.get("/health").json()

    assert set(body).issubset({"status", "timestamp", "model", "catalogSize"})
    assert "database" not in body
