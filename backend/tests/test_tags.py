from core.tags import normalize_tags


def test_strips_hash_whitespace_and_lowercases():
    assert normalize_tags(["#아침", " 아침 ", "##Morning", "아침 루틴"]) == ["아침", "morning", "아침루틴"]


def test_drops_empty_and_too_long_and_caps_count():
    assert normalize_tags(["#", "", "a" * 21, "1", "2", "3", "4", "5", "6"]) == ["1", "2", "3", "4", "5"]
