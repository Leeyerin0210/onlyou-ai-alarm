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


from core.prompt_builder import (
    MAX_USER_NOTES_CHARS,
    build_alarm_persona_block,
    build_chat_system_prompt,
)


def test_chat_prompt_embeds_preset_name_and_call_sign():
    out = build_chat_system_prompt("casual_warm", "미야", "주인님", [])
    assert PRESETS["casual_warm"].prompt in out
    assert "미야" in out
    assert "주인님" in out


def test_chat_prompt_marks_absent_user_notes():
    out = build_chat_system_prompt("polite_brief", "루나", "사용자님", [])
    assert "관찰된 유저 특징: 아직 없음" in out


def test_chat_prompt_lists_user_notes():
    out = build_chat_system_prompt("polite_brief", "루나", "사용자님", ["커피를 좋아함", "야근이 잦음"])
    assert "- 커피를 좋아함" in out
    assert "- 야근이 잦음" in out


def test_chat_prompt_truncates_runaway_user_notes():
    """유저 노트는 클라이언트가 보내는 값이다 — 프롬프트 폭식을 여기서 막는다."""
    out = build_chat_system_prompt("polite_brief", "루나", "사용자님", ["가" * 10_000])
    assert len(out) < MAX_USER_NOTES_CHARS + 5_000


def test_chat_prompt_forbids_self_gendering():
    """스펙: 이름과 목소리를 유저가 조합하므로 AI가 자기 성별을 말하면 몰입이 깨진다."""
    out = build_chat_system_prompt("casual_warm", "미야", "주인님", [])
    assert "성별" in out


def test_chat_prompt_keeps_jailbreak_guard():
    out = build_chat_system_prompt("casual_warm", "미야", "주인님", [])
    assert "탈옥" in out


def test_unknown_preset_falls_back_without_raising():
    out = build_chat_system_prompt("nope", "미야", "주인님", [])
    assert PRESETS[DEFAULT_PRESET_ID].prompt in out


def test_alarm_persona_block_is_preset_body():
    assert build_alarm_persona_block("casual_blunt") == PRESETS["casual_blunt"].prompt
    assert build_alarm_persona_block(None) == PRESETS[DEFAULT_PRESET_ID].prompt
