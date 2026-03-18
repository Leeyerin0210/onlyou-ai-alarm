package com.nemuria.miya.ui.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nemuria.miya.ui.theme.MiyaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmTitleCard() {
    // 1. 사용자가 입력한 텍스트를 저장할 상태 (임시로 "기상 알람" 설정)
    var title by remember { mutableStateOf("기상 알람") }
    val colors = MiyaTheme.colors // 테마 컬러 사용

    // 2. 외부 둥근 카드 컨테이너 (그림자 + 배경 + 패딩)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp) // 화면 양옆 여백
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = colors.primary.copy(alpha = 0.5f)
            )
            .background(
                color = colors.surface,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp), // 카드 내부 컨텐츠 여백
    ) {
        // 고정된 제목 텍스트
        Text(
            text = "Alarm Title",
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            fontSize = 14.sp, // 제목은 조금 작게
            modifier = Modifier.align(Alignment.Start)
        )
        
        Spacer(Modifier.height(12.dp))

        // ---------------------------------------------------------
        // ✨ [핵심 부분] 글자 수정이 가능한 커스텀 텍스트 필드
        // ---------------------------------------------------------
        val interactionSource = remember { MutableInteractionSource() }

        BasicTextField(
            value = title,
            onValueChange = { title = it }, // 글자가 바뀔 때마다 상태 업데이트
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interactionSource,
            // 입력되는 텍스트 스타일 (크고 진하게)
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = colors.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            ),
            singleLine = true, // 한 줄 입력으로 제한
            decorationBox = @Composable { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = title,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    // 입력 칸이 비었을 때 보여줄 가이드 문구
                    placeholder = { Text("알람 이름을 입력해 주세요", color = colors.onSurface.copy(alpha = 0.4f)) },
                    shape = RoundedCornerShape(12.dp), // 입력 칸 자체 모서리
                    colors = TextFieldDefaults.colors(
                        // 입력 칸 배경색 (카드 surface와 대비되게 살짝 다르게 줘도 좋습니다)
                        focusedContainerColor = colors.background.copy(alpha = 0.5f), 
                        unfocusedContainerColor = colors.background.copy(alpha = 0.5f),
                        // 둥근 모서리일 때 밑줄 제거
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    // ✨ 중요한 내부 패딩 설정
                    contentPadding = PaddingValues(
                        horizontal = 16.dp, // 양옆 여백
                        vertical = 12.dp    // 위아래 여백
                    )
                )
            }
        )
    }
}