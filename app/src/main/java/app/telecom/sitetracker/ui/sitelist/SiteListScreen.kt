package app.telecom.sitetracker.ui.sitelist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import app.telecom.sitetracker.R
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.ui.components.EnterpriseListRow
import app.telecom.sitetracker.ui.components.EnterpriseTopBar
import app.telecom.sitetracker.ui.components.SearchCapsule
import app.telecom.sitetracker.ui.components.StatusDotBadge
import app.telecom.sitetracker.ui.theme.SapIdStyle
import app.telecom.sitetracker.ui.theme.StatusDownRed
import app.telecom.sitetracker.ui.theme.StatusRestoredGreen
import app.telecom.sitetracker.ui.theme.TelecomAmber
import app.telecom.sitetracker.ui.theme.TelemetryCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteListScreen(
    viewModel: SiteListViewModel,
    onSiteClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onImportClick: () -> Unit,
    onSeedSampleData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagedSites = viewModel.pagedSites.collectAsLazyPagingItems()
    val activeCount by viewModel.activeSitesCount.collectAsStateWithLifecycle(initialValue = 0)
    val context = LocalContext.current

    Scaffold(
        topBar = {
            EnterpriseTopBar(
                title = stringResource(R.string.site_list_title),
                subtitle = "$activeCount active towers",
                actions = {
                    IconButton(onClick = onImportClick) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = "Import file",
                            tint = TelecomAmber
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Instant Search Trigger Capsule
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable(onClick = onSearchClick)
            ) {
                SearchCapsule(
                    query = "",
                    onQueryChange = {},
                    placeholder = "Search by SAP ID, site name, JC...",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    pagedSites.loadState.refresh is LoadState.Loading -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading sites directory...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    pagedSites.itemCount == 0 && pagedSites.loadState.refresh !is LoadState.Loading -> {
                        EmptySitesView(
                            onImportClick = onImportClick,
                            onSeedClick = onSeedSampleData
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = 4.dp,
                                bottom = 80.dp
                            )
                        ) {
                            items(
                                count = pagedSites.itemCount,
                                key = { index ->
                                    val item = pagedSites[index]
                                    if (item != null) "${item.sapId}_$index" else index
                                }
                            ) { index ->
                                val site = pagedSites[index]
                                if (site != null) {
                                    SiteCard(
                                        site = site,
                                        onClick = { onSiteClick(site.sapId) },
                                        onCopyCoordinates = { lat, lng ->
                                            copyToClipboard(context, "Coordinates", "$lat, $lng")
                                        }
                                    )
                                }
                            }

                            if (pagedSites.loadState.append is LoadState.Loading) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
fun SiteCard(
    site: SiteEntity,
    onClick: () -> Unit,
    onCopyCoordinates: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    EnterpriseListRow(
        onClick = onClick,
        modifier = modifier
    ) {
        // Row 1: SAP ID + Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = site.sapId,
                style = SapIdStyle.copy(fontSize = 16.sp, color = TelecomAmber)
            )

            val statusText = site.status ?: "Active"
            val isDown = statusText.equals("down", ignoreCase = true)
            val badgeColor = if (isDown) StatusDownRed else StatusRestoredGreen

            StatusDotBadge(text = statusText, color = badgeColor)
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Row 2: Name & Circle / District
        if (!site.name.isNullOrBlank()) {
            Text(
                text = site.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        val locationParts = listOfNotNull(site.city, site.district, site.circle)
        if (locationParts.isNotEmpty()) {
            Text(
                text = locationParts.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Row 3: Coordinates + Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (site.lat != null && site.lng != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onCopyCoordinates(site.lat, site.lng) }
                ) {
                    Text(
                        text = "${site.lat.format(4)}, ${site.lng.format(4)}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy coordinates",
                        modifier = Modifier.size(12.dp),
                        tint = TelemetryCyan
                    )
                }
            } else {
                Text(
                    text = "No GPS recorded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!site.technology.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = site.technology,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (!site.siteType.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = site.siteType,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
        }
    }
}

@Composable
private fun EmptySitesView(
    onImportClick: () -> Unit,
    onSeedClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CellTower,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = TelecomAmber
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Sites Found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_sites_found),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onImportClick,
            colors = ButtonDefaults.buttonColors(containerColor = TelecomAmber),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Import Excel / CSV Master", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onSeedClick,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Load Sample Dataset")
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

private fun Double.format(digits: Int): String = String.format(java.util.Locale.US, "%.${digits}f", this)
