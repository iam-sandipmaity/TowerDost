package app.telecom.sitetracker

import app.telecom.sitetracker.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenNavigationTest {

    @Test
    fun testBottomNavItemsAreNotNull() {
        val items = Screen.bottomNavItems
        assertEquals(3, items.size)
        for (item in items) {
            assertNotNull("Nav item must not be null", item)
            assertNotNull("Route must not be null", item.route)
            assertTrue("Route must not be empty", item.route.isNotEmpty())
        }
    }

    @Test
    fun testRoutesMatch() {
        assertEquals("sites", Screen.Sites.route)
        assertEquals("activity", Screen.Activity.route)
        assertEquals("tools", Screen.Tools.route)
        assertEquals("home", Screen.Home.route)
        assertEquals("map", Screen.Map.route)
        assertEquals("register", Screen.Register.route)
        assertEquals("insights", Screen.Insights.route)
        assertEquals("import_wizard", Screen.ImportWizard.route)
        assertEquals("search", Screen.Search.route)
        assertEquals("sitedetail/DEL123", Screen.SiteDetail.createRoute("DEL123"))
        assertEquals("log_incident", Screen.LogIncident.createRoute())
        assertEquals("log_incident?sapId=DEL123", Screen.LogIncident.createRoute(sapId = "DEL123"))
        assertEquals("log_incident?sapId=DEL123&incidentId=INC456", Screen.LogIncident.createRoute(sapId = "DEL123", incidentId = "INC456"))
    }
}
