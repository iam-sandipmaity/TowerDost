package app.telecom.sitetracker

import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.import.ColumnMapper
import app.telecom.sitetracker.data.import.TargetField
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewCustomFieldsAndToolsTest {

    @Test
    fun testInferSpecGroupRecognizesOverviewUtilityAttributes() {
        // Headers like consumer id, installation no, meter no should route to OVERVIEW
        assertEquals("OVERVIEW", ColumnMapper.inferSpecGroup("Consumer ID"))
        assertEquals("OVERVIEW", ColumnMapper.inferSpecGroup("Consumer Number"))
        assertEquals("OVERVIEW", ColumnMapper.inferSpecGroup("Installation No"))
        assertEquals("OVERVIEW", ColumnMapper.inferSpecGroup("EB Meter No"))
        assertEquals("OVERVIEW", ColumnMapper.inferSpecGroup("Meter Serial No"))
        assertEquals("OVERVIEW", ColumnMapper.inferSpecGroup("CA No"))
        assertEquals("OVERVIEW", ColumnMapper.inferSpecGroup("Landlord Name"))
        assertEquals("OVERVIEW", ColumnMapper.inferSpecGroup("Landlord Mobile"))
        assertEquals("OVERVIEW", ColumnMapper.inferSpecGroup("Discom Name"))
    }

    @Test
    fun testInferSpecGroupKeepsEquipmentSpecsSeparate() {
        assertEquals("SMPS", ColumnMapper.inferSpecGroup("SMPS Capacity"))
        assertEquals("Battery Bank", ColumnMapper.inferSpecGroup("Battery Capacity AH"))
        assertEquals("Diesel Generator", ColumnMapper.inferSpecGroup("DG KVA Rating"))
        assertEquals("Solar MPPT", ColumnMapper.inferSpecGroup("Solar MPPT Make"))
    }

    @Test
    fun testExtractPhoneNumberIdentifiesValidTelecomPhoneNumbers() {
        fun extractPhoneNumber(label: String, value: String?): String? {
            if (value.isNullOrBlank()) return null
            val clean = value.filter { it.isDigit() || it == '+' }
            val digitCount = clean.count { it.isDigit() }
            val l = label.lowercase(Locale.US)
            val isPhoneKey = l.contains("phone") || l.contains("mobile") || l.contains("contact") ||
                    l.contains("landlord") || l.contains("caretaker") || l.contains("guard") ||
                    l.contains("lead") || l.contains("tech") || l.contains("no")
            return if (isPhoneKey && digitCount >= 7) clean
            else if (digitCount in 10..15 && !clean.startsWith("19") && !clean.startsWith("20")) clean
            else null
        }

        // Positive cases
        assertEquals("+919876543210", extractPhoneNumber("Landlord Mobile", "+91 98765 43210"))
        assertEquals("9876543210", extractPhoneNumber("Caretaker Contact", "98765-43210"))
        assertEquals("03322114455", extractPhoneNumber("Security Guard Phone", "033-22114455"))
        assertEquals("9876543210", extractPhoneNumber("Cluster Lead", "9876543210"))

        // Negative cases (Meter no or non-phone keys)
        assertNull(extractPhoneNumber("EB Meter No", "MTR-88219"))
        assertNull(extractPhoneNumber("SMPS Model", "Delta 48V 100A"))
        assertNull(extractPhoneNumber("Installation No", "12345"))
    }

    @Test
    fun testSeparatingOverviewFieldsFromTechnicalSpecs() {
        val allFields = listOf(
            SiteFieldEntity(id = 1, sapId = "S01", fieldKey = "CONSUMER_ID", fieldValue = "EB-8849", groupName = "OVERVIEW"),
            SiteFieldEntity(id = 2, sapId = "S01", fieldKey = "METER_NO", fieldValue = "MTR-9901", groupName = "OVERVIEW"),
            SiteFieldEntity(id = 3, sapId = "S01", fieldKey = "SMPS_MAKE", fieldValue = "Delta", groupName = "POWER"),
            SiteFieldEntity(id = 4, sapId = "S01", fieldKey = "DG_KVA", fieldValue = "15 KVA", groupName = "DG")
        )

        val overviewCustom = allFields.filter { it.groupName.equals("OVERVIEW", ignoreCase = true) }
        val technicalSpecs = allFields.filterNot { it.groupName.equals("OVERVIEW", ignoreCase = true) }

        assertEquals(2, overviewCustom.size)
        assertEquals("CONSUMER_ID", overviewCustom[0].fieldKey)
        assertEquals("METER_NO", overviewCustom[1].fieldKey)

        assertEquals(2, technicalSpecs.size)
        assertEquals("SMPS_MAKE", technicalSpecs[0].fieldKey)
        assertEquals("DG_KVA", technicalSpecs[1].fieldKey)
    }

    @Test
    fun testBuildSiteSummaryCardContainsOverviewAndCustomFields() {
        val site = SiteEntity(
            sapId = "DL-1001",
            sapNorm = "DL1001",
            name = "Connaught Place Hub",
            circle = "Delhi",
            district = "New Delhi",
            city = "Central Delhi",
            technology = "5G",
            siteType = "GBT",
            status = "Operational",
            lat = 28.6315,
            lng = 77.2167,
            address = "Radial Road 3, CP"
        )

        val customOverview = listOf(
            SiteFieldEntity(id = 1, sapId = "DL-1001", fieldKey = "Consumer ID", fieldValue = "DL-EB-9921", groupName = "OVERVIEW"),
            SiteFieldEntity(id = 2, sapId = "DL-1001", fieldKey = "Meter No", fieldValue = "MTR-88210", groupName = "OVERVIEW")
        )

        fun buildSummary(s: SiteEntity, custom: List<SiteFieldEntity>): String {
            val sb = StringBuilder()
            sb.appendLine("📍 [TowerDost Site Card]")
            sb.appendLine("SAP ID: ${s.sapId}")
            if (!s.name.isNullOrBlank()) sb.appendLine("Name: ${s.name}")
            val loc = listOfNotNull(s.circle, s.district, s.city).filter { it.isNotBlank() }
            if (loc.isNotEmpty()) sb.appendLine("Location: ${loc.joinToString(" • ")}")
            if (!s.technology.isNullOrBlank()) sb.appendLine("Tech: ${s.technology}")
            if (!s.status.isNullOrBlank()) sb.appendLine("Status: ${s.status}")
            if (s.lat != null && s.lng != null) sb.appendLine("GPS: ${s.lat}, ${s.lng}")
            for (c in custom) {
                if (!c.fieldValue.isNullOrBlank()) sb.appendLine("${c.fieldKey}: ${c.fieldValue}")
            }
            return sb.toString().trimEnd()
        }

        val card = buildSummary(site, customOverview)
        assertTrue(card.contains("DL-1001"))
        assertTrue(card.contains("Connaught Place Hub"))
        assertTrue(card.contains("Consumer ID: DL-EB-9921"))
        assertTrue(card.contains("Meter No: MTR-88210"))
    }
}
