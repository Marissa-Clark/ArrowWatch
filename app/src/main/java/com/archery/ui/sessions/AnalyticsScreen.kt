package com.archery.ui.sessions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.archery.analytics.DetectedShot
import com.archery.analytics.DetectionProfile
import com.archery.analytics.HrPoint
import com.archery.analytics.RoundAnalytics
import com.archery.analytics.SensorSample
import com.archery.analytics.SessionAnalytics
import com.archery.shared.RoundSummary
import com.archery.shared.ScoreZone
import com.archery.ui.theme.*
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

private val ABgWhite       = Color.White
private val ARed500        = Color(0xFFEF4444)
private val AGzColor       = Color(0xFF8B5CF6)

// Semantic analytics colors — consistent across all charts
private val AScoreBlue     = Color(0xFF2563EB)   // score = blue
private val AScoreBlue100  = Color(0xFFDBEAFE)
private val AScoreBlue800  = Color(0xFF1E40AF)
private val AHrRed         = Color(0xFFDC2626)   // HR = red
private val AHrRed100      = Color(0xFFFEE2E2)
// hold time = gold — aliases for AppAmber* to make semantic intent clear in chart code
private val AHoldGold      = AppAmber600
private val AHoldGold100   = AppAmber100
private val AHoldGold800   = AppAmber800

