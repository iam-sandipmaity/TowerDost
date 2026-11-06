package app.telecom.sitetracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.telecom.sitetracker.ui.home.HomeScreen
import app.telecom.sitetracker.ui.home.HomeUiState
import app.telecom.sitetracker.ui.insights.InsightsScreen
import app.telecom.sitetracker.ui.map.MapScreen
import app.telecom.sitetracker.ui.outages.DownRegisterScreen
import app.telecom.sitetracker.ui.sitelist.SiteListScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    homeUiState: HomeUiState,
    onSeedSampleData: () -> Unit,
    onDismissHomeMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                uiState = homeUiState,
                onSeedSampleData = onSeedSampleData,
                onLogIncidentClick = {
                    // Navigate to log incident sheet in M2
                },
                onSiteClick = { sapId ->
                    // Navigate to site detail in M1
                },
                onDismissMessage = onDismissHomeMessage
            )
        }

        composable(Screen.Sites.route) {
            SiteListScreen(
                onSiteClick = { sapId ->
                    // Navigate to site detail in M1
                }
            )
        }

        composable(Screen.Map.route) {
            MapScreen(
                onSiteClick = { sapId ->
                    // Open site preview
                }
            )
        }

        composable(Screen.Register.route) {
            DownRegisterScreen(
                onOutageClick = { id ->
                    // Open outage detail
                }
            )
        }

        composable(Screen.Insights.route) {
            InsightsScreen(
                onDayClick = { dayStartMs ->
                    // Open Day view
                }
            )
        }
    }
}
