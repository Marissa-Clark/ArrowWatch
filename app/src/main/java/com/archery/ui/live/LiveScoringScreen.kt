package com.archery.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.archery.shared.LiveArrow
import com.archery.shared.LiveRound
import com.archery.shared.ScoreZone
import com.archery.shared.WatchPhase

// ── Score options ────────────────────────────────────────────────────────────

private data class ScoreOption(
    val label: String,
    val exactScore: Int?,
    val zone: ScoreZone,
    val color: Color,
    val textColor: Color = Color.White,
)

private val SCORE_OPTIONS = listOf(
    ScoreOption("10", 10, ScoreZone.GOLD,  Color(0xFFD97706)),
    ScoreOption("9",   9, ScoreZone.GOLD,  Color(0xFFD97706)),
    ScoreOption("8",   8, ScoreZone.RED,   Color(0xFFDC2626)),
    ScoreOption("7",   7, ScoreZone.RED,   Color(0xFFDC2626)),
    ScoreOption("6",   6, ScoreZone.BLUE,  Color(0xFF0891B2)),
    ScoreOption("5",   5, ScoreZone.BLUE,  Color(0xFF0891B2)),
    ScoreOption("4",   4, ScoreZone.BLACK, Color(0xFF334155)),
    ScoreOption("3",   3, ScoreZone.BLACK, Color(0xFF334155)),
    ScoreOption("2",   2, ScoreZone.WHITE, Color(0xFFE2E8F0), Color(0xFF1E293B)),
    ScoreOption("1",   1, ScoreZone.WHITE, Color(0xFFE2E8F0), Color(0xFF1E293B)),
    ScoreOption("M",   0, ScoreZone.MISS,  Color(0xFFCBD5E1), Color(0xFF1E293B)),
)

// ── Color palette ────────────────────────────────────────────────────────────

private val HeaderDark    = Color(0xFF1E293B)
private val HeaderDarker  = Color(0xFF0F172A)
private val BgPage        = Color(0xFFF8FAFC)
private val BgWhite       = Color.White
private val TextPrimary   = Color(0xFF1E293B)
private val TextSecondary = Color(0xFF475569)
private val TextMuted     = Color(0xFF94A3B8)
private val TextSlate300  = Color(0xFFCBD5E1)
private val Cyan600       = Color(0xFF0891B2)
private val Cyan100       = Color(0xFFCFFAFE)
private val Cyan800       = Color(0xFF155E75)
private val Amber600      = Color(0xFFD97706)
private val Amber100      = Color(0xFFFEF3C7)
private val Amber800      = Color(0xFF92400E)
private val LiveGreen     = Color(0xFF16A34A)
private val BorderLight   = Color(0xFFE2E8F0)
private val WatchRed      = Color(0xFFEF4444)
private val BgSlate100    = Color(0xFFF1F5F9)

private val ZONE_COLORS = mapOf(
    ScoreZone.GOLD  to Color(0xFFD97706),
    ScoreZone.RED   to Color(0xFFDC2626),
    ScoreZone.BLUE  to Color(0xFF0891B2),
    ScoreZone.BLACK to Color(0xFF334155),
    ScoreZone.WHITE to Color(0xFFE2E8F0),
    ScoreZone.MISS  to Color(0xFFCBD5E1),
    ScoreZone.DNS   to Color(0xFF555555),
)

private val ZONE_ORDER = listOf(
    ScoreZone.GOLD, ScoreZone.RED, ScoreZone.BLUE,
    ScoreZone.BLACK, ScoreZone.WHITE, ScoreZone.MISS,
)

// ── Entry point ──────────────────────────────────────────────────────────────

