package app.telecom.sitetracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.telecom.sitetracker.SiteTrackerApp
import app.telecom.sitetracker.ui.home.HomeScreen
import app.telecom.sitetracker.ui.home.HomeUiState
import app.telecom.sitetracker.ui.importwizard.ImportWizardScreen
import app.telecom.sitetracker.ui.importwizard.ImportWizardViewModel
import app.telecom.sitetracker.ui.incident.LogIncidentScreen
import app.telecom.sitetracker.ui.incident.LogIncidentViewModel
import app.telecom.sitetracker.ui.insights.InsightsScreen
import app.telecom.sitetracker.ui.insights.InsightsViewModel
import app.telecom.sitetracker.ui.map.MapScreen
import app.telecom.sitetracker.ui.outages.DownRegisterScreen
import app.telecom.sitetracker.ui.outages.DownRegisterViewModel
import app.telecom.sitetracker.ui.reasons.ReasonCatalogueScreen
import app.telecom.sitetracker.ui.reasons.ReasonCatalogueViewModel
import app.telecom.sitetracker.ui.search.SearchScreen
import app.telecom.sitetracker.ui.search.SearchViewModel
import app.telecom.sitetracker.ui.sitedetail.SiteDetailScreen
import app.telecom.sitetracker.ui.sitedetail.SiteDetailViewModel
import app.telecom.sitetracker.ui.sitelist.SiteListScreen
import app.telecom.sitetracker.ui.sitelist.SiteListViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController,
    homeUiState: HomeUiState,
    onSeedSampleData: () -> Unit,
    onDismissHomeMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = (context.applicationContext as? SiteTrackerApp) ?: SiteTrackerApp.instance!!

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        // 1. Home Dashboard
        composable(Screen.Home.route) {
            HomeScreen(
                uiState = homeUiState,
                onSeedSampleData = onSeedSampleData,
                onLogIncidentClick = {
                    navController.navigate(Screen.LogIncident.createRoute())
                },
                onSiteClick = { sapId ->
                    navController.navigate(Screen.SiteDetail.createRoute(sapId))
                },
                onDismissMessage = onDismissHomeMessage,
                onTotalSitesClick = {
                    navController.navigate(Screen.Sites.route)
                },
                onSitesDownClick = {
                    navController.navigate(Screen.Register.route)
                },
                onOpenIncidentsClick = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        // 2. Sites Master Directory
        composable(Screen.Sites.route) {
            val siteListViewModel: SiteListViewModel = viewModel {
                SiteListViewModel(
                    siteRepository = app.siteRepository,
                    outageRepository = app.outageRepository,
                    incidentRepository = app.incidentRepository
                )
            }
            SiteListScreen(
                viewModel = siteListViewModel,
                onSiteClick = { sapId ->
                    navController.navigate(Screen.SiteDetail.createRoute(sapId))
                },
                onSearchClick = {
                    navController.navigate(Screen.Search.route)
                },
                onImportClick = {
                    navController.navigate(Screen.ImportWizard.route)
                },
                onSeedSampleData = onSeedSampleData
            )
        }

        // 3. Site Detail Screen
        composable(
            route = Screen.SiteDetail.route,
            arguments = listOf(navArgument("sapId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sapId = backStackEntry.arguments?.getString("sapId") ?: ""
            val siteDetailViewModel: SiteDetailViewModel = viewModel(key = "site_$sapId") {
                SiteDetailViewModel(
                    sapId = sapId,
                    siteRepository = app.siteRepository,
                    incidentRepository = app.incidentRepository
                )
            }
            SiteDetailScreen(
                viewModel = siteDetailViewModel,
                onNavigateBack = { navController.popBackStack() },
                onLogIncident = { siteSapId ->
                    navController.navigate(Screen.LogIncident.createRoute(siteSapId))
                }
            )
        }

        // 4. Import Wizard
        composable(Screen.ImportWizard.route) {
            val importViewModel: ImportWizardViewModel = viewModel {
                ImportWizardViewModel(
                    application = app,
                    db = app.database,
                    clock = app.clock
                )
            }
            ImportWizardScreen(
                viewModel = importViewModel,
                onNavigateBack = { navController.popBackStack() },
                onImportComplete = {
                    navController.navigate(Screen.Sites.route) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // 5. Full-Text & Near Me Search
        composable(Screen.Search.route) {
            val searchViewModel: SearchViewModel = viewModel {
                SearchViewModel(
                    siteRepository = app.siteRepository
                )
            }
            SearchScreen(
                viewModel = searchViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSiteClick = { sapId ->
                    navController.navigate(Screen.SiteDetail.createRoute(sapId))
                }
            )
        }

        // 6. Map Screen
        composable(Screen.Map.route) {
            MapScreen(
                onSiteClick = { sapId ->
                    navController.navigate(Screen.SiteDetail.createRoute(sapId))
                }
            )
        }

        // 7. Down Register Screen
        composable(Screen.Register.route) {
            val downRegisterViewModel: DownRegisterViewModel = viewModel {
                DownRegisterViewModel(
                    outageRepository = app.outageRepository,
                    incidentRepository = app.incidentRepository,
                    clock = app.clock
                )
            }
            DownRegisterScreen(
                viewModel = downRegisterViewModel,
                onSiteClick = { sapId ->
                    navController.navigate(Screen.SiteDetail.createRoute(sapId))
                },
                onLogOutageClick = {
                    navController.navigate(Screen.LogIncident.createRoute())
                }
            )
        }

        // 8. Insights Screen
        composable(Screen.Insights.route) {
            val insightsViewModel: InsightsViewModel = viewModel {
                InsightsViewModel(
                    statsRepository = app.statsRepository,
                    outageRepository = app.outageRepository,
                    incidentRepository = app.incidentRepository,
                    siteRepository = app.siteRepository,
                    clock = app.clock
                )
            }
            InsightsScreen(
                viewModel = insightsViewModel,
                onSiteClick = { sapId: String ->
                    navController.navigate(Screen.SiteDetail.createRoute(sapId))
                }
            )
        }

        // 9. Log Incident / Outage Screen
        composable(
            route = Screen.LogIncident.route,
            arguments = listOf(
                navArgument("sapId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val sapId = backStackEntry.arguments?.getString("sapId")
            val logIncidentViewModel: LogIncidentViewModel = viewModel(key = "log_$sapId") {
                LogIncidentViewModel(
                    initialSapId = sapId,
                    siteRepository = app.siteRepository,
                    incidentRepository = app.incidentRepository,
                    outageRepository = app.outageRepository,
                    reasonRepository = app.reasonRepository,
                    clock = app.clock
                )
            }
            LogIncidentScreen(
                viewModel = logIncidentViewModel,
                onNavigateBack = { navController.popBackStack() },
                onIncidentSaved = { targetSapId, incidentId ->
                    navController.popBackStack()
                }
            )
        }

        // 10. Root Cause Catalogue Screen
        composable(Screen.Reasons.route) {
            val reasonViewModel: ReasonCatalogueViewModel = viewModel {
                ReasonCatalogueViewModel(app.reasonRepository)
            }
            ReasonCatalogueScreen(
                viewModel = reasonViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
