from core.persona_fields import (
    DEFAULT_ROLE,
    MAX_FIELD_LEN,
    PersonaFields,
    compose_legacy_prompt,
)


def test_constants():
    assert MAX_FIELD_LEN == 300
    assert DEFAULT_ROLE == "유저의 하루를 챙기는 비서"


def test_compose_legacy_prompt_joins_three_fields():
    out = compose_legacy_prompt(PersonaFields("반말", "다정함", "룸메이트"))
    assert out == "말투: 반말\n성격: 다정함\n역할: 룸메이트"


def test_compose_legacy_prompt_uses_default_role_when_blank():
    out = compose_legacy_prompt(PersonaFields("반말", "다정함", "  "))
    assert out.endswith(f"역할: {DEFAULT_ROLE}")
