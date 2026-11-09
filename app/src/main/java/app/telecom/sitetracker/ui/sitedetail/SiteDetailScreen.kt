package app.telecom.sitetracker.ui.sitedetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.telecom.sitetracker.R
import app.telecom.sitetracker.data.entity.IncidentEntity
import kotlinx.coroutines.launch
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.ui.components.EnterpriseListRow
import app.telecom.sitetracker.ui.components.EnterpriseSegmentedControl
import app.telecom.sitetracker.ui.components.EnterpriseTopBar
import app.telecom.sitetracker.ui.components.SearchCapsule
import app.telecom.sitetracker.ui.components.StatusDotBadge
import app.telecom.sitetracker.ui.theme.MetricMonoStyle
import app.telecom.sitetracker.ui.theme.RFCyan
import app.telecom.sitetracker.ui.theme.SapIdStyle
import app.telecom.sitetracker.ui.theme.StatusRestoredGreen
import app.telecom.sitetracker.ui.theme.TagIssueColor
import app.telecom.sitetracker.ui.theme.TagMaintBg
import app.telecom.sitetracker.ui.theme.TagMaintColor
import app.telecom.sitetracker.ui.theme.TagNoteColor
import app.telecom.sitetracker.ui.theme.TagVisitColor
import app.telecom.sitetracker.ui.theme.TelecomAmber
import app.telecom.sitetracker.util.Dates
import java.util.Locale

