package app.telecom.sitetracker.ui.sitedetail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import app.telecom.sitetracker.R
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.ui.theme.BrandPrimary
import app.telecom.sitetracker.ui.theme.TagIssueBg
import app.telecom.sitetracker.ui.theme.TagIssueColor
import app.telecom.sitetracker.ui.theme.TagMaintBg
import app.telecom.sitetracker.ui.theme.TagMaintColor
import app.telecom.sitetracker.ui.theme.TagNoteBg
import app.telecom.sitetracker.ui.theme.TagNoteColor
import app.telecom.sitetracker.ui.theme.TagVisitBg
import app.telecom.sitetracker.ui.theme.TagVisitColor
import app.telecom.sitetracker.util.Dates
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
            TopAppBar(
                title = { Text(uiState.site?.sapId ?: stringResource(R.string.site_detail_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.exportSingleSiteReport(context)
                        }
                    ) {
                        Icon(Icons.Default.Description, contentDescription = "Export Site CSV")
                    }
                    IconButton(
                        onClick = {
                            val site = uiState.site ?: return@IconButton
                            shareSiteDetails(context, site, uiState.groupedSpecs)
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share specs")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
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
                CircularProgressIndicator()
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
                // Pinned Header Card
                SiteHeaderCard(
                    site = site,
                    contactPhone = uiState.contactPhone,
                    onNavigate = { lat, lng -> launchNavigation(context, lat, lng, site.sapId) },
                    onCall = { phone -> launchCall(context, phone) },
                    onAddLog = { onLogIncident(site.sapId) }
                )

                // Tabs: 0 = Specs, 1 = Site Logs, 2 = Overview
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            val specCount = uiState.groupedSpecs.values.sumOf { it.size }
                            Text("Specs ($specCount)", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text("Logs (${uiState.incidents.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text("Overview", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal)
                        }
                    )
                }

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
                Icon(Icons.Default.Edit, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(36.dp))
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
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Confirm & Save", fontWeight = FontWeight.Bold)
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
private fun SiteHeaderCard(
    site: SiteEntity,
    contactPhone: String?,
    onNavigate: (Double, Double) -> Unit,
    onCall: (String) -> Unit,
    onAddLog: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = site.sapId,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = BrandPrimary
                )

                if (!site.city.isNullOrBlank() || !site.district.isNullOrBlank()) {
                    Text(
                        text = listOfNotNull(site.city, site.district).joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!site.name.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = site.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!site.address.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
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
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row: Directions, Call, Add Log
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddLog,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Log", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (site.lat != null && site.lng != null) {
                    OutlinedButton(
                        onClick = { onNavigate(site.lat, site.lng) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Map", fontSize = 13.sp)
                    }
                }

                if (!contactPhone.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onCall(contactPhone) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Call", fontSize = 13.sp)
                    }
                }
            }
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Site Activity History (${logs.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            TextButton(onClick = onAddLogClick) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("+ New Log Entry", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onAddLogClick,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add First Log Entry", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs, key = { it.id }) { log ->
                    val cat = log.category.lowercase().trim()
                    val (badgeText, badgeColor, badgeBg) = when {
                        cat.contains("maint") || cat.contains("repair") -> Triple("MAINTENANCE", TagMaintColor, TagMaintBg)
                        cat.contains("visit") || cat.contains("site") || cat.contains("inspect") || cat.contains("audit") -> Triple("VISIT", TagVisitColor, TagVisitBg)
                        cat.contains("note") || cat.contains("general") -> Triple("NOTE", TagNoteColor, TagNoteBg)
                        cat.contains("issue") || log.isOutage == 1 -> Triple("ISSUE", TagIssueColor, TagIssueBg)
                        else -> Triple(log.category.uppercase().ifBlank { "NOTE" }, TagNoteColor, TagNoteBg)
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(badgeBg)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = badgeColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }

                                Text(
                                    text = Dates.formatDateTime(log.occurredAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            val text = log.description ?: log.title
                            if (text.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 20.sp
                                )
                            }

                            if (!log.actionTaken.isNullOrBlank() && log.actionTaken != text) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Action: ${log.actionTaken}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF15803D),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (!log.engineer.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = log.engineer,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { onEditLogClick(log.id) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                TextButton(
                                    onClick = { onDeleteLogClick(log) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = TagIssueColor)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TagIssueColor)
                                }
                            }
                        }
                    }
                }
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = onFilterChange,
                placeholder = { Text("Filter specs (e.g. SMPS, DG)...") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            OutlinedButton(
                onClick = onAddSpecClick,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Spec", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                filteredGroups.forEach { (groupName, fields) ->
                    item(key = groupName) {
                        SpecGroupCard(
                            groupName = groupName,
                            fields = fields,
                            onEditSpec = onEditSpecClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecGroupCard(
    groupName: String,
    fields: List<SiteFieldEntity>,
    onEditSpec: (SiteFieldEntity) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = groupName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = BrandPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            fields.forEachIndexed { index, field ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = field.fieldKey,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.2f)
                    )
                    Text(
                        text = field.fieldValue ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onEditSpec(field) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit ${field.fieldKey}",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (index < fields.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
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
                    enabled = isNew // Primary spec key locked if existing, or freely editable
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
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                enabled = fieldKey.isNotBlank()
            ) {
                Text("Save Specification", fontWeight = FontWeight.Bold)
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
        // Edit Mode with explicit safeguards
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = TagMaintBg),
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
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = CardDefaults.outlinedCardBorder(),
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
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Site Overview Protected",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
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
        }
    }
}

@Composable
private fun OverviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
