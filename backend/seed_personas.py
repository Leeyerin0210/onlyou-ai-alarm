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
        "name": "루나", "user_call_sign": "사용자님",
        "primary_hex": "#6495ED", "secondary_hex": "#F0F8FF",
    },
    {
        "id": "official_casual_warm", "preset_key": "casual_warm",
        "name": "하루", "user_call_sign": "너",
        "primary_hex": "#FFB7C5", "secondary_hex": "#FFF0F5",
    },
    {
        "id": "official_casual_blunt", "preset_key": "casual_blunt",
        "name": "도윤", "user_call_sign": "야",
        "primary_hex": "#5C6B73", "secondary_hex": "#E8EDF0",
    },
]


def seed():
    init_schema()
    cleanup_removed_personas()
    with closing(get_conn()) as conn, conn.cursor() as cur:
        for p in PERSONAS:
            assert p["preset_key"] in PRESETS, f"미지의 preset_key: {p['preset_key']}"
            row = {**p, "creator_id": SYSTEM_CREATOR,
                   "description": PRESETS[p["preset_key"]].description}
            cur.execute(
                "INSERT INTO personas (id, name, description, user_call_sign, "
                "primary_hex, secondary_hex, creator_id, preset_key, is_private) "
                "VALUES (%(id)s,%(name)s,%(description)s,%(user_call_sign)s,"
                "%(primary_hex)s,%(secondary_hex)s,%(creator_id)s,%(preset_key)s, FALSE) "
                "ON CONFLICT (id) DO UPDATE SET "
                "name=EXCLUDED.name, description=EXCLUDED.description, "
                "user_call_sign=EXCLUDED.user_call_sign, "
                "primary_hex=EXCLUDED.primary_hex, secondary_hex=EXCLUDED.secondary_hex, "
                "preset_key=EXCLUDED.preset_key",
                row,
            )
    print(f"seeded {len(PERSONAS)} personas")


if __name__ == "__main__":
    seed()
