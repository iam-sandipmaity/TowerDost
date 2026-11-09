package app.telecom.sitetracker.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.util.Dates
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Exporter {

    private fun escapeCsv(value: String?): String {
        if (value == null) return ""
        var s = value.replace("\r", " ").replace("\n", " ")
        if (s.contains(",") || s.contains("\"")) {
            s = "\"" + s.replace("\"", "\"\"") + "\""
        }
        return s
    }

    /**
     * Exports all incidents / outages into a standard CSV file.
     */
    fun exportRegisterCsv(outputDir: File, incidents: List<IncidentEntity>): File {
        outputDir.mkdirs()
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(outputDir, "TowerDost_Register_$dateStamp.csv")

        FileWriter(file).use { writer ->
            writer.append("SAP ID,Title,Type,Severity,Category,Status,Down At (IST),Restored At (IST),Downtime Hours,Engineer,Ticket Ref,Action Taken,Description,GPS Lat,GPS Lng\n")

            for (item in incidents) {
                val isOutage = item.isOutage == 1
                val type = if (isOutage) "Outage" else "Visit/Maintenance"

                val downtimeHours = if (item.downAt != null) {
                    val end = item.restoredAt ?: System.currentTimeMillis()
                    if (end >= item.downAt) "%.2f".format((end - item.downAt) / 3600000.0) else "0.0"
                } else ""

                val row = listOf(
                    escapeCsv(item.sapId),
                    escapeCsv(item.title),
                    escapeCsv(type),
                    escapeCsv(item.severity),
                    escapeCsv(item.category),
                    escapeCsv(item.status),
                    escapeCsv(Dates.formatDateTime(item.downAt ?: item.occurredAt)),
                    escapeCsv(Dates.formatDateTime(item.restoredAt)),
                    escapeCsv(downtimeHours),
                    escapeCsv(item.engineer),
                    escapeCsv(item.ticketRef),
                    escapeCsv(item.actionTaken),
                    escapeCsv(item.description),
                    escapeCsv(item.gpsLat?.toString()),
                    escapeCsv(item.gpsLng?.toString())
                ).joinToString(",")

                writer.append(row).append("\n")
            }
        }

        return file
    }

    fun exportRegisterCsv(context: Context, incidents: List<IncidentEntity>): File =
        exportRegisterCsv(File(context.cacheDir, "exports"), incidents)

    /**
     * Exports master sites and dynamic equipment specifications to CSV.
     */
    fun exportSitesCsv(
        outputDir: File,
        sites: List<SiteEntity>,
        allFields: List<SiteFieldEntity> = emptyList()
    ): File {
        outputDir.mkdirs()
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(outputDir, "TowerDost_MasterSites_$dateStamp.csv")

        data class ColumnSpec(val tabId: String, val fieldKey: String, val headerName: String)

        val distinctColumns = allFields.map { field ->
            val isCustomTab = field.tabId.isNotBlank() && !field.tabId.equals("SPECS", ignoreCase = true)
            val header = if (isCustomTab) {
                "[${field.tabId.replace("_", " ").uppercase(Locale.US)}] ${field.fieldKey}"
            } else {
                field.fieldKey
            }
            ColumnSpec(field.tabId, field.fieldKey, header)
        }.distinctBy { it.headerName }.sortedWith(
            compareBy(
                { if (it.tabId.equals("SPECS", ignoreCase = true)) 0 else 1 },
                { it.headerName }
            )
        )

        FileWriter(file).use { writer ->
            val headers = mutableListOf(
                "SAP ID",
                "Site Name",
                "Circle",
                "Region",
                "District",
                "City",
                "State",
                "Technology",
                "Site Type",
                "Status",
                "Latitude",
                "Longitude",
                "Address",
                "Remarks",
                "Last Updated"
            )
            headers.addAll(distinctColumns.map { it.headerName })
            writer.append(headers.joinToString(",")).append("\n")

            // Index fields by sapId -> headerName -> fieldValue for O(1) row assembly
            val fieldsBySite = allFields.groupBy { it.sapId }

            for (site in sites) {
                val siteFields = fieldsBySite[site.sapId] ?: emptyList()
                val siteSpecsMap = siteFields.associate { field ->
                    val isCustomTab = field.tabId.isNotBlank() && !field.tabId.equals("SPECS", ignoreCase = true)
                    val header = if (isCustomTab) {
                        "[${field.tabId.replace("_", " ").uppercase(Locale.US)}] ${field.fieldKey}"
                    } else {
                        field.fieldKey
                    }
                    header to field.fieldValue
                }

                val rowValues = mutableListOf(
                    escapeCsv(site.sapId),
                    escapeCsv(site.name),
                    escapeCsv(site.circle),
                    escapeCsv(site.region),
                    escapeCsv(site.district),
                    escapeCsv(site.city),
                    escapeCsv(site.state),
                    escapeCsv(site.technology),
                    escapeCsv(site.siteType),
                    escapeCsv(site.status),
                    escapeCsv(site.lat?.toString()),
                    escapeCsv(site.lng?.toString()),
                    escapeCsv(site.address),
                    escapeCsv(site.notes),
                    escapeCsv(if (site.updatedAt != null) Dates.formatDateTime(site.updatedAt) else "")
                )

                for (col in distinctColumns) {
                    rowValues.add(escapeCsv(siteSpecsMap[col.headerName]))
                }

                writer.append(rowValues.joinToString(",")).append("\n")
            }
        }

        return file
    }

    fun exportSitesCsv(
        context: Context,
        sites: List<SiteEntity>,
        allFields: List<SiteFieldEntity> = emptyList()
    ): File = exportSitesCsv(File(context.cacheDir, "exports"), sites, allFields)

    /**
     * Exports a comprehensive individual site report (Overview, Specs, Incident History).
     */
    fun exportSingleSiteCsv(
        outputDir: File,
        site: SiteEntity,
        specs: List<SiteFieldEntity>,
        incidents: List<IncidentEntity>
    ): File {
        outputDir.mkdirs()
        val safeSap = site.sapId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(outputDir, "Site_${safeSap}_$dateStamp.csv")

        FileWriter(file).use { writer ->
            // Section 1: Site Overview
            writer.append("=== SITE OVERVIEW: ${site.sapId} ===\n")
            writer.append("Field,Value\n")
            writer.append("SAP ID,${escapeCsv(site.sapId)}\n")
            writer.append("Site Name,${escapeCsv(site.name)}\n")
            writer.append("Circle,${escapeCsv(site.circle)}\n")
            writer.append("Region,${escapeCsv(site.region)}\n")
            writer.append("District,${escapeCsv(site.district)}\n")
            writer.append("City / JC,${escapeCsv(site.city)}\n")
            writer.append("State,${escapeCsv(site.state)}\n")
            writer.append("Technology,${escapeCsv(site.technology)}\n")
            writer.append("Site Type,${escapeCsv(site.siteType)}\n")
            writer.append("Status,${escapeCsv(site.status)}\n")
            writer.append("Latitude,${escapeCsv(site.lat?.toString())}\n")
            writer.append("Longitude,${escapeCsv(site.lng?.toString())}\n")
            writer.append("Address,${escapeCsv(site.address)}\n")
            writer.append("Remarks,${escapeCsv(site.notes)}\n")
            writer.append("Last Updated,${escapeCsv(if (site.updatedAt != null) Dates.formatDateTime(site.updatedAt) else "")}\n\n")

            // Section 2: Technical Specifications & Site Tabs
            writer.append("=== TECHNICAL SPECIFICATIONS & SITE TABS ===\n")
            writer.append("Tab,Group,Specification Name,Value\n")
            for (spec in specs.sortedWith(compareBy({ if (it.tabId.equals("SPECS", ignoreCase = true)) 0 else 1 }, { it.tabId }, { it.sortOrder ?: 0 }))) {
                val tabLabel = (if (spec.tabId.isNotBlank()) spec.tabId else "SPECS").replace("_", " ").uppercase(Locale.US)
                writer.append("${escapeCsv(tabLabel)},${escapeCsv(spec.groupName ?: "GENERAL")},${escapeCsv(spec.fieldKey)},${escapeCsv(spec.fieldValue)}\n")
            }
            writer.append("\n")

            // Section 3: Incident & Activity History
            writer.append("=== INCIDENT & ACTIVITY HISTORY ===\n")
            writer.append("Log ID,Date (IST),Type,Category,Severity,Status,Title,Engineer,Action Taken,Description\n")
            for (inc in incidents) {
                val isOutage = inc.isOutage == 1
                val type = if (isOutage) "Outage" else "Activity / Note"
                val row = listOf(
                    escapeCsv(inc.id),
                    escapeCsv(Dates.formatDateTime(inc.downAt ?: inc.occurredAt)),
                    escapeCsv(type),
                    escapeCsv(inc.category),
                    escapeCsv(inc.severity),
                    escapeCsv(inc.status),
                    escapeCsv(inc.title),
                    escapeCsv(inc.engineer),
                    escapeCsv(inc.actionTaken),
                    escapeCsv(inc.description)
                ).joinToString(",")
                writer.append(row).append("\n")
            }
        }

        return file
    }

    fun exportSingleSiteCsv(
        context: Context,
        site: SiteEntity,
        specs: List<SiteFieldEntity>,
        incidents: List<IncidentEntity>
    ): File = exportSingleSiteCsv(File(context.cacheDir, "exports"), site, specs, incidents)

    /**
     * Launches Android Share sheet via FileProvider to send or save CSV file.
     */
    fun shareCsvFile(context: Context, file: File, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Export / Share via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
