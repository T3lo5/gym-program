from app.auth import bearer_token, is_authorized, validate_public_deploy
from fastapi.testclient import TestClient

from app import main


def test_public_url_requires_token():
    try:
        validate_public_deploy("https://treinos.example.com", "")
        assert False, "deveria rejeitar deploy público sem token"
    except RuntimeError as exc:
        assert "API_TOKEN" in str(exc)


def test_local_url_allows_empty_token():
    validate_public_deploy("http://192.168.0.10:3000", "")
    validate_public_deploy("", "")


def test_bearer_token_and_constant_compare():
    assert bearer_token("Bearer abc") == "abc"
    assert bearer_token("Basic abc") == ""
    assert is_authorized("Bearer abc", "abc")
    assert not is_authorized("Bearer errado", "abc")
    assert is_authorized(None, "")


def test_v1_exige_bearer_e_health_fica_publico(monkeypatch):
    monkeypatch.setattr(main, "API_TOKEN", "abc")
    main.db.init_db()
    client = TestClient(main.app)

    assert client.get("/health").status_code == 200
    assert client.get("/v1/sync/status").status_code == 401
    response = client.get(
        "/v1/sync/status",
        headers={"Authorization": "Bearer abc"},
    )
    assert response.status_code == 200
