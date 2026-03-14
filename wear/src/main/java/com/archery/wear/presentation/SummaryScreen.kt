package com.archery.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.archery.shared.ScoreZone
import com.archery.wear.WatchSession
import com.archery.wear.presentation.theme.WatchAmber
import com.archery.wear.presentation.theme.WatchBg
import com.archery.wear.presentation.theme.WatchBtnConfirm
import com.archery.wear.presentation.theme.WatchCyan
import com.archery.wear.presentation.theme.WatchRed
import com.archery.wear.presentation.theme.WatchSurface
import com.archery.wear.presentation.theme.WatchTextPrimary
import com.archery.wear.presentation.theme.WatchTextSecondary
import kotlinx.coroutines.launch

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
    ScoreZone.BLACK, ScoreZone.WHITE, ScoreZone.MISS, ScoreZone.DNS,
)

@Composable
fun SummaryScreen(
    session: WatchSession?,
    onNewSession: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchBg)
            .onRotaryScrollEvent { event ->
                scope.launch {
                    val target = (scrollState.value + event.verticalScrollPixels.toInt()).coerceIn(0, scrollState.maxValue)
                    scrollState.scrollTo(target)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .verticalScroll(scrollState)
            .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Complete", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WatchCyan, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(6.dp))

        if (session != null) {
            // Row 1: Score | Rounds
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniStatTile(Modifier.weight(1f), "Score",
                    if (session.totalScore > 0) "%.0f".format(session.totalScore) else "—", WatchAmber)
                MiniStatTile(Modifier.weight(1f), "Rounds", "${session.rounds.size}", WatchCyan)
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Avg/Arrow | Avg HR
            val avgHr = session.avgHeartRate()
            val avgPerArrow = session.avgPerArrow
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniStatTile(Modifier.weight(1f), "Avg/Arrow",
                    if (avgPerArrow > 0f) "%.1f".format(avgPerArrow) else "—", WatchAmber)
                if (avgHr > 0f) {
                    MiniStatTile(Modifier.weight(1f), "Avg HR", "${avgHr.toInt()}", WatchRed, "bpm")
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }

            // Zone distribution bar
            val allShots = session.rounds.flatMap { it.shots }
            val zoneCounts = allShots.groupingBy { it.effectiveZone ?: ScoreZone.MISS }.eachCount()
            val totalWithZone = allShots.count { it.effectiveZone != null }
            if (totalWithZone > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                ) {
                    ZONE_ORDER.forEach { zone ->
                        val count = zoneCounts[zone] ?: 0
                        if (count > 0) {
                            Box(
                                Modifier
                                    .weight(count.toFloat())
                                    .fillMaxHeight()
                                    .background(ZONE_COLORS[zone] ?: Color.Gray)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Per-round list
            session.rounds.forEachIndexed { i, round ->
                val scoreText = round.displayScore?.let { "%.0f".format(it) } ?: "—"
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "R${i + 1}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = WatchCyan)
                    Text(text = "${round.shots.size} arrows", fontSize = 10.sp, color = WatchTextSecondary)
                    Text(text = "$scoreText pts", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = WatchAmber)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onNewSession,
            modifier = Modifier.fillMaxWidth(0.7f).height(36.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = WatchBtnConfirm),
        ) {
            Text("New Session", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WatchTextPrimary)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun MiniStatTile(modifier: Modifier, label: String, value: String, valueColor: Color, unit: String = "") {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(WatchSurface).padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = WatchTextSecondary)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = valueColor)
                if (unit.isNotEmpty()) {
                    Text(text = unit, fontSize = 9.sp, color = WatchTextSecondary, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
    }
}
