package com.archery.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.archery.shared.ScoreZone
import com.archery.wear.WatchRound
import com.archery.wear.presentation.theme.WatchAmber
import com.archery.wear.presentation.theme.WatchBg
import com.archery.wear.presentation.theme.WatchBtnConfirm
import com.archery.wear.presentation.theme.WatchBtnSecondary
import com.archery.wear.presentation.theme.WatchCyan
import com.archery.wear.presentation.theme.WatchRed
import com.archery.wear.presentation.theme.WatchSurfaceLight
import com.archery.wear.presentation.theme.WatchTextMuted
import com.archery.wear.presentation.theme.WatchTextPrimary
import com.archery.wear.presentation.theme.WatchTextSecondary

/**
 * Sum all scored (non-DNS) shots. Pass [overrideIdx]/[overrideZone] to include
 * a just-scored arrow whose zone hasn't been committed to the VM yet.
 */
private fun roundColorTotal(
    round: WatchRound?,
    overrideIdx: Int = -1,
    overrideZone: ScoreZone? = null,
): Int =
    round?.shots?.withIndex()?.sumOf { (i, shot) ->
        val z = if (i == overrideIdx) overrideZone else (shot.finalZone ?: shot.quickZone)
        if (z == ScoreZone.DNS) 0.0 else z?.defaultScore?.toDouble() ?: 0.0
    }?.toInt() ?: 0

@Composable
fun ScoreScreen(
    round: WatchRound?,
    onScoreArrow: (Int, ScoreZone) -> Unit,
    onAddArrow: () -> Unit,
    onRemoveArrow: (Int) -> Unit,
    onSetTotal: (Float) -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    onCancelScoring: () -> Unit = {},
) {
    val totalArrows = round?.shots?.size ?: 0
    val focusRequester = remember { FocusRequester() }
    val firstUnscored = round?.shots?.indexOfFirst { it.finalZone == null }
    var currentArrow by remember { mutableIntStateOf((firstUnscored ?: 0).coerceAtLeast(0)) }
    var showTotalEntry by remember { mutableStateOf(false) }
    var enteredTotal by remember { mutableIntStateOf(0) }

    LaunchedEffect(totalArrows) {
        if (totalArrows > 0) currentArrow = currentArrow.coerceIn(0, totalArrows - 1)
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun scoreAndAdvance(arrowIdx: Int, zone: ScoreZone) {
        onScoreArrow(arrowIdx, zone)
        val remainingUnscored = round?.shots?.withIndex()?.count { (i, shot) ->
            i != arrowIdx && shot.finalZone == null
        } ?: 0
        if (remainingUnscored == 0 && totalArrows > 0) {
            // round state hasn't been updated in the VM yet — supply the just-scored zone manually
            enteredTotal = roundColorTotal(round, overrideIdx = arrowIdx, overrideZone = zone)
            showTotalEntry = true
        } else {
            val next = round?.shots?.let { shots ->
                ((arrowIdx + 1) until totalArrows).firstOrNull { shots[it].finalZone == null }
            }
            if (next != null) currentArrow = next
        }
    }

    if (!showTotalEntry) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WatchBg)
                .onRotaryScrollEvent { event ->
                    // Crown forward → finish round; crown backward → cancel back to shooting
                    if (event.verticalScrollPixels > 0) { onFinish(); true }
                    else { onCancelScoring(); true }
                }
                .focusRequester(focusRequester)
                .focusable(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp, start = 12.dp, end = 12.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (totalArrows > 0) {
                    val currentZone = round?.shots?.getOrNull(currentArrow)?.finalZone

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        round?.shots?.forEachIndexed { idx, shot ->
                            val z = shot.finalZone ?: shot.quickZone
                            val isActive = idx == currentArrow
                            val isDns = z == ScoreZone.DNS
                            Box(
                                modifier = Modifier
                                    .size(if (isActive) 16.dp else 10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isDns) Color(0xFF555555)
                                        else if (z != null) Color(z.colorInt)
                                        else WatchSurfaceLight
                                    )
                                    .then(if (isActive) Modifier.border(2.dp, WatchCyan, CircleShape) else Modifier)
                                    .clickable { currentArrow = idx }
                                    .then(if (isDns) Modifier.drawBehind {
                                        drawLine(WatchRed, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 2f)
                                    } else Modifier),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (z in listOf(ScoreZone.GOLD, ScoreZone.RED, ScoreZone.BLUE)) {
                            BigScoreButton(z, selected = currentZone == z) { scoreAndAdvance(currentArrow, z) }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (z in listOf(ScoreZone.BLACK, ScoreZone.WHITE, ScoreZone.MISS)) {
                            BigScoreButton(z, selected = currentZone == z) { scoreAndAdvance(currentArrow, z) }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DnsButton(selected = currentZone == ScoreZone.DNS) { scoreAndAdvance(currentArrow, ScoreZone.DNS) }
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(WatchSurfaceLight)
                                .clickable { onSkip() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Skip", fontSize = 10.sp, color = WatchTextMuted)
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WatchBg)
                .onRotaryScrollEvent { event ->
                    val delta = if (event.verticalScrollPixels > 0) 1 else -1
                    enteredTotal = (enteredTotal + delta).coerceIn(0, 300)
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "← Round Total",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = WatchTextSecondary,
                modifier = Modifier.clickable { showTotalEntry = false },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { enteredTotal = (enteredTotal - 1).coerceAtLeast(0) },
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = WatchBtnSecondary),
                ) { Text("−", fontSize = 18.sp, color = WatchTextPrimary) }

                Text(text = "$enteredTotal", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = WatchAmber)

                Button(
                    onClick = { enteredTotal = (enteredTotal + 1).coerceAtMost(300) },
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = WatchBtnSecondary),
                ) { Text("+", fontSize = 18.sp, color = WatchTextPrimary) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier.size(44.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = WatchBtnSecondary),
                ) { Text("Skip", fontSize = 10.sp, color = WatchTextSecondary) }

                Button(
                    onClick = { onSetTotal(enteredTotal.toFloat()); onFinish() },
                    modifier = Modifier.size(48.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = WatchBtnConfirm),
                ) { Text("✓", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = WatchTextPrimary) }
            }
        }
    }
}

@Composable
private fun BigScoreButton(zone: ScoreZone, selected: Boolean = false, onClick: () -> Unit) {
    val textColor = when (zone) {
        ScoreZone.GOLD, ScoreZone.WHITE, ScoreZone.MISS -> Color.Black
        else -> Color.White
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(zone.colorInt))
            .then(if (selected) Modifier.border(3.dp, WatchCyan, CircleShape) else Modifier)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = zone.label.take(1), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

@Composable
private fun DnsButton(selected: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0xFF555555))
            .then(if (selected) Modifier.border(3.dp, WatchCyan, CircleShape) else Modifier)
            .clickable { onClick() }
            .drawBehind {
                drawCircle(WatchRed, radius = size.minDimension / 2 - 3f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
                drawLine(WatchRed, Offset(size.width * 0.2f, size.height * 0.8f),
                    Offset(size.width * 0.8f, size.height * 0.2f), strokeWidth = 2.5f)
            },
    )
}
