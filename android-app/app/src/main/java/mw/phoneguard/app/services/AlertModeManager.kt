package mw.phoneguard.app.services

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import mw.phoneguard.app.data.storage.TokenStore
import kotlin.math.sqrt

/**
 * Detects phone motion while Alert Mode is armed.
 */
class AlertModeManager(
    private val context: Context,
    private val onMotionDetected: () -> Unit
) : SensorEventListener {

    private val TAG = "PhoneGuardAlertMode"
    private val GRAVITY = 9.81f

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Try linear acceleration first; fall back to raw accelerometer
    private val sensor: Sensor? = run {
        val wakeLinear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION, true)
        if (wakeLinear != null) {
            Log.i(TAG, "Sensor: LINEAR_ACCELERATION (wake-up capable)")
            return@run wakeLinear
        }

        val linear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (linear != null) {
            Log.w(TAG, "Sensor: LINEAR_ACCELERATION (non-wake-up)")
            return@run linear
        }

        val wakeRaw = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
        if (wakeRaw != null) {
            Log.i(TAG, "Sensor: ACCELEROMETER (wake-up capable, manual gravity removal)")
            return@run wakeRaw
        }

        val raw = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (raw != null) {
            Log.w(TAG, "Sensor: ACCELEROMETER (non-wake-up)")
            return@run raw
        }

        Log.e(TAG, "No accelerometer sensor of any type available")
        null
    }
    private val useLinear: Boolean = sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION

    // Read sensitivity and threshold dynamically at runtime from TokenStore
    private val currentSensitivity: String
        get() = TokenStore.get(context).getMotionSensitivity()

    private val currentThreshold: Float
        get() {
            val base = when (currentSensitivity) {
                "LOW" -> 6.0f
                "HIGH" -> 3.0f
                else -> 4.5f // MEDIUM
            }
            return base
        }

    private val consecutiveRequired: Int = if (useLinear) 2 else 3
    private val minTriggerIntervalMs = if (useLinear) 10_000L else 15_000L

    private var consecutiveHighReadings = 0
    private var isRegistered = false
    private var lastMotionTimeMs = 0L

    fun start() {
        if (isRegistered) {
            Log.d(TAG, "Already registered")
            return
        }
        val s = sensor
        if (s == null) {
            Log.e(TAG, "Cannot start — no sensor available")
            return
        }

        val ok = sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
        if (ok) {
            isRegistered = true
            val wakeUpCapable = s.isWakeUpSensor()
            val threshold = currentThreshold
            val msg = "Sensor registered (type=${if (useLinear) "LINEAR" else "RAW"}, wakeUp=$wakeUpCapable, threshold=$threshold, consecutive=$consecutiveRequired)"
            Log.i(TAG, msg)
            ServiceLogger.log(TAG, msg)
        } else {
            Log.e(TAG, "registerListener returned false")
        }
    }

    fun stop() {
        if (!isRegistered) return
        sensorManager.unregisterListener(this)
        isRegistered = false
        consecutiveHighReadings = 0
        Log.i(TAG, "Sensor unregistered")
        ServiceLogger.log(TAG, "Sensor unregistered")
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val motionValue = if (useLinear) {
            sqrt(x * x + y * y + z * z)
        } else {
            val magnitude = sqrt(x * x + y * y + z * z)
            kotlin.math.abs(magnitude - GRAVITY)
        }

        val threshold = currentThreshold
        if (motionValue > threshold) {
            consecutiveHighReadings++
            if (consecutiveHighReadings >= consecutiveRequired) {
                val now = System.currentTimeMillis()
                if (now - lastMotionTimeMs > minTriggerIntervalMs) {
                    lastMotionTimeMs = now
                    val alertMsg = "!!! MOTION DETECTED (value=${"%.2f".format(motionValue)}, threshold=$threshold) !!!"
                    Log.w(TAG, alertMsg)
                    ServiceLogger.log(TAG, alertMsg)
                    onMotionDetected()
                }
                consecutiveHighReadings = 0
            }
        } else {
            consecutiveHighReadings = 0
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