@Composable
fun LiveScoringScreen(
    onBack: () -> Unit,
    vm: LiveScoringViewModel = viewModel(),
) {
    val session by vm.session.collectAsState()

    LaunchedEffect(session?.isEnded) {
        if (session?.isEnded == true) onBack()
    }

    val s = session
    if (s == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(BgPage),
            contentAlignment = Alignment.Center,
        ) {
            Text("No active session", color = TextMuted)
        }
        return
    }

    val currentRoundNum = s.currentRound
    val currentRound = s.rounds.find { it.number == currentRoundNum && !it.isComplete }
    val completedRounds = s.rounds.filter { it.isComplete }
    val phase = s.currentPhase

    val firstUnscored = currentRound?.arrows?.indexOfFirst { !it.isScored } ?: -1
    var selectedArrow by remember(currentRoundNum, phase) {
        mutableIntStateOf(firstUnscored.coerceAtLeast(0))
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPage)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Dark gradient header ──────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(HeaderDark, HeaderDarker)))
                .padding(start = 20.dp, end = 20.dp, top = statusBarTop + 8.dp, bottom = 16.dp)
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically,
                ) {
                    Text(
                        "← Back",
                        fontSize = 14.sp,
                        color = TextSlate300,
                        modifier = Modifier.clickable { onBack() }.padding(vertical = 4.dp),
                    )
                    val (phaseBg, phaseText, phaseLabel) = when (phase) {
                        WatchPhase.SCORING  -> Triple(Amber600.copy(0.2f), Amber600, "SCORING")
                        WatchPhase.SHOOTING -> Triple(LiveGreen.copy(0.2f), LiveGreen, "SHOOTING")
                        WatchPhase.SUMMARY  -> Triple(Cyan600.copy(0.2f), Cyan600, "SUMMARY")
                        WatchPhase.IDLE     -> Triple(TextMuted.copy(0.2f), TextMuted, "IDLE")
                    }
                    Box(
                        Modifier
                            .background(phaseBg, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            phaseLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = phaseText, letterSpacing = 1.sp,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "Live Scoring — End $currentRoundNum",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White,
                )
            }
        }

        // ── Main content ──────────────────────────────────────────────────────
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            when (phase) {
                WatchPhase.SHOOTING -> {
                    ShootingPhaseView(
                        round = currentRound,
                        arrowsPerRound = s.arrowsPerRound,
                        completedRounds = completedRounds,
                        onEnterScoring = vm::enterScoring,
                        onEditArrow = { roundNum, shotIndex, zone, score ->
                            vm.scoreArrow(roundNum, shotIndex, zone.name, score)
                        },
                    )
                }
                WatchPhase.SCORING -> {
                    ScoringPhaseView(
                        round = currentRound,
                        roundNumber = currentRoundNum,
                        arrowsPerRound = s.arrowsPerRound,
                        selectedArrow = selectedArrow,
                        onSelectArrow = { selectedArrow = it },
                        onScoreArrow = { listIndex, zone, exactScore ->
                            val arrow = currentRound?.arrows?.getOrNull(listIndex)
                            val shotIndex = arrow?.shotIndex ?: listIndex
                            vm.scoreArrow(currentRoundNum, shotIndex, zone.name, exactScore)
                            val arrows = currentRound?.arrows ?: emptyList()
                            val nextUnscored = arrows.withIndex().firstOrNull { (i, a) ->
                                i != listIndex && !a.isScored
                            }
                            if (nextUnscored != null) selectedArrow = nextUnscored.index
                        },
                        onDone = { vm.finishScoring() },
                    )
                }
                else -> {}
            }

            // ── End session button ────────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFDC2626).copy(alpha = 0.08f))
                    .border(1.dp, Color(0xFFDC2626).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable { vm.endSession(); onBack() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "End Session", color = Color(0xFFDC2626),
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Shooting phase ────────────────────────────────────────────────────────────

@Composable
private fun ShootingPhaseView(
    round: LiveRound?,
    arrowsPerRound: Int,
    completedRounds: List<LiveRound>,
    onEnterScoring: () -> Unit,
    onEditArrow: (roundNum: Int, shotIndex: Int, zone: ScoreZone, score: Int?) -> Unit,
) {
    // ── Session total + avg card ──────────────────────────────────────────────
    val sessionTotal = completedRounds.sumOf { (it.confirmedScore ?: it.detectedScore ?: 0f).toDouble() }.toFloat()
    val totalArrowsScored = completedRounds.sumOf { r -> r.arrows.count { it.isScored && it.zone != ScoreZone.DNS } }
    val avgPerArrow = if (totalArrowsScored > 0) sessionTotal / totalArrowsScored else null

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HeaderDark),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Total
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (sessionTotal > 0f) "%.0f".format(sessionTotal) else "—",
                    fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White,
                )
                Text(
                    "TOTAL", fontSize = 11.sp, fontWeight = FontWeight.Normal,
                    letterSpacing = 2.sp, color = TextMuted,
                )
            }
            // Divider
            Box(Modifier.width(1.dp).height(48.dp).background(Color(0xFF334155)))
            // Avg per arrow
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (avgPerArrow != null) "%.1f".format(avgPerArrow) else "—",
                    fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Amber600,
                )
                Text(
                    "AVG / ARROW", fontSize = 11.sp, fontWeight = FontWeight.Normal,
                    letterSpacing = 2.sp, color = TextMuted,
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // ── Current end card — detected shots + enter scoring ─────────────────────
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (round != null && round.arrows.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    round.arrows.forEach { arrow ->
                        val qz = arrow.quickZone
                        val zoneColor = ZONE_COLORS[qz]
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(zoneColor ?: BorderLight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                qz?.label?.take(1) ?: "?",
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                color = if (qz == ScoreZone.WHITE || qz == ScoreZone.MISS)
                                    TextPrimary else Color.White,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Button(
                onClick = onEnterScoring,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    "Enter Scoring", fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, color = Color.White,
                )
            }
        }
    }

    if (completedRounds.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))

        // ── Zone distribution across all completed ends ───────────────────────
        val zoneCounts = completedRounds
            .flatMap { it.arrows }
            .mapNotNull { it.zone }
            .filter { it != ScoreZone.DNS }
            .groupingBy { it }
            .eachCount()
        LiveZoneDistribution(zoneCounts)

        Spacer(Modifier.height(16.dp))

        Text(
            "End Details", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        LiveRoundDetailsTable(completedRounds, arrowsPerRound, onEditArrow = onEditArrow)
    }
}

