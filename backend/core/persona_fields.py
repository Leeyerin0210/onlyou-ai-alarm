"""비서 성격 세 칸(말투·성격·역할).

프리셋을 폐지하고 유저가 직접 쓰는 세 칸으로 성격을 정한다
(스펙: docs/superpowers/specs/2026-10-13-presets-removal-design.md).
자유 칸 하나가 아니라 세 칸 × 300자로 묶은 이유는 탈옥·성인 콘텐츠 통로를
작게 유지하고 공개 심사 비용을 줄이기 위해서다.
"""
from dataclasses import dataclass

MAX_FIELD_LEN = 300
DEFAULT_ROLE = "유저의 하루를 챙기는 비서"

# 공식 비서의 creator_id = 운영자 계정. 공식 비서는 코드(seed_personas.py)로 관리되고
# 신고·기동 정리 대상이 아니다. 운영자가 앱에서 직접 고칠 수 있다(저장할 때 심사는 받는다).
SYSTEM_CREATOR = "D5Dno2ABGxUNyKqdZO7jHydkLxy2"
# 예전에 공식 비서 작성자로 쓰던 uid. 서버 기동 때 이 uid 소유 비서를 SYSTEM_CREATOR로 옮긴다.
LEGACY_SYSTEM_CREATORS = ("QK876dED1mZPwXqApiePEchoObv2",)


@dataclass(frozen=True)
class PersonaFields:
    speech_style: str
    personality: str
    role: str


def compose_legacy_prompt(fields: PersonaFields) -> str:
    """구버전 앱이 GET /personas의 prompt로 읽던 자리를 채운다. 다음 릴리스에서 제거."""
    role = fields.role.strip() or DEFAULT_ROLE
    return f"말투: {fields.speech_style.strip()}\n성격: {fields.personality.strip()}\n역할: {role}"
