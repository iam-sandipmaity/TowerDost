package app.telecom.sitetracker.ui.tools

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.telecom.sitetracker.SiteTrackerApp
import app.telecom.sitetracker.data.export.Exporter
import app.telecom.sitetracker.ui.theme.BrandPrimary
import app.telecom.sitetracker.ui.theme.TagIssueColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

    var showClearSampleDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tools & Data", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Database & Master Tools",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                border = CardDefaults.outlinedCardBorder()
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
                            .background(BrandPrimary)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "TowerDost Field Database",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "100% Offline • Zero Internet Permission",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Stats row
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

            // Section: Import & Data Ingestion
            Text(
                text = "Data Management",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            ToolActionCard(
                title = "Import Master Site Sheet",
                description = "Load master sites and specs from Excel (.xlsx), CSV, or JSON.",
                icon = Icons.Default.UploadFile,
                buttonText = "Open Wizard",
                onClick = onNavigateToImport
            )

            // Section: Export & Sharing
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

            // Section: Database Cleanliness
            Text(
                text = "Database Maintenance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
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
                        color = TagIssueColor
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
                        colors = ButtonDefaults.buttonColors(containerColor = TagIssueColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Wipe All & Start Fresh")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
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
                    Text("Wipe All", color = TagIssueColor, fontWeight = FontWeight.Bold)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = count, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
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
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
