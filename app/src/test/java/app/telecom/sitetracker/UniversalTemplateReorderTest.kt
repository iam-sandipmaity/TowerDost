package app.telecom.sitetracker

import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalTemplateReorderTest {

    @Test
    fun testMoveFieldUpWithinSectionSwapsOrder() {
        val fields = mutableListOf(
            UniversalFieldEntity("POWER_01", "POWER", null, 1),
            UniversalFieldEntity("POWER_02", "POWER", null, 2),
            UniversalFieldEntity("POWER_03", "POWER", null, 3)
        )

        // Move POWER_02 up
        val index = fields.indexOfFirst { it.fieldKey == "POWER_02" }
        assertTrue(index > 0)
        val prev = fields[index - 1]
        val curr = fields[index]
        fields[index - 1] = curr.copy(sortOrder = prev.sortOrder)
        fields[index] = prev.copy(sortOrder = curr.sortOrder)

        assertEquals("POWER_02", fields[0].fieldKey)
        assertEquals("POWER_01", fields[1].fieldKey)
        assertEquals("POWER_03", fields[2].fieldKey)
    }

    @Test
    fun testMoveFieldDownWithinSectionSwapsOrder() {
        val fields = mutableListOf(
            UniversalFieldEntity("POWER_01", "POWER", null, 1),
            UniversalFieldEntity("POWER_02", "POWER", null, 2),
            UniversalFieldEntity("POWER_03", "POWER", null, 3)
        )

        // Move POWER_01 down
        val index = fields.indexOfFirst { it.fieldKey == "POWER_01" }
        assertTrue(index < fields.size - 1)
        val curr = fields[index]
        val next = fields[index + 1]
        fields[index] = next.copy(sortOrder = curr.sortOrder)
        fields[index + 1] = curr.copy(sortOrder = next.sortOrder)

        assertEquals("POWER_02", fields[0].fieldKey)
        assertEquals("POWER_01", fields[1].fieldKey)
        assertEquals("POWER_03", fields[2].fieldKey)
    }

    @Test
    fun testMoveFieldToTopSetsAsFirstInGroup() {
        val fields = mutableListOf(
            UniversalFieldEntity("POWER_01", "POWER", null, 1),
            UniversalFieldEntity("POWER_02", "POWER", null, 2),
            UniversalFieldEntity("POWER_03", "POWER", null, 3)
        )

        val target = fields.first { it.fieldKey == "POWER_03" }
        fields.remove(target)
        fields.add(0, target)

        // Normalize sort order
        val normalized = fields.mapIndexed { idx, item -> item.copy(sortOrder = idx + 1) }
        assertEquals("POWER_03", normalized[0].fieldKey)
        assertEquals(1, normalized[0].sortOrder)
        assertEquals("POWER_01", normalized[1].fieldKey)
        assertEquals(2, normalized[1].sortOrder)
        assertEquals("POWER_02", normalized[2].fieldKey)
        assertEquals(3, normalized[2].sortOrder)
    }

    @Test
    fun testMoveFieldToSectionChangesGroupAndSortsToEnd() {
        val powerFields = mutableListOf(
            UniversalFieldEntity("POWER_01", "POWER", null, 1),
            UniversalFieldEntity("POWER_02", "POWER", null, 2)
        )
        val dgFields = mutableListOf(
            UniversalFieldEntity("DG_01", "DG", null, 3)
        )

        val moving = powerFields.removeAt(0)
        val updatedMoving = moving.copy(groupName = "DG", sortOrder = dgFields.size + 1)
        dgFields.add(updatedMoving)

        assertEquals(1, powerFields.size)
        assertEquals("POWER_02", powerFields[0].fieldKey)

        assertEquals(2, dgFields.size)
        assertEquals("DG_01", dgFields[0].fieldKey)
        assertEquals("POWER_01", dgFields[1].fieldKey)
        assertEquals("DG", dgFields[1].groupName)
    }

    @Test
    fun testMoveSectionUpSwapsCategoryPlacement() {
        // Sections: 1. BATTERY, 2. POWER
        val fields = mutableListOf(
            UniversalFieldEntity("BATTERY_01", "BATTERY", null, 1),
            UniversalFieldEntity("BATTERY_02", "BATTERY", null, 2),
            UniversalFieldEntity("POWER_01", "POWER", null, 3),
            UniversalFieldEntity("POWER_02", "POWER", null, 4)
        )

        val groups = fields.groupBy { it.groupName }.toMutableMap()
        val groupKeys = groups.keys.toList() // ["BATTERY", "POWER"]
        val powerIdx = groupKeys.indexOf("POWER")
        assertTrue(powerIdx > 0)

        // Swap sections: POWER moves up before BATTERY
        val newOrder = mutableListOf<UniversalFieldEntity>()
        newOrder.addAll(groups["POWER"]!!)
        newOrder.addAll(groups["BATTERY"]!!)

        val normalized = newOrder.mapIndexed { idx, item -> item.copy(sortOrder = idx + 1) }
        val reGrouped = normalized.groupBy { it.groupName }
        val finalGroupKeys = reGrouped.keys.toList()

        assertEquals("POWER", finalGroupKeys[0])
        assertEquals("BATTERY", finalGroupKeys[1])
        assertEquals("POWER_01", reGrouped["POWER"]!![0].fieldKey)
        assertEquals(1, reGrouped["POWER"]!![0].sortOrder)
    }

    @Test
    fun testSiteSpecsInheritGlobalUniversalTemplateOrdering() {
        // Universal template specifies:
        // 1. POWER: SMPS_MAKE, SMPS_CAPACITY
        // 2. DG: DG_KVA
        // 3. BATTERY: BATTERY_AH
        val universalTemplate = listOf(
            UniversalFieldEntity("SMPS_MAKE", "POWER", null, 1),
            UniversalFieldEntity("SMPS_CAPACITY", "POWER", null, 2),
            UniversalFieldEntity("DG_KVA", "DG", null, 3),
            UniversalFieldEntity("BATTERY_AH", "BATTERY", null, 4)
        )

        // A site has imported fields in arbitrary / reversed order:
        val siteFields = listOf(
            SiteFieldEntity(id = 1, sapId = "S01", fieldKey = "BATTERY_AH", fieldValue = "500AH", groupName = "BATTERY", sortOrder = 10),
            SiteFieldEntity(id = 2, sapId = "S01", fieldKey = "SMPS_CAPACITY", fieldValue = "100A", groupName = "POWER", sortOrder = 20),
            SiteFieldEntity(id = 3, sapId = "S01", fieldKey = "CUSTOM_TENANT", fieldValue = "Airtel", groupName = "TENANCY", sortOrder = 30),
            SiteFieldEntity(id = 4, sapId = "S01", fieldKey = "DG_KVA", fieldValue = "15KVA", groupName = "DG", sortOrder = 40),
            SiteFieldEntity(id = 5, sapId = "S01", fieldKey = "SMPS_MAKE", fieldValue = "Delta", groupName = "POWER", sortOrder = 50)
        )

        // Rank maps from universal template
        val groupRank = universalTemplate.map { it.groupName.uppercase(Locale.US) }
            .distinct()
            .mapIndexed { idx, grp -> grp to idx }
            .toMap()

        val fieldRank = universalTemplate.mapIndexed { idx, u ->
            (u.groupName.uppercase(Locale.US) to u.fieldKey.uppercase(Locale.US)) to idx
        }.toMap()

        val keyRank = universalTemplate.mapIndexed { idx, u ->
            u.fieldKey.uppercase(Locale.US) to idx
        }.toMap()

        // Group site fields
        val rawGrouped = siteFields.groupBy { it.groupName?.ifBlank { "General Specifications" } ?: "General Specifications" }

        // Sort groups by global template rank (unmapped groups placed at the end)
        val sortedGroups = rawGrouped.entries.sortedWith(
            compareBy { (grp, _) -> groupRank[grp.uppercase(Locale.US)] ?: Int.MAX_VALUE }
        )

        val orderedResult = sortedGroups.associate { (grp, list) ->
            val sortedList = list.sortedWith(
                compareBy(
                    { it -> fieldRank[grp.uppercase(Locale.US) to it.fieldKey.uppercase(Locale.US)] ?: keyRank[it.fieldKey.uppercase(Locale.US)] ?: (Int.MAX_VALUE - 1000 + (it.sortOrder ?: 0)) },
                    { it.sortOrder ?: 0 },
                    { it.fieldKey }
                )
            )
            grp to sortedList
        }

        val resultSectionKeys = orderedResult.keys.toList()
        // POWER, DG, BATTERY should come first according to template; TENANCY at end
        assertEquals("POWER", resultSectionKeys[0])
        assertEquals("DG", resultSectionKeys[1])
        assertEquals("BATTERY", resultSectionKeys[2])
        assertEquals("TENANCY", resultSectionKeys[3])

        // Within POWER: SMPS_MAKE should be before SMPS_CAPACITY according to template
        val powerSpecs = orderedResult["POWER"]!!
        assertEquals("SMPS_MAKE", powerSpecs[0].fieldKey)
        assertEquals("SMPS_CAPACITY", powerSpecs[1].fieldKey)
    }
}
