package com.archery.ui.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.archery.data.db.ArcheryDatabase
import com.archery.data.repository.SessionRepository
import com.archery.shared.ScoreZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SessionRepository(ArcheryDatabase.getInstance(application))

    /**
     * Creates a manual (retrospective) session with per-arrow scores.
     * [arrowData][r][a] = (zone, score) or null for unset arrows.
     * Calls [onCreated] on the main thread with the new session ID.
     */
    fun save(
        dateMs: Long,
        name: String?,
        arrowsPerRound: Int,
        arrowData: List<List<Pair<ScoreZone, Float>?>>,
        distanceM: Int?,
        targetSizeCm: Int?,
        onCreated: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                repo.createManualSession(dateMs, name, arrowsPerRound, arrowData, distanceM, targetSizeCm)
            }
            onCreated(id)
        }
    }
}
