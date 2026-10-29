package com.onlyou.com.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlyou.com.ui.theme.MiyaTheme

// 앱 전체에서 공통으로 쓰는 입력·버튼 컨트롤.
// 화면마다 색·크기를 따로 지정하지 말고 여기 것을 쓴다 — 디자인을 바꿀 땐 이 파일만 고친다.

private val ButtonShape = RoundedCornerShape(14.dp)
private val ButtonHeight = 56.dp

/** 앱 공통 토글. 켜짐 = primary 트랙 + 배경색 썸, 꺼짐 = 흐린 neutral 트랙. */
@Composable
fun MiyaSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MiyaTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.background,
            checkedTrackColor = colors.primary,
            checkedBorderColor = colors.primary,
            uncheckedThumbColor = colors.neutral,
            uncheckedTrackColor = colors.neutral.copy(alpha = 0.2f),
            uncheckedBorderColor = colors.neutral.copy(alpha = 0.4f),
        ),
    )
}

/** 제목 + (선택) 설명 + 오른쪽 [MiyaSwitch]. 설정성 토글 한 줄. */
@Composable
fun MiyaToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = MiyaTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = colors.onSurfaceA)
            if (description != null) {
                Text(description, fontSize = 12.sp, color = colors.neutral)
            }
        }
        Spacer(Modifier.width(12.dp))
        MiyaSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 버튼 안에서 쓰는 작은 로딩 표시. */
@Composable
fun ButtonProgress(color: Color, size: Dp = 20.dp) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = color,
        strokeWidth = 2.dp,
    )
}

/** 화면 하단 주요 동작 버튼 (꽉 찬 너비, primary 배경). */
@Composable
fun MiyaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val colors = MiyaTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = Color.White,
            disabledContainerColor = colors.primary.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.7f),
        ),
    ) {
        if (isLoading) {
            ButtonProgress(color = Color.White)
        } else {
            ButtonLabel(text, leadingIcon)
        }
    }
}

/** 취소·삭제 같은 보조 동작 버튼 (테두리만). [MiyaPrimaryButton]과 같은 크기. */
@Composable
fun MiyaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = MiyaTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
        shape = ButtonShape,
        border = BorderStroke(1.dp, colors.neutral.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurfaceA),
    ) {
        ButtonLabel(text, leadingIcon)
    }
}

@Composable
private fun RowScope.ButtonLabel(text: String, leadingIcon: ImageVector?) {
    if (leadingIcon != null) {
        Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}
