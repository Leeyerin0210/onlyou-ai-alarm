"""비서 해시태그 정규화 — 앱(TagNormalizer.kt)과 규칙을 맞춘다. 최종 판정은 서버."""
import re

MAX_TAGS = 5
MAX_TAG_LEN = 20
_WS = re.compile(r"\s+")


def normalize_tags(raw: list[str]) -> list[str]:
    out: list[str] = []
    for t in raw:
        t = _WS.sub("", t.lstrip().lstrip("#")).lower()
        if not t or len(t) > MAX_TAG_LEN or t in out:
            continue
        out.append(t)
        if len(out) == MAX_TAGS:
            break
    return out
