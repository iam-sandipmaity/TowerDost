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
        assertEquals(5, items.size)
        for (item in items) {
            assertNotNull("Nav item must not be null", item)
            assertNotNull("Route must not be null", item.route)
            assertTrue("Route must not be empty", item.route.isNotEmpty())
        }
    }

    @Test
    fun testRoutesMatch() {
        assertEquals("home", Screen.Home.route)
        assertEquals("sites", Screen.Sites.route)
        assertEquals("map", Screen.Map.route)
        assertEquals("register", Screen.Register.route)
        assertEquals("insights", Screen.Insights.route)
        assertEquals("import_wizard", Screen.ImportWizard.route)
        assertEquals("search", Screen.Search.route)
        assertEquals("sitedetail/DEL123", Screen.SiteDetail.createRoute("DEL123"))
    }
}
