package com.archery.ui.sessions

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.archery.data.db.ArcheryDatabase
import com.archery.data.repository.SessionRepository
import com.archery.shared.LiveSession
import com.archery.shared.SessionSummary
import com.archery.sync.LiveSessionRepository
import com.archery.sync.WatchCommandSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SessionListViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SessionRepository(ArcheryDatabase.getInstance(application))
    private val sender = WatchCommandSender(application)

    val showArchived = MutableStateFlow(false)

    init {
        // One-shot import of any CSVs in app external files Archery/ not yet in the DB.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val archeryDir = File(application.getExternalFilesDir(null), "Archery")
                if (!archeryDir.exists()) return@launch
                val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                archeryDir.listFiles { f -> f.name.endsWith(".csv") }?.forEach { file ->
                    val dateMs = try {
                        val stamp = file.nameWithoutExtension.removePrefix("session_")
                        LocalDateTime.parse(stamp, fmt)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } catch (e: Exception) { file.lastModified() }
                    repo.importCsv(file, dateMs)
                }
            } catch (e: Exception) {
                Log.w("SessionListVM", "Archery dir import failed", e)
            }
        }
    }

    val sessions: StateFlow<List<SessionSummary>> = repo.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val liveSession: StateFlow<LiveSession?> = LiveSessionRepository.session
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun archive(id: Long, archived: Boolean) {
        viewModelScope.launch { repo.setArchived(id, archived) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteSession(id) }
    }

    fun createManualSession(
        dateMs: Long,
        displayName: String?,
        roundCount: Int,
        arrowsPerRound: Int,
        roundScores: List<Float>,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.createManualSession(dateMs, displayName, roundCount, arrowsPerRound, roundScores)
        }
    }

    fun startSession() = sender.sendStartSession()

    fun endSession() = sender.sendEndSession()

    /** Clears the stale live session from phone memory without touching the watch. */
    fun dismissLiveSession() = LiveSessionRepository.clear()
}
