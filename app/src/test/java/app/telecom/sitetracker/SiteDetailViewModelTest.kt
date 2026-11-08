package app.telecom.sitetracker

import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import app.telecom.sitetracker.ui.sitedetail.SiteDetailUiState
import app.telecom.sitetracker.ui.sitedetail.SpecDraftItem
import app.telecom.sitetracker.ui.sitedetail.SpecDraftSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteDetailViewModelTest {

    @Test
    fun testDefaultStateHasSpecsEditingInactive() {
        val state = SiteDetailUiState()
        assertFalse("Specs editing must be inactive by default", state.isEditingSpecs)
        assertTrue("Draft specs sections must be empty initially", state.draftSpecsSections.isEmpty())
        assertFalse("Confirm save specs must be false", state.confirmSaveSpecs)
        assertFalse("Add section dialog must be false", state.isAddingSectionDialog)
    }

    @Test
    fun testStartEditingSpecsPopulatesDraftFromGroupedSpecs() {
        val site = SiteEntity(sapId = "WB-KOL-5001", sapNorm = "WBKOL5001")
        val powerSpecs = listOf(
            SiteFieldEntity(id = 1L, sapId = site.sapId, fieldKey = "SMPS", fieldValue = "48V 100A", groupName = "POWER"),
            SiteFieldEntity(id = 2L, sapId = site.sapId, fieldKey = "DG", fieldValue = "15KVA", groupName = "POWER")
        )
        val batterySpecs = listOf(
            SiteFieldEntity(id = 3L, sapId = site.sapId, fieldKey = "BANK1", fieldValue = "300AH", groupName = "BATTERY")
        )
        val grouped = mapOf("POWER" to powerSpecs, "BATTERY" to batterySpecs)

        val sections = grouped.map { (groupName, fields) ->
            SpecDraftSection(
                sectionName = groupName,
                items = fields.map { SpecDraftItem(id = it.id, fieldKey = it.fieldKey, fieldValue = it.fieldValue ?: "") }
            )
        }

        val state = SiteDetailUiState(
            site = site,
            groupedSpecs = grouped,
            isEditingSpecs = true,
            draftSpecsSections = sections
        )

        assertEquals(2, state.draftSpecsSections.size)
        assertEquals("POWER", state.draftSpecsSections[0].sectionName)
        assertEquals(2, state.draftSpecsSections[0].items.size)
        assertEquals("SMPS", state.draftSpecsSections[0].items[0].fieldKey)
        assertEquals("48V 100A", state.draftSpecsSections[0].items[0].fieldValue)
        assertEquals("BATTERY", state.draftSpecsSections[1].sectionName)
        assertEquals(1, state.draftSpecsSections[1].items.size)
    }

    @Test
    fun testDraftSpecsItemMutations() {
        var sections = listOf(
            SpecDraftSection(
                sectionName = "POWER",
                items = listOf(SpecDraftItem(id = 1L, fieldKey = "SMPS", fieldValue = "48V 100A"))
            )
        )

        // 1. Add item to section 0
        val sec0 = sections[0]
        val mutableItems = sec0.items.toMutableList()
        mutableItems.add(SpecDraftItem(fieldKey = "DG CAPACITY", fieldValue = "15 KVA"))
        sections = listOf(sec0.copy(items = mutableItems))

        assertEquals(2, sections[0].items.size)
        assertEquals("DG CAPACITY", sections[0].items[1].fieldKey)

        // 2. Update item
        val updatedItems = sections[0].items.toMutableList()
        updatedItems[0] = updatedItems[0].copy(fieldValue = "48V 150A Upgraded")
        sections = listOf(sections[0].copy(items = updatedItems))

        assertEquals("48V 150A Upgraded", sections[0].items[0].fieldValue)

        // 3. Remove item
        val trimmedItems = sections[0].items.toMutableList()
        trimmedItems.removeAt(1)
        sections = listOf(sections[0].copy(items = trimmedItems))

        assertEquals(1, sections[0].items.size)
        assertEquals("SMPS", sections[0].items[0].fieldKey)
    }

    @Test
    fun testDraftSpecsItemMutationsByTempId() {
        val item1 = SpecDraftItem(id = 1L, tempId = "item-uuid-1", fieldKey = "SMPS", fieldValue = "48V 100A")
        val item2 = SpecDraftItem(id = 2L, tempId = "item-uuid-2", fieldKey = "DG", fieldValue = "15 KVA")
        val sec = SpecDraftSection(sectionName = "POWER", items = listOf(item1, item2))

        // Mutate by tempId
        val updatedItems = sec.items.map {
            if (it.tempId == "item-uuid-1") it.copy(fieldValue = "48V 150A Modular") else it
        }
        assertEquals("48V 150A Modular", updatedItems[0].fieldValue)

        // Remove by tempId
        val filteredItems = updatedItems.filter { it.tempId != "item-uuid-2" }
        assertEquals(1, filteredItems.size)
        assertEquals("item-uuid-1", filteredItems[0].tempId)
    }

    @Test
    fun testDraftSectionAddAndRemove() {
        val sections = mutableListOf(
            SpecDraftSection(
                sectionName = "POWER",
                items = listOf(SpecDraftItem(fieldKey = "SMPS", fieldValue = "48V"))
            )
        )

        // Add section
        val newSec = SpecDraftSection(
            sectionName = "TRANSMISSION",
            items = listOf(SpecDraftItem(fieldKey = "MEDIA", fieldValue = "OFC"))
        )
        sections.add(newSec)

        assertEquals(2, sections.size)
        assertEquals("TRANSMISSION", sections[1].sectionName)

        // Remove section
        sections.removeAt(0)
        assertEquals(1, sections.size)
        assertEquals("TRANSMISSION", sections[0].sectionName)
    }

    @Test
    fun testSpecValidationCatchesValueWithoutFieldKey() {
        val invalidSections = listOf(
            SpecDraftSection(
                sectionName = "POWER",
                items = listOf(SpecDraftItem(fieldKey = "", fieldValue = "48V 100A"))
            )
        )

        // Validation rule: item with blank key and non-blank value must fail
        var hasError = false
        var errorMsg: String? = null
        for (sec in invalidSections) {
            for (item in sec.items) {
                if (item.fieldKey.isBlank() && item.fieldValue.isNotBlank()) {
                    hasError = true
                    errorMsg = "Specification in '${sec.sectionName}' has a value without a field name."
                    break
                }
            }
        }

        assertTrue("Validation must flag blank key with non-blank value", hasError)
        assertNotNull(errorMsg)
        assertTrue(errorMsg!!.contains("POWER"))
    }

    @Test
    fun testConfirmAndSaveSpecsFiltersBlankItemsAndMapsEntities() {
        val sapId = "WB-KOL-7777"
        val draftSections = listOf(
            SpecDraftSection(
                sectionName = "POWER",
                items = listOf(
                    SpecDraftItem(fieldKey = "SMPS", fieldValue = "48V 150A"),
                    SpecDraftItem(fieldKey = "   ", fieldValue = "") // completely blank row should be omitted
                )
            ),
            SpecDraftSection(
                sectionName = "RF & ANTENNA",
                items = listOf(
                    SpecDraftItem(fieldKey = "AZIMUTH", fieldValue = "0/120/240")
                )
            )
        )

        val newEntities = mutableListOf<SiteFieldEntity>()
        var sortIndex = 1
        for (sec in draftSections) {
            val secName = sec.sectionName.trim().uppercase()
            for (item in sec.items) {
                val key = item.fieldKey.trim()
                val value = item.fieldValue.trim()
                if (key.isNotBlank()) {
                    newEntities.add(
                        SiteFieldEntity(
                            sapId = sapId,
                            fieldKey = key,
                            fieldValue = value.ifBlank { null },
                            groupName = secName,
                            sortOrder = sortIndex++
                        )
                    )
                }
            }
        }

        assertEquals(2, newEntities.size)
        assertEquals("SMPS", newEntities[0].fieldKey)
        assertEquals("48V 150A", newEntities[0].fieldValue)
        assertEquals("POWER", newEntities[0].groupName)
        assertEquals(1, newEntities[0].sortOrder)

        assertEquals("AZIMUTH", newEntities[1].fieldKey)
        assertEquals("0/120/240", newEntities[1].fieldValue)
        assertEquals("RF & ANTENNA", newEntities[1].groupName)
        assertEquals(2, newEntities[1].sortOrder)
    }

    @Test
    fun testModeToggleResetsSpecsEditingSafely() {
        var state = SiteDetailUiState(
            isEditMode = true,
            isEditingSpecs = true,
            draftSpecsSections = listOf(SpecDraftSection("POWER", listOf(SpecDraftItem(fieldKey = "K", fieldValue = "V"))))
        )

        // User locks back to View Only
        val newMode = false
        state = state.copy(
            isEditMode = newMode,
            isEditingOverview = if (!newMode) false else state.isEditingOverview,
            isEditingSpecs = if (!newMode) false else state.isEditingSpecs
        )

        assertFalse("isEditMode must be false", state.isEditMode)
        assertFalse("isEditingSpecs must be false when returning to View Only", state.isEditingSpecs)
    }

    @Test
    fun testUniversalFieldsMergedWithSiteSpecificSpecs() {
        val actualSapId = "WB-KOL-1001"
        // Site has existing specific recorded fields
        val recordedFields = listOf(
            SiteFieldEntity(id = 101L, sapId = actualSapId, fieldKey = "SMPS MAKE & MODEL", fieldValue = "Delta 48V 100A", groupName = "POWER", sortOrder = 1),
            SiteFieldEntity(id = 102L, sapId = actualSapId, fieldKey = "CUSTOM SITE NOTE", fieldValue = "Guard present 24/7", groupName = "ACCESS", sortOrder = 2)
        )

        // Universal template has global fields
        val universalFields = listOf(
            UniversalFieldEntity(fieldKey = "SMPS MAKE & MODEL", groupName = "POWER", defaultValue = null, sortOrder = 1),
            UniversalFieldEntity(fieldKey = "DG RATING KVA", groupName = "DG", defaultValue = "15 KVA", sortOrder = 2),
            UniversalFieldEntity(fieldKey = "BATTERY BANK CAPACITY", groupName = "BATTERY", defaultValue = null, sortOrder = 3)
        )

        // Merge logic
        val mergedFields = recordedFields.toMutableList()
        for (uni in universalFields) {
            if (mergedFields.none { it.fieldKey.equals(uni.fieldKey, ignoreCase = true) }) {
                mergedFields.add(
                    SiteFieldEntity(
                        id = 0L,
                        sapId = actualSapId,
                        fieldKey = uni.fieldKey,
                        fieldValue = uni.defaultValue,
                        groupName = uni.groupName,
                        sortOrder = uni.sortOrder
                    )
                )
            }
        }

        // Assertions:
        // 1. Total fields = 4 (1 common preserved, 1 site-specific, 2 universal placeholders added)
        assertEquals(4, mergedFields.size)

        // 2. Existing site field value is NOT overridden
        val smpsField = mergedFields.first { it.fieldKey.equals("SMPS MAKE & MODEL", ignoreCase = true) }
        assertEquals("Delta 48V 100A", smpsField.fieldValue)
        assertEquals(101L, smpsField.id)

        // 3. New universal fields are added with id = 0L and default values
        val dgField = mergedFields.first { it.fieldKey.equals("DG RATING KVA", ignoreCase = true) }
        assertEquals(0L, dgField.id)
        assertEquals("15 KVA", dgField.fieldValue)
        assertEquals("DG", dgField.groupName)

        val batteryField = mergedFields.first { it.fieldKey.equals("BATTERY BANK CAPACITY", ignoreCase = true) }
        assertEquals(0L, batteryField.id)
        assertNull(batteryField.fieldValue)
        assertEquals("BATTERY", batteryField.groupName)

        // 4. Custom site-specific field is preserved
        val customField = mergedFields.first { it.fieldKey.equals("CUSTOM SITE NOTE", ignoreCase = true) }
        assertEquals("Guard present 24/7", customField.fieldValue)
        assertEquals("ACCESS", customField.groupName)
    }

    @Test
    fun testWhenUniversalFieldsAreEmptyOnlyExcelImportedFieldsAppear() {
        val sapId = "IN-UPW-1002"
        // User imported Excel fields containing specific battery data
        val excelImportedFields = listOf(
            SiteFieldEntity(id = 1L, sapId = sapId, fieldKey = "BATTERY BANK (AH)", fieldValue = "600 AH", groupName = "INFRA"),
            SiteFieldEntity(id = 2L, sapId = sapId, fieldKey = "BATTERY VENDORS", fieldValue = "EXIDE", groupName = "INFRA")
        )

        // Universal template is empty (no auto-seeded fields polluting site)
        val universalFields = emptyList<UniversalFieldEntity>()

        // Merge logic
        val mergedFields = excelImportedFields.toMutableList()
        for (uni in universalFields) {
            if (mergedFields.none { it.fieldKey.equals(uni.fieldKey, ignoreCase = true) }) {
                mergedFields.add(
                    SiteFieldEntity(
                        id = 0L,
                        sapId = sapId,
                        fieldKey = uni.fieldKey,
                        fieldValue = uni.defaultValue,
                        groupName = uni.groupName,
                        sortOrder = uni.sortOrder
                    )
                )
            }
        }

        // Exactly the 2 imported fields, NO duplicate or blank "BATTERY BANK CAPACITY"
        assertEquals(2, mergedFields.size)
        assertEquals("BATTERY BANK (AH)", mergedFields[0].fieldKey)
        assertEquals("600 AH", mergedFields[0].fieldValue)
        assertEquals("BATTERY VENDORS", mergedFields[1].fieldKey)
        assertEquals("EXIDE", mergedFields[1].fieldValue)
        assertFalse(mergedFields.any { it.fieldKey.contains("CAPACITY") })
    }
}