@Composable
fun AnalyticsScreen(
    sessionId: Long,
    onBack: () -> Unit,
    vm: SessionDetailViewModel = viewModel(),
) {
    LaunchedEffect(sessionId) { vm.load(sessionId) }
    val session by vm.session.collectAsState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a")

    // Analytics are parsed once per unique filePath in the ViewModel and cached there,
    // so navigating away and back does not re-trigger the (potentially slow) parse.
    val analytics by vm.analytics.collectAsState()
    val isAnalyticsLoading by vm.isAnalyticsLoading.collectAsState()
    val dismissedState by vm.dismissedState.collectAsState()
    val manualShots by vm.manualShots.collectAsState()
    val activeProfile by vm.activeProfile.collectAsState()
    val suggestion by vm.suggestion.collectAsState()

    Column(
        Modifier.fillMaxSize().background(AppBgPage).verticalScroll(rememberScrollState())
    ) {
        // Dark gradient header
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(AppHeaderDark, AppHeaderDarker)))
                .padding(start = 20.dp, end = 20.dp, top = statusBarTop + 12.dp, bottom = 24.dp)
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Back", fontSize = 14.sp, color = AppTextSlate300,
                        modifier = Modifier.clickable { onBack() }.padding(vertical = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        // Lock toggle — "good session" flag
                        val locked = session?.analyticsLocked == true
                        Text(
                            if (locked) "🔒 Locked" else "🔓 Lock",
                            fontSize = 13.sp,
                            color = if (locked) AHoldGold else AppTextSlate300,
                            modifier = Modifier
                                .clickable { vm.lockAnalytics(!locked) }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                        )
                        // Refresh — hidden while loading or locked
                        if (!isAnalyticsLoading && !locked) {
                            Text("↺ Refresh", fontSize = 13.sp, color = AppCyan400,
                                modifier = Modifier
                                    .clickable { vm.refreshAnalytics() }
                                    .padding(vertical = 4.dp, horizontal = 4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Sensor Analytics", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(session?.date?.format(formatter) ?: "", fontSize = 14.sp, color = AppTextSlate300)
                Spacer(Modifier.height(4.dp))
                when {
                    analytics != null -> analytics?.let { a ->
                        Text("${a.allShots.size} detected shots · ${a.roundAnalytics.size} rounds",
                            fontSize = 12.sp, color = AppCyan400)
                    }
                    isAnalyticsLoading ->
                        Text("Analyzing sensor data…", fontSize = 12.sp,
                            color = AppTextMuted.copy(alpha = 0.7f))
                }
            }
        }

        val s = session
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Loading…", color = AppTextMuted)
            }
            return@Column
        }

        // ── Score per round bar chart (uses arrow data — no analytics required) ──
        val scoreBars: List<Triple<Int, Float, Float>> = s.rounds.mapNotNull { round ->
            val arrows = round.arrows.filter { it.zone != ScoreZone.DNS }
            if (arrows.isEmpty()) return@mapNotNull null
            val avg = arrows.map { it.score }.average().toFloat()
            val variance = if (arrows.size > 1)
                arrows.map { a -> (a.score - avg).let { d -> d * d } }.average().toFloat() else 0f
            Triple(round.number, avg, sqrt(variance.toDouble()).toFloat())
        }

        // ── Hold time per round bar chart (analytics shots if available, CSV fallback) ──
        val holdBars: List<Triple<Int, Float, Float>> = s.rounds.mapNotNull { round ->
            val ra = analytics?.roundAnalytics?.find { it.origCsvRound == round.number }
            val dismissed = dismissedState[round.number] ?: emptySet()
            val holdSecs: List<Float> = if (ra != null && ra.detectedShots.isNotEmpty()) {
                ra.detectedShots.filterIndexed { i, _ -> i !in dismissed }.map { it.holdSec }
            } else {
                val h = (round.avgHoldMs ?: 0L).takeIf { it > 0L }?.let { it / 1000f }
                    ?: return@mapNotNull null
                listOf(h)
            }
            if (holdSecs.isEmpty()) return@mapNotNull null
            val avg = holdSecs.average().toFloat()
            val variance = if (holdSecs.size > 1)
                holdSecs.map { d -> (d - avg).let { it * it } }.average().toFloat() else 0f
            Triple(round.number, avg, sqrt(variance.toDouble()).toFloat())
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {

            // Summary bar charts
            if (scoreBars.size >= 2) {
                RoundLineChart(
                    title = "Avg Score / Arrow",
                    subtitle = "per round  ±1σ shading",
                    bars = scoreBars,
                    lineColor = AScoreBlue,
                    yLabel = { "%.1f".format(it) },
                )
                Spacer(Modifier.height(12.dp))
            }
            if (holdBars.size >= 2) {
                RoundLineChart(
                    title = "Avg Hold Time",
                    subtitle = "seconds per round  ±1σ shading",
                    bars = holdBars,
                    lineColor = AHoldGold,
                    yLabel = { "%.1fs".format(it) },
                )
                Spacer(Modifier.height(12.dp))
            }

            if (analytics == null) {
                if (scoreBars.size < 2 && holdBars.size < 2) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isAnalyticsLoading) {
                                Text("Analyzing sensor data…", fontSize = 16.sp, color = AppTextSecondary)
                                Spacer(Modifier.height(8.dp))
                                Text("Large sessions may take a few seconds",
                                    fontSize = 13.sp, color = AppTextMuted,
                                    textAlign = TextAlign.Center)
                            } else {
                                Text("No sensor data available", fontSize = 16.sp, color = AppTextSecondary)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Sensor analytics require a CSV with sensor data recorded during the session.",
                                    fontSize = 13.sp, color = AppTextMuted,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                } else if (isAnalyticsLoading) {
                    // Charts from arrow data are showing, but sensor analysis still loading
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Analyzing sensor data…", fontSize = 13.sp, color = AppTextMuted)
                    }
                }
                Spacer(Modifier.height(16.dp))
                return@Column
            }

            // Capture analytics to a local val for smart-cast (delegated var can't be smart-cast)
            val a = analytics ?: return@Column

            // Hold time vs avg-per-arrow scatter (uses active/non-dismissed shots)
            val holdPoints: List<Triple<Float, Float, Int>> =
                a.roundAnalytics.mapNotNull { ra ->
                    val round = s.rounds.find { it.number == ra.origCsvRound }
                    val scored = round?.arrows?.count { it.zone != ScoreZone.DNS } ?: 0
                    val avgScore = if (scored > 0 && ra.score > 0f) ra.score / scored else 0f
                    val dismissed = dismissedState[ra.origCsvRound] ?: emptySet()
                    val activeShots = ra.detectedShots.filterIndexed { i, _ -> i !in dismissed }
                    if (activeShots.isNotEmpty() && avgScore > 0f)
                        Triple(activeShots.map { it.holdSec }.average().toFloat(), avgScore, ra.round)
                    else null
                }.ifEmpty {
                    s.rounds.mapNotNull { r ->
                        val hold = (r.avgHoldMs ?: 0L) / 1000f
                        val scored = r.arrows.count { it.zone != ScoreZone.DNS }
                        val avgScore = if (scored > 0) r.displayScore / scored else 0f
                        if (hold > 0f && avgScore > 0f) Triple(hold, avgScore, r.number)
                        else null
                    }
                }
            if (holdPoints.size >= 2) {
                HoldTimeVsScoreChart(holdPoints)
                Spacer(Modifier.height(12.dp))
            }

            // HR vs avg-per-arrow scatter
            val hrPoints: List<Triple<Float, Float, Int>> =
                a.roundAnalytics.mapNotNull { ra ->
                    val round = s.rounds.find { it.number == ra.origCsvRound }
                    val scored = round?.arrows?.count { it.zone != ScoreZone.DNS } ?: 0
                    val avgScore = if (scored > 0 && ra.score > 0f) ra.score / scored else 0f
                    if (ra.avgHr > 0f && avgScore > 0f) Triple(ra.avgHr, avgScore, ra.round)
                    else null
                }.ifEmpty {
                    s.rounds.mapNotNull { r ->
                        val hr = r.avgHeartRate ?: 0f
                        val scored = r.arrows.count { it.zone != ScoreZone.DNS }
                        val avgScore = if (scored > 0) r.displayScore / scored else 0f
                        if (hr > 0f && avgScore > 0f) Triple(hr, avgScore, r.number)
                        else null
                    }
                }
            if (hrPoints.size >= 2) {
                HrVsScoreChart(hrPoints)
                Spacer(Modifier.height(16.dp))
            }

            // Current detection thresholds summary
            val isLocked = s.analyticsLocked
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "gz_ddt [${activeProfile.gzDdtMin}, ${activeProfile.gzDdtMax}]  " +
                    "hold ≥ ${activeProfile.holdMinSec}s  gz_d ≥ ${activeProfile.gzMinDetrended}",
                    fontSize = 11.sp, color = AppTextMuted,
                    modifier = Modifier.weight(1f),
                )
                if (isLocked) {
                    Text("🔒 locked", fontSize = 11.sp, color = AHoldGold,
                        modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text("↺ Refresh", fontSize = 12.sp, color = AppCyan600,
                        modifier = Modifier
                            .clickable { vm.refreshAnalytics() }
                            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            // Per-round analytics — iterate analytics rounds as the source of truth.
            // Falls back to an empty RoundSummary shell if the DB round is missing
            // (e.g. session stored before arrows were parsed correctly).
            if (a.roundAnalytics.isNotEmpty()) {
                Text("Per Round", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    color = AppHeaderDark)
                Spacer(Modifier.height(4.dp))
                Text("Tap a round to expand sensor charts", fontSize = 12.sp, color = AppTextMuted)
                Spacer(Modifier.height(12.dp))
                a.roundAnalytics.forEach { ra ->
                    // Try to find the matching DB round; fall back to an empty shell so
                    // the card still renders even when the DB session has 0 rounds.
                    val round = s.rounds.find { it.number == ra.origCsvRound }
                        ?: RoundSummary(
                            number = ra.origCsvRound,
                            arrows = emptyList(),
                            detectedScore = ra.score,
                            confirmedScore = null,
                            avgHeartRate = ra.avgHr.takeIf { it > 0f },
                            avgHoldMs = null,
                        )
                    val dismissed = dismissedState[ra.origCsvRound] ?: emptySet()
                    val manual = manualShots[ra.origCsvRound] ?: emptyList()
                    RoundAnalyticsCard(
                        round = round,
                        ra = ra,
                        dismissedShotIndices = dismissed,
                        manualShotTimes = manual,
                        profile = activeProfile,
                        onDismissShot = { i -> vm.dismissShot(ra.origCsvRound, i, s.filePath) },
                        onRestoreShot = { i -> vm.restoreShot(ra.origCsvRound, i, s.filePath) },
                        onFlagMissedAt = { timeSec -> vm.flagMissedShot(ra.origCsvRound, timeSec, s.filePath) },
                        onUnflagManual = { timeSec -> vm.unflagManualShot(ra.origCsvRound, timeSec, s.filePath) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ═══════════════ ROUND LINE CHART (line + ±1σ shading) ═══════════════

@Composable
private fun RoundLineChart(
    title: String,
    subtitle: String,
    bars: List<Triple<Int, Float, Float>>,   // (roundNumber, avg, stdev)
    lineColor: Color,
    yLabel: (Float) -> String,
) {
    val yMax = (bars.maxOf { it.second + it.third } * 1.2f).coerceAtLeast(1f)

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ABgWhite),
        shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(lineColor))
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppHeaderDark)
            }
            Text(subtitle, fontSize = 11.sp, color = AppTextMuted)
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                val w = size.width; val h = size.height
                val padL = 36f; val padT = 10f; val padB = 22f; val padR = 8f
                val chartW = w - padL - padR; val chartH = h - padT - padB
                val n = bars.size

                fun valToY(v: Float) = padT + chartH * (1f - (v / yMax).coerceIn(0f, 1f))

                val labelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(0x94, 0xA3, 0xB8)
                    textSize = 16f; isAntiAlias = true
                }
                val xLabelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(0x94, 0xA3, 0xB8)
                    textSize = 15f; isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                // Grid lines + y labels
                for (i in 0..3) {
                    val v = yMax * i / 3f
                    val y = valToY(v)
                    drawLine(AppBorderLight, Offset(padL, y), Offset(padL + chartW, y), 0.5f)
                    drawContext.canvas.nativeCanvas.drawText(yLabel(v), 0f, y + 5f, labelPaint)
                }

                // X positions
                val xs = bars.mapIndexed { idx, _ ->
                    if (n > 1) padL + chartW * idx / (n - 1f) else padL + chartW / 2f
                }

                // ±1σ shaded band (upper edge forward, lower edge backward → closed polygon)
                if (n >= 2) {
                    val band = Path().apply {
                        // upper edge (mean + stdev), left → right
                        bars.forEachIndexed { idx, (_, avg, stdev) ->
                            val y = valToY((avg + stdev).coerceAtMost(yMax))
                            if (idx == 0) moveTo(xs[idx], y) else lineTo(xs[idx], y)
                        }
                        // lower edge (mean - stdev), right → left
                        bars.indices.reversed().forEach { idx ->
                            val (_, avg, stdev) = bars[idx]
                            lineTo(xs[idx], valToY((avg - stdev).coerceAtLeast(0f)))
                        }
                        close()
                    }
                    drawPath(band, lineColor.copy(alpha = 0.15f))
                }

                // Mean line
                val linePath = Path().apply {
                    bars.forEachIndexed { idx, (_, avg, _) ->
                        val y = valToY(avg)
                        if (idx == 0) moveTo(xs[idx], y) else lineTo(xs[idx], y)
                    }
                }
                drawPath(linePath, lineColor, style = Stroke(2.5f))

                // Dots + x labels
                bars.forEachIndexed { idx, (roundNum, avg, _) ->
                    val xc = xs[idx]; val yc = valToY(avg)
                    drawCircle(lineColor, 4.5f, Offset(xc, yc))
                    drawCircle(ABgWhite, 2.5f, Offset(xc, yc))
                    drawContext.canvas.nativeCanvas.drawText("R$roundNum", xc, h - 2f, xLabelPaint)
                }
            }
        }
    }
}

