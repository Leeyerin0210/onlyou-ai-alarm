package com.nemuria.miya.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GradientDivider(
    gradientColors: List<Color>,
    thickness: Dp = 2.dp,
    isVertical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val brush =
        if (isVertical) {
            Brush.verticalGradient(colors = gradientColors)
        } else {
            Brush.horizontalGradient(
                colors = gradientColors,
            )
        }

    if (isVertical) {
        Spacer(
            modifier =
                modifier
                    .fillMaxHeight()
                    .width(thickness)
                    .background(
                        brush = brush,
                    ),
        )
        return
    }
    Spacer(
        modifier =
            modifier
                .fillMaxWidth()
                .height(thickness)
                .background(
                    brush = brush,
                ),
    )
}