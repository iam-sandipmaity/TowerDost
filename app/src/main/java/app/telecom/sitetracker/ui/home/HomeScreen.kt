package app.telecom.sitetracker.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.telecom.sitetracker.R
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.ui.components.EnterpriseListRow
import app.telecom.sitetracker.ui.components.EnterpriseTopBar
import app.telecom.sitetracker.ui.components.MetricTile
import app.telecom.sitetracker.ui.components.StatusDotBadge
import app.telecom.sitetracker.ui.theme.SapIdStyle
import app.telecom.sitetracker.ui.theme.SeverityCritical
import app.telecom.sitetracker.ui.theme.SeverityMajor
import app.telecom.sitetracker.ui.theme.StatusDownRed
import app.telecom.sitetracker.ui.theme.StatusRestoredGreen
import app.telecom.sitetracker.ui.theme.TelecomAmber
import app.telecom.sitetracker.ui.theme.TelemetryCyan
import app.telecom.sitetracker.util.Dates

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onSeedSampleData: () -> Unit,
    onLogIncidentClick: () -> Unit,
    onSiteClick: (String) -> Unit,
    onDismissMessage: () -> Unit,
    onTotalSitesClick: () -> Unit = {},
    onSitesDownClick: () -> Unit = {},
    onOpenIncidentsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onDismissMessage()
        }
    }

    Scaffold(
        topBar = {
            EnterpriseTopBar(
                title = "Command Center",
                subtitle = "${Dates.formatIndianNumber(uiState.kpis.totalSites.toLong())} Monitored Towers"
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Clock Skew Warning Banner
            if (uiState.kpis.hasClockSkew) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        WarningBanner(
                            message = stringResource(R.string.warning_clock_skew),
                            backgroundColor = SeverityCritical.copy(alpha = 0.15f),
                            textColor = SeverityCritical
                        )
                    }
                }
            }

            // Executive 3-Column Metric Strip
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val downCount = uiState.kpis.sitesDownNow
                    MetricTile(
                        label = "Down Now",
                        value = downCount.toString(),
                        valueColor = if (downCount > 0) StatusDownRed else StatusRestoredGreen,
                        modifier = Modifier.weight(1f),
                        onClick = onSitesDownClick
                    )
                    MetricTile(
                        label = "Unrestored",
                        value = "${uiState.openOutages.size}",
                        valueColor = if (uiState.openOutages.isNotEmpty()) SeverityMajor else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenIncidentsClick
                    )
                    MetricTile(
                        label = "24h Loss",
                        value = Dates.formatHours(uiState.kpis.yesterdayDowntimeHours),
                        valueColor = TelecomAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Fast Action Dock
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onLogIncidentClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF0F172A))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Outage", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }

                    if (uiState.kpis.totalSites == 0) {
                        OutlinedButton(
                            onClick = onSeedSampleData,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            enabled = !uiState.isSeeding,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (uiState.isSeeding) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp).width(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Load Sample")
                            }
                        }
                    }
                }
            }

            // Secondary Metrics Strip
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricTile(
                        label = "Total Master Sites",
                        value = Dates.formatIndianNumber(uiState.kpis.totalSites.toLong()),
                        valueColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        onClick = onTotalSitesClick
                    )
                    MetricTile(
                        label = "Average MTTR (90d)",
                        value = Dates.formatHours(uiState.kpis.avgMttrHours),
                        valueColor = TelemetryCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Section Header: Active Outages Feed
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE OUTAGES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "${uiState.openOutages.size} active incidents",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            if (uiState.openOutages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.kpis.totalSites == 0)
                                "No master site data loaded. Tap 'Load Sample' to begin."
                            else
                                "Zero live outages recorded. All sites reporting operational.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(uiState.openOutages, key = { it.id }) { outage ->
                    ActiveOutageRow(
                        outage = outage,
                        onSiteClick = { onSiteClick(outage.sapId) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun ActiveOutageRow(
    outage: IncidentEntity,
    onSiteClick: () -> Unit
) {
    val now = System.currentTimeMillis()
    val daysDown = if (outage.downAt != null && now >= outage.downAt) {
        ((now - outage.downAt) / Dates.MS_PER_DAY).toInt() + 1
    } else 1

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
                text = if (daysDown > 1) "STILL DOWN • DAY $daysDown" else "DOWN NOW",
                color = StatusDownRed
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Reason: ${outage.category}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Since: ${Dates.formatDateTime(outage.downAt)}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WarningBanner(
    message: String,
    backgroundColor: Color,
    textColor: Color
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = textColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(color = textColor, fontWeight = FontWeight.Medium)
            )
        }
    }
}