@Composable
private fun HoldTimeVsScoreChart(points: List<Triple<Float, Float, Int>>) {
    val holdMin = (points.minOf { it.first } - 0.5f).coerceAtLeast(0f)
    val holdMax = points.maxOf { it.first } + 0.5f
    val holdRange = (holdMax - holdMin).coerceAtLeast(1f)
    val scoreMin = (points.minOf { it.second } - 2f).coerceAtLeast(0f)
    val scoreMax = points.maxOf { it.second } + 2f
    val scoreRange = (scoreMax - scoreMin).coerceAtLeast(1f)
    val totalRounds = points.size

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ABgWhite),
        shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("Hold Time vs Avg/Arrow", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = AppHeaderDark)
            Text("Each dot = 1 round (darker = earlier)", fontSize = 11.sp, color = AppTextMuted)
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val w = size.width; val h = size.height
                val padLeft = 34f; val padTop = 8f; val padBottom = 24f; val padRight = 12f
                val chartW = w - padLeft - padRight; val chartH = h - padTop - padBottom
                fun holdToX(v: Float) = padLeft + ((v - holdMin) / holdRange) * chartW
                fun scoreToY(v: Float) = padTop + chartH * (1f - (v - scoreMin) / scoreRange)
                for (i in 0..4) {
                    val v = scoreMin + scoreRange * i / 4f
                    drawLine(AppBorderLight, Offset(padLeft, scoreToY(v)), Offset(padLeft + chartW, scoreToY(v)), 0.5f)
                }
                for (i in 0..4) {
                    val v = holdMin + holdRange * i / 4f
                    drawLine(AppBorderLight, Offset(holdToX(v), padTop), Offset(holdToX(v), padTop + chartH), 0.5f)
                }
                points.forEach { (hold, score, round) ->
                    val alpha = 1f - (round - 1).toFloat() / totalRounds.coerceAtLeast(2) * 0.75f
                    val x = holdToX(hold); val y = scoreToY(score)
                    drawCircle(AScoreBlue.copy(alpha = alpha * 0.25f), 12f, Offset(x, y))
                    drawCircle(AScoreBlue.copy(alpha = alpha), 6f, Offset(x, y))
                }
                val yPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(0x94, 0xA3, 0xB8); textSize = 18f; isAntiAlias = true
                }
                for (i in 0..4) {
                    val v = scoreMin + scoreRange * i / 4f
                    drawContext.canvas.nativeCanvas.drawText("%.0f".format(v), 2f, scoreToY(v) + 5f, yPaint)
                }
                val xPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(0x94, 0xA3, 0xB8); textSize = 16f; isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                for (i in 0..4) {
                    val v = holdMin + holdRange * i / 4f
                    drawContext.canvas.nativeCanvas.drawText("%.1fs".format(v), holdToX(v), h - 2f, xPaint)
                }
            }
        }
    }
}

