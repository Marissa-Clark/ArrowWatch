package com.archery.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.archery.wear.presentation.theme.WatchAmber
import com.archery.wear.presentation.theme.WatchBg
import com.archery.wear.presentation.theme.WatchBtnDanger
import com.archery.wear.presentation.theme.WatchGreenDim
import com.archery.wear.presentation.theme.WatchRed
import com.archery.wear.presentation.theme.WatchSurfaceLight
import com.archery.wear.presentation.theme.WatchTextMuted
import com.archery.wear.presentation.theme.WatchTextPrimary
import com.archery.wear.presentation.theme.WatchTextSecondary
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.tooling.preview.devices.WearDevices
import kotlinx.coroutines.delay

@Composable
fun ShootingScreen(
    shotCount: Int,
    arrowsPerRound: Int,
    roundNumber: Int,
    heartRate: Float,
    previousRoundInfo: String?,
    totalScore: Float,
    avgPerArrow: Float,
    walkingSteps: Int,
    isApprox: Boolean = false,
    roundStartMs: Long = 0L,
    onEnterScoring: () -> Unit,
    onEndSession: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Elapsed timer — only ticks when roundStartMs is a real timestamp
    var elapsedSec by remember(roundStartMs) { mutableLongStateOf(0L) }
    LaunchedEffect(roundStartMs) {
        if (roundStartMs <= 0L) return@LaunchedEffect
        while (true) {
            elapsedSec = (System.currentTimeMillis() - roundStartMs) / 1000L
            delay(1000L)
        }
    }
    val timerText = "%d:%02d".format(elapsedSec / 60, elapsedSec % 60)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchBg)
            .onRotaryScrollEvent { event ->
                // Crown forward (clockwise) → enter scoring; backward → scroll
                if (event.verticalScrollPixels > 0) { onEnterScoring(); true } else false
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Round label — centered
            Text(
                text = "ROUND $roundNumber",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = WatchTextMuted,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
            )

            if (previousRoundInfo != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = previousRoundInfo,
                    fontSize = 9.sp,
                    color = WatchGreenDim,
                    letterSpacing = 0.2.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // HR — large, light weight
            Text(
                text = if (heartRate > 0) "${heartRate.toInt()}" else "—",
                fontSize = 42.sp,
                fontWeight = FontWeight.Light,
                color = if (heartRate > 0) WatchRed else WatchTextMuted,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "BPM",
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                color = WatchTextMuted,
                letterSpacing = 1.5.sp,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Session score stats — total and avg per arrow
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (totalScore > 0) "%.0f".format(totalScore) else "—",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WatchTextPrimary,
                    )
                    Text(
                        text = "total",
                        fontSize = 8.sp,
                        color = WatchTextMuted,
                        letterSpacing = 0.5.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (avgPerArrow > 0) "%.1f".format(avgPerArrow) else "—",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WatchAmber,
                    )
                    Text(
                        text = "avg",
                        fontSize = 8.sp,
                        color = WatchTextMuted,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Score Round — visible surface button (SurfaceLight for contrast against bg)
            Button(
                onClick = onEnterScoring,
                modifier = Modifier.fillMaxWidth(0.80f).height(36.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = WatchSurfaceLight),
            ) {
                Text(
                    text = "Score Round",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = WatchTextSecondary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onEndSession,
                modifier = Modifier.width(56.dp).height(26.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = WatchBtnDanger),
            ) {
                Text("End", fontSize = 10.sp, color = WatchTextPrimary)
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Timer — top-right overlay, only shown once a real round has started
        if (roundStartMs > 0L) {
            Text(
                text = timerText,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 10.dp),
                fontSize = 9.sp,
                color = WatchTextMuted,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true, name = "Shooting – new round")
@Composable
private fun ShootingPreviewFresh() {
    com.archery.wear.presentation.theme.ArcheryTheme {
        ShootingScreen(
            shotCount = 0,
            arrowsPerRound = 3,
            roundNumber = 1,
            heartRate = 0f,
            previousRoundInfo = null,
            totalScore = 0f,
            avgPerArrow = 0f,
            walkingSteps = 0,
            roundStartMs = 0L,
            onEnterScoring = {},
            onEndSession = {},
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true, name = "Shooting – mid session")
@Composable
private fun ShootingPreviewMidSession() {
    com.archery.wear.presentation.theme.ArcheryTheme {
        ShootingScreen(
            shotCount = 0,
            arrowsPerRound = 3,
            roundNumber = 4,
            heartRate = 82f,
            previousRoundInfo = "R3: 8.7 avg",
            totalScore = 78f,
            avgPerArrow = 8.7f,
            walkingSteps = 0,
            // Fixed offset so preview shows ~2:15 elapsed
            roundStartMs = System.currentTimeMillis() - 135_000L,
            onEnterScoring = {},
            onEndSession = {},
        )
    }
}
