package app.telecom.sitetracker.ui.sitelist

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import app.telecom.sitetracker.R
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.ui.components.EnterpriseListRow
import app.telecom.sitetracker.ui.components.EnterpriseSearchBar
import app.telecom.sitetracker.ui.components.EnterpriseTopBar
import app.telecom.sitetracker.ui.components.StatusDotBadge
import app.telecom.sitetracker.ui.theme.RFCyan
import app.telecom.sitetracker.ui.theme.SapIdStyle
import app.telecom.sitetracker.ui.theme.StatusDownRed
import app.telecom.sitetracker.ui.theme.StatusRestoredGreen
import app.telecom.sitetracker.ui.theme.TelecomAmber
import app.telecom.sitetracker.ui.theme.TelemetryCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteListScreen(
    viewModel: SiteListViewModel,
    onSiteClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onImportClick: () -> Unit,
    onAddSiteClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pagedSites = viewModel.pagedSites.collectAsLazyPagingItems()
    val activeCount by viewModel.activeSitesCount.collectAsStateWithLifecycle(initialValue = 0)
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val isNearMeActive by viewModel.isNearMeActive.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val activeFilters by viewModel.activeFilters.collectAsStateWithLifecycle()
    val availableDimensions by viewModel.availableDimensions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilterDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissUserMessage()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onNearMeClicked(context)
        } else {
            Toast.makeText(context, "Location permission needed for Near Me radar", Toast.LENGTH_SHORT).show()
        }
    }

    val isSearchActive = searchQuery.isNotBlank()

    Scaffold(
        topBar = {
            EnterpriseTopBar(
                title = stringResource(R.string.site_list_title),
                subtitle = when {
                    activeFilters.isNotEmpty() && isNearMeActive && isSearchActive -> "${searchResults.size} matches • ${activeFilters.size} filter(s) • Nearest First (GPS)"
                    activeFilters.isNotEmpty() && isNearMeActive -> "${searchResults.size} matches • ${activeFilters.size} filter(s) • Nearest First (GPS)"
                    activeFilters.isNotEmpty() && isSearchActive -> "${searchResults.size} matches • ${activeFilters.size} filter(s)"
                    activeFilters.isNotEmpty() -> "${searchResults.size} matches • ${activeFilters.size} filter(s)"
                    isNearMeActive && isSearchActive -> "${searchResults.size} matches • Nearest First (GPS)"
                    isNearMeActive -> "${searchResults.size} nearby towers (GPS)"
                    isSearchActive -> "${searchResults.size} matches"
                    else -> "$activeCount active towers"
                },
                actions = {
                    IconButton(onClick = onAddSiteClick) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Create New Site",
                            tint = TelecomAmber
                        )
                    }
                    IconButton(onClick = onImportClick) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = "Import file",
                            tint = TelecomAmber
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddSiteClick,
                containerColor = TelecomAmber,
                contentColor = Color(0xFF0F172A),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add New Site")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live Search Bar + Near Me GPS Quick Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    EnterpriseSearchBar(
                        query = searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = "Search SAP ID, site name, city, circle...",
                        onClear = { viewModel.clearSearch() }
                    )
                }

                Surface(
                    onClick = {
                        if (isNearMeActive) {
                            viewModel.clearNearMe()
                        } else {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isNearMeActive) TelecomAmber else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isNearMeActive) TelecomAmber else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isNearMeActive) Icons.Default.Close else Icons.Default.NearMe,
                            contentDescription = if (isNearMeActive) "Clear Near Me" else "Near Me GPS",
                            tint = if (isNearMeActive) Color(0xFF0F172A) else TelecomAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isNearMeActive) "Clear" else "Near Me",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isNearMeActive) Color(0xFF0F172A) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Dynamic Specification & Attribute Filter Row (Max 4 active)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "+ Filter" button
                item(key = "add_filter_button") {
                    Surface(
                        onClick = {
                            if (activeFilters.size >= 4) {
                                Toast.makeText(context, "Maximum 4 filters allowed at once", Toast.LENGTH_SHORT).show()
                            } else {
                                showFilterDialog = true
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (activeFilters.size >= 4) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (activeFilters.size >= 4) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            else TelecomAmber.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.sizeIn(minHeight = 36.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Add Filter",
                                tint = if (activeFilters.size >= 4) MaterialTheme.colorScheme.onSurfaceVariant
                                else TelecomAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (activeFilters.isEmpty()) "+ Filter" else "+ Filter (${activeFilters.size}/4)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeFilters.size >= 4) MaterialTheme.colorScheme.onSurfaceVariant
                                    else TelecomAmber
                                )
                            )
                        }
                    }
                }

                // Active filter chips
                items(activeFilters, key = { it.id }) { filter ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, RFCyan.copy(alpha = 0.7f)),
                        modifier = Modifier.sizeIn(minHeight = 36.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                        ) {
                            Text(
                                text = "${filter.displayName}: ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RFCyan
                                )
                            )
                            Text(
                                text = filter.selectedValue,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { viewModel.removeFilter(filter.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove filter",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // If filters are active, allow 1-tap "Clear All"
                if (activeFilters.isNotEmpty()) {
                    item(key = "clear_all_filters") {
                        TextButton(
                            onClick = { viewModel.clearAllFilters() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.sizeIn(minHeight = 36.dp)
                        ) {
                            Text(
                                text = "Clear All",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = StatusDownRed,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isSearching,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = TelecomAmber
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val isFilterActive = isNearMeActive || isSearchActive || activeFilters.isNotEmpty()
                if (isFilterActive) {
                    if (searchResults.isEmpty() && !isSearching) {
                        EmptySearchResultsView(
                            query = when {
                                isNearMeActive && isSearchActive -> "$searchQuery (Near Me)"
                                isNearMeActive -> "Near Me"
                                isSearchActive -> searchQuery
                                else -> activeFilters.joinToString(", ") { "${it.displayName}: ${it.selectedValue}" }
                            },
                            onClear = {
                                viewModel.clearSearch()
                                viewModel.clearNearMe()
                                viewModel.clearAllFilters()
                            }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp)
                        ) {
                            itemsIndexed(searchResults, key = { index, item -> "${item.site.sapId}_$index" }) { _, item ->
                                SiteCard(
                                    site = item.site,
                                    distanceInfo = item.distanceInfo,
                                    onClick = { onSiteClick(item.site.sapId) },
                                    onCopyCoordinates = { lat, lng ->
                                        copyToClipboard(context, "Coordinates", "$lat, $lng")
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Standard Directory Mode: Display Paged Sites
                    when {
                        pagedSites.loadState.refresh is LoadState.Loading -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(strokeWidth = 2.dp, color = TelecomAmber)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Loading sites directory...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        pagedSites.itemCount == 0 && pagedSites.loadState.refresh !is LoadState.Loading -> {
                            EmptySitesView(
                                onImportClick = onImportClick
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                            ) {
                                items(
                                    count = pagedSites.itemCount,
                                    key = { index ->
                                        val item = pagedSites[index]
                                        if (item != null) "${item.sapId}_$index" else index
                                    }
                                ) { index ->
                                    val site = pagedSites[index]
                                    if (site != null) {
                                        SiteCard(
                                            site = site,
                                            onClick = { onSiteClick(site.sapId) },
                                            onCopyCoordinates = { lat, lng ->
                                                copyToClipboard(context, "Coordinates", "$lat, $lng")
                                            }
                                        )
                                    }
                                }

                                if (pagedSites.loadState.append is LoadState.Loading) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = TelecomAmber)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        DynamicFilterDialog(
            dimensions = availableDimensions,
            onAddFilter = { dim, valItem ->
                viewModel.addFilter(dim, valItem)
            },
            onDismiss = { showFilterDialog = false }
        )
    }
}

@Composable
fun SiteCard(
    site: SiteEntity,
    distanceInfo: String? = null,
    onClick: () -> Unit,
    onCopyCoordinates: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    EnterpriseListRow(
        onClick = onClick,
        modifier = modifier
    ) {
        // Row 1: SAP ID + (Distance Tag + Status Dot Badge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = site.sapId,
                style = SapIdStyle.copy(fontSize = 16.sp, color = TelecomAmber)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!distanceInfo.isNullOrBlank()) {
                    Surface(
                        color = TelecomAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, TelecomAmber.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NearMe,
                                contentDescription = null,
                                tint = TelecomAmber,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = distanceInfo,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    color = TelecomAmber
                                )
                            )
                        }
                    }
                }

                val statusText = site.status ?: "Active"
                val isDown = statusText.equals("down", ignoreCase = true)
                val badgeColor = if (isDown) StatusDownRed else StatusRestoredGreen

                StatusDotBadge(text = statusText, color = badgeColor)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Row 2: Site Name
        if (!site.name.isNullOrBlank()) {
            Text(
                text = site.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Location Breadcrumbs
        val locationParts = listOfNotNull(site.city, site.district, site.circle)
        if (locationParts.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = locationParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 3: Coordinates + Tech Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (site.lat != null && site.lng != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onCopyCoordinates(site.lat, site.lng) }
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "${site.lat.format(4)}, ${site.lng.format(4)}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy coordinates",
                        modifier = Modifier.size(12.dp),
                        tint = TelemetryCyan
                    )
                }
            } else {
                Text(
                    text = "No GPS recorded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!site.technology.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = site.technology,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TelecomAmber),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (!site.siteType.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = site.siteType,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySearchResultsView(
    query: String,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Towers Found",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "No towers matched \"$query\". Check SAP ID spelling or search by district/city.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onClear,
            colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Clear Search", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptySitesView(
    onImportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CellTower,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = TelecomAmber
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Sites Found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_sites_found),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onImportClick,
            colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Import Excel / CSV Master", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

private fun Double.format(digits: Int): String = String.format(java.util.Locale.US, "%.${digits}f", this)

@Composable
private fun DynamicFilterDialog(
    dimensions: List<FilterDimension>,
    onAddFilter: (FilterDimension, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDimension by remember { mutableStateOf<FilterDimension?>(null) }
    var customValueText by remember { mutableStateOf("") }
    var dimensionSearch by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            if (selectedDimension == null) {
                Text(
                    text = "Add Filter (Max 4)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            selectedDimension = null
                            customValueText = ""
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to dimensions",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Value: ${selectedDimension!!.displayName}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        },
        text = {
            if (selectedDimension == null) {
                // Step 1: Choose Dimension
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Choose a database attribute or specification:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dimensionSearch,
                        onValueChange = { dimensionSearch = it },
                        placeholder = { Text("Search attributes/specs...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val filteredDimensions = if (dimensionSearch.isBlank()) dimensions
                    else dimensions.filter { it.displayName.contains(dimensionSearch, ignoreCase = true) }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val coreDims = filteredDimensions.filter { it.isCoreField }
                        if (coreDims.isNotEmpty()) {
                            item(key = "hdr_core") {
                                Text(
                                    text = "CORE SITE ATTRIBUTES",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TelecomAmber,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                            }
                            items(coreDims, key = { "core_${it.key}" }) { dim ->
                                Surface(
                                    onClick = { selectedDimension = dim },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .sizeIn(minHeight = 44.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = dim.displayName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                        )
                                        Text(
                                            text = "${dim.availableValues.size} values",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        val specDims = filteredDimensions.filter { !it.isCoreField }
                        if (specDims.isNotEmpty()) {
                            item(key = "hdr_specs") {
                                Text(
                                    text = "EQUIPMENT SPECIFICATIONS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = RFCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                            items(specDims, key = { "spec_${it.key}" }) { dim ->
                                Surface(
                                    onClick = { selectedDimension = dim },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .sizeIn(minHeight = 44.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = dim.displayName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                            )
                                            if (!dim.groupName.isNullOrBlank()) {
                                                Text(
                                                    text = dim.groupName,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 10.sp
                                                    )
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${dim.availableValues.size} values",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Step 2: Choose Value
                val dim = selectedDimension!!
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Type or select a value for ${dim.displayName}:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customValueText,
                            onValueChange = { customValueText = it },
                            placeholder = { Text("Custom value...", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (customValueText.isNotBlank()) {
                                    onAddFilter(dim, customValueText.trim())
                                    onDismiss()
                                }
                            },
                            enabled = customValueText.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber, contentColor = Color(0xFF0F172A))
                        ) {
                            Text("Apply", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    if (dim.availableValues.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Or tap an existing value (${dim.availableValues.size}):",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(dim.availableValues, key = { it }) { valItem ->
                                Surface(
                                    onClick = {
                                        onAddFilter(dim, valItem)
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .sizeIn(minHeight = 44.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = valItem,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
