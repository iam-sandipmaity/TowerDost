package app.telecom.sitetracker.ui.merger

import android.net.Uri
import android.provider.OpenableColumns
import app.telecom.sitetracker.data.import.ColumnMapper
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.telecom.sitetracker.data.entity.SiteTabEntity
import app.telecom.sitetracker.data.import.TargetField
import app.telecom.sitetracker.data.merger.MergeStrategy
import app.telecom.sitetracker.data.merger.OverwritePolicy
import app.telecom.sitetracker.data.merger.VlookupColumnMapping
import app.telecom.sitetracker.ui.components.EnterpriseTopBar
import app.telecom.sitetracker.ui.theme.RFCyan
import app.telecom.sitetracker.ui.theme.StatusDownRed
import app.telecom.sitetracker.ui.theme.StatusRestoredGreen
import app.telecom.sitetracker.ui.theme.TelecomAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VlookupMergerScreen(
    viewModel: VlookupMergerViewModel,
    onNavigateBack: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "Supplemental Sheet"
            var fileSize = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }
            viewModel.onFileSelected(uri, fileName, fileSize)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            EnterpriseTopBar(
                title = "VLOOKUP Sheet Column Merger",
                subtitle = "Step ${uiState.step.stepNumber} of 6 • ${uiState.step.title}",
                onBack = {
                    when (uiState.step) {
                        VlookupStep.SELECT_FILE -> onNavigateBack()
                        VlookupStep.SELECT_KEY -> viewModel.goToStep(VlookupStep.SELECT_FILE)
                        VlookupStep.MAP_COLUMNS -> viewModel.goToStep(VlookupStep.SELECT_KEY)
                        VlookupStep.PREVIEW -> viewModel.goToStep(VlookupStep.MAP_COLUMNS)
                        VlookupStep.EXECUTING -> {} // Cannot navigate back during execution
                        VlookupStep.COMPLETE -> onComplete()
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Stepper indicator header
            StepperHeader(currentStep = uiState.step)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (uiState.step) {
                    VlookupStep.SELECT_FILE -> {
                        StepSelectFile(
                            uiState = uiState,
                            onPickFile = {
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "text/comma-separated-values",
                                        "text/csv",
                                        "application/csv",
                                        "*/*"
                                    )
                                )
                            },
                            onSelectSheet = { viewModel.onSheetSelected(it) },
                            onProceed = { viewModel.onProceedToMapping() }
                        )
                    }

                    VlookupStep.SELECT_KEY -> {
                        StepSelectKeyColumn(
                            uiState = uiState,
                            onSelectKey = { viewModel.onKeyColumnSelected(it) },
                            onProceed = { viewModel.onProceedToMapping() }
                        )
                    }

                    VlookupStep.MAP_COLUMNS -> {
                        StepMapColumns(
                            uiState = uiState,
                            onUpdateTarget = { col, target, group, key, tabId, tabDisplayName ->
                                viewModel.updateColumnTarget(col, target, group, key, tabId, tabDisplayName)
                            },
                            onCreateNewTab = { col, tabName, sectionName ->
                                viewModel.createNewTabAndMap(col, tabName, sectionName)
                            },
                            onToggleColumn = { col, isIncluded ->
                                viewModel.toggleColumnSelection(col, isIncluded)
                            },
                            onSelectAll = { viewModel.selectAllColumns() },
                            onDeselectAll = { viewModel.deselectAllColumns() },
                            onSetStrategy = { viewModel.setMergeStrategy(it) },
                            onSetOverwritePolicy = { viewModel.setOverwritePolicy(it) },
                            onGeneratePreview = { viewModel.generatePreview() }
                        )
                    }

                    VlookupStep.PREVIEW -> {
                        StepPreview(
                            uiState = uiState,
                            onExecuteMerge = { viewModel.executeMerge() },
                            onBackToMapping = { viewModel.goToStep(VlookupStep.MAP_COLUMNS) }
                        )
                    }

                    VlookupStep.EXECUTING -> {
                        StepExecuting(uiState = uiState)
                    }

                    VlookupStep.COMPLETE -> {
                        StepComplete(
                            result = uiState.result,
                            onDone = onComplete
                        )
                    }
                }

                if (uiState.isProcessing && uiState.step != VlookupStep.EXECUTING) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, TelecomAmber),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(color = TelecomAmber)
                                    Text("Analyzing Spreadsheet...", fontWeight = FontWeight.Bold)
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
private fun StepperHeader(currentStep: VlookupStep) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val steps = listOf(
                VlookupStep.SELECT_FILE,
                VlookupStep.SELECT_KEY,
                VlookupStep.MAP_COLUMNS,
                VlookupStep.PREVIEW
            )

            steps.forEachIndexed { index, step ->
                val isActive = currentStep == step
                val isDone = currentStep.stepNumber > step.stepNumber

                val badgeColor = when {
                    isDone -> StatusRestoredGreen
                    isActive -> TelecomAmber
                    else -> MaterialTheme.colorScheme.outlineVariant
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = if (isActive || isDone) 1f else 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Text(
                                text = "${step.stepNumber}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) Color(0xFF0F172A) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) TelecomAmber else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                if (index < steps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepSelectFile(
    uiState: VlookupMergerUiState,
    onPickFile: () -> Unit,
    onSelectSheet: (app.telecom.sitetracker.data.import.XlsxReader.SheetInfo) -> Unit,
    onProceed: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.UploadFile,
                    contentDescription = null,
                    tint = TelecomAmber,
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = "Select Supplemental Spreadsheet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Ingest an Excel (.xlsx) or CSV file containing site attributes or equipment specs to merge into existing sites.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onPickFile,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TelecomAmber,
                        contentColor = Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick File (.xlsx / .csv)", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (uiState.selectedFileName != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, RFCyan.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = uiState.selectedFileName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${uiState.selectedFileSize / 1024} KB • ${uiState.detectedFormat}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusRestoredGreen)
                    }

                    if (uiState.sheets.size > 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Select Worksheet:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.sheets.forEach { sheet ->
                                val isSelected = uiState.selectedSheet?.name == sheet.name
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) TelecomAmber else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { onSelectSheet(sheet) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = sheet.name,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color(0xFF0F172A) else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
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
private fun StepSelectKeyColumn(
    uiState: VlookupMergerUiState,
    onSelectKey: (Int) -> Unit,
    onProceed: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "MATCH KEY COLUMN",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = TelecomAmber
                )
                Text(
                    text = "Select which column in your sheet holds the SAP ID or Site Code. TowerDost will match each row to master sites using this key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = uiState.headers.mapIndexed { idx, h -> idx to h },
                key = { it.first }
            ) { (idx, header) ->
                val isSelected = uiState.keyColumnIndex == idx
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) TelecomAmber.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) TelecomAmber else MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectKey(idx) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectKey(idx) },
                            colors = RadioButtonDefaults.colors(selectedColor = TelecomAmber)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = header,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            val samples = uiState.sampleRows.mapNotNull { it.getOrNull(idx) }.filter { it.isNotBlank() }.take(3)
                            if (samples.isNotEmpty()) {
                                Text(
                                    text = "Sample: ${samples.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall.copy(
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

        Button(
            onClick = onProceed,
            enabled = uiState.keyColumnIndex >= 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = TelecomAmber,
                contentColor = Color(0xFF0F172A)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Proceed to Column Mapping", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun StepMapColumns(
    uiState: VlookupMergerUiState,
    onUpdateTarget: (Int, TargetField, String?, String?, String, String) -> Unit,
    onCreateNewTab: (Int, String, String?) -> Unit,
    onToggleColumn: (Int, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onSetStrategy: (MergeStrategy) -> Unit,
    onSetOverwritePolicy: (OverwritePolicy) -> Unit,
    onGeneratePreview: () -> Unit
) {
    val nonKeyMappings = uiState.columnMappings.filter { it.columnIndex != uiState.keyColumnIndex }
    val totalNonKey = nonKeyMappings.size
    val includedCount = nonKeyMappings.count { it.targetField != TargetField.IGNORE }
    val skippedCount = totalNonKey - includedCount

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Strategy & Overwrite Policy Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "MERGE POLICIES",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TelecomAmber
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Strategy
                Text(
                    text = "Unmatched Row Handling:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MergeStrategy.values().forEach { strat ->
                        val isSelected = uiState.mergeStrategy == strat
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) TelecomAmber.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSelected) TelecomAmber else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSetStrategy(strat) }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strat.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) TelecomAmber else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Overwrite Policy
                Text(
                    text = "Field Overwrite Policy:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OverwritePolicy.values().forEach { policy ->
                        val isSelected = uiState.overwritePolicy == policy
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) RFCyan.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSelected) RFCyan else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSetOverwritePolicy(policy) }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = policy.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) RFCyan else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }
        }

        // Column Selection Toolbar
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$includedCount OF $totalNonKey COLUMNS SELECTED • $skippedCount SKIPPED",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (skippedCount > 0) TelecomAmber else MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onSelectAll,
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.sizeIn(minHeight = 32.dp)
                        ) {
                            Text("All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onDeselectAll,
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.sizeIn(minHeight = 32.dp)
                        ) {
                            Text("None", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                val existingCount = nonKeyMappings.count { it.targetField != TargetField.IGNORE && it.isExistingInDatabase }
                val newCount = nonKeyMappings.count { it.targetField != TargetField.IGNORE && !it.isExistingInDatabase }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "• $existingCount Existing in Data",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = RFCyan
                    )
                    Text(
                        text = "• $newCount New Attribute",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = StatusRestoredGreen
                    )
                }
            }
        }

        // Column Mappings List with Checkboxes
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = nonKeyMappings,
                key = { "col_${it.columnIndex}_${it.sourceHeader}" }
            ) { mapping ->
                ColumnMappingCard(
                    mapping = mapping,
                    sampleValues = uiState.sampleRows.mapNotNull { it.getOrNull(mapping.columnIndex) }.filter { it.isNotBlank() },
                    availableTabs = uiState.availableTabs,
                    onToggleSelected = { isIncluded -> onToggleColumn(mapping.columnIndex, isIncluded) },
                    onTargetChanged = { target, group, key, tabId, tabDisplayName ->
                        onUpdateTarget(mapping.columnIndex, target, group, key, tabId, tabDisplayName)
                    },
                    onCreateNewTab = { tabName, sectionName ->
                        onCreateNewTab(mapping.columnIndex, tabName, sectionName)
                    }
                )
            }
        }

        Button(
            onClick = onGeneratePreview,
            colors = ButtonDefaults.buttonColors(
                containerColor = TelecomAmber,
                contentColor = Color(0xFF0F172A)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Preview Match & Changes ($includedCount columns)", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun ColumnMappingCard(
    mapping: VlookupColumnMapping,
    sampleValues: List<String>,
    availableTabs: List<SiteTabEntity>,
    onToggleSelected: (Boolean) -> Unit,
    onTargetChanged: (TargetField, String?, String?, String, String) -> Unit,
    onCreateNewTab: (String, String?) -> Unit
) {
    val isIncluded = mapping.targetField != TargetField.IGNORE
    var isTabExpanded by remember { mutableStateOf(false) }
    var isFieldExpanded by remember { mutableStateOf(false) }
    var showCreateTabDialog by remember { mutableStateOf(false) }
    var showCustomSectionDialog by remember { mutableStateOf(false) }

    val currentTabType = when {
        mapping.targetField == TargetField.IGNORE -> "IGNORE"
        mapping.targetField in TargetField.standardFields() &&
            mapping.targetField != TargetField.CUSTOM_SPEC &&
            mapping.targetField != TargetField.IGNORE -> "OVERVIEW"
        mapping.tabId.equals("LOGS", ignoreCase = true) -> "LOGS"
        mapping.tabId.equals("OVERVIEW", ignoreCase = true) -> "OVERVIEW"
        mapping.tabId.equals("SPECS", ignoreCase = true) -> "SPECS"
        else -> "CUSTOM"
    }

    val currentTabDisplay = when (currentTabType) {
        "IGNORE" -> "Skipped"
        "OVERVIEW" -> "Overview"
        "LOGS" -> "Logs"
        "SPECS" -> "Specs"
        else -> mapping.tabDisplayName.ifBlank { mapping.tabId }
    }

    val customTabs = availableTabs.filter {
        !it.isSystem &&
            !it.tabId.equals("SPECS", ignoreCase = true) &&
            !it.tabId.equals("LOGS", ignoreCase = true) &&
            !it.tabId.equals("OVERVIEW", ignoreCase = true)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isIncluded) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(
            1.dp,
            if (isIncluded) MaterialTheme.colorScheme.outlineVariant
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Tier 1: Checkbox, Column Header Name, and Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isIncluded,
                    onCheckedChange = { onToggleSelected(it) },
                    colors = CheckboxDefaults.colors(checkedColor = TelecomAmber),
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = mapping.sourceHeader,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isIncluded) FontWeight.Bold else FontWeight.Medium,
                    color = if (isIncluded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleSelected(!isIncluded) }
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (isIncluded) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (mapping.isExistingInDatabase) RFCyan.copy(alpha = 0.15f) else StatusRestoredGreen.copy(alpha = 0.15f),
                        border = BorderStroke(
                            1.dp,
                            if (mapping.isExistingInDatabase) RFCyan.copy(alpha = 0.7f) else StatusRestoredGreen.copy(alpha = 0.7f)
                        )
                    ) {
                        Text(
                            text = if (mapping.isExistingInDatabase) "EXISTING" else "NEW ATTRIBUTE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = if (mapping.isExistingInDatabase) RFCyan else StatusRestoredGreen
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "SKIPPED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Tier 2: 2-Step Mapping: Box 1 (Choose Tab) -> Box 2 (Choose Column / Attribute)
            if (isIncluded) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (mapping.isExistingInDatabase) "Updates existing records" else "New specification entry",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (sampleValues.isNotEmpty()) {
                        Text(
                            text = "Sample: ${sampleValues.take(2).joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Two-Step Selection Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // --- Box 1: Select Target Tab ---
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            onClick = { isTabExpanded = true },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .sizeIn(minHeight = 48.dp),
                            border = BorderStroke(1.dp, RFCyan.copy(alpha = 0.8f)),
                            color = RFCyan.copy(alpha = 0.1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "1. TARGET TAB",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RFCyan.copy(alpha = 0.8f)
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = currentTabDisplay,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = RFCyan
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select target tab",
                                        tint = RFCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = isTabExpanded,
                            onDismissRequest = { isTabExpanded = false }
                        ) {
                            Text(
                                text = "CHOOSE TAB",
                                style = MaterialTheme.typography.labelSmall.copy(color = RFCyan, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            DropdownMenuItem(
                                text = { Text("Specs") },
                                onClick = {
                                    onTargetChanged(TargetField.CUSTOM_SPEC, mapping.specGroup ?: "GENERAL", mapping.sourceHeader, "SPECS", "Specs")
                                    isTabExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Overview") },
                                onClick = {
                                    val defaultOverviewField = if (mapping.targetField in TargetField.standardFields() &&
                                        mapping.targetField != TargetField.CUSTOM_SPEC &&
                                        mapping.targetField != TargetField.IGNORE
                                    ) mapping.targetField else (ColumnMapper.matchField(mapping.sourceHeader) ?: TargetField.NAME)
                                    onTargetChanged(defaultOverviewField, null, mapping.sourceHeader, "OVERVIEW", "Overview")
                                    isTabExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Logs") },
                                onClick = {
                                    onTargetChanged(TargetField.NOTES, null, mapping.sourceHeader, "LOGS", "Logs")
                                    isTabExpanded = false
                                }
                            )

                            if (customTabs.isNotEmpty()) {
                                HorizontalDivider()
                                Text(
                                    text = "CUSTOM TABS (${customTabs.size})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                                customTabs.forEach { tab ->
                                    DropdownMenuItem(
                                        text = { Text(tab.displayName) },
                                        onClick = {
                                            onTargetChanged(TargetField.CUSTOM_SPEC, "GENERAL", mapping.sourceHeader, tab.tabId, tab.displayName)
                                            isTabExpanded = false
                                        }
                                    )
                                }
                            }

                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "+ CREATE NEW TAB...",
                                        color = TelecomAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                onClick = {
                                    isTabExpanded = false
                                    showCreateTabDialog = true
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Ignore Column") },
                                onClick = {
                                    onTargetChanged(TargetField.IGNORE, null, null, "SPECS", "Specs")
                                    isTabExpanded = false
                                }
                            )
                        }
                    }

                    // --- Box 2: Select Column / Field ---
                    Box(modifier = Modifier.weight(1.15f)) {
                        val isOverviewTab = currentTabType == "OVERVIEW"
                        val currentFieldDisplay = when (currentTabType) {
                            "IGNORE" -> "Skipped"
                            "OVERVIEW" -> mapping.targetField.displayName
                            "LOGS" -> when (mapping.targetField) {
                                TargetField.TAGS -> "Tags / Scope"
                                TargetField.CUSTOM_SPEC -> "[LOGS] ${mapping.specKey}"
                                else -> "Notes / Remarks"
                            }
                            else -> {
                                val sec = mapping.specGroup?.ifBlank { "GENERAL" } ?: "GENERAL"
                                "[$sec] ${mapping.specKey}"
                            }
                        }

                        Surface(
                            onClick = { isFieldExpanded = true },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .sizeIn(minHeight = 48.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isOverviewTab) TelecomAmber.copy(alpha = 0.8f) else RFCyan.copy(alpha = 0.8f)
                            ),
                            color = if (isOverviewTab) TelecomAmber.copy(alpha = 0.12f) else RFCyan.copy(alpha = 0.12f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (isOverviewTab) "2. CORE ATTRIBUTE" else "2. TARGET COLUMN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOverviewTab) TelecomAmber.copy(alpha = 0.9f) else RFCyan.copy(alpha = 0.9f)
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = currentFieldDisplay,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (isOverviewTab) TelecomAmber else RFCyan
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select target column",
                                        tint = if (isOverviewTab) TelecomAmber else RFCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = isFieldExpanded,
                            onDismissRequest = { isFieldExpanded = false }
                        ) {
                            when (currentTabType) {
                                "OVERVIEW" -> {
                                    Text(
                                        text = "OVERVIEW ATTRIBUTES",
                                        style = MaterialTheme.typography.labelSmall.copy(color = TelecomAmber, fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                    listOf(
                                        TargetField.NAME,
                                        TargetField.LAT,
                                        TargetField.LNG,
                                        TargetField.CIRCLE,
                                        TargetField.CITY,
                                        TargetField.DISTRICT,
                                        TargetField.STATE,
                                        TargetField.TECHNOLOGY,
                                        TargetField.SITE_TYPE,
                                        TargetField.STATUS,
                                        TargetField.ADDRESS,
                                        TargetField.NOTES,
                                        TargetField.TAGS
                                    ).forEach { field ->
                                        DropdownMenuItem(
                                            text = { Text(field.displayName) },
                                            onClick = {
                                                onTargetChanged(field, null, mapping.sourceHeader, "OVERVIEW", "Overview")
                                                isFieldExpanded = false
                                            }
                                        )
                                    }
                                }

                                "LOGS" -> {
                                    Text(
                                        text = "LOG ATTRIBUTES",
                                        style = MaterialTheme.typography.labelSmall.copy(color = TelecomAmber, fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Site Notes / Remarks") },
                                        onClick = {
                                            onTargetChanged(TargetField.NOTES, null, mapping.sourceHeader, "LOGS", "Logs")
                                            isFieldExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tags / Scope") },
                                        onClick = {
                                            onTargetChanged(TargetField.TAGS, null, mapping.sourceHeader, "LOGS", "Logs")
                                            isFieldExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Log Spec: [LOGS] ${mapping.specKey}") },
                                        onClick = {
                                            onTargetChanged(TargetField.CUSTOM_SPEC, "LOGS", mapping.sourceHeader, "LOGS", "Logs")
                                            isFieldExpanded = false
                                        }
                                    )
                                }

                                else -> {
                                    val tabTitle = if (currentTabType == "SPECS") "SPECS SECTIONS" else "${mapping.tabDisplayName.uppercase(Locale.US)} SECTIONS"
                                    Text(
                                        text = tabTitle,
                                        style = MaterialTheme.typography.labelSmall.copy(color = RFCyan, fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )

                                    val defaultSections = if (currentTabType == "SPECS") {
                                        listOf(
                                            "GENERAL", "POWER", "BATTERY", "DG", "RAN",
                                            "TRANSMISSION", "SAFETY", "ENVIRONMENT", "SECURITY", "CIVIL"
                                        )
                                    } else {
                                        listOf("GENERAL")
                                    }

                                    val allSections = (defaultSections + listOfNotNull(mapping.specGroup)).distinct()

                                    allSections.forEach { section ->
                                        DropdownMenuItem(
                                            text = { Text("[$section] ${mapping.specKey}") },
                                            onClick = {
                                                onTargetChanged(TargetField.CUSTOM_SPEC, section, mapping.sourceHeader, mapping.tabId, mapping.tabDisplayName)
                                                isFieldExpanded = false
                                            }
                                        )
                                    }

                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "+ CUSTOM SECTION...",
                                                color = TelecomAmber,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        onClick = {
                                            isFieldExpanded = false
                                            showCustomSectionDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (showCustomSectionDialog) {
                    var customSectionInput by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showCustomSectionDialog = false },
                        title = {
                            Text("Add Custom Section", fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Enter section name for tab '${mapping.tabDisplayName}' (e.g. INVERTER, METER, SECURITY):",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = customSectionInput,
                                    onValueChange = { customSectionInput = it },
                                    label = { Text("Section Name") },
                                    placeholder = { Text("e.g. INVERTER") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val trimmed = customSectionInput.trim().uppercase(Locale.US)
                                    if (trimmed.isNotBlank()) {
                                        onTargetChanged(TargetField.CUSTOM_SPEC, trimmed, mapping.sourceHeader, mapping.tabId, mapping.tabDisplayName)
                                    }
                                    showCustomSectionDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RFCyan),
                                enabled = customSectionInput.isNotBlank()
                            ) {
                                Text("Set Section", fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCustomSectionDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                if (showCreateTabDialog) {
                    var newTabName by remember { mutableStateOf("") }
                    var newSectionName by remember { mutableStateOf("") }

                    AlertDialog(
                        onDismissRequest = { showCreateTabDialog = false },
                        title = {
                            Text(
                                text = "Create New Site Tab",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Add a new dynamic tab to TowerDost. Incoming values for '${mapping.sourceHeader}' will be assigned to this tab.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = newTabName,
                                    onValueChange = { newTabName = it },
                                    label = { Text("Tab Name (e.g. EB BILLING)") },
                                    placeholder = { Text("EB BILLING") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = newSectionName,
                                    onValueChange = { newSectionName = it },
                                    label = { Text("Section / Group (Optional)") },
                                    placeholder = { Text("GENERAL") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (newTabName.isNotBlank()) {
                                        onCreateNewTab(newTabName.trim(), newSectionName.trim().ifBlank { null })
                                        showCreateTabDialog = false
                                    }
                                },
                                enabled = newTabName.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TelecomAmber,
                                    contentColor = Color(0xFF0F172A)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Create & Map", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showCreateTabDialog = false },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepPreview(
    uiState: VlookupMergerUiState,
    onExecuteMerge: () -> Unit,
    onBackToMapping: () -> Unit
) {
    val preview = uiState.previewData ?: return

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary KPI Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, TelecomAmber),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "VLOOKUP MERGE SUMMARY",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = TelecomAmber
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "${preview.matchedSitesCount}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = StatusRestoredGreen
                            )
                        )
                        Text(
                            text = "Sites Matched",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column {
                        Text(
                            text = "${preview.unmatchedRowsCount}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.mergeStrategy == MergeStrategy.UPDATE_AND_CREATE_NEW) RFCyan else StatusDownRed
                            )
                        )
                        Text(
                            text = if (uiState.mergeStrategy == MergeStrategy.UPDATE_AND_CREATE_NEW) "New Sites to Add" else "Unmatched (Skipped)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column {
                        Text(
                            text = "${preview.estimatedFieldsToUpdate}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TelecomAmber
                            )
                        )
                        Text(
                            text = "Fields to Update",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Sample Row Diff Previews
        Text(
            text = "Sample Row Changes Preview (First 10 Matches):",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = preview.samplePreviews,
                key = { index, item -> "${item.sapId}_$index" }
            ) { _, rowPreview ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = rowPreview.sapId,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            if (rowPreview.isNewSite) {
                                Text(
                                    text = "NEW SITE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = RFCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        rowPreview.diffs.forEach { diff ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = diff.fieldName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${diff.oldValue ?: "—"}  ➔  ${diff.newValue}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TelecomAmber
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBackToMapping,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Back")
            }

            Button(
                onClick = onExecuteMerge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TelecomAmber,
                    contentColor = Color(0xFF0F172A)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(2f)
                    .height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Execute Batch Merge", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StepExecuting(uiState: VlookupMergerUiState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, TelecomAmber),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    strokeWidth = 3.dp,
                    color = TelecomAmber,
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = "Merging Spreadsheet Columns...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (uiState.totalRowsToProcess > 0) {
                    val progressFloat = uiState.processedRows.toFloat() / uiState.totalRowsToProcess.toFloat()
                    LinearProgressIndicator(
                        progress = { progressFloat },
                        color = TelecomAmber,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = "Processed ${uiState.processedRows} of ${uiState.totalRowsToProcess} rows",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }

                Text(
                    text = "Executing non-destructive Room database transaction and updating FTS full-text search index...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StepComplete(
    result: app.telecom.sitetracker.data.merger.VlookupResult?,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(StatusRestoredGreen.copy(alpha = 0.15f))
                .border(2.dp, StatusRestoredGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = StatusRestoredGreen,
                modifier = Modifier.size(40.dp)
            )
        }

        Text(
            text = "Batch Merge Complete!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        result?.let { res ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ResultStatRow("Total Rows Processed", "${res.totalProcessed}")
                    ResultStatRow("Existing Sites Updated", "${res.sitesUpdated}")
                    if (res.sitesCreated > 0) {
                        ResultStatRow("New Sites Created", "${res.sitesCreated}")
                    }
                    ResultStatRow("Equipment Specs Added/Updated", "${res.specsUpdated}")
                    ResultStatRow("Execution Time", "${res.elapsedMs} ms")
                }
            }
        }

        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(
                containerColor = TelecomAmber,
                contentColor = Color(0xFF0F172A)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Open Sites Directory", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
