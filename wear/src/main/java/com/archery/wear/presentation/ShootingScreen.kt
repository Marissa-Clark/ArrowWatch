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
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.text.TextStyle
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
import com.archery.wear.presentation.theme.WatchBg
import com.archery.wear.presentation.theme.WatchBtnDanger
import com.archery.wear.presentation.theme.WatchRed
import com.archery.wear.presentation.theme.WatchSurfaceLight
import com.archery.wear.presentation.theme.WatchTextMuted
import com.archery.wear.presentation.theme.WatchTextPrimary
import com.archery.wear.presentation.theme.WatchTextSecondary
import kotlinx.coroutines.delay

// ── Score zone colour mapping for arc segments ───────────────────────────────

private fun zoneArcColor(zone: ScoreZone?): Color = when (zone) {
    ScoreZone.GOLD  -> Color(0xFFD97706)
    ScoreZone.RED   -> Color(0xFFDC2626)
    ScoreZone.BLUE  -> Color(0xFF2563EB)
    ScoreZone.BLACK -> Color(0xFF475569)
    ScoreZone.WHITE -> Color(0xFFCBD5E1)
    ScoreZone.MISS  -> Color(0xFF64748B)
    ScoreZone.DNS   -> Color(0xFF374151)
    null            -> Color(0xFF1A2A35)
}

// ── Colour a metric by which archery zone it falls into ──────────────────────
// avg ≥ 9 → gold, ≥ 7 → red, ≥ 5 → blue, ≥ 3 → gray, else dim

private fun scoreColor(avg: Float): Color = when {
    avg >= 9f -> Color(0xFFD97706)   // gold zone
    avg >= 7f -> Color(0xFFEF4444)   // red zone
    avg >= 5f -> Color(0xFF3B82F6)   // blue zone
    avg >= 3f -> Color(0xFF94A3B8)   // black zone — lightened for visibility
    avg > 0f  -> Color(0xFF64748B)   // white zone
    else      -> Color(0xFF334155)   // no data yet
}

// ── Tabular-number text style so digits don't shift width as values change ───

