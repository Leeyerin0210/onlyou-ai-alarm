package com.onlyou.com.ui.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlyou.com.data.remote.PresetDto
import com.onlyou.com.ui.theme.MiyaTheme

/**
 * 성격 프리셋 선택.
 *
 * 자유 프롬프트 입력을 대체한다. 프롬프트 본문은 서버 상수에만 있으므로
 * 여기서는 라벨과 한 줄 설명만 보여준다.
 */
@Composable
fun PresetPicker(
    presets: List<PresetDto>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiyaTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        presets.forEach { preset ->
            val selected = preset.id == selectedId
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(
                        if (selected) colors.surfaceB else colors.surfaceA,
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) colors.primary else colors.neutral.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(preset.id) }
                    .padding(16.dp),
            ) {
                Text(preset.label, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.onSurfaceA)
                Spacer(Modifier.height(4.dp))
                Text(preset.description, fontSize = 12.sp, color = colors.neutral)
            }
        }
    }
}
