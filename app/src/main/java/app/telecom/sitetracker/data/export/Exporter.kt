package app.telecom.sitetracker.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.SiteEntity
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
     * Exports all incidents / outages into a standard CSV file and saves to app cache.
     */
    fun exportRegisterCsv(context: Context, incidents: List<IncidentEntity>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(exportDir, "TowerDost_Register_$dateStamp.csv")

        FileWriter(file).use { writer ->
            // CSV Header
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

    /**
     * Exports master sites to CSV.
     */
    fun exportSitesCsv(context: Context, sites: List<SiteEntity>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(exportDir, "TowerDost_MasterSites_$dateStamp.csv")

        FileWriter(file).use { writer ->
            writer.append("SAP ID,Site Name,Circle,District,State,Latitude,Longitude,Address\n")

            for (site in sites) {
                val row = listOf(
                    escapeCsv(site.sapId),
                    escapeCsv(site.name),
                    escapeCsv(site.circle),
                    escapeCsv(site.district),
                    escapeCsv(site.state),
                    escapeCsv(site.lat?.toString()),
                    escapeCsv(site.lng?.toString()),
                    escapeCsv(site.address)
                ).joinToString(",")

                writer.append(row).append("\n")
            }
        }

        return file
    }

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
