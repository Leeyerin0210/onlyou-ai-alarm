from contextlib import closing

from core.persona_fields import MAX_FIELD_LEN, SYSTEM_CREATOR
from core.rdb import get_conn


def test_official_personas_have_fields_within_limit():
    import seed_personas
    for p in seed_personas.PERSONAS:
        for key in ("speech_style", "personality"):
            assert p[key].strip(), f"{p['id']} {key} 비어 있음"
        for key in ("speech_style", "personality", "role"):
            assert len(p[key]) <= MAX_FIELD_LEN, p["id"]


def test_seed_writes_fields_and_system_creator(client):
    import seed_personas
    seed_personas.seed()
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT creator_id, speech_style, personality FROM personas WHERE id = 'official_casual_blunt'"
        )
        creator, speech, personality = cur.fetchone()
    assert creator == SYSTEM_CREATOR
    assert speech.strip() and personality.strip()


def test_seed_avoids_banned_vocabulary():
    """스펙: 연령 특정 금지, 서브컬쳐 어휘 대신 대중 어휘."""
    import seed_personas
    banned = ["여고생", "남고생", "고등학생", "중학생", "츤데레", "얀데레", "미성년"]
    for p in seed_personas.PERSONAS:
        blob = " ".join(p[k] for k in ("name", "speech_style", "personality", "role"))
        for word in banned:
            assert word not in blob, f"{p['id']}에 금지 어휘 '{word}'"
