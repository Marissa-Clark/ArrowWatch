package com.archery.wear

import com.archery.shared.ScoreZone

/** Internal watch-side session data. Immutable for clean StateFlow updates. */

data class WatchShot(
    val number: Int,
    val holdMs: Long = 0L,
    val heartRate: Float = 0f,
    val quickZone: ScoreZone? = null,
    val finalZone: ScoreZone? = null,
    val finalScore: Int? = null,
) {
    val effectiveZone: ScoreZone? get() = finalZone ?: quickZone
    val effectiveScore: Float
        get() = finalScore?.toFloat()
            ?: finalZone?.defaultScore
            ?: quickZone?.defaultScore
            ?: 0f
    val isDns: Boolean get() = finalZone == ScoreZone.DNS
}

data class WatchRound(
    val number: Int,
    val shots: List<WatchShot> = emptyList(),
    val confirmedTotal: Float? = null,
    val totalConfirmed: Boolean = false,
) {
    val approxScore: Float
        get() = shots.filter { !it.isDns }.sumOf { it.effectiveScore.toDouble() }.toFloat()
    val displayScore: Float?
        get() = if (totalConfirmed) confirmedTotal
                else if (approxScore > 0) approxScore
                else null
}

data class WatchSession(
    val arrowsPerRound: Int,
    val rounds: List<WatchRound> = emptyList(),
    val heartRateSamples: List<Pair<Long, Float>> = emptyList(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
) {
    val currentRound: WatchRound? get() = rounds.lastOrNull()
    val totalShots: Int get() = rounds.sumOf { it.shots.size }
    val nonDnsShots: Int get() = rounds.sumOf { r -> r.shots.count { !it.isDns } }
    val totalScore: Float get() = rounds.sumOf { (it.displayScore ?: 0f).toDouble() }.toFloat()
    val avgPerArrow: Float get() = if (nonDnsShots > 0) totalScore / nonDnsShots else 0f

    fun avgHeartRate(): Float {
        val samples = heartRateSamples.map { it.second }
        return if (samples.isEmpty()) 0f else samples.average().toFloat()
    }

    fun avgHoldDuration(): Long {
        val holds = rounds.flatMap { it.shots }.map { it.holdMs }.filter { it > 0 }
        return if (holds.isEmpty()) 0L else holds.average().toLong()
    }

    /**
     * True when any completed round's score is based on zone midpoint estimates
     * rather than a confirmed total entered via the stepper.
     */
    val isScoreApprox: Boolean
        get() = rounds.dropLast(1).any { !it.totalConfirmed && it.approxScore > 0f }
}
