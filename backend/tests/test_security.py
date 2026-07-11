from fastapi.testclient import TestClient
from main import app


def test_missing_token_returns_401():
    client = TestClient(app)  # 오버라이드 없음 → 실제 get_uid 사용
    res = client.get("/personas")
    assert res.status_code == 401
