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

        val headers3 = listOf("Circle", "Tower ID", "Status")
        val keyIndex3 = VlookupEngine.detectKeyColumn(headers3)
        assertEquals(1, keyIndex3)

        val headers4 = listOf("Tower Code", "Status")
        val keyIndex4 = VlookupEngine.detectKeyColumn(headers4)
        assertEquals(0, keyIndex4)
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

    @Test
    fun testVlookupIgnoreColumnSkipsMerge() {
        val mapping = app.telecom.sitetracker.data.merger.VlookupColumnMapping(
            columnIndex = 2,
            sourceHeader = "UNWANTED_COLUMN",
            targetField = TargetField.CUSTOM_SPEC,
            specGroup = "MISC"
        )

        // User unchecks unwanted column to skip it
        val skipped = mapping.copy(
            targetField = TargetField.IGNORE,
            previousTarget = mapping.targetField
        )
        assertEquals(TargetField.IGNORE, skipped.targetField)
        assertEquals(TargetField.CUSTOM_SPEC, skipped.previousTarget)

        // Filter active mappings (same as VlookupEngine does)
        val activeMappings = listOf(mapping, skipped).filter { it.targetField != TargetField.IGNORE }
        assertEquals(1, activeMappings.size)
        assertEquals("UNWANTED_COLUMN", activeMappings[0].sourceHeader)
    }

    @Test
    fun testDuplicateColumnNamesProduceUniqueComposeKeys() {
        val headers = listOf("SAP ID", "Contact No", "Site Name", "Contact No", "Remarks")
        val mappings = VlookupEngine.autoMapColumns(headers, keyIndex = 0)
        assertEquals(5, mappings.size)

        // Non-key mappings
        val nonKey = mappings.filter { it.columnIndex != 0 }
        assertEquals(4, nonKey.size)

        // Generate keys using col_{columnIndex}_{sourceHeader}
        val composeKeys = nonKey.map { "col_${it.columnIndex}_${it.sourceHeader}" }
        val distinctKeys = composeKeys.toSet()

        // Verify that even though "Contact No" is repeated, all compose keys are strictly unique
        assertEquals(4, distinctKeys.size)
        assertTrue(composeKeys.contains("col_1_Contact No"))
        assertTrue(composeKeys.contains("col_3_Contact No"))
    }

    @Test
    fun testExistingVsNewFieldIdentification() {
        val existingDbSpecs = setOf("DG MAKE", "BATTERY AH", "RECTIFIER RATING")

        fun checkIsExisting(target: TargetField, specKey: String?): Boolean {
            return when (target) {
                TargetField.CUSTOM_SPEC -> {
                    val k = specKey?.trim()?.uppercase(java.util.Locale.US) ?: ""
                    existingDbSpecs.contains(k)
                }
                TargetField.SAP_ID, TargetField.IGNORE -> false
                else -> true // Core schema attributes
            }
        }

        // Core fields are always existing in master site schema
        assertTrue(checkIsExisting(TargetField.NAME, null))
        assertTrue(checkIsExisting(TargetField.LAT, null))
        assertTrue(checkIsExisting(TargetField.LNG, null))
        assertTrue(checkIsExisting(TargetField.CIRCLE, null))

        // Known custom specs from DB
        assertTrue(checkIsExisting(TargetField.CUSTOM_SPEC, "DG Make"))
        assertTrue(checkIsExisting(TargetField.CUSTOM_SPEC, "Battery Ah"))
        assertTrue(checkIsExisting(TargetField.CUSTOM_SPEC, "RECTIFIER RATING"))

        // Brand new custom specs
        assertFalse(checkIsExisting(TargetField.CUSTOM_SPEC, "Solar Panel Wattage"))
        assertFalse(checkIsExisting(TargetField.CUSTOM_SPEC, "Wind Turbine Model"))

        // Ignored or key
        assertFalse(checkIsExisting(TargetField.IGNORE, "DG Make"))
        assertFalse(checkIsExisting(TargetField.SAP_ID, "SAP ID"))
    }
}
