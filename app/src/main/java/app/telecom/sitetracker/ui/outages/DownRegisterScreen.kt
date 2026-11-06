package app.telecom.sitetracker.ui.outages

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.ui.theme.MetricMonoStyle
import app.telecom.sitetracker.ui.theme.SapIdStyle
import app.telecom.sitetracker.ui.theme.SeverityCritical
import app.telecom.sitetracker.ui.theme.SeverityMajor
import app.telecom.sitetracker.ui.theme.SeverityMinor
import app.telecom.sitetracker.ui.theme.TealPrimary
import app.telecom.sitetracker.util.Dates

@Composable
fun DownRegisterScreen(
    viewModel: DownRegisterViewModel,
    onSiteClick: (String) -> Unit,
    onLogOutageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onLogOutageClick,
                containerColor = TealPrimary,
                contentColor = Color.White
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Log Outage")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Log Outage", fontWeight = FontWeight.Bold)
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
                placeholder = { Text("Search down register (SAP ID, Title, Tech)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            // Tabs: Active Down, Restored, All
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.onTabSelected(0) },
                    text = {
                        val count = uiState.openOutages.size
                        Text("Down Now ($count)", fontWeight = if (uiState.selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                    }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.onTabSelected(1) },
                    text = {
                        Text("Restored (${uiState.restoredOutages.size})")
                    }
                )
                Tab(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.onTabSelected(2) },
                    text = {
                        Text("All (${uiState.allOutages.size})")
                    }
                )
            }

            // Current List
            val currentList = when (uiState.selectedTab) {
                0 -> uiState.openOutages
                1 -> uiState.restoredOutages
                else -> uiState.allOutages
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
                            0 -> if (uiState.searchQuery.isNotBlank()) "No open outages match your search." else "All sites are operational! No active outages."
                            1 -> "No restored outages recorded."
                            else -> "No outages recorded in register."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(currentList, key = { it.id }) { outage ->
                        OutageRegisterCard(
                            outage = outage,
                            onSiteClick = { onSiteClick(outage.sapId) },
                            onRestore = { viewModel.restoreSite(outage) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) } // FAB clearance
                }
            }
        }
    }
}

@Composable
private fun OutageRegisterCard(
    outage: IncidentEntity,
    onSiteClick: () -> Unit,
    onRestore: () -> Unit
) {
    val isStillDown = outage.restoredAt == null && outage.isOutage == 1
    val now = System.currentTimeMillis()

    val runningHours = if (outage.downAt != null) {
        val endTime = outage.restoredAt ?: now
        if (endTime >= outage.downAt) (endTime - outage.downAt) / 3600000.0 else 0.0
    } else 0.0

    val daysDown = if (runningHours > 0) (runningHours / 24.0).toInt() + 1 else 1

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSiteClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isStillDown) Color(0xFFFFF7ED) else MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = outage.sapId,
                    style = SapIdStyle.copy(fontSize = 18.sp, color = TealPrimary)
                )

                // Still Down or Resolved badge
                if (isStillDown) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SeverityCritical)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (daysDown > 1) "STILL DOWN • DAY $daysDown" else "DOWN • ${Dates.formatHours(runningHours)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFDCFCE7))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "RESTORED (${Dates.formatHours(runningHours)})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = outage.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (!outage.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = outage.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Severity pill
                    val sevColor = when (outage.severity) {
                        "Critical" -> SeverityCritical
                        "Major" -> SeverityMajor
                        "Minor" -> SeverityMinor
                        else -> Color(0xFF64748B)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(sevColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = outage.severity,
                            style = MaterialTheme.typography.labelSmall.copy(color = sevColor, fontWeight = FontWeight.Bold)
                        )
                    }

                    // Category pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = outage.category,
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }

                Text(
                    text = "Down: ${Dates.formatDateTime(outage.downAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Restore Site Button (Only for open outages)
            if (isStillDown) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRestore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("1-Tap Restore Site Now", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
