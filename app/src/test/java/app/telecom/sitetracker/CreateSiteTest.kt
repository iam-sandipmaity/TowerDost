package app.telecom.sitetracker

import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import app.telecom.sitetracker.ui.createsite.SpecEntryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateSiteTest {

    @Test
    fun testSapIdNormalizationOnCreate() {
        val rawSap = "dl-del-9021-x"
        val normalized = SiteEntity.normalize(rawSap)
        assertEquals("DLDEL9021X", normalized)

        val site = SiteEntity(
            sapId = " DL-DEL-9021-X ",
            sapNorm = SiteEntity.normalize(" DL-DEL-9021-X "),
            name = "Connaught Place Hub"
        )
        assertEquals("DLDEL9021X", site.sapNorm)
    }

    @Test
    fun testCoordinateValidationRules() {
        val validLat = 28.6139
        val validLng = 77.2090
        assertTrue(validLat in -90.0..90.0)
        assertTrue(validLng in -180.0..180.0)

        val invalidLat = 95.123
        val invalidLng = -195.456
        assertFalse(invalidLat in -90.0..90.0)
        assertFalse(invalidLng in -180.0..180.0)
    }

    @Test
    fun testUniversalSpecsPrepopulationForNewSite() {
        val universalFields = listOf(
            UniversalFieldEntity("SMPS MAKE & MODEL", "POWER", null, 1),
            UniversalFieldEntity("BATTERY BANK CAPACITY", "BATTERY", "300AH", 2),
            UniversalFieldEntity("DG RATING KVA", "DG", null, 3)
        )

        val specEntries = universalFields.map { uni ->
            SpecEntryState(
                fieldKey = uni.fieldKey,
                fieldValue = uni.defaultValue ?: "",
                groupName = uni.groupName,
                isUniversal = true
            )
        }

        assertEquals(3, specEntries.size)
        assertEquals("SMPS MAKE & MODEL", specEntries[0].fieldKey)
        assertEquals("", specEntries[0].fieldValue)
        assertEquals("BATTERY BANK CAPACITY", specEntries[1].fieldKey)
        assertEquals("300AH", specEntries[1].fieldValue)
        assertTrue(specEntries.all { it.isUniversal })
    }

    @Test
    fun testFilteringEmptySpecsOnCreation() {
        val enteredSpecs = listOf(
            SiteFieldEntity(id = 0, sapId = "DL-101", fieldKey = "SMPS MAKE", fieldValue = "Delta 48V", groupName = "POWER"),
            SiteFieldEntity(id = 0, sapId = "DL-101", fieldKey = "DG CAPACITY", fieldValue = "", groupName = "DG"),
            SiteFieldEntity(id = 0, sapId = "DL-101", fieldKey = "BATTERY BANK", fieldValue = "   ", groupName = "BATTERY"),
            SiteFieldEntity(id = 0, sapId = "DL-101", fieldKey = "   ", fieldValue = "Valid Value", groupName = "OTHER"),
            SiteFieldEntity(id = 0, sapId = "DL-101", fieldKey = "ANTENNA TILT", fieldValue = "4 deg", groupName = "RAN")
        )

        val validSpecsToPersist = enteredSpecs.mapNotNull { spec ->
            val key = spec.fieldKey.trim()
            val value = spec.fieldValue?.trim()
            if (key.isNotEmpty() && !value.isNullOrEmpty()) {
                spec.copy(fieldKey = key, fieldValue = value)
            } else null
        }

        assertEquals(2, validSpecsToPersist.size)
        assertEquals("SMPS MAKE", validSpecsToPersist[0].fieldKey)
        assertEquals("Delta 48V", validSpecsToPersist[0].fieldValue)
        assertEquals("ANTENNA TILT", validSpecsToPersist[1].fieldKey)
        assertEquals("4 deg", validSpecsToPersist[1].fieldValue)
    }

    @Test
    fun testDuplicateSapIdCheck() {
        val existingSaps = setOf("DEL1001", "MUM2002", "BLR3003")
        val newSap = "del 1001"
        val normalizedNew = SiteEntity.normalize(newSap)
        assertTrue(existingSaps.contains(normalizedNew))

        val uniqueSap = "KOL 4004"
        val normalizedUnique = SiteEntity.normalize(uniqueSap)
        assertFalse(existingSaps.contains(normalizedUnique))
    }
}
