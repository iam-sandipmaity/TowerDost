package app.telecom.sitetracker.ui.activity

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.telecom.sitetracker.data.entity.IncidentEntity
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onSiteClick: (String) -> Unit,
    onAddLogClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filters = listOf("All", "Issue", "Maintenance", "Visit", "Note")

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddLogClick,
                containerColor = BrandPrimary,
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Log")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Log", fontWeight = FontWeight.Bold)
                }
            }
        },
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
                        label = { Text(filter, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandPrimary.copy(alpha = 0.12f),
                            selectedLabelColor = BrandPrimary
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
                                "No site logs recorded yet.\nTap '+ Add Log' below to record work done at any site.",
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
                            onSiteClick = { onSiteClick(log.sapId) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SiteLogFeedCard(
    log: IncidentEntity,
    onSiteClick: () -> Unit
) {
    val cat = log.category.lowercase()
    val (badgeText, badgeColor, badgeBg) = when {
        cat.contains("issue") || log.isOutage == 1 -> Triple("ISSUE", TagIssueColor, TagIssueBg)
        cat.contains("maint") || cat.contains("repair") -> Triple("MAINTENANCE", TagMaintColor, TagMaintBg)
        cat.contains("visit") || cat.contains("audit") -> Triple("VISIT", TagVisitColor, TagVisitBg)
        else -> Triple("NOTE", TagNoteColor, TagNoteBg)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSiteClick,
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
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = BrandPrimary
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
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
                    color = Color(0xFF15803D),
                    fontWeight = FontWeight.Medium
                )
            }

            // Technician / Engineer
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
        }
    }
}
