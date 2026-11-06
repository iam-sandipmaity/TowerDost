package app.telecom.sitetracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
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

    companion object {
        val bottomNavItems = listOf(Home, Sites, Map, Register, Insights)
    }
}