@Composable
private fun HrVsScoreChart(points: List<Triple<Float, Float, Int>>) {
    val hrMin = (points.minOf { it.first } - 5f).coerceAtLeast(40f)
    val hrMax = points.maxOf { it.first } + 5f
    val hrRange = (hrMax - hrMin).coerceAtLeast(1f)
    val scoreMin = (points.minOf { it.second } - 2f).coerceAtLeast(0f)
    val scoreMax = points.maxOf { it.second } + 2f
    val scoreRange = (scoreMax - scoreMin).coerceAtLeast(1f)
    val totalRounds = points.size

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ABgWhite),
        shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("Heart Rate vs Avg/Arrow", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = AppHeaderDark)
            Text("Each dot = 1 round (darker = earlier)", fontSize = 11.sp, color = AppTextMuted)
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val w = size.width; val h = size.height
                val padLeft = 34f; val padTop = 8f; val padBottom = 24f; val padRight = 12f
                val chartW = w - padLeft - padRight; val chartH = h - padTop - padBottom
                fun hrToX(v: Float) = padLeft + ((v - hrMin) / hrRange) * chartW
                fun scoreToY(v: Float) = padTop + chartH * (1f - (v - scoreMin) / scoreRange)
                for (i in 0..4) {
                    val v = scoreMin + scoreRange * i / 4f
                    drawLine(AppBorderLight, Offset(padLeft, scoreToY(v)), Offset(padLeft + chartW, scoreToY(v)), 0.5f)
                }
                for (i in 0..4) {
                    val v = hrMin + hrRange * i / 4f
                    drawLine(AppBorderLight, Offset(hrToX(v), padTop), Offset(hrToX(v), padTop + chartH), 0.5f)
                }
                points.forEach { (hr, score, round) ->
                    val alpha = 1f - (round - 1).toFloat() / totalRounds.coerceAtLeast(2) * 0.75f
                    val x = hrToX(hr); val y = scoreToY(score)
                    drawCircle(AHrRed.copy(alpha = alpha * 0.25f), 12f, Offset(x, y))
                    drawCircle(AHrRed.copy(alpha = alpha), 6f, Offset(x, y))
                }
                val yPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(0x94, 0xA3, 0xB8); textSize = 18f; isAntiAlias = true
                }
                for (i in 0..4) {
                    val v = scoreMin + scoreRange * i / 4f
                    drawContext.canvas.nativeCanvas.drawText("%.0f".format(v), 2f, scoreToY(v) + 5f, yPaint)
                }
                val xPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(0xDC, 0x26, 0x26); textSize = 16f; isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                for (i in 0..4) {
                    val v = hrMin + hrRange * i / 4f
                    drawContext.canvas.nativeCanvas.drawText("%.0f".format(v), hrToX(v), h - 2f, xPaint)
                }
            }
        }
    }
}

