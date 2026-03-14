package com.archery.sync

import android.content.Context
import android.util.Log
import com.archery.shared.WearPaths
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Sends commands from the phone to the paired watch via Wear MessageClient.
 */
class WatchCommandSender(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sendStartSession(arrowsPerRound: Int = 6) = send(WearPaths.CMD_START_SESSION, json {
        put(WearPaths.KEY_ARROWS_PER_ROUND, arrowsPerRound)
    })

    fun sendEnterScoring() = send(WearPaths.CMD_ENTER_SCORING, "{}")

    fun sendFinishScoring() = send(WearPaths.CMD_FINISH_SCORING, "{}")

    fun sendEndSession() = send(WearPaths.CMD_END_SESSION, "{}")

    fun sendScoreArrow(round: Int, shotIndex: Int, zone: String, exactScore: Int? = null) {
        send(WearPaths.CMD_SCORE_ARROW, json {
            put(WearPaths.KEY_ROUND, round)
            put(WearPaths.KEY_SHOT_INDEX, shotIndex)
            put(WearPaths.KEY_ZONE, zone)
            if (exactScore != null) put(WearPaths.KEY_EXACT_SCORE, exactScore)
        })
    }

    fun sendSetRoundTotal(round: Int, total: Float) {
        send(WearPaths.CMD_SET_ROUND_TOTAL, json {
            put(WearPaths.KEY_ROUND, round)
            put(WearPaths.KEY_TOTAL, total)
        })
    }

    private fun send(path: String, payload: String) {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                Log.i("WatchCommandSender", "send $path nodes=${nodes.size}: ${nodes.map { it.displayName }}")
                val bytes = payload.toByteArray(Charsets.UTF_8)
                nodes.forEach { node ->
                    Wearable.getMessageClient(context).sendMessage(node.id, path, bytes).await()
                    Log.i("WatchCommandSender", "sent $path to ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.w("WatchCommandSender", "Failed to send $path: ${e.message}")
            }
        }
    }

    private fun json(block: JSONObject.() -> Unit) = JSONObject().apply(block).toString()
}
