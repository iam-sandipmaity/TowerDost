package app.telecom.sitetracker.ui.tools

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.telecom.sitetracker.R
import app.telecom.sitetracker.SiteTrackerApp
import app.telecom.sitetracker.data.backup.BackupManager
import app.telecom.sitetracker.data.export.Exporter
import app.telecom.sitetracker.data.import.CsvReader
import app.telecom.sitetracker.data.import.DetectedFormat
import app.telecom.sitetracker.data.import.FileSniffer
import app.telecom.sitetracker.data.import.JsonReader
import app.telecom.sitetracker.data.import.LogColumnMapper
import app.telecom.sitetracker.data.import.LogColumnMapping
import app.telecom.sitetracker.data.import.LogImporter
import app.telecom.sitetracker.data.import.MergePolicy
import app.telecom.sitetracker.data.import.XlsxReader
import app.telecom.sitetracker.ui.components.EnterpriseTopBar
import app.telecom.sitetracker.ui.theme.BorderDark
import app.telecom.sitetracker.ui.theme.StatusDownRed
import app.telecom.sitetracker.ui.theme.StatusRestoredGreen
import app.telecom.sitetracker.ui.theme.TelecomAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class StagedLogFileData(
    val fileName: String,
    val headers: List<String>,
    val mappings: List<LogColumnMapping>,
    val rows: List<List<String>>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateToImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = (context.applicationContext as? SiteTrackerApp) ?: SiteTrackerApp.instance!!
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val totalSites by app.siteRepository.observeActiveSites().map { it.size }.collectAsState(initial = 0)
    val totalLogs by app.incidentRepository.observeAllIncidents().map { it.size }.collectAsState(initial = 0)

    val backupManager = remember { BackupManager(app.database, app.clock) }
    val logImporter = remember { LogImporter(app.database, app.clock) }

    var showClearSampleDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }

    // Staged Log Import State
    var stagedLogData by remember { mutableStateOf<StagedLogFileData?>(null) }
    var isImportingLogs by remember { mutableStateOf(false) }
    var logImportProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Staged Backup Restore State
    var stagedBackupJson by remember { mutableStateOf<String?>(null) }
    var isRestoringBackup by remember { mutableStateOf(false) }

    // File Picker for Log Import
    val logFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val displayName = getFileNameFromUri(context, uri) ?: "Site_Logs"
                    val tempFile = File(context.cacheDir, "temp_log_import_${System.currentTimeMillis()}.tmp")
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

    // File Picker for Full Backup Restore
    val backupPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (json.isNullOrBlank()) {
                        snackbarHostState.showSnackbar("Selected backup file is empty.")
                        return@launch
                    }
                    stagedBackupJson = json
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Error reading backup: ${e.message}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            EnterpriseTopBar(
                title = "Tools & Migration",
                subtitle = "Database & Master Tools"
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info Card (Industrial Slate with crisp border)
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
                        Icon(Icons.Default.Security, contentDescription = null, tint = TelecomAmber)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "TowerDost Field Database",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "100% Offline • Zero Network Dependency",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Stats row (Tabular Monospace Figures)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Master Sites",
                    count = "$totalSites",
                    icon = Icons.Default.CellTower,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Site Logs",
                    count = "$totalLogs",
                    icon = Icons.Default.Description,
                    modifier = Modifier.weight(1f)
                )
            }

            // ==========================================
            // Section 1: Device Migration & Backup (New!)
            // ==========================================
            Text(
                text = "Device Migration & Backup",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            ToolActionCard(
                title = "Full Database Backup (.json)",
                description = "Export complete sites, specs, and logs into a single file to switch devices seamlessly.",
                icon = Icons.Default.CloudUpload,
                buttonText = "Backup All",
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val file = backupManager.exportBackupToFile(context)
                            backupManager.shareBackupFile(context, file)
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Backup failed: ${e.message}")
                        }
                    }
                }
            )

            ToolActionCard(
                title = "Restore Database Backup (.json)",
                description = "Restore full database backup from your previous phone.",
                icon = Icons.Default.CloudDownload,
                buttonText = "Restore",
                onClick = {
                    backupPickerLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                }
            )

            // ==========================================
            // Section 2: Data Ingestion & Logs
            // ==========================================
            Text(
                text = "Data Ingestion & Logs",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            ToolActionCard(
                title = "Import Site Logs (.csv / .xlsx)",
                description = "Import previously recorded maintenance, visit, or NOC outage logs.",
                icon = Icons.Default.FileUpload,
                buttonText = "Import Logs",
                onClick = {
                    logFilePickerLauncher.launch(arrayOf("*/*"))
                }
            )

            ToolActionCard(
                title = "Import Master Site Sheet",
                description = "Load master sites and specs from Excel (.xlsx), CSV, or JSON.",
                icon = Icons.Default.UploadFile,
                buttonText = "Open Wizard",
                onClick = onNavigateToImport
            )

            // ==========================================
            // Section 3: Export & Reports
            // ==========================================
            Text(
                text = "Export & Reports",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            ToolActionCard(
                title = "Export Site Logs (.csv)",
                description = "Export all recorded maintenance, visit, and issue notes.",
                icon = Icons.Default.Share,
                buttonText = "Export Logs",
                onClick = {
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
                }
            )

            ToolActionCard(
                title = "Export Master Sites (.csv)",
                description = "Export current master site inventory and equipment specifications.",
                icon = Icons.Default.Share,
                buttonText = "Export Sites",
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val sites = app.siteRepository.getAllSitesList()
                            if (sites.isEmpty()) {
                                snackbarHostState.showSnackbar("No sites to export.")
                                return@launch
                            }
                            val allFields = app.siteRepository.getAllFields()
                            val file = Exporter.exportSitesCsv(context, sites, allFields)
                            Exporter.shareCsvFile(context, file, "TowerDost Master Sites")
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Export failed: ${e.message}")
                        }
                    }
                }
            )

            // ==========================================
            // Section 4: Database Cleanliness
            // ==========================================
            Text(
                text = "Database Maintenance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Clean Sample / Dummy Data",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Removes sample demo sites (DEL, MUM, BLR) and leaves real imported sites and logs intact.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showClearSampleDialog = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remove Demo Data")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Reset Entire Database",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusDownRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Wipes all sites and logs so you can start completely fresh with your own files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { showResetAllDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDownRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Wipe All & Start Fresh")
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Log Import Preview Dialog
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
                        snackbarHostState.showSnackbar("Successfully imported ${result.insertedCount} new logs (${result.updatedCount} updated) in ${(result.elapsedMs / 1000.0)}s")
                    } catch (e: Exception) {
                        isImportingLogs = false
                        snackbarHostState.showSnackbar("Log import failed: ${e.message}")
                    }
                }
            }
        )
    }

    // Backup Restore Confirmation Dialog
    if (stagedBackupJson != null) {
        val json = stagedBackupJson!!
        AlertDialog(
            onDismissRequest = { if (!isRestoringBackup) stagedBackupJson = null },
            title = { Text("Restore TowerDost Backup?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Do you want to clean-overwrite (wipe current data and mirror backup) or merge backup records into existing tables?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (isRestoringBackup) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Restoring backup...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isRestoringBackup = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val res = backupManager.restoreFromBackupJson(json, cleanOverwrite = true)
                                isRestoringBackup = false
                                stagedBackupJson = null
                                snackbarHostState.showSnackbar("Restore complete! Loaded ${res.sitesRestored} sites, ${res.incidentsRestored} logs.")
                            } catch (e: Exception) {
                                isRestoringBackup = false
                                snackbarHostState.showSnackbar("Restore failed: ${e.message}")
                            }
                        }
                    },
                    enabled = !isRestoringBackup,
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber)
                ) {
                    Text("Clean Overwrite (1:1 Switch)", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }
            },
            dismissButton = {
                Row {
                    OutlinedButton(
                        onClick = {
                            isRestoringBackup = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val res = backupManager.restoreFromBackupJson(json, cleanOverwrite = false)
                                    isRestoringBackup = false
                                    stagedBackupJson = null
                                    snackbarHostState.showSnackbar("Merge complete! Loaded ${res.sitesRestored} sites, ${res.incidentsRestored} logs.")
                                } catch (e: Exception) {
                                    isRestoringBackup = false
                                    snackbarHostState.showSnackbar("Restore failed: ${e.message}")
                                }
                            }
                        },
                        enabled = !isRestoringBackup
                    ) {
                        Text("Merge Records")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { stagedBackupJson = null },
                        enabled = !isRestoringBackup
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (showClearSampleDialog) {
        AlertDialog(
            onDismissRequest = { showClearSampleDialog = false },
            title = { Text("Remove Demo Data?") },
            text = { Text("This will delete only the demo fictional sites and demo logs. Your imported sites and real logs will remain safe.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearSampleDialog = false
                        scope.launch {
                            app.siteRepository.deleteSampleSites()
                            app.incidentRepository.clearSampleIncidents()
                            snackbarHostState.showSnackbar("Demo data removed.")
                        }
                    }
                ) {
                    Text("Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSampleDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text("Wipe All Data?") },
            text = { Text("This will delete all sites, specs, and logs permanently. You can import your Excel sheet afterwards.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetAllDialog = false
                        scope.launch {
                            app.siteRepository.deleteAllSites()
                            app.incidentRepository.clearAllIncidents()
                            snackbarHostState.showSnackbar("All data wiped. Ready for fresh import.")
                        }
                    }
                ) {
                    Text("Wipe All", color = StatusDownRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    count: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = TelecomAmber, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToolActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    buttonText: String,
    onClick: () -> Unit
) {
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
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
