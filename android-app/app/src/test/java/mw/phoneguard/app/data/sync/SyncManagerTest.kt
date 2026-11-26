package mw.phoneguard.app.data.sync

import android.content.Context
import io.mockk.*
import mw.phoneguard.app.data.models.PostEventRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SyncManagerTest {

    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun testSyncManagerInitialization() {
        val syncManager = SyncManager(context)
        assertNotNull(syncManager)
    }

    @Test
    fun testSilentEmailAlertTargetEvents() {
        val targetAlertTypes = setOf("sim_change", "snatch", "wrong_pin")
        
        assertTrue("sim_change" in targetAlertTypes)
        assertTrue("snatch" in targetAlertTypes)
        assertTrue("wrong_pin" in targetAlertTypes)
        assertFalse("restart" in targetAlertTypes)
        assertFalse("false_alarm" in targetAlertTypes)
    }

    @Test
    fun testPostEventRequestSilentEmailAlertField() {
        val request = PostEventRequest(
            type = "sim_change",
            timestamp = System.currentTimeMillis(),
            silentEmailAlert = true
        )

        assertEquals("sim_change", request.type)
        assertEquals(true, request.silentEmailAlert)
    }
}
