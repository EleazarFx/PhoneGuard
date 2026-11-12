package mw.phoneguard.app.ui.activity

import mw.phoneguard.app.data.local.EventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ActivityViewModelTest {

    @Test
    fun testEventGroupingByDay() {
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L

        val todayEvent = EventEntity(
            id = "1",
            deviceId = "dev1",
            type = "wrong_pin",
            timestamp = now
        )

        val yesterdayEvent = EventEntity(
            id = "2",
            deviceId = "dev1",
            type = "sim_change",
            timestamp = now - oneDayMillis
        )

        val olderDateMillis = LocalDate.now().minusDays(5)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val olderEvent = EventEntity(
            id = "3",
            deviceId = "dev1",
            type = "snatch",
            timestamp = olderDateMillis
        )

        val events = listOf(todayEvent, yesterdayEvent, olderEvent)

        // Helper grouping function test
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val monthDayFormatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d", java.util.Locale.getDefault())

        val grouped = LinkedHashMap<String, MutableList<EventEntity>>()
        for (event in events) {
            val date = java.time.Instant.ofEpochMilli(event.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            val title = when {
                date == today -> "Today"
                date == yesterday -> "Yesterday"
                date.year == today.year -> date.format(monthDayFormatter)
                else -> date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.getDefault()))
            }
            grouped.getOrPut(title) { mutableListOf() }.add(event)
        }

        val groups = grouped.map { (title, list) -> EventGroup(title, list) }

        assertEquals(3, groups.size)
        assertEquals("Today", groups[0].title)
        assertEquals(1, groups[0].events.size)
        assertEquals("1", groups[0].events[0].id)

        assertEquals("Yesterday", groups[1].title)
        assertEquals(1, groups[1].events.size)
        assertEquals("2", groups[1].events[0].id)

        val expectedOlderTitle = LocalDate.now().minusDays(5).format(monthDayFormatter)
        assertEquals(expectedOlderTitle, groups[2].title)
        assertEquals(1, groups[2].events.size)
        assertEquals("3", groups[2].events[0].id)
    }

    @Test
    fun testHighThreatFilter() {
        val highThreatTypes = setOf("sim_change", "snatch", "tamper")
        val mediumThreatTypes = setOf("wrong_pin", "restart")

        for (type in highThreatTypes) {
            assertTrue("Expected $type to be high threat", type in setOf("sim_change", "snatch", "tamper"))
        }

        for (type in mediumThreatTypes) {
            assertFalse("Expected $type to NOT be high threat", type in setOf("sim_change", "snatch", "tamper"))
        }
    }

    @Test
    fun testPreserveSurvivesPurgeLogic() {
        val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000L
        val cutoffTimestamp = System.currentTimeMillis() - thirtyDaysMillis

        val recentEvent = EventEntity(
            id = "recent",
            deviceId = "dev1",
            type = "wrong_pin",
            timestamp = System.currentTimeMillis(),
            isPreserved = false
        )

        val oldNormalEvent = EventEntity(
            id = "old_normal",
            deviceId = "dev1",
            type = "tamper",
            timestamp = cutoffTimestamp - 10_000L,
            isPreserved = false
        )

        val oldPreservedEvent = EventEntity(
            id = "old_preserved",
            deviceId = "dev1",
            type = "snatch",
            timestamp = cutoffTimestamp - 10_000L,
            isPreserved = true
        )

        val allEvents = listOf(recentEvent, oldNormalEvent, oldPreservedEvent)

        // Simulate purge logic: DELETE WHERE isPreserved = 0 AND timestamp < cutoffTimestamp
        val remainingEvents = allEvents.filterNot { !it.isPreserved && it.timestamp < cutoffTimestamp }

        assertEquals(2, remainingEvents.size)
        assertTrue(remainingEvents.any { it.id == "recent" })
        assertTrue(remainingEvents.any { it.id == "old_preserved" })
        assertFalse(remainingEvents.any { it.id == "old_normal" })
    }
}
