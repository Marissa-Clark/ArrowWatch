package com.archery.ui.sessions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.archery.analytics.DetectedShot
import com.archery.analytics.HrPoint
import com.archery.analytics.RoundAnalytics
import com.archery.analytics.SensorSample
import com.archery.analytics.SessionAnalytics
import com.archery.shared.RoundSummary
import com.archery.shared.ScoreZone
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

private val ABgPage        = Color(0xFFF8FAFC)
private val ABgWhite       = Color.White
private val AHeaderDark    = Color(0xFF1E293B)
private val AHeaderDarker  = Color(0xFF0F172A)
private val ATextPrimary   = Color(0xFF1E293B)
private val ATextSecondary = Color(0xFF475569)
private val ATextMuted     = Color(0xFF94A3B8)
private val ATextSlate300  = Color(0xFFCBD5E1)
private val ACyan400       = Color(0xFF22D3EE)
private val ACyan600       = Color(0xFF0891B2)
private val ACyan100       = Color(0xFFCFFAFE)
private val ACyan800       = Color(0xFF155E75)
private val AAmber600      = Color(0xFFD97706)
private val AAmber100      = Color(0xFFFEF3C7)
private val AAmber800      = Color(0xFF92400E)
private val ARed500        = Color(0xFFEF4444)
private val ABorderLight   = Color(0xFFE2E8F0)
private val AHrPink        = Color(0xFFEC4899)
private val AGzColor       = Color(0xFF8B5CF6)
private val ASlate100      = Color(0xFFF1F5F9)
private val AAmber700      = Color(0xFFB45309)

