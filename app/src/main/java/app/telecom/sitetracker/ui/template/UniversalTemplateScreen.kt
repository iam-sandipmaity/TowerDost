package app.telecom.sitetracker.ui.template

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import app.telecom.sitetracker.ui.components.EnterpriseTopBar
import app.telecom.sitetracker.ui.theme.StatusDownRed
import app.telecom.sitetracker.ui.theme.TelecomAmber

@Composable
fun UniversalTemplateScreen(
    viewModel: UniversalTemplateViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                title = "Universal Specs Template",
                subtitle = "Standard equipment specs inherited by all sites",
                onBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.requestResetDefaults() },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Defaults",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "template_info_banner") {
                    Spacer(modifier = Modifier.height(4.dp))
                    // Header explanatory card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TelecomAmber.copy(alpha = 0.15f))
                                    .border(1.dp, TelecomAmber, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = TelecomAmber)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Universal Equipment Blueprint",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Specifications defined here automatically appear across all telecom sites in View and Edit modes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item(key = "template_stats_row") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiState.groupedFields.size} CATEGORIES • ${uiState.totalFieldCount} GLOBAL SPECS",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = { viewModel.startAddSection() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.sizeIn(minHeight = 40.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Add Category", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Render each Section Card
                items(
                    items = uiState.groupedFields.entries.toList(),
                    key = { it.key }
                ) { (groupName, fields) ->
                    UniversalSectionCard(
                        groupName = groupName,
                        fields = fields,
                        onAddField = { viewModel.startAddField(groupName) },
                        onDeleteField = { viewModel.requestDeleteField(it) },
                        onDeleteSection = { viewModel.requestDeleteSection(groupName) }
                    )
                }

                item(key = "bottom_add_section_action") {
                    Button(
                        onClick = { viewModel.startAddSection() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .sizeIn(minHeight = 48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Another Equipment Category",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // --- Dialogs ---

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

    if (uiState.confirmResetDefaults) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelResetDefaults() },
            title = { Text("Reset to Telecom Defaults?") },
            text = {
                Text("This will restore the standard telecom blueprint (POWER, BATTERY, DG, TRANSMISSION, etc.). Custom fields you added may need to be re-entered.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmResetDefaults() },
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber)
                ) {
                    Text("Reset Defaults")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelResetDefaults() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun UniversalSectionCard(
    groupName: String,
    fields: List<UniversalFieldEntity>,
    onAddField: () -> Unit,
    onDeleteField: (UniversalFieldEntity) -> Unit,
    onDeleteSection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(TelecomAmber.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Category,
                            contentDescription = null,
                            tint = TelecomAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = groupName.uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
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
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onAddField,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.sizeIn(minHeight = 36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Field", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDeleteSection,
                        modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Section",
                            tint = StatusDownRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Specs List
            fields.forEachIndexed { index, field ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = field.fieldKey.uppercase(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val subText = if (!field.defaultValue.isNullOrBlank()) {
                            "Default: ${field.defaultValue}"
                        } else {
                            "Blank template (field input required)"
                        }
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { onDeleteField(field) },
                        modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Spec",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (index < fields.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
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
