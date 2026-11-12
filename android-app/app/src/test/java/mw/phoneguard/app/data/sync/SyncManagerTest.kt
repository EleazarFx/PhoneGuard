package mw.phoneguard.app.data.sync

import android.content.Context
import io.mockk.*
import org.junit.jupiter.api.AfterEach
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
        assert(syncManager != null)
    }
}
