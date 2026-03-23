package com.archery.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archery.shared.ScoreZone
import com.archery.shared.WatchPhase
import com.archery.wear.data.SessionLogger
import com.archery.wear.sensor.WatchSensorManager
import com.archery.wear.sync.LiveSyncManager
import com.archery.wear.sync.PhoneCommandBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionViewModel : ViewModel() {

    // ── Config ───────────────────────────────────────────────────────────────

    private val _arrowsPerRound = MutableStateFlow(3)
    val arrowsPerRound: StateFlow<Int> = _arrowsPerRound.asStateFlow()

    // ── Session state ────────────────────────────────────────────────────────

    private val _session = MutableStateFlow<WatchSession?>(null)
    val session: StateFlow<WatchSession?> = _session.asStateFlow()

    private val _phase = MutableStateFlow(WatchPhase.IDLE)
    val phase: StateFlow<WatchPhase> = _phase.asStateFlow()

    private val _showQuickScore = MutableStateFlow(false)
    val showQuickScore: StateFlow<Boolean> = _showQuickScore.asStateFlow()

    private val _previousRoundInfo = MutableStateFlow<String?>(null)
    val previousRoundInfo: StateFlow<String?> = _previousRoundInfo.asStateFlow()

    // ── External dependencies (set by Activity) ──────────────────────────────

    var sensorManager: WatchSensorManager? = null
    var logger: SessionLogger? = null
    var liveSyncManager: LiveSyncManager? = null

    // ── Init ─────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            PhoneCommandBus.commands.collect { cmd -> handlePhoneCommand(cmd) }
        }
    }

    // ── Config ───────────────────────────────────────────────────────────────

    fun setArrowsPerRound(count: Int) {
        _arrowsPerRound.value = count.coerceIn(1, 12)
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    fun startSession() {
        val session = WatchSession(
            arrowsPerRound = _arrowsPerRound.value,
            rounds = listOf(WatchRound(number = 1)),
        )
        _session.value = session
        _previousRoundInfo.value = null
        _phase.value = WatchPhase.SHOOTING
        logger?.startSession()
        liveSyncManager?.sendSessionStart(_arrowsPerRound.value)
    }

    fun endSession() {
        val session = _session.value ?: return
        val trimmed = if (session.currentRound?.shots?.isEmpty() == true)
            session.copy(rounds = session.rounds.dropLast(1), endTime = System.currentTimeMillis())
        else
            session.copy(endTime = System.currentTimeMillis())
        _session.value = trimmed
        _phase.value = WatchPhase.SUMMARY
        liveSyncManager?.sendSessionEnd()
        // Stop the logger AFTER sending MSG_SESSION_END so the phone transitions
        // its live UI immediately. Once the CSV is fully flushed and closed,
        // send it as a Data Layer asset for the analytics screen.
        val sync = liveSyncManager
        logger?.stopSessionAndThen { file, startTimeMs ->
            if (file != null) sync?.sendSessionCsv(file, startTimeMs)
        }
    }

    fun newSession() {
        _session.value = null
        _phase.value = WatchPhase.IDLE
        _previousRoundInfo.value = null
    }

    // ── Walk-based auto round split ───────────────────────────────────────────

    private var stepsSinceLastShot = 0
    private var lastShotMs = 0L
    private val AUTO_SPLIT_STEPS = 8          // steps before auto-advancing
    private val AUTO_SPLIT_MIN_QUIET_MS = 12_000L  // min ms since last shot

    /** Called by MainActivity on each step detector event. */
    fun onStepDetected() {
        if (_phase.value != WatchPhase.SHOOTING) return
        val round = _session.value?.currentRound ?: return
        if (round.shots.isEmpty()) return  // nothing to split yet
        stepsSinceLastShot++
        if (stepsSinceLastShot >= AUTO_SPLIT_STEPS &&
            System.currentTimeMillis() - lastShotMs >= AUTO_SPLIT_MIN_QUIET_MS
        ) {
            stepsSinceLastShot = 0
            skipScoring()  // advance to next round without scoring
        }
    }

    // ── Shot recording ───────────────────────────────────────────────────────

    fun manualShot() {
        if (_phase.value != WatchPhase.SHOOTING) return
        val session = _session.value ?: return
        val round = session.currentRound ?: return
        val hr = sensorManager?.heartRate?.value ?: 0f
        val now = System.currentTimeMillis()

        val shot = WatchShot(number = round.shots.size + 1, heartRate = hr)
        _session.value = session.updateCurrentRound { it.copy(shots = it.shots + shot) }

        stepsSinceLastShot = 0
        lastShotMs = now
        _showQuickScore.value = true
        logger?.logShotDetected(now, round.number, shot.number, 0L, 0f, hr, manual = true)
        liveSyncManager?.sendShot(round.number, shot.number, 0L, hr, null)
    }

    fun quickScore(zone: ScoreZone) {
        val session = _session.value ?: return
        val round = session.currentRound ?: return
        val lastShot = round.shots.lastOrNull() ?: return

        val updated = lastShot.copy(quickZone = zone)
        _session.value = session.updateCurrentRound { r ->
            r.copy(shots = r.shots.dropLast(1) + updated)
        }
        _showQuickScore.value = false
        logger?.logQuickScore(System.currentTimeMillis(), round.number, lastShot.number, zone)
    }

    fun dismissQuickScore() {
        _showQuickScore.value = false
    }

    // ── Scoring phase ─────────────────────────────────────────────────────────

    fun enterScoring() {
        if (_phase.value != WatchPhase.SHOOTING) return
        val session = _session.value ?: return
        val round = session.currentRound ?: return
        val target = _arrowsPerRound.value
        val beforeCount = round.shots.size
        val now = System.currentTimeMillis()

        // Fill arrow slots up to arrowsPerRound
        val extraShots = (beforeCount until target).map { idx ->
            WatchShot(number = idx + 1, heartRate = 0f)
        }
        _session.value = session.updateCurrentRound { r ->
            r.copy(shots = r.shots + extraShots)
        }
        _showQuickScore.value = false
        _phase.value = WatchPhase.SCORING

        for (i in beforeCount until target) {
            logger?.logShotDetected(now, round.number, i + 1, 0L, 0f, 0f, manual = true)
            liveSyncManager?.sendShot(round.number, i + 1, 0L, 0f, null)
        }
        liveSyncManager?.sendPhaseChange("SCORING", round.number)
    }

    fun scoreArrow(shotIndex: Int, zone: ScoreZone, source: String = "watch", exactScore: Int? = null) {
        if (_phase.value != WatchPhase.SCORING && _phase.value != WatchPhase.SHOOTING) return
        val session = _session.value ?: return
        val round = session.currentRound ?: return
        if (shotIndex < 0 || shotIndex >= round.shots.size) return

        val updated = round.shots[shotIndex].copy(finalZone = zone, finalScore = exactScore)
        _session.value = session.updateCurrentRound { r ->
            r.copy(shots = r.shots.toMutableList().also { it[shotIndex] = updated })
        }
        logger?.logFinalScore(System.currentTimeMillis(), round.number, shotIndex + 1, zone, exactScore)
        val echoScore = exactScore?.toFloat() ?: zone.defaultScore
        liveSyncManager?.sendArrowScored(round.number, shotIndex, zone.name, echoScore, source)
    }

    fun setRoundTotal(total: Float) {
        val session = _session.value ?: return
        val round = session.currentRound ?: return
        _session.value = session.updateCurrentRound { r ->
            r.copy(confirmedTotal = total, totalConfirmed = true)
        }
        liveSyncManager?.sendRoundTotalSet(round.number, total, confirmed = true)
    }

    fun addScoringArrow() {
        val session = _session.value ?: return
        val round = session.currentRound ?: return
        val now = System.currentTimeMillis()
        val shot = WatchShot(number = round.shots.size + 1)
        _session.value = session.updateCurrentRound { it.copy(shots = it.shots + shot) }
        logger?.logShotDetected(now, round.number, shot.number, 0L, 0f, 0f, manual = true)
    }

    fun removeScoringArrow(index: Int) {
        val session = _session.value ?: return
        val round = session.currentRound ?: return
        if (index < 0 || index >= round.shots.size) return
        val renumbered = round.shots.toMutableList()
            .also { it.removeAt(index) }
            .mapIndexed { i, s -> s.copy(number = i + 1) }
        _session.value = session.updateCurrentRound { it.copy(shots = renumbered) }
    }

    fun finishScoring() {
        val session = _session.value ?: return
        val round = session.currentRound ?: return
        val now = System.currentTimeMillis()

        val approxScore = round.approxScore
        val actualScore = if (round.totalConfirmed) round.confirmedTotal else null
        logger?.logRoundScores(now, round.number, approxScore, actualScore)

        val displayScore = round.displayScore
        val nonDnsCount = round.shots.count { it.effectiveZone != ScoreZone.DNS && it.effectiveZone != null }
        val info = if (displayScore != null && nonDnsCount > 0)
            "%.1f avg".format(displayScore / nonDnsCount)
        else
            "${round.shots.size} arrows"
        _previousRoundInfo.value = "R${round.number}: $info"

        liveSyncManager?.sendRoundComplete(round.number, approxScore, actualScore)

        // Snapshot and send the round's sensor data to the phone for live analytics.
        // snapshotAndResetRoundBuffer posts to the same IO thread queue as the log writes
        // above, so it always captures the complete round buffer.
        val capturedRound = round.number
        val sync = liveSyncManager
        logger?.snapshotAndResetRoundBuffer { sensorBytes, hrBytes ->
            sync?.sendRoundSensorData(capturedRound, sensorBytes, hrBytes)
        }

        val nextRound = WatchRound(number = session.rounds.size + 1)
        _session.value = session.copy(rounds = session.rounds + nextRound)
        _phase.value = WatchPhase.SHOOTING
        liveSyncManager?.sendPhaseChange("SHOOTING", nextRound.number)
    }

    fun skipScoring() = finishScoring()

    // ── HR ────────────────────────────────────────────────────────────────────

    fun recordHeartRate(bpm: Float) {
        if (bpm <= 0f) return
        val session = _session.value ?: return
        _session.value = session.copy(
            heartRateSamples = session.heartRateSamples + Pair(System.currentTimeMillis(), bpm)
        )
    }

    // ── Phone commands ────────────────────────────────────────────────────────

    private fun handlePhoneCommand(cmd: PhoneCommandBus.PhoneCommand) {
        when (cmd) {
            is PhoneCommandBus.PhoneCommand.ScoreArrow -> {
                if (_phase.value != WatchPhase.SHOOTING && _phase.value != WatchPhase.SCORING) return
                val round = _session.value?.currentRound ?: return
                if (cmd.round != round.number) return
                val zone = ScoreZone.entries.find { it.name == cmd.zone } ?: return
                scoreArrow(cmd.shotIndex, zone, "phone", cmd.exactScore)
            }
            is PhoneCommandBus.PhoneCommand.SetRoundTotal -> {
                val round = _session.value?.currentRound ?: return
                if (cmd.round != round.number) return
                setRoundTotal(cmd.total)
            }
            is PhoneCommandBus.PhoneCommand.FinishScoring -> finishScoring()
            is PhoneCommandBus.PhoneCommand.EnterScoring -> enterScoring()
            is PhoneCommandBus.PhoneCommand.StartSession -> {
                if (_phase.value == WatchPhase.IDLE) {
                    setArrowsPerRound(cmd.arrowsPerRound)
                    startSession()
                }
            }
            is PhoneCommandBus.PhoneCommand.EndSession -> {
                if (_phase.value != WatchPhase.IDLE && _phase.value != WatchPhase.SUMMARY) {
                    endSession()
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun WatchSession.updateCurrentRound(transform: (WatchRound) -> WatchRound): WatchSession {
        if (rounds.isEmpty()) return this
        val updated = transform(rounds.last())
        return copy(rounds = rounds.dropLast(1) + updated)
    }
}
