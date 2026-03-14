package com.archery.sync

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.archery.data.db.ArcheryDatabase
import com.archery.data.repository.SessionRepository
import com.archery.shared.ScoreZone
import com.archery.shared.WearPaths
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.File

class WatchListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "WatchListenerService"
    }

    // ── Live messages (watch → phone) ────────────────────────────────────────

    override fun onMessageReceived(event: MessageEvent) {
        Log.i(TAG, "onMessageReceived: ${event.path}")
        val p = try {
            JSONObject(String(event.data, Charsets.UTF_8))
        } catch (e: Exception) {
            JSONObject()
        }

        when (event.path) {
            WearPaths.MSG_SESSION_START ->
                LiveSessionRepository.onSessionStart(p.optInt(WearPaths.KEY_ARROWS_PER_ROUND, 3))

            WearPaths.MSG_SHOT -> LiveSessionRepository.onShot(
                roundNumber = p.optInt(WearPaths.KEY_ROUND, 1),
                shotNumber  = p.optInt(WearPaths.KEY_SHOT_NUMBER, 1),
                holdMs      = p.optLong(WearPaths.KEY_HOLD_MS, 0L),
                heartRate   = p.optDouble(WearPaths.KEY_HEART_RATE, 0.0).toFloat(),
                quickZone   = p.optString(WearPaths.KEY_ZONE, "")
                    .let { if (it.isEmpty()) null else ScoreZone.fromLabel(it) },
            )

            WearPaths.MSG_PHASE_CHANGE ->
                LiveSessionRepository.onPhaseChange(p.optString(WearPaths.KEY_PHASE, ""))

            WearPaths.MSG_ARROW_SCORED -> LiveSessionRepository.onArrowScored(
                roundNumber = p.optInt(WearPaths.KEY_ROUND, 1),
                shotIndex   = p.optInt(WearPaths.KEY_SHOT_INDEX, 0),
                zoneName    = p.optString(WearPaths.KEY_ZONE, "MISS"),
                score       = p.optDouble(WearPaths.KEY_SCORE, 0.0).toFloat(),
            )

            WearPaths.MSG_ROUND_TOTAL -> LiveSessionRepository.onRoundTotalSet(
                roundNumber = p.optInt(WearPaths.KEY_ROUND, 1),
                total       = p.optDouble(WearPaths.KEY_TOTAL, 0.0).toFloat(),
            )

            WearPaths.MSG_ROUND_COMPLETE -> LiveSessionRepository.onRoundComplete(
                roundNumber  = p.optInt(WearPaths.KEY_ROUND, 1),
                approxScore  = p.optDouble(WearPaths.KEY_APPROX_SCORE, 0.0).toFloat(),
                actualScore  = if (p.has(WearPaths.KEY_ACTUAL_SCORE))
                    p.getDouble(WearPaths.KEY_ACTUAL_SCORE).toFloat() else null,
            )

            // MSG_SESSION_END: mark session ended in the live UI so LiveScoringScreen
            // navigates back. The CSV (with full sensor data) arrives shortly after
            // via onDataChanged and is the authoritative source written to the DB.
            WearPaths.MSG_SESSION_END -> LiveSessionRepository.onSessionEnd()

            else -> Log.d(TAG, "Unknown path: ${event.path}")
        }
    }

    // ── CSV asset (watch → phone, Data Layer) ────────────────────────────────

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WearPaths.DATA_SESSION_CSV
            ) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val fileName    = dataMap.getString(WearPaths.KEY_CSV_FILE_NAME) ?: return@forEach
                val asset       = dataMap.getAsset(WearPaths.KEY_CSV_ASSET)      ?: return@forEach
                val startTimeMs = dataMap.getLong(WearPaths.KEY_SESSION_START_MS,
                    System.currentTimeMillis())
                Log.i(TAG, "Data changed: CSV=$fileName startMs=$startTimeMs")
                scope.launch { receiveCsvAsset(fileName, asset, startTimeMs) }
            }
        }
    }

    private suspend fun receiveCsvAsset(
        fileName: String,
        asset: com.google.android.gms.wearable.Asset,
        startTimeMs: Long,
    ) {
        try {
            val response = Wearable.getDataClient(applicationContext)
                .getFdForAsset(asset).await()
            val destDir  = File(
                applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir,
                "Archery"
            ).also { it.mkdirs() }
            val destFile = File(destDir, fileName)

            response.inputStream.use { input ->
                destFile.outputStream().use { out -> input.copyTo(out) }
            }

            val db   = ArcheryDatabase.getInstance(applicationContext)
            val repo = SessionRepository(db)
            val id   = repo.importCsv(destFile, startTimeMs)
            Log.i(TAG, "Imported CSV $fileName → sessionId=$id")

            // Also copy to phone Downloads so it's visible in Files app / USB MTP
            copyToDownloads(destFile)

            // Clear the in-memory live session now that it's been persisted from CSV
            LiveSessionRepository.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to receive/import CSV", e)
        }
    }

    /**
     * Copies the CSV to the public Downloads folder via MediaStore (API 29+).
     * Makes it visible in the Files app and accessible via USB MTP without ADB.
     */
    private fun copyToDownloads(srcFile: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val resolver = applicationContext.contentResolver
            // Check if a file with this name already exists and delete it first
            val existing = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(srcFile.name),
                null,
            )
            existing?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    resolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI.buildUpon()
                            .appendPath(id.toString()).build(),
                        null, null,
                    )
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, srcFile.name)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/Archery")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return
            resolver.openOutputStream(uri)?.use { out ->
                srcFile.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Log.i(TAG, "CSV copied to Downloads/Archery/${srcFile.name}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy CSV to Downloads", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
