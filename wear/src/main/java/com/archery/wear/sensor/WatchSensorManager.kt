package com.archery.wear.sensor

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Registers and collects watch sensors.
 *
 * Motion/orientation sensors (Game Rotation Vector, Gravity, Step Detector) run
 * directly from this class via SensorManager.
 *
 * Heart rate is collected by [HrService] — a foreground service with
 * foregroundServiceType="health" — which is required for TYPE_HEART_RATE to fire
 * on Samsung Galaxy Watch (Wear OS 4 / Android 13+).  [heartRate] is a direct
 * reference to [HrService.heartRate] so observers see updates immediately.
 */
class WatchSensorManager(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "WatchSensorManager"
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /** Heart rate (bpm) — sourced from HrService foreground service. */
    val heartRate: StateFlow<Float> = HrService.heartRate

    /** Every raw HR sensor reading — used to log heart_rate rows to the session CSV. */
    val heartRateEvents: SharedFlow<Float> = HrService.heartRateEvent

    private val _gravityZ = MutableStateFlow(0f)
    val gravityZ: StateFlow<Float> = _gravityZ.asStateFlow()

    /** Called ~1Hz for CSV sensor rows. Includes all three gravity axes. */
    var onSensorUpdate: ((yaw: Float, pitch: Float, roll: Float, gz: Float, steps: Long, gx: Float, gy: Float) -> Unit)? = null

    private var yaw = 0f
    private var pitch = 0f
    private var roll = 0f
    private var gravityX = 0f
    private var gravityY = 0f
    private var stepCount = 0L
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    fun start() {
        // Motion sensors
        register(Sensor.TYPE_GAME_ROTATION_VECTOR, SensorManager.SENSOR_DELAY_GAME)
        register(Sensor.TYPE_GRAVITY, SensorManager.SENSOR_DELAY_GAME)
        register(Sensor.TYPE_STEP_DETECTOR, SensorManager.SENSOR_DELAY_NORMAL)

        // Heart rate via foreground health service
        context.startService(
            Intent(context, HrService::class.java).apply { action = HrService.ACTION_START }
        )

        Log.i(TAG, "Sensors started")
    }

    fun stop() {
        sensorManager.unregisterListener(this)

        context.startService(
            Intent(context, HrService::class.java).apply { action = HrService.ACTION_STOP }
        )

        Log.i(TAG, "Sensors stopped")
    }

    private fun register(type: Int, delay: Int) {
        val sensor = sensorManager.getDefaultSensor(type)
        if (sensor != null) {
            val ok = sensorManager.registerListener(this, sensor, delay)
            Log.i(TAG, "registerListener ${sensor.name} -> $ok")
        } else {
            Log.w(TAG, "Sensor type $type not available")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                yaw   = orientationAngles[0]
                pitch = orientationAngles[1]
                roll  = orientationAngles[2]
                onSensorUpdate?.invoke(yaw, pitch, roll, _gravityZ.value, stepCount, gravityX, gravityY)
            }
            Sensor.TYPE_GRAVITY -> {
                gravityX = event.values[0]
                gravityY = event.values[1]
                _gravityZ.value = event.values[2]
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                stepCount++
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}
