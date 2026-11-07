package app.telecom.sitetracker.ui.sitedetail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.telecom.sitetracker.R
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.ui.theme.SeverityCritical
import app.telecom.sitetracker.ui.theme.SeverityMajor
import app.telecom.sitetracker.ui.theme.SeverityMinor
import app.telecom.sitetracker.util.Dates
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteDetailScreen(
    viewModel: SiteDetailViewModel,
    onNavigateBack: () -> Unit,
    onLogIncident: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var specFilterQuery by remember { mutableStateOf("") }

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
            TopAppBar(
                title = { Text(stringResource(R.string.site_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val site = uiState.site ?: return@IconButton
                            shareSiteDetails(context, site, uiState.groupedSpecs)
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share specs")
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
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.site == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Site not found", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val site = uiState.site!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Pinned Header Card
                SiteHeaderCard(
                    site = site,
                    contactPhone = uiState.contactPhone,
                    onNavigate = { lat, lng -> launchNavigation(context, lat, lng, site.sapId) },
                    onCall = { phone -> launchCall(context, phone) }
                )

                // Before-you-go Repeat Fault Alert
                if (uiState.isRepeatFault) {
                    BeforeYouGoCard(
                        count = uiState.repeatFaultCount,
                        dominantCategory = uiState.dominantCategory,
                        recommendation = uiState.recommendation
                    )
                }

                // Live Outage Status Banner
                LiveOutageStatusBar(
                    openOutage = uiState.openOutage,
                    onMarkDown = { onLogIncident(site.sapId) },
                    onRestore = { viewModel.restoreCurrentOutage() }
                )

                // Tabs: Overview, Specs, Incidents
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Overview") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            val specCount = uiState.groupedSpecs.values.sumOf { it.size }
                            Text("Specs ($specCount)")
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Incidents (${uiState.incidents.size})") }
                    )
                }

                // Tab Content
                when (selectedTab) {
                    0 -> OverviewTab(site = site)
                    1 -> SpecsTab(
                        groupedSpecs = uiState.groupedSpecs,
                        filterQuery = specFilterQuery,
                        onFilterChange = { specFilterQuery = it }
                    )
                    2 -> IncidentsTab(
                        sapId = site.sapId,
                        incidents = uiState.incidents,
                        onLogIncident = { onLogIncident(site.sapId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteHeaderCard(
    site: SiteEntity,
    contactPhone: String?,
    onNavigate: (Double, Double) -> Unit,
    onCall: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = site.sapId,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                val statusText = site.status ?: "Active"
                Surface(
                    color = if (statusText.equals("active", ignoreCase = true)) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = statusText.uppercase(Locale.US),
                        color = if (statusText.equals("active", ignoreCase = true)) Color(0xFF2E7D32) else Color(0xFFC62828),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (!site.name.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = site.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (site.lat != null && site.lng != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${site.lat.format(5)}, ${site.lng.format(5)}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (site.lat != null && site.lng != null) {
                    Button(
                        onClick = { onNavigate(site.lat, site.lng) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_navigate))
                    }
                }

                if (!contactPhone.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onCall(contactPhone) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_call))
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(site: SiteEntity) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OverviewRow("Circle", site.circle ?: "—")
        OverviewRow("Region", site.region ?: "—")
        OverviewRow("District", site.district ?: "—")
        OverviewRow("City / JC", site.city ?: "—")
        OverviewRow("Technology", site.technology ?: "—")
        OverviewRow("Site Type", site.siteType ?: "—")
        OverviewRow("Address", site.address ?: "—")
        OverviewRow("Tags / Scope", site.tags ?: "—")
        OverviewRow("Source File", site.sourceFile ?: "—")
        if (site.updatedAt != null) {
            OverviewRow("Last Updated", Dates.formatDateTime(site.updatedAt))
        }
        if (!site.notes.isNullOrBlank()) {
            OverviewRow("Remarks", site.notes)
        }
    }
}

@Composable
private fun OverviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
}

@Composable
private fun SpecsTab(
    groupedSpecs: Map<String, List<SiteFieldEntity>>,
    filterQuery: String,
    onFilterChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = filterQuery,
            onValueChange = onFilterChange,
            placeholder = { Text("Filter specifications (e.g. SMPS, rating, DG)...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            singleLine = true
        )

        val filteredGroups = groupedSpecs.mapValues { (_, fields) ->
            if (filterQuery.isBlank()) fields else {
                fields.filter {
                    it.fieldKey.contains(filterQuery, ignoreCase = true) ||
                            (it.fieldValue?.contains(filterQuery, ignoreCase = true) == true)
                }
            }
        }.filter { it.value.isNotEmpty() }

        if (filteredGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matching specifications found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                filteredGroups.forEach { (groupName, fields) ->
                    item(key = groupName) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = groupName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                fields.forEach { field ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = field.fieldKey,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.45f)
                                        )
                                        Text(
                                            text = field.fieldValue ?: "—",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.weight(0.55f)
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
}

@Composable
private fun IncidentsTab(
    sapId: String,
    incidents: List<app.telecom.sitetracker.data.entity.IncidentEntity>,
    onLogIncident: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Incident History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onLogIncident) {
                Text("Log Incident")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (incidents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No incidents recorded for $sapId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(incidents, key = { it.id }) { incident ->
                    val isOutage = incident.isOutage == 1
                    val isResolved = incident.status == "Resolved" || incident.status == "Closed"

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOutage && !isResolved) Color(0xFFFFF7ED) else MaterialTheme.colorScheme.surface
                        ),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Severity
                                    val sevColor = when (incident.severity) {
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
                                            text = incident.severity,
                                            style = MaterialTheme.typography.labelSmall.copy(color = sevColor, fontWeight = FontWeight.Bold)
                                        )
                                    }

                                    // Type badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isOutage) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isOutage) "OUTAGE" else "VISIT",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isOutage) SeverityCritical else MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }

                                    // Status badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isResolved) Color(0xFFDCFCE7) else Color(0xFFFEF3C7))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = incident.status,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isResolved) Color(0xFF15803D) else Color(0xFFB45309),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }

                                Text(
                                    text = Dates.formatDateTime(incident.occurredAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = incident.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Category: ${incident.category}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!incident.description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = incident.description,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            if (!incident.actionTaken.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Action: ${incident.actionTaken}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF15803D),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (!incident.engineer.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tech: ${incident.engineer}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun launchNavigation(context: Context, lat: Double, lng: Double, label: String) {
    val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
    val intent = Intent(Intent.ACTION_VIEW, geoUri)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // Fallback to browser Google Maps URL
        val webUri = Uri.parse("https://maps.google.com/?q=$lat,$lng")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open map", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun launchCall(context: Context, phone: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Could not dial phone", Toast.LENGTH_SHORT).show()
    }
}

private fun shareSiteDetails(
    context: Context,
    site: SiteEntity,
    groupedSpecs: Map<String, List<SiteFieldEntity>>
) {
    val sb = StringBuilder()
    sb.append("Site: ${site.sapId} (${site.name ?: ""})\n")
    if (site.lat != null && site.lng != null) {
        sb.append("GPS: ${site.lat}, ${site.lng}\n")
    }
    if (!site.circle.isNullOrBlank()) sb.append("Circle: ${site.circle}\n")
    if (!site.district.isNullOrBlank()) sb.append("District: ${site.district}\n")
    sb.append("\nEquipment Specs:\n")
    groupedSpecs.forEach { (group, fields) ->
        sb.append("[$group]\n")
        fields.forEach { f ->
            sb.append("• ${f.fieldKey}: ${f.fieldValue ?: "—"}\n")
        }
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Site Specifications: ${site.sapId}")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(intent, "Share Site Specifications"))
}

private fun Double.format(digits: Int): String = String.format(Locale.US, "%.${digits}f", this)

@Composable
private fun BeforeYouGoCard(
    count: Int,
    dominantCategory: String?,
    recommendation: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        border = BorderStroke(1.5.dp, SeverityCritical)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = SeverityCritical, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "BEFORE-YOU-GO REPEAT FAULT ALERT",
                    fontWeight = FontWeight.Bold,
                    color = SeverityCritical,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Site has experienced $count outages in the last 30 days (Frequent repeat fault).",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            if (!dominantCategory.isNullOrBlank()) {
                Text(
                    text = "Primary Root Cause: $dominantCategory",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!recommendation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Recommendation: $recommendation",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                )
            }
        }
    }
}

@Composable
private fun LiveOutageStatusBar(
    openOutage: IncidentEntity?,
    onMarkDown: () -> Unit,
    onRestore: () -> Unit
) {
    if (openOutage != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
            border = BorderStroke(1.dp, SeverityCritical)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(SeverityCritical)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SITE CURRENTLY DOWN",
                            fontWeight = FontWeight.Bold,
                            color = SeverityCritical,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    val now = System.currentTimeMillis()
                    val downAt = openOutage.downAt ?: openOutage.occurredAt
                    val durationHours = if (now > downAt) (now - downAt) / 3600000.0 else 0.0
                    Text(
                        text = "Elapsed: ${Dates.formatHours(durationHours)}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = SeverityCritical
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${openOutage.category}: ${openOutage.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onRestore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("1-Tap Restore Site Now", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
            border = BorderStroke(1.dp, Color(0xFF86EFAC))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF16A34A))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SITE OPERATIONAL",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF16A34A),
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Button(
                    onClick = onMarkDown,
                    colors = ButtonDefaults.buttonColors(containerColor = SeverityCritical),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Mark Down", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

