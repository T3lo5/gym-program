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


def test_space_isolation_e_join():
    setup_test_db()
    client = TestClient(main.app)
    secret = "segredo-seguro-123456"
    space = "11111111-1111-1111-1111-111111111111"

    created = client.post(
        "/v1/sync/spaces/create",
        json={"syncSpaceId": space, "syncSecret": secret},
    )
    assert created.status_code == 200
    joined = client.post(
        "/v1/sync/spaces/join",
        json={"syncSpaceId": space, "syncSecret": secret, "deviceId": "d1"},
    )
    assert joined.status_code == 200

    first = client.post(
        "/v1/sync/changes",
        headers={"X-Sync-Space": space, "X-Sync-Secret": secret},
        json={
            "deviceId": "d1",
            "baseCursor": 0,
            "mutations": [{
                "mutationId": "m1",
                "entity": "session",
                "entityId": "1",
                "operation": "upsert",
                "updatedAt": 10,
                "payload": '{"localId": 1, "exercises": []}',
            }],
        },
    )
    assert first.status_code == 200

    other = client.post(
        "/v1/sync/changes",
        headers={
            "X-Sync-Space": "22222222-2222-2222-2222-222222222222",
            "X-Sync-Secret": "outro-segredo-123",
        },
        json={"deviceId": "d2", "baseCursor": 0, "mutations": []},
    )
    assert other.status_code == 401


def test_join_secret_invalido_e_revocacao():
    setup_test_db()
    client = TestClient(main.app)
    secret = "segredo-seguro-123456"
    space = "11111111-1111-1111-1111-111111111111"
    assert client.post(
        "/v1/sync/spaces/create",
        json={"syncSpaceId": space, "syncSecret": secret},
    ).status_code == 200
    assert client.post(
        "/v1/sync/spaces/join",
        json={"syncSpaceId": space, "syncSecret": "errado", "deviceId": "d1"},
    ).status_code == 401

    revoked = client.post(
        "/v1/sync/spaces/revoke",
        json={"syncSpaceId": space, "syncSecret": secret},
    )
    assert revoked.status_code == 200
    assert client.post(
        "/v1/sync/changes",
        headers={"X-Sync-Space": space, "X-Sync-Secret": secret},
        json={"deviceId": "d1", "baseCursor": 0, "mutations": []},
    ).status_code == 401