private val MetricStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun ShootingScreen(
    shotCount: Int,
    arrowsPerRound: Int,
    endNumber: Int,
    heartRate: Float,
    lastEndAvg: Float?,
    totalScore: Float,
    avgPerArrow: Float,
    walkingSteps: Int,
    isApprox: Boolean = false,
    roundStartMs: Long = 0L,
    sessionShotZones: List<ScoreZone?> = emptyList(),
    onEnterScoring: () -> Unit,
    onEndSession: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
        // ── Arc ring ─────────────────────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 7.dp.toPx()
            val inset = 3.dp.toPx()
            val diameter = size.minDimension - strokeWidth - inset * 2
            val radius = diameter / 2
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(diameter, diameter)

            // Ghost ring
            drawArc(
                color = Color(0xFF111827),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
            // Coloured segments
            if (sessionShotZones.isNotEmpty()) {
                val total = sessionShotZones.size
                val sweepEach = 360f / total
                val gapDeg = if (total > 20) 1f else if (total > 10) 1.5f else 2.5f
                sessionShotZones.forEachIndexed { i, zone ->
                    drawArc(
                        color = zoneArcColor(zone),
                        startAngle = -90f + i * sweepEach + gapDeg / 2f,
                        sweepAngle = (sweepEach - gapDeg).coerceAtLeast(1f),
                        useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
        }

        // ── Main layout ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {

            // ── TOP: End number ───────────────────────────────────────────────
            Text(
                text = "END  $endNumber",
                style = MetricStyle.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 3.sp,
                    color = WatchTextSecondary,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── CENTER: Score metrics ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Last end avg
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (lastEndAvg != null) "%.1f".format(lastEndAvg) else "—",
                        style = MetricStyle.copy(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Thin,
                            color = if (lastEndAvg != null) scoreColor(lastEndAvg) else WatchTextMuted,
                        ),
                    )
                    Text(
                        text = "LAST",
                        style = TextStyle(
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 1.5.sp,
                            color = WatchTextMuted,
                        ),
                    )
                }

                // Vertical divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(Color(0xFF1E293B)),
                )

                // Overall avg per arrow
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (avgPerArrow > 0f) "%.1f".format(avgPerArrow) else "—",
                        style = MetricStyle.copy(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Thin,
                            color = if (avgPerArrow > 0f) scoreColor(avgPerArrow) else WatchTextMuted,
                        ),
                    )
                    Text(
                        text = "AVG",
                        style = TextStyle(
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 1.5.sp,
                            color = WatchTextMuted,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── BOTTOM: Heart rate ────────────────────────────────────────────
            Text(
                text = if (heartRate > 0f) "${heartRate.toInt()}" else "—",
                style = MetricStyle.copy(
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Thin,
                    color = if (heartRate > 0f) WatchRed else WatchTextMuted,
                ),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "BPM",
                style = TextStyle(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 2.sp,
                    color = WatchTextMuted,
                ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Score End — main action (crown forward also works)
            Button(
                onClick = onEnterScoring,
                modifier = Modifier.fillMaxWidth(0.75f).height(32.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = WatchSurfaceLight),
            ) {
                Text(
                    text = "SCORE END",
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 1.sp,
                        color = WatchTextSecondary,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // End session — small, danger-red
            Button(
                onClick = onEndSession,
                modifier = Modifier.width(52.dp).height(24.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = WatchBtnDanger),
            ) {
                Text(
                    text = "END",
                    style = TextStyle(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 1.sp,
                        color = WatchTextPrimary,
                    ),
                )
            }
        }

        // ── Timer — top-right overlay ────────────────────────────────────────
        if (roundStartMs > 0L) {
            Text(
                text = timerText,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 12.dp),
                style = TextStyle(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp,
                    color = WatchTextMuted,
                    fontFeatureSettings = "tnum",
                ),
            )
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true, name = "End 1 – no prior data")
@Composable
private fun ShootingPreviewFirst() {
    com.archery.wear.presentation.theme.ArcheryTheme {
        ShootingScreen(
            shotCount = 0, arrowsPerRound = 3, endNumber = 1,
            heartRate = 65f,
            lastEndAvg = null,
            totalScore = 0f, avgPerArrow = 0f,
            walkingSteps = 0, roundStartMs = 0L,
            sessionShotZones = emptyList(),
            onEnterScoring = {}, onEndSession = {},
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true, name = "End 5 – gold range")
@Composable
private fun ShootingPreviewGold() {
    com.archery.wear.presentation.theme.ArcheryTheme {
        ShootingScreen(
            shotCount = 0, arrowsPerRound = 3, endNumber = 5,
            heartRate = 82f,
            lastEndAvg = 9.3f,
            totalScore = 111f, avgPerArrow = 9.2f,
            walkingSteps = 0,
            roundStartMs = System.currentTimeMillis() - 95_000L,
            sessionShotZones = listOf(
                ScoreZone.GOLD, ScoreZone.GOLD, ScoreZone.RED,
                ScoreZone.GOLD, ScoreZone.RED,  ScoreZone.GOLD,
                ScoreZone.GOLD, ScoreZone.GOLD, ScoreZone.GOLD,
                ScoreZone.RED,  ScoreZone.GOLD, ScoreZone.GOLD,
            ),
            onEnterScoring = {}, onEndSession = {},
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true, name = "End 7 – blue range")
@Composable
private fun ShootingPreviewBlue() {
    com.archery.wear.presentation.theme.ArcheryTheme {
        ShootingScreen(
            shotCount = 0, arrowsPerRound = 3, endNumber = 7,
            heartRate = 91f,
            lastEndAvg = 6.0f,
            totalScore = 126f, avgPerArrow = 5.8f,
            walkingSteps = 0,
            roundStartMs = System.currentTimeMillis() - 210_000L,
            sessionShotZones = listOf(
                ScoreZone.BLUE, ScoreZone.RED,  ScoreZone.BLUE,
                ScoreZone.GOLD, ScoreZone.BLUE, ScoreZone.RED,
                ScoreZone.BLUE, ScoreZone.MISS, ScoreZone.BLUE,
                ScoreZone.RED,  ScoreZone.BLUE, ScoreZone.BLUE,
                ScoreZone.BLUE, ScoreZone.GOLD, ScoreZone.RED,
                ScoreZone.BLUE, ScoreZone.BLUE, ScoreZone.MISS,
            ),
            onEnterScoring = {}, onEndSession = {},
        )
    }
}
