package mw.phoneguard.app.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SnatchGuardTest {

    private lateinit var context: Context
    private lateinit var dpm: DevicePolicyManager
    private lateinit var notificationManager: NotificationManager

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any() as String) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any() as String) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(Looper::class)
        val looper = mockk<Looper>(relaxed = true)
        every { Looper.getMainLooper() } returns looper

        mockkStatic(PendingIntent::class)
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk(relaxed = true)

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().postDelayed(any(), any()) } returns true
        every { anyConstructed<Handler>().removeCallbacks(any()) } just Runs

        context = mockk(relaxed = true)
        dpm = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)

        every { context.getSystemService(Context.DEVICE_POLICY_SERVICE) } returns dpm
        every { context.getSystemService(NotificationManager::class.java) } returns notificationManager
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        every { context.applicationContext } returns context
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun testSnatchGuardStartAndStop() {
        val snatchGuard = SnatchGuard(context, gracePeriodMs = 1000L)
        snatchGuard.start()
        snatchGuard.stop()
    }
}
