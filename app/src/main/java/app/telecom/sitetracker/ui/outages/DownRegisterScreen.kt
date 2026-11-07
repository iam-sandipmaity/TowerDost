package app.telecom.sitetracker.ui.outages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import app.telecom.sitetracker.R
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.ReasonEntity
import app.telecom.sitetracker.ui.components.EnterpriseListRow
import app.telecom.sitetracker.ui.components.EnterpriseSegmentedControl
import app.telecom.sitetracker.ui.components.EnterpriseTopBar
import app.telecom.sitetracker.ui.components.MetricTile
import app.telecom.sitetracker.ui.components.SearchCapsule
import app.telecom.sitetracker.ui.components.StatusDotBadge
import app.telecom.sitetracker.ui.theme.SapIdStyle
import app.telecom.sitetracker.ui.theme.SeverityCritical
import app.telecom.sitetracker.ui.theme.SeverityMajor
import app.telecom.sitetracker.ui.theme.SeverityMinor
import app.telecom.sitetracker.ui.theme.StatusDownRed
import app.telecom.sitetracker.ui.theme.StatusRestoredGreen
import app.telecom.sitetracker.ui.theme.TealPrimary
import app.telecom.sitetracker.ui.theme.TelecomAmber
import app.telecom.sitetracker.util.Dates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownRegisterScreen(
    viewModel: DownRegisterViewModel,
    onSiteClick: (String) -> Unit,
    onLogOutageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showMenu by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var editingReasonIncidentId by remember { mutableStateOf<String?>(null) }
    var editingReasonCurrentId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            val hasUndo = uiState.undoableIncidentId != null
            val actionLabel = if (hasUndo) "UNDO" else null
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = actionLabel,
                duration = if (hasUndo) SnackbarDuration.Short else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoRestore()
            }
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            EnterpriseTopBar(
                title = stringResource(R.string.title_down_register),
                subtitle = "${uiState.openOutages.size} active outages",
                actions = {
                    // Multi-select toggle
                    IconButton(onClick = { viewModel.toggleMultiSelectMode() }) {
                        Icon(
                            imageVector = if (uiState.isMultiSelectMode) Icons.Default.Close else Icons.Default.DoneAll,
                            contentDescription = "Bulk Selection",
                            tint = if (uiState.isMultiSelectMode) SeverityCritical else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Export Button
                    IconButton(onClick = { viewModel.exportRegister(context) }) {
                        Icon(Icons.Default.Share, contentDescription = "Export Register")
                    }

                    // Overflow Menu
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export Register (.csv)") },
                            onClick = {
                                showMenu = false
                                viewModel.exportRegister(context)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (uiState.isMultiSelectMode) "Exit Multi-Select" else "Select Multiple Sites") },
                            onClick = {
                                showMenu = false
                                viewModel.toggleMultiSelectMode()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove Sample / Dummy Data") },
                            onClick = {
                                showMenu = false
                                viewModel.clearSampleDummyData()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear All Register Records") },
                            onClick = {
                                showMenu = false
                                showClearConfirmDialog = true
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!uiState.isMultiSelectMode) {
                FloatingActionButton(
                    onClick = onLogOutageClick,
                    containerColor = TelecomAmber,
                    contentColor = Color(0xFF0F172A)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = "Log Outage")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Outage", fontWeight = FontWeight.Bold)
                    }
                }
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
            // Search Input
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                SearchCapsule(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = "Search by SAP ID, Title, Reason..."
                )
            }

            // Enterprise Segmented Control (Pill-slider tabs)
            EnterpriseSegmentedControl(
                options = listOf(
                    "Down (${uiState.openOutages.size})",
                    "Day View",
                    "All (${uiState.allIncidents.size})",
                    "Restored"
                ),
                selectedIndex = uiState.selectedTab,
                onSelect = { viewModel.onTabSelected(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Multi-Select Action Bar
            if (uiState.isMultiSelectMode && uiState.selectedTab == 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${uiState.selectedIncidentIds.size} selected",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (uiState.selectedIncidentIds.size == uiState.openOutages.size) {
                                        viewModel.clearSelection()
                                    } else {
                                        viewModel.selectAll(uiState.openOutages.map { it.id })
                                    }
                                }
                            ) {
                                Text(if (uiState.selectedIncidentIds.size == uiState.openOutages.size) "Deselect" else "Select All")
                            }

                            Button(
                                onClick = { viewModel.restoreSelected() },
                                enabled = uiState.selectedIncidentIds.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusRestoredGreen)
                            ) {
                                Text("Restore Selected", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Tab 1: Day View Navigation & Content
            if (uiState.selectedTab == 1) {
                DayViewContent(
                    dateMs = uiState.selectedDateEpochMs,
                    sitesDownCount = uiState.daySitesDownCount,
                    totalDowntimeHours = uiState.dayTotalDowntimeHours,
                    items = uiState.dayOutages,
                    onPreviousDay = { viewModel.onPreviousDay() },
                    onNextDay = { viewModel.onNextDay() },
                    onOpenDatePicker = { showDatePickerDialog = true },
                    onSiteClick = onSiteClick,
                    onRestore = { viewModel.restoreSite(it) },
                    onChangeReason = { incId, currReason ->
                        editingReasonIncidentId = incId
                        editingReasonCurrentId = currReason
                    }
                )
            } else {
                // Tab 0 (Down Now), Tab 2 (All Incidents), Tab 3 (Restored)
                val currentList = when (uiState.selectedTab) {
                    0 -> uiState.openOutages
                    2 -> uiState.allIncidents
                    else -> uiState.restoredOutages
                }

                if (currentList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (uiState.selectedTab) {
                                0 -> if (uiState.searchQuery.isNotBlank()) "No open outages match your search." else "All sites are operational! No active outages recorded."
                                2 -> if (uiState.searchQuery.isNotBlank()) "No incidents match your search." else "No incidents recorded. Tap '+ Log Outage' below to record one."
                                else -> "No restored outages recorded."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 72.dp)
                    ) {
                        items(currentList, key = { it.id }) { outage ->
                            val isSelected = uiState.selectedIncidentIds.contains(outage.id)
                            OutageRegisterCard(
                                outage = outage,
                                isMultiSelect = uiState.isMultiSelectMode && uiState.selectedTab == 0,
                                isSelected = isSelected,
                                onToggleSelect = { viewModel.toggleIncidentSelection(outage.id) },
                                onSiteClick = { onSiteClick(outage.sapId) },
                                onRestore = { viewModel.restoreSite(outage) },
                                onChangeReason = {
                                    editingReasonIncidentId = outage.id
                                    editingReasonCurrentId = outage.reasonId
                                }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(72.dp)) } // FAB clearance
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Clearing All Register Data
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear All Register Records?") },
            text = { Text("This will remove all logged incidents and outages. Master site specs and equipment data will not be affected. You will have a clean, fresh register.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        viewModel.clearAllRegisterData()
                    }
                ) {
                    Text("Clear All", color = SeverityCritical, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Jump to Date Dialog for Day View
    if (showDatePickerDialog) {
        JumpToDateDialog(
            currentDateMs = uiState.selectedDateEpochMs,
            onConfirm = {
                viewModel.onSelectDate(it)
                showDatePickerDialog = false
            },
            onDismiss = { showDatePickerDialog = false }
        )
    }

    // Root Cause Reason Edit Dialog
    if (editingReasonIncidentId != null) {
        val incidentId = editingReasonIncidentId!!
        ReasonSelectionDialog(
            reasons = uiState.activeReasons,
            currentReasonId = editingReasonCurrentId,
            onConfirm = { newReasonId ->
                viewModel.updateIncidentReason(incidentId, newReasonId)
                editingReasonIncidentId = null
                editingReasonCurrentId = null
            },
            onDismiss = {
                editingReasonIncidentId = null
                editingReasonCurrentId = null
            }
        )
    }
}

@Composable
private fun DayViewContent(
    dateMs: Long,
    sitesDownCount: Int,
    totalDowntimeHours: Double,
    items: List<DayViewOutageItem>,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onSiteClick: (String) -> Unit,
    onRestore: (IncidentEntity) -> Unit,
    onChangeReason: (incidentId: String, currentReasonId: String?) -> Unit
) {
    val dateStr = remember(dateMs) { Dates.formatDate(dateMs) }
    val isToday = remember(dateMs) {
        Dates.getDayStartEpochMs(System.currentTimeMillis()) == dateMs
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Date Navigation Strip
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onPreviousDay, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Day")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onOpenDatePicker() }
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp), tint = TelecomAmber)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isToday) "Today ($dateStr)" else dateStr,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                IconButton(onClick = onNextDay, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Day")
                }
            }
        }

        // Daily Summary KPI Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                label = "Sites Down",
                value = "$sitesDownCount",
                valueColor = if (sitesDownCount > 0) StatusDownRed else StatusRestoredGreen,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Daily Loss",
                value = Dates.formatHours(totalDowntimeHours),
                valueColor = if (totalDowntimeHours > 0) StatusDownRed else StatusRestoredGreen,
                modifier = Modifier.weight(1f)
            )
            val statusText = if (sitesDownCount == 0) "100% SLA" else "${items.size} Outage${if (items.size > 1) "s" else ""}"
            MetricTile(
                label = "Day Status",
                value = statusText,
                valueColor = if (sitesDownCount == 0) StatusRestoredGreen else TelecomAmber,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Outages for Selected Date
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No outages active on $dateStr.\nAll sites were operational on this date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 72.dp)
            ) {
                items(items, key = { it.incident.id }) { item ->
                    DayOutageCard(
                        item = item,
                        onSiteClick = { onSiteClick(item.incident.sapId) },
                        onRestore = { onRestore(item.incident) },
                        onChangeReason = { onChangeReason(item.incident.id, item.incident.reasonId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayOutageCard(
    item: DayViewOutageItem,
    onSiteClick: () -> Unit,
    onRestore: () -> Unit,
    onChangeReason: () -> Unit
) {
    val outage = item.incident
    val isStillDown = outage.restoredAt == null && outage.status != "Resolved" && outage.status != "Closed"

    EnterpriseListRow(onClick = onSiteClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = outage.sapId,
                style = SapIdStyle.copy(fontSize = 16.sp, color = TelecomAmber)
            )
            StatusDotBadge(
                text = if (isStillDown) "STILL DOWN" else "RESTORED",
                color = if (isStillDown) StatusDownRed else StatusRestoredGreen
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = outage.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onChangeReason() }
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Icon(Icons.Default.Edit, contentDescription = "Edit Reason", modifier = Modifier.size(12.dp), tint = TelecomAmber)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = item.reasonLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Down: ${Dates.formatDateTime(outage.downAt ?: outage.occurredAt)}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${Dates.formatHours(item.clippedHours)} on date",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isStillDown) StatusDownRed else StatusRestoredGreen
            )
        }

        if (isStillDown) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onRestore,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StatusRestoredGreen),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("1-Tap Restore Site", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun OutageRegisterCard(
    outage: IncidentEntity,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onSiteClick: () -> Unit,
    onRestore: () -> Unit,
    onChangeReason: () -> Unit
) {
    val isOutage = outage.isOutage == 1
    val isStillDown = isOutage && outage.restoredAt == null && outage.status != "Resolved" && outage.status != "Closed"
    val now = System.currentTimeMillis()

    val runningHours = if (outage.downAt != null) {
        val endTime = outage.restoredAt ?: now
        if (endTime >= outage.downAt) (endTime - outage.downAt) / 3600000.0 else 0.0
    } else 0.0

    val daysDown = if (runningHours > 0) (runningHours / 24.0).toInt() + 1 else 1

    EnterpriseListRow(
        onClick = { if (isMultiSelect) onToggleSelect() else onSiteClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            if (isMultiSelect) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = outage.sapId,
                        style = SapIdStyle.copy(fontSize = 16.sp, color = TelecomAmber)
                    )

                    if (isStillDown) {
                        StatusDotBadge(
                            text = if (daysDown > 1) "DOWN • DAY $daysDown" else "DOWN • ${Dates.formatHours(runningHours)}",
                            color = StatusDownRed
                        )
                    } else if (isOutage) {
                        StatusDotBadge(
                            text = "RESTORED (${Dates.formatHours(runningHours)})",
                            color = StatusRestoredGreen
                        )
                    } else {
                        StatusDotBadge(
                            text = outage.status.uppercase(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = outage.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!outage.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = outage.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }

                if (!outage.actionTaken.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Action: ${outage.actionTaken}",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusRestoredGreen,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.clickable { onChangeReason() }
                        ) {
                            Text(
                                text = "${outage.category} ✎",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        val sevColor = when (outage.severity) {
                            "Critical" -> StatusDownRed
                            "Major" -> SeverityMajor
                            "Minor" -> SeverityMinor
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            color = sevColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, sevColor.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = outage.severity,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = sevColor),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = Dates.formatDateTime(outage.downAt ?: outage.occurredAt),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isStillDown && !isMultiSelect) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRestore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusRestoredGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("1-Tap Restore Site Now", fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun JumpToDateDialog(
    currentDateMs: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var dateInput by remember { mutableStateOf(Dates.formatDate(currentDateMs)) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to Date", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter date in dd/MM/yyyy format:", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dateInput,
                    onValueChange = { dateInput = it; error = null },
                    placeholder = { Text("e.g. 07/11/2026") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(error!!, color = SeverityCritical, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = Dates.parseDateTime(dateInput, "00:00")
                    if (parsed != null) {
                        onConfirm(parsed)
                    } else {
                        error = "Invalid date format. Use dd/MM/yyyy"
                    }
                }
            ) {
                Text("Jump", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ReasonSelectionDialog(
    reasons: List<ReasonEntity>,
    currentReasonId: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedId by remember { mutableStateOf(currentReasonId ?: reasons.firstOrNull()?.id ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Root Cause Reason", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(reasons, key = { it.id }) { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedId = reason.id }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedId == reason.id,
                            onClick = { selectedId = reason.id }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(reason.label, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${reason.category ?: "Other"} • ${if (reason.countsTowardDowntime == 1) "Downtime SLA" else "Excluded"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedId) },
                enabled = selectedId.isNotBlank()
            ) {
                Text("Update Reason", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
