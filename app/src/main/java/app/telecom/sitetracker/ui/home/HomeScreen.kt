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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.telecom.sitetracker.R
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.ui.theme.MetricMonoStyle
import app.telecom.sitetracker.ui.theme.SapIdStyle
import app.telecom.sitetracker.ui.theme.SeverityCritical
import app.telecom.sitetracker.ui.theme.SeverityMajor
import app.telecom.sitetracker.ui.theme.TealPrimary
import app.telecom.sitetracker.util.Dates

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onSeedSampleData: () -> Unit,
    onLogIncidentClick: () -> Unit,
    onSiteClick: (String) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onDismissMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Clock Skew Warning Banner
            if (uiState.kpis.hasClockSkew) {
                item {
                    WarningBanner(
                        message = stringResource(R.string.warning_clock_skew),
                        backgroundColor = Color(0xFFFEF2F2),
                        textColor = Color(0xFF991B1B)
                    )
                }
            }

            // Today Strip
            item {
                TodayStrip(
                    sitesDownNow = uiState.kpis.sitesDownNow,
                    stillDownCount = uiState.openOutages.size,
                    yesterdayDowntimeHours = uiState.kpis.yesterdayDowntimeHours
                )
            }

            // Quick Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onLogIncidentClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Outage")
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

            // KPI Grid
            item {
                Text(
                    text = "Key Operations Metrics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                KpiGrid(kpis = uiState.kpis)
            }

            // Open Outages Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Outages (${uiState.openOutages.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (uiState.openOutages.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.kpis.totalSites == 0)
                                    "No master site data loaded yet. Tap 'Load Sample' to begin."
                                else
                                    "No open outages recorded. All sites reporting operational.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.openOutages, key = { it.id }) { outage ->
                    ActiveOutageCard(
                        outage = outage,
                        onSiteClick = { onSiteClick(outage.sapId) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun TodayStrip(
    sitesDownNow: Int,
    stillDownCount: Int,
    yesterdayDowntimeHours: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (sitesDownNow > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Sites Down Now",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF475569)
                )
                Text(
                    text = sitesDownNow.toString(),
                    style = MetricMonoStyle.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (sitesDownNow > 0) SeverityCritical else Color(0xFF16A34A)
                    )
                )
            }

            Column {
                Text(
                    text = "Unrestored",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF475569)
                )
                Text(
                    text = "$stillDownCount sites",
                    style = MetricMonoStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                )
            }

            Column {
                Text(
                    text = "Yesterday Downtime",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF475569)
                )
                Text(
                    text = Dates.formatHours(yesterdayDowntimeHours),
                    style = MetricMonoStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
fun KpiGrid(kpis: app.telecom.sitetracker.data.repo.HomeKpiData) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                title = "Total Master Sites",
                value = Dates.formatIndianNumber(kpis.totalSites.toLong()),
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Sites with Open Incidents",
                value = kpis.openIncidentsCount.toString(),
                modifier = Modifier.weight(1f),
                valueColor = if (kpis.openIncidentsCount > 0) SeverityMajor else Color.Unspecified
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                title = "Critical Open",
                value = kpis.criticalOpenCount.toString(),
                modifier = Modifier.weight(1f),
                valueColor = if (kpis.criticalOpenCount > 0) SeverityCritical else Color.Unspecified
            )
            KpiCard(
                title = "Average MTTR (90d)",
                value = Dates.formatHours(kpis.avgMttrHours),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MetricMonoStyle.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
fun ActiveOutageCard(
    outage: IncidentEntity,
    onSiteClick: () -> Unit
) {
    val now = System.currentTimeMillis()
    val daysDown = if (outage.downAt != null && now >= outage.downAt) {
        ((now - outage.downAt) / Dates.MS_PER_DAY).toInt() + 1
    } else 1

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSiteClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = outage.sapId,
                    style = SapIdStyle.copy(color = TealPrimary)
                )
                // Running Day Counter Badge (Pinned visibility)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SeverityCritical)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "STILL DOWN - DAY $daysDown",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = outage.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Category: ${outage.category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Since: ${Dates.formatDateTime(outage.downAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