// Semantic analytics colors — consistent across all charts
private val AScoreBlue     = Color(0xFF2563EB)   // score = blue
private val AScoreBlue100  = Color(0xFFDBEAFE)
private val AScoreBlue800  = Color(0xFF1E40AF)
private val AHrRed         = Color(0xFFDC2626)   // HR = red
private val AHrRed100      = Color(0xFFFEE2E2)
private val AHoldGold      = Color(0xFFD97706)   // hold time = gold (= AAmber600)
private val AHoldGold100   = Color(0xFFFEF3C7)   // (= AAmber100)
private val AHoldGold800   = Color(0xFF92400E)   // (= AAmber800)

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

    Column(
        Modifier.fillMaxSize().background(ABgPage).verticalScroll(rememberScrollState())
    ) {
        // Dark gradient header
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(AHeaderDark, AHeaderDarker)))
                .padding(start = 20.dp, end = 20.dp, top = statusBarTop + 12.dp, bottom = 24.dp)
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Back", fontSize = 14.sp, color = ATextSlate300,
                        modifier = Modifier.clickable { onBack() }.padding(vertical = 4.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Sensor Analytics", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(session?.date?.format(formatter) ?: "", fontSize = 14.sp, color = ATextSlate300)
                Spacer(Modifier.height(4.dp))
                when {
                    analytics != null -> analytics?.let { a ->
                        Text("${a.allShots.size} detected shots · ${a.roundAnalytics.size} rounds",
                            fontSize = 12.sp, color = ACyan400)
                    }
                    isAnalyticsLoading ->
                        Text("Analysing sensor data…", fontSize = 12.sp,
                            color = ATextMuted.copy(alpha = 0.7f))
                }
            }
        }

        val s = session
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Loading…", color = ATextMuted)
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
                RoundBarChart(
                    title = "Avg Score / Arrow",
                    subtitle = "per round  ±1σ whiskers",
                    bars = scoreBars,
                    barColor = AScoreBlue,
                    yLabel = { "%.1f".format(it) },
                )
                Spacer(Modifier.height(12.dp))
            }
            if (holdBars.size >= 2) {
                RoundBarChart(
                    title = "Avg Hold Time",
                    subtitle = "seconds per round  ±1σ whiskers",
                    bars = holdBars,
                    barColor = AHoldGold,
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
                                Text("Analysing sensor data…", fontSize = 16.sp, color = ATextSecondary)
                                Spacer(Modifier.height(8.dp))
                                Text("Large sessions may take a few seconds",
                                    fontSize = 13.sp, color = ATextMuted,
                                    textAlign = TextAlign.Center)
                            } else {
                                Text("No sensor data available", fontSize = 16.sp, color = ATextSecondary)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Sensor analytics require a CSV with sensor data recorded during the session.",
                                    fontSize = 13.sp, color = ATextMuted,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                } else if (isAnalyticsLoading) {
                    // Charts from arrow data are showing, but sensor analysis still loading
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center) {
                        Text("Analysing sensor data…", fontSize = 13.sp, color = ATextMuted)
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

            // Per-round analytics — iterate analytics rounds as the source of truth.
            // Falls back to an empty RoundSummary shell if the DB round is missing
            // (e.g. session stored before arrows were parsed correctly).
            if (a.roundAnalytics.isNotEmpty()) {
                Text("Per Round", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    color = ATextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("Tap a round to expand sensor charts", fontSize = 12.sp, color = ATextMuted)
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
                    RoundAnalyticsCard(
                        round = round,
                        ra = ra,
                        dismissedShotIndices = dismissed,
                        onDismissShot = { i -> vm.dismissShot(ra.origCsvRound, i, s.filePath) },
                        onRestoreShot = { i -> vm.restoreShot(ra.origCsvRound, i, s.filePath) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ═══════════════ ROUND BAR CHART ═══════════════

@Composable
private fun RoundBarChart(
    title: String,
    subtitle: String,
    bars: List<Triple<Int, Float, Float>>,  // (roundNumber, avg, stdev)
    barColor: Color,
    yLabel: (Float) -> String,
) {
    val yMax = (bars.maxOf { it.second + it.third } * 1.25f).coerceAtLeast(1f)

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = ABgWhite),
        shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(14.dp)) {
            // Title row with color swatch for quick identification
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(barColor))
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ATextPrimary)
            }
            Text(subtitle, fontSize = 11.sp, color = ATextMuted)
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                val w = size.width; val h = size.height
                val padL = 36f; val padT = 10f; val padB = 22f; val padR = 8f
                val chartW = w - padL - padR; val chartH = h - padT - padB
                val n = bars.size
                val slotW = chartW / n
                val barW = (slotW * 0.6f).coerceAtLeast(8f)

                fun valToY(v: Float) = padT + chartH * (1f - (v / yMax).coerceIn(0f, 1f))
                val baseY = valToY(0f)

                val labelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(0x94, 0xA3, 0xB8)
                    textSize = 16f; isAntiAlias = true
                }
                val xLabelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(0x94, 0xA3, 0xB8)
                    textSize = 15f; isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                // Grid lines
                for (i in 0..3) {
                    val v = yMax * i / 3f
                    val y = valToY(v)
                    drawLine(ABorderLight, Offset(padL, y), Offset(padL + chartW, y), 0.5f)
                    drawContext.canvas.nativeCanvas.drawText(yLabel(v), 0f, y + 5f, labelPaint)
                }

                bars.forEachIndexed { idx, (roundNum, avg, stdev) ->
                    val centerX = padL + slotW * (idx + 0.5f)
                    val left = centerX - barW / 2f
                    val right = centerX + barW / 2f
                    val topY = valToY(avg)

                    // Bar fill with rounded top corners
                    if (topY < baseY) {
                        val r = (barW * 0.25f).coerceAtMost(8f)
                        val barPath = Path().apply {
                            moveTo(left, baseY)
                            lineTo(left, topY + r)
                            quadraticTo(left, topY, left + r, topY)
                            lineTo(right - r, topY)
                            quadraticTo(right, topY, right, topY + r)
                            lineTo(right, baseY)
                            close()
                        }
                        drawPath(barPath, barColor.copy(alpha = 0.7f))
                    }

                    // ±1σ whiskers
                    if (stdev > 0f) {
                        val hiY = valToY((avg + stdev).coerceAtMost(yMax))
                        val loY = valToY((avg - stdev).coerceAtLeast(0f))
                        val capW = barW * 0.35f
                        drawLine(barColor, Offset(centerX, hiY), Offset(centerX, loY), 2f)
                        drawLine(barColor, Offset(centerX - capW, hiY), Offset(centerX + capW, hiY), 2f)
                        drawLine(barColor, Offset(centerX - capW, loY), Offset(centerX + capW, loY), 2f)
                    }

                    // Round label
                    drawContext.canvas.nativeCanvas.drawText("R$roundNum", centerX, h - 2f, xLabelPaint)
                }

                // Baseline — slightly bolder than grid lines for visual anchor
                drawLine(ATextMuted.copy(alpha = 0.35f), Offset(padL, baseY), Offset(padL + chartW, baseY), 1.5f)
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
                color = ATextPrimary)
            Text("Each dot = 1 round (darker = earlier)", fontSize = 11.sp, color = ATextMuted)
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val w = size.width; val h = size.height
                val padLeft = 34f; val padTop = 8f; val padBottom = 24f; val padRight = 12f
                val chartW = w - padLeft - padRight; val chartH = h - padTop - padBottom
                fun holdToX(v: Float) = padLeft + ((v - holdMin) / holdRange) * chartW
                fun scoreToY(v: Float) = padTop + chartH * (1f - (v - scoreMin) / scoreRange)
                for (i in 0..4) {
                    val v = scoreMin + scoreRange * i / 4f
                    drawLine(ABorderLight, Offset(padLeft, scoreToY(v)), Offset(padLeft + chartW, scoreToY(v)), 0.5f)
                }
                for (i in 0..4) {
                    val v = holdMin + holdRange * i / 4f
                    drawLine(ABorderLight, Offset(holdToX(v), padTop), Offset(holdToX(v), padTop + chartH), 0.5f)
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
                color = ATextPrimary)
            Text("Each dot = 1 round (darker = earlier)", fontSize = 11.sp, color = ATextMuted)
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val w = size.width; val h = size.height
                val padLeft = 34f; val padTop = 8f; val padBottom = 24f; val padRight = 12f
                val chartW = w - padLeft - padRight; val chartH = h - padTop - padBottom
                fun hrToX(v: Float) = padLeft + ((v - hrMin) / hrRange) * chartW
                fun scoreToY(v: Float) = padTop + chartH * (1f - (v - scoreMin) / scoreRange)
                for (i in 0..4) {
                    val v = scoreMin + scoreRange * i / 4f
                    drawLine(ABorderLight, Offset(padLeft, scoreToY(v)), Offset(padLeft + chartW, scoreToY(v)), 0.5f)
                }
                for (i in 0..4) {
                    val v = hrMin + hrRange * i / 4f
                    drawLine(ABorderLight, Offset(hrToX(v), padTop), Offset(hrToX(v), padTop + chartH), 0.5f)
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
    onDismissShot: (Int) -> Unit,
    onRestoreShot: (Int) -> Unit,
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
                    Text(if (expanded) "▼" else "▶", fontSize = 12.sp, color = ATextMuted)
                    Text("Round ${round.number}", fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, color = ATextPrimary)
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
                    if (activeShotCount > 0) Text("$activeShotCount shots", fontSize = 11.sp, color = ATextMuted)
                    if (ra.avgHr > 0f) Text("%.0f bpm".format(ra.avgHr), fontSize = 11.sp, color = AHrRed)
                    if (avgHoldSec != null) Text("%.1fs hold".format(avgHoldSec), fontSize = 11.sp, color = AHoldGold)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))

                    // HR chart WITH shot markers
                    if (ra.hrSamples.size >= 2) {
                        Text("Heart Rate", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ATextSecondary)
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
                            fontWeight = FontWeight.Medium, color = ATextSecondary)
                        Spacer(Modifier.height(4.dp))
                        GravityZChart(
                            sensorData = nonWalkingData,
                            durationSec = roundDurationSec,
                            detectedShots = activeShots,
                            modifier = Modifier.fillMaxWidth().height(110.dp),
                            startSec = ra.startSec,
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    // Shot hold chips
                    if (ra.detectedShots.isNotEmpty()) {
                        Text("Detected Shots", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = ATextSecondary)
                        Spacer(Modifier.height(6.dp))
                        ShotHoldChips(
                            shots = ra.detectedShots,
                            dismissedIndices = dismissedShotIndices,
                            onDismiss = onDismissShot,
                            onRestore = onRestoreShot,
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
            drawLine(ABorderLight, Offset(padL, hrToY(v)), Offset(padL + chartW, hrToY(v)), 0.5f)
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
            drawLine(AAmber600.copy(alpha = 0.8f), Offset(x, padT), Offset(x, padT + chartH), 1.5f)
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
    modifier: Modifier = Modifier,
    startSec: Float = 0f,
) {
    if (sensorData.isEmpty()) return
    val gzMin = (sensorData.minOf { it.gz } - 0.5f).coerceAtLeast(-10f)
    val gzMax = (sensorData.maxOf { it.gz } + 0.5f).coerceAtMost(12f)
    val gzRange = (gzMax - gzMin).coerceAtLeast(1f)
    val timeRange = durationSec.coerceAtLeast(1f)

    Canvas(modifier) {
        val w = size.width; val h = size.height
        val padL = 34f; val padT = 6f; val padB = 18f; val padR = 8f
        val chartW = w - padL - padR; val chartH = h - padT - padB

        fun tToX(t: Float) = padL + ((t - startSec) / timeRange) * chartW
        fun gzToY(v: Float) = padT + chartH * (1f - (v - gzMin) / gzRange)

        // Shoot zone band (gz 5–7.5, purple tint)
        val shootTop = gzToY(7.5f)
        val shootBot = gzToY(5.0f)
        if (shootBot > shootTop) {
            drawRect(AGzColor.copy(alpha = 0.08f), Offset(padL, shootTop),
                androidx.compose.ui.geometry.Size(chartW, shootBot - shootTop))
        }

        // Grid lines
        for (i in 0..3) {
            val v = gzMin + gzRange * i / 3f
            drawLine(ABorderLight, Offset(padL, gzToY(v)), Offset(padL + chartW, gzToY(v)), 0.5f)
        }

        // GZ line
        val path = Path()
        sensorData.forEachIndexed { i, s ->
            val x = tToX(s.time); val y = gzToY(s.gz.coerceIn(gzMin, gzMax))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, AGzColor, style = Stroke(width = 1.5f))

        // Shot markers (amber vertical lines) on top
        detectedShots.forEach { shot ->
            val x = tToX(shot.time)
            drawLine(AAmber600.copy(alpha = 0.8f), Offset(x, padT), Offset(x, padT + chartH), 1.5f)
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
}

@Composable
private fun ShotHoldChips(
    shots: List<DetectedShot>,
    dismissedIndices: Set<Int>,
    onDismiss: (Int) -> Unit,
    onRestore: (Int) -> Unit,
) {
    var selectedIdx by remember { mutableIntStateOf(-1) }

    // ── Chip row ──
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        shots.forEachIndexed { idx, shot ->
            val dismissed  = idx in dismissedIndices
            val isSelected = !dismissed && selectedIdx == idx
            val bgColor = when {
                dismissed  -> ABorderLight
                isSelected -> AHoldGold.copy(alpha = 0.15f)
                else       -> AHoldGold100
            }
            val textColor = if (dismissed) ATextMuted else AHoldGold800
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .then(if (isSelected) Modifier.border(1.dp, AHoldGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp)) else Modifier)
                    .clickable {
                        if (dismissed) onRestore(idx)
                        else selectedIdx = if (selectedIdx == idx) -1 else idx
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1fs".format(shot.holdSec), fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = textColor)
                    if (dismissed) {
                        Text("↩ restore", fontSize = 10.sp, color = ATextMuted)
                    } else {
                        Text("Shot ${idx + 1}", fontSize = 10.sp, color = AHoldGold)
                        if (shot.gzStdev > 0f) {
                            Text("±%.2f".format(shot.gzStdev), fontSize = 9.sp, color = AGzColor)
                        }
                    }
                }
            }
        }
    }

    // ── Detail panel for selected shot ──
    val selShot = shots.getOrNull(selectedIdx)
    if (selShot != null && selectedIdx !in dismissedIndices) {
        Spacer(Modifier.height(8.dp))
        ShotDetailPanel(
            shot      = selShot,
            shotIndex = selectedIdx,
            onDismiss = { onDismiss(selectedIdx); selectedIdx = -1 },
            onClose   = { selectedIdx = -1 },
        )
    }

    Spacer(Modifier.height(4.dp))
    Text(
        "Tap chip to inspect · dismissed shots excluded from stats",
        fontSize = 10.sp, color = ATextMuted,
    )
}

// ── Per-shot detail panel ──────────────────────────────────────────────────

@Composable
private fun ShotDetailPanel(
    shot: DetectedShot,
    shotIndex: Int,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
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
            Text("Shot ${shotIndex + 1} detail", fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = AHoldGold800)
            Text("✕", fontSize = 14.sp, color = ATextMuted,
                modifier = Modifier.clickable { onClose() }.padding(4.dp))
        }
        Spacer(Modifier.height(8.dp))

        // Stat pills
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShotStatPill(Modifier.weight(1f), "Hold", "%.1fs".format(shot.holdSec), AHoldGold800, AHoldGold100)
            ShotStatPill(Modifier.weight(1f), "Stability",
                if (shot.gzStdev > 0f) "±%.2f gz".format(shot.gzStdev) else "—",
                AGzColor, AGzColor.copy(alpha = 0.08f))
            if (shot.hrAtShot != null) {
                ShotStatPill(Modifier.weight(1f), "HR at shot",
                    "%.0f bpm".format(shot.hrAtShot), AHrPink, AHrPink.copy(alpha = 0.08f))
            }
        }

        // Gz mini-chart for hold window
        if (shot.sensorWindow.size >= 2) {
            Spacer(Modifier.height(10.dp))
            Text("Gravity Z during hold", fontSize = 11.sp,
                fontWeight = FontWeight.Medium, color = ATextSecondary)
            Spacer(Modifier.height(4.dp))
            ShotGzMiniChart(
                sensorWindow = shot.sensorWindow,
                shot         = shot,
                modifier     = Modifier.fillMaxWidth().height(72.dp),
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.End) {
            Text(
                "Dismiss (false positive)",
                fontSize = 11.sp, color = ARed500,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
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
            Text(label, fontSize = 9.sp, color = ATextMuted, textAlign = TextAlign.Center)
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
            drawLine(ABorderLight, Offset(padL, gzToY(v)), Offset(padL + chartW, gzToY(v)), 0.5f)
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
