package com.onlyou.com.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onlyou.com.ui.theme.MiyaTheme

/**
 * 회원가입(최초 로그인) 전 필수 동의 게이트.
 *
 * 개인정보보호법 대응:
 * - 이용약관 동의와 개인정보 수집·이용 동의를 각각 구분하여 받는다.
 * - 만 14세 이상 여부를 확인한다(제22조의2).
 */
@Composable
fun ConsentDialog(
    onAgree: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiyaTheme.colors
    var over14 by remember { mutableStateOf(false) }
    var termsAgreed by remember { mutableStateOf(false) }
    var privacyAgreed by remember { mutableStateOf(false) }
    // null이 아니면 해당 문서를 전체 화면으로 열람 중
    var viewingDocument by remember { mutableStateOf<Pair<String, String>?>(null) }

    viewingDocument?.let { (title, body) ->
        Dialog(
            onDismissRequest = { viewingDocument = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            LegalDocumentScreen(
                title = title,
                body = body,
                onBack = { viewingDocument = null },
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceA)
                .padding(24.dp),
        ) {
            Text(
                "서비스 이용 동의",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceA,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "서비스를 이용하려면 아래 항목에 모두 동의해 주세요.",
                fontSize = 13.sp,
                color = colors.neutral,
            )
            Spacer(Modifier.height(16.dp))

            val allChecked = over14 && termsAgreed && privacyAgreed
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceB)
                    .clickable {
                        val next = !allChecked
                        over14 = next; termsAgreed = next; privacyAgreed = next
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = allChecked,
                    onCheckedChange = {
                        over14 = it; termsAgreed = it; privacyAgreed = it
                    },
                    colors = CheckboxDefaults.colors(checkedColor = colors.primary, uncheckedColor = colors.neutral),
                )
                Text("전체 동의", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onSurfaceA)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.surfaceB)

            ConsentRow(
                checked = over14,
                onCheckedChange = { over14 = it },
                label = "(필수) 만 14세 이상입니다",
            )
            ConsentRow(
                checked = termsAgreed,
                onCheckedChange = { termsAgreed = it },
                label = "(필수) 서비스 이용약관 동의",
                onViewClick = { viewingDocument = "서비스 이용약관" to LegalTexts.TERMS_OF_SERVICE },
            )
            ConsentRow(
                checked = privacyAgreed,
                onCheckedChange = { privacyAgreed = it },
                label = "(필수) 개인정보 수집·이용 동의",
                onViewClick = { viewingDocument = "개인정보 처리방침" to LegalTexts.PRIVACY_POLICY },
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onAgree,
                enabled = allChecked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.White,
                    disabledContainerColor = colors.primary.copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
            ) {
                Text("동의하고 계속하기", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("취소", color = colors.neutral)
            }
        }
    }
}

@Composable
fun ConsentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    onViewClick: (() -> Unit)? = null,
) {
    val colors = MiyaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = colors.primary, uncheckedColor = colors.neutral),
        )
        Text(
            label,
            fontSize = 14.sp,
            color = colors.onSurfaceA,
            modifier = Modifier.weight(1f),
        )
        if (onViewClick != null) {
            TextButton(onClick = onViewClick) {
                Text("보기", fontSize = 13.sp, color = colors.primary)
            }
        }
    }
}
