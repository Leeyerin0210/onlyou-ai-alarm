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


def test_official_seed_has_voice_texts(client):
    import seed_personas
    from gen_reference_voices import OFFICIAL_VOICES
    seed_personas.seed()
    with closing(get_conn()) as conn, conn.cursor() as cur:
        cur.execute("SELECT id, voice_description, voice_sample_text FROM personas WHERE creator_id = %s",
                    (SYSTEM_CREATOR,))
        rows = {r[0]: (r[1], r[2]) for r in cur.fetchall()}
    assert len(OFFICIAL_VOICES) == 3
    for pid, instruct, ref_text in OFFICIAL_VOICES:
        assert rows[pid] == (instruct, ref_text)
