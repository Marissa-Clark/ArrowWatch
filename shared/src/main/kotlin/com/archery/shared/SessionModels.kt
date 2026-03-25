package com.archery.shared

import java.time.LocalDateTime

/**
 * Display models produced by SessionParser (CSV → structured data).
 * These are used by both the phone UI and as the basis for Room entities.
 */

data class SessionSummary(
    val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val date: LocalDateTime,
    val durationSec: Long,
    val rounds: List<RoundSummary>,
    val zoneCounts: Map<ScoreZone, Int>,
    val avgHeartRate: Float?,
    val avgHoldMs: Long?,
    val isArchived: Boolean = false,
    /** User-supplied name; null = show default "Practice Session". */
    val displayName: String? = null,
    /** True when the user has confirmed detection caught everything — suppresses re-run prompts. */
    val analyticsLocked: Boolean = false,
) {
    val totalArrows: Int get() = rounds.sumOf { it.arrows.size }
    val totalNonDnsArrows: Int get() = rounds.sumOf { r -> r.arrows.count { it.zone != ScoreZone.DNS } }
    /** Sum of per-round displayScores (live from arrows when unconfirmed). */
    val displayScore: Float get() = rounds.sumOf { it.displayScore.toDouble() }.toFloat()
    /** Per-arrow average excluding DNS shots. */
    val avgPerArrow: Float get() = if (totalNonDnsArrows > 0) displayScore / totalNonDnsArrows else 0f
}

data class RoundSummary(
    val number: Int,
    val arrows: List<ArrowScore>,
    /** Algorithm-detected total (from approx_score event or sum of arrow scores). */
    val detectedScore: Float,
    /** User-confirmed total (from actual_score or edit_round_total event). */
    val confirmedScore: Float?,
    val avgHeartRate: Float?,
    val avgHoldMs: Long?,
) {
    /**
     * Confirmed total takes highest priority.
     * If unconfirmed but arrows are present, sum them live (excluding DNS) so
     * any per-arrow edits are immediately reflected without re-parsing the CSV.
     * Falls back to the stored detectedScore for sessions without arrow data.
     */
    val displayScore: Float get() {
        if (confirmedScore != null) return confirmedScore
        if (arrows.isNotEmpty()) return arrows
            .filter { it.zone != ScoreZone.DNS }
            .sumOf { it.score.toDouble() }
            .toFloat()
        return detectedScore
    }
}

data class ArrowScore(
    val shotNumber: Int,
    val zone: ScoreZone,
    val score: Float,
    /** True if from final_score/edit_score; false if from quick_score. */
    val isFinal: Boolean,
)
