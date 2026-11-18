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


def test_optimize_endpoint_runs_offline_with_defaults(monkeypatch):
    # No LLM provider and no Java API URL -> mock LLM + in-memory fake Java client.
    monkeypatch.delenv("QUERYFORGE_LLM_PROVIDER", raising=False)
    monkeypatch.delenv("QUERYFORGE_JAVA_API_URL", raising=False)
    sql = "SELECT id FROM orders WHERE customer_id = 42"
    response = client.post("/optimize", json={"sql": sql, "mode": "estimate"})
    assert response.status_code == 200
    body = response.json()
    assert body["result_sql"] is not None
    assert body["trace_id"]
    assert "candidates" in body and "verification_results" in body
