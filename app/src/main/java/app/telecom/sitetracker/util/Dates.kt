package app.telecom.sitetracker.util

import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Date and time utilities strictly anchored to Indian Standard Time (IST - Asia/Kolkata, UTC+5:30).
 * India has no Daylight Saving Time, guaranteeing every calendar day is exactly 24 hours (86,400,000 ms).
 */
object Dates {
    val IST_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    const val MS_PER_DAY: Long = 86_400_000L
    const val MS_PER_HOUR: Double = 3_600_000.0

    private val DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)

    private val DATE_TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ENGLISH)

    private val TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    /**
     * Returns IST midnight (00:00:00.000) epoch milliseconds for the given epoch ms.
     */
    fun getDayStartEpochMs(epochMs: Long): Long {
        val instant = Instant.ofEpochMilli(epochMs)
        val zonedDateTime = instant.atZone(IST_ZONE)
        return zonedDateTime.toLocalDate().atStartOfDay(IST_ZONE).toInstant().toEpochMilli()
    }

    /**
     * Returns IST end of day (next day 00:00:00.000) epoch milliseconds.
     */
    fun getDayEndEpochMs(epochMs: Long): Long {
        return getDayStartEpochMs(epochMs) + MS_PER_DAY
    }

    /**
     * Returns LocalDate in IST for an epoch ms.
     */
    fun toLocalDateIst(epochMs: Long): LocalDate {
        return Instant.ofEpochMilli(epochMs).atZone(IST_ZONE).toLocalDate()
    }

    /**
     * Returns epoch ms for start of day of given IST LocalDate.
     */
    fun fromLocalDateIst(date: LocalDate): Long {
        return date.atStartOfDay(IST_ZONE).toInstant().toEpochMilli()
    }

    /**
     * Formats epoch ms as dd/MM/yyyy in IST.
     */
    fun formatDate(epochMs: Long?): String {
        if (epochMs == null || epochMs <= 0) return "-"
        return Instant.ofEpochMilli(epochMs).atZone(IST_ZONE).format(DATE_FORMATTER)
    }

    /**
     * Formats epoch ms as dd/MM/yyyy HH:mm in IST.
     */
    fun formatDateTime(epochMs: Long?): String {
        if (epochMs == null || epochMs <= 0) return "-"
        return Instant.ofEpochMilli(epochMs).atZone(IST_ZONE).format(DATE_TIME_FORMATTER)
    }

    /**
     * Formats epoch ms as HH:mm in IST.
     */
    fun formatTime(epochMs: Long?): String {
        if (epochMs == null || epochMs <= 0) return "-"
        return Instant.ofEpochMilli(epochMs).atZone(IST_ZONE).format(TIME_FORMATTER)
    }

    /**
     * Parses dd/MM/yyyy and HH:mm in IST timezone to epoch milliseconds.
     */
    fun parseDateTime(dateStr: String, timeStr: String): Long? {
        return try {
            val d = LocalDate.parse(dateStr.trim(), DATE_FORMATTER)
            val t = java.time.LocalTime.parse(timeStr.trim(), TIME_FORMATTER)
            LocalDateTime.of(d, t).atZone(IST_ZONE).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Calculates duration in hours between down and restored/now.
     */
    fun calculateDurationHours(downAt: Long, restoredAt: Long?, nowMs: Long): Double {
        val effectiveEnd = restoredAt ?: nowMs
        if (effectiveEnd <= downAt) return 0.0
        return (effectiveEnd - downAt) / MS_PER_HOUR
    }

    /**
     * Calculates downtime clipped strictly to a specific 24-hour day in hours.
     * Overlap span formula: (min(restored ?: now, dayEnd) - max(down, dayStart)) / 3600000.0
     */
    fun clipOutageToDayHours(downAt: Long, restoredAt: Long?, dayStartMs: Long, nowMs: Long): Double {
        val dayEndMs = dayStartMs + MS_PER_DAY
        val effectiveEnd = restoredAt ?: nowMs

        if (downAt >= dayEndMs || effectiveEnd <= dayStartMs) {
            return 0.0
        }

        val overlapStart = maxOf(downAt, dayStartMs)
        val overlapEnd = minOf(effectiveEnd, dayEndMs)

        return if (overlapEnd > overlapStart) {
            (overlapEnd - overlapStart) / MS_PER_HOUR
        } else {
            0.0
        }
    }

    /**
     * Formats Indian numbers with 1,00,000 grouping.
     */
    fun formatIndianNumber(value: Long): String {
        val s = value.toString()
        if (s.length <= 3) return s
        val last3 = s.substring(s.length - 3)
        val remaining = s.substring(0, s.length - 3)
        val groupedRemaining = remaining.reversed().chunked(2).joinToString(",").reversed()
        return "$groupedRemaining,$last3"
    }

    /**
     * Formats hours to 1 decimal place (e.g. 14.5 h) or whole integer if no fraction.
     */
    fun formatHours(hours: Double): String {
        return if (hours % 1.0 == 0.0) {
            "${hours.toLong()}h"
        } else {
            val df = DecimalFormat("#0.0")
            "${df.format(hours)}h"
        }
    }

    /**
     * Validates if the device clock appears skewed:
     * - Earlier than newest stored record
     * - Current year earlier than application build year
     */
    fun isClockSkewed(clockMs: Long, newestRecordMs: Long?, buildYear: Int = 2026): Boolean {
        if (newestRecordMs != null && clockMs < newestRecordMs) {
            return true
        }
        val currentYear = Instant.ofEpochMilli(clockMs).atZone(IST_ZONE).year
        return currentYear < buildYear
    }
}
