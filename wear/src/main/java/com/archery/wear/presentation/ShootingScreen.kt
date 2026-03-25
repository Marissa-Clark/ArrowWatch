package com.archery.wear.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.archery.wear.presentation.theme.WatchAmber
import com.archery.wear.presentation.theme.WatchBg
import com.archery.wear.presentation.theme.WatchBtnDanger
import com.archery.wear.presentation.theme.WatchBtnPrimary
import com.archery.wear.presentation.theme.WatchCyan
import com.archery.wear.presentation.theme.WatchGreenDim
import com.archery.wear.presentation.theme.WatchRed
import com.archery.wear.presentation.theme.WatchSurfaceLight
import com.archery.wear.presentation.theme.WatchTextMuted
import com.archery.wear.presentation.theme.WatchTextPrimary
import com.archery.wear.presentation.theme.WatchTextSecondary

@Composable
fun ShootingScreen(
    shotCount: Int,
    arrowsPerRound: Int,
    roundNumber: Int,
    heartRate: Float,
    previousRoundInfo: String?,
    totalScore: Float,
    avgPerArrow: Float,
    walkingSteps: Int,
    isApprox: Boolean = false,
    onEnterScoring: () -> Unit,
    onEndSession: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(WatchBg),
    ) {
        Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Round $roundNumber",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WatchTextSecondary,
                    letterSpacing = 0.3.sp,
                )

                Spacer(modifier = Modifier.height(2.dp))

                if (previousRoundInfo != null) {
                    Text(text = previousRoundInfo, fontSize = 9.sp, color = WatchGreenDim, letterSpacing = 0.2.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // HR
                Text(
                    text = if (heartRate > 0) "${heartRate.toInt()}" else "—",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (heartRate > 0) WatchRed else WatchTextMuted,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "BPM",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = WatchTextMuted,
                    letterSpacing = 1.sp,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Score stats
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val prefix = if (isApprox) "~" else ""
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
                        Text(
                            text = if (totalScore > 0) "$prefix%.0f".format(totalScore) else "—",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = WatchCyan,
                        )
                        Text(text = "TOTAL", fontSize = 8.sp, color = WatchTextMuted, letterSpacing = 0.8.sp)
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(WatchSurfaceLight))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
                        Text(
                            text = if (avgPerArrow > 0) "$prefix%.1f".format(avgPerArrow) else "—",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = WatchAmber,
                        )
                        Text(text = "AVG", fontSize = 8.sp, color = WatchTextMuted, letterSpacing = 0.8.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onEnterScoring,
                    modifier = Modifier.fillMaxWidth(0.75f).height(32.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = WatchBtnPrimary),
                ) {
                    Text("Score Round", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = WatchTextPrimary)
                }

                Spacer(modifier = Modifier.height(5.dp))

                Button(
                    onClick = onEndSession,
                    modifier = Modifier.size(width = 60.dp, height = 26.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = WatchBtnDanger),
                ) {
                    Text("End", fontSize = 10.sp, color = WatchTextPrimary)
                }

                Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
