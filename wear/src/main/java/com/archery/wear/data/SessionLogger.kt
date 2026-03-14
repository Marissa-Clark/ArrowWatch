package com.archery.wear.data

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.archery.shared.ScoreZone
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes session events to a timestamped CSV file.
 * All I/O runs on a dedicated background thread.
 * Sensor rows are throttled to ~1Hz.
 *
 * Pull files with:
 *   adb pull /sdcard/Android/data/com.archery.wear/files/Archery/
 */
class SessionLogger(private val context: Context) {

    companion object {
        private const val TAG = "SessionLogger"
    }

    private val baseDir: File
    private var writer: PrintWriter? = null
    private var currentFile: File? = null
    private var startTimeMs = 0L
    private var lastSensorLogMs = 0L

    private val ioThread = HandlerThread("SessionLoggerIO").apply { start() }
    private val ioHandler = Handler(ioThread.looper)

    init {
        val ext = context.getExternalFilesDir(null)
        baseDir = File(ext ?: context.filesDir, "Archery").also { it.mkdirs() }
        Log.i(TAG, "Log dir: $baseDir")
    }

    fun getSessionFile(): File? = currentFile

    fun startSession() {
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        startTimeMs = System.currentTimeMillis()
        lastSensorLogMs = 0L
        ioHandler.post {
            try {
                val file = File(baseDir, "session_$ts.csv")
                currentFile = file
                writer = PrintWriter(FileWriter(file))
                writer?.println("elapsed_sec,event_type,round,shot_number,hold_ms,gravity_z,roll_swing,zone,score,heart_rate,yaw,pitch,roll,steps,gravity_x,gravity_y")
                writer?.flush()
                Log.i(TAG, "Session file: $file")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start session", e)
            }
        }
    }

    fun logSensor(timestampMs: Long, yaw: Float, pitch: Float, roll: Float, gravityZ: Float, steps: Long = 0, gravityX: Float = 0f, gravityY: Float = 0f) {
        if (timestampMs - lastSensorLogMs < 100L) return
        lastSensorLogMs = timestampMs
        val elapsed = elapsed(timestampMs)
        val line = "%.1f,sensor,,,,%.2f,,,,,%.2f,%.2f,%.2f,%d,%.2f,%.2f".format(elapsed, gravityZ, yaw, pitch, roll, steps, gravityX, gravityY)
        post { writer?.println(line) }
    }

    fun logShotDetected(timestampMs: Long, round: Int, shotNumber: Int, holdMs: Long, gravityZ: Float, heartRate: Float, manual: Boolean = false) {
        val elapsed = elapsed(timestampMs)
        val type = if (manual) "manual_shot" else "auto_shot"
        val line = "%.1f,%s,%d,%d,%d,%.2f,0.00,,,%.0f,,,".format(elapsed, type, round, shotNumber, holdMs, gravityZ, heartRate)
        post { writer?.println(line) }
    }

    fun logQuickScore(timestampMs: Long, round: Int, shotNumber: Int, zone: ScoreZone) {
        val elapsed = elapsed(timestampMs)
        val line = "%.1f,quick_score,%d,%d,,,,%s,%.1f,,,,".format(elapsed, round, shotNumber, zone.name, zone.defaultScore)
        post { writer?.println(line) }
    }

    fun logFinalScore(timestampMs: Long, round: Int, shotNumber: Int, zone: ScoreZone, exactScore: Int? = null) {
        val elapsed = elapsed(timestampMs)
        val score = exactScore?.toFloat() ?: zone.defaultScore
        val line = "%.1f,final_score,%d,%d,,,,%s,%.1f,,,,".format(elapsed, round, shotNumber, zone.name, score)
        post { writer?.println(line) }
    }

    fun logRoundScores(timestampMs: Long, round: Int, approxScore: Float, actualScore: Float?) {
        val elapsed = elapsed(timestampMs)
        post {
            writer?.println("%.1f,approx_score,%d,,,,,,%.1f,,,,".format(elapsed, round, approxScore))
            if (actualScore != null) {
                writer?.println("%.1f,actual_score,%d,,,,,,%.1f,,,,".format(elapsed, round, actualScore))
            }
        }
    }

    fun logHeartRate(timestampMs: Long, bpm: Float) {
        if (bpm <= 0f) return
        val elapsed = elapsed(timestampMs)
        val line = "%.1f,heart_rate,,,,,,,,%.0f,,,".format(elapsed, bpm)
        post { writer?.println(line) }
    }

    fun logWalking(timestampMs: Long, walking: Boolean) {
        val elapsed = elapsed(timestampMs)
        val event = if (walking) "walking_start" else "walking_stop"
        val line = "%.1f,%s,,,,,,,,,,,".format(elapsed, event)
        post { writer?.println(line) }
    }

    fun flush() {
        post { writer?.flush() }
    }

    fun getStartTimeMs(): Long = startTimeMs

    fun stopSession() {
        post {
            val elapsed = elapsed(System.currentTimeMillis())
            writer?.println("%.1f,session_end,,,,,,,,,,,".format(elapsed))
            writer?.flush()
            writer?.close()
            writer = null
            Log.i(TAG, "Session stopped")
        }
    }

    /**
     * Stop the session, flush and close the CSV, then invoke [onDone] with the
     * completed file and the session start timestamp (epoch ms). Called on the
     * IO background thread — safe to hand off to LiveSyncManager.
     */
    fun stopSessionAndThen(onDone: (file: File?, startTimeMs: Long) -> Unit) {
        val capturedFile  = currentFile
        val capturedStart = startTimeMs
        post {
            val elapsed = elapsed(System.currentTimeMillis())
            writer?.println("%.1f,session_end,,,,,,,,,,,".format(elapsed))
            writer?.flush()
            writer?.close()
            writer = null
            Log.i(TAG, "Session stopped")
            onDone(capturedFile, capturedStart)
        }
    }

    fun shutdown() {
        ioThread.quitSafely()
    }

    private fun elapsed(timestampMs: Long) = (timestampMs - startTimeMs) / 1000.0

    private fun post(block: () -> Unit) = ioHandler.post(block)
}
