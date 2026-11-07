package app.telecom.sitetracker.ui.sitelist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.site_list_title), fontWeight = FontWeight.Bold)
                        Text(
                            text = "$activeCount active sites",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Search sites")
                    }
                    IconButton(onClick = onImportClick) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Import file")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Instant Search Trigger Bar
            Card(
                onClick = onSearchClick,
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Search by SAP ID, site name, JC...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                        CircularProgressIndicator()
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
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 80.dp
                        )
                    ) {
                        items(
                            count = pagedSites.itemCount,
                            key = { index ->
                                val item = pagedSites[index]
                                item?.sapId ?: index
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
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Row 1: SAP ID + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = site.sapId,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                val statusText = site.status ?: "Active"
                val (containerColor, contentColor) = when (statusText.lowercase()) {
                    "active" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    "down" -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
                    else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = statusText.uppercase(),
                        color = contentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Name & Circle / District
            if (!site.name.isNullOrBlank()) {
                Text(
                    text = site.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
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

            Spacer(modifier = Modifier.height(8.dp))

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
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy coordinates",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    Text(
                        text = "No GPS recorded",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!site.technology.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = site.technology,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (!site.siteType.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = site.siteType,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
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
            tint = MaterialTheme.colorScheme.primary
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
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("Import Excel / CSV Master")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onSeedClick,
            modifier = Modifier.fillMaxWidth(0.8f)
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
