package com.onlyou.com.ui.components

/**
 * 가로 리스트 끝에서 남은 스크롤·플링을 여기서 소비한다.
 * 그대로 두면 메인 탭 HorizontalPager로 전달돼 옆 탭으로 넘어간다.
 */
object ConsumeHorizontalOverscroll : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    override fun onPostScroll(
        consumed: androidx.compose.ui.geometry.Offset,
        available: androidx.compose.ui.geometry.Offset,
        source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
    ) = available.copy(y = 0f)

    override suspend fun onPostFling(
        consumed: androidx.compose.ui.unit.Velocity,
        available: androidx.compose.ui.unit.Velocity,
    ) = available.copy(y = 0f)
}
