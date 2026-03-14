package com.archery.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

// ── Backgrounds ──────────────────────────────────────────────────────────────
val WatchBg            = Color(0xFF0F172A)
val WatchSurface       = Color(0xFF1E293B)
val WatchSurfaceLight  = Color(0xFF334155)

// ── Text ─────────────────────────────────────────────────────────────────────
val WatchTextPrimary   = Color(0xFFF1F5F9)
val WatchTextSecondary = Color(0xFF94A3B8)
val WatchTextMuted     = Color(0xFF64748B)

// ── Cyan ─────────────────────────────────────────────────────────────────────
val WatchCyan          = Color(0xFF22D3EE)
val WatchCyanDim       = Color(0xFF0891B2)
val WatchCyanBg        = Color(0xFF164E63)

// ── Amber ─────────────────────────────────────────────────────────────────────
val WatchAmber         = Color(0xFFFBBF24)
val WatchAmberDim      = Color(0xFFD97706)

// ── Red ───────────────────────────────────────────────────────────────────────
val WatchRed           = Color(0xFFF87171)
val WatchRedDim        = Color(0xFFDC2626)

// ── Green ─────────────────────────────────────────────────────────────────────
val WatchGreen         = Color(0xFF4ADE80)
val WatchGreenDim      = Color(0xFF16A34A)

// ── Buttons ───────────────────────────────────────────────────────────────────
val WatchBtnPrimary    = Color(0xFF0E7490)
val WatchBtnConfirm    = Color(0xFF15803D)
val WatchBtnSecondary  = Color(0xFF334155)
val WatchBtnDanger     = Color(0xFF92400E)

private val WatchColors = Colors(
    primary = WatchCyan,
    primaryVariant = WatchCyanDim,
    secondary = WatchAmber,
    secondaryVariant = WatchAmberDim,
    background = WatchBg,
    surface = WatchSurface,
    error = WatchRed,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = WatchTextPrimary,
    onSurface = WatchTextPrimary,
    onError = Color.Black,
)

@Composable
fun ArcheryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = WatchColors, content = content)
}
