package app.telecom.sitetracker

import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.export.Exporter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SiteUpdateAndExportTest {

    @Test
    fun testExportSitesCsvIncludesUpdatedOverviewAndDynamicSpecs() {
        val tempDir = Files.createTempDirectory("td_export_test").toFile()

        val site1 = SiteEntity(
            sapId = "WB-KOL-1001",
            sapNorm = "WBKOL1001",
            name = "Salt Lake Sector V Tower",
            city = "Kolkata",
            district = "North 24 Parganas",
            circle = "West Bengal",
            state = "WB",
            lat = 22.5801,
            lng = 88.4215,
            technology = "4G/5G",
            siteType = "RTT",
            status = "On Air",
            address = "Sector V, Salt Lake City",
            notes = "Upgraded SMPS rectifier modules",
            updatedAt = 1762512000000L
        )

        val specs = listOf(
            SiteFieldEntity(
                id = 1L,
                sapId = "WB-KOL-1001",
                fieldKey = "SMPS CAPACITY",
                fieldValue = "48V 150A Modular",
                groupName = "POWER",
                sortOrder = 1
            ),
            SiteFieldEntity(
                id = 2L,
                sapId = "WB-KOL-1001",
                fieldKey = "DG CAPACITY",
                fieldValue = "15 KVA Silent",
                groupName = "POWER",
                sortOrder = 2
            ),
            SiteFieldEntity(
                id = 3L,
                sapId = "WB-KOL-1001",
                fieldKey = "BATTERY BANK",
                fieldValue = "Amaron 48V 300AH",
                groupName = "BATTERY",
                sortOrder = 3
            )
        )

        val csvFile = Exporter.exportSitesCsv(tempDir, listOf(site1), specs)
        assertTrue(csvFile.exists())

        val content = csvFile.readText()

        // 1. Check overview columns & updated values
        assertTrue(content.contains("SAP ID"))
        assertTrue(content.contains("Site Name"))
        assertTrue(content.contains("Circle"))
        assertTrue(content.contains("District"))
        assertTrue(content.contains("City"))
        assertTrue(content.contains("Technology"))
        assertTrue(content.contains("Site Type"))
        assertTrue(content.contains("Latitude"))
        assertTrue(content.contains("Longitude"))
        assertTrue(content.contains("Salt Lake Sector V Tower"))
        assertTrue(content.contains("Kolkata"))
        assertTrue(content.contains("22.5801"))
        assertTrue(content.contains("88.4215"))

        // 2. Check dynamic specification columns and values
        assertTrue(content.contains("SMPS CAPACITY"))
        assertTrue(content.contains("DG CAPACITY"))
        assertTrue(content.contains("BATTERY BANK"))
        assertTrue(content.contains("48V 150A Modular"))
        assertTrue(content.contains("15 KVA Silent"))
        assertTrue(content.contains("Amaron 48V 300AH"))
    }

    @Test
    fun testExportSingleSiteReport() {
        val tempDir = Files.createTempDirectory("td_single_export_test").toFile()

        val site = SiteEntity(
            sapId = "WB-NDRT-9009",
            sapNorm = "WBNDRT9009",
            name = "Nadia Central Hub",
            city = "Nadia",
            district = "Nadia",
            circle = "West Bengal",
            state = "WB",
            lat = 23.4712,
            lng = 88.5523,
            technology = "4G",
            siteType = "GBT",
            status = "Active",
            updatedAt = 1762513000000L
        )

        val specs = listOf(
            SiteFieldEntity(
                id = 10L,
                sapId = "WB-NDRT-9009",
                fieldKey = "EB POWER STATUS",
                fieldValue = "Commercial 3-Phase",
                groupName = "POWER"
            )
        )

        val incident = IncidentEntity(
            id = "INC-12345",
            sapId = "WB-NDRT-9009",
            title = "Rectifier SMPS Fan Failure",
            category = "Maintenance",
            severity = "P2",
            status = "Resolved",
            occurredAt = 1762500000000L,
            downAt = null,
            restoredAt = 1762503600000L,
            engineer = "A. Sen",
            actionTaken = "Replaced 48V cooling fan and tested RM load"
        )

        val csvFile = Exporter.exportSingleSiteCsv(tempDir, site, specs, listOf(incident))
        assertTrue(csvFile.exists())

        val content = csvFile.readText()
        assertTrue(content.contains("SITE OVERVIEW: WB-NDRT-9009"))
        assertTrue(content.contains("Nadia Central Hub"))
        assertTrue(content.contains("TECHNICAL SPECIFICATIONS"))
        assertTrue(content.contains("EB POWER STATUS"))
        assertTrue(content.contains("Commercial 3-Phase"))
        assertTrue(content.contains("INCIDENT & ACTIVITY HISTORY"))
        assertTrue(content.contains("Rectifier SMPS Fan Failure"))
        assertTrue(content.contains("A. Sen"))
        assertTrue(content.contains("Replaced 48V cooling fan and tested RM load"))
    }
}
