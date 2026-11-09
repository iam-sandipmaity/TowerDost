package app.telecom.sitetracker

import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.export.Exporter
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicTabExportTest {

    @Test
    fun testExportSitesCsvWithCustomTabColumns() {
        val tempDir = Files.createTempDirectory("td_export_custom_tabs_test").toFile()

        val site1 = SiteEntity(
            sapId = "DEL-101",
            sapNorm = "DEL101",
            name = "South Extension Hub",
            circle = "Delhi",
            city = "New Delhi"
        )
        val site2 = SiteEntity(
            sapId = "DEL-102",
            sapNorm = "DEL102",
            name = "Connaught Tower",
            circle = "Delhi",
            city = "New Delhi"
        )

        val specs = listOf(
            SiteFieldEntity(
                sapId = "DEL-101",
                fieldKey = "DG_CAPACITY",
                fieldValue = "15 KVA",
                tabId = "SPECS",
                groupName = "POWER"
            ),
            SiteFieldEntity(
                sapId = "DEL-101",
                fieldKey = "TENANT_NAME",
                fieldValue = "Airtel",
                tabId = "TENANT_INFO",
                groupName = "TENANTS"
            ),
            SiteFieldEntity(
                sapId = "DEL-102",
                fieldKey = "TENANT_NAME",
                fieldValue = "Jio",
                tabId = "TENANT_INFO",
                groupName = "TENANTS"
            ),
            SiteFieldEntity(
                sapId = "DEL-102",
                fieldKey = "SOLAR_KW",
                fieldValue = "5KW",
                tabId = "SOLAR",
                groupName = "GREEN_ENERGY"
            )
        )

        val csvFile = Exporter.exportSitesCsv(tempDir, listOf(site1, site2), specs)
        assertTrue(csvFile.exists())

        val lines = csvFile.readLines()
        assertTrue(lines.size >= 3)
        val header = lines[0]

        // Specs tab column has clean name without prefix
        assertTrue("Header should contain DG_CAPACITY", header.contains("DG_CAPACITY"))

        // Custom tabs have bracketed tab prefix
        assertTrue("Header should contain [TENANT INFO] TENANT_NAME", header.contains("[TENANT INFO] TENANT_NAME"))
        assertTrue("Header should contain [SOLAR] SOLAR_KW", header.contains("[SOLAR] SOLAR_KW"))

        val row1 = lines.first { it.startsWith("DEL-101") }
        assertTrue("DEL-101 should contain 15 KVA", row1.contains("15 KVA"))
        assertTrue("DEL-101 should contain Airtel", row1.contains("Airtel"))

        val row2 = lines.first { it.startsWith("DEL-102") }
        assertTrue("DEL-102 should contain Jio", row2.contains("Jio"))
        assertTrue("DEL-102 should contain 5KW", row2.contains("5KW"))

        tempDir.deleteRecursively()
    }

    @Test
    fun testExportSingleSiteCsvWithCustomTabSections() {
        val tempDir = Files.createTempDirectory("td_single_export_custom_tabs_test").toFile()

        val site = SiteEntity(
            sapId = "MUM-901",
            sapNorm = "MUM901",
            name = "BKC Datacenter",
            circle = "Mumbai",
            city = "Mumbai"
        )

        val specs = listOf(
            SiteFieldEntity(
                sapId = "MUM-901",
                fieldKey = "RECTIFIER",
                fieldValue = "Delta 48V 100A",
                tabId = "SPECS",
                groupName = "POWER"
            ),
            SiteFieldEntity(
                sapId = "MUM-901",
                fieldKey = "SHARING_OPERATOR",
                fieldValue = "Vodafone Idea",
                tabId = "TENANT_INFO",
                groupName = "OPERATORS"
            )
        )

        val incidents = listOf(
            IncidentEntity(
                id = "LOG-1001",
                sapId = "MUM-901",
                title = "Routine Audit",
                category = "Audit",
                severity = "Low",
                status = "Closed",
                isOutage = 0,
                occurredAt = 1762500000000L
            )
        )

        val csvFile = Exporter.exportSingleSiteCsv(tempDir, site, specs, incidents)
        assertTrue(csvFile.exists())

        val content = csvFile.readText()
        assertTrue(content.contains("SITE OVERVIEW: MUM-901"))
        assertTrue(content.contains("TECHNICAL SPECIFICATIONS"))
        assertTrue(content.contains("Tab,Group,Specification Name,Value") || content.contains("Group,Specification Name,Value"))
        assertTrue(content.contains("SPECS"))
        assertTrue(content.contains("TENANT INFO"))
        assertTrue(content.contains("POWER"))
        assertTrue(content.contains("RECTIFIER"))
        assertTrue(content.contains("Delta 48V 100A"))
        assertTrue(content.contains("OPERATORS"))
        assertTrue(content.contains("SHARING_OPERATOR"))
        assertTrue(content.contains("Vodafone Idea"))

        tempDir.deleteRecursively()
    }
}
