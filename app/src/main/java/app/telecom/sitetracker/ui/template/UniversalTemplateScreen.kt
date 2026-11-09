package app.telecom.sitetracker.ui.template

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.telecom.sitetracker.data.entity.SiteTabEntity
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import app.telecom.sitetracker.ui.components.EnterpriseTopBar
import app.telecom.sitetracker.ui.components.SearchCapsule
import app.telecom.sitetracker.ui.theme.MetricMonoStyle
import app.telecom.sitetracker.ui.theme.RFCyan
import app.telecom.sitetracker.ui.theme.StatusDownRed
import app.telecom.sitetracker.ui.theme.TagIssueColor
import app.telecom.sitetracker.ui.theme.TelecomAmber
import java.util.Locale

@Composable
fun UniversalTemplateScreen(
    viewModel: UniversalTemplateViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddTabDialog by remember { mutableStateOf(false) }

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
                title = "Universal Template",
                subtitle = "Global equipment blueprint for all sites",
                onBack = onNavigateBack,
                actions = {
                    if (uiState.groupedFields.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.toggleReorderMode() },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = if (uiState.isReorderMode) "Done Reordering" else "Reorder Specs & Categories",
                                tint = if (uiState.isReorderMode) TelecomAmber else RFCyan
                            )
                        }
                    }
                    if (uiState.siteSpecsAvailableCount > 0) {
                        IconButton(
                            onClick = { viewModel.requestSyncFromSites() },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync from Sites",
                                tint = RFCyan
                            )
                        }
                    }
                    if (uiState.groupedFields.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.requestClearAll() },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All Blueprint Specs",
                                tint = StatusDownRed.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        if (uiState.isLoading && uiState.groupedFields.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TelecomAmber)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // High-Contrast Horizontal Tab Selector
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val availableTabs = if (uiState.tabs.isNotEmpty()) uiState.tabs else listOf(SiteTabEntity.SYSTEM_SPECS)
                        items(availableTabs, key = { it.tabId }) { tab ->
                            val isSelected = tab.tabId.equals(uiState.selectedTabId, ignoreCase = true)
                            Surface(
                                onClick = { viewModel.selectTab(tab.tabId) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) TelecomAmber.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) TelecomAmber else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .sizeIn(minHeight = 48.dp)
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(TelecomAmber)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = tab.displayName.ifBlank { tab.tabId },
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (isSelected) TelecomAmber else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }

                        // Add Tab Button
                        item(key = "add_universal_tab_action") {
                            Surface(
                                onClick = { showAddTabDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                color = RFCyan.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, RFCyan.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add New Universal Tab",
                                        tint = RFCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Tab",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = RFCyan
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Header Info Strip
                val currentTab = uiState.tabs.find { it.tabId.equals(uiState.selectedTabId, ignoreCase = true) }
                val currentTabName = currentTab?.displayName?.ifBlank { currentTab.tabId } ?: if (uiState.selectedTabId == "SPECS") "Specifications" else uiState.selectedTabId
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
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
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = TelecomAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "$currentTabName (${uiState.totalFieldCount} fields in ${uiState.groupedFields.size} categories)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Global blueprint inherited across all telecom sites",
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
                                onClick = { viewModel.startAddSection() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Category", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::onSearchQueryChange,
                        placeholder = "Filter specifications...",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (uiState.isReorderMode) {
                    Surface(
                        color = TelecomAmber.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TelecomAmber.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.SwapVert, contentDescription = null, tint = TelecomAmber, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Reorder Mode: Tap arrows or Move to position specs globally",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    fontSize = 11.sp,
                                    color = TelecomAmber
                                )
                            }
                            TextButton(
                                onClick = { viewModel.toggleReorderMode() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Done", fontWeight = FontWeight.Bold, color = TelecomAmber, fontSize = 12.sp)
                            }
                        }
                    }
                }

                val filteredGroups = remember(uiState.groupedFields, uiState.searchQuery) {
                    if (uiState.searchQuery.isBlank()) uiState.groupedFields
                    else {
                        val q = uiState.searchQuery.trim().lowercase(Locale.US)
                        uiState.groupedFields.mapValues { (_, fields) ->
                            fields.filter {
                                it.fieldKey.lowercase(Locale.US).contains(q) ||
                                        (it.defaultValue?.lowercase(Locale.US)?.contains(q) == true)
                            }
                        }.filterValues { it.isNotEmpty() }
                    }
                }

                if (filteredGroups.isEmpty()) {
                    if (uiState.searchQuery.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No specifications match \"${uiState.searchQuery}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Clean empty template state - zero dummy data
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = TelecomAmber,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Text(
                                        text = if (uiState.selectedTabId == "SPECS") "Universal Blueprint Empty" else "$currentTabName Blueprint Empty",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (uiState.selectedTabId != "SPECS") {
                                            "No equipment categories defined for $currentTabName yet. Tap 'Add Category' above to add sections and specifications to this tab."
                                        } else if (uiState.siteSpecsAvailableCount > 0) {
                                            "Found ${uiState.siteSpecsAvailableCount} equipment specifications in your imported site records. You can sync them now into the global template or define custom categories."
                                        } else {
                                            "No equipment specifications defined yet. When you import telecom site records, the blueprint will automatically reflect their columns, or you can add custom categories below."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (uiState.siteSpecsAvailableCount > 0) {
                                            Button(
                                                onClick = { viewModel.requestSyncFromSites() },
                                                colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.sizeIn(minHeight = 44.dp)
                                            ) {
                                                Icon(Icons.Default.Sync, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Sync From Sites (${uiState.siteSpecsAvailableCount})",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color.Black
                                                )
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = { viewModel.startAddSection() },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.sizeIn(minHeight = 44.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Add Category", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        filteredGroups.entries.forEachIndexed { groupIndex, (groupName, fields) ->
                            item(key = "header_${groupIndex}_$groupName") {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = groupName.uppercase(Locale.US),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = RFCyan,
                                            letterSpacing = 0.5.sp
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            IconButton(
                                                onClick = { viewModel.moveSectionUp(groupName) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowUp,
                                                    contentDescription = "Move category $groupName Up",
                                                    tint = TelecomAmber,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.moveSectionDown(groupName) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Move category $groupName Down",
                                                    tint = TelecomAmber,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surface
                                            ) {
                                                Text(
                                                    text = "${fields.size}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.startAddField(groupName) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add specification to $groupName",
                                                    tint = RFCyan,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.requestDeleteSection(groupName) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete category $groupName",
                                                    tint = TagIssueColor.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            itemsIndexed(
                                items = fields,
                                key = { index, field -> "template_spec_${groupIndex}_${field.fieldKey}_$index" }
                            ) { index, field ->
                                EnterpriseTemplateSpecRow(
                                    field = field,
                                    positionIndex = index,
                                    isReorderMode = uiState.isReorderMode,
                                    onMoveUp = { viewModel.moveFieldUp(field) },
                                    onMoveDown = { viewModel.moveFieldDown(field) },
                                    onMoveMenu = { viewModel.startMoveField(field) },
                                    onEdit = { viewModel.startEditField(field) },
                                    onDelete = { viewModel.requestDeleteField(field) }
                                )
                            }
                        }

                        item(key = "bottom_spacer") {
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    if (showAddTabDialog) {
        AddUniversalTabDialog(
            onDismiss = { showAddTabDialog = false },
            onConfirm = { name ->
                viewModel.createNewTab(name)
                showAddTabDialog = false
            }
        )
    }

    if (uiState.isAddingField && uiState.targetSectionForAdd != null) {
        AddFieldDialog(
            sectionName = uiState.targetSectionForAdd!!,
            onDismiss = { viewModel.dismissAddField() },
            onConfirm = { key, defaultVal -> viewModel.confirmAddField(key, defaultVal) }
        )
    }

    if (uiState.isAddingSection) {
        AddSectionDialog(
            onDismiss = { viewModel.dismissAddSection() },
            onConfirm = { sectionName, key, defaultVal ->
                viewModel.confirmAddSection(sectionName, key, defaultVal)
            }
        )
    }

    if (uiState.isEditingField && uiState.fieldToEdit != null) {
        EditFieldDialog(
            field = uiState.fieldToEdit!!,
            onDismiss = { viewModel.dismissEditField() },
            onConfirm = { oldKey, newKey, newGroup, defaultVal ->
                viewModel.confirmEditField(oldKey, newKey, newGroup, defaultVal)
            }
        )
    }

    if (uiState.confirmClearAll) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelClearAll() },
            title = { Text("Clear All Blueprint Specs?") },
            text = {
                Text("Are you sure you want to remove all universal specification fields? Sites will display only their imported spreadsheet data without any placeholder specs.\n\nYour existing site records are safe and will not be deleted.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmClearAll() },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDownRed)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelClearAll() }) {
                    Text("Cancel")
                }
            }
        )
    }

    uiState.confirmDeleteField?.let { field ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteField() },
            title = { Text("Delete Universal Spec?") },
            text = {
                Text("Are you sure you want to remove '${field.fieldKey}' from the universal template? Existing site records will not be deleted, but future sites will not inherit this field.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteField() },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDownRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteField() }) {
                    Text("Cancel")
                }
            }
        )
    }

    uiState.confirmDeleteSection?.let { section ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteSection() },
            title = { Text("Delete Category?") },
            text = {
                Text("Are you sure you want to delete category '$section' and all its universal specification fields?")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteSection() },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDownRed)
                ) {
                    Text("Delete Category")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteSection() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.confirmSyncFromSites) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelSyncFromSites() },
            title = { Text("Sync Blueprint from Sites?") },
            text = {
                Text(
                    "This will scan your imported telecom sites and populate the universal blueprint with all distinct equipment columns (${uiState.siteSpecsAvailableCount} specifications found across your sites).\n\nExisting site records remain untouched."
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmSyncFromSites() },
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber)
                ) {
                    Text("Sync Specifications", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelSyncFromSites() }) {
                    Text("Cancel")
                }
            }
        )
    }

    uiState.fieldToMove?.let { field ->
        MoveFieldDialog(
            field = field,
            allSections = uiState.groupedFields.keys.toList(),
            onDismiss = { viewModel.dismissMoveField() },
            onMoveUp = { viewModel.moveFieldUp(field) },
            onMoveDown = { viewModel.moveFieldDown(field) },
            onMoveToTop = { viewModel.moveFieldToTop(field) },
            onMoveToBottom = { viewModel.moveFieldToBottom(field) },
            onMoveToSection = { targetSection -> viewModel.moveFieldToSection(field, targetSection) }
        )
    }
}

