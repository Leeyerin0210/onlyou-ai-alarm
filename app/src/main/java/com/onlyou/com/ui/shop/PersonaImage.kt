package com.onlyou.com.ui.shop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.onlyou.com.BuildConfig

/** 서버가 준 사진 경로를 실제 URL로 — 상대 경로는 API 서버(BASE_URL) 기준이다. */
fun resolvePersonaImageUrl(path: String?, baseUrl: String): String? {
    if (path.isNullOrBlank()) return null
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    return baseUrl.trimEnd('/') + "/" + path.trimStart('/')
}

/**
 * 비서 사진. 부모 크기를 꽉 채워 Crop하고, 사진이 없거나 로딩 전·실패 시에는
 * 뒤에 깔린 사람 아이콘이 보인다. 배경·모양은 부모가 정한다.
 */
@Composable
fun PersonaImage(
    imageUrl: String?,
    iconSize: Dp,
    iconTint: Color,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Person, null, tint = iconTint, modifier = iconModifier.size(iconSize))
        val url = resolvePersonaImageUrl(imageUrl, BuildConfig.BASE_URL)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
