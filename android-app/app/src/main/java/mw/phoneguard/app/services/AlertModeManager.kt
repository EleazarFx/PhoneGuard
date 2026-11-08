package mw.phoneguard.app.services

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * Detects phone motion while Alert Mode is armed.
 *
 * Strategy:
 *  - Prefer Sensor.TYPE_LINEAR_ACCELERATION (gravity removed by Android; some
 *    budget phones don't have it).
 *  - Fall back to Sensor.TYPE_ACCELEROMETER (mandatory on every Android phone)
 *    and remove gravity manually.
 *
 * Algorithm for raw accelerometer:
 *  1. Compute magnitude = sqrt(x² + y² + z²)
 *  2. Subtract 9.81 (gravity)
 *  3. Take absolute value
 *  4. If > threshold for N consecutive readings → motion
 *
 * Tuning constants (adjust after testing):
 *   - LINEAR: 3.0 m/s² (already gravity-removed; only real motion)
 *   - RAW: 4.5 m/s² (after gravity subtraction; higher threshold compensates
 *     for remaining noise)
 *   - CONSECUTIVE_READINGS: 3 (higher than 2 because raw is noisier)
 *   - MIN_TRIGGER_INTERVAL_MS: 15s (debounce)
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
        // Tier 1: Wake-up linear acceleration (best case)
        val wakeLinear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION, true)
        if (wakeLinear != null) {
            Log.i(TAG, "Sensor: LINEAR_ACCELERATION (wake-up capable)")
            return@run wakeLinear
        }

        // Tier 2: Non-wake-up linear acceleration
        val linear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (linear != null) {
            Log.w(TAG, "Sensor: LINEAR_ACCELERATION (non-wake-up — motion only when screen on)")
            return@run linear
        }

        // Tier 3: Wake-up raw accelerometer
        val wakeRaw = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
        if (wakeRaw != null) {
            Log.i(TAG, "Sensor: ACCELEROMETER (wake-up capable, manual gravity removal)")
            return@run wakeRaw
        }

        // Tier 4: Non-wake-up raw accelerometer (guaranteed to exist if anything does)
        val raw = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (raw != null) {
            Log.w(TAG, "Sensor: ACCELEROMETER (non-wake-up — motion only when screen on)")
            return@run raw
        }

        Log.e(TAG, "No accelerometer sensor of any type available — this should never happen")
        null
    }
    private val useLinear: Boolean = sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION

    // Thresholds tuned per sensor type
    private val threshold: Float = if (useLinear) 3.0f else 4.5f
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
            Log.i(TAG, "Sensor registered (type=${if (useLinear) "LINEAR" else "RAW"}, " +
                    "wakeUp=$wakeUpCapable, threshold=$threshold, consecutive=$consecutiveRequired)")
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
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val motionValue = if (useLinear) {
            // Already gravity-free: just take magnitude
            sqrt(x * x + y * y + z * z)
        } else {
            // Raw: magnitude minus gravity, absolute value
            val magnitude = sqrt(x * x + y * y + z * z)
            kotlin.math.abs(magnitude - GRAVITY)
        }

        if (motionValue > threshold) {
            consecutiveHighReadings++
            if (consecutiveHighReadings >= consecutiveRequired) {
                val now = System.currentTimeMillis()
                if (now - lastMotionTimeMs > minTriggerIntervalMs) {
                    lastMotionTimeMs = now
                    Log.w(TAG, "!!! MOTION DETECTED (value=${"%.2f".format(motionValue)}, sensor=${if (useLinear) "LINEAR" else "RAW"}) !!!")
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