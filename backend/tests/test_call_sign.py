from core.call_sign import render_call_sign


def test_plain_call_sign_unchanged():
    assert render_call_sign("사용자님", "예린", "너") == "사용자님"


def test_user_token_replaced_with_name():
    assert render_call_sign("{{user}} 오빠", "예린", "너") == "예린 오빠"
    assert render_call_sign("{{user}}님", "예린", "너") == "예린님"


def test_vocative_follows_final_consonant():
    assert render_call_sign("{{user}}야", "예린", "너") == "예린아"
    assert render_call_sign("{{user}}아", "지수", "너") == "지수야"
    assert render_call_sign("{{user}}야", "Yerin", "너") == "Yerin야"


def test_missing_name_keeps_title_or_falls_back():
    assert render_call_sign("{{user}} 오빠", "", "너") == "오빠"
    assert render_call_sign("{{user}}아", None, "너") == "너"
    assert render_call_sign("{{user}}님", "  ", "사용자님") == "사용자님"
