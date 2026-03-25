package com.archery.ui.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.archery.analytics.AnalyticsCache
import com.archery.analytics.AnalyticsParser
import com.archery.analytics.DetectedShot
import com.archery.analytics.DetectionProfile
import com.archery.analytics.DetectionSettings
import com.archery.analytics.ProfileSuggestion
import com.archery.analytics.SessionAnalytics
import com.archery.analytics.loadDismissedShots
import com.archery.analytics.loadManualShots
import com.archery.analytics.saveDismissedShots
import com.archery.analytics.saveManualShots
import com.archery.analytics.SensorSample
import com.archery.data.db.ArcheryDatabase
import com.archery.data.repository.SessionRepository
import com.archery.shared.ScoreZone
import com.archery.shared.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SessionRepository(ArcheryDatabase.getInstance(application))

    private val _sessionId = MutableStateFlow<Long>(-1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val session: StateFlow<SessionSummary?> = _sessionId
        .flatMapLatest { id -> if (id < 0) flowOf(null) else repo.getSession(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── Analytics (process-lifetime cache via AnalyticsCache; stable across navigation) ──

    private val _analytics = MutableStateFlow<SessionAnalytics?>(null)
    val analytics: StateFlow<SessionAnalytics?> = _analytics.asStateFlow()

    private val _isAnalyticsLoading = MutableStateFlow(false)
    val isAnalyticsLoading: StateFlow<Boolean> = _isAnalyticsLoading.asStateFlow()

    private val _dismissedState = MutableStateFlow<MutableMap<Int, MutableSet<Int>>>(mutableMapOf())
    val dismissedState: StateFlow<MutableMap<Int, MutableSet<Int>>> = _dismissedState.asStateFlow()

    // Manual (flagged) shots: map of csvRound → list of timeSec offsets relative to round start
    private val _manualShots = MutableStateFlow<MutableMap<Int, MutableList<Float>>>(mutableMapOf())
    val manualShots: StateFlow<MutableMap<Int, MutableList<Float>>> = _manualShots.asStateFlow()

    // ── Detection settings (single global threshold set) ─────────────────────

    /** The currently active detection thresholds — mirrors DetectionSettings.active. */
    val activeProfile: StateFlow<DetectionProfile> = DetectionSettings.active
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetectionSettings.active.value)

    /**
     * Apply a dismissal suggestion: update global settings, clear cache, re-run analytics.
     * The suggestion may also be applied from the Detection Settings screen directly.
     */
    fun applySuggestion(s: ProfileSuggestion) {
        DetectionSettings.update(s.suggested)
        AnalyticsCache.clear()
        refreshAnalytics()
    }

    /** Suggested tighter thresholds derived from dismissed vs. kept shots. Null if none or no useful change. */
    val suggestion: StateFlow<ProfileSuggestion?> = combine(
        _analytics, _dismissedState, DetectionSettings.active,
    ) { a, dismissed, profile -> computeSuggestion(a, dismissed, profile) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun computeSuggestion(
        analytics: SessionAnalytics?,
        dismissed: Map<Int, Set<Int>>,
        profile: DetectionProfile,
    ): ProfileSuggestion? {
        if (analytics == null) return null
        val dismissedShots = analytics.roundAnalytics.flatMap { ra ->
            val d = dismissed[ra.origCsvRound] ?: emptySet()
            ra.detectedShots.filterIndexed { i, _ -> i in d }
        }
        if (dismissedShots.isEmpty()) return null
        val keptShots = analytics.roundAnalytics.flatMap { ra ->
            val d = dismissed[ra.origCsvRound] ?: emptySet()
            ra.detectedShots.filterIndexed { i, _ -> i !in d }
        }

        // Characteristic gzD value per shot = mean of its gzDWindow
        fun shotGzD(s: DetectedShot) =
            if (s.gzDWindow.isNotEmpty()) s.gzDWindow.average().toFloat() else 0f

        // Round up to nearest 0.5
        fun ceilHalf(v: Float) = (kotlin.math.ceil(v.toDouble() * 2.0) / 2.0).toFloat()

        val maxDismissedHold = dismissedShots.maxOf { it.holdSec }
        val suggestedHold    = maxOf(profile.holdMinSec, ceilHalf(maxDismissedHold + 0.01f))

        val maxDismissedGzD = dismissedShots.maxOf { shotGzD(it) }
        val suggestedGzD    = maxOf(profile.gzMinDetrended, ceilHalf(maxDismissedGzD + 0.01f))

        if (suggestedHold == profile.holdMinSec && suggestedGzD == profile.gzMinDetrended) return null

        return ProfileSuggestion(
            suggested      = profile.copy(name = "Tuned", holdMinSec = suggestedHold, gzMinDetrended = suggestedGzD),
            dismissedCount = dismissedShots.size,
            holdChanged    = suggestedHold != profile.holdMinSec,
            gzChanged      = suggestedGzD  != profile.gzMinDetrended,
            holdConflicts  = keptShots.count { it.holdSec < suggestedHold },
            gzConflicts    = keptShots.count { shotGzD(it) < suggestedGzD },
        )
    }

    init {
        DetectionSettings.init(getApplication())
        // Collect session; when the filePath first becomes non-empty (or changes), load
        // analytics from the process-lifetime cache (instant) or parse from disk.
        // All subsequent DB emissions with the same path are ignored.
        viewModelScope.launch {
            var lastParsedPath: String? = null
            session.collect { s ->
                val path = s?.filePath?.takeIf { it.isNotEmpty() } ?: return@collect
                if (path == lastParsedPath) return@collect
                lastParsedPath = path

                val cached = AnalyticsCache.get(path)
                if (cached != null) {
                    // Instant restore from cache — no loading state needed.
                    _analytics.value = cached
                } else {
                    _analytics.value = null
                    _isAnalyticsLoading.value = true
                    val parsed = withContext(Dispatchers.IO) { AnalyticsParser.parse(path, DetectionSettings.active.value) }
                    if (parsed != null) {
                        AnalyticsCache.put(path, parsed)
                        // Persist analytics-derived hold times so home screen can display them.
                        val sessionId = session.value?.id ?: -1L
                        if (sessionId >= 0) {
                            withContext(Dispatchers.IO) {
                                parsed.roundAnalytics.forEach { ra ->
                                    if (ra.detectedShots.isNotEmpty()) {
                                        val avgMs = ra.detectedShots.map { it.holdSec * 1000f }
                                            .average().toLong()
                                        repo.updateRoundHoldMs(sessionId, ra.origCsvRound, avgMs)
                                    }
                                }
                            }
                        }
                    }
                    _analytics.value = parsed
                    _isAnalyticsLoading.value = false
                }
                _dismissedState.value = withContext(Dispatchers.IO) { loadDismissedShots(path) }
                _manualShots.value = withContext(Dispatchers.IO) { loadManualShots(path) }
            }
        }
    }

    fun lockAnalytics(locked: Boolean) {
        val id = _sessionId.value.takeIf { it >= 0 } ?: return
        viewModelScope.launch { repo.lockAnalytics(id, locked) }
    }

    /** Re-parses the CSV from disk with the current global detection settings. Ignored when locked. */
    fun refreshAnalytics() {
        if (session.value?.analyticsLocked == true) return
        val path = session.value?.filePath?.takeIf { it.isNotEmpty() } ?: return
        AnalyticsCache.invalidate(path)
        viewModelScope.launch {
            _analytics.value = null
            _isAnalyticsLoading.value = true
            val parsed = withContext(Dispatchers.IO) { AnalyticsParser.parse(path, DetectionSettings.active.value) }
            if (parsed != null) AnalyticsCache.put(path, parsed)
            _analytics.value = parsed
            _isAnalyticsLoading.value = false
        }
    }

    fun dismissShot(roundNumber: Int, shotIndex: Int, filePath: String) {
        val updated = _dismissedState.value.toMutableMap()
        updated[roundNumber] = (updated[roundNumber]?.toMutableSet() ?: mutableSetOf()).also { it.add(shotIndex) }
        _dismissedState.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                saveDismissedShots(filePath, updated)
                syncHoldTimeForRound(roundNumber, updated)
            }
        }
    }

    fun restoreShot(roundNumber: Int, shotIndex: Int, filePath: String) {
        val updated = _dismissedState.value.toMutableMap()
        updated[roundNumber] = (updated[roundNumber]?.toMutableSet() ?: mutableSetOf()).also { it.remove(shotIndex) }
        _dismissedState.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                saveDismissedShots(filePath, updated)
                syncHoldTimeForRound(roundNumber, updated)
            }
        }
    }

    /**
     * Flag a shot at [timeSec] (absolute session time) as a missed detection for [csvRound].
     * Stored in *.manual.json alongside the dismissed-shots file.
     */
    fun flagMissedShot(csvRound: Int, timeSec: Float, filePath: String) {
        val updated = _manualShots.value.toMutableMap()
        updated[csvRound] = (updated[csvRound]?.toMutableList() ?: mutableListOf()).also { it.add(timeSec) }
        _manualShots.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) { saveManualShots(filePath, updated) }
        }
    }

    fun unflagManualShot(csvRound: Int, timeSec: Float, filePath: String) {
        val updated = _manualShots.value.toMutableMap()
        updated[csvRound] = (updated[csvRound]?.toMutableList() ?: mutableListOf())
            .also { it.remove(timeSec) }
        _manualShots.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) { saveManualShots(filePath, updated) }
        }
    }

    /** Recompute and persist the avg hold time for [origRound] using only non-dismissed shots. */
    private suspend fun syncHoldTimeForRound(origRound: Int, dismissed: Map<Int, Set<Int>>) {
        val sessionId = _sessionId.value.takeIf { it >= 0 } ?: return
        val ra = _analytics.value?.roundAnalytics?.find { it.origCsvRound == origRound } ?: return
        val dismissedSet = dismissed[origRound] ?: emptySet()
        val activeShots = ra.detectedShots.filterIndexed { i, _ -> i !in dismissedSet }
        val avgMs = if (activeShots.isNotEmpty())
            activeShots.map { it.holdSec * 1000f }.average().toLong()
        else 0L
        repo.updateRoundHoldMs(sessionId, origRound, avgMs)
    }

    fun load(sessionId: Long) {
        _sessionId.value = sessionId
    }

    fun updateArrowScore(roundNumber: Int, shotNumber: Int, zone: ScoreZone, score: Float) {
        val id = _sessionId.value.takeIf { it >= 0 } ?: return
        viewModelScope.launch { repo.updateArrowScore(id, roundNumber, shotNumber, zone, score) }
    }

    fun updateRoundTotal(roundNumber: Int, total: Float) {
        val id = _sessionId.value.takeIf { it >= 0 } ?: return
        viewModelScope.launch { repo.updateRoundTotal(id, roundNumber, total) }
    }

    fun deleteRound(roundNumber: Int) {
        val id = _sessionId.value.takeIf { it >= 0 } ?: return
        viewModelScope.launch { repo.deleteRound(id, roundNumber) }
    }

    fun insertRoundAfter(roundNumber: Int) {
        val id = _sessionId.value.takeIf { it >= 0 } ?: return
        viewModelScope.launch { repo.insertRoundAfter(id, roundNumber) }
    }

    fun rename(name: String) {
        val id = _sessionId.value.takeIf { it >= 0 } ?: return
        viewModelScope.launch { repo.renameSession(id, name.trim().ifBlank { null }) }
    }

    fun archive(archived: Boolean) {
        val id = _sessionId.value.takeIf { it >= 0 } ?: return
        viewModelScope.launch { repo.setArchived(id, archived) }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = _sessionId.value.takeIf { it >= 0 } ?: return
        viewModelScope.launch {
            repo.deleteSession(id)
            onDeleted()
        }
    }
}
