"""공용 기본 페르소나 시드. 실행: DATABASE_URL 설정 후 python seed_personas.py

프리셋 하나당 공식 페르소나 하나를 만든다. 프롬프트 본문은 core/presets.py에 있고
여기서는 preset_key로 참조만 한다 — 시드가 프롬프트를 복사해 들고 있으면
프리셋을 고칠 때마다 시드도 같이 고쳐야 하는 이중 관리가 된다.
"""
from contextlib import closing

from core.presets import PRESETS
from core.rdb import cleanup_removed_personas, get_conn, init_schema

SYSTEM_CREATOR = "QK876dED1mZPwXqApiePEchoObv2"

# preset_key → (persona_id, 표시 이름, 유저 호칭, 색상)
PERSONAS = [
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
]


def seed():
    init_schema()
    cleanup_removed_personas()
    with closing(get_conn()) as conn, conn.cursor() as cur:
        for p in PERSONAS:
            assert p["preset_key"] in PRESETS, f"미지의 preset_key: {p['preset_key']}"
            row = {**p, "creator_id": SYSTEM_CREATOR,
                   "description": PRESETS[p["preset_key"]].description,
                   # 사진 파일은 persona_images/{id}.jpg (main.py가 /persona-images로 서빙)
                   "image_url": f"persona-images/{p['id']}.jpg"}
            cur.execute(
                "INSERT INTO personas (id, name, description, user_call_sign, "
                "primary_hex, secondary_hex, creator_id, preset_key, is_private, tags, image_url) "
                "VALUES (%(id)s,%(name)s,%(description)s,%(user_call_sign)s,"
                "%(primary_hex)s,%(secondary_hex)s,%(creator_id)s,%(preset_key)s, FALSE, %(tags)s, %(image_url)s) "
                "ON CONFLICT (id) DO UPDATE SET "
                "name=EXCLUDED.name, description=EXCLUDED.description, "
                "user_call_sign=EXCLUDED.user_call_sign, "
                "primary_hex=EXCLUDED.primary_hex, secondary_hex=EXCLUDED.secondary_hex, "
                "preset_key=EXCLUDED.preset_key, tags=EXCLUDED.tags, image_url=EXCLUDED.image_url",
                row,
            )
    print(f"seeded {len(PERSONAS)} personas")


if __name__ == "__main__":
    seed()
