package com.onlyou.com.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlyou.com.ui.components.MiyaTopAppBar
import com.onlyou.com.ui.theme.MiyaTheme

/** 이용약관/개인정보 처리방침 등 법적 문서 열람 화면. */
@Composable
fun LegalDocumentScreen(
    title: String,
    body: String,
    onBack: () -> Unit,
) {
    val colors = MiyaTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        MiyaTopAppBar(title, onBack)
        Text(
            text = body,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = colors.onSurfaceA,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}
