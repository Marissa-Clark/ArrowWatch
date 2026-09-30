package com.archery.sync

import com.archery.analytics.DetectedShot
import com.archery.shared.LiveArrow
import com.archery.shared.LiveRound
import com.archery.shared.LiveSession
import com.archery.shared.ScoreZone
import com.archery.shared.WatchPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Singleton live session state, updated by WatchListenerService as watch messages arrive.
 */
object LiveSessionRepository {

    private val _session = MutableStateFlow<LiveSession?>(null)
    val session: StateFlow<LiveSession?> = _session.asStateFlow()

    /** Per-round detected shots, populated as each round's sensor data is received. */
    private val _roundAnalytics = MutableStateFlow<Map<Int, List<DetectedShot>>>(emptyMap())
    val roundAnalytics: StateFlow<Map<Int, List<DetectedShot>>> = _roundAnalytics.asStateFlow()

    fun onSessionStart(arrowsPerRound: Int) {
        _session.value = LiveSession(arrowsPerRound = arrowsPerRound)
    }

    fun onPhaseChange(phase: String) {
        val s = _session.value ?: return
        val watchPhase = WatchPhase.entries.find { it.name == phase } ?: return
        _session.value = s.copy(currentPhase = watchPhase)
    }

    fun onShot(roundNumber: Int, shotNumber: Int, holdMs: Long, heartRate: Float, quickZone: ScoreZone?) {
        val s = _session.value ?: return
        val arrow = LiveArrow(
            shotIndex = shotNumber - 1,
            quickZone = quickZone,
            holdMs = holdMs.takeIf { it > 0 },
            heartRate = heartRate.takeIf { it > 0 },
        )
        _session.value = s.withArrow(roundNumber, arrow)
    }

    fun onArrowScored(roundNumber: Int, shotIndex: Int, zoneName: String, score: Float) {
        val s = _session.value ?: return
        val zone = ScoreZone.fromLabel(zoneName)
        val existing = s.findArrow(roundNumber, shotIndex) ?: LiveArrow(shotIndex)
        _session.value = s.withArrow(roundNumber, existing.copy(zone = zone, score = score))
    }

    fun onRoundTotalSet(roundNumber: Int, total: Float) {
        val s = _session.value ?: return
        _session.value = s.updateRound(roundNumber) { it.copy(confirmedScore = total) }
    }

    fun onRoundComplete(roundNumber: Int, approxScore: Float, actualScore: Float?) {
        val s = _session.value ?: return
        _session.value = s.updateRound(roundNumber) {
            it.copy(
                detectedScore = approxScore,
                confirmedScore = actualScore ?: it.confirmedScore,
                isComplete = true,
            )
        }
    }

    fun onSessionEnd() {
        _session.value = _session.value?.copy(isEnded = true)
    }

    fun onRoundAnalytics(round: Int, shots: List<DetectedShot>) {
        _roundAnalytics.update { it + (round to shots) }
    }

    /** Edit an arrow in a completed round locally on the phone (watch has moved on). */
    fun editCompletedArrow(roundNumber: Int, shotIndex: Int, zoneName: String, score: Float) {
        val s = _session.value ?: return
        val zone = ScoreZone.entries.find { it.name == zoneName } ?: ScoreZone.fromLabel(zoneName)
        val existing = s.findArrow(roundNumber, shotIndex) ?: LiveArrow(shotIndex)
        _session.value = s.withArrow(roundNumber, existing.copy(zone = zone, score = score))
    }

    fun clear() {
        _session.value = null
        _roundAnalytics.value = emptyMap()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun LiveSession.withArrow(roundNumber: Int, arrow: LiveArrow): LiveSession {
        val idx = roundNumber - 1
        val rounds = this.rounds.toMutableList()
        while (rounds.size <= idx) rounds.add(LiveRound(number = rounds.size + 1))
        val round = rounds[idx]
        val arrows = round.arrows.toMutableList()
        val pos = arrows.indexOfFirst { it.shotIndex == arrow.shotIndex }
        if (pos >= 0) arrows[pos] = arrow else arrows.add(arrow)
        rounds[idx] = round.copy(
            arrows = arrows.sortedBy { it.shotIndex },
            latestHeartRate = arrow.heartRate ?: round.latestHeartRate,
            latestHoldMs = arrow.holdMs ?: round.latestHoldMs,
        )
        return copy(rounds = rounds)
    }

    private fun LiveSession.findArrow(roundNumber: Int, shotIndex: Int): LiveArrow? =
        rounds.getOrNull(roundNumber - 1)?.arrows?.find { it.shotIndex == shotIndex }

    private fun LiveSession.updateRound(roundNumber: Int, transform: (LiveRound) -> LiveRound): LiveSession {
        val idx = roundNumber - 1
        val rounds = this.rounds.toMutableList()
        if (idx < 0 || idx >= rounds.size) return this
        rounds[idx] = transform(rounds[idx])
        return copy(rounds = rounds)
    }
}
