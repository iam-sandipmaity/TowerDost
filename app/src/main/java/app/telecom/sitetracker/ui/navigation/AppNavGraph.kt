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
import app.telecom.sitetracker.ui.activity.ActivityScreen
import app.telecom.sitetracker.ui.activity.ActivityViewModel
import app.telecom.sitetracker.ui.createsite.CreateSiteScreen
import app.telecom.sitetracker.ui.createsite.CreateSiteViewModel
import app.telecom.sitetracker.ui.tools.ToolsScreen
import app.telecom.sitetracker.ui.home.HomeScreen
import app.telecom.sitetracker.ui.home.HomeUiState
import app.telecom.sitetracker.ui.importwizard.ImportWizardScreen
import app.telecom.sitetracker.ui.importwizard.ImportWizardViewModel
import app.telecom.sitetracker.ui.incident.LogIncidentScreen
import app.telecom.sitetracker.ui.incident.LogIncidentViewModel
import app.telecom.sitetracker.ui.insights.InsightsScreen
import app.telecom.sitetracker.ui.insights.InsightsViewModel
import app.telecom.sitetracker.ui.map.MapScreen
import app.telecom.sitetracker.ui.merger.VlookupMergerScreen
import app.telecom.sitetracker.ui.merger.VlookupMergerViewModel
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
import app.telecom.sitetracker.ui.template.UniversalTemplateScreen
import app.telecom.sitetracker.ui.template.UniversalTemplateViewModel

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
        startDestination = Screen.Sites.route,
        modifier = modifier
    ) {
        // 1. Sites Master Directory (Main Tab 1)
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
                onAddSiteClick = {
                    navController.navigate(Screen.CreateSite.route)
                },
                onSeedSampleData = onSeedSampleData
            )
        }

        // 2. Activity / Site Logs Feed (Main Tab 2)
        composable(Screen.Activity.route) {
            val activityViewModel: ActivityViewModel = viewModel {
                ActivityViewModel(
                    incidentRepository = app.incidentRepository
                )
            }
            ActivityScreen(
                viewModel = activityViewModel,
                onSiteClick = { sapId ->
                    navController.navigate(Screen.SiteDetail.createRoute(sapId))
                },
                onAddLogClick = {
                    navController.navigate(Screen.LogIncident.createRoute())
                },
                onEditLogClick = { sapId, incidentId ->
                    navController.navigate(Screen.LogIncident.createRoute(sapId = sapId, incidentId = incidentId))
                }
            )
        }

        // 3. Tools & Data (Main Tab 3)
        composable(Screen.Tools.route) {
            ToolsScreen(
                onNavigateToImport = {
                    navController.navigate(Screen.ImportWizard.route)
                },
                onNavigateToTemplate = {
                    navController.navigate(Screen.UniversalTemplate.route)
                },
                onNavigateToMerger = {
                    navController.navigate(Screen.VlookupMerger.route)
                }
            )
        }

        // Legacy Home Dashboard
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
                    navController.navigate(Screen.Activity.route)
                },
                onOpenIncidentsClick = {
                    navController.navigate(Screen.Activity.route)
                }
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
                    incidentRepository = app.incidentRepository,
                    outageRepository = app.outageRepository
                )
            }
            SiteDetailScreen(
                viewModel = siteDetailViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTemplate = {
                    navController.navigate(Screen.UniversalTemplate.route)
                },
                onLogIncident = { siteSapId ->
                    navController.navigate(Screen.LogIncident.createRoute(sapId = siteSapId))
                },
                onEditLog = { siteSapId, incidentId ->
                    navController.navigate(Screen.LogIncident.createRoute(sapId = siteSapId, incidentId = incidentId))
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
                        popUpTo(Screen.Sites.route) { inclusive = true }
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
                    reasonRepository = app.reasonRepository,
                    siteRepository = app.siteRepository,
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

        // 9. Log Incident / Outage Screen (Add or Edit)
        composable(
            route = Screen.LogIncident.route,
            arguments = listOf(
                navArgument("sapId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("incidentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val sapId = backStackEntry.arguments?.getString("sapId")
            val incidentId = backStackEntry.arguments?.getString("incidentId")
            val logIncidentViewModel: LogIncidentViewModel = viewModel(key = "log_${sapId}_$incidentId") {
                LogIncidentViewModel(
                    initialSapId = sapId,
                    initialIncidentId = incidentId,
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
                onIncidentSaved = { targetSapId, id ->
                    navController.popBackStack()
                },
                onIncidentDeleted = {
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

        // 11. Universal Specs Template Screen
        composable(Screen.UniversalTemplate.route) {
            val templateViewModel: UniversalTemplateViewModel = viewModel {
                UniversalTemplateViewModel(app.siteRepository)
            }
            UniversalTemplateScreen(
                viewModel = templateViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 12. Create New Master Site Screen (Pillar 2)
        composable(Screen.CreateSite.route) {
            val createSiteViewModel: CreateSiteViewModel = viewModel {
                CreateSiteViewModel(app.siteRepository)
            }
            CreateSiteScreen(
                viewModel = createSiteViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSiteCreated = { sapId ->
                    navController.navigate(Screen.SiteDetail.createRoute(sapId)) {
                        popUpTo(Screen.Sites.route)
                    }
                }
            )
        }

        // 13. VLOOKUP Sheet Column Merger (Pillar 3)
        composable(Screen.VlookupMerger.route) {
            val mergerViewModel: VlookupMergerViewModel = viewModel {
                VlookupMergerViewModel(
                    application = app,
                    db = app.database,
                    clock = app.clock
                )
            }
            VlookupMergerScreen(
                viewModel = mergerViewModel,
                onNavigateBack = { navController.popBackStack() },
                onComplete = {
                    navController.navigate(Screen.Sites.route) {
                        popUpTo(Screen.Sites.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
