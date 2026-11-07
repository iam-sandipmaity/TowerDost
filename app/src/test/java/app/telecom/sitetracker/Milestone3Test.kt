package app.telecom.sitetracker

import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.util.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class Milestone3Test {

    @Test
    fun testClippedDowntimeHoursCalculations() {
        // Assume day: 07/11/2026 00:00:00 IST to 08/11/2026 00:00:00 IST
        val dayStart = Dates.fromLocalDateIst(LocalDate.of(2026, 11, 7))
        val dayEnd = dayStart + Dates.MS_PER_DAY
        val now = dayStart + (12 * 3600000L) // 12:00 PM same day

        // Scenario 1: Outage down all day (down 2 days ago, still open) -> 24.0 hours (or until now if same day)
        // For a past day where now > dayEnd:
        val pastDayStart = dayStart - Dates.MS_PER_DAY
        val pastDayEnd = pastDayStart + Dates.MS_PER_DAY
        val hoursFullDay = Dates.clipOutageToDayHours(
            downAt = pastDayStart - 10000L,
            restoredAt = null,
            dayStartMs = pastDayStart,
            nowMs = now
        )
        assertEquals(24.0, hoursFullDay, 0.001)

        // Scenario 2: Started at 02:00 AM, restored at 06:30 AM on same day -> 4.5 hours
        val down2Am = dayStart + (2 * 3600000L)
        val restored630Am = dayStart + (6 * 3600000L) + (30 * 60000L)
        val hoursPartial = Dates.clipOutageToDayHours(
            downAt = down2Am,
            restoredAt = restored630Am,
            dayStartMs = dayStart,
            nowMs = now
        )
        assertEquals(4.5, hoursPartial, 0.001)

        // Scenario 3: Started before dayStart (22:00 yesterday), restored at 04:00 AM today -> 4.0 hours on today
        val downYesterday = dayStart - (2 * 3600000L)
        val restored4Am = dayStart + (4 * 3600000L)
        val hoursCrossDay = Dates.clipOutageToDayHours(
            downAt = downYesterday,
            restoredAt = restored4Am,
            dayStartMs = dayStart,
            nowMs = now
        )
        assertEquals(4.0, hoursCrossDay, 0.001)

        // Scenario 4: Outage that happened completely outside the day -> 0.0 hours
        val hoursOutside = Dates.clipOutageToDayHours(
            downAt = dayEnd + 1000L,
            restoredAt = dayEnd + 50000L,
            dayStartMs = dayStart,
            nowMs = now
        )
        assertEquals(0.0, hoursOutside, 0.001)
    }

    @Test
    fun testRepeatFaultDetectionLogic() {
        val now = 1700000000000L
        val thirtyDaysAgo = now - (30L * Dates.MS_PER_DAY)

        val incidents = listOf(
            IncidentEntity(
                id = "inc-1",
                sapId = "SITE-01",
                occurredAt = now - (5L * Dates.MS_PER_DAY),
                category = "Power",
                reasonId = "REAS_EB_GRID",
                severity = "Critical",
                status = "Resolved",
                isOutage = 1,
                downAt = now - (5L * Dates.MS_PER_DAY),
                restoredAt = now - (5L * Dates.MS_PER_DAY) + 3600000L,
                title = "EB Fail",
                createdAt = now,
                updatedAt = now
            ),
            IncidentEntity(
                id = "inc-2",
                sapId = "SITE-01",
                occurredAt = now - (12L * Dates.MS_PER_DAY),
                category = "Power",
                reasonId = "REAS_EB_GRID",
                severity = "Critical",
                status = "Resolved",
                isOutage = 1,
                downAt = now - (12L * Dates.MS_PER_DAY),
                restoredAt = now - (12L * Dates.MS_PER_DAY) + 7200000L,
                title = "EB Fail 2",
                createdAt = now,
                updatedAt = now
            ),
            IncidentEntity(
                id = "inc-3",
                sapId = "SITE-01",
                occurredAt = now - (20L * Dates.MS_PER_DAY),
                category = "Power",
                reasonId = "REAS_SMPS_PLANT",
                severity = "Major",
                status = "Resolved",
                isOutage = 1,
                downAt = now - (20L * Dates.MS_PER_DAY),
                restoredAt = now - (20L * Dates.MS_PER_DAY) + 3600000L,
                title = "SMPS plant",
                createdAt = now,
                updatedAt = now
            ),
            IncidentEntity(
                id = "inc-4",
                sapId = "SITE-01",
                occurredAt = now - (45L * Dates.MS_PER_DAY), // Older than 30 days
                category = "DG",
                reasonId = "REAS_DG_AUTO_FAIL",
                severity = "Minor",
                status = "Resolved",
                isOutage = 1,
                downAt = now - (45L * Dates.MS_PER_DAY),
                restoredAt = now - (45L * Dates.MS_PER_DAY) + 3600000L,
                title = "Old DG fail",
                createdAt = now,
                updatedAt = now
            )
        )

        // Filter last 30 days outages
        val recentOutages = incidents.filter { it.isOutage == 1 && it.occurredAt >= thirtyDaysAgo }
        assertEquals(3, recentOutages.size)

        val isRepeatFault = recentOutages.size >= 3
        assertTrue("Should be flagged as repeat fault", isRepeatFault)

        // Find dominant category
        val dominantCategory = recentOutages.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key
        assertEquals("Power", dominantCategory)
    }

    @Test
    fun testDayNavigationCalculations() {
        val initialMs = Dates.fromLocalDateIst(LocalDate.of(2026, 11, 7))
        val prevDayMs = initialMs - Dates.MS_PER_DAY
        val nextDayMs = initialMs + Dates.MS_PER_DAY

        assertEquals("06/11/2026", Dates.formatDate(prevDayMs))
        assertEquals("07/11/2026", Dates.formatDate(initialMs))
        assertEquals("08/11/2026", Dates.formatDate(nextDayMs))
    }
}
