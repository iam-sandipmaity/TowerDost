package app.telecom.sitetracker

import app.telecom.sitetracker.data.db.DistinctFieldSpec
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import app.telecom.sitetracker.ui.template.UniversalTemplateUiState
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalTemplateTest {

    @Test
    fun testDefaultUiState() {
        val state = UniversalTemplateUiState()
        assertTrue(state.isLoading)
        assertTrue(state.groupedFields.isEmpty())
        assertEquals(0, state.totalFieldCount)
        assertEquals("", state.searchQuery)
        assertEquals(0, state.siteSpecsAvailableCount)
        assertFalse(state.isAddingField)
        assertFalse(state.isAddingSection)
        assertNull(state.targetSectionForAdd)
        assertNull(state.confirmDeleteField)
        assertNull(state.confirmDeleteSection)
        assertFalse(state.confirmClearAll)
        assertFalse(state.confirmSyncFromSites)
    }

    @Test
    fun testGroupingUniversalFieldsBySectionPreservingSort() {
        val fields = listOf(
            UniversalFieldEntity("SMPS MAKE & MODEL", "POWER", null, 1),
            UniversalFieldEntity("RECTIFIER MODULES", "POWER", "4 Modules", 2),
            UniversalFieldEntity("BATTERY CAPACITY", "BATTERY", "300AH", 3),
            UniversalFieldEntity("DG KVA", "DG", "15KVA", 4)
        )

        val grouped = fields.groupBy { it.groupName.uppercase(Locale.US) }
        assertEquals(3, grouped.size)
        assertTrue(grouped.containsKey("POWER"))
        assertTrue(grouped.containsKey("BATTERY"))
        assertTrue(grouped.containsKey("DG"))

        val powerFields = grouped["POWER"]!!
        assertEquals(2, powerFields.size)
        assertEquals("SMPS MAKE & MODEL", powerFields[0].fieldKey)
        assertEquals("RECTIFIER MODULES", powerFields[1].fieldKey)
        assertEquals("4 Modules", powerFields[1].defaultValue)
    }

    @Test
    fun testSearchFilterMatchesFieldKeyOrValue() {
        val fields = listOf(
            UniversalFieldEntity("SMPS MAKE & MODEL", "POWER", "Delta 48V", 1),
            UniversalFieldEntity("BATTERY BANK (AH)", "BATTERY", "600 AH", 2),
            UniversalFieldEntity("DG RATING KVA", "GENERATOR", "15 KVA", 3),
            UniversalFieldEntity("AIR CONDITIONER", "CLIMATE", "1.5 TON", 4)
        )
        val grouped = fields.groupBy { it.groupName.uppercase(Locale.US) }

        // Filter by key "battery"
        val query1 = "battery"
        val filtered1 = grouped.mapValues { (_, fList) ->
            fList.filter {
                it.fieldKey.lowercase(Locale.US).contains(query1) ||
                        (it.defaultValue?.lowercase(Locale.US)?.contains(query1) == true)
            }
        }.filterValues { it.isNotEmpty() }

        assertEquals(1, filtered1.size)
        assertTrue(filtered1.containsKey("BATTERY"))
        assertEquals("BATTERY BANK (AH)", filtered1["BATTERY"]!!.first().fieldKey)

        // Filter by value "48v"
        val query2 = "48v"
        val filtered2 = grouped.mapValues { (_, fList) ->
            fList.filter {
                it.fieldKey.lowercase(Locale.US).contains(query2) ||
                        (it.defaultValue?.lowercase(Locale.US)?.contains(query2) == true)
            }
        }.filterValues { it.isNotEmpty() }

        assertEquals(1, filtered2.size)
        assertTrue(filtered2.containsKey("POWER"))
        assertEquals("SMPS MAKE & MODEL", filtered2["POWER"]!!.first().fieldKey)
    }

    @Test
    fun testDerivingBlueprintFromDistinctSiteSpecs() {
        // Given distinct specs extracted from imported site_fields
        val distinctSpecs = listOf(
            DistinctFieldSpec(groupName = "Power", fieldKey = "Smps Capacity", minSort = 1),
            DistinctFieldSpec(groupName = "Battery", fieldKey = "Battery Bank (Ah)", minSort = 5),
            DistinctFieldSpec(groupName = "DG", fieldKey = "DG Rating KVA", minSort = 10)
        )

        val universalEntities = distinctSpecs.mapIndexed { idx, spec ->
            UniversalFieldEntity(
                fieldKey = spec.fieldKey.trim().uppercase(Locale.US),
                groupName = spec.groupName.trim().uppercase(Locale.US),
                defaultValue = null,
                sortOrder = if (spec.minSort > 0) spec.minSort else idx + 1
            )
        }

        assertEquals(3, universalEntities.size)
        assertEquals("SMPS CAPACITY", universalEntities[0].fieldKey)
        assertEquals("POWER", universalEntities[0].groupName)
        assertEquals(1, universalEntities[0].sortOrder)

        assertEquals("BATTERY BANK (AH)", universalEntities[1].fieldKey)
        assertEquals("BATTERY", universalEntities[1].groupName)
        assertEquals(5, universalEntities[1].sortOrder)

        assertEquals("DG RATING KVA", universalEntities[2].fieldKey)
        assertEquals("DG", universalEntities[2].groupName)
        assertEquals(10, universalEntities[2].sortOrder)
    }

    @Test
    fun testAddNewUniversalFieldComputesNextSortOrder() {
        val currentFields = listOf(
            UniversalFieldEntity("SMPS MAKE & MODEL", "POWER", null, 1),
            UniversalFieldEntity("SMPS CAPACITY", "POWER", null, 2)
        )
        val maxSort = currentFields.maxOfOrNull { it.sortOrder } ?: 0
        val nextSort = maxSort + 1

        val newField = UniversalFieldEntity(
            fieldKey = "EB SANCTIONED LOAD",
            groupName = "POWER",
            defaultValue = "15 KW",
            sortOrder = nextSort
        )

        val updatedList = currentFields + newField
        assertEquals(3, updatedList.size)
        assertEquals(3, nextSort)
        assertEquals("EB SANCTIONED LOAD", updatedList.last().fieldKey)
        assertEquals("15 KW", updatedList.last().defaultValue)
    }

    @Test
    fun testDeleteSectionRemovesAllSectionFields() {
        val fields = listOf(
            UniversalFieldEntity("SMPS MAKE & MODEL", "POWER", null, 1),
            UniversalFieldEntity("DG KVA", "DG", null, 2),
            UniversalFieldEntity("DG FUEL TANK", "DG", null, 3)
        )

        val sectionToDelete = "DG"
        val remaining = fields.filterNot { it.groupName.equals(sectionToDelete, ignoreCase = true) }

        assertEquals(1, remaining.size)
        assertEquals("POWER", remaining[0].groupName)
        assertEquals("SMPS MAKE & MODEL", remaining[0].fieldKey)
    }

    @Test
    fun testValidationRequiresNonBlankFieldKeyAndSection() {
        fun validateNewField(section: String?, key: String): Pair<Boolean, String?> {
            if (section.isNullOrBlank()) return Pair(false, "Section name is required.")
            if (key.trim().isBlank()) return Pair(false, "Specification field name is required.")
            return Pair(true, null)
        }

        val res1 = validateNewField(null, "TEST KEY")
        assertFalse(res1.first)
        assertNotNull(res1.second)

        val res2 = validateNewField("POWER", "   ")
        assertFalse(res2.first)
        assertNotNull(res2.second)

        val res3 = validateNewField("POWER", "VALID KEY")
        assertTrue(res3.first)
        assertNull(res3.second)
    }

    @Test
    fun testDefaultUiStateSupportsEditingAndClearAll() {
        val state = UniversalTemplateUiState()
        assertFalse(state.isEditingField)
        assertNull(state.fieldToEdit)
        assertFalse(state.confirmClearAll)
    }

    @Test
    fun testEditFieldUpdatesKeyGroupAndDefaultValue() {
        val original = UniversalFieldEntity(
            fieldKey = "DG RATING",
            groupName = "DG",
            defaultValue = "15 KVA",
            sortOrder = 2
        )

        // Simulate edit action
        val updated = original.copy(
            fieldKey = "DG CAPACITY (KVA)",
            groupName = "GENERATOR",
            defaultValue = "20 KVA"
        )

        assertEquals("DG CAPACITY (KVA)", updated.fieldKey)
        assertEquals("GENERATOR", updated.groupName)
        assertEquals("20 KVA", updated.defaultValue)
        assertEquals(2, updated.sortOrder)
    }

    @Test
    fun testClearAllEmptiesTemplateList() {
        val fields = mutableListOf(
            UniversalFieldEntity("DG RATING", "DG", "15KVA", 1),
            UniversalFieldEntity("BATTERY BANK", "BATTERY", "300AH", 2)
        )
        assertEquals(2, fields.size)

        fields.clear()
        assertTrue(fields.isEmpty())
    }
}
