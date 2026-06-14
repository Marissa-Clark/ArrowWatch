package com.archery.wear.presentation

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.archery.wear.presentation.theme.WatchAmber
import com.archery.wear.presentation.theme.WatchBg
import com.archery.wear.presentation.theme.WatchBtnConfirm
import com.archery.wear.presentation.theme.WatchBtnSecondary
import com.archery.wear.presentation.theme.WatchCyan
import com.archery.wear.presentation.theme.WatchTextPrimary
import com.archery.wear.presentation.theme.WatchTextSecondary

@Composable
fun StartScreen(
    arrowsPerRound: Int,
    onArrowsChanged: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .onRotaryScrollEvent { event ->
                    val delta = if (event.verticalScrollPixels > 0) 1 else -1
                    onArrowsChanged((arrowsPerRound + delta).coerceIn(1, 12))
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Arrows / End",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = WatchTextSecondary,
                letterSpacing = 0.5.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Button(
                    onClick = { onArrowsChanged((arrowsPerRound - 1).coerceIn(1, 12)) },
                    modifier = Modifier.size(30.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = WatchBtnSecondary),
                ) {
                    Text("−", fontSize = 16.sp, color = WatchTextPrimary)
                }

                Text(
                    text = "$arrowsPerRound",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = WatchAmber,
                    textAlign = TextAlign.Center,
                )

                Button(
                    onClick = { onArrowsChanged((arrowsPerRound + 1).coerceIn(1, 12)) },
                    modifier = Modifier.size(30.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = WatchBtnSecondary),
                ) {
                    Text("+", fontSize = 16.sp, color = WatchTextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(0.65f).height(36.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = WatchBtnConfirm),
            ) {
                Text("Start", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = WatchTextPrimary)
            }
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true, name = "Start screen")
@Composable
private fun StartScreenPreview() {
    com.archery.wear.presentation.theme.ArcheryTheme {
        StartScreen(arrowsPerRound = 3, onArrowsChanged = {}, onStart = {})
    }
}
