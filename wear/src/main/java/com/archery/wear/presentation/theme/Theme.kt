package com.archery.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

// ── Backgrounds ──────────────────────────────────────────────────────────────
val WatchBg            = Color(0xFF000000)   // pure black — fitness watch baseline
val WatchSurface       = Color(0xFF111827)   // very dark surface
val WatchSurfaceLight  = Color(0xFF1E293B)   // elevated surface

// ── Text ─────────────────────────────────────────────────────────────────────
val WatchTextPrimary   = Color(0xFFF1F5F9)
val WatchTextSecondary = Color(0xFF94A3B8)
val WatchTextMuted     = Color(0xFF64748B)

// ── Cyan ─────────────────────────────────────────────────────────────────────
val WatchCyan          = Color(0xFF67E8F9)   // softer sky, not neon
val WatchCyanDim       = Color(0xFF0891B2)
val WatchCyanBg        = Color(0xFF164E63)

// ── Amber ─────────────────────────────────────────────────────────────────────
val WatchAmber         = Color(0xFFFCD34D)   // warm gold, slightly softer
val WatchAmberDim      = Color(0xFFD97706)

// ── Red ───────────────────────────────────────────────────────────────────────
val WatchRed           = Color(0xFFFCA5A5)   // muted rose, less alarm-like
val WatchRedDim        = Color(0xFFEF4444)

// ── Green ─────────────────────────────────────────────────────────────────────
val WatchGreen         = Color(0xFF86EFAC)   // soft sage
val WatchGreenDim      = Color(0xFF16A34A)

// ── Buttons ───────────────────────────────────────────────────────────────────
val WatchBtnPrimary    = Color(0xFF155E75)   // deep cyan — less saturated
val WatchBtnConfirm    = Color(0xFF166534)   // deep green
val WatchBtnSecondary  = Color(0xFF1E293B)   // nearly-surface, very subtle
val WatchBtnDanger     = Color(0xFF7F1D1D)   // deep red, less brown

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
