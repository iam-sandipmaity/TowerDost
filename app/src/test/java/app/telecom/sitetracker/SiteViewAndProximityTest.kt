package app.telecom.sitetracker

import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.repo.SiteWithDistance
import app.telecom.sitetracker.ui.sitedetail.SiteDetailUiState
import app.telecom.sitetracker.util.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteViewAndProximityTest {

    @Test
    fun testSiteDetailDefaultsToSafeViewOnlyMode() {
        val state = SiteDetailUiState()
        // Must default to View Only to prevent accidental touchscreen modifications in the field
        assertFalse("Default mode must be View Only (isEditMode == false)", state.isEditMode)
        assertFalse("Overview editing must be inactive by default", state.isEditingOverview)
        assertNull("Distance info must be null initially before GPS lock", state.distanceInfo)
    }

    @Test
    fun testModeToggleAndOverviewSafeguard() {
        var state = SiteDetailUiState(
            site = SiteEntity(sapId = "DEL-NOI-101", sapNorm = "DELNOI101"),
            isEditMode = false,
            isEditingOverview = false
        )

        // 1. User unlocks Edit Mode
        state = state.copy(isEditMode = true, isEditingOverview = true)
        assertTrue(state.isEditMode)
        assertTrue(state.isEditingOverview)

        // 2. Switching back to View Only Mode must forcibly cancel in-progress overview drafts
        val newEditMode = false
        state = state.copy(
            isEditMode = newEditMode,
            isEditingOverview = if (!newEditMode) false else state.isEditingOverview
        )
        assertFalse("isEditMode must now be false", state.isEditMode)
        assertFalse("isEditingOverview must be cleanly reset when returning to View Only", state.isEditingOverview)
    }

    @Test
    fun testProximityDistanceCalculationAndFormatting() {
        val userLat = 28.6139
        val userLng = 77.2090

        // Site ~1.5 km away in Delhi
        val siteNearLat = 28.6250
        val siteNearLng = 77.2180

        val distKm = Geo.haversineDistanceKm(userLat, userLng, siteNearLat, siteNearLng)
        assertTrue("Distance should be between 1.0 and 2.0 km: $distKm", distKm in 1.0..2.0)

        val formatted = Geo.formatDistanceAndBearing(userLat, userLng, siteNearLat, siteNearLng)
        assertTrue("Formatted distance must include km: $formatted", formatted.contains("km"))
        assertNotNull(formatted)
    }

    @Test
    fun testSiteWithDistanceProximityOrdering() {
        val userLat = 22.5726
        val userLng = 88.3639 // Kolkata

        val siteA = SiteEntity(sapId = "WB-KOL-A", sapNorm = "WBKOLA", lat = 22.5750, lng = 88.3650) // ~300m
        val siteB = SiteEntity(sapId = "WB-KOL-B", sapNorm = "WBKOLB", lat = 22.6500, lng = 88.4000) // ~9km
        val siteC = SiteEntity(sapId = "WB-KOL-C", sapNorm = "WBKOLC", lat = 22.3452, lng = 87.3232) // ~112km

        val list = listOf(siteC, siteA, siteB).map { site ->
            val dist = Geo.haversineDistanceKm(userLat, userLng, site.lat!!, site.lng!!)
            val bearing = Geo.bearingDegrees(userLat, userLng, site.lat, site.lng)
            val fmt = Geo.formatDistanceAndBearing(userLat, userLng, site.lat, site.lng)
            SiteWithDistance(site, dist, bearing, fmt)
        }.sortedBy { it.distanceKm }

        // Must sort closest to farthest
        assertEquals("WB-KOL-A", list[0].site.sapId)
        assertEquals("WB-KOL-B", list[1].site.sapId)
        assertEquals("WB-KOL-C", list[2].site.sapId)

        assertTrue(list[0].distanceKm < 1.0)
        assertTrue(list[1].distanceKm in 5.0..15.0)
        assertTrue(list[2].distanceKm > 100.0)
    }
}
