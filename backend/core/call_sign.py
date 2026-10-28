"""비서 호칭 템플릿. 앱(util/CallSign.kt)과 규칙을 맞춘다.

비서마다 user_call_sign에 '{{user}} 오빠'처럼 적어 두면 유저 프로필 이름으로 치환한다.
'{{user}}아'·'{{user}}야'는 이름 끝 받침에 맞춰 고친다 (예린아 / 지수야).
"""

USER_TOKEN = "{{user}}"
# 이름이 없을 때 이것만 남으면 호칭이 성립하지 않는다
_BARE_SUFFIXES = {"", "아", "야", "님", "씨"}


def _has_final_consonant(ch: str) -> bool:
    code = ord(ch) - 0xAC00
    return 0 <= code < 11172 and code % 28 != 0


def render_call_sign(template: str, user_name: str | None, fallback: str) -> str:
    if USER_TOKEN not in template:
        return template
    name = (user_name or "").strip()
    if not name:
        rest = template.replace(USER_TOKEN, "").strip()
        return fallback if rest in _BARE_SUFFIXES else rest
    vocative = "아" if _has_final_consonant(name[-1]) else "야"
    return (template
            .replace(USER_TOKEN + "아", USER_TOKEN + vocative)
            .replace(USER_TOKEN + "야", USER_TOKEN + vocative)
            .replace(USER_TOKEN, name))
