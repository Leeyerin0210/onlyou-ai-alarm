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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlyou.com.data.remote.PresetDto

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
    val brandPurple = Color(0xFF8B5CF6)
    Column(modifier = modifier.fillMaxWidth()) {
        presets.forEach { preset ->
            val selected = preset.id == selectedId
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(
                        if (selected) Color(0xFFF3EEFF) else Color.White,
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) brandPurple else Color(0xFFE0E0E0),
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(preset.id) }
                    .padding(16.dp),
            ) {
                Text(preset.label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(preset.description, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}
