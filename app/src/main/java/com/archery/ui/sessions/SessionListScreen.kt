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
import com.archery.shared.SessionSummary
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val BgPage        = Color(0xFFF8FAFC)
private val BgWhite       = Color.White
private val HeaderDarker  = Color(0xFF0F172A)
private val LiveGreen     = Color(0xFF16A34A)
private val Amber700      = Color(0xFFB45309)
private val Red600        = Color(0xFFDC2626)
private val Cyan600       = Color(0xFF0891B2)
private val Cyan800       = Color(0xFF155E75)
private val Amber400      = Color(0xFFFBBF24)
private val TextPrimary   = Color(0xFF1E293B)
private val TextSecondary = Color(0xFF475569)
private val TextMuted     = Color(0xFF94A3B8)
private val TextSlate300  = Color(0xFFCBD5E1)
private val BorderLight   = Color(0xFFE2E8F0)

@Composable
fun SessionListScreen(
    onSessionClick: (Long) -> Unit,
    onLiveSessionClick: () -> Unit,
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
        Box(Modifier.fillMaxSize().background(BgPage), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No sessions yet", fontSize = 18.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Text("Sessions will appear here after you shoot on your watch",
                    fontSize = 14.sp, color = TextMuted)
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(Cyan600, Cyan800)))
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
        modifier = Modifier.fillMaxSize().background(BgPage).padding(horizontal = 16.dp),
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
                            .background(TextMuted.copy(alpha = 0.08f))
                            .border(1.dp, TextMuted.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .clickable { vm.dismissLiveSession() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Dismiss (phone)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = TextMuted)
                    }
                }
            }
        }

        if (!hasLive) {
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(Cyan600, Cyan800)))
                        .clickable { vm.startSession() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Start Session", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White)
                }
            }
        }

        if (scoredSessions.size >= 2) {
            item { ScoreTrendChart(scoredSessions.reversed()) }
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
                    fontSize = 13.sp, color = Cyan600,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { showArchived = !showArchived }
                        .padding(vertical = 12.dp),
                )
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
        colors = CardDefaults.cardColors(containerColor = HeaderDarker),
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
                Text("Shots", fontSize = 11.sp, color = TextSlate300)
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
            Text(label, fontSize = 10.sp, color = TextSlate300, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
                if (unit.isNotEmpty()) Text(unit, fontSize = 10.sp, color = TextSlate300,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun SessionCard(session: SessionSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a")
    val avg = session.avgPerArrow
    val durationMin = (session.durationSec / 60).toInt()
    val name = session.displayName ?: "Practice Session"
    var confirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                    modifier = Modifier.weight(1f))
                if (!confirmDelete) {
                    if (avg > 0) {
                        val pfx = if (session.rounds.all { it.confirmedScore != null }) "" else "~"
                        Text("$pfx%.1f / arrow".format(avg), fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, color = Amber700,
                            modifier = Modifier.padding(end = 8.dp))
                    }
                    // Small delete icon
                    Text("✕", fontSize = 13.sp, color = TextMuted,
                        modifier = Modifier
                            .clickable { confirmDelete = true }
                            .padding(4.dp))
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(session.date.format(formatter), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(3.dp))
            if (confirmDelete) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Delete this session?", fontSize = 12.sp, color = Red600,
                        modifier = Modifier.weight(1f))
                    Text("Yes", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red600,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Red600.copy(alpha = 0.1f))
                            .clickable { onDelete() }
                            .padding(horizontal = 10.dp, vertical = 4.dp))
                    Text("No", fontSize = 12.sp, color = TextMuted,
                        modifier = Modifier
                            .clickable { confirmDelete = false }
                            .padding(horizontal = 6.dp, vertical = 4.dp))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (session.isArchived) Text("Archived", fontSize = 12.sp, color = TextMuted)
                    Text("${session.rounds.size} rounds", fontSize = 12.sp, color = TextSecondary)
                    Text("${session.totalArrows} arrows", fontSize = 12.sp, color = TextSecondary)
                    if (durationMin > 0) Text("${durationMin}min", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun ScoreTrendChart(sessions: List<SessionSummary>) {
    val scores = sessions.map { it.avgPerArrow }
    val maxS = scores.max(); val minS = scores.min()
    val range = (maxS - minS).coerceAtLeast(1f)

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = BgWhite),
        shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("Avg Score / Arrow", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val w = size.width; val h = size.height
                val pt = 8f; val pb = 24f; val ch = h - pt - pb
                for (i in 0..3) {
                    val y = pt + ch * (1 - i / 3f)
                    drawLine(BorderLight, Offset(0f, y), Offset(w, y), 1f)
                }
                val pts = scores.mapIndexed { i, s ->
                    Offset(
                        if (scores.size > 1) w * i / (scores.size - 1f) else w / 2f,
                        pt + ch * (1 - (s - minS) / range),
                    )
                }
                val area = Path().apply {
                    moveTo(pts.first().x, pt + ch)
                    pts.forEach { lineTo(it.x, it.y) }
                    lineTo(pts.last().x, pt + ch); close()
                }
                drawPath(area, Cyan600.copy(0.15f))
                val line = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                }
                drawPath(line, Cyan600, style = Stroke(2.5f))
                pts.forEach { drawCircle(Cyan600, 4f, it); drawCircle(BgWhite, 2f, it) }
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(0x47, 0x55, 0x69)
                    textSize = 22f; isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                scores.forEachIndexed { i, s ->
                    drawContext.canvas.nativeCanvas.drawText("%.1f".format(s),
                        pts[i].x, pt + ch + pb - 4f, paint)
                }
            }
        }
    }
}
