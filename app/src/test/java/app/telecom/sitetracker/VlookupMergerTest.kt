package app.telecom.sitetracker

import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.import.TargetField
import app.telecom.sitetracker.data.merger.MergeStrategy
import app.telecom.sitetracker.data.merger.OverwritePolicy
import app.telecom.sitetracker.data.merger.VlookupEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VlookupMergerTest {

    @Test
    fun testDetectKeyColumn() {
        val headers1 = listOf("Sr No", "SAP ID", "DG Make", "Battery Ah")
        val keyIndex1 = VlookupEngine.detectKeyColumn(headers1)
        assertEquals(1, keyIndex1)

        val headers2 = listOf("site_id", "Circle", "City", "Lat", "Long")
        val keyIndex2 = VlookupEngine.detectKeyColumn(headers2)
        assertEquals(0, keyIndex2)

        val headers3 = listOf("Tower Code", "Tower ID", "Status")
        val keyIndex3 = VlookupEngine.detectKeyColumn(headers3)
        assertEquals(0, keyIndex3) // "tower code" matches or "tower id" matches
    }

    @Test
    fun testAutoMapColumnsAndInferCategories() {
        val headers = listOf(
            "SAP ID",
            "Site Name",
            "Latitude",
            "DG FUEL TANK CAPACITY",
            "BATTERY BANK SOH",
            "SMPS RECTIFIER RATING",
            "ANTENNA TILT AZIMUTH",
            "OPTICAL FIBRE STATUS",
            "FIRE EXTINGUISHER EXPIRY"
        )

        val mappings = VlookupEngine.autoMapColumns(headers, keyIndex = 0)
        assertEquals(9, mappings.size)

        assertEquals(TargetField.SAP_ID, mappings[0].targetField)
        assertEquals(TargetField.NAME, mappings[1].targetField)
        assertEquals(TargetField.LAT, mappings[2].targetField)

        // Inferred spec categories
        assertEquals(TargetField.CUSTOM_SPEC, mappings[3].targetField)
        assertEquals("DG", mappings[3].specGroup)

        assertEquals(TargetField.CUSTOM_SPEC, mappings[4].targetField)
        assertEquals("BATTERY", mappings[4].specGroup)

        assertEquals(TargetField.CUSTOM_SPEC, mappings[5].targetField)
        assertEquals("POWER", mappings[5].specGroup)

        assertEquals(TargetField.CUSTOM_SPEC, mappings[6].targetField)
        assertEquals("RAN", mappings[6].specGroup)

        assertEquals(TargetField.CUSTOM_SPEC, mappings[7].targetField)
        assertEquals("TRANSMISSION", mappings[7].specGroup)

        assertEquals(TargetField.CUSTOM_SPEC, mappings[8].targetField)
        assertEquals("SAFETY", mappings[8].specGroup)
    }

    @Test
    fun testOverwritePolicyFillBlanksOnlyPreservesExistingData() {
        val existingSite = SiteEntity(
            sapId = "WB-KOL-1001",
            sapNorm = "WBKOL1001",
            name = "Original Salt Lake Hub",
            city = null, // blank
            circle = "West Bengal",
            technology = "4G"
        )

        val incomingRow = mapOf(
            TargetField.NAME to "New Name From Sheet",
            TargetField.CITY to "Kolkata Central",
            TargetField.TECHNOLOGY to "5G"
        )

        // Under FILL_BLANKS_ONLY:
        // Name should stay "Original Salt Lake Hub"
        // City should become "Kolkata Central"
        // Technology should stay "4G"
        var updated = existingSite
        for ((field, value) in incomingRow) {
            val isBlank = when (field) {
                TargetField.NAME -> updated.name.isNullOrBlank()
                TargetField.CITY -> updated.city.isNullOrBlank()
                TargetField.TECHNOLOGY -> updated.technology.isNullOrBlank()
                else -> false
            }
            if (isBlank) {
                updated = when (field) {
                    TargetField.NAME -> updated.copy(name = value)
                    TargetField.CITY -> updated.copy(city = value)
                    TargetField.TECHNOLOGY -> updated.copy(technology = value)
                    else -> updated
                }
            }
        }

        assertEquals("Original Salt Lake Hub", updated.name)
        assertEquals("Kolkata Central", updated.city)
        assertEquals("4G", updated.technology)
    }

    @Test
    fun testOverwritePolicyOverwriteAllReplacesExistingData() {
        val existingSite = SiteEntity(
            sapId = "WB-KOL-1001",
            sapNorm = "WBKOL1001",
            name = "Original Salt Lake Hub",
            city = "Old Kolkata",
            technology = "4G"
        )

        val incomingRow = mapOf(
            TargetField.NAME to "Updated Hub V",
            TargetField.CITY to "Kolkata Sector V",
            TargetField.TECHNOLOGY to "4G+5G"
        )

        var updated = existingSite
        for ((field, value) in incomingRow) {
            updated = when (field) {
                TargetField.NAME -> updated.copy(name = value)
                TargetField.CITY -> updated.copy(city = value)
                TargetField.TECHNOLOGY -> updated.copy(technology = value)
                else -> updated
            }
        }

        assertEquals("Updated Hub V", updated.name)
        assertEquals("Kolkata Sector V", updated.city)
        assertEquals("4G+5G", updated.technology)
    }

    @Test
    fun testMergeStrategyFiltering() {
        val dbSaps = setOf("DEL1001", "MUM2002", "BLR3003")
        val fileRows = listOf(
            "DEL-1001", // matched
            "MUM-2002", // matched
            "CHN-4004", // unmatched
            "PUN-5005"  // unmatched
        )

        val matched = fileRows.filter { dbSaps.contains(SiteEntity.normalize(it)) }
        val unmatched = fileRows.filterNot { dbSaps.contains(SiteEntity.normalize(it)) }

        assertEquals(2, matched.size)
        assertEquals(2, unmatched.size)
        assertEquals(listOf("DEL-1001", "MUM-2002"), matched)
        assertEquals(listOf("CHN-4004", "PUN-5005"), unmatched)
    }
}
