package app.telecom.sitetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.telecom.sitetracker.ui.home.HomeViewModel
import app.telecom.sitetracker.ui.navigation.AppNavGraph
import app.telecom.sitetracker.ui.navigation.Screen
import app.telecom.sitetracker.ui.theme.TelecomSiteTrackerTheme
import java.io.PrintWriter
import java.io.StringWriter

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppShell(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    homeUiState: app.telecom.sitetracker.ui.home.HomeUiState
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    var showMenu by remember { mutableStateOf(false) }

    val isBottomBarDestination = Screen.bottomNavItems.any { it.route == currentRoute }
    val showTopBar = isBottomBarDestination && currentRoute != Screen.Sites.route

    val currentScreen = Screen.bottomNavItems.find { it.route == currentRoute } ?: Screen.Home

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(currentScreen.titleRes),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate(Screen.Search.route) }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                        }
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import Wizard") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.ImportWizard.route)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Search") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.Search.route)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Root Cause Catalogue") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.Reasons.route)
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
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
                                    val isTargetStart = screen.route == Screen.Home.route
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
        AppNavGraph(
            navController = navController,
            homeUiState = homeUiState,
            onSeedSampleData = { homeViewModel.seedSampleData() },
            onDismissHomeMessage = { homeViewModel.dismissUserMessage() },
            modifier = Modifier.padding(innerPadding)
        )
    }
}
