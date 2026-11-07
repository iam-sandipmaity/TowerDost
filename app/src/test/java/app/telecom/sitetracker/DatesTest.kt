package app.telecom.sitetracker

import app.telecom.sitetracker.util.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DatesTest {

    @Test
    fun testDayStartAndEndEpochMsAreStrictlyIST() {
        // 2026-11-06 14:30:00 IST -> Day start is 2026-11-06 00:00:00 IST
        val localDate = LocalDate.of(2026, 11, 6)
        val dayStartMs = Dates.fromLocalDateIst(localDate)
        val dayEndMs = Dates.getDayEndEpochMs(dayStartMs)

        // Verify dayEnd is exactly 24 hours (86,400,000 ms) after dayStart
        assertEquals(86_400_000L, dayEndMs - dayStartMs)

        // Midday timestamp: 14:00 IST
        val middayMs = dayStartMs + (14 * 3600000L)
        assertEquals(dayStartMs, Dates.getDayStartEpochMs(middayMs))
        assertEquals(dayEndMs, Dates.getDayEndEpochMs(middayMs))
    }

    @Test
    fun testMidnightCrossingOutageClipping() {
        // Outage starts at 22:00 on Day 1 and ends at 04:00 on Day 2
        val day1Start = Dates.fromLocalDateIst(LocalDate.of(2026, 11, 6))
        val downAt = day1Start + (22 * 3600000L)
        val restoredAt = day1Start + Dates.MS_PER_DAY + (4 * 3600000L) // 04:00 Day 2

        val day2Start = day1Start + Dates.MS_PER_DAY

        // Day 1: 22:00 to 24:00 = exactly 2.0 hours
        val day1Hours = Dates.clipOutageToDayHours(downAt, restoredAt, day1Start, restoredAt)
        assertEquals(2.0, day1Hours, 0.001)

        // Day 2: 00:00 to 04:00 = exactly 4.0 hours
        val day2Hours = Dates.clipOutageToDayHours(downAt, restoredAt, day2Start, restoredAt)
        assertEquals(4.0, day2Hours, 0.001)

        // Total duration is 6.0 hours
        assertEquals(6.0, day1Hours + day2Hours, 0.001)
    }

    @Test
    fun testFiveDayOutageClippingAcrossInterveningDays() {
        val day1Start = Dates.fromLocalDateIst(LocalDate.of(2026, 11, 1))
        val downAt = day1Start + (10 * 3600000L) // 10:00 AM on Nov 1
        val restoredAt = day1Start + (5L * Dates.MS_PER_DAY) + (14 * 3600000L) // 2:00 PM on Nov 6

        // Day 1 (Nov 1): 10:00 to 24:00 = 14 hours
        assertEquals(14.0, Dates.clipOutageToDayHours(downAt, restoredAt, day1Start, restoredAt), 0.001)

        // Day 2 (Nov 2): Full 24 hours
        val day2Start = day1Start + Dates.MS_PER_DAY
        assertEquals(24.0, Dates.clipOutageToDayHours(downAt, restoredAt, day2Start, restoredAt), 0.001)

        // Day 3 (Nov 3): Full 24 hours
        val day3Start = day1Start + 2 * Dates.MS_PER_DAY
        assertEquals(24.0, Dates.clipOutageToDayHours(downAt, restoredAt, day3Start, restoredAt), 0.001)

        // Day 4 (Nov 4): Full 24 hours
        val day4Start = day1Start + 3 * Dates.MS_PER_DAY
        assertEquals(24.0, Dates.clipOutageToDayHours(downAt, restoredAt, day4Start, restoredAt), 0.001)

        // Day 5 (Nov 5): Full 24 hours
        val day5Start = day1Start + 4 * Dates.MS_PER_DAY
        assertEquals(24.0, Dates.clipOutageToDayHours(downAt, restoredAt, day5Start, restoredAt), 0.001)

        // Day 6 (Nov 6): 00:00 to 14:00 = 14 hours
        val day6Start = day1Start + 5 * Dates.MS_PER_DAY
        assertEquals(14.0, Dates.clipOutageToDayHours(downAt, restoredAt, day6Start, restoredAt), 0.001)

        // Day 7 (Nov 7): 0 hours (already restored)
        val day7Start = day1Start + 6 * Dates.MS_PER_DAY
        assertEquals(0.0, Dates.clipOutageToDayHours(downAt, restoredAt, day7Start, restoredAt), 0.001)
    }

    @Test
    fun testIndianNumberFormatting() {
        assertEquals("0", Dates.formatIndianNumber(0))
        assertEquals("500", Dates.formatIndianNumber(500))
        assertEquals("1,000", Dates.formatIndianNumber(1000))
        assertEquals("10,000", Dates.formatIndianNumber(10000))
        assertEquals("1,00,000", Dates.formatIndianNumber(100000))
        assertEquals("25,43,210", Dates.formatIndianNumber(2543210))
    }

    @Test
    fun testClockSkewDetection() {
        val nowMs = System.currentTimeMillis()

        // Clock earlier than newest record should be detected as skewed
        assertTrue(Dates.isClockSkewed(nowMs - 50000, nowMs))

        // Normal clock should not be skewed
        assertFalse(Dates.isClockSkewed(nowMs, nowMs - 50000))

        // Year earlier than build year (2026) is skewed
        val year2020Ms = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli()
        assertTrue(Dates.isClockSkewed(year2020Ms, null, buildYear = 2026))
    }

    @Test
    fun testParseFlexibleDateTime_variousFormats() {
        // dd/MM/yyyy HH:mm
        val t1 = Dates.parseFlexibleDateTime("15/08/2026 14:30")
        assertTrue(t1 != null && t1 > 0)

        // dd/MM/yyyy HH:mm:ss
        val t2 = Dates.parseFlexibleDateTime("15/08/2026 14:30:45")
        assertTrue(t2 != null && t2 > 0)

        // yyyy-MM-dd HH:mm:ss
        val t3 = Dates.parseFlexibleDateTime("2026-08-15 14:30:00")
        assertTrue(t3 != null && t3 > 0)

        // yyyy-MM-dd'T'HH:mm:ss
        val t4 = Dates.parseFlexibleDateTime("2026-08-15T14:30:00")
        assertTrue(t4 != null && t4 > 0)

        // dd MMM yyyy, HH:mm
        val t5 = Dates.parseFlexibleDateTime("15 Aug 2026, 14:30")
        assertTrue(t5 != null && t5 > 0)

        // dd-MM-yyyy HH:mm
        val t6 = Dates.parseFlexibleDateTime("15-08-2026 14:30")
        assertTrue(t6 != null && t6 > 0)

        // dd/MM/yyyy (fallback to midnight)
        val t7 = Dates.parseFlexibleDateTime("15/08/2026")
        assertTrue(t7 != null && t7 > 0)

        // Raw epoch ms
        val now = 1786780000000L
        val t8 = Dates.parseFlexibleDateTime(now.toString())
        assertEquals(now, t8)

        // Null or blank or invalid
        assertEquals(null, Dates.parseFlexibleDateTime(null))
        assertEquals(null, Dates.parseFlexibleDateTime(""))
        assertEquals(null, Dates.parseFlexibleDateTime("   "))
        assertEquals(null, Dates.parseFlexibleDateTime("invalid_garbage_timestamp"))
    }

    @Test
    fun testIsWithinMinute() {
        val base = 1786780000000L
        assertTrue(Dates.isWithinMinute(base, base + 30000L))
        assertTrue(Dates.isWithinMinute(base, base - 59000L))
        assertTrue(Dates.isWithinMinute(base, base))
        assertFalse(Dates.isWithinMinute(base, base + 61000L))
        assertFalse(Dates.isWithinMinute(base, base - 61000L))
        assertFalse(Dates.isWithinMinute(null, base))
        assertFalse(Dates.isWithinMinute(base, null))
    }
}
