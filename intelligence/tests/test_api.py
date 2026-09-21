import pytest
from fastapi.testclient import TestClient

from app.main import create_app


@pytest.fixture(scope="module")
def client(trained):
    model_path, _ = trained
    # "with" dispara o lifespan (carregamento do modelo), como numa subida real.
    with TestClient(create_app(model_path)) as test_client:
        yield test_client


def test_health_reports_up_and_model_version(client):
    response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["modelVersion"].startswith("v1-")   # camelCase, como a API Java


def test_suggestion_returns_category_and_priority_with_confidence(client):
    response = client.post("/v1/suggestions", json={
        "title": "Impressora fiscal parada",
        "description": "A impressora do caixa parou e a loja não consegue emitir cupons",
    })

    assert response.status_code == 200
    body = response.json()
    assert body["category"]["label"] == "Impressora"
    assert 0 < body["category"]["confidence"] <= 1
    assert len(body["category"]["alternatives"]) == 2
    assert body["priority"]["label"] in {"P1", "P2", "P3", "P4"}
    assert "modelVersion" in body


@pytest.mark.parametrize("payload", [
    {"title": "", "description": "texto"},              # título vazio
    {"title": "   ", "description": "texto"},           # só espaços
    {"title": "ok", "description": ""},                 # descrição vazia
    {"title": "ok"},                                    # campo ausente
    {"title": "x" * 151, "description": "texto"},       # acima do limite da API Java (150)
    {"title": "ok", "description": "x" * 4001},         # acima do limite (4000)
])
def test_invalid_requests_are_rejected_with_422(client, payload):
    assert client.post("/v1/suggestions", json=payload).status_code == 422


def test_app_refuses_to_start_without_a_model(tmp_path):
    with pytest.raises(FileNotFoundError):
        with TestClient(create_app(tmp_path / "ausente.joblib")):
            pass
