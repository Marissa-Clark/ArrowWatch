package com.archery.ui.live

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.archery.analytics.DetectedShot
import com.archery.shared.LiveSession
import com.archery.sync.LiveSessionRepository
import com.archery.sync.WatchCommandSender
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LiveScoringViewModel(application: Application) : AndroidViewModel(application) {

    private val sender = WatchCommandSender(application)

    val session: StateFlow<LiveSession?> = LiveSessionRepository.session
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val roundAnalytics: StateFlow<Map<Int, List<DetectedShot>>> = LiveSessionRepository.roundAnalytics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun enterScoring() = sender.sendEnterScoring()

    fun finishScoring() = sender.sendFinishScoring()

    fun endSession() = sender.sendEndSession()

    fun scoreArrow(round: Int, shotIndex: Int, zone: String, exactScore: Int? = null) =
        sender.sendScoreArrow(round, shotIndex, zone, exactScore)

    fun setRoundTotal(round: Int, total: Float) =
        sender.sendSetRoundTotal(round, total)
}
