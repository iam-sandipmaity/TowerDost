package app.telecom.sitetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.telecom.sitetracker.ui.home.HomeViewModel
import app.telecom.sitetracker.ui.navigation.AppNavGraph
import app.telecom.sitetracker.ui.navigation.Screen
import app.telecom.sitetracker.ui.theme.TelecomSiteTrackerTheme

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels {
        val app = (applicationContext as? SiteTrackerApp) ?: SiteTrackerApp.instance!!
        HomeViewModel.Factory(app)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            enableEdgeToEdge()
        } catch (_: Throwable) {
            // Defensive: non-fatal fallback on custom vendor ROMs
        }

        super.onCreate(savedInstanceState)

        setContent {
            TelecomSiteTrackerTheme {
                val navController = rememberNavController()
                val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()

                MainAppShell(
                    navController = navController,
                    homeViewModel = homeViewModel,
                    homeUiState = homeUiState
                )
            }
        }
    }
}

@Composable
fun MainAppShell(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    homeUiState: app.telecom.sitetracker.ui.home.HomeUiState
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Sites.route

    val isBottomBarDestination = Screen.bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isBottomBarDestination) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    for (screen in Screen.bottomNavItems) {
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    val isTargetStart = screen.route == Screen.Sites.route
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = !isTargetStart
                                        }
                                        launchSingleTop = true
                                        restoreState = !isTargetStart
                                    }
                                }
                            },
                            icon = {
                                Icon(screen.icon, contentDescription = stringResource(screen.titleRes))
                            },
                            label = {
                                Text(stringResource(screen.titleRes))
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            AppNavGraph(
                navController = navController,
                homeUiState = homeUiState,
                onSeedSampleData = { homeViewModel.seedSampleData() },
                onDismissHomeMessage = { homeViewModel.dismissUserMessage() }
            )
        }
    }
}
