package app.telecom.sitetracker.ui.sitedetail

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.telecom.sitetracker.R
import app.telecom.sitetracker.data.entity.IncidentEntity
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
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var specFilterQuery by remember { mutableStateOf("") }
    var logToDelete by remember { mutableStateOf<IncidentEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage, uiState.errorMessage) {
        val msg = uiState.userMessage ?: uiState.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUserMessage()
        }
    }

    Scaffold(
        topBar = {
            EnterpriseTopBar(
                title = uiState.site?.sapId ?: stringResource(R.string.site_detail_title),
                subtitle = listOfNotNull(uiState.site?.name, uiState.site?.city).joinToString(" • ").ifBlank { null },
                onBack = onNavigateBack,
                actions = {
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
                    Column {
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
                SiteHeaderBanner(site = site)

                // Segmented Tabs: 0 = Specs, 1 = Site Logs, 2 = Overview
                val specCount = uiState.groupedSpecs.values.sumOf { it.size }
                EnterpriseSegmentedControl(
                    options = listOf("Specs ($specCount)", "Logs (${uiState.incidents.size})", "Overview"),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )

                // Tab Content
                when (selectedTab) {
                    0 -> SpecsTab(
                        groupedSpecs = uiState.groupedSpecs,
                        filterQuery = specFilterQuery,
                        onFilterChange = { specFilterQuery = it },
                        onAddSpecClick = { viewModel.startAddingSpec() },
                        onEditSpecClick = { spec -> viewModel.startEditingSpec(spec) }
                    )
                    1 -> SiteLogsTab(
                        logs = uiState.incidents,
                        onAddLogClick = { onLogIncident(site.sapId) },
                        onEditLogClick = { incId -> onEditLog(site.sapId, incId) },
                        onDeleteLogClick = { logToDelete = it }
                    )
                    2 -> OverviewTab(
                        site = site,
                        isEditing = uiState.isEditingOverview,
                        draft = uiState.draftOverview ?: site,
                        onStartEdit = { viewModel.startEditingOverview() },
                        onCancelEdit = { viewModel.cancelEditingOverview() },
                        onDraftChange = { transform -> viewModel.updateOverviewDraft(transform) },
                        onSaveClick = { viewModel.requestSaveOverview() }
                    )
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

    // Safeguard: Spec Editor Dialog (Add or Edit)
    if (uiState.specToEdit != null || uiState.isAddingSpec) {
        val editingSpec = uiState.specToEdit
        val isNew = uiState.isAddingSpec
        SpecEditDialog(
            spec = editingSpec,
            isNew = isNew,
            sapId = uiState.site?.sapId ?: "",
            onDismiss = { viewModel.dismissSpecEditor() },
            onSave = { key, value, group ->
                viewModel.saveSpec(key, value, group)
            },
            onDelete = if (!isNew && editingSpec != null) {
                { viewModel.requestDeleteSpec(it) }
            } else null
        )
    }

    // Safeguard: Spec Deletion Confirmation Dialog
    if (uiState.confirmDeleteSpec != null) {
        val target = uiState.confirmDeleteSpec!!
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteSpec() },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = TagIssueColor, modifier = Modifier.size(36.dp))
            },
            title = {
                Text("Delete Specification?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "⚠️ Warning: Are you sure you want to delete '${target.fieldKey}' from ${target.sapId}?\n\nThis specification will be removed from technical specifications and omitted from subsequent CSV exports.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteSpec() },
                    colors = ButtonDefaults.buttonColors(containerColor = TagIssueColor)
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteSpec() }) {
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
}

@Composable
private fun SiteHeaderBanner(
    site: SiteEntity
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
                    Text(
                        text = site.sapId,
                        style = SapIdStyle.copy(
                            fontSize = 20.sp,
                            color = TelecomAmber
                        )
                    )

                    if (!site.city.isNullOrBlank() || !site.district.isNullOrBlank()) {
                        StatusDotBadge(
                            text = listOfNotNull(site.city, site.district).joinToString(" • "),
                            color = RFCyan
                        )
                    }
                }

                if (!site.name.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = site.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (!site.address.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = site.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }

                if (site.lat != null && site.lng != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "GPS: ${site.lat.format(5)}, ${site.lng.format(5)}",
                        style = MetricMonoStyle.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun SiteLogsTab(
    logs: List<IncidentEntity>,
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

                    EnterpriseListRow(onClick = { onEditLogClick(log.id) }) {
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
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun SpecsTab(
    groupedSpecs: Map<String, List<SiteFieldEntity>>,
    filterQuery: String,
    onFilterChange: (String) -> Unit,
    onAddSpecClick: () -> Unit,
    onEditSpecClick: (SiteFieldEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchCapsule(
                query = filterQuery,
                onQueryChange = onFilterChange,
                placeholder = "Filter specifications...",
                modifier = Modifier.weight(1f)
            )

            OutlinedButton(
                onClick = onAddSpecClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Spec", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
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
                filteredGroups.forEach { (groupName, fields) ->
                    item(key = groupName) {
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

                    items(fields, key = { "${it.groupName}_${it.fieldKey}" }) { field ->
                        EnterpriseSpecRow(
                            field = field,
                            onEdit = { onEditSpecClick(field) }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun EnterpriseSpecRow(
    field: SiteFieldEntity,
    onEdit: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
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
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit ${field.fieldKey}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
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
private fun SpecEditDialog(
    spec: SiteFieldEntity?,
    isNew: Boolean,
    sapId: String,
    onDismiss: () -> Unit,
    onSave: (key: String, value: String, group: String) -> Unit,
    onDelete: ((SiteFieldEntity) -> Unit)?
) {
    var fieldKey by remember { mutableStateOf(spec?.fieldKey ?: "") }
    var fieldValue by remember { mutableStateOf(spec?.fieldValue ?: "") }
    var groupName by remember { mutableStateOf(spec?.groupName ?: "GENERAL SPECIFICATIONS") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNew) "Add Specification" else "Edit Specification",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ Updating this specification will modify the master database for $sapId and will be included in all future CSV exports.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                OutlinedTextField(
                    value = fieldKey,
                    onValueChange = { fieldKey = it },
                    label = { Text("Field Name (e.g. SMPS CAPACITY)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = isNew
                )

                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { fieldValue = it },
                    label = { Text("Field Value (e.g. 48V 100A)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group / Category (e.g. POWER, BATTERY, RF)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(fieldKey, fieldValue, groupName) },
                colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber),
                enabled = fieldKey.isNotBlank()
            ) {
                Text("Save Specification", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        },
        dismissButton = {
            Row {
                if (!isNew && spec != null && onDelete != null) {
                    TextButton(onClick = { onDelete(spec) }) {
                        Text("Delete", color = TagIssueColor, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun OverviewTab(
    site: SiteEntity,
    isEditing: Boolean,
    draft: SiteEntity,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDraftChange: ((SiteEntity) -> SiteEntity) -> Unit,
    onSaveClick: () -> Unit
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
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Site Overview Protected",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Locked against accidental edits",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onStartEdit,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Details", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            OverviewRow("Site Name", site.name ?: "—")
            OverviewRow("Circle", site.circle ?: "—")
            OverviewRow("Region", site.region ?: "—")
            OverviewRow("District", site.district ?: "—")
            OverviewRow("City / JC", site.city ?: "—")
            OverviewRow("State", site.state ?: "—")
            OverviewRow("Technology", site.technology ?: "—")
            OverviewRow("Site Type", site.siteType ?: "—")
            OverviewRow("Address", site.address ?: "—")
            if (site.lat != null && site.lng != null) {
                OverviewRow("GPS Coordinates", "${site.lat.format(5)}, ${site.lng.format(5)}")
            }
            OverviewRow("Tags / Scope", site.tags ?: "—")
            OverviewRow("Source File", site.sourceFile ?: "—")
            if (site.updatedAt != null) {
                OverviewRow("Last Updated", Dates.formatDateTime(site.updatedAt))
            }
            if (!site.notes.isNullOrBlank()) {
                OverviewRow("Remarks", site.notes)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun OverviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(130.dp)
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
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
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

private fun Double.format(digits: Int): String = String.format(Locale.US, "%.${digits}f", this)
