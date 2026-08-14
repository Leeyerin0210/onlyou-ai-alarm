from core.presets import (
    DEFAULT_PRESET_ID,
    PRESETS,
    get_preset,
    is_valid_preset_id,
)


def test_presets_cover_the_axes():
    """축 없이 만들면 유저는 3개를 1개로 느낀다 — 존댓말/반말이 갈리는지 확인."""
    assert set(PRESETS) == {"polite_brief", "casual_warm", "casual_blunt"}


def test_every_preset_has_content():
    for pid, p in PRESETS.items():
        assert p.id == pid
        assert p.label.strip()
        assert p.description.strip()
        assert len(p.prompt) > 50, f"{pid} 프롬프트가 너무 짧다"
        assert p.tags, f"{pid}에 성별 태그가 없다"


def test_presets_avoid_banned_vocabulary():
    """스펙: 연령 특정 금지, 서브컬쳐 어휘 대신 대중 어휘."""
    banned = ["여고생", "남고생", "고등학생", "중학생", "츤데레", "얀데레", "미성년"]
    for pid, p in PRESETS.items():
        blob = f"{p.label} {p.description} {p.prompt}"
        for word in banned:
            assert word not in blob, f"{pid}에 금지 어휘 '{word}'"


def test_get_preset_falls_back_to_default():
    assert get_preset(None).id == DEFAULT_PRESET_ID
    assert get_preset("nope").id == DEFAULT_PRESET_ID
    assert get_preset("casual_warm").id == "casual_warm"


def test_is_valid_preset_id():
    assert is_valid_preset_id("casual_blunt")
    assert not is_valid_preset_id("nope")
    assert not is_valid_preset_id("")
