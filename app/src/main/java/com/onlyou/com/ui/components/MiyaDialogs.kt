package com.onlyou.com.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlyou.com.ui.theme.DangerRed
import com.onlyou.com.ui.theme.MiyaTheme

/**
 * 앱 공통 확인 다이얼로그.
 *
 * @param message 본문 설명. [content]와 함께 주면 설명 아래에 [content]가 붙는다.
 * @param destructive true면 확인 버튼을 경고색으로 칠한다 (탈퇴·초기화처럼 되돌릴 수 없는 동작).
 * @param isLoading true면 확인 버튼 자리에 로딩을 띄우고, 바깥 탭·취소로 닫히지 않게 막는다.
 */
@Composable
fun MiyaAlertDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    dismissText: String = "취소",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    isLoading: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    val colors = MiyaTheme.colors
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = colors.surfaceA,
        title = { Text(title, color = colors.onSurfaceA, fontWeight = FontWeight.Bold) },
        text = if (message == null && content == null) {
            null
        } else {
            {
                Column {
                    if (message != null) {
                        Text(message, color = colors.neutral, fontSize = 14.sp)
                    }
                    if (message != null && content != null) {
                        Spacer(Modifier.height(16.dp))
                    }
                    content?.invoke()
                }
            }
        },
        confirmButton = {
            val enabled = confirmEnabled && !isLoading
            TextButton(onClick = onConfirm, enabled = enabled) {
                if (isLoading) {
                    ButtonProgress(color = colors.primary, size = 16.dp)
                } else {
                    Text(
                        confirmText,
                        color = when {
                            !enabled -> colors.neutral
                            destructive -> DangerRed
                            else -> colors.primary
                        },
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(dismissText, color = colors.neutral)
            }
        },
    )
}

/** 시·분 선택 다이얼로그. [is24Hour]가 false면 오전/오후 표시. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiyaTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    is24Hour: Boolean = true,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = is24Hour)
    MiyaAlertDialog(
        title = title,
        confirmText = "확인",
        onConfirm = { onConfirm(state.hour, state.minute) },
        onDismiss = onDismiss,
        content = { TimePicker(state = state) },
    )
}
