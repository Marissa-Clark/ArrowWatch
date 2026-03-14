package com.archery.wear.sync

import android.util.Log
import com.archery.shared.WearPaths
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

/**
 * Receives commands from the phone app via Wear Data Layer MessageClient.
 * Dispatches decoded commands to [PhoneCommandBus].
 */
class PhoneCommandReceiver : WearableListenerService() {

    companion object {
        private const val TAG = "PhoneCommandReceiver"
    }

    override fun onMessageReceived(event: MessageEvent) {
        val payload = try {
            JSONObject(String(event.data, Charsets.UTF_8))
        } catch (e: Exception) {
            JSONObject()
        }

        val cmd = when (event.path) {
            WearPaths.CMD_START_SESSION -> {
                val atr = payload.optInt(WearPaths.KEY_ARROWS_PER_ROUND, 3)
                PhoneCommandBus.PhoneCommand.StartSession(atr)
            }
            WearPaths.CMD_END_SESSION -> PhoneCommandBus.PhoneCommand.EndSession
            WearPaths.CMD_ENTER_SCORING -> PhoneCommandBus.PhoneCommand.EnterScoring
            WearPaths.CMD_FINISH_SCORING -> PhoneCommandBus.PhoneCommand.FinishScoring
            WearPaths.CMD_SCORE_ARROW -> PhoneCommandBus.PhoneCommand.ScoreArrow(
                round = payload.optInt(WearPaths.KEY_ROUND),
                shotIndex = payload.optInt(WearPaths.KEY_SHOT_INDEX),
                zone = payload.optString(WearPaths.KEY_ZONE),
                exactScore = if (payload.has(WearPaths.KEY_EXACT_SCORE))
                    payload.getInt(WearPaths.KEY_EXACT_SCORE) else null,
            )
            WearPaths.CMD_SET_ROUND_TOTAL -> PhoneCommandBus.PhoneCommand.SetRoundTotal(
                round = payload.optInt(WearPaths.KEY_ROUND),
                total = payload.optDouble(WearPaths.KEY_TOTAL, 0.0).toFloat(),
            )
            else -> {
                Log.d(TAG, "Unknown path: ${event.path}")
                return
            }
        }

        Log.d(TAG, "Command received: $cmd")
        PhoneCommandBus.emit(cmd)
    }
}
