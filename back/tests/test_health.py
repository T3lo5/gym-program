from fastapi.testclient import TestClient

from app.main import app


def test_health_responde_e_carrega_catalogo():
    with TestClient(app) as client:
        response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "healthy"
    assert body["catalogSize"] > 0


def test_catalogo_tem_exercicios_com_id():
    with TestClient(app) as client:
        response = client.get("/v1/catalog")

    assert response.status_code == 200
    exercises = response.json()["exercises"]
    assert exercises
    assert all(exercise.get("id") for exercise in exercises)
