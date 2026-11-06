package app.telecom.sitetracker

import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.IncidentHistoryEntity
import app.telecom.sitetracker.data.entity.ReasonEntity
import app.telecom.sitetracker.data.seed.SampleDataSeeder
import app.telecom.sitetracker.util.Dates
import app.telecom.sitetracker.util.FakeAppClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class Milestone2Test {

    @Test
    fun testRunningDowntimeCalculation() {
        val clock = FakeAppClock(1700000000000L)
        val downAt = clock.currentTimeMillis() - (14.5 * Dates.MS_PER_HOUR).toLong()

        val runningHours = (clock.currentTimeMillis() - downAt) / Dates.MS_PER_HOUR
        assertEquals(14.5, runningHours, 0.001)

        val formatted = Dates.formatHours(runningHours)
        assertEquals("14.5h", formatted)

        // Still down day counter
        val daysDown = if (runningHours > 0) (runningHours / 24.0).toInt() + 1 else 1
        assertEquals(1, daysDown)

        // 36 hours down -> Day 2
        val down36h = clock.currentTimeMillis() - (36.0 * Dates.MS_PER_HOUR).toLong()
        val hours36 = (clock.currentTimeMillis() - down36h) / Dates.MS_PER_HOUR
        val daysDown36 = if (hours36 > 0) (hours36 / 24.0).toInt() + 1 else 1
        assertEquals(2, daysDown36)
    }

    @Test
    fun testDayBoundarySpanClipping() {
        // Assume day boundary from 00:00 to 24:00 (86,400,000 ms)
        val dayStart = 1700000000000L
        val dayEnd = dayStart + Dates.MS_PER_DAY

        // Outage 1: Starts before dayStart (2 hours before) and ends within the day (4 hours after dayStart)
        val downAt1 = dayStart - (2 * Dates.MS_PER_HOUR).toLong()
        val restoredAt1 = dayStart + (4 * Dates.MS_PER_HOUR).toLong()

        val clippedStart1 = Math.max(downAt1, dayStart)
        val clippedEnd1 = Math.min(restoredAt1, dayEnd)
        val clippedHours1 = (clippedEnd1 - clippedStart1) / Dates.MS_PER_HOUR
        assertEquals(4.0, clippedHours1, 0.001)

        // Outage 2: Starts within the day (at 20:00) and extends past dayEnd (to 04:00 next day)
        val downAt2 = dayEnd - (4 * Dates.MS_PER_HOUR).toLong()
        val restoredAt2 = dayEnd + (4 * Dates.MS_PER_HOUR).toLong()

        val clippedStart2 = Math.max(downAt2, dayStart)
        val clippedEnd2 = Math.min(restoredAt2, dayEnd)
        val clippedHours2 = (clippedEnd2 - clippedStart2) / Dates.MS_PER_HOUR
        assertEquals(4.0, clippedHours2, 0.001)

        // Outage 3: Entirely spans the full day (down before dayStart, restored after dayEnd)
        val downAt3 = dayStart - (10 * Dates.MS_PER_HOUR).toLong()
        val restoredAt3 = dayEnd + (10 * Dates.MS_PER_HOUR).toLong()

        val clippedStart3 = Math.max(downAt3, dayStart)
        val clippedEnd3 = Math.min(restoredAt3, dayEnd)
        val clippedHours3 = (clippedEnd3 - clippedStart3) / Dates.MS_PER_HOUR
        assertEquals(24.0, clippedHours3, 0.001)
    }

    @Test
    fun testAuditTrailDiffComputation() {
        val now = 1700000000000L
        val original = IncidentEntity(
            id = "INC_DIFF_01",
            sapId = "DEL-9001",
            occurredAt = now,
            category = "Power",
            reasonId = "REAS_EB_GRID",
            severity = "Major",
            status = "Open",
            title = "EB failure",
            downAt = now,
            restoredAt = null,
            createdAt = now,
            updatedAt = now
        )

        val updated = original.copy(
            severity = "Critical",
            status = "Resolved",
            restoredAt = now + 7200000L,
            actionTaken = "DG started and EB supply resumed"
        )

        // Compute diffs like IncidentRepository
        val diffs = mutableListOf<IncidentHistoryEntity>()
        fun check(field: String, oldVal: String?, newVal: String?) {
            if (oldVal != newVal) {
                diffs.add(
                    IncidentHistoryEntity(
                        id = UUID.randomUUID().toString(),
                        incidentId = updated.id,
                        changedAt = now + 7200000L,
                        changedBy = "Ramesh Kumar",
                        field = field,
                        oldValue = oldVal,
                        newValue = newVal
                    )
                )
            }
        }

        check("status", original.status, updated.status)
        check("severity", original.severity, updated.severity)
        check("category", original.category, updated.category)
        check("reason_id", original.reasonId, updated.reasonId)
        check("down_at", original.downAt?.toString(), updated.downAt?.toString())
        check("restored_at", original.restoredAt?.toString(), updated.restoredAt?.toString())
        check("title", original.title, updated.title)
        check("action_taken", original.actionTaken, updated.actionTaken)

        assertEquals(4, diffs.size)

        val statusDiff = diffs.find { it.field == "status" }
        assertNotNull(statusDiff)
        assertEquals("Open", statusDiff?.oldValue)
        assertEquals("Resolved", statusDiff?.newValue)

        val severityDiff = diffs.find { it.field == "severity" }
        assertNotNull(severityDiff)
        assertEquals("Major", severityDiff?.oldValue)
        assertEquals("Critical", severityDiff?.newValue)

        val restoredDiff = diffs.find { it.field == "restored_at" }
        assertNotNull(restoredDiff)
        assertNull(restoredDiff?.oldValue)
        assertEquals((now + 7200000L).toString(), restoredDiff?.newValue)
    }

    @Test
    fun testReasonCatalogueIntegrityAndDowntimeFlag() {
        val now = 1700000000000L
        val reasons = SampleDataSeeder.getDefaultReasons(now)

        assertTrue(reasons.isNotEmpty())

        // Verify SLA exclusion
        val planned = reasons.filter { it.countsTowardDowntime == 0 }
        assertTrue(planned.isNotEmpty())
        assertTrue(planned.any { it.label.contains("Planned", ignoreCase = true) })
        assertTrue(planned.any { it.label.contains("Load shedding", ignoreCase = true) })

        // Verify active filtering
        val active = reasons.filter { it.isHidden == 0 }
        assertEquals(reasons.size, active.size)

        // Hiding a reason removes it from active picker
        val withHidden = reasons.map {
            if (it.id == "REAS_NO_RECORD") it.copy(isHidden = 1) else it
        }
        val activeAfterHide = withHidden.filter { it.isHidden == 0 }
        assertEquals(reasons.size - 1, activeAfterHide.size)
        assertFalse(activeAfterHide.any { it.id == "REAS_NO_RECORD" })
    }

    @Test
    fun testOutageFilterQueryMatching() {
        val now = 1700000000000L
        val outages = listOf(
            IncidentEntity(
                id = "1",
                sapId = "DEL-9001",
                occurredAt = now,
                category = "Power",
                severity = "Critical",
                status = "Open",
                title = "EB Phase Cutout",
                engineer = "Amit Sharma"
            ),
            IncidentEntity(
                id = "2",
                sapId = "MUM-9009",
                occurredAt = now,
                category = "Transmission",
                severity = "Major",
                status = "Open",
                title = "Fibre Cut on Backhaul",
                engineer = "Pooja Patel"
            )
        )

        fun search(query: String): List<IncidentEntity> {
            if (query.isBlank()) return outages
            val q = query.trim().lowercase()
            return outages.filter {
                it.sapId.lowercase().contains(q) ||
                it.title.lowercase().contains(q) ||
                it.category.lowercase().contains(q) ||
                (it.engineer?.lowercase()?.contains(q) == true)
            }
        }

        assertEquals(1, search("DEL").size)
        assertEquals(1, search("Fibre").size)
        assertEquals(1, search("Amit").size)
        assertEquals(1, search("Transmission").size)
        assertEquals(2, search("").size)
        assertEquals(0, search("Kolkata").size)
    }
}
