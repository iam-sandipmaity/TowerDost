package app.telecom.sitetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.telecom.sitetracker.ui.theme.TelecomAmber
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
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (screen in Screen.bottomNavItems) {
                                val selected = currentRoute == screen.route
                                val tint = if (selected) TelecomAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable {
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
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        screen.icon,
                                        contentDescription = stringResource(screen.titleRes),
                                        modifier = Modifier.size(20.dp),
                                        tint = tint
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(screen.titleRes),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = tint
                                        ),
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(if (selected) TelecomAmber else Color.Transparent)
                                    )
                                }
                            }
                        }
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
