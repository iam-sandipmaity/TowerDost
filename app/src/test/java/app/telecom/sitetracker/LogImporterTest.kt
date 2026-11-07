package app.telecom.sitetracker

import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.import.LogColumnMapper
import app.telecom.sitetracker.data.import.LogTargetField
import app.telecom.sitetracker.util.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class LogImporterTest {

    @Test
    fun testLogColumnMapperMatchesStandardExportHeaders() {
        val headers = listOf(
            "SAP ID", "Title", "Type", "Severity", "Category", "Status",
            "Down At (IST)", "Restored At (IST)", "Downtime Hours", "Engineer",
            "Ticket Ref", "Action Taken", "Description", "GPS Lat", "GPS Lng"
        )

        val mappings = LogColumnMapper.generateInitialMappings(headers)

        assertEquals(LogTargetField.SAP_ID, mappings[0].targetField)
        assertEquals(LogTargetField.TITLE, mappings[1].targetField)
        assertEquals(LogTargetField.TYPE, mappings[2].targetField)
        assertEquals(LogTargetField.SEVERITY, mappings[3].targetField)
        assertEquals(LogTargetField.CATEGORY, mappings[4].targetField)
        assertEquals(LogTargetField.STATUS, mappings[5].targetField)
        assertEquals(LogTargetField.DOWN_AT, mappings[6].targetField)
        assertEquals(LogTargetField.RESTORED_AT, mappings[7].targetField)
        assertEquals(LogTargetField.IGNORE, mappings[8].targetField) // Downtime Hours is derived
        assertEquals(LogTargetField.ENGINEER, mappings[9].targetField)
        assertEquals(LogTargetField.TICKET_REF, mappings[10].targetField)
        assertEquals(LogTargetField.ACTION_TAKEN, mappings[11].targetField)
        assertEquals(LogTargetField.DESCRIPTION, mappings[12].targetField)
        assertEquals(LogTargetField.LAT, mappings[13].targetField)
        assertEquals(LogTargetField.LNG, mappings[14].targetField)
    }

    @Test
    fun testLogColumnMapperMatchesAlternativeNocHeaders() {
        assertEquals(LogTargetField.SAP_ID, LogColumnMapper.matchField("Site ID"))
        assertEquals(LogTargetField.SAP_ID, LogColumnMapper.matchField("Tower Code"))
        assertEquals(LogTargetField.TITLE, LogColumnMapper.matchField("Alarm Name"))
        assertEquals(LogTargetField.DOWN_AT, LogColumnMapper.matchField("Outage Start Time"))
        assertEquals(LogTargetField.RESTORED_AT, LogColumnMapper.matchField("Clearance Time"))
        assertEquals(LogTargetField.TICKET_REF, LogColumnMapper.matchField("TT Number"))
        assertEquals(LogTargetField.ENGINEER, LogColumnMapper.matchField("Technician Name"))
        assertEquals(LogTargetField.ACTION_TAKEN, LogColumnMapper.matchField("Remedy / Work Done"))
    }

    @Test
    fun testDeduplicationDetection() {
        val existing = IncidentEntity(
            id = "log-101",
            sapId = "DEL-001",
            occurredAt = 1786780000000L,
            downAt = 1786780000000L,
            title = "EB Fail",
            category = "Issue",
            severity = "Critical",
            status = "Open",
            isOutage = 1,
            ticketRef = "INC-9999"
        )

        // Matching by exact ID
        assertTrue(LogColumnMapper.isMatch(existing, incomingId = "log-101", incomingSapId = "DEL-001", incomingTimestamp = null, incomingTicketRef = null))

        // Matching by Ticket Ref
        assertTrue(LogColumnMapper.isMatch(existing, incomingId = null, incomingSapId = "DEL-001", incomingTimestamp = null, incomingTicketRef = "INC-9999"))

        // Matching by SAP ID and timestamp within same minute (+15s)
        assertTrue(LogColumnMapper.isMatch(existing, incomingId = null, incomingSapId = "DEL-001", incomingTimestamp = 1786780015000L, incomingTicketRef = null))

        // Different SAP ID does not match
        assertFalse(LogColumnMapper.isMatch(existing, incomingId = null, incomingSapId = "DEL-002", incomingTimestamp = 1786780000000L, incomingTicketRef = null))

        // Same SAP ID but hours apart does not match
        assertFalse(LogColumnMapper.isMatch(existing, incomingId = null, incomingSapId = "DEL-001", incomingTimestamp = 1786780000000L + (5 * 3600000L), incomingTicketRef = null))
    }
}