@Composable
private fun RoundAnalyticsCard(
    round: RoundSummary,
    ra: RoundAnalytics,
    dismissedShotIndices: Set<Int>,
    manualShotTimes: List<Float>,
    profile: DetectionProfile,
    onDismissShot: (Int) -> Unit,
    onRestoreShot: (Int) -> Unit,
    onFlagMissedAt: (Float) -> Unit,
    onUnflagManual: (Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val activeShotCount = ra.detectedShots.indices.count { it !in dismissedShotIndices }
    val roundDurationSec = (ra.endSec - ra.startSec).coerceAtLeast(1f)

    // Compute avg score per non-DNS arrow
    val scored = round.arrows.count { it.zone != ScoreZone.DNS }
    val avgScore = if (scored > 0) round.displayScore / scored else 0f
    // Use active (non-dismissed) detected shots as primary source for hold time
    val activeShots = ra.detectedShots.filterIndexed { i, _ -> i !in dismissedShotIndices }
    val avgHoldSec: Float? = if (activeShots.isNotEmpty()) {
        activeShots.map { it.holdSec }.average().toFloat()
    } else {
        (round.avgHoldMs ?: 0L).takeIf { it > 0L }?.let { it / 1000f }
    }

    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = ABgWhite),
        shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(12.dp)) {
            // Header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (expanded) "▼" else "▶", fontSize = 12.sp, color = AppTextMuted)
                    Text("Round ${round.number}", fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, color = AppHeaderDark)
                    // Total score badge (blue = score)
                    Box(Modifier.background(AScoreBlue100, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("%.0f".format(round.displayScore), fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, color = AScoreBlue800)
                    }
                    // Avg/arrow badge (same blue family)
                    if (avgScore > 0f) {
                        Box(Modifier.background(AScoreBlue, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("%.1f avg".format(avgScore), fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (activeShotCount > 0) Text("$activeShotCount shots", fontSize = 11.sp, color = AppTextMuted)
                    // near-miss count badge disabled for now
                    // if (ra.nearMisses.isNotEmpty()) Text("${ra.nearMisses.size} near-miss", ...)
                    if (ra.avgHr > 0f) Text("%.0f bpm".format(ra.avgHr), fontSize = 11.sp, color = AHrRed)
                    if (avgHoldSec != null) Text("%.1fs hold".format(avgHoldSec), fontSize = 11.sp, color = AHoldGold)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))

                    // HR chart WITH shot markers
                    if (ra.hrSamples.size >= 2) {
                        Text("Heart Rate", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppTextSecondary)
                        Spacer(Modifier.height(4.dp))
                        HrChart(
                            hrSamples = ra.hrSamples,
                            durationSec = roundDurationSec,
                            detectedShots = activeShots,
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            startSec = ra.startSec,
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    // Gravity Z chart — walking intervals excluded from data
                    val nonWalkingData = remember(ra) {
                        ra.sensorData.filter { sample ->
                            ra.walkingIntervals.none { (ws, we) -> sample.time in ws..we }
                        }
                    }
                    if (nonWalkingData.size >= 2) {
                        Text("Gravity Z (arm position)", fontSize = 12.sp,
                            fontWeight = FontWeight.Medium, color = AppTextSecondary)
                        Spacer(Modifier.height(4.dp))
                        GravityZChart(
                            sensorData = nonWalkingData,
                            durationSec = roundDurationSec,
                            detectedShots = activeShots,
                            manualShotTimes = manualShotTimes,
                            modifier = Modifier.fillMaxWidth().height(110.dp),
                            startSec = ra.startSec,
                            onFlagMissedAt = onFlagMissedAt,
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    // Shot hold chips
                    if (ra.detectedShots.isNotEmpty()) {
                        Text("Detected Shots", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = AppTextSecondary)
                        Spacer(Modifier.height(6.dp))
                        ShotHoldChips(
                            shots = ra.detectedShots,
                            profile = profile,
                            dismissedIndices = dismissedShotIndices,
                            onDismiss = onDismissShot,
                            onRestore = onRestoreShot,
                            onUnflagManual = onUnflagManual,
                        )
                    }

                }
            }
        }
    }
}

@Composable
private fun HrChart(
    hrSamples: List<HrPoint>,
    durationSec: Float,
    detectedShots: List<DetectedShot> = emptyList(),
    modifier: Modifier = Modifier,
    startSec: Float = 0f,
) {
    if (hrSamples.isEmpty()) return
    val minHr = (hrSamples.minOf { it.bpm } - 5f).coerceAtLeast(40f)
    val maxHr = hrSamples.maxOf { it.bpm } + 5f
    val hrRange = (maxHr - minHr).coerceAtLeast(1f)
    val timeRange = durationSec.coerceAtLeast(1f)

    Canvas(modifier) {
        val w = size.width; val h = size.height
        val padL = 34f; val padT = 6f; val padB = 18f; val padR = 8f
        val chartW = w - padL - padR; val chartH = h - padT - padB

        fun tToX(t: Float) = padL + ((t - startSec) / timeRange) * chartW
        fun hrToY(v: Float) = padT + chartH * (1f - (v - minHr) / hrRange)

        for (i in 0..3) {
            val v = minHr + hrRange * i / 3f
            drawLine(AppBorderLight, Offset(padL, hrToY(v)), Offset(padL + chartW, hrToY(v)), 0.5f)
        }

        val path = Path()
        hrSamples.forEachIndexed { i, pt ->
            val x = tToX(pt.time); val y = hrToY(pt.bpm)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, AHrRed, style = Stroke(width = 2f))
        hrSamples.forEach { pt -> drawCircle(AHrRed, 3.5f, Offset(tToX(pt.time), hrToY(pt.bpm))) }

        // Shot markers (amber vertical lines) - drawn on top of HR line
        detectedShots.forEach { shot ->
            val x = tToX(shot.time)
            drawLine(AppAmber600.copy(alpha = 0.8f), Offset(x, padT), Offset(x, padT + chartH), 1.5f)
        }

        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(0x94, 0xA3, 0xB8); textSize = 16f; isAntiAlias = true
        }
        for (i in 0..3) {
            val v = minHr + hrRange * i / 3f
            drawContext.canvas.nativeCanvas.drawText("%.0f".format(v), 0f, hrToY(v) + 5f, paint)
        }
    }
}

@Composable
private fun GravityZChart(
    sensorData: List<SensorSample>,
    durationSec: Float,
    detectedShots: List<DetectedShot>,
    manualShotTimes: List<Float> = emptyList(),
    modifier: Modifier = Modifier,
    startSec: Float = 0f,
    onFlagMissedAt: ((timeSec: Float) -> Unit)? = null,
) {
    if (sensorData.isEmpty()) return
    val gzMin = (sensorData.minOf { it.gz } - 0.5f).coerceAtLeast(-10f)
    val gzMax = (sensorData.maxOf { it.gz } + 0.5f).coerceAtMost(12f)
    val gzRange = (gzMax - gzMin).coerceAtLeast(1f)
    val timeRange = durationSec.coerceAtLeast(1f)

    // Crosshair state: fraction 0..1 along the time axis; null = nothing pinned
    var pinnedFrac by remember { mutableStateOf<Float?>(null) }
    val padL = 34f; val padR = 8f; val padT = 6f; val padB = 18f

    Column {
        Canvas(
            modifier.then(
                if (onFlagMissedAt != null) Modifier.pointerInput(sensorData) {
                    detectTapGestures { tap ->
                        val chartW = size.width - padL - padR
                        val frac = ((tap.x - padL) / chartW).coerceIn(0f, 1f)
                        // Second tap near same spot → clear pin
                        pinnedFrac = if (pinnedFrac != null &&
                            kotlin.math.abs(frac - pinnedFrac!!) < 0.04f) null else frac
                    }
                } else Modifier
            )
        ) {
            val w = size.width; val h = size.height
            val chartW = w - padL - padR; val chartH = h - padT - padB

            fun tToX(t: Float) = padL + ((t - startSec) / timeRange) * chartW
            fun gzToY(v: Float) = padT + chartH * (1f - (v - gzMin) / gzRange)

            // Shoot zone band (gz 5–7.5, purple tint)
            val shootTop = gzToY(7.5f)
            val shootBot = gzToY(5.0f)
            if (shootBot > shootTop)
                drawRect(AGzColor.copy(alpha = 0.08f), Offset(padL, shootTop),
                    Size(chartW, shootBot - shootTop))

            // Grid lines
            for (i in 0..3) {
                val v = gzMin + gzRange * i / 3f
                drawLine(AppBorderLight, Offset(padL, gzToY(v)), Offset(padL + chartW, gzToY(v)), 0.5f)
            }

            // GZ line
            val path = Path()
            sensorData.forEachIndexed { i, s ->
                val x = tToX(s.time); val y = gzToY(s.gz.coerceIn(gzMin, gzMax))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, AGzColor, style = Stroke(width = 1.5f))

            // Detected shot markers (amber)
            detectedShots.forEach { shot ->
                val x = tToX(shot.time)
                drawLine(AppAmber600.copy(alpha = 0.8f), Offset(x, padT), Offset(x, padT + chartH), 1.5f)
            }

            // Manual shot markers (cyan dashed)
            manualShotTimes.forEach { t ->
                val x = tToX(t)
                for (seg in 0..4) {
                    val y0 = padT + chartH * seg / 5f
                    val y1 = padT + chartH * (seg + 0.6f) / 5f
                    drawLine(AppCyan600.copy(alpha = 0.7f), Offset(x, y0), Offset(x, y1), 1.5f)
                }
            }

            // Pinned crosshair
            pinnedFrac?.let { frac ->
                val xPin = padL + frac * chartW
                val timeSec = startSec + frac * timeRange
                val nearest = sensorData.minByOrNull { kotlin.math.abs(it.time - timeSec) }
                val gz = nearest?.gz ?: 0f
                val yPin = gzToY(gz.coerceIn(gzMin, gzMax))

                drawLine(AppCyan600, Offset(xPin, padT), Offset(xPin, padT + chartH), 1.5f)
                drawCircle(AppCyan600, 4.5f, Offset(xPin, yPin))
                drawCircle(ABgWhite, 2.5f, Offset(xPin, yPin))

                val pinPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(0x08, 0x91, 0xB2)
                    textSize = 17f; isAntiAlias = true
                    textAlign = if (frac > 0.6f)
                        android.graphics.Paint.Align.RIGHT
                    else android.graphics.Paint.Align.LEFT
                }
                val labelX = if (frac > 0.6f) xPin - 5f else xPin + 5f
                drawContext.canvas.nativeCanvas.drawText(
                    "gz %.1f  t %.1fs".format(gz, timeSec - startSec),
                    labelX, padT + 14f, pinPaint,
                )
            }

            // Y labels
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(0x94, 0xA3, 0xB8); textSize = 16f; isAntiAlias = true
            }
            for (i in 0..3) {
                val v = gzMin + gzRange * i / 3f
                drawContext.canvas.nativeCanvas.drawText("%.0f".format(v), 0f, gzToY(v) + 5f, paint)
            }
        }

        // Action row — only visible when crosshair is pinned
        if (onFlagMissedAt != null && pinnedFrac != null) {
            val frac    = pinnedFrac!!
            val timeSec = startSec + frac * timeRange
            val nudge   = 0.25f / timeRange   // 0.25 s per tap
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ◀ nudge left
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppSlate100)
                        .clickable { pinnedFrac = (frac - nudge).coerceIn(0f, 1f) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) { Text("◀", fontSize = 13.sp, color = AppTextSecondary) }

                // Time + gz readout
                Text(
                    "t %.1fs".format(timeSec - startSec),
                    fontSize = 11.sp, color = AppCyan600,
                    modifier = Modifier.weight(1f),
                )

                // ▶ nudge right
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppSlate100)
                        .clickable { pinnedFrac = (frac + nudge).coerceIn(0f, 1f) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) { Text("▶", fontSize = 13.sp, color = AppTextSecondary) }

                Spacer(Modifier.width(4.dp))

                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppCyan600.copy(alpha = 0.1f))
                        .border(1.dp, AppCyan600.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                        .clickable { onFlagMissedAt(timeSec); pinnedFrac = null }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("⚑ Flag", fontSize = 11.sp, color = AppCyan600,
                        fontWeight = FontWeight.Medium)
                }
                Text("✕", fontSize = 13.sp, color = AppTextMuted,
                    modifier = Modifier.clickable { pinnedFrac = null }.padding(4.dp))
            }
        }
    }
}

