"""라우터 테스트 공통 픽스처.

전제: 로컬 Postgres (네이티브 설치, 5432, onlyou/onlyou) + 테스트 전용 DB `onlyou_test`.
개발 DB(onlyou)를 쓰면 TRUNCATE가 개발 데이터를 지우고 테스트 잔여물이 앱에 노출되므로
반드시 분리된 DB를 쓴다. (없으면: CREATE DATABASE onlyou_test OWNER onlyou)
docker compose의 db(호스트 5433)를 쓸 때는 TEST_DATABASE_URL로 오버라이드.
DATABASE_URL을 테스트용으로 강제한 뒤 앱을 import한다.
"""
import os

os.environ["DATABASE_URL"] = os.environ.get(
    "TEST_DATABASE_URL", "postgresql://onlyou:onlyou@localhost:5432/onlyou_test"
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
