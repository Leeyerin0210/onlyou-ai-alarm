"""상점 '이번 주 테마' 결정.

지금은 운영자가 정한 목록을 ISO 주차로 순환한다. 추후 운영자 직접 지정으로
바꿀 때는 current_theme()만 교체한다 — 라우터와 앱은 결과 형태만 안다.
"""
from dataclasses import dataclass
from datetime import date


@dataclass(frozen=True)
class Theme:
    tag: str    # 정규화된 태그 (core/tags.normalize_tags 결과 형태)
    title: str  # 상점 섹션 제목


WEEKLY_THEMES: tuple[Theme, ...] = (
    Theme("아침", "상쾌한 아침을 여는 비서들"),
    Theme("다정", "다정하게 챙겨주는 비서들"),
    Theme("동기부여", "의욕을 깨워주는 비서들"),
    Theme("차분", "차분하게 정리해주는 비서들"),
    Theme("유머", "하루를 웃게 하는 비서들"),
    Theme("직설", "돌려 말하지 않는 비서들"),
)
MIN_THEME_PERSONAS = 3


def current_theme(cur, today: date) -> Theme | None:
    """이번 주 차례인 테마부터 순환하며, 공개 비서가 충분한 첫 테마를 고른다."""
    if not WEEKLY_THEMES:
        return None
    year, week, _ = today.isocalendar()
    start = (year * 53 + week) % len(WEEKLY_THEMES)
    for i in range(len(WEEKLY_THEMES)):
        theme = WEEKLY_THEMES[(start + i) % len(WEEKLY_THEMES)]
        cur.execute(
            "SELECT COUNT(*) FROM personas WHERE is_private = FALSE AND tags @> ARRAY[%s]",
            (theme.tag,),
        )
        if cur.fetchone()[0] >= MIN_THEME_PERSONAS:
            return theme
    return None