@Composable
private fun ShotHoldChips(
    shots: List<DetectedShot>,
    profile: DetectionProfile,
    dismissedIndices: Set<Int>,
    onDismiss: (Int) -> Unit,
    onRestore: (Int) -> Unit,
    onUnflagManual: (Float) -> Unit = {},
) {
    var selectedShotIdx by remember { mutableIntStateOf(-1) }

    // ── Chip row ──
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        shots.forEachIndexed { idx, shot ->
            val dismissed  = idx in dismissedIndices
            val isManual   = shot.isManual
            val isSelected = !dismissed && selectedShotIdx == idx
            val bgColor = when {
                dismissed  -> AppBorderLight
                isSelected -> AHoldGold.copy(alpha = 0.15f)
                else       -> AHoldGold100
            }
            val textColor = when {
                dismissed -> AppTextMuted
                else      -> AHoldGold800
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .then(
                        if (isSelected) Modifier.border(1.dp, AHoldGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        else Modifier
                    )
                    .clickable {
                        when {
                            dismissed -> onRestore(idx)
                            else      -> selectedShotIdx = if (selectedShotIdx == idx) -1 else idx
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%.1fs".format(shot.holdSec),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor,
                    )
                    when {
                        dismissed -> Text("↩ restore", fontSize = 10.sp, color = AppTextMuted)
                        else -> {
                            // ⚑ marker for manual shots; regular shot number otherwise
                            Text(
                                if (isManual) "Shot ${idx + 1} ⚑" else "Shot ${idx + 1}",
                                fontSize = 10.sp, color = AHoldGold,
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Detail panel for selected shot (manual or auto-detected) ──
    val selShot = shots.getOrNull(selectedShotIdx)
    if (selShot != null && selectedShotIdx !in dismissedIndices) {
        Spacer(Modifier.height(8.dp))
        ShotDetailPanel(
            shot      = selShot,
            shotIndex = selectedShotIdx,
            profile   = profile,
            onDismiss = { onDismiss(selectedShotIdx); selectedShotIdx = -1 },
            onClose   = { selectedShotIdx = -1 },
            onUnflag  = if (selShot.isManual) {
                { onUnflagManual(selShot.time); selectedShotIdx = -1 }
            } else null,
        )
    }

    Spacer(Modifier.height(4.dp))
    Text(
        "Tap chip to inspect · dismissed shots excluded from stats · ⚑ = manually added",
        fontSize = 10.sp, color = AppTextMuted,
    )
}

// ── Per-shot detail panel ──────────────────────────────────────────────────

@Composable
private fun ShotDetailPanel(
    shot: DetectedShot,
    shotIndex: Int,
    profile: DetectionProfile,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
    /** Non-null only for manually added shots — removes the shot from the dataset entirely. */
    onUnflag: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AHoldGold100.copy(alpha = 0.6f))
            .border(1.dp, AHoldGold.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(
                if (shot.isManual) "Shot ${shotIndex + 1} ⚑ (manual)" else "Shot ${shotIndex + 1} detail",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AHoldGold800,
            )
            Text("✕", fontSize = 14.sp, color = AppTextMuted,
                modifier = Modifier.clickable { onClose() }.padding(4.dp))
        }
        Spacer(Modifier.height(8.dp))

        // Stat pills
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShotStatPill(Modifier.weight(1f), "Hold", "%.1fs".format(shot.holdSec), AHoldGold800, AHoldGold100)
            ShotStatPill(Modifier.weight(1f), "HR",
                if (shot.hrAtShot != null) "%.0f bpm".format(shot.hrAtShot) else "—",
                AppHrPink, AppHrPink.copy(alpha = 0.08f))
        }

        // Threshold check — shows actual vs required for each criterion
        Spacer(Modifier.height(10.dp))
        Text("Threshold check", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = AppTextSecondary)
        Spacer(Modifier.height(4.dp))
        val gzDMean = shot.gzDWindow.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        // gz_ddt: show % of samples in band rather than min/max — the window may span merge gaps
        // (brief out-of-band dips between two qualifying segments that were merged together).
        // A detected shot always passed the band filter on the qualifying segments by definition.
        val gzDdtInBandPct = shot.gzDdtWindow.takeIf { it.isNotEmpty() }?.let { w ->
            w.count { it >= profile.gzDdtMin && it <= profile.gzDdtMax } * 100 / w.size
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            ThresholdRow("Hold",
                "%.1fs".format(shot.holdSec),
                "min %.1fs".format(profile.holdMinSec),
                shot.holdSec >= profile.holdMinSec,
                "%.1fs margin".format(shot.holdSec - profile.holdMinSec))
            if (gzDMean != null)
                ThresholdRow("gz_d (mean)",
                    "%.2f".format(gzDMean),
                    "min %.2f".format(profile.gzMinDetrended),
                    gzDMean >= profile.gzMinDetrended,
                    "%.2f margin".format(gzDMean - profile.gzMinDetrended))
            if (gzDdtInBandPct != null)
                // Always ✓ for detected shots — qualifying segments were in-band by definition.
                // % < 100 means the window spans a merge gap (brief out-of-band dip between segments).
                ThresholdRow("gz_ddt",
                    "$gzDdtInBandPct% in band",
                    "band [%.1f, %.1f]".format(profile.gzDdtMin, profile.gzDdtMax),
                    true,
                    if (gzDdtInBandPct < 100) "merged gap" else null)
            // Angle thresholds (only when configured)
            if (profile.yawMin != null || profile.yawMax != null) {
                val yawVals = shot.sensorWindow.map { it.yaw }
                val yawMin = yawVals.minOrNull(); val yawMax = yawVals.maxOrNull()
                if (yawMin != null)
                    ThresholdRow("yaw range",
                        "[%.1f, %.1f]".format(yawMin, yawMax!!),
                        "bounds [${profile.yawMin?.let { "%.1f".format(it) } ?: "–"}, ${profile.yawMax?.let { "%.1f".format(it) } ?: "–"}]",
                        (profile.yawMin == null || yawMin >= profile.yawMin) && (profile.yawMax == null || yawMax!! <= profile.yawMax),
                        null)
            }
            if (profile.pitchMin != null || profile.pitchMax != null) {
                val pVals = shot.sensorWindow.map { it.pitch }
                val pMin = pVals.minOrNull(); val pMax = pVals.maxOrNull()
                if (pMin != null)
                    ThresholdRow("pitch range",
                        "[%.1f, %.1f]".format(pMin, pMax!!),
                        "bounds [${profile.pitchMin?.let { "%.1f".format(it) } ?: "–"}, ${profile.pitchMax?.let { "%.1f".format(it) } ?: "–"}]",
                        (profile.pitchMin == null || pMin >= profile.pitchMin) && (profile.pitchMax == null || pMax!! <= profile.pitchMax),
                        null)
            }
            if (profile.rollMin != null || profile.rollMax != null) {
                val rVals = shot.sensorWindow.map { it.roll }
                val rMin = rVals.minOrNull(); val rMax = rVals.maxOrNull()
                if (rMin != null)
                    ThresholdRow("roll range",
                        "[%.1f, %.1f]".format(rMin, rMax!!),
                        "bounds [${profile.rollMin?.let { "%.1f".format(it) } ?: "–"}, ${profile.rollMax?.let { "%.1f".format(it) } ?: "–"}]",
                        (profile.rollMin == null || rMin >= profile.rollMin) && (profile.rollMax == null || rMax!! <= profile.rollMax),
                        null)
            }
        }

        // Gz mini-chart for hold window
        if (shot.sensorWindow.size >= 2) {
            Spacer(Modifier.height(10.dp))
            Text("Gravity Z during hold", fontSize = 11.sp,
                fontWeight = FontWeight.Medium, color = AppTextSecondary)
            Spacer(Modifier.height(4.dp))
            ShotGzMiniChart(
                sensorWindow = shot.sensorWindow,
                shot         = shot,
                modifier     = Modifier.fillMaxWidth().height(72.dp),
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (onUnflag != null) {
                // Manual shot: offer full removal in addition to the standard dismiss
                Text(
                    "Remove manual",
                    fontSize = 11.sp, color = AppTextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onUnflag() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Text(
                if (onUnflag != null) "Dismiss (exclude from stats)" else "Dismiss (false positive)",
                fontSize = 11.sp, color = ARed500,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

// ── Threshold check row ────────────────────────────────────────────────────

@Composable
private fun ThresholdRow(label: String, actual: String, required: String, passed: Boolean, margin: String?) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (passed) Color(0xFFECFDF5) else Color(0xFFFFF1F2))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (passed) "✓" else "✗", fontSize = 11.sp,
            color = if (passed) Color(0xFF16A34A) else ARed500)
        Text(label, fontSize = 11.sp, color = AppTextSecondary, modifier = Modifier.width(70.dp))
        Text(actual, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = if (passed) Color(0xFF15803D) else ARed500, modifier = Modifier.weight(1f))
        Text(required, fontSize = 10.sp, color = AppTextMuted)
        if (margin != null) {
            // Prefix "+" only for numeric margins (e.g. "1.2s margin"); not for labels like "merged gap"
            val display = if (margin.first().isDigit()) "+$margin" else margin
            Text(display, fontSize = 10.sp, color = Color(0xFF16A34A))
        }
    }
}

@Composable
private fun ShotStatPill(
    modifier: Modifier,
    label: String,
    value: String,
    textColor: Color,
    bgColor: Color,
) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(bgColor).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 9.sp, color = AppTextMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = textColor, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ShotGzMiniChart(
    sensorWindow: List<SensorSample>,
    shot: DetectedShot,
    modifier: Modifier = Modifier,
) {
    if (sensorWindow.isEmpty()) return
    val gzMin = (sensorWindow.minOf { it.gz } - 0.3f).coerceAtLeast(-2f)
    val gzMax = (sensorWindow.maxOf { it.gz } + 0.3f).coerceAtMost(11f)
    val gzRange = (gzMax - gzMin).coerceAtLeast(0.5f)
    val tMin = sensorWindow.first().time
    val tMax = sensorWindow.last().time
    val tRange = (tMax - tMin).coerceAtLeast(0.1f)

    Canvas(modifier) {
        val w = size.width; val h = size.height
        val padL = 28f; val padT = 4f; val padB = 14f; val padR = 4f
        val chartW = w - padL - padR; val chartH = h - padT - padB

        fun tToX(t: Float) = padL + ((t - tMin) / tRange) * chartW
        fun gzToY(v: Float) = padT + chartH * (1f - (v - gzMin) / gzRange)

        // Shoot zone band (5.0–7.5) in light purple
        val bandTop = gzToY(7.5f.coerceAtMost(gzMax))
        val bandBot = gzToY(5.0f.coerceAtLeast(gzMin))
        if (bandBot > bandTop) {
            drawRect(AGzColor.copy(alpha = 0.12f), Offset(padL, bandTop),
                Size(chartW, bandBot - bandTop))
        }

        // Grid lines
        for (i in 0..3) {
            val v = gzMin + gzRange * i / 3f
            drawLine(AppBorderLight, Offset(padL, gzToY(v)), Offset(padL + chartW, gzToY(v)), 0.5f)
        }

        // GZ line
        val path = Path()
        sensorWindow.forEachIndexed { i, s ->
            val x = tToX(s.time)
            val y = gzToY(s.gz.coerceIn(gzMin, gzMax))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, AGzColor, style = Stroke(width = 2f))

        // Mean line (dashed visual — draw as subtle solid)
        val meanY = gzToY(shot.gzMean.coerceIn(gzMin, gzMax))
        drawLine(AHoldGold.copy(alpha = 0.5f), Offset(padL, meanY), Offset(padL + chartW, meanY), 1f)

        // Y labels
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(0x94, 0xA3, 0xB8)
            textSize = 15f; isAntiAlias = true
        }
        for (i in 0..3) {
            val v = gzMin + gzRange * i / 3f
            drawContext.canvas.nativeCanvas.drawText("%.0f".format(v), 0f, gzToY(v) + 4f, paint)
        }
        // X axis: show hold duration
        val xPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(0xB4, 0x53, 0x09)
            textSize = 14f; isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.drawText(
            "%.1fs hold".format(shot.holdSec),
            padL + chartW / 2f, h - 1f, xPaint,
        )
    }
}

// ═══════════════ DISMISSAL TUNE CARD ═══════════════


