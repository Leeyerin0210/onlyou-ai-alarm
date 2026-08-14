def test_list_presets(client):
    res = client.get("/presets")
    assert res.status_code == 200
    items = res.json()
    assert {i["id"] for i in items} == {"polite_brief", "casual_warm", "casual_blunt"}
    first = items[0]
    assert first["label"] and first["description"]
    assert isinstance(first["tags"], list)


def test_preset_body_is_never_exposed(client):
    """프리셋 본문은 우리 자산이자 프롬프트 인젝션 참고자료다 — 내보내지 않는다."""
    for item in client.get("/presets").json():
        assert "prompt" not in item
