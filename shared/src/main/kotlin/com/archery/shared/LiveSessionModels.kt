package com.archery.shared

/**
 * Live session state, built on the phone from watch MessageClient events.
 * Mirrors the watch state machine in real time.
 */

enum class WatchPhase { IDLE, SHOOTING, SCORING, SUMMARY }

data class LiveSession(
    val arrowsPerRound: Int,
    val rounds: List<LiveRound> = emptyList(),
    val currentPhase: WatchPhase = WatchPhase.SHOOTING,
    val isEnded: Boolean = false,
    val startTimeMs: Long = System.currentTimeMillis(),
) {
    val currentRound: Int get() = rounds.size.coerceAtLeast(1)
    val totalScore: Float get() = rounds.sumOf { (it.confirmedScore ?: it.detectedScore ?: 0f).toDouble() }.toFloat()
}

data class LiveRound(
    val number: Int,
    val arrows: List<LiveArrow> = emptyList(),
    val latestHoldMs: Long? = null,
    val latestHeartRate: Float? = null,
    /** Sum of arrow scores detected by the watch. */
    val detectedScore: Float? = null,
    /** User-confirmed round total. */
    val confirmedScore: Float? = null,
    val isComplete: Boolean = false,
) {
    val displayScore: Float? get() = confirmedScore ?: detectedScore
}

data class LiveArrow(
    val shotIndex: Int,
    /** Color tapped immediately after shot (quick score). */
    val quickZone: ScoreZone? = null,
    /** Final zone after detailed scoring. */
    val zone: ScoreZone? = null,
    val score: Float? = null,
    val holdMs: Long? = null,
    val heartRate: Float? = null,
) {
    val displayZone: ScoreZone? get() = zone ?: quickZone
    val isScored: Boolean get() = zone != null || score != null
}
