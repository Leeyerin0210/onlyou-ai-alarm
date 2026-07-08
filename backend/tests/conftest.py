"""라우터 테스트 공통 픽스처.

전제: docker compose up -d db (로컬 Postgres, 호스트 5433).
DATABASE_URL을 테스트용으로 강제한 뒤 앱을 import한다.
"""
import os

os.environ["DATABASE_URL"] = os.environ.get(
    "TEST_DATABASE_URL", "postgresql://conne:conne@localhost:5433/conne"
)

import pytest
from fastapi.testclient import TestClient

from main import app
from core.rdb import init_schema, get_conn
from core.security import get_uid

TEST_UID = "test-uid"


@pytest.fixture()
def client():
    init_schema()
    # 각 테스트 전 관련 테이블 초기화
    with get_conn() as conn, conn.cursor() as cur:
        cur.execute("TRUNCATE personas, users, schedules, backups")
    app.dependency_overrides[get_uid] = lambda: TEST_UID
    yield TestClient(app)
    app.dependency_overrides.clear()
