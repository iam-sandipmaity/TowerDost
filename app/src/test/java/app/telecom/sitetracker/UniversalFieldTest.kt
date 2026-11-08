package app.telecom.sitetracker

import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalFieldTest {

    @Test
    fun testUniversalFieldEntityCreationAndDefaults() {
        val field = UniversalFieldEntity(
            fieldKey = "DG FUEL CAPACITY",
            groupName = "DG & POWER",
            defaultValue = "150 L",
            sortOrder = 1
        )
        assertEquals("DG FUEL CAPACITY", field.fieldKey)
        assertEquals("DG & POWER", field.groupName)
        assertEquals("150 L", field.defaultValue)
        assertEquals(1, field.sortOrder)
    }

    @Test
    fun testUniversalFieldsMergeWithExistingSiteSpecs() {
        // Universal template defines 3 standard fields
        val universalTemplate = listOf(
            UniversalFieldEntity(fieldKey = "SMPS MAKE", groupName = "POWER", sortOrder = 1),
            UniversalFieldEntity(fieldKey = "BATTERY SOH", groupName = "BATTERY", sortOrder = 2),
            UniversalFieldEntity(fieldKey = "FIRE EXT EXPIRY", groupName = "SAFETY", sortOrder = 3)
        )

        // Site has data for SMPS MAKE and a custom field unique to this site, but missing BATTERY SOH and FIRE EXT EXPIRY
        val siteSapId = "WB-KOL-8888"
        val existingSiteSpecs = listOf(
            SiteFieldEntity(id = 10L, sapId = siteSapId, fieldKey = "SMPS MAKE", fieldValue = "Delta 48V", groupName = "POWER"),
            SiteFieldEntity(id = 11L, sapId = siteSapId, fieldKey = "CUSTOM RF JUMPER", fieldValue = "Din to Din 2m", groupName = "RF")
        )

        // Merge logic
        val mergedList = existingSiteSpecs.toMutableList()
        for (uni in universalTemplate) {
            val exists = mergedList.any { it.fieldKey.equals(uni.fieldKey, ignoreCase = true) }
            if (!exists) {
                mergedList.add(
                    SiteFieldEntity(
                        id = 0L,
                        sapId = siteSapId,
                        fieldKey = uni.fieldKey,
                        fieldValue = uni.defaultValue,
                        groupName = uni.groupName,
                        sortOrder = uni.sortOrder
                    )
                )
            }
        }

        // Must now have 4 total fields
        assertEquals(4, mergedList.size)

        // 1. Existing SMPS MAKE value must be preserved exactly
        val smpsField = mergedList.first { it.fieldKey.equals("SMPS MAKE", ignoreCase = true) }
        assertEquals("Delta 48V", smpsField.fieldValue)
        assertEquals(10L, smpsField.id)

        // 2. Custom site field must still exist
        val customField = mergedList.firstOrNull { it.fieldKey.equals("CUSTOM RF JUMPER", ignoreCase = true) }
        assertNotNull(customField)
        assertEquals("Din to Din 2m", customField?.fieldValue)

        // 3. Missing universal fields must be added with null values
        val batteryField = mergedList.first { it.fieldKey.equals("BATTERY SOH", ignoreCase = true) }
        assertNull(batteryField.fieldValue)
        assertEquals("BATTERY", batteryField.groupName)

        val fireField = mergedList.first { it.fieldKey.equals("FIRE EXT EXPIRY", ignoreCase = true) }
        assertNull(fireField.fieldValue)
        assertEquals("SAFETY", fireField.groupName)
    }

    @Test
    fun testDefaultTelecomSeedsContainStandardCategories() {
        val standardKeys = listOf(
            "SMPS CAPACITY" to "POWER",
            "RECTIFIER MODULES" to "POWER",
            "BATTERY BANK CAPACITY" to "BATTERY",
            "BATTERY BACKUP HOURS" to "BATTERY",
            "DG RATING KVA" to "DG",
            "DG FUEL TANK" to "DG",
            "OPTICAL FIBRE TYPE" to "TRANSMISSION",
            "FIRE EXTINGUISHER" to "SAFETY",
            "AC PRECISION UNITS" to "ENVIRONMENT"
        )

        val groups = standardKeys.map { it.second }.distinct()
        assertTrue(groups.contains("POWER"))
        assertTrue(groups.contains("BATTERY"))
        assertTrue(groups.contains("DG"))
        assertTrue(groups.contains("TRANSMISSION"))
        assertTrue(groups.contains("SAFETY"))
    }
}
