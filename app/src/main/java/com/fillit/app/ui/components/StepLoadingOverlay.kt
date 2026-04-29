package com.fillit.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fillit.app.R
import kotlinx.coroutines.delay

private data class LoadingStep(
    val emoji: String,
    val text: String,
    val delayMs: Long,
    val progressPercent: Int
)

private enum class StepState { DONE, ACTIVE, PENDING }

@Composable
fun StepLoadingOverlay(
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val steps = remember {
        listOf(
            LoadingStep("📍", "주변 장소 탐색 중", 0L, 15),
            LoadingStep("🗓️", "일정 맥락 분석 중", 800L, 35),
            LoadingStep("💜", "취향 키워드 매칭 중", 1800L, 60),
            LoadingStep("✨", "추천 이유 생성 중", 3000L, 85),
            LoadingStep("🎯", "개인화 추천 완성!", 4500L, 100)
        )
    }

    var currentStep by remember { mutableStateOf(0) }
    val progress = remember { Animatable(0f) }
    var showOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            showOverlay = true
            currentStep = 0
            progress.snapTo(0f)

            steps.forEachIndexed { index, step ->
                val delayMs = if (index == 0) 0L else step.delayMs - steps[index - 1].delayMs
                delay(delayMs)
                currentStep = index
                val target = step.progressPercent / 100f
                progress.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 600)
                )
            }
        }
    }

    LaunchedEffect(isLoading, showOverlay) {
        if (!isLoading && showOverlay) {
            currentStep = steps.lastIndex
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350)
            )
            delay(200)
            showOverlay = false
        }
    }

    AnimatedVisibility(
        visible = showOverlay,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(300))
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "Fillit logo",
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "개인화 추천을 준비하고 있어요",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )

                Spacer(modifier = Modifier.height(40.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = { progress.value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(progress.value * 100).toInt()}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                steps.forEachIndexed { index, step ->
                    LoadingStepRow(
                        step = step,
                        state = when {
                            index < currentStep -> StepState.DONE
                            index == currentStep -> StepState.ACTIVE
                            else -> StepState.PENDING
                        }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun LoadingStepRow(step: LoadingStep, state: StepState) {
    val alpha by animateFloatAsState(
        targetValue = if (state == StepState.PENDING) 0.35f else 1f,
        animationSpec = tween(durationMillis = 350),
        label = "loading-step-alpha"
    )
    val textColor = when (state) {
        StepState.DONE -> MaterialTheme.colorScheme.primary
        StepState.ACTIVE -> Color(0xFF333333)
        StepState.PENDING -> Color(0xFFAAAAAA)
    }
    val weight = if (state == StepState.DONE) FontWeight.Bold else FontWeight.Normal

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        StepState.DONE -> MaterialTheme.colorScheme.primary
                        StepState.ACTIVE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        StepState.PENDING -> Color(0xFFEEEEEE)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                StepState.DONE -> Text("✓", color = Color.White, fontSize = 14.sp)
                StepState.ACTIVE -> Text(step.emoji, fontSize = 14.sp)
                StepState.PENDING -> Text(step.emoji, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        val animatedDots = rememberActiveDots(state == StepState.ACTIVE)
        Text(
            text = buildString {
                append(step.text)
                if (state == StepState.ACTIVE) append(animatedDots)
            },
            fontSize = 15.sp,
            fontWeight = weight,
            color = textColor
        )
    }
}

@Composable
private fun rememberActiveDots(isActive: Boolean): String {
    var dots by remember { mutableStateOf("") }

    LaunchedEffect(isActive) {
        if (!isActive) {
            dots = ""
            return@LaunchedEffect
        }

        while (isActive) {
            dots = "."
            delay(350)
            dots = ".."
            delay(350)
            dots = "..."
            delay(350)
        }
    }

    return dots
}
