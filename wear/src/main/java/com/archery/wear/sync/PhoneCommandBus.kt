package com.archery.wear.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Singleton bus for phone → watch commands dispatched from PhoneCommandReceiver. */
object PhoneCommandBus {

    sealed class PhoneCommand {
        data class StartSession(val arrowsPerRound: Int) : PhoneCommand()
        object EndSession : PhoneCommand()
        object EnterScoring : PhoneCommand()
        object FinishScoring : PhoneCommand()
        data class ScoreArrow(val round: Int, val shotIndex: Int, val zone: String, val exactScore: Int?) : PhoneCommand()
        data class SetRoundTotal(val round: Int, val total: Float) : PhoneCommand()
    }

    private val _commands = MutableSharedFlow<PhoneCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<PhoneCommand> = _commands.asSharedFlow()

    fun emit(cmd: PhoneCommand) = _commands.tryEmit(cmd)
}