@Composable
private fun EnterpriseTemplateSpecRow(
    field: UniversalFieldEntity,
    positionIndex: Int,
    isReorderMode: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveMenu: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = if (isReorderMode) onMoveMenu else onEdit)
    ) {
        Column {
            if (isReorderMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Position Handle",
                            tint = RFCyan.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = RFCyan.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, RFCyan.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "#${positionIndex + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = RFCyan
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = field.fieldKey,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = onMoveUp,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Move Up",
                                tint = TelecomAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onMoveDown,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Move Down",
                                tint = TelecomAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onMoveMenu,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Position / Move",
                                tint = RFCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
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
                        text = field.defaultValue ?: "—",
                        style = MetricMonoStyle.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = onMoveMenu,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Reorder ${field.fieldKey}",
                                tint = TelecomAmber,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit ${field.fieldKey}",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete ${field.fieldKey}",
                                tint = TagIssueColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun MoveFieldDialog(
    field: UniversalFieldEntity,
    allSections: List<String>,
    onDismiss: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onMoveToSection: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Reorder & Position Specification")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = field.fieldKey,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = RFCyan
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Current Section: ${field.groupName.uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "REORDER POSITION",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = TelecomAmber
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onMoveToTop()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.VerticalAlignTop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Top", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            onMoveUp()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Up", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            onMoveDown()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Down", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            onMoveToBottom()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.VerticalAlignBottom, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bottom", fontSize = 12.sp)
                    }
                }

                val otherSections = allSections.filter { !it.equals(field.groupName, ignoreCase = true) }
                if (otherSections.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = "MOVE TO ANOTHER SECTION",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TelecomAmber
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(otherSections) { targetSec ->
                            Surface(
                                onClick = {
                                    onMoveToSection(targetSec)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = targetSec.uppercase(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Move here →",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RFCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun EditFieldDialog(
    field: UniversalFieldEntity,
    onDismiss: () -> Unit,
    onConfirm: (oldKey: String, newKey: String, newGroup: String, defaultValue: String?) -> Unit
) {
    var fieldKey by remember { mutableStateOf(field.fieldKey) }
    var groupName by remember { mutableStateOf(field.groupName) }
    var defaultValue by remember { mutableStateOf(field.defaultValue ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Specification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Category / Section *") },
                    placeholder = { Text("e.g. POWER, BATTERY, DG") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = fieldKey,
                    onValueChange = { fieldKey = it },
                    label = { Text("Specification Key *") },
                    placeholder = { Text("e.g. DG RATING KVA") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = defaultValue,
                    onValueChange = { defaultValue = it },
                    label = { Text("Default Value (Optional)") },
                    placeholder = { Text("Leave blank if site-dependent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(field.fieldKey, fieldKey, groupName, defaultValue) },
                enabled = fieldKey.isNotBlank() && groupName.isNotBlank()
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddFieldDialog(
    sectionName: String,
    onDismiss: () -> Unit,
    onConfirm: (fieldKey: String, defaultValue: String?) -> Unit
) {
    var fieldKey by remember { mutableStateOf("") }
    var defaultValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Specification to $sectionName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fieldKey,
                    onValueChange = { fieldKey = it },
                    label = { Text("Specification Key *") },
                    placeholder = { Text("e.g. DG RATING KVA") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = defaultValue,
                    onValueChange = { defaultValue = it },
                    label = { Text("Default Value (Optional)") },
                    placeholder = { Text("Leave blank if site-dependent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(fieldKey, defaultValue) },
                enabled = fieldKey.isNotBlank()
            ) {
                Text("Add Specification")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddSectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (sectionName: String, firstFieldKey: String, firstDefaultValue: String?) -> Unit
) {
    var sectionName by remember { mutableStateOf("") }
    var fieldKey by remember { mutableStateOf("") }
    var defaultValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Equipment Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = sectionName,
                    onValueChange = { sectionName = it },
                    label = { Text("Category Name *") },
                    placeholder = { Text("e.g. AIR CONDITIONING") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = fieldKey,
                    onValueChange = { fieldKey = it },
                    label = { Text("First Specification Key *") },
                    placeholder = { Text("e.g. AC 1 CAPACITY TON") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = defaultValue,
                    onValueChange = { defaultValue = it },
                    label = { Text("Default Value (Optional)") },
                    placeholder = { Text("Leave blank if site-dependent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(sectionName, fieldKey, defaultValue) },
                enabled = sectionName.isNotBlank() && fieldKey.isNotBlank()
            ) {
                Text("Create Category")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddUniversalTabDialog(
    onDismiss: () -> Unit,
    onConfirm: (tabName: String) -> Unit
) {
    var tabName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Create New Universal Tab",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Adds a dynamic tab to the universal blueprint. All sites will inherit this tab and any specification categories added to it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tabName,
                    onValueChange = { tabName = it },
                    label = { Text("Tab Display Name *") },
                    placeholder = { Text("e.g. Tenant Info, Solar Specs") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(tabName) },
                enabled = tabName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber),
                modifier = Modifier.sizeIn(minHeight = 44.dp)
            ) {
                Text("Create Tab", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.sizeIn(minHeight = 44.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