// ── Scoring phase ─────────────────────────────────────────────────────────────

@Composable
private fun ScoringPhaseView(
    round: LiveRound?,
    roundNumber: Int,
    arrowsPerRound: Int,
    selectedArrow: Int,
    onSelectArrow: (Int) -> Unit,
    onScoreArrow: (listIndex: Int, zone: ScoreZone, exactScore: Int?) -> Unit,
    onDone: () -> Unit,
) {
    val arrows = round?.arrows ?: emptyList()
    val numArrows = arrows.size.coerceAtLeast(arrowsPerRound)

    // ── Running end summary ───────────────────────────────────────────────────
    val scoredArrows = arrows.filter { it.isScored && it.zone != ScoreZone.DNS }
    val runningTotal = scoredArrows.sumOf {
        it.score?.toDouble() ?: it.zone?.defaultScore?.toDouble() ?: 0.0
    }.toFloat()
    val scoredCount = arrows.count { it.isScored }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "End $roundNumber",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = TextSecondary, letterSpacing = 0.5.sp,
                )
                Text(
                    "$scoredCount / $numArrows scored",
                    fontSize = 11.sp, color = TextMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (runningTotal > 0f) "%.0f".format(runningTotal) else "—",
                    fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = if (runningTotal > 0f) Cyan800 else TextMuted,
                )
                Text(
                    "total",
                    fontSize = 10.sp, color = TextMuted,
                )
            }
        }

        // Arrow selectors inside the card
        if (numArrows > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 14.dp),
            ) {
                for (i in 0 until numArrows) {
                    val arrow = arrows.getOrNull(i)
                    val zone = arrow?.zone
                    val isDns = zone == ScoreZone.DNS
                    val zoneColor = ZONE_COLORS[zone]
                    val isSelected = i == selectedArrow

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(zoneColor ?: BorderLight)
                            .then(if (isSelected) Modifier.border(3.dp, Cyan600, CircleShape) else Modifier)
                            .clickable { onSelectArrow(i) }
                            .then(
                                if (isDns) Modifier.drawBehind {
                                    drawCircle(
                                        WatchRed, radius = size.minDimension / 2 - 4f,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                                    )
                                    drawLine(
                                        WatchRed,
                                        Offset(size.width * 0.2f, size.height * 0.8f),
                                        Offset(size.width * 0.8f, size.height * 0.2f),
                                        strokeWidth = 3f,
                                    )
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (zone != null && !isDns) {
                            val scoreText = arrow?.score?.toInt()?.let {
                                if (it == 10) "10" else "$it"
                            } ?: zone.label.take(1)
                            Text(
                                scoreText,
                                fontSize = if (scoreText.length > 1) 11.sp else 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (zone == ScoreZone.WHITE || zone == ScoreZone.MISS)
                                    TextPrimary else Color.White,
                            )
                        } else if (zone == null) {
                            Text(
                                "${i + 1}", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Text(
        "Arrow ${selectedArrow + 1}", fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold, color = TextPrimary,
    )
    Spacer(Modifier.height(8.dp))

    // Score buttons: pairs of 2 (10/9, 8/7, 6/5, 4/3, 2/1)
    SCORE_OPTIONS.take(10).chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pair.forEach { option ->
                ScoreButton(
                    option = option,
                    modifier = Modifier.weight(1f),
                    onClick = { onScoreArrow(selectedArrow, option.zone, option.exactScore) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }

    // M + DNS row
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val missOpt = SCORE_OPTIONS[10]
        ScoreButton(
            option = missOpt,
            modifier = Modifier.weight(1f),
            onClick = { onScoreArrow(selectedArrow, missOpt.zone, missOpt.exactScore) },
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF555555))
                .clickable { onScoreArrow(selectedArrow, ScoreZone.DNS, null) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(28.dp).drawBehind {
                    drawCircle(
                        Color.White, radius = size.minDimension / 2 - 2f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f),
                    )
                    drawLine(
                        Color.White,
                        Offset(size.width * 0.2f, size.height * 0.8f),
                        Offset(size.width * 0.8f, size.height * 0.2f),
                        strokeWidth = 2.5f,
                    )
                }
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Cyan600),
        shape = RoundedCornerShape(10.dp),
    ) { Text("Done", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
}

@Composable
private fun ScoreButton(option: ScoreOption, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(option.color)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(option.label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = option.textColor)
    }
}

// ── Completed round row ───────────────────────────────────────────────────────

@Composable
private fun CompletedRoundRow(round: LiveRound) {
    val total = round.confirmedScore ?: round.detectedScore
    val confirmed = round.confirmedScore != null

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(28.dp).background(HeaderDark, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${round.number}", fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, color = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    round.arrows.forEach { arrow ->
                        val isDns = arrow.zone == ScoreZone.DNS
                        val zoneColor = ZONE_COLORS[arrow.zone]
                        Box(
                            Modifier.size(22.dp).clip(CircleShape)
                                .background(zoneColor ?: BorderLight)
                                .then(
                                    if (isDns) Modifier.drawBehind {
                                        drawCircle(
                                            WatchRed, radius = size.minDimension / 2 - 2f,
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                                        )
                                        drawLine(
                                            WatchRed,
                                            Offset(size.width * 0.2f, size.height * 0.8f),
                                            Offset(size.width * 0.8f, size.height * 0.2f),
                                            strokeWidth = 2f,
                                        )
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!isDns) {
                                val scoreText = arrow.score?.toInt()?.toString()
                                    ?: arrow.zone?.label?.take(1) ?: ""
                                Text(
                                    scoreText,
                                    fontSize = if (scoreText.length > 1) 8.sp else 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (arrow.zone == ScoreZone.WHITE || arrow.zone == ScoreZone.MISS)
                                        TextPrimary else Color.White,
                                )
                            }
                        }
                    }
                }
            }
            @Suppress("UNUSED_VARIABLE") val isApprox = !confirmed
            Text(
                if (total != null && total > 0) "%.0f".format(total) else "—",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = if (confirmed) Cyan800 else Amber600,
            )
        }
    }
}

// ── Round details table ───────────────────────────────────────────────────────

private typealias EditTarget = Pair<Int, Int>

@Composable
private fun LiveRoundDetailsTable(
    rounds: List<LiveRound>,
    arrowsPerRound: Int,
    onEditArrow: (roundNum: Int, shotIndex: Int, zone: ScoreZone, score: Int?) -> Unit,
) {
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    val maxArrows = rounds.maxOf { it.arrows.size.coerceAtLeast(arrowsPerRound) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column {
            // Header row
            Row(
                Modifier.fillMaxWidth().background(BgSlate100)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("End", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = TextSecondary, modifier = Modifier.width(36.dp))
                for (a in 1..maxArrows) {
                    Text("A$a", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = TextSecondary, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f))
                }
                Text("Tot", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = TextSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.width(44.dp))
                Text("Avg", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = TextSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.width(40.dp))
            }

            rounds.forEach { round ->
                val roundTotal = round.confirmedScore ?: round.detectedScore ?: 0f
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(36.dp)) {
                        Box(
                            Modifier.size(24.dp).background(HeaderDark, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${round.number}", fontSize = 10.sp,
                                fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    for (a in 0 until maxArrows) {
                        val arrow = round.arrows.getOrNull(a)
                        val scoreText = when {
                            arrow == null -> "—"
                            arrow.zone == ScoreZone.DNS -> "–"
                            else -> arrow.score?.let { "${it.toInt()}" }
                                ?: arrow.zone?.label?.take(1) ?: "—"
                        }
                        val shotIdx = arrow?.shotIndex ?: a
                        Text(
                            scoreText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (arrow?.zone != null) TextSecondary else TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { editTarget = round.number to shotIdx },
                        )
                    }
                    Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.background(Cyan100, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (roundTotal > 0) "%.0f".format(roundTotal) else "—",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Cyan800,
                            )
                        }
                    }
                    Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                        val scored = round.arrows.count {
                            it.zone != ScoreZone.DNS && it.zone != null
                        }
                        val avg = if (scored > 0) roundTotal / scored else 0f
                        Box(
                            Modifier.background(Amber100, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (avg > 0) "%.1f".format(avg) else "—",
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Amber800,
                            )
                        }
                    }
                }
            }

            // Totals row
            val totalScore = rounds.sumOf {
                ((it.confirmedScore ?: it.detectedScore ?: 0f).toDouble())
            }
            val totalArrows = rounds.sumOf { r ->
                r.arrows.count { it.zone != ScoreZone.DNS && it.zone != null }
            }
            val overallAvg = if (totalArrows > 0) totalScore / totalArrows else 0.0

            Row(
                Modifier.fillMaxWidth().background(BgSlate100)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = TextPrimary, modifier = Modifier.width(36.dp))
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                    Text("%.0f".format(totalScore), fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, color = Cyan800)
                }
                Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (overallAvg > 0) "%.1f".format(overallAvg) else "—",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Amber800,
                    )
                }
            }
        }
    }

    // ── Arrow edit dialog ─────────────────────────────────────────────────────
    val target = editTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("Edit Arrow") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SCORE_OPTIONS.take(10).chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { opt ->
                                Box(
                                    Modifier.weight(1f).height(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(opt.color)
                                        .clickable {
                                            onEditArrow(target.first, target.second, opt.zone, opt.exactScore)
                                            editTarget = null
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(opt.label, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                        color = opt.textColor)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val missOpt = SCORE_OPTIONS[10]
                        Box(
                            Modifier.weight(1f).height(44.dp)
                                .clip(RoundedCornerShape(8.dp)).background(missOpt.color)
                                .clickable {
                                    onEditArrow(target.first, target.second, missOpt.zone, missOpt.exactScore)
                                    editTarget = null
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("M", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Box(
                            Modifier.weight(1f).height(44.dp)
                                .clip(RoundedCornerShape(8.dp)).background(Color(0xFF555555))
                                .clickable {
                                    onEditArrow(target.first, target.second, ScoreZone.DNS, null)
                                    editTarget = null
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("DNS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("Cancel") }
            },
        )
    }
}

// ── Zone distribution ─────────────────────────────────────────────────────────

@Composable
private fun LiveZoneDistribution(zoneCounts: Map<ScoreZone, Int>) {
    val total = zoneCounts.values.sum()
    if (total == 0) return
    val ordered = ZONE_ORDER.filter { zoneCounts.containsKey(it) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(16.dp)
                .clip(RoundedCornerShape(10.dp)).background(BgSlate100)
        ) {
            ordered.forEach { zone ->
                val frac = (zoneCounts[zone] ?: 0).toFloat() / total
                Box(
                    Modifier.weight(frac).height(16.dp)
                        .background(ZONE_COLORS[zone] ?: TextMuted)
                )
            }
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private val previewArrows3 = listOf(
    LiveArrow(shotIndex = 1, quickZone = ScoreZone.GOLD),
    LiveArrow(shotIndex = 2, quickZone = ScoreZone.RED),
    LiveArrow(shotIndex = 3, quickZone = ScoreZone.BLUE),
)

private val previewCompletedRounds = listOf(
    LiveRound(
        number = 1, isComplete = true,
        confirmedScore = 26f,
        arrows = listOf(
            LiveArrow(1, zone = ScoreZone.GOLD, score = 10f),
            LiveArrow(2, zone = ScoreZone.GOLD, score = 9f),
            LiveArrow(3, zone = ScoreZone.RED,  score = 7f),
        ),
    ),
    LiveRound(
        number = 2, isComplete = true,
        confirmedScore = 21f,
        arrows = listOf(
            LiveArrow(1, zone = ScoreZone.BLUE,  score = 6f),
            LiveArrow(2, zone = ScoreZone.RED,   score = 8f),
            LiveArrow(3, zone = ScoreZone.BLACK, score = 4f),
        ),
    ),
    LiveRound(
        number = 3, isComplete = true,
        confirmedScore = 28f,
        arrows = listOf(
            LiveArrow(1, zone = ScoreZone.GOLD, score = 10f),
            LiveArrow(2, zone = ScoreZone.GOLD, score = 10f),
            LiveArrow(3, zone = ScoreZone.RED,  score = 8f),
        ),
    ),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Shooting – no arrows yet")
@Composable
private fun PreviewShootingEmpty() {
    ShootingPhaseView(
        round = null,
        arrowsPerRound = 3,
        completedRounds = emptyList(),
        onEnterScoring = {},
        onEditArrow = { _, _, _, _ -> },
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Shooting – arrows detected + history")
@Composable
private fun PreviewShootingWithHistory() {
    Column(Modifier.background(BgPage).padding(20.dp)) {
        ShootingPhaseView(
            round = LiveRound(number = 4, arrows = previewArrows3),
            arrowsPerRound = 3,
            completedRounds = previewCompletedRounds,
            onEnterScoring = {},
            onEditArrow = { _, _, _, _ -> },
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Scoring – unscored")
@Composable
private fun PreviewScoringUnscored() {
    Column(Modifier.background(BgPage).padding(20.dp)) {
        ScoringPhaseView(
            round = LiveRound(
                number = 4,
                arrows = listOf(
                    LiveArrow(shotIndex = 1),
                    LiveArrow(shotIndex = 2),
                    LiveArrow(shotIndex = 3),
                ),
            ),
            roundNumber = 4,
            arrowsPerRound = 3,
            selectedArrow = 0,
            onSelectArrow = {},
            onScoreArrow = { _, _, _ -> },
            onDone = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Scoring – partially scored")
@Composable
private fun PreviewScoringPartial() {
    Column(Modifier.background(BgPage).padding(20.dp)) {
        ScoringPhaseView(
            round = LiveRound(
                number = 4,
                arrows = listOf(
                    LiveArrow(shotIndex = 1, zone = ScoreZone.GOLD, score = 10f),
                    LiveArrow(shotIndex = 2, zone = ScoreZone.RED,  score = 8f),
                    LiveArrow(shotIndex = 3),
                ),
            ),
            roundNumber = 4,
            arrowsPerRound = 3,
            selectedArrow = 2,
            onSelectArrow = {},
            onScoreArrow = { _, _, _ -> },
            onDone = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Zone distribution")
@Composable
private fun PreviewZoneDistribution() {
    Column(Modifier.background(BgPage).padding(20.dp)) {
        LiveZoneDistribution(
            mapOf(
                ScoreZone.GOLD  to 7,
                ScoreZone.RED   to 4,
                ScoreZone.BLUE  to 3,
                ScoreZone.BLACK to 1,
                ScoreZone.MISS  to 1,
            )
        )
    }
}
