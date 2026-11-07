package app.telecom.sitetracker.data.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.ReasonEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.entity.SiteFts
import app.telecom.sitetracker.util.AppClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupData(
    val version: Int = 1,
    val appName: String = "TowerDost",
    val exportedAt: Long,
    val sites: List<SiteEntity> = emptyList(),
    val fields: List<SiteFieldEntity> = emptyList(),
    val incidents: List<IncidentEntity> = emptyList(),
    val reasons: List<ReasonEntity> = emptyList()
)

data class BackupRestoreResult(
    val sitesRestored: Int,
    val fieldsRestored: Int,
    val incidentsRestored: Int,
    val reasonsRestored: Int,
    val elapsedMs: Long
)

class BackupManager(
    private val db: AppDatabase,
    private val clock: AppClock
) {
    private val siteDao = db.siteDao()
    private val fieldDao = db.fieldDao()
    private val incidentDao = db.incidentDao()
    private val reasonDao = db.reasonDao()

    companion object {
        const val CURRENT_VERSION = 1
        const val APP_IDENTIFIER = "TowerDost"

        fun serializeBackupJson(data: BackupData): String {
            val root = JSONObject()
            root.put("version", data.version)
            root.put("appName", data.appName)
            root.put("exportedAt", data.exportedAt)

            // Sites Array
            val sitesArray = JSONArray()
            for (s in data.sites) {
                val obj = JSONObject()
                obj.put("sapId", s.sapId)
                obj.put("sapNorm", s.sapNorm)
                obj.putOpt("name", s.name)
                obj.putOpt("lat", s.lat)
                obj.putOpt("lng", s.lng)
                obj.putOpt("circle", s.circle)
                obj.putOpt("region", s.region)
                obj.putOpt("state", s.state)
                obj.putOpt("district", s.district)
                obj.putOpt("city", s.city)
                obj.putOpt("technology", s.technology)
                obj.putOpt("siteType", s.siteType)
                obj.putOpt("status", s.status)
                obj.putOpt("address", s.address)
                obj.putOpt("tags", s.tags)
                obj.putOpt("notes", s.notes)
                obj.putOpt("sourceFile", s.sourceFile)
                obj.putOpt("createdAt", s.createdAt)
                obj.putOpt("updatedAt", s.updatedAt)
                sitesArray.put(obj)
            }
            root.put("sites", sitesArray)

            // Fields Array
            val fieldsArray = JSONArray()
            for (f in data.fields) {
                val obj = JSONObject()
                obj.put("sapId", f.sapId)
                obj.put("fieldKey", f.fieldKey)
                obj.put("fieldValue", f.fieldValue)
                obj.putOpt("groupName", f.groupName)
                obj.putOpt("sortOrder", f.sortOrder)
                fieldsArray.put(obj)
            }
            root.put("fields", fieldsArray)

            // Incidents Array
            val incidentsArray = JSONArray()
            for (inc in data.incidents) {
                val obj = JSONObject()
                obj.put("id", inc.id)
                obj.put("sapId", inc.sapId)
                obj.put("occurredAt", inc.occurredAt)
                obj.put("category", inc.category)
                obj.put("severity", inc.severity)
                obj.put("status", inc.status)
                obj.put("isOutage", inc.isOutage)
                obj.putOpt("downAt", inc.downAt)
                obj.putOpt("restoredAt", inc.restoredAt)
                obj.put("title", inc.title)
                obj.putOpt("description", inc.description)
                obj.putOpt("actionTaken", inc.actionTaken)
                obj.putOpt("engineer", inc.engineer)
                obj.putOpt("ticketRef", inc.ticketRef)
                obj.putOpt("gpsLat", inc.gpsLat)
                obj.putOpt("gpsLng", inc.gpsLng)
                obj.putOpt("createdAt", inc.createdAt)
                obj.putOpt("updatedAt", inc.updatedAt)
                incidentsArray.put(obj)
            }
            root.put("incidents", incidentsArray)

            // Reasons Array
            val reasonsArray = JSONArray()
            for (r in data.reasons) {
                val obj = JSONObject()
                obj.put("id", r.id)
                obj.put("label", r.label)
                obj.putOpt("groupName", r.groupName)
                obj.putOpt("category", r.category)
                obj.put("countsTowardDowntime", r.countsTowardDowntime)
                obj.putOpt("sortOrder", r.sortOrder)
                obj.put("isHidden", r.isHidden)
                obj.put("isSeeded", r.isSeeded)
                obj.putOpt("updatedAt", r.updatedAt)
                reasonsArray.put(obj)
            }
            root.put("reasons", reasonsArray)

            return root.toString()
        }

        fun parseBackupJson(jsonString: String): BackupData {
            val root = JSONObject(jsonString)
            val app = root.optString("appName")
            if (app != APP_IDENTIFIER) {
                throw IllegalArgumentException("Invalid backup file: Not a TowerDost archive (found '$app').")
            }

            val version = root.optInt("version", 1)
            val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())

            val sites = mutableListOf<SiteEntity>()
            val sitesArray = root.optJSONArray("sites") ?: JSONArray()
            for (i in 0 until sitesArray.length()) {
                val obj = sitesArray.getJSONObject(i)
                sites.add(
                    SiteEntity(
                        sapId = obj.getString("sapId"),
                        sapNorm = obj.optString("sapNorm", SiteEntity.normalizeSapId(obj.getString("sapId"))),
                        name = if (obj.has("name")) obj.optString("name") else null,
                        lat = if (obj.has("lat")) obj.optDouble("lat") else null,
                        lng = if (obj.has("lng")) obj.optDouble("lng") else null,
                        circle = if (obj.has("circle")) obj.optString("circle") else null,
                        region = if (obj.has("region")) obj.optString("region") else null,
                        state = if (obj.has("state")) obj.optString("state") else null,
                        district = if (obj.has("district")) obj.optString("district") else null,
                        city = if (obj.has("city")) obj.optString("city") else null,
                        technology = if (obj.has("technology")) obj.optString("technology") else null,
                        siteType = if (obj.has("siteType")) obj.optString("siteType") else null,
                        status = if (obj.has("status")) obj.optString("status") else "Active",
                        address = if (obj.has("address")) obj.optString("address") else null,
                        tags = if (obj.has("tags")) obj.optString("tags") else null,
                        notes = if (obj.has("notes")) obj.optString("notes") else null,
                        sourceFile = if (obj.has("sourceFile")) obj.optString("sourceFile") else "BackupRestore",
                        createdAt = obj.optLong("createdAt", exportedAt),
                        updatedAt = obj.optLong("updatedAt", exportedAt)
                    )
                )
            }

            val fields = mutableListOf<SiteFieldEntity>()
            val fieldsArray = root.optJSONArray("fields") ?: JSONArray()
            for (i in 0 until fieldsArray.length()) {
                val obj = fieldsArray.getJSONObject(i)
                fields.add(
                    SiteFieldEntity(
                        sapId = obj.getString("sapId"),
                        fieldKey = obj.getString("fieldKey"),
                        fieldValue = obj.getString("fieldValue"),
                        groupName = if (obj.has("groupName")) obj.optString("groupName") else null,
                        sortOrder = if (obj.has("sortOrder")) obj.optInt("sortOrder") else null
                    )
                )
            }

            val incidents = mutableListOf<IncidentEntity>()
            val incidentsArray = root.optJSONArray("incidents") ?: JSONArray()
            for (i in 0 until incidentsArray.length()) {
                val obj = incidentsArray.getJSONObject(i)
                incidents.add(
                    IncidentEntity(
                        id = obj.getString("id"),
                        sapId = obj.getString("sapId"),
                        occurredAt = obj.getLong("occurredAt"),
                        category = obj.getString("category"),
                        severity = obj.getString("severity"),
                        status = obj.getString("status"),
                        isOutage = obj.optInt("isOutage", 0),
                        downAt = if (obj.has("downAt")) obj.optLong("downAt") else null,
                        restoredAt = if (obj.has("restoredAt")) obj.optLong("restoredAt") else null,
                        title = obj.getString("title"),
                        description = if (obj.has("description")) obj.optString("description") else null,
                        actionTaken = if (obj.has("actionTaken")) obj.optString("actionTaken") else null,
                        engineer = if (obj.has("engineer")) obj.optString("engineer") else null,
                        ticketRef = if (obj.has("ticketRef")) obj.optString("ticketRef") else null,
                        gpsLat = if (obj.has("gpsLat")) obj.optDouble("gpsLat") else null,
                        gpsLng = if (obj.has("gpsLng")) obj.optDouble("gpsLng") else null,
                        createdAt = obj.optLong("createdAt", exportedAt),
                        updatedAt = obj.optLong("updatedAt", exportedAt)
                    )
                )
            }

            val reasons = mutableListOf<ReasonEntity>()
            val reasonsArray = root.optJSONArray("reasons") ?: JSONArray()
            for (i in 0 until reasonsArray.length()) {
                val obj = reasonsArray.getJSONObject(i)
                reasons.add(
                    ReasonEntity(
                        id = obj.getString("id"),
                        label = obj.getString("label"),
                        groupName = if (obj.has("groupName")) obj.optString("groupName") else null,
                        category = if (obj.has("category")) obj.optString("category") else null,
                        countsTowardDowntime = obj.optInt("countsTowardDowntime", 1),
                        sortOrder = if (obj.has("sortOrder") && !obj.isNull("sortOrder")) obj.optInt("sortOrder") else null,
                        isHidden = obj.optInt("isHidden", 0),
                        isSeeded = obj.optInt("isSeeded", 0),
                        updatedAt = if (obj.has("updatedAt") && !obj.isNull("updatedAt")) obj.optLong("updatedAt") else null
                    )
                )
            }

            return BackupData(
                version = version,
                appName = app,
                exportedAt = exportedAt,
                sites = sites,
                fields = fields,
                incidents = incidents,
                reasons = reasons
            )
        }
    }

    suspend fun createFullBackup(): BackupData = withContext(Dispatchers.IO) {
        val now = clock.currentTimeMillis()
        val sites = siteDao.getAllSitesForBackup()
        val fields = fieldDao.getAllFields()
        val incidents = incidentDao.getAllIncidentsForBackup()
        val reasons = reasonDao.getAllReasonsList()

        BackupData(
            version = CURRENT_VERSION,
            appName = APP_IDENTIFIER,
            exportedAt = now,
            sites = sites,
            fields = fields,
            incidents = incidents,
            reasons = reasons
        )
    }

    suspend fun exportBackupToFile(context: Context): File = withContext(Dispatchers.IO) {
        val backupData = createFullBackup()
        val json = serializeBackupJson(backupData)

        val dir = File(context.cacheDir, "backups")
        dir.mkdirs()
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(dir, "TowerDost_Backup_$dateStamp.json")

        FileWriter(file).use { it.write(json) }
        file
    }

    suspend fun restoreFromBackupJson(jsonString: String, cleanOverwrite: Boolean): BackupRestoreResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val data = parseBackupJson(jsonString)

        db.withTransaction {
            if (cleanOverwrite) {
                siteDao.deleteAllSites()
                fieldDao.deleteAllFields()
                incidentDao.deleteAllIncidents()
                siteDao.clearFts()
            }

            if (data.sites.isNotEmpty()) {
                siteDao.insertSites(data.sites)
            }
            if (data.fields.isNotEmpty()) {
                fieldDao.insertFields(data.fields)
            }
            if (data.incidents.isNotEmpty()) {
                incidentDao.insertIncidents(data.incidents)
            }
            if (data.reasons.isNotEmpty()) {
                reasonDao.insertReasons(data.reasons)
            }

            // Rebuild FTS
            val ftsList = data.sites.map { s ->
                SiteFts(
                    sapNorm = s.sapNorm,
                    name = s.name,
                    address = s.address ?: s.city ?: s.district,
                    fieldText = "${s.technology ?: ""} ${s.siteType ?: ""}"
                )
            }
            if (ftsList.isNotEmpty()) {
                siteDao.insertFts(ftsList)
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        BackupRestoreResult(
            sitesRestored = data.sites.size,
            fieldsRestored = data.fields.size,
            incidentsRestored = data.incidents.size,
            reasonsRestored = data.reasons.size,
            elapsedMs = elapsed
        )
    }

    fun shareBackupFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "TowerDost Full Database Backup")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share TowerDost Backup via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
