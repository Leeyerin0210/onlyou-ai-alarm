"""공용 기본 페르소나 시드. 실행: DATABASE_URL 설정 후 python seed_personas.py"""
from contextlib import closing

from core.rdb import get_conn, init_schema

SYSTEM_CREATOR = "QK876dED1mZPwXqApiePEchoObv2"

PERSONAS = [
    {
        "id": "miya_default", "name": "미야",
        "prompt": "너는 친절하고 다정한 개인 비서 '미야'야. 주인의 일정을 관리하고 항상 밝은 모습으로 응원해줘.",
        "description": "코네(Conne)의 기본 비서입니다. 다정한 성격으로 당신의 하루를 챙겨줍니다.",
        "voice_prompt": "다정하고 친절한 어조로", "user_call_sign": "주인님",
        "image_url": "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&q=80&w=200",
        "primary_hex": "#FFB7C5", "secondary_hex": "#FFF0F5",
    },
    {
        "id": "luna_cool", "name": "루나",
        "prompt": "너는 차분하고 지적인 비서 '루나'야. 효율적인 일정 관리와 논리적인 대화를 선호해.",
        "description": "차분하고 논리적인 루나와 함께 효율적으로 하루를 관리해 보세요.",
        "voice_prompt": "차분하고 지적한 어조로", "user_call_sign": "사용자님",
        "image_url": "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?auto=format&fit=crop&q=80&w=200",
        "primary_hex": "#6495ED", "secondary_hex": "#F0F8FF",
    },
]


def seed():
    init_schema()
    with closing(get_conn()) as conn, conn.cursor() as cur:
        for p in PERSONAS:
            cur.execute(
                "INSERT INTO personas (id, name, prompt, description, voice_prompt, "
                "user_call_sign, image_url, primary_hex, secondary_hex, creator_id, is_private) "
                "VALUES (%(id)s,%(name)s,%(prompt)s,%(description)s,%(voice_prompt)s,"
                "%(user_call_sign)s,%(image_url)s,%(primary_hex)s,%(secondary_hex)s,"
                f"'{SYSTEM_CREATOR}', FALSE) "
                "ON CONFLICT (id) DO NOTHING",
                p,
            )
    print(f"seeded {len(PERSONAS)} personas")


if __name__ == "__main__":
    seed()
