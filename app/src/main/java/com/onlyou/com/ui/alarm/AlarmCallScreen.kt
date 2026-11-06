package com.onlyou.com.ui.alarm

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlyou.com.domain.model.AiSchedule
import com.onlyou.com.domain.model.Persona
import com.onlyou.com.domain.repository.WeatherInfo
import com.onlyou.com.service.AlarmCallPhase
import com.onlyou.com.ui.shop.PersonaImage
import com.onlyou.com.util.SNOOZE_MINUTES
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val CallBgTop = Color(0xFF241A3D)
private val CallBgBottom = Color(0xFF0D0A16)
private val AcceptGreen = Color(0xFF34C759)
private val DeclineRed = Color(0xFFFF3B30)

/** 통화 시간 표기: 65초 → "01:05" */
fun formatCallDuration(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

/** 이름 아래 한 줄. 받은 뒤엔 전화처럼 통화 시간을 보여준다. */
fun callStatusLabel(phase: AlarmCallPhase, elapsedSeconds: Long): String = when (phase) {
    AlarmCallPhase.RINGING -> "전화가 왔어요"
    AlarmCallPhase.CONNECTING -> "연결 중…"
    AlarmCallPhase.SPEAKING -> formatCallDuration(elapsedSeconds)
    AlarmCallPhase.FINISHED, AlarmCallPhase.ENDED -> "통화 종료"
    AlarmCallPhase.FAILED -> "지금은 연결이 안 돼요"
}

/** 이름 위 작은 라벨 — 아침이면 모닝콜, 그 외엔 알람 콜. ("좋은 아침"을 아무 때나 하지 않는다) */
fun callKindLabel(now: LocalTime): String = if (now.hour in 4..11) "온리유 모닝콜" else "온리유 알람 콜"

@Composable
fun AlarmCallContent(
    persona: Persona?,
    phase: AlarmCallPhase,
    todaySchedules: List<AiSchedule>,
    weathers: List<WeatherInfo>,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onSnooze: () -> Unit,
) {
    val ringing = phase == AlarmCallPhase.RINGING

    // 받은 순간부터 통화 시간을 잰다
    var answeredAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(ringing) {
        if (!ringing && answeredAt == 0L) answeredAt = System.currentTimeMillis()
        while (!ringing) {
            elapsed = (System.currentTimeMillis() - answeredAt) / 1000
            delay(500)
        }
    }

    Box(Modifier.fillMaxSize().background(CallBgBottom)) {
        // 비서 사진을 흐리게 깔아 '그 사람에게서 온 전화' 느낌 (blur는 안드로이드 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PersonaImage(
                imageUrl = persona?.imageUrl,
                iconSize = 0.dp,
                iconTint = Color.Transparent,
                modifier = Modifier.blur(48.dp).alpha(0.45f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(CallBgTop.copy(alpha = 0.75f), CallBgBottom.copy(alpha = 0.95f))),
            ),
        )

        Column(
            Modifier
                .fillMaxSize()
                // 상태바를 숨기므로 카메라 컷아웃까지 포함한 safeDrawing으로 비킨다
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Text(
                callKindLabel(LocalTime.now()),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                persona?.name ?: "온리유",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            AnimatedContent(
                targetState = callStatusLabel(phase, elapsed),
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "status",
            ) { label ->
                Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp)
            }

            Spacer(Modifier.height(48.dp))
            CallAvatar(persona = persona, ringing = ringing)
            Spacer(Modifier.height(28.dp))
            if (phase == AlarmCallPhase.SPEAKING) VoiceBars() else Spacer(Modifier.height(24.dp))

            Spacer(Modifier.weight(1f))

            if (!ringing) {
                TodayBriefCard(todaySchedules, weathers)
                Spacer(Modifier.height(32.dp))
            }

            if (ringing) {
                SnoozePill(onSnooze)
                Spacer(Modifier.height(36.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CallButton(Icons.Default.CallEnd, DeclineRed, "끄기", onDecline)
                    CallButton(Icons.Default.Call, AcceptGreen, "받기", onAnswer, bounce = true)
                }
            } else {
                CallButton(Icons.Default.CallEnd, DeclineRed, "끊기", onDecline)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun CallAvatar(persona: Persona?, ringing: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "pulse",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
        if (ringing) {
            // 벨이 울리는 동안 퍼져 나가는 고리 두 개
            listOf(0f, 0.5f).forEach { offset ->
                val t = (pulse + offset) % 1f
                Box(
                    Modifier
                        .size(150.dp)
                        .scale(1f + t * 0.6f)
                        .alpha((1f - t) * 0.35f)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
        Box(
            Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        ) {
            PersonaImage(
                imageUrl = persona?.imageUrl,
                iconSize = 64.dp,
                iconTint = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

/** 말하는 중임을 보여주는 음성 막대 */
@Composable
private fun VoiceBars() {
    val transition = rememberInfiniteTransition(label = "voice")
    Row(
        Modifier.height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(0, 180, 360, 120, 300).forEach { delayMs ->
            val h by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(420, delayMillis = delayMs, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse,
                ),
                label = "bar",
            )
            Box(
                Modifier
                    .width(4.dp)
                    .height(24.dp * h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.8f)),
            )
        }
    }
}

@Composable
private fun SnoozePill(onSnooze: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onSnooze)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Snooze, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("${SNOOZE_MINUTES}분 뒤 다시 걸기", color = Color.White, fontSize = 15.sp)
    }
}

@Composable
private fun CallButton(
    icon: ImageVector,
    color: Color,
    label: String,
    onClick: () -> Unit,
    bounce: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "bounce")
    val lift by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (bounce) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "lift",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .padding(bottom = (lift * 8).dp)
                .size(76.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(10.dp + ((1 - lift) * 8).dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
    }
}

/**
 * 통화 중 화면 카드: 오늘 일정과 날씨 요약. 음성 대본은 TTS용으로 숫자를 한글로 풀어 써서
 * ('칠십 프로') 읽기 어색하므로, 화면에는 구조화 데이터로 따로 보여준다.
 */
@Composable
private fun TodayBriefCard(schedules: List<AiSchedule>, weathers: List<WeatherInfo>) {
    val time = remember { DateTimeFormatter.ofPattern("HH:mm") }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("오늘", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        if (schedules.isEmpty()) {
            Text("예정된 일정이 없어요", color = Color.White, fontSize = 15.sp)
        } else {
            schedules.take(3).forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.startTime?.format(time) ?: "종일",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        modifier = Modifier.width(52.dp),
                    )
                    Text(
                        s.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (schedules.size > 3) {
                Text("외 ${schedules.size - 3}개", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
            }
        }
        weathers.forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (w.weatherCode < 3) Icons.Default.WbSunny else Icons.Default.Cloud,
                    null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${w.locationName} ${w.temperature.toInt()}° · 강수 ${w.precipitationProbability}%",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun AlarmCallRingingPreview() {
    AlarmCallContent(null, AlarmCallPhase.RINGING, emptyList(), emptyList(), {}, {}, {})
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun AlarmCallSpeakingPreview() {
    AlarmCallContent(
        null,
        AlarmCallPhase.SPEAKING,
        listOf(AiSchedule(title = "헬스장", startTime = LocalTime.of(7, 0))),
        emptyList(),
        {},
        {},
        {},
    )
}