@Composable
fun SiteDetailScreen(
    viewModel: SiteDetailViewModel,
    onNavigateBack: () -> Unit,
    onLogIncident: (String) -> Unit,
    onEditLog: (sapId: String, incidentId: String) -> Unit = { _, _ -> },
    onNavigateToTemplate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var specFilterQuery by remember { mutableStateOf("") }
    var logToDelete by remember { mutableStateOf<IncidentEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose { }
    }

    LaunchedEffect(uiState.userMessage, uiState.errorMessage) {
        val msg = uiState.userMessage ?: uiState.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUserMessage()
        }
    }

    LaunchedEffect(uiState.site) {
        val site = uiState.site
        if (site?.lat != null && site.lng != null) {
            val loc = app.telecom.sitetracker.util.Geo.getLastKnownLocation(context)
            if (loc != null) {
                viewModel.updateCurrentLocation(loc.latitude, loc.longitude)
            }
        }
    }

    Scaffold(
        topBar = {
            EnterpriseTopBar(
                title = uiState.site?.sapId ?: stringResource(R.string.site_detail_title),
                subtitle = listOfNotNull(uiState.site?.name, uiState.site?.city).joinToString(" • ").ifBlank { null },
                onBack = onNavigateBack,
                actions = {
                    val currentTabId = uiState.selectedTabId
                    val isCurrentlyEditing = when (currentTabId) {
                        "OVERVIEW" -> uiState.isEditingOverview
                        "LOGS" -> false
                        else -> uiState.isEditingSpecs
                    }

                    Surface(
                        onClick = {
                            when (currentTabId) {
                                "OVERVIEW" -> {
                                    if (uiState.isEditingOverview) viewModel.cancelEditingOverview()
                                    else viewModel.startEditingOverview()
                                }
                                "LOGS" -> onLogIncident(uiState.site?.sapId ?: "")
                                else -> {
                                    if (uiState.isEditingSpecs) viewModel.cancelEditingSpecs()
                                    else viewModel.startEditingSpecs()
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCurrentlyEditing) TelecomAmber.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (isCurrentlyEditing) TelecomAmber else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isCurrentlyEditing) Icons.Default.Lock else if (currentTabId == "LOGS") Icons.Default.Add else Icons.Default.Edit,
                                contentDescription = null,
                                tint = if (isCurrentlyEditing) TelecomAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    isCurrentlyEditing -> "Cancel"
                                    currentTabId == "LOGS" -> "+ Log"
                                    currentTabId == "OVERVIEW" -> "Edit Site"
                                    currentTabId == "SPECS" -> "Edit Specs"
                                    else -> "Edit Tab"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isCurrentlyEditing) TelecomAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.exportSingleSiteReport(context) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Export Site CSV",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            val site = uiState.site ?: return@IconButton
                            shareSiteDetails(context, site, uiState.groupedSpecs)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share specs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.site != null) {
                val site = uiState.site!!
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onLogIncident(site.sapId) },
                                colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Add Log",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            if (site.lat != null && site.lng != null) {
                                OutlinedButton(
                                    onClick = { launchNavigation(context, site.lat, site.lng, site.sapId) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Navigation,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Map", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            if (!uiState.contactPhone.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = { launchCall(context, uiState.contactPhone!!) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Call", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TelecomAmber)
            }
        } else if (uiState.site == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Site not found", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val site = uiState.site!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Pinned Telemetry Header Banner
                SiteHeaderBanner(
                    site = site,
                    distanceInfo = uiState.distanceInfo,
                    onCopy = { label, value ->
                        copyToClipboard(context, label, value)
                        scope.launch { snackbarHostState.showSnackbar("Copied $label to clipboard") }
                    }
                )

                // Dynamic Tab Strip with horizontal scrolling and [+] button
                val currentTabId = uiState.selectedTabId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    uiState.tabs.forEach { tab ->
                        val isSelected = tab.tabId.equals(currentTabId, ignoreCase = true)
                        val count = when (tab.tabId) {
                            "SPECS" -> if (isSelected) uiState.groupedSpecs.values.sumOf { it.size } else null
                            "LOGS" -> uiState.incidents.size
                            "OVERVIEW" -> null
                            else -> if (isSelected) uiState.groupedSpecs.values.sumOf { it.size } else null
                        }
                        val title = if (count != null) "${tab.displayName} ($count)" else tab.displayName

                        Surface(
                            onClick = { viewModel.selectTab(tab.tabId) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) RFCyan else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .defaultMinSize(minWidth = 64.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) RFCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    // [+] Add Tab Button
                    Surface(
                        onClick = { viewModel.showCreateTabDialog() },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .defaultMinSize(minWidth = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add New Tab",
                                tint = RFCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Tab",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = RFCyan
                                )
                            )
                        }
                    }
                }

                // Tab Content
                when (currentTabId) {
                    "LOGS" -> SiteLogsTab(
                        logs = uiState.incidents,
                        isEditMode = uiState.isEditMode,
                        onAddLogClick = { onLogIncident(site.sapId) },
                        onEditLogClick = { incId -> onEditLog(site.sapId, incId) },
                        onDeleteLogClick = { logToDelete = it }
                    )
                    "OVERVIEW" -> OverviewTab(
                        site = site,
                        isEditMode = uiState.isEditMode,
                        isEditing = uiState.isEditingOverview,
                        draft = uiState.draftOverview ?: site,
                        onStartEdit = { viewModel.startEditingOverview() },
                        onCancelEdit = { viewModel.cancelEditingOverview() },
                        onDraftChange = { transform -> viewModel.updateOverviewDraft(transform) },
                        onSaveClick = { viewModel.requestSaveOverview() },
                        onCopy = { label, value ->
                            copyToClipboard(context, label, value)
                            scope.launch { snackbarHostState.showSnackbar("Copied $label to clipboard") }
                        },
                        onNavigateGps = { lat, lng, label ->
                            launchNavigation(context, lat, lng, label)
                        }
                    )
                    else -> {
                        val isCustomTab = currentTabId != "SPECS"
                        Column {
                            SpecsTab(
                                site = site,
                                groupedSpecs = uiState.groupedSpecs,
                                filterQuery = specFilterQuery,
                                isEditingSpecs = uiState.isEditingSpecs,
                                draftSections = uiState.draftSpecsSections,
                                onFilterChange = { specFilterQuery = it },
                                onNavigateToTemplate = onNavigateToTemplate,
                                onStartEditSpecs = { viewModel.startEditingSpecs() },
                                onCancelEditSpecs = { viewModel.cancelEditingSpecs() },
                                onSaveSpecsClick = { viewModel.requestSaveSpecs() },
                                onAddSpecItem = { secIdx -> viewModel.addDraftSpecItem(secIdx) },
                                onUpdateSpecItem = { secIdx, tempId, k, v ->
                                    viewModel.updateDraftSpecItem(secIdx, tempId, k, v)
                                },
                                onRemoveSpecItem = { secIdx, tempId ->
                                    viewModel.removeDraftSpecItem(secIdx, tempId)
                                },
                                onShowAddSection = { viewModel.showAddSectionDialog() },
                                onRemoveSection = { secIdx -> viewModel.removeDraftSection(secIdx) },
                                onCopySpec = { key, value ->
                                    copyToClipboard(context, key, value)
                                    scope.launch { snackbarHostState.showSnackbar("Copied $key to clipboard") }
                                }
                            )

                            if (isCustomTab && !uiState.isEditingSpecs) {
                                val currentTabEntity = uiState.tabs.find { it.tabId.equals(currentTabId, ignoreCase = true) }
                                OutlinedButton(
                                    onClick = { viewModel.requestDeleteTab(currentTabId) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TagIssueColor),
                                    border = BorderStroke(1.dp, TagIssueColor.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                        .heightIn(min = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = TagIssueColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Delete '${currentTabEntity?.displayName ?: currentTabId}' Tab",
                                        fontWeight = FontWeight.Bold,
                                        color = TagIssueColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Safeguard: Overview Save Confirmation Dialog
    if (uiState.confirmSaveOverview && uiState.site != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSaveOverviewConfirmation() },
            icon = {
                Icon(Icons.Default.Edit, contentDescription = null, tint = TelecomAmber, modifier = Modifier.size(36.dp))
            },
            title = {
                Text("Save Site Overview Changes?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "You are updating the master record for site ${uiState.site?.sapId}.\n\nThese modifications will be permanently stored in the local database and will be included in all future master CSV exports.\n\nDo you want to apply these changes?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmAndSaveOverview() },
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber)
                ) {
                    Text("Confirm & Save", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSaveOverviewConfirmation() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Safeguard: Specifications Save Confirmation Dialog
    if (uiState.confirmSaveSpecs && uiState.site != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSaveSpecsConfirmation() },
            icon = {
                Icon(Icons.Default.Edit, contentDescription = null, tint = TelecomAmber, modifier = Modifier.size(36.dp))
            },
            title = {
                Text("Save Specifications Changes?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "You are updating technical specifications for site ${uiState.site?.sapId}.\n\nThese modifications will be permanently stored in the local database and will be reflected in all future CSV exports.\n\nDo you want to apply these changes?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmAndSaveSpecs() },
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber)
                ) {
                    Text("Confirm & Save", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSaveSpecsConfirmation() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Add Specification Section
    if (uiState.isAddingSectionDialog) {
        var sectionInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissAddSectionDialog() },
            title = {
                Text("Add Specification Section", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a name for the new equipment category (e.g. TRANSMISSION, SECURITY, CLIMATE CONTROL, POWER).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = sectionInput,
                        onValueChange = { sectionInput = it },
                        label = { Text("Section Name") },
                        placeholder = { Text("e.g. OPTICAL FIBRE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.addDraftSection(sectionInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber),
                    enabled = sectionInput.isNotBlank()
                ) {
                    Text("Add Section", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAddSectionDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Safeguard: Log Deletion Confirmation Dialog
    if (logToDelete != null) {
        val target = logToDelete!!
        AlertDialog(
            onDismissRequest = { logToDelete = null },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = TagIssueColor, modifier = Modifier.size(36.dp))
            },
            title = {
                Text("Delete Site Log?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "⚠️ Warning: This will permanently remove this log entry from the database. Any notes, technician details, and photos attached to this log will be deleted.\n\nThis action cannot be undone. Are you sure you want to proceed?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteIncident(target.id)
                        logToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TagIssueColor)
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { logToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Create New Universal Tab
    if (uiState.isCreatingTabDialog) {
        var tabNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissCreateTabDialog() },
            title = {
                Text("Create New Universal Tab", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a name for the new site tab (e.g. Tenant Info, Solar Specs, EB Billing, Battery Bank). This tab will be available across all sites.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tabNameInput,
                        onValueChange = { tabNameInput = it },
                        label = { Text("Tab Name") },
                        placeholder = { Text("e.g. Tenant Info") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.createUniversalTab(tabNameInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = RFCyan),
                    enabled = tabNameInput.isNotBlank()
                ) {
                    Text("Create Tab", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCreateTabDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Delete Tab with Option 2A Pre-Deletion Backup Verification Gate
    if (uiState.tabToDelete != null) {
        val tab = uiState.tabToDelete!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteTabDialog() },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = TagIssueColor, modifier = Modifier.size(36.dp))
            },
            title = {
                Text("Delete Tab: ${tab.displayName}?", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "⚠️ This tab contains specifications across ${uiState.tabDeleteSiteCount} site(s). To prevent accidental or irreversible data loss, TowerDost requires generating a verified JSON backup file to your device storage before deletion.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedButton(
                        onClick = { viewModel.generateSafetyBackup(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (uiState.backupVerified) RFCyan else TelecomAmber
                        )
                    ) {
                        Icon(
                            imageVector = if (uiState.backupVerified) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.backupVerified) "Backup Verified (Ready)" else "Generate Safety Backup (.json)",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (uiState.backupVerified && uiState.backupFilePath != null) {
                        Text(
                            text = "✅ Safety backup verified: ${uiState.backupFilePath}",
                            style = MaterialTheme.typography.labelSmall,
                            color = RFCyan
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteTab(context) },
                    enabled = uiState.backupVerified,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TagIssueColor,
                        disabledContainerColor = TagIssueColor.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        "Confirm Delete",
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.backupVerified) Color.White else Color.White.copy(alpha = 0.5f)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissDeleteTabDialog() },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SiteHeaderBanner(
    site: SiteEntity,
    distanceInfo: String? = null,
    onCopy: (String, String) -> Unit = { _, _ -> }
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onCopy("SAP ID", site.sapId) }
                    ) {
                        Text(
                            text = site.sapId,
                            style = SapIdStyle.copy(
                                fontSize = 20.sp,
                                color = TelecomAmber
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy SAP ID",
                            tint = TelecomAmber.copy(alpha = 0.5f),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    if (!site.city.isNullOrBlank() || !site.district.isNullOrBlank()) {
                        val locText = listOfNotNull(site.city, site.district).joinToString(" • ")
                        StatusDotBadge(
                            text = locText,
                            color = RFCyan,
                            modifier = Modifier.clickable { onCopy("Location", locText) }
                        )
                    }
                }

                if (!site.name.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = site.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onCopy("Site Name", site.name) }
                    )
                }

                if (!site.address.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = site.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onCopy("Address", site.address) }
                    )
                }

                if (site.lat != null && site.lng != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "GPS: ${site.lat.format(5)}, ${site.lng.format(5)}",
                            style = MetricMonoStyle.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onCopy("GPS Coordinates", "${site.lat}, ${site.lng}") }
                        )
                        if (!distanceInfo.isNullOrBlank()) {
                            Surface(
                                color = TelecomAmber.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TelecomAmber.copy(alpha = 0.35f))
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
                                        text = "$distanceInfo away",
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = TelecomAmber
                                    )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun SiteLogsTab(
    logs: List<IncidentEntity>,
    isEditMode: Boolean,
    onAddLogClick: () -> Unit,
    onEditLogClick: (String) -> Unit,
    onDeleteLogClick: (IncidentEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No notes or issues recorded for this site yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onAddLogClick,
                        colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add First Log Entry", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs, key = { it.id }) { log ->
                    val cat = log.category.lowercase().trim()
                    val (badgeText, badgeColor) = when {
                        cat.contains("maint") || cat.contains("repair") -> Pair("MAINTENANCE", TagMaintColor)
                        cat.contains("visit") || cat.contains("site") || cat.contains("inspect") || cat.contains("audit") -> Pair("VISIT", TagVisitColor)
                        cat.contains("note") || cat.contains("general") -> Pair("NOTE", TagNoteColor)
                        cat.contains("issue") || log.isOutage == 1 -> Pair("ISSUE", TagIssueColor)
                        else -> Pair(log.category.uppercase().ifBlank { "NOTE" }, TagNoteColor)
                    }

                    EnterpriseListRow(onClick = { if (isEditMode) onEditLogClick(log.id) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusDotBadge(text = badgeText, color = badgeColor)

                            Text(
                                text = Dates.formatDateTime(log.occurredAt),
                                style = MetricMonoStyle.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        val text = log.description ?: log.title
                        if (text.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp
                            )
                        }

                        if (!log.actionTaken.isNullOrBlank() && log.actionTaken != text) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Action: ${log.actionTaken}",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusRestoredGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!log.engineer.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = log.engineer,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            if (isEditMode) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onEditLogClick(log.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Log",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteLogClick(log) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Log",
                                            tint = TagIssueColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun SpecsTab(
    site: SiteEntity,
    groupedSpecs: Map<String, List<SiteFieldEntity>>,
    filterQuery: String,
    isEditingSpecs: Boolean,
    draftSections: List<SpecDraftSection>,
    onFilterChange: (String) -> Unit,
    onNavigateToTemplate: () -> Unit = {},
    onStartEditSpecs: () -> Unit,
    onCancelEditSpecs: () -> Unit,
    onSaveSpecsClick: () -> Unit,
    onAddSpecItem: (sectionIndex: Int) -> Unit,
    onUpdateSpecItem: (sectionIndex: Int, tempId: String, key: String, value: String) -> Unit,
    onRemoveSpecItem: (sectionIndex: Int, tempId: String) -> Unit,
    onShowAddSection: () -> Unit,
    onRemoveSection: (sectionIndex: Int) -> Unit,
    onCopySpec: (String, String) -> Unit
) {
    if (isEditingSpecs) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "specs_editing_header") {
                Spacer(modifier = Modifier.height(4.dp))
                // Editing Header Banner (matching Overview's TagMaintBg styling)
                Surface(
                    color = TagMaintBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = TagMaintColor, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Editing Specifications: ${site.sapId}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TagMaintColor
                            )
                            Text(
                                text = "Modifications will update master records and reflect in all CSV exports.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (draftSections.isEmpty()) {
                item(key = "specs_empty_sections") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "No specification sections exist yet. Tap Add New Section below to begin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                draftSections.forEachIndexed { secIdx, section ->
                    item(key = "section_card_${secIdx}_${section.sectionName}") {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Section Card Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = section.sectionName.uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = RFCyan,
                                        letterSpacing = 0.5.sp
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onAddSpecItem(secIdx) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Add Field", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        IconButton(
                                            onClick = { onRemoveSection(secIdx) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Section ${section.sectionName}",
                                                tint = TagIssueColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )

                                if (section.items.isEmpty()) {
                                    Text(
                                        text = "No fields in this section. Tap 'Add Field' above.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                } else {
                                    section.items.forEach { item ->
                                        SpecEditRow(
                                            item = item,
                                            onKeyChange = { newKey ->
                                                onUpdateSpecItem(secIdx, item.tempId, newKey, item.fieldValue)
                                            },
                                            onValueChange = { newVal ->
                                                onUpdateSpecItem(secIdx, item.tempId, item.fieldKey, newVal)
                                            },
                                            onDelete = {
                                                onRemoveSpecItem(secIdx, item.tempId)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "specs_add_section_btn") {
                OutlinedButton(
                    onClick = onShowAddSection,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, TelecomAmber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = TelecomAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add New Section",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TelecomAmber
                    )
                }
            }

            item(key = "specs_action_buttons") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancelEditSpecs,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = onSaveSpecsClick,
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Changes", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    } else {
        // View Mode
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            val totalCount = groupedSpecs.values.sumOf { it.size }
                            Text(
                                text = "Specifications ($totalCount fields)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Global Template Order • Tap Template to reorder",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onNavigateToTemplate,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Template", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onStartEditSpecs,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Specs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Search Capsule Filter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchCapsule(
                    query = filterQuery,
                    onQueryChange = onFilterChange,
                    placeholder = "Filter specifications...",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val filteredGroups = remember(groupedSpecs, filterQuery) {
                if (filterQuery.isBlank()) groupedSpecs
                else {
                    val q = filterQuery.trim().lowercase(Locale.US)
                    groupedSpecs.mapValues { (_, fields) ->
                        fields.filter {
                            it.fieldKey.lowercase(Locale.US).contains(q) ||
                                    (it.fieldValue?.lowercase(Locale.US)?.contains(q) == true)
                        }
                    }.filterValues { it.isNotEmpty() }
                }
            }

            if (filteredGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (filterQuery.isBlank()) "No equipment specifications available." else "No specs match \"$filterQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    filteredGroups.entries.forEachIndexed { groupIndex, (groupName, fields) ->
                        item(key = "group_header_${groupIndex}_$groupName") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = groupName.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = RFCyan,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        itemsIndexed(
                            items = fields,
                            key = { index, field ->
                                if (field.id > 0L) "spec_id_${field.id}_$index"
                                else "spec_group_${groupIndex}_${field.fieldKey}_$index"
                            }
                        ) { _, field ->
                            EnterpriseSpecRow(
                                field = field,
                                onCopy = { onCopySpec(field.fieldKey, field.fieldValue ?: "") }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SpecEditRow(
    item: SpecDraftItem,
    onKeyChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var keyText by remember(item.tempId) { mutableStateOf(item.fieldKey) }
    var valueText by remember(item.tempId) { mutableStateOf(item.fieldValue) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = keyText,
            onValueChange = {
                keyText = it
                onKeyChange(it)
            },
            label = { Text("Field Name", fontSize = 11.sp) },
            placeholder = { Text("e.g. CAPACITY") },
            singleLine = true,
            modifier = Modifier.weight(1.1f)
        )

        OutlinedTextField(
            value = valueText,
            onValueChange = {
                valueText = it
                onValueChange(it)
            },
            label = { Text("Value", fontSize = 11.sp) },
            placeholder = { Text("e.g. 48V 100A") },
            singleLine = true,
            modifier = Modifier.weight(1.1f)
        )

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Field",
                tint = TagIssueColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun EnterpriseSpecRow(
    field: SiteFieldEntity,
    onCopy: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = field.fieldKey,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.2f)
                )

                Text(
                    text = field.fieldValue ?: "—",
                    style = MetricMonoStyle.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy ${field.fieldKey}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun OverviewTab(
    site: SiteEntity,
    isEditMode: Boolean,
    isEditing: Boolean,
    draft: SiteEntity,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDraftChange: ((SiteEntity) -> SiteEntity) -> Unit,
    onSaveClick: () -> Unit,
    onCopy: (String, String) -> Unit,
    onNavigateGps: (Double, Double, String) -> Unit
) {
    if (isEditing) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = TagMaintBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = TagMaintColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Editing Overview: ${site.sapId}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TagMaintColor
                        )
                        Text(
                            text = "Modifications will update master records and reflect in all CSV exports.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            OutlinedTextField(
                value = draft.name ?: "",
                onValueChange = { v -> onDraftChange { it.copy(name = v) } },
                label = { Text("Site Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft.lat?.toString() ?: "",
                    onValueChange = { v -> onDraftChange { it.copy(lat = v.toDoubleOrNull()) } },
                    label = { Text("Latitude") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.lng?.toString() ?: "",
                    onValueChange = { v -> onDraftChange { it.copy(lng = v.toDoubleOrNull()) } },
                    label = { Text("Longitude") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft.circle ?: "",
                    onValueChange = { v -> onDraftChange { it.copy(circle = v) } },
                    label = { Text("Circle") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.district ?: "",
                    onValueChange = { v -> onDraftChange { it.copy(district = v) } },
                    label = { Text("District") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft.city ?: "",
                    onValueChange = { v -> onDraftChange { it.copy(city = v) } },
                    label = { Text("City / JC") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.state ?: "",
                    onValueChange = { v -> onDraftChange { it.copy(state = v) } },
                    label = { Text("State") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft.technology ?: "",
                    onValueChange = { v -> onDraftChange { it.copy(technology = v) } },
                    label = { Text("Technology (e.g. 4G/5G)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = draft.siteType ?: "",
                    onValueChange = { v -> onDraftChange { it.copy(siteType = v) } },
                    label = { Text("Site Type (e.g. GBT, RTT)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = draft.address ?: "",
                onValueChange = { v -> onDraftChange { it.copy(address = v) } },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            OutlinedTextField(
                value = draft.notes ?: "",
                onValueChange = { v -> onDraftChange { it.copy(notes = v) } },
                label = { Text("Remarks / Notes") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCancelEdit,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onSaveClick,
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    } else {
        // Locked View Mode
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Edit else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isEditMode) TelecomAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isEditMode) "Overview Editable" else "View Only Inspection",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isEditMode) "Tap Edit Details to modify site metadata" else "Locked against accidental edits",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val summary = buildSiteSummary(site)
                                onCopy("Site Card", summary)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Card", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onStartEdit,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isEditMode) "Edit Details" else "Unlock to Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            OverviewRow("Site Name", site.name ?: "—", onCopy = onCopy)
            OverviewRow("Circle", site.circle ?: "—", onCopy = onCopy)
            OverviewRow("Region", site.region ?: "—", onCopy = onCopy)
            OverviewRow("District", site.district ?: "—", onCopy = onCopy)
            OverviewRow("City / JC", site.city ?: "—", onCopy = onCopy)
            OverviewRow("State", site.state ?: "—", onCopy = onCopy)
            OverviewRow("Technology", site.technology ?: "—", onCopy = onCopy)
            OverviewRow("Site Type", site.siteType ?: "—", onCopy = onCopy)
            OverviewRow("Address", site.address ?: "—", onCopy = onCopy)
            if (site.lat != null && site.lng != null) {
                val coords = "${site.lat.format(5)}, ${site.lng.format(5)}"
                val rawCoords = "${site.lat}, ${site.lng}"
                OverviewRow(
                    label = "GPS Coordinates",
                    value = coords,
                    onCopy = { l, _ -> onCopy(l, rawCoords) },
                    onAction = { onNavigateGps(site.lat, site.lng, site.sapId) },
                    actionIcon = Icons.Default.NearMe,
                    actionDescription = "Navigate to site"
                )
            }
            OverviewRow("Tags / Scope", site.tags ?: "—", onCopy = onCopy)
            OverviewRow("Source File", site.sourceFile ?: "—", onCopy = onCopy)
            if (site.updatedAt != null) {
                OverviewRow("Last Updated", Dates.formatDateTime(site.updatedAt), onCopy = onCopy)
            }
            if (!site.notes.isNullOrBlank()) {
                OverviewRow("Remarks", site.notes, onCopy = onCopy)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun OverviewRow(
    label: String,
    value: String,
    onCopy: ((label: String, value: String) -> Unit)? = null,
    onAction: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
    actionDescription: String? = null
) {
    val canCopy = onCopy != null && value != "—" && value.isNotBlank()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (canCopy) Modifier.clickable { onCopy(label, value) }
                else Modifier
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(125.dp)
                )

                Text(
                    text = value,
                    style = MetricMonoStyle.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (onAction != null && actionIcon != null) {
                        IconButton(
                            onClick = onAction,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = actionDescription,
                                tint = RFCyan,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    if (canCopy) {
                        IconButton(
                            onClick = { onCopy(label, value) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy $label",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        }
    }
}

private fun buildSiteSummary(site: SiteEntity): String {
    val sb = StringBuilder()
    sb.appendLine("📍 [TowerDost Site Card]")
    sb.appendLine("SAP ID: ${site.sapId}")
    if (!site.name.isNullOrBlank()) sb.appendLine("Name: ${site.name}")
    val locParts = listOfNotNull(site.circle, site.district, site.city, site.state).filter { it.isNotBlank() }
    if (locParts.isNotEmpty()) sb.appendLine("Location: ${locParts.joinToString(" • ")}")
    if (!site.technology.isNullOrBlank()) sb.appendLine("Tech: ${site.technology}")
    if (!site.siteType.isNullOrBlank()) sb.appendLine("Type: ${site.siteType}")
    if (!site.status.isNullOrBlank()) sb.appendLine("Status: ${site.status}")
    if (site.lat != null && site.lng != null) sb.appendLine("GPS: ${site.lat.format(5)}, ${site.lng.format(5)}")
    if (!site.address.isNullOrBlank()) sb.appendLine("Address: ${site.address}")
    if (!site.notes.isNullOrBlank()) sb.appendLine("Remarks: ${site.notes}")
    return sb.toString().trimEnd()
}

private fun launchNavigation(context: Context, lat: Double, lng: Double, label: String) {
    val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
    val intent = Intent(Intent.ACTION_VIEW, geoUri)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        val webUri = Uri.parse("https://maps.google.com/?q=$lat,$lng")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open map", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun launchCall(context: Context, phone: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Could not dial phone", Toast.LENGTH_SHORT).show()
    }
}

private fun shareSiteDetails(
    context: Context,
    site: SiteEntity,
    groupedSpecs: Map<String, List<SiteFieldEntity>>
) {
    val sb = StringBuilder()
    sb.append("Site: ${site.sapId} (${site.name ?: ""})\n")
    if (site.lat != null && site.lng != null) {
        sb.append("GPS: ${site.lat}, ${site.lng}\n")
    }
    if (!site.circle.isNullOrBlank()) sb.append("Circle: ${site.circle}\n")
    if (!site.district.isNullOrBlank()) sb.append("District: ${site.district}\n")
    sb.append("\nEquipment Specs:\n")
    groupedSpecs.forEach { (group, fields) ->
        sb.append("[$group]\n")
        fields.forEach { f ->
            sb.append("• ${f.fieldKey}: ${f.fieldValue ?: "—"}\n")
        }
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Site Specifications: ${site.sapId}")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(intent, "Share Site Specifications"))
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}

private fun Double.format(digits: Int): String = String.format(Locale.US, "%.${digits}f", this)
