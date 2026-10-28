package com.onlyou.com.util

/**
 * 알람 인사 문구 — 비서 말투(반말/존댓말)에 맞춘다.
 * 말투는 지금 프리셋 키로 판단한다 (casual_* = 반말). 프리셋을 없애면 이 판단 근거를 바꿔야 한다.
 */
fun isCasualSpeech(presetKey: String?): Boolean = presetKey?.startsWith("casual") == true

data class MorningGreeting(val title: String, val subtitle: String, val fallbackScript: String)

fun morningGreeting(callSign: String, presetKey: String?): MorningGreeting =
    if (isCasualSpeech(presetKey)) {
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
