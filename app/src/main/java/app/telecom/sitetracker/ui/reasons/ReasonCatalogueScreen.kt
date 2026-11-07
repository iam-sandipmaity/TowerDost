package app.telecom.sitetracker.ui.reasons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.telecom.sitetracker.R
import app.telecom.sitetracker.data.entity.ReasonEntity
import app.telecom.sitetracker.ui.theme.SeverityCritical
import app.telecom.sitetracker.ui.theme.StatusRestoredGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasonCatalogueScreen(
    viewModel: ReasonCatalogueViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_reason_catalogue)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Reason")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search root cause or category...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Reasons List
            if (uiState.reasons.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No reasons found matching \"${uiState.searchQuery}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(uiState.reasons, key = { it.id }) { reason ->
                        ReasonRowCard(
                            reason = reason,
                            onEdit = { viewModel.openEditDialog(reason) },
                            onToggleHidden = { viewModel.toggleReasonHidden(reason) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(88.dp)) } // FAB clearance
                }
            }
        }
    }

    // Add Reason Dialog
    if (uiState.isAddDialogOpen) {
        ReasonDialog(
            title = "Add Root Cause Reason",
            initialLabel = "",
            initialCategory = "Power",
            initialCountsTowardDowntime = true,
            onDismiss = { viewModel.closeAddDialog() },
            onSave = { label, category, downtime ->
                viewModel.saveReason(label, category, downtime)
            }
        )
    }

    // Edit Reason Dialog
    uiState.editingReason?.let { reason ->
        ReasonDialog(
            title = "Edit Root Cause Reason",
            initialLabel = reason.label,
            initialCategory = reason.category ?: "Power",
            initialCountsTowardDowntime = reason.countsTowardDowntime == 1,
            onDismiss = { viewModel.closeEditDialog() },
            onSave = { label, category, downtime ->
                viewModel.updateReason(reason, label, category, downtime)
            }
        )
    }
}

@Composable
private fun ReasonRowCard(
    reason: ReasonEntity,
    onEdit: () -> Unit,
    onToggleHidden: () -> Unit
) {
    val isHidden = reason.isHidden == 1
    val countsDowntime = reason.countsTowardDowntime == 1

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHidden) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reason.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = reason.category ?: "General",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Downtime counts badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (countsDowntime) SeverityCritical.copy(alpha = 0.15f) else StatusRestoredGreen.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (countsDowntime) "Counts as Downtime" else "Excluded from Downtime",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (countsDowntime) SeverityCritical else StatusRestoredGreen
                        )
                    }
                }
            }

            // Edit icon
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Reason", tint = MaterialTheme.colorScheme.primary)
            }

            // Hide/Show Switch
            Switch(
                checked = !isHidden,
                onCheckedChange = { onToggleHidden() }
            )
        }
    }
}

@Composable
private fun ReasonDialog(
    title: String,
    initialLabel: String,
    initialCategory: String,
    initialCountsTowardDowntime: Boolean,
    onDismiss: () -> Unit,
    onSave: (label: String, category: String, countsTowardDowntime: Boolean) -> Unit
) {
    var label by remember { mutableStateOf(initialLabel) }
    var category by remember { mutableStateOf(initialCategory) }
    var countsTowardDowntime by remember { mutableStateOf(initialCountsTowardDowntime) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Reason Description *") },
                    placeholder = { Text("e.g. DG fuel pipe blockage") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category / Subsystem") },
                    placeholder = { Text("Power, DG, Battery, Transmission, etc.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Counts Toward Downtime", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (countsTowardDowntime) "Included in site SLA downtime %" else "Excluded (e.g. planned maintenance)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = countsTowardDowntime,
                        onCheckedChange = { countsTowardDowntime = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (label.isNotBlank()) {
                        onSave(label, category, countsTowardDowntime)
                    }
                },
                enabled = label.isNotBlank()
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
