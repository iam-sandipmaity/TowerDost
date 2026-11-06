package app.telecom.sitetracker.util

import androidx.compose.ui.graphics.Color

/**
 * Incident and Outage Severity definitions with strict color specifications.
 * Critical: #B91C1C
 * Major:    #C2410C
 * Minor:    #B45309
 * Info:     #475569
 */
enum class SeverityLevel(
    val code: String,
    val label: String,
    val color: Color,
    val weight: Int
) {
    CRITICAL("Critical", "Critical", Color(0xFFB91C1C), 4),
    MAJOR("Major", "Major", Color(0xFFC2410C), 3),
    MINOR("Minor", "Minor", Color(0xFFB45309), 2),
    INFO("Info", "Info", Color(0xFF475569), 1);

    companion object {
        fun fromString(value: String?): SeverityLevel {
            if (value.isNullOrBlank()) return MINOR
            return values().firstOrNull { it.code.equals(value.trim(), ignoreCase = true) } ?: MINOR
        }
    }
}

/**
 * Standard Status definitions for Incidents.
 */
enum class IncidentStatus(val code: String, val label: String) {
    OPEN("Open", "Open"),
    IN_PROGRESS("In Progress", "In Progress"),
    RESOLVED("Resolved", "Resolved"),
    CLOSED("Closed", "Closed");

    companion object {
        fun fromString(value: String?): IncidentStatus {
            if (value.isNullOrBlank()) return OPEN
            return values().firstOrNull { it.code.equals(value.trim(), ignoreCase = true) } ?: OPEN
        }
    }
}
