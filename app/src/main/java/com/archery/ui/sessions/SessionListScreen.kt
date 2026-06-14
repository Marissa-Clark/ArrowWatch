package com.archery.ui.sessions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.archery.shared.LiveSession
import com.archery.shared.ScoreZone
import com.archery.shared.SessionSummary
import com.archery.ui.theme.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlinx.coroutines.delay

private val BgWhite       = Color.White
private val LiveGreen     = Color(0xFF16A34A)
private val Amber700      = Color(0xFFB45309)
private val Red600        = Color(0xFFDC2626)
private val Amber400      = Color(0xFFFBBF24)

@Composable
fun SessionListScreen(
    onSessionClick: (Long) -> Unit,
    onLiveSessionClick: () -> Unit,
    onManageProfiles: () -> Unit,
    onLogPastSession: () -> Unit,
    vm: SessionListViewModel = viewModel(),
) {
    val allSessions by vm.sessions.collectAsState()
    val liveSession by vm.liveSession.collectAsState()
    var showArchived by remember { mutableStateOf(false) }

    val activeSessions = allSessions.filter { !it.isArchived }
    val archivedSessions = allSessions.filter { it.isArchived }
    val displaySessions = if (showArchived) allSessions else activeSessions

    val hasLive = liveSession != null && !(liveSession?.isEnded ?: true)
    val scoredSessions = activeSessions.filter { it.avgPerArrow > 0 }

    if (allSessions.isEmpty() && !hasLive) {
        Box(Modifier.fillMaxSize().background(AppBgPage), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No sessions yet", fontSize = 18.sp, color = AppTextSecondary)
                Spacer(Modifier.height(8.dp))
                Text("Sessions will appear here after you shoot on your watch",
                    fontSize = 14.sp, color = AppTextMuted)
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(AppCyan600, AppCyan800)))
                        .clickable { vm.startSession() }
                        .padding(horizontal = 32.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Start Session", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White)
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AppBgPage).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Spacer(Modifier.height(52.dp)) }

        if (hasLive) {
            item { LiveSessionCard(liveSession!!, onClick = onLiveSessionClick) }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // End session — tells watch to end
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Red600.copy(alpha = 0.1f))
                            .border(1.dp, Red600.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .clickable { vm.endSession() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("End Session", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = Red600)
                    }
                    // Dismiss — clears stale live session from phone only
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppTextMuted.copy(alpha = 0.08f))
                            .border(1.dp, AppTextMuted.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .clickable { vm.dismissLiveSession() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Dismiss (phone)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = AppTextMuted)
                    }
                }
            }
        }

        if (!hasLive) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.horizontalGradient(listOf(AppCyan600, AppCyan800)))
                            .clickable { vm.startSession() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Start Session", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = Color.White)
                    }
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppTextMuted.copy(alpha = 0.1f))
                            .border(1.dp, AppBorderLight, RoundedCornerShape(12.dp))
                            .clickable { onLogPastSession() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Log Past Session", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = AppTextSecondary)
                    }
                }
            }
        }



        val dateFmt = DateTimeFormatter.ofPattern("M/d")

        // Group sessions by calendar day; average same-day sessions together
        val scoreDailyGroups = scoredSessions
            .groupBy { it.date.toLocalDate() }
            .entries.sortedBy { it.key }
        val scoreFirstDay = scoreDailyGroups.firstOrNull()?.key
        val scoreEntries = scoreDailyGroups.map { (day, sessions) ->
            val vals = sessions.flatMap { s ->
                s.rounds.mapNotNull { r ->
                    val n = r.arrows.count { it.zone != ScoreZone.DNS }
                    if (n > 0) (r.displayScore / n).toDouble() else null
                }
            }
            val mean = if (vals.isNotEmpty()) vals.average().toFloat()
                       else sessions.map { it.avgPerArrow }.average().toFloat()
            val offset = if (scoreFirstDay != null) ChronoUnit.DAYS.between(scoreFirstDay, day) else 0L
            BarEntry(mean, stdevOf(vals), day.format(dateFmt), offset)
        }
        if (scoreEntries.size >= 2) {
            item { TrendLineChart("Avg Score / Arrow", scoreEntries, AppCyan600) { "%.1f".format(it) } }
        }

        val holdSessions = activeSessions.filter { (it.avgHoldMs ?: 0L) > 0L }
        val holdDailyGroups = holdSessions
            .groupBy { it.date.toLocalDate() }
            .entries.sortedBy { it.key }
        val holdFirstDay = holdDailyGroups.firstOrNull()?.key
        val holdEntries = holdDailyGroups.map { (day, sessions) ->
            val vals = sessions.flatMap { s ->
                s.rounds.mapNotNull { r ->
                    (r.avgHoldMs ?: 0L).takeIf { it > 0L }?.let { it / 1000.0 }
                }
            }
            val mean = if (vals.isNotEmpty()) vals.average().toFloat()
                       else sessions.map { (it.avgHoldMs ?: 0L) / 1000f }.average().toFloat()
            val offset = if (holdFirstDay != null) ChronoUnit.DAYS.between(holdFirstDay, day) else 0L
            BarEntry(mean, stdevOf(vals), day.format(dateFmt), offset)
        }
        if (holdEntries.size >= 2) {
            item { TrendLineChart("Avg Hold / Arrow (s)", holdEntries, Amber700) { "%.1f".format(it) } }
        }

        items(displaySessions, key = { it.id }) { s ->
            SessionCard(
                session = s,
                onClick = { onSessionClick(s.id) },
                onDelete = { vm.delete(s.id) },
            )
        }

        if (archivedSessions.isNotEmpty()) {
            item {
                Text(
                    if (showArchived) "Hide archived (${archivedSessions.size})"
                    else "Show archived (${archivedSessions.size})",
                    fontSize = 13.sp, color = AppCyan600,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { showArchived = !showArchived }
                        .padding(vertical = 12.dp),
                )
            }
        }

        item {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppTextMuted.copy(alpha = 0.07f))
                    .border(1.dp, AppBorderLight, RoundedCornerShape(10.dp))
                    .clickable(onClick = onManageProfiles)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Detection Settings", fontSize = 14.sp, color = AppTextSecondary,
                    fontWeight = FontWeight.Medium)
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun LiveSessionCard(session: LiveSession, onClick: () -> Unit) {
    val currentRound = session.rounds.lastOrNull { !it.isComplete } ?: session.rounds.lastOrNull()
    val completedRounds = session.rounds.filter { it.isComplete }
    val runningScore = completedRounds.mapNotNull { it.confirmedScore ?: it.detectedScore }.sum()
    val totalShots = session.rounds.sumOf { it.arrows.size }
    val latestHr = currentRound?.latestHeartRate ?: 0f
    val holdTimes = session.rounds.mapNotNull { it.latestHoldMs }.filter { it > 0L }
    val avgHold = if (holdTimes.isNotEmpty()) holdTimes.average().toLong() else 0L
    val shots = currentRound?.arrows?.size ?: 0
    val arrowsPerRound = session.arrowsPerRound.coerceAtLeast(1)
    val prog = shots.toFloat() / arrowsPerRound

    var tickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tickMs = System.currentTimeMillis() } }
    val elapsed = tickMs - session.startTimeMs
    val min = (elapsed / 60000).toInt(); val sec = ((elapsed % 60000) / 1000).toInt()

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AppHeaderDarker),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.background(LiveGreen.copy(0.2f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                        Arrangement.spacedBy(5.dp), Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(LiveGreen, CircleShape))
                        Text("LIVE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = LiveGreen, letterSpacing = 1.sp)
                    }
                    Text("Round ${session.currentRound}", fontSize = 14.sp,
                        fontWeight = FontWeight.Medium, color = Color.White)
                }
                Text("%d:%02d".format(min, sec), fontSize = 20.sp,
                    fontWeight = FontWeight.Medium, color = Color.White, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Shots", fontSize = 11.sp, color = AppTextSlate300)
                Text("$shots/$arrowsPerRound", fontSize = 11.sp,
                    fontWeight = FontWeight.Medium, color = Color.White)
            }
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().height(4.dp)
                .background(Color.White.copy(0.1f), RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth(prog.coerceIn(0f, 1f)).height(4.dp)
                    .background(LiveGreen, RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) {
                LiveTile(Modifier.weight(1f), "Score",
                    if (runningScore > 0) "%.0f".format(runningScore) else "—", Amber400)
                LiveTile(Modifier.weight(1f), "HR",
                    if (latestHr > 0f) "%.0f".format(latestHr) else "—", Red600,
                    if (latestHr > 0f) "bpm" else "")
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp)) {
                LiveTile(Modifier.weight(1f), "Arrows", "$totalShots", Color.White)
                LiveTile(Modifier.weight(1f), "Hold",
                    if (avgHold > 0) "%.1f".format(avgHold / 1000f) else "—", Color.White,
                    if (avgHold > 0) "s" else "")
            }
        }
    }
}

