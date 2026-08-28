package com.clxmhcs.chinaunicom.automation

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationCoordinatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun beforeScheduledTimeQueuesToday() {
        val now = time(2026, 8, 28, 7, 45)

        val target = nextAutomationOccurrence(
            now = now,
            scheduledMinute = 8 * 60,
            compensationMinutes = 6,
        )

        assertEquals(time(2026, 8, 28, 8, 0), target)
    }

    @Test
    fun insideCompensationWindowRunsImmediately() {
        val now = time(2026, 8, 28, 8, 5)

        val target = nextAutomationOccurrence(
            now = now,
            scheduledMinute = 8 * 60,
            compensationMinutes = 6,
        )

        assertEquals(now, target)
    }

    @Test
    fun afterCompensationWindowQueuesNextDay() {
        val now = time(2026, 8, 28, 8, 7)

        val target = nextAutomationOccurrence(
            now = now,
            scheduledMinute = 8 * 60,
            compensationMinutes = 6,
        )

        assertEquals(time(2026, 8, 29, 8, 0), target)
    }

    @Test
    fun followingOccurrenceIsAlwaysStrictlyFuture() {
        val now = time(2026, 8, 28, 8, 1)

        val target = nextFutureAutomationOccurrence(
            now = now,
            scheduledMinute = 8 * 60,
        )

        assertEquals(time(2026, 8, 29, 8, 0), target)
    }

    @Test
    fun followingOccurrenceCanUseLaterSlotToday() {
        val now = time(2026, 8, 28, 8, 1)

        val target = nextFutureAutomationOccurrence(
            now = now,
            scheduledMinute = 11 * 60,
        )

        assertEquals(time(2026, 8, 28, 11, 0), target)
    }

    private fun time(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): ZonedDateTime = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
}
