package com.archery.ui.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.archery.data.db.ArcheryDatabase
import com.archery.data.repository.SessionRepository
import com.archery.shared.ScoreZone
import com.archery.shared.SessionSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SessionRepository(ArcheryDatabase.getInstance(application))

    private val _sessionId = MutableStateFlow<Long>(-1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val session: StateFlow<SessionSummary?> = _sessionId
        .flatMapLatest { id -> if (id < 0) flowOf(null) else repo.getSession(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