@Composable
private fun LiveTile(mod: Modifier, label: String, value: String, valueColor: Color, unit: String = "") {
    Box(mod.background(Color.White.copy(0.07f), RoundedCornerShape(10.dp)).padding(10.dp)) {
        Column {
            Text(label, fontSize = 10.sp, color = AppTextSlate300, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
                if (unit.isNotEmpty()) Text(unit, fontSize = 10.sp, color = AppTextSlate300,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun SessionCard(session: SessionSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
    val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
    val avg = session.avgPerArrow
    var confirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                // ── Left: date + time + optional name ────────────────────────
                Column(Modifier.weight(1f)) {
                    Text(
                        session.date.format(dateFmt),
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppHeaderDark,
                    )
                    Text(
                        session.date.format(timeFmt),
                        fontSize = 12.sp, color = AppTextSecondary,
                    )
                    session.displayName?.let { name ->
                        Text(name, fontSize = 11.sp, color = AppTextMuted)
                    }
                }

                if (confirmDelete) {
                    // ── Delete confirm ────────────────────────────────────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Delete?", fontSize = 12.sp, color = Red600)
                        Text("Yes", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red600,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Red600.copy(alpha = 0.1f))
                                .clickable { onDelete() }
                                .padding(horizontal = 10.dp, vertical = 4.dp))
                        Text("No", fontSize = 12.sp, color = AppTextMuted,
                            modifier = Modifier
                                .clickable { confirmDelete = false }
                                .padding(horizontal = 6.dp, vertical = 4.dp))
                    }
                } else {
                    // ── Right: three stat columns + delete ────────────────────
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SessionStat(label = "ENDS",   value = "${session.rounds.size}")
                        SessionStatDivider()
                        SessionStat(label = "ARROWS", value = "${session.totalArrows}")
                        SessionStatDivider()
                        SessionStat(
                            label = "AVG",
                            value = if (avg > 0) "%.1f".format(avg) else "—",
                            color = if (avg > 0) Amber700 else AppTextMuted,
                        )
                        Text("  ✕", fontSize = 13.sp, color = AppTextMuted,
                            modifier = Modifier.clickable { confirmDelete = true }.padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionStat(
    label: String,
    value: String,
    color: Color = AppHeaderDark,
) {
    Column(Modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = AppTextMuted,
            fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun SessionStatDivider() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .size(width = 1.dp, height = 32.dp)
            .background(AppBorderLight)
    )
}

// ── Shared trend-chart helpers ────────────────────────────────────────────────

private data class BarEntry(val mean: Float, val stdev: Float, val xLabel: String, val dayOffset: Long = 0L)

private fun stdevOf(vals: List<Double>): Float {
    if (vals.size < 2) return 0f
    val m = vals.average()
    return sqrt(vals.sumOf { (it - m) * (it - m) } / vals.size).toFloat()
}

/** Rounds v up to the nearest multiple of [step]. */
private fun ceilTo(v: Float, step: Float) = ceil(v / step) * step

@Composable
private fun TrendLineChart(
    title: String,
    entries: List<BarEntry>,
    lineColor: Color,
    yFmt: (Float) -> String,
) {
    if (entries.isEmpty()) return
    val dataTop = entries.maxOf { (it.mean + it.stdev).coerceAtLeast(it.mean) }
    val step = when {
        dataTop > 20 -> 5f
        dataTop > 10 -> 2f
        dataTop > 5  -> 1f
        dataTop > 2  -> 0.5f
        else         -> 0.2f
    }
    val yMax = ceilTo(dataTop * 1.05f, step).coerceAtLeast(step)
    val n = entries.size

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = BgWhite),
        shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppHeaderDark)
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val padL = 46f; val padR = 8f; val padT = 10f; val padB = 28f
                val cw = size.width - padL - padR
                val ch = size.height - padT - padB

                fun yScr(v: Float) = padT + ch * (1f - (v / yMax).coerceIn(0f, 1f))
                // Time-based x: position proportional to actual days since first entry
                val maxOffset = entries.maxOf { it.dayOffset }.coerceAtLeast(1L)
                fun xPos(i: Int) = when {
                    n <= 1     -> padL + cw / 2f
                    maxOffset == 1L -> padL + cw * i / (n - 1f)   // all same day → spread evenly
                    else       -> padL + cw * entries[i].dayOffset / maxOffset.toFloat()
                }

                // Y grid + labels
                val yPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(0x94, 0xA3, 0xB8)
                    textSize = 20f; isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                for (k in 0..4) {
                    val v = yMax * k / 4f
                    val y = yScr(v)
                    drawLine(AppBorderLight, Offset(padL, y), Offset(padL + cw, y), 1f)
                    drawContext.canvas.nativeCanvas.drawText(yFmt(v), padL - 5f, y + 6f, yPaint)
                }

                // ±1σ shaded band (closed polygon: upper edge L→R, lower edge R→L)
                if (n >= 2) {
                    val band = Path().apply {
                        entries.forEachIndexed { i, e ->
                            val y = yScr((e.mean + e.stdev).coerceAtMost(yMax))
                            if (i == 0) moveTo(xPos(i), y) else lineTo(xPos(i), y)
                        }
                        entries.indices.reversed().forEach { i ->
                            lineTo(xPos(i), yScr((entries[i].mean - entries[i].stdev).coerceAtLeast(0f)))
                        }
                        close()
                    }
                    drawPath(band, lineColor.copy(alpha = 0.15f))
                }

                // Mean line
                val linePath = Path().apply {
                    entries.forEachIndexed { i, e ->
                        val y = yScr(e.mean)
                        if (i == 0) moveTo(xPos(i), y) else lineTo(xPos(i), y)
                    }
                }
                drawPath(linePath, lineColor, style = Stroke(2.5f))

                // Dots + x labels
                val xPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(0x94, 0xA3, 0xB8)
                    textSize = 20f; isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                entries.forEachIndexed { i, e ->
                    val xc = xPos(i); val yc = yScr(e.mean)
                    drawCircle(lineColor, 4.5f, Offset(xc, yc))
                    drawCircle(Color.White, 2.5f, Offset(xc, yc))
                    if (n <= 8 || i % 2 == 0)
                        drawContext.canvas.nativeCanvas.drawText(e.xLabel, xc, size.height - 4f, xPaint)
                }
            }
        }
    }
}

