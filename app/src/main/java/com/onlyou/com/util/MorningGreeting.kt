package com.onlyou.com.util

/**
 * 알람 인사 문구 — 비서 말투(반말/존댓말)에 맞춘다.
 * 말투 칸에 "반말"이 있으면 반말, "존댓말"이 있으면 존댓말이다.
 * 말투 칸이 비었거나 둘 다 없으면 예전 프리셋 키(casual_* = 반말)로 판단한다.
 */
fun isCasualSpeech(presetKey: String?, speechStyle: String? = null): Boolean =
    when {
        speechStyle?.contains("반말") == true -> true
        speechStyle?.contains("존댓말") == true -> false
        else -> presetKey?.startsWith("casual") == true
    }

data class MorningGreeting(val title: String, val subtitle: String, val fallbackScript: String)

fun morningGreeting(callSign: String, presetKey: String?, speechStyle: String? = null): MorningGreeting =
    if (isCasualSpeech(presetKey, speechStyle)) {
        MorningGreeting(
            title = "좋은 아침, $callSign! ☀️",
            subtitle = "오늘도 잘해 보자.",
            fallbackScript = "$callSign, 좋은 아침! 오늘도 힘내자.",
        )
    } else {
        MorningGreeting(
            title = "좋은 아침이에요, $callSign! ☀️",
            subtitle = "오늘도 멋진 하루가 될 거예요.",
            fallbackScript = "$callSign, 좋은 아침이에요! 오늘도 화이팅하세요.",
        )
    }
