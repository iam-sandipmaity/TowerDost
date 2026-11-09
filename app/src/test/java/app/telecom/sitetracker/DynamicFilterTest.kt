package app.telecom.sitetracker

import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.ui.sitelist.ActiveFilter
import app.telecom.sitetracker.ui.sitelist.FilterDimension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicFilterTest {

    private fun matchesFilter(
        site: SiteEntity,
        fieldsForSite: List<SiteFieldEntity>,
        filter: ActiveFilter
    ): Boolean {
        if (filter.isCoreField) {
            val siteVal = when (filter.dimensionKey.lowercase()) {
                "circle" -> site.circle
                "city" -> site.city
                "district" -> site.district
                "technology" -> site.technology
                "site_type" -> site.siteType
                "status" -> site.status
                else -> null
            } ?: return false
            return siteVal.trim().equals(filter.selectedValue.trim(), ignoreCase = true)
        } else {
            return fieldsForSite.any { field ->
                field.fieldKey.trim().equals(filter.dimensionKey.trim(), ignoreCase = true) &&
                        field.fieldValue?.trim()?.equals(filter.selectedValue.trim(), ignoreCase = true) == true
            }
        }
    }

    private fun matchesAllFilters(
        site: SiteEntity,
        fieldsForSite: List<SiteFieldEntity>,
        filters: List<ActiveFilter>
    ): Boolean {
        return filters.all { filter -> matchesFilter(site, fieldsForSite, filter) }
    }

    @Test
    fun testMaxFourFiltersEnforced() {
        val activeFilters = mutableListOf<ActiveFilter>()
        val maxAllowed = 4

        fun addFilter(filter: ActiveFilter): Boolean {
            if (activeFilters.size >= maxAllowed) return false
            activeFilters.add(filter)
            return true
        }

        assertTrue(addFilter(ActiveFilter(dimensionKey = "circle", displayName = "Circle", selectedValue = "West Bengal", isCoreField = true)))
        assertTrue(addFilter(ActiveFilter(dimensionKey = "technology", displayName = "Technology", selectedValue = "5G", isCoreField = true)))
        assertTrue(addFilter(ActiveFilter(dimensionKey = "DG CAPACITY", displayName = "DG CAPACITY", selectedValue = "15KVA", isCoreField = false)))
        assertTrue(addFilter(ActiveFilter(dimensionKey = "status", displayName = "Status", selectedValue = "Active", isCoreField = true)))

        // 5th filter must be rejected
        assertFalse(addFilter(ActiveFilter(dimensionKey = "city", displayName = "City", selectedValue = "Kolkata", isCoreField = true)))
        assertEquals(4, activeFilters.size)
    }

    @Test
    fun testCoreFieldFiltering() {
        val site1 = SiteEntity(sapId = "WB-01", sapNorm = "WB01", circle = "West Bengal", technology = "5G")
        val site2 = SiteEntity(sapId = "DL-01", sapNorm = "DL01", circle = "Delhi", technology = "4G")

        val filterCircle = ActiveFilter(dimensionKey = "circle", displayName = "Circle", selectedValue = "West Bengal", isCoreField = true)
        val filterTech = ActiveFilter(dimensionKey = "technology", displayName = "Technology", selectedValue = "5G", isCoreField = true)

        assertTrue(matchesFilter(site1, emptyList(), filterCircle))
        assertFalse(matchesFilter(site2, emptyList(), filterCircle))

        assertTrue(matchesFilter(site1, emptyList(), filterTech))
        assertFalse(matchesFilter(site2, emptyList(), filterTech))
    }

    @Test
    fun testCustomSpecificationFiltering() {
        val site1 = SiteEntity(sapId = "WB-01", sapNorm = "WB01")
        val site2 = SiteEntity(sapId = "WB-02", sapNorm = "WB02")

        val specsSite1 = listOf(
            SiteFieldEntity(sapId = "WB-01", groupName = "DG", fieldKey = "DG CAPACITY", fieldValue = "15KVA", sortOrder = 1)
        )
        val specsSite2 = listOf(
            SiteFieldEntity(sapId = "WB-02", groupName = "DG", fieldKey = "DG CAPACITY", fieldValue = "25KVA", sortOrder = 1)
        )

        val filterDg15 = ActiveFilter(dimensionKey = "DG CAPACITY", displayName = "DG CAPACITY", selectedValue = "15KVA", isCoreField = false)

        assertTrue(matchesFilter(site1, specsSite1, filterDg15))
        assertFalse(matchesFilter(site2, specsSite2, filterDg15))
    }

    @Test
    fun testMultiFilterConjunction() {
        val site1 = SiteEntity(sapId = "WB-01", sapNorm = "WB01", circle = "West Bengal", technology = "5G")
        val specsSite1 = listOf(
            SiteFieldEntity(sapId = "WB-01", groupName = "DG", fieldKey = "DG CAPACITY", fieldValue = "15KVA", sortOrder = 1)
        )

        val site2 = SiteEntity(sapId = "WB-02", sapNorm = "WB02", circle = "West Bengal", technology = "4G")
        val specsSite2 = listOf(
            SiteFieldEntity(sapId = "WB-02", groupName = "DG", fieldKey = "DG CAPACITY", fieldValue = "15KVA", sortOrder = 1)
        )

        val filters = listOf(
            ActiveFilter(dimensionKey = "circle", displayName = "Circle", selectedValue = "West Bengal", isCoreField = true),
            ActiveFilter(dimensionKey = "technology", displayName = "Technology", selectedValue = "5G", isCoreField = true),
            ActiveFilter(dimensionKey = "DG CAPACITY", displayName = "DG CAPACITY", selectedValue = "15KVA", isCoreField = false)
        )

        // Site 1 matches all 3 filters
        assertTrue(matchesAllFilters(site1, specsSite1, filters))
        // Site 2 matches circle and DG, but fails technology (4G != 5G)
        assertFalse(matchesAllFilters(site2, specsSite2, filters))
    }
}
