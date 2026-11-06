package app.telecom.sitetracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.ui.graphics.vector.ImageVector
import app.telecom.sitetracker.R

sealed class Screen(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector
) {
    object Home : Screen("home", R.string.nav_home, Icons.Default.Home)
    object Sites : Screen("sites", R.string.nav_sites, Icons.Default.CellTower)
    object Map : Screen("map", R.string.nav_map, Icons.Default.Map)
    object Register : Screen("register", R.string.nav_register, Icons.Default.AccessTime)
    object Insights : Screen("insights", R.string.nav_insights, Icons.Default.BarChart)

    object SiteDetail : Screen("sitedetail/{sapId}", R.string.site_detail_title, Icons.Default.Description) {
        fun createRoute(sapId: String) = "sitedetail/$sapId"
    }

    object ImportWizard : Screen("import_wizard", R.string.import_title, Icons.Default.UploadFile)
    object Search : Screen("search", R.string.action_search, Icons.Default.Search)

    companion object {
        val bottomNavItems = listOf(Home, Sites, Map, Register, Insights)
    }
}
