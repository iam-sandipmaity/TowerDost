package app.telecom.sitetracker.ui.activity

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.telecom.sitetracker.SiteTrackerApp
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.export.Exporter
import app.telecom.sitetracker.data.import.CsvReader
import app.telecom.sitetracker.data.import.DetectedFormat
import app.telecom.sitetracker.data.import.FileSniffer
import app.telecom.sitetracker.data.import.JsonReader
import app.telecom.sitetracker.data.import.LogColumnMapper
import app.telecom.sitetracker.data.import.LogColumnMapping
import app.telecom.sitetracker.data.import.LogImporter
import app.telecom.sitetracker.data.import.XlsxReader
import app.telecom.sitetracker.ui.theme.BorderDark
import app.telecom.sitetracker.ui.theme.SapIdStyle
import app.telecom.sitetracker.ui.theme.StatusDownRed
import app.telecom.sitetracker.ui.theme.StatusRestoredGreen
import app.telecom.sitetracker.ui.theme.TagIssueBg
import app.telecom.sitetracker.ui.theme.TagIssueColor
import app.telecom.sitetracker.ui.theme.TagMaintBg
import app.telecom.sitetracker.ui.theme.TagMaintColor
import app.telecom.sitetracker.ui.theme.TagNoteBg
import app.telecom.sitetracker.ui.theme.TagNoteColor
import app.telecom.sitetracker.ui.theme.TagVisitBg
import app.telecom.sitetracker.ui.theme.TagVisitColor
import app.telecom.sitetracker.ui.theme.TelecomAmber
import app.telecom.sitetracker.ui.tools.LogImportDialog
import app.telecom.sitetracker.ui.tools.StagedLogFileData
import app.telecom.sitetracker.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onSiteClick: (String) -> Unit,
    onAddLogClick: () -> Unit,
    onEditLogClick: (sapId: String, incidentId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = (context.applicationContext as? SiteTrackerApp) ?: SiteTrackerApp.instance!!
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val filters = listOf("All", "Issue", "Maintenance", "Visit", "Note")
    var logToDelete by remember { mutableStateOf<IncidentEntity?>(null) }

    val logImporter = remember { LogImporter(app.database, app.clock) }
    var stagedLogData by remember { mutableStateOf<StagedLogFileData?>(null) }
    var isImportingLogs by remember { mutableStateOf(false) }
    var logImportProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val logFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val displayName = getFileNameFromUri(context, uri) ?: "Site_Logs"
                    val tempFile = File(context.cacheDir, "temp_activity_log_${System.currentTimeMillis()}.tmp")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }

                    val format = tempFile.inputStream().buffered().use { FileSniffer.sniffFormat(it) }
                    val headers: List<String>
                    val allRows = mutableListOf<List<String>>()

                    when (format) {
                        DetectedFormat.XLSX -> {
                            val reader = XlsxReader(tempFile)
                            val sheets = reader.getSheets()
                            if (sheets.isEmpty()) {
                                snackbarHostState.showSnackbar("No sheets found in Excel file.")
                                return@launch
                            }
                            val sharedStrings = reader.readSharedStrings()
                            val headerData = reader.detectHeaders(sheets.first().targetPath, sharedStrings)
                            headers = headerData.headers
                            reader.streamSheetRows(sheets.first().targetPath, sharedStrings, skipRows = headerData.firstDataRowIndex) { _, cells ->
                                val row = (0 until headers.size).map { cells[it] ?: "" }
                                allRows.add(row)
                            }
                        }
                        DetectedFormat.CSV -> {
                            val reader = CsvReader(tempFile)
                            val headerData = reader.readHeaders()
                            headers = headerData.headers
                            var isFirst = true
                            reader.streamRecords(headerData.delimiter, headerData.charset) { r ->
                                if (isFirst) isFirst = false else allRows.add(r)
                            }
                        }
                        DetectedFormat.JSON -> {
                            val reader = JsonReader(tempFile)
                            val headerData = reader.readHeaders()
                            headers = headerData.headers
                            reader.streamRecords(headers) { r -> allRows.add(r) }
                        }
                        else -> {
                            snackbarHostState.showSnackbar("Unsupported file format. Please choose CSV or XLSX.")
                            return@launch
                        }
                    }

                    val mappings = LogColumnMapper.generateInitialMappings(headers)
                    stagedLogData = StagedLogFileData(
                        fileName = displayName,
                        headers = headers,
                        mappings = mappings,
                        rows = allRows
                    )
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to read log file: ${e.message}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Site Logs", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (uiState.allLogs.isEmpty()) "No activity yet" else "${uiState.allLogs.size} recorded entries",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { logFilePickerLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import Logs", tint = TelecomAmber)
                    }
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val logs = app.incidentRepository.getAllIncidentsList()
                                if (logs.isEmpty()) {
                                    snackbarHostState.showSnackbar("No logs to export.")
                                    return@launch
                                }
                                val file = Exporter.exportRegisterCsv(context, logs)
                                Exporter.shareCsvFile(context, file, "TowerDost Site Logs")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Export failed: ${e.message}")
                            }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export Logs")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddLogClick,
                containerColor = TelecomAmber,
                contentColor = Color(0xFF0F172A)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Log", tint = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Log", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
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
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search logs by SAP ID, notes, technician...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            // Category Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (filter in filters) {
                    val isSelected = uiState.selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onFilterSelected(filter) },
                        label = { Text(filter, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TelecomAmber.copy(alpha = 0.2f),
                            selectedLabelColor = TelecomAmber
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // List of Logs
            if (uiState.filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (uiState.searchQuery.isNotBlank() || uiState.selectedFilter != "All")
                                "No logs match your filter."
                            else
                                "No site logs recorded yet.\nTap '+ Add Log' below or tap the Import icon above to load previous logs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.filteredLogs, key = { it.id }) { log ->
                        SiteLogFeedCard(
                            log = log,
                            onSiteClick = { onSiteClick(log.sapId) },
                            onEditClick = { onEditLogClick(log.sapId, log.id) },
                            onDeleteClick = { logToDelete = log }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (stagedLogData != null) {
        val staged = stagedLogData!!
        LogImportDialog(
            fileName = staged.fileName,
            totalRows = staged.rows.size,
            mappings = staged.mappings,
            isImporting = isImportingLogs,
            importProgress = logImportProgress,
            onDismiss = { stagedLogData = null },
            onConfirmImport = { policy, mappings ->
                isImportingLogs = true
                scope.launch(Dispatchers.IO) {
                    try {
                        val result = logImporter.executeImport(
                            rows = staged.rows,
                            mappings = mappings,
                            policy = policy,
                            onProgress = { processed, total ->
                                logImportProgress = Pair(processed, total)
                            }
                        )
                        isImportingLogs = false
                        stagedLogData = null
                        snackbarHostState.showSnackbar("Imported ${result.insertedCount} new logs (${result.updatedCount} updated).")
                    } catch (e: Exception) {
                        isImportingLogs = false
                        snackbarHostState.showSnackbar("Import failed: ${e.message}")
                    }
                }
            }
        )
    }

    if (logToDelete != null) {
        val target = logToDelete!!
        AlertDialog(
            onDismissRequest = { logToDelete = null },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = StatusDownRed, modifier = Modifier.size(36.dp))
            },
            title = {
                Text("Delete Site Log?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "⚠️ Warning: This will permanently remove the log for site ${target.sapId} from the database. Any notes, technician details, and photos attached to this log will be deleted.\n\nThis action cannot be undone. Are you sure you want to proceed?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLog(target.id)
                        logToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDownRed)
                ) {
                    Text("Delete Permanently", fontWeight = FontWeight.Bold, color = Color.White)
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
private fun SiteLogFeedCard(
    log: IncidentEntity,
    onSiteClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val cat = log.category.lowercase().trim()
    val (badgeText, badgeColor, badgeBg) = when {
        cat.contains("maint") || cat.contains("repair") -> Triple("MAINTENANCE", TagMaintColor, TagMaintBg)
        cat.contains("visit") || cat.contains("site") || cat.contains("inspect") || cat.contains("audit") -> Triple("VISIT", TagVisitColor, TagVisitBg)
        cat.contains("note") || cat.contains("general") -> Triple("NOTE", TagNoteColor, TagNoteBg)
        cat.contains("issue") || log.isOutage == 1 -> Triple("ISSUE", TagIssueColor, TagIssueBg)
        else -> Triple(log.category.uppercase().ifBlank { "NOTE" }, TagNoteColor, TagNoteBg)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: SAP ID + Tag + Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = log.sapId,
                        style = SapIdStyle.copy(fontSize = 16.sp, color = TelecomAmber),
                        modifier = Modifier.clickable { onSiteClick() }
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Text(
                    text = Dates.formatDateTime(log.occurredAt),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Note Text
            val displayText = log.description ?: log.title
            if (displayText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            }

            // Action taken (if present)
            if (!log.actionTaken.isNullOrBlank() && log.actionTaken != displayText) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Action: ${log.actionTaken}",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusRestoredGreen,
                    fontWeight = FontWeight.Medium
                )
            }

            // Technician / Engineer
            if (!log.engineer.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Action footer row (48dp height targets)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onSiteClick,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.CellTower, contentDescription = null, modifier = Modifier.size(16.dp), tint = TelecomAmber)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tower", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TelecomAmber)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onEditClick,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(
                        onClick = onDeleteClick,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusDownRed)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = StatusDownRed)
                    }
                }
            }
        }
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            name = cursor.getString(nameIndex)
        }
    }
    return name
}
