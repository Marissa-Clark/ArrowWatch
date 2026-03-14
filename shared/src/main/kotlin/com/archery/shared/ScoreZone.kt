package com.archery.shared

/**
 * Archery scoring zones, ordered highest to lowest.
 * Used by both watch (quick score UI) and phone (parsing, display).
 */
enum class ScoreZone(
    val label: String,
    val colorInt: Int,
    val defaultScore: Float,
) {
    GOLD("GOLD",  0xFFFFD700.toInt(), 9.5f),
    RED("RED",   0xFFEF4444.toInt(), 7.5f),
    BLUE("BLUE", 0xFF3B82F6.toInt(), 5.5f),
    BLACK("BLACK", 0xFF1E293B.toInt(), 3.5f),
    WHITE("WHITE", 0xFFF8FAFC.toInt(), 1.5f),
    MISS("MISS", 0xFF94A3B8.toInt(), 0.5f),
    DNS("DNS",   0xFF64748B.toInt(), 0f);

    companion object {
        fun fromLabel(label: String): ScoreZone =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: MISS
    }
}
