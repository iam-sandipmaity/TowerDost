package app.telecom.sitetracker

import app.telecom.sitetracker.data.backup.BackupData
import app.telecom.sitetracker.data.backup.BackupManager
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.ReasonEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {

    @Test
    fun testBackupJsonSerializationAndParsingRoundTrip() {
        val sites = listOf(
            SiteEntity(
                sapId = "DEL-001",
                sapNorm = "del001",
                name = "Connaught Place Hub",
                lat = 28.6304,
                lng = 77.2177,
                circle = "Delhi",
                district = "Central Delhi",
                city = "New Delhi",
                technology = "4G/5G",
                siteType = "RTT",
                status = "Active",
                sourceFile = "test_sheet.xlsx",
                createdAt = 1786700000000L,
                updatedAt = 1786700000000L
            )
        )

        val fields = listOf(
            SiteFieldEntity(
                sapId = "DEL-001",
                fieldKey = "DG Capacity",
                fieldValue = "25 kVA",
                groupName = "Diesel Generator",
                sortOrder = 0
            )
        )

        val incidents = listOf(
            IncidentEntity(
                id = "inc-001",
                sapId = "DEL-001",
                occurredAt = 1786750000000L,
                category = "Issue",
                severity = "Critical",
                status = "Open",
                isOutage = 1,
                downAt = 1786750000000L,
                restoredAt = null,
                title = "EB Fail - DG Auto-Start Error",
                engineer = "Aakash Sharma",
                ticketRef = "TT-998822"
            )
        )

        val reasons = listOf(
            ReasonEntity(
                id = "reason-eb-01",
                label = "Grid / EB Supply Failure",
                groupName = "Power",
                category = "Power",
                countsTowardDowntime = 1,
                sortOrder = 1,
                isHidden = 0,
                isSeeded = 1
            )
        )

        val backupData = BackupData(
            version = 1,
            appName = "TowerDost",
            exportedAt = 1786780000000L,
            sites = sites,
            fields = fields,
            incidents = incidents,
            reasons = reasons
        )

        val jsonString = BackupManager.serializeBackupJson(backupData)

        assertTrue(jsonString.contains("\"version\":1"))
        assertTrue(jsonString.contains("\"appName\":\"TowerDost\""))
        assertTrue(jsonString.contains("Connaught Place Hub"))
        assertTrue(jsonString.contains("DG Capacity"))
        assertTrue(jsonString.contains("TT-998822"))

        val parsed = BackupManager.parseBackupJson(jsonString)

        assertEquals(1, parsed.version)
        assertEquals("TowerDost", parsed.appName)
        assertEquals(1, parsed.sites.size)
        assertEquals("DEL-001", parsed.sites[0].sapId)
        assertEquals("Connaught Place Hub", parsed.sites[0].name)
        assertEquals(28.6304, parsed.sites[0].lat!!, 0.0001)

        assertEquals(1, parsed.fields.size)
        assertEquals("DG Capacity", parsed.fields[0].fieldKey)
        assertEquals("25 kVA", parsed.fields[0].fieldValue)

        assertEquals(1, parsed.incidents.size)
        assertEquals("inc-001", parsed.incidents[0].id)
        assertEquals("TT-998822", parsed.incidents[0].ticketRef)
        assertEquals(1, parsed.incidents[0].isOutage)

        assertEquals(1, parsed.reasons.size)
        assertEquals("reason-eb-01", parsed.reasons[0].id)
        assertEquals("Grid / EB Supply Failure", parsed.reasons[0].label)
        assertEquals("Power", parsed.reasons[0].groupName)
        assertEquals(1, parsed.reasons[0].countsTowardDowntime)
        assertEquals(1, parsed.reasons[0].isSeeded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testParseBackupJsonRejectsIncompatibleApp() {
        val invalidJson = "{\"version\":1,\"appName\":\"OtherApp\",\"sites\":[]}"
        BackupManager.parseBackupJson(invalidJson)
    }
}
