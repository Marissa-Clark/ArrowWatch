package com.archery.wear.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Foreground health service for heart rate collection on Samsung Galaxy Watch.
 *
 * Requires foregroundServiceType="health" in the manifest and the
 * android.permission.health.READ_HEART_RATE permission — both of which are
 * needed for SensorManager.TYPE_HEART_RATE to fire events on Wear OS 4 / Android 13+.
 *
 * HR is published to [HrService.heartRate] (companion object StateFlow) so that
 * WatchSensorManager can expose it without binding to the service.
 */
class HrService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "HrService"
        private const val CHANNEL_ID = "archery_hr"
        private const val NOTIF_ID = 42

        const val ACTION_START = "com.archery.wear.START_HR"
        const val ACTION_STOP  = "com.archery.wear.STOP_HR"

        /** Latest HR value — deduplicated, used for UI. */
        val heartRate = MutableStateFlow(0f)

        /**
         * Every raw sensor reading, even if the value hasn't changed.
         * Collected by MainActivity to write heart_rate rows to the session CSV.
         */
        private val _heartRateEvent = MutableSharedFlow<Float>(replay = 0, extraBufferCapacity = 32)
        val heartRateEvent: SharedFlow<Float> = _heartRateEvent.asSharedFlow()
    }

    private lateinit var sensorManager: SensorManager
    private var registered = false

    // -------------------------------------------------------------------------
    // Service lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startHr()
            ACTION_STOP  -> stopHr()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopHr()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // HR sensor
    // -------------------------------------------------------------------------

    private fun startHr() {
        if (registered) return
        startForeground(NOTIF_ID, buildNotification())
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (sensor != null) {
            // 5-second batch latency saves power while still providing timely updates
            val ok = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, 5_000_000)
            registered = ok
            Log.i(TAG, "HR sensor '${sensor.name}' registered: $ok")
        } else {
            Log.w(TAG, "TYPE_HEART_RATE sensor not available on this device")
        }
    }

    private fun stopHr() {
        if (registered) {
            sensorManager.unregisterListener(this)
            registered = false
        }
        heartRate.value = 0f
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "HR service stopped")
    }

    // -------------------------------------------------------------------------
    // SensorEventListener
    // -------------------------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
            val bpm = event.values[0]
            if (bpm > 0f) {
                Log.d(TAG, "HR: ${bpm.toInt()} bpm (accuracy=${event.accuracy})")
                heartRate.value = bpm
                _heartRateEvent.tryEmit(bpm)   // fires every raw reading for CSV logging
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val level = when (accuracy) {
            SensorManager.SENSOR_STATUS_NO_CONTACT  -> "NO_CONTACT"
            SensorManager.SENSOR_STATUS_UNRELIABLE  -> "UNRELIABLE"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW    -> "LOW"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MEDIUM"
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH   -> "HIGH"
            else -> "UNKNOWN($accuracy)"
        }
        Log.i(TAG, "HR accuracy: $level")
    }

    // -------------------------------------------------------------------------
    // Notification (required for foreground service)
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Heart Rate",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Heart rate monitoring during archery session" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Archery")
            .setContentText("Heart rate monitoring active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
}
