package com.onlyou.com.ui.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlyou.com.domain.model.TagNormalizer
import com.onlyou.com.ui.theme.MiyaTheme

/**
 * 해시태그 입력 — 스페이스·쉼표·완료 키·"추가"로 칩을 확정한다.
 * 정규화는 TagNormalizer(서버와 같은 규칙)로 즉시 반영한다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagInput(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
) {
    val colors = MiyaTheme.colors
    var text by remember { mutableStateOf("") }
    val isFull = tags.size >= TagNormalizer.MAX_TAGS

    fun commit(raw: String) {
        val tag = TagNormalizer.normalizeOne(raw)
        if (tag != null) onTagsChange(TagNormalizer.normalize(tags + tag))
        text = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.primary.copy(alpha = 0.12f))
                            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("#$tag", fontSize = 13.sp, color = colors.primary)
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "$tag 태그 삭제",
                            tint = colors.primary,
                            modifier = Modifier.size(14.dp).clickable { onTagsChange(tags - tag) },
                        )
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFEFEFEF), RoundedCornerShape(12.dp))
                .background(colors.surfaceA, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(
                        if (isFull) "태그는 ${TagNormalizer.MAX_TAGS}개까지 달 수 있어요" else "#아침 #다정 처럼 입력해주세요",
                        fontSize = 14.sp,
                        color = Color.Gray,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { v ->
                        // 스페이스·쉼표가 입력되면 그 앞까지를 태그로 확정한다
                        if (v.isNotEmpty() && (v.last().isWhitespace() || v.last() == ',')) commit(v.dropLast(1)) else text = v
                    },
                    enabled = !isFull,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = colors.onSurfaceA),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit(text) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (text.isNotBlank()) {
                Text(
                    "추가",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                    modifier = Modifier.padding(start = 8.dp).clickable { commit(text) },
                )
            }
        }
    }
}
