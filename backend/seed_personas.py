"""공식 비서 시드. 실행: DATABASE_URL 설정 후 python seed_personas.py

공식 비서의 성격 세 칸은 여기 코드로 관리한다 — 코드 리뷰를 거치므로 자동 심사·신고
대상이 아니다(스펙: docs/superpowers/specs/2026-10-13-presets-removal-design.md).
preset_key는 구버전 앱 호환용으로 한 릴리스 동안 유지한다.
"""
from contextlib import closing

from core.persona_fields import SYSTEM_CREATOR
from core.presets import PRESETS, legacy_fields
from core.rdb import cleanup_removed_personas, get_conn, init_schema


def _with_fields(p: dict) -> dict:
    """지금은 프리셋 매핑 값을 그대로 쓴다(공식 비서 20명 작업에서 개별 문구로 교체)."""
    f = legacy_fields(p["preset_key"])
    return {**p, "speech_style": f.speech_style, "personality": f.personality, "role": f.role}


# preset_key → (persona_id, 표시 이름, 유저 호칭, 색상)
PERSONAS = [_with_fields(p) for p in [
    {
        "id": "official_polite_brief", "preset_key": "polite_brief",
        "name": "루나", "user_call_sign": "{{user}}님",
        "primary_hex": "#6495ED", "secondary_hex": "#F0F8FF",
        "tags": ["일정", "차분"],
    },
    {
        "id": "official_casual_warm", "preset_key": "casual_warm",
        "name": "하루", "user_call_sign": "{{user}}야",
        "primary_hex": "#FFB7C5", "secondary_hex": "#FFF0F5",
        "tags": ["아침", "다정"],
    },
    {
        "id": "official_casual_blunt", "preset_key": "casual_blunt",
        "name": "도윤", "user_call_sign": "{{user}}",
        "primary_hex": "#5C6B73", "secondary_hex": "#E8EDF0",
        "tags": ["직설", "유머"],
    },
]]


def seed():
    from gen_reference_voices import OFFICIAL_VOICES
    voices = {v[0]: v for v in OFFICIAL_VOICES}
    init_schema()
    cleanup_removed_personas()
    with closing(get_conn()) as conn, conn.cursor() as cur:
        for p in PERSONAS:
            assert p["preset_key"] in PRESETS, f"미지의 preset_key: {p['preset_key']}"
            row = {**p, "creator_id": SYSTEM_CREATOR,
                   "description": PRESETS[p["preset_key"]].description,
                   # 사진 파일은 persona_images/{id}.jpg (main.py가 /persona-images로 서빙)
                   "image_url": f"persona-images/{p['id']}.jpg",
                   "voice_description": voices[p["id"]][1], "voice_sample_text": voices[p["id"]][2]}
            cur.execute(
                "INSERT INTO personas (id, name, description, user_call_sign, "
                "primary_hex, secondary_hex, creator_id, preset_key, is_private, tags, image_url, "
                "speech_style, personality, role, voice_description, voice_sample_text) "
                "VALUES (%(id)s,%(name)s,%(description)s,%(user_call_sign)s,"
                "%(primary_hex)s,%(secondary_hex)s,%(creator_id)s,%(preset_key)s, FALSE, %(tags)s, %(image_url)s, "
                "%(speech_style)s,%(personality)s,%(role)s,%(voice_description)s,%(voice_sample_text)s) "
                "ON CONFLICT (id) DO UPDATE SET "
                "name=EXCLUDED.name, description=EXCLUDED.description, "
                "user_call_sign=EXCLUDED.user_call_sign, "
                "primary_hex=EXCLUDED.primary_hex, secondary_hex=EXCLUDED.secondary_hex, "
                "preset_key=EXCLUDED.preset_key, tags=EXCLUDED.tags, image_url=EXCLUDED.image_url, "
                "speech_style=EXCLUDED.speech_style, personality=EXCLUDED.personality, role=EXCLUDED.role, "
                "voice_description=EXCLUDED.voice_description, voice_sample_text=EXCLUDED.voice_sample_text",
                row,
            )
    print(f"seeded {len(PERSONAS)} personas")


if __name__ == "__main__":
    seed()
