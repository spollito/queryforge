from fastapi.testclient import TestClient

from queryforge_orchestrator.app import app

client = TestClient(app)


def test_health_reports_ok_and_mock_provider(monkeypatch):
    monkeypatch.delenv("QUERYFORGE_LLM_PROVIDER", raising=False)
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["llm_provider"] == "mock"
