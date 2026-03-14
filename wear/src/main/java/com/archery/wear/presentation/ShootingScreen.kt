package com.archery.wear.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.archery.shared.ScoreZone
import com.archery.wear.presentation.theme.WatchAmber
import com.archery.wear.presentation.theme.WatchBg
import com.archery.wear.presentation.theme.WatchBtnDanger
import com.archery.wear.presentation.theme.WatchBtnPrimary
import com.archery.wear.presentation.theme.WatchCyan
import com.archery.wear.presentation.theme.WatchGreen
import com.archery.wear.presentation.theme.WatchRed
import com.archery.wear.presentation.theme.WatchTextMuted
import com.archery.wear.presentation.theme.WatchTextPrimary
import com.archery.wear.presentation.theme.WatchTextSecondary
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
    isApprox: Boolean = false,
    showQuickScore: Boolean,
    onQuickScore: (ScoreZone) -> Unit,
    onDismissQuickScore: () -> Unit,
    onManualShot: () -> Unit,
    onEnterScoring: () -> Unit,
    onEndSession: () -> Unit,
) {
    if (showQuickScore) {
        LaunchedEffect(Unit) {
            delay(4000)
            onDismissQuickScore()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(WatchBg),
    ) {
        if (showQuickScore) {
            QuickScoreOverlay(onScore = onQuickScore, onDismiss = onDismissQuickScore)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Round $roundNumber",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = WatchTextSecondary,
                )

                if (previousRoundInfo != null) {
                    Text(text = previousRoundInfo, fontSize = 10.sp, color = WatchGreen)
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (heartRate > 0) {
                    Text(text = "${heartRate.toInt()}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = WatchRed, textAlign = TextAlign.Center)
                    Text(text = "bpm", fontSize = 11.sp, color = WatchRed.copy(alpha = 0.7f))
                } else {
                    Text(text = "—", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = WatchTextMuted, textAlign = TextAlign.Center)
                    Text(text = "bpm", fontSize = 11.sp, color = WatchTextMuted)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val approxPrefix = if (isApprox) "~" else ""
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (totalScore > 0) "$approxPrefix%.0f".format(totalScore) else "—",
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WatchCyan,
                        )
                        Text(text = "total", fontSize = 9.sp, color = WatchTextMuted)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (avgPerArrow > 0) "$approxPrefix%.1f".format(avgPerArrow) else "—",
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WatchAmber,
                        )
                        Text(text = "avg", fontSize = 9.sp, color = WatchTextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onEnterScoring,
                    modifier = Modifier.fillMaxWidth(0.7f).height(32.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = WatchBtnPrimary),
                ) {
                    Text("Score Round", fontSize = 11.sp, color = WatchTextPrimary)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = onEndSession,
                    modifier = Modifier.size(width = 60.dp, height = 28.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = WatchBtnDanger),
                ) {
                    Text("End", fontSize = 11.sp, color = WatchTextPrimary)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun QuickScoreOverlay(
    onScore: (ScoreZone) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(WatchBg).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Quick Score", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = WatchTextSecondary, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScoreCircle(ScoreZone.GOLD, onScore)
            ScoreCircle(ScoreZone.RED, onScore)
            ScoreCircle(ScoreZone.BLUE, onScore)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScoreCircle(ScoreZone.BLACK, onScore)
            ScoreCircle(ScoreZone.WHITE, onScore)
            ScoreCircle(ScoreZone.MISS, onScore)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "tap or wait to skip",
            fontSize = 9.sp,
            color = WatchTextMuted,
            modifier = Modifier.clickable { onDismiss() },
        )
    }
}

@Composable
private fun ScoreCircle(zone: ScoreZone, onClick: (ScoreZone) -> Unit) {
    val bgColor = Color(zone.colorInt)
    val textColor = when (zone) {
        ScoreZone.GOLD, ScoreZone.WHITE, ScoreZone.MISS -> Color.Black
        else -> Color.White
    }
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(bgColor).clickable { onClick(zone) },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = zone.label.take(1), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}
