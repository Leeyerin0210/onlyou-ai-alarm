"""/memory/clear가 벡터 컬렉션만 삭제하는지 확인 (그래프 삭제 제거 이후)."""


def test_clear_memory_deletes_vector_collection_only(client, monkeypatch):
    import routers.memory as memory_router

    calls = []

    class FakeCollection:
        def delete_by_uid(self, uid):
            calls.append(uid)

    monkeypatch.setattr(memory_router, "collection", FakeCollection())

    res = client.delete("/memory/clear")

    assert res.status_code == 200
    assert calls == ["test-uid"]
