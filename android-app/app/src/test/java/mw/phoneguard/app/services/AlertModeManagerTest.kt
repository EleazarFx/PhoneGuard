package mw.phoneguard.app.services

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AlertModeManagerTest {

    private lateinit var context: Context
    private lateinit var sensorManager: SensorManager
    private lateinit var sensor: Sensor

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any() as String) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any() as String) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        context = mockk(relaxed = true)
        sensorManager = mockk(relaxed = true)
        sensor = mockk(relaxed = true)

        every { context.getSystemService(Context.SENSOR_SERVICE) } returns sensorManager
        every { sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION, true) } returns sensor
        every { sensor.type } returns Sensor.TYPE_LINEAR_ACCELERATION
        every { sensorManager.registerListener(any<SensorEventListener>(), any<Sensor>(), any<Int>()) } returns true
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun testAlertModeStartStop() {
        var motionDetected = false
        val manager = AlertModeManager(context) {
            motionDetected = true
        }

        manager.start()
        manager.stop()
    }
}
