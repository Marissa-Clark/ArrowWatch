package com.archery.wear.sync

import android.content.Context
import android.util.Log
import com.archery.shared.WearPaths
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.File

/**
 * Sends live session events to the paired phone via Wear Data Layer MessageClient.
 * Fire-and-forget — no acknowledgement expected.
 */
class LiveSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "LiveSyncManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val dataClient: DataClient = Wearable.getDataClient(context)

    // ── Outbound messages ─────────────────────────────────────────────────────

    fun sendSessionStart(arrowsPerRound: Int) {
        send(WearPaths.MSG_SESSION_START, json {
            put(WearPaths.KEY_ARROWS_PER_ROUND, arrowsPerRound)
        })
    }

    fun sendShot(round: Int, shotNumber: Int, holdMs: Long, heartRate: Float, quickZone: String?) {
        send(WearPaths.MSG_SHOT, json {
            put(WearPaths.KEY_ROUND, round)
            put(WearPaths.KEY_SHOT_NUMBER, shotNumber)
            put(WearPaths.KEY_HOLD_MS, holdMs)
            put(WearPaths.KEY_HEART_RATE, heartRate)
            if (quickZone != null) put(WearPaths.KEY_ZONE, quickZone)
        })
    }

    fun sendPhaseChange(phase: String, round: Int) {
        send(WearPaths.MSG_PHASE_CHANGE, json {
            put(WearPaths.KEY_PHASE, phase)
            put(WearPaths.KEY_ROUND, round)
        })
    }

    fun sendArrowScored(round: Int, shotIndex: Int, zone: String, score: Float, source: String) {
        send(WearPaths.MSG_ARROW_SCORED, json {
            put(WearPaths.KEY_ROUND, round)
            put(WearPaths.KEY_SHOT_INDEX, shotIndex)
            put(WearPaths.KEY_ZONE, zone)
            put(WearPaths.KEY_SCORE, score)
        })
    }

    fun sendRoundTotalSet(round: Int, total: Float, confirmed: Boolean) {
        send(WearPaths.MSG_ROUND_TOTAL, json {
            put(WearPaths.KEY_ROUND, round)
            put(WearPaths.KEY_TOTAL, total)
            put(WearPaths.KEY_CONFIRMED, confirmed)
        })
    }

    fun sendRoundComplete(round: Int, approxScore: Float, actualScore: Float?) {
        send(WearPaths.MSG_ROUND_COMPLETE, json {
            put(WearPaths.KEY_ROUND, round)
            put(WearPaths.KEY_APPROX_SCORE, approxScore)
            if (actualScore != null) put(WearPaths.KEY_ACTUAL_SCORE, actualScore)
        })
    }

    fun sendSessionEnd() {
        send(WearPaths.MSG_SESSION_END, "{}")
    }

    /**
     * Send raw sensor + HR data for a completed round so the phone can run
     * AnalyticsParser.detectShots() and display hold-time chips immediately.
     *
     * @param sensorBytes packed floats [elapsed,gz,yaw,pitch,roll] — 20 bytes/row
     * @param hrBytes     packed floats [elapsed,bpm]               —  8 bytes/row
     */
    fun sendRoundSensorData(round: Int, sensorBytes: ByteArray, hrBytes: ByteArray) {
        send(WearPaths.MSG_ROUND_SENSOR_DATA, json {
            put(WearPaths.KEY_ROUND, round)
            put(WearPaths.KEY_SENSOR_DATA, android.util.Base64.encodeToString(sensorBytes, android.util.Base64.NO_WRAP))
            put(WearPaths.KEY_HR_DATA,     android.util.Base64.encodeToString(hrBytes,     android.util.Base64.NO_WRAP))
        })
    }

    /**
     * Send the final CSV file to the phone as a Data Layer Asset.
     * Must be called AFTER the logger has flushed and closed the file
     * (use SessionLogger.stopSessionAndThen to guarantee ordering).
     *
     * @param startTimeMs epoch-ms of session start, stored in the DataMap so
     *                    the phone can use it as the DB dateMs without parsing
     *                    the filename.
     */
    fun sendSessionCsv(csvFile: File, startTimeMs: Long) {
        if (!csvFile.exists()) return
        scope.launch {
            try {
                val asset = com.google.android.gms.wearable.Asset.createFromUri(
                    android.net.Uri.fromFile(csvFile)
                )
                val request = com.google.android.gms.wearable.PutDataMapRequest
                    .create(WearPaths.DATA_SESSION_CSV).apply {
                        dataMap.putString(WearPaths.KEY_CSV_FILE_NAME, csvFile.name)
                        dataMap.putAsset(WearPaths.KEY_CSV_ASSET, asset)
                        dataMap.putLong(WearPaths.KEY_SESSION_START_MS, startTimeMs)
                    }
                    .asPutDataRequest()
                    .setUrgent()
                dataClient.putDataItem(request).await()
                Log.i(TAG, "CSV sent: ${csvFile.name} startMs=$startTimeMs")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send CSV", e)
            }
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun send(path: String, payload: String) {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                Log.i(TAG, "send $path nodes=${nodes.size}: ${nodes.map { it.displayName }}")
                val bytes = payload.toByteArray(Charsets.UTF_8)
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, path, bytes).await()
                    Log.i(TAG, "sent $path to ${node.displayName} (${node.id})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send $path: ${e.message}")
            }
        }
    }

    fun shutdown() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun json(block: JSONObject.() -> Unit): String =
        JSONObject().apply(block).toString()
}
