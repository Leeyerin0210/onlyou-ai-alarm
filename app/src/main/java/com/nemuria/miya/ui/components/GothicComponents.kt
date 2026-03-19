package com.nemuria.miya.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.nemuria.miya.ui.theme.MiyaTheme

/**
 * 테마의 글꼴 설정을 따르는 텍스트 컴포넌트입니다.
 * [GhanaText]는 강조 제목 등에 사용되며, 테마가 GOTHIC일 때 Ghana Chocolate 글꼴을 사용합니다.
 */
@Composable
fun GhanaText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge, // 기본 테마 스타일 참조
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style, // MiyaTheme에서 결정된 typography를 그대로 따름
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
    )
}

/**
 * 테마의 글꼴 설정을 따르는 텍스트 컴포넌트입니다.
 * [HeirText]는 일반 본문이나 소제목 등에 사용되며, 테마가 GOTHIC일 때 Heir of Light 글꼴을 사용합니다.
 */
@Composable
fun HeirText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge, // 기본 테마 스타일 참조
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style, // MiyaTheme에서 결정된 typography를 그대로 따름
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
    )
}

@Composable
fun GothicCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MiyaTheme.colors
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceA,
        ),
        border = BorderStroke(1.dp, colors.primary),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            content()
        }
    }
}
