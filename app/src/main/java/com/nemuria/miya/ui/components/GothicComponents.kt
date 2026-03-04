package com.nemuria.miya.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.nemuria.miya.ui.theme.GhanaTypography
import com.nemuria.miya.ui.theme.GoldMedium
import com.nemuria.miya.ui.theme.GothicGrey
import com.nemuria.miya.ui.theme.HeirTypography

@Composable
fun GhanaText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = GhanaTypography.bodyLarge,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
    )
}

@Composable
fun HeirText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = HeirTypography.bodyLarge,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
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
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = GothicGrey,
            ),
        border = BorderStroke(1.dp, GoldMedium),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            content()
        }
    }
}
