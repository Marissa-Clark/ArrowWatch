package com.archery.wear.presentation

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.archery.shared.ScoreZone
import com.archery.wear.presentation.theme.WatchAmber
import com.archery.wear.presentation.theme.WatchBg
import com.archery.wear.presentation.theme.WatchBtnDanger
import com.archery.wear.presentation.theme.WatchGreen
import com.archery.wear.presentation.theme.WatchRed
import com.archery.wear.presentation.theme.WatchSurfaceLight
import com.archery.wear.presentation.theme.WatchTextMuted
import com.archery.wear.presentation.theme.WatchTextPrimary
import com.archery.wear.presentation.theme.WatchTextSecondary
import kotlinx.coroutines.delay

/** Map a ScoreZone to an arc segment colour. */
private fun zoneArcColor(zone: ScoreZone?): Color = when (zone) {
    ScoreZone.GOLD  -> Color(0xFFD97706)  // warm amber
    ScoreZone.RED   -> Color(0xFFDC2626)  // red
    ScoreZone.BLUE  -> Color(0xFF2563EB)  // blue
    ScoreZone.BLACK -> Color(0xFF475569)  // dark slate — visible on near-black bg
    ScoreZone.WHITE -> Color(0xFFCBD5E1)  // light gray
    ScoreZone.MISS  -> Color(0xFF64748B)  // muted
    ScoreZone.DNS   -> Color(0xFF374151)  // very dim — did-not-shoot
    null            -> Color(0xFF1E3A4A)  // placeholder / no zone yet
}

@Composable
fun ShootingScreen(
    shotCount: Int,
    arrowsPerRound: Int,
    endNumber: Int,
    heartRate: Float,
    /** Per-arrow average for the most recently completed end; null if first end. */
    lastEndAvg: Float?,
    totalScore: Float,
    avgPerArrow: Float,
    walkingSteps: Int,
    isApprox: Boolean = false,
    roundStartMs: Long = 0L,
    /** Zone for every arrow shot in completed ends this session (drives the arc). */
    sessionShotZones: List<ScoreZone?> = emptyList(),
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
                if (event.verticalScrollPixels > 0) { onEnterScoring(); true } else false
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        // ── Arc ring — session shot history around the bezel ─────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 7.dp.toPx()
            val inset = 3.dp.toPx()
            val diameter = size.minDimension - strokeWidth - inset * 2
            val radius = diameter / 2
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(diameter, diameter)

            // Ghost base ring — always present so the bezel looks intentional
            drawArc(
                color = Color(0xFF1E293B),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )

            // Coloured segments — one per completed arrow
            if (sessionShotZones.isNotEmpty()) {
                val total = sessionShotZones.size
                val sweepEach = 360f / total
                // Smaller gap when there are many arrows so segments don't vanish
                val gapDeg = if (total > 20) 1f else if (total > 10) 1.5f else 2.5f

                sessionShotZones.forEachIndexed { i, zone ->
                    drawArc(
                        color = zoneArcColor(zone),
                        startAngle = -90f + i * sweepEach + gapDeg / 2f,
                        sweepAngle = (sweepEach - gapDeg).coerceAtLeast(1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
        }

        // ── Main content ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // End label — bigger, bolder than before
            Text(
                text = "END $endNumber",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = WatchTextSecondary,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Heart rate — dominant central metric
            Text(
                text = if (heartRate > 0) "${heartRate.toInt()}" else "—",
                fontSize = 44.sp,
                fontWeight = FontWeight.Light,
                color = if (heartRate > 0) WatchRed else WatchTextMuted,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "bpm",
                fontSize = 9.sp,
                color = WatchTextMuted,
                letterSpacing = 1.sp,
            )

            // Last end avg — shows after first end is completed
            Spacer(modifier = Modifier.height(3.dp))
            if (lastEndAvg != null) {
                Text(
                    text = "last  %.1f / arrow".format(lastEndAvg),
                    fontSize = 9.sp,
                    color = WatchGreen,
                    letterSpacing = 0.3.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                // Placeholder height so layout doesn't jump when it appears
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Session totals
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (totalScore > 0) "%.0f".format(totalScore) else "—",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WatchTextPrimary,
                    )
                    Text(text = "total", fontSize = 8.sp, color = WatchTextMuted, letterSpacing = 0.5.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (avgPerArrow > 0) "%.1f".format(avgPerArrow) else "—",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WatchAmber,
                    )
                    Text(text = "avg", fontSize = 8.sp, color = WatchTextMuted, letterSpacing = 0.5.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onEnterScoring,
                modifier = Modifier.fillMaxWidth(0.78f).height(36.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = WatchSurfaceLight),
            ) {
                Text(
                    text = "Score End",
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

        // Timer — top-right overlay, only once a real round has started
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

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true, name = "Shooting – first end")
@Composable
private fun ShootingPreviewFirst() {
    com.archery.wear.presentation.theme.ArcheryTheme {
        ShootingScreen(
            shotCount = 0,
            arrowsPerRound = 3,
            endNumber = 1,
            heartRate = 68f,
            lastEndAvg = null,
            totalScore = 0f,
            avgPerArrow = 0f,
            walkingSteps = 0,
            roundStartMs = 0L,
            sessionShotZones = emptyList(),
            onEnterScoring = {},
            onEndSession = {},
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true, name = "Shooting – mid session 3×10")
@Composable
private fun ShootingPreviewMid() {
    com.archery.wear.presentation.theme.ArcheryTheme {
        ShootingScreen(
            shotCount = 0,
            arrowsPerRound = 3,
            endNumber = 5,
            heartRate = 84f,
            lastEndAvg = 8.3f,
            totalScore = 99f,
            avgPerArrow = 8.3f,
            walkingSteps = 0,
            roundStartMs = System.currentTimeMillis() - 95_000L,
            sessionShotZones = listOf(
                ScoreZone.GOLD, ScoreZone.RED, ScoreZone.GOLD,   // end 1
                ScoreZone.GOLD, ScoreZone.BLUE, ScoreZone.RED,   // end 2
                ScoreZone.RED,  ScoreZone.GOLD, ScoreZone.BLUE,  // end 3
                ScoreZone.MISS, ScoreZone.GOLD, ScoreZone.RED,   // end 4
            ),
            onEnterScoring = {},
            onEndSession = {},
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true, name = "Shooting – late session 3×10")
@Composable
private fun ShootingPreviewLate() {
    com.archery.wear.presentation.theme.ArcheryTheme {
        ShootingScreen(
            shotCount = 0,
            arrowsPerRound = 3,
            endNumber = 9,
            heartRate = 91f,
            lastEndAvg = 7.7f,
            totalScore = 222f,
            avgPerArrow = 7.9f,
            walkingSteps = 0,
            roundStartMs = System.currentTimeMillis() - 210_000L,
            sessionShotZones = listOf(
                ScoreZone.GOLD, ScoreZone.RED,  ScoreZone.GOLD,
                ScoreZone.GOLD, ScoreZone.BLUE, ScoreZone.RED,
                ScoreZone.RED,  ScoreZone.GOLD, ScoreZone.BLUE,
                ScoreZone.MISS, ScoreZone.GOLD, ScoreZone.RED,
                ScoreZone.GOLD, ScoreZone.GOLD, ScoreZone.RED,
                ScoreZone.BLUE, ScoreZone.RED,  ScoreZone.GOLD,
                ScoreZone.RED,  ScoreZone.MISS, ScoreZone.BLUE,
                ScoreZone.GOLD, ScoreZone.RED,  ScoreZone.GOLD,
            ),
            onEnterScoring = {},
            onEndSession = {},
        )
    }
}
