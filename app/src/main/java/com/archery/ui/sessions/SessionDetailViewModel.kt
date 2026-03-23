package com.archery.ui.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.archery.analytics.AnalyticsCache
import com.archery.analytics.AnalyticsParser
import com.archery.analytics.SessionAnalytics
import com.archery.analytics.loadDismissedShots
import com.archery.analytics.saveDismissedShots
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

    init {
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
                    val parsed = withContext(Dispatchers.IO) { AnalyticsParser.parse(path) }
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
            }
        }
    }

    /** Re-parses the CSV from disk, replacing the cached result. */
    fun refreshAnalytics() {
        val path = session.value?.filePath?.takeIf { it.isNotEmpty() } ?: return
        AnalyticsCache.invalidate(path)
        viewModelScope.launch {
            _analytics.value = null
            _isAnalyticsLoading.value = true
            val parsed = withContext(Dispatchers.IO) { AnalyticsParser.parse(path) }
            if (parsed != null) AnalyticsCache.put(path, parsed)
            _analytics.value = parsed
            _isAnalyticsLoading.value = false
        }
    }

    fun dismissShot(roundNumber: Int, shotIndex: Int, filePath: String) {
        val updated = _dismissedState.value.toMutableMap()
        updated[roundNumber] = (updated[roundNumber]?.toMutableSet() ?: mutableSetOf()).also { it.add(shotIndex) }
        _dismissedState.value = updated
        // NonCancellable ensures the file write completes even if the user presses back
        // before the coroutine has a chance to run.
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) { saveDismissedShots(filePath, updated) }
        }
    }

    fun restoreShot(roundNumber: Int, shotIndex: Int, filePath: String) {
        val updated = _dismissedState.value.toMutableMap()
        updated[roundNumber] = (updated[roundNumber]?.toMutableSet() ?: mutableSetOf()).also { it.remove(shotIndex) }
        _dismissedState.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) { saveDismissedShots(filePath, updated) }
        }
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
