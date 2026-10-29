package com.onlyou.com.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.onlyou.com.ui.theme.DangerRed
import com.onlyou.com.ui.theme.MiyaTheme

/** 앱 공통 한 줄 입력창. [isPassword]면 가림 처리하고, 토글 콜백을 주면 보기/숨기기 아이콘을 단다. */
@Composable
fun MiyaOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordVisibilityToggle: (() -> Unit)? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MiyaTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        isError = isError,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.neutral.copy(alpha = 0.6f),
            )
        },
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = if (isPassword && onPasswordVisibilityToggle != null) {
            {
                IconButton(onClick = onPasswordVisibilityToggle) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "비밀번호 숨기기" else "비밀번호 보기",
                        tint = colors.neutral,
                    )
                }
            }
        } else {
            null
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.surfaceB,
            focusedContainerColor = colors.surfaceA,
            unfocusedContainerColor = colors.surfaceA,
            focusedTextColor = colors.onSurfaceA,
            unfocusedTextColor = colors.onSurfaceA,
            cursorColor = colors.primary,
            errorBorderColor = DangerRed,
            errorCursorColor = DangerRed,
        ),
        singleLine = true,
    )
}
