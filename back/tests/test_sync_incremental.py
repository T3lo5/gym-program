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


def test_mutation_upsert_e_duplicata_sao_idempotentes():
    setup_test_db()
    client = TestClient(main.app)
    mutation = {
        "mutationId": "plan-1-v1",
        "entity": "plan",
        "entityId": "1",
        "operation": "upsert",
        "updatedAt": 20,
        "payload": '{"localId": 1, "name": "P", "days": []}',
    }

    first = client.post(
        "/v1/sync/changes",
        json={"deviceId": "d1", "baseCursor": 0, "mutations": [mutation]},
    )
    second = client.post(
        "/v1/sync/changes",
        json={"deviceId": "d1", "baseCursor": first.json()["cursor"], "mutations": [mutation]},
    )

    assert first.status_code == 200
    assert first.json()["accepted"] == ["plan-1-v1"]
    assert second.status_code == 200
    assert second.json()["ignored"] == ["plan-1-v1"]


def test_revisao_mais_antiga_nao_substitui_revisao_nova():
    setup_test_db()
    client = TestClient(main.app)
    client.post(
        "/v1/sync/changes",
        json={
            "deviceId": "d1",
            "mutations": [{
                "mutationId": "plan-1-v2",
                "entity": "plan",
                "entityId": "1",
                "operation": "upsert",
                "updatedAt": 20,
                "payload": '{"localId": 1, "name": "Novo", "days": []}',
            }],
        },
    )
    response = client.post(
        "/v1/sync/changes",
        json={
            "deviceId": "d1",
            "mutations": [{
                "mutationId": "plan-1-v1",
                "entity": "plan",
                "entityId": "1",
                "operation": "upsert",
                "updatedAt": 10,
                "payload": '{"localId": 1, "name": "Antigo", "days": []}',
            }],
        },
    )

    assert response.json()["ignored"] == ["plan-1-v1"]
    download = client.get("/v1/sync/download?deviceId=d1").json()
    assert download["plans"][0]["name"] == "Novo"


def test_tombstone_delete_remove_plano():
    setup_test_db()
    client = TestClient(main.app)
    client.post(
        "/v1/sync/changes",
        json={
            "deviceId": "d1",
            "mutations": [{
                "mutationId": "plan-1-upsert",
                "entity": "plan",
                "entityId": "1",
                "operation": "upsert",
                "updatedAt": 10,
                "payload": '{"localId": 1, "name": "P", "days": []}',
            }],
        },
    )
    deleted = client.post(
        "/v1/sync/changes",
        json={
            "deviceId": "d1",
            "mutations": [{
                "mutationId": "plan-1-delete",
                "entity": "plan",
                "entityId": "1",
                "operation": "delete",
                "updatedAt": 20,
                "payload": "{}",
            }],
        },
    )

    assert deleted.json()["accepted"] == ["plan-1-delete"]
    download = client.get("/v1/sync/download?deviceId=d1").json()
    assert download["plans"] == []


def test_cursor_avanca_e_retorna_apenas_lote_posterior():
    setup_test_db()
    client = TestClient(main.app)
    first = client.post(
        "/v1/sync/changes",
        json={
            "deviceId": "d1",
            "mutations": [{
                "mutationId": "session-1",
                "entity": "session",
                "entityId": "1",
                "operation": "upsert",
                "updatedAt": 10,
                "payload": '{"localId": 1, "name": "S", "exercises": []}',
            }],
        },
    )
    second = client.post(
        "/v1/sync/changes",
        json={
            "deviceId": "d1",
            "baseCursor": first.json()["cursor"],
            "mutations": [{
                "mutationId": "session-2",
                "entity": "session",
                "entityId": "2",
                "operation": "upsert",
                "updatedAt": 20,
                "payload": '{"localId": 2, "name": "S2", "exercises": []}',
            }],
        },
    )

    assert second.json()["cursor"] > first.json()["cursor"]
