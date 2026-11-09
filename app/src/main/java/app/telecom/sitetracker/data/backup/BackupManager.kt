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
import app.telecom.sitetracker.data.entity.SiteTabEntity
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import app.telecom.sitetracker.util.AppClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
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
    val reasons: List<ReasonEntity> = emptyList(),
    val tabs: List<SiteTabEntity> = emptyList(),
    val universalFields: List<UniversalFieldEntity> = emptyList()
)

data class BackupRestoreResult(
    val sitesRestored: Int,
    val fieldsRestored: Int,
    val incidentsRestored: Int,
    val reasonsRestored: Int,
    val elapsedMs: Long,
    val tabsRestored: Int = 0,
    val universalFieldsRestored: Int = 0
)

class BackupManager(
    private val db: AppDatabase,
    private val clock: AppClock
) {
    private val siteDao = db.siteDao()
    private val fieldDao = db.fieldDao()
    private val incidentDao = db.incidentDao()
    private val reasonDao = db.reasonDao()
    private val siteTabDao = db.siteTabDao()
    private val universalFieldDao = db.universalFieldDao()

    companion object {
        const val CURRENT_VERSION = 1
        const val APP_IDENTIFIER = "TowerDost"

        fun serializeBackupJson(data: BackupData): String {
            val root = buildJsonObject {
                put("version", data.version)
                put("appName", data.appName)
                put("exportedAt", data.exportedAt)

                put("sites", buildJsonArray {
                    for (s in data.sites) {
                        add(buildJsonObject {
                            put("sapId", s.sapId)
                            put("sapNorm", s.sapNorm)
                            s.name?.let { put("name", it) }
                            s.lat?.let { put("lat", it) }
                            s.lng?.let { put("lng", it) }
                            s.circle?.let { put("circle", it) }
                            s.region?.let { put("region", it) }
                            s.state?.let { put("state", it) }
                            s.district?.let { put("district", it) }
                            s.city?.let { put("city", it) }
                            s.technology?.let { put("technology", it) }
                            s.siteType?.let { put("siteType", it) }
                            s.status?.let { put("status", it) }
                            s.address?.let { put("address", it) }
                            s.tags?.let { put("tags", it) }
                            s.notes?.let { put("notes", it) }
                            s.sourceFile?.let { put("sourceFile", it) }
                            s.createdAt?.let { put("createdAt", it) }
                            s.updatedAt?.let { put("updatedAt", it) }
                        })
                    }
                })

                put("fields", buildJsonArray {
                    for (f in data.fields) {
                        add(buildJsonObject {
                            put("sapId", f.sapId)
                            put("fieldKey", f.fieldKey)
                            put("fieldValue", f.fieldValue ?: "")
                            f.groupName?.let { put("groupName", it) }
                            f.sortOrder?.let { put("sortOrder", it) }
                            put("tabId", f.tabId)
                        })
                    }
                })

                put("incidents", buildJsonArray {
                    for (inc in data.incidents) {
                        add(buildJsonObject {
                            put("id", inc.id)
                            put("sapId", inc.sapId)
                            put("occurredAt", inc.occurredAt)
                            put("category", inc.category)
                            put("severity", inc.severity)
                            put("status", inc.status)
                            put("isOutage", inc.isOutage)
                            inc.downAt?.let { put("downAt", it) }
                            inc.restoredAt?.let { put("restoredAt", it) }
                            put("title", inc.title)
                            inc.description?.let { put("description", it) }
                            inc.actionTaken?.let { put("actionTaken", it) }
                            inc.engineer?.let { put("engineer", it) }
                            inc.ticketRef?.let { put("ticketRef", it) }
                            inc.gpsLat?.let { put("gpsLat", it) }
                            inc.gpsLng?.let { put("gpsLng", it) }
                            inc.createdAt?.let { put("createdAt", it) }
                            inc.updatedAt?.let { put("updatedAt", it) }
                        })
                    }
                })

                put("reasons", buildJsonArray {
                    for (r in data.reasons) {
                        add(buildJsonObject {
                            put("id", r.id)
                            put("label", r.label)
                            r.groupName?.let { put("groupName", it) }
                            r.category?.let { put("category", it) }
                            put("countsTowardDowntime", r.countsTowardDowntime)
                            r.sortOrder?.let { put("sortOrder", it) }
                            put("isHidden", r.isHidden)
                            put("isSeeded", r.isSeeded)
                            r.updatedAt?.let { put("updatedAt", it) }
                        })
                    }
                })

                put("tabs", buildJsonArray {
                    for (t in data.tabs) {
                        add(buildJsonObject {
                            put("tabId", t.tabId)
                            put("displayName", t.displayName)
                            put("sortOrder", t.sortOrder)
                            put("isSystem", t.isSystem)
                        })
                    }
                })

                put("universalFields", buildJsonArray {
                    for (uf in data.universalFields) {
                        add(buildJsonObject {
                            put("tabId", uf.tabId)
                            put("fieldKey", uf.fieldKey)
                            put("groupName", uf.groupName)
                            uf.defaultValue?.let { put("defaultValue", it) }
                            put("sortOrder", uf.sortOrder)
                        })
                    }
                })
            }
            return root.toString()
        }

        fun parseBackupJson(jsonString: String): BackupData {
            val root = try {
                Json.parseToJsonElement(jsonString).jsonObject
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid backup JSON format: ${e.message}", e)
            }

            val app = root["appName"]?.jsonPrimitive?.contentOrNull ?: ""
            if (app != APP_IDENTIFIER) {
                throw IllegalArgumentException("Invalid backup file: Not a TowerDost archive (found '$app').")
            }

            val version = root["version"]?.jsonPrimitive?.intOrNull ?: 1
            val exportedAt = root["exportedAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

            val sites = mutableListOf<SiteEntity>()
            root["sites"]?.jsonArray?.forEach { el ->
                val obj = el.jsonObject
                val sapId = obj["sapId"]?.jsonPrimitive?.content ?: return@forEach
                sites.add(
                    SiteEntity(
                        sapId = sapId,
                        sapNorm = obj["sapNorm"]?.jsonPrimitive?.contentOrNull ?: SiteEntity.normalizeSapId(sapId),
                        name = obj["name"]?.jsonPrimitive?.contentOrNull,
                        lat = obj["lat"]?.jsonPrimitive?.doubleOrNull,
                        lng = obj["lng"]?.jsonPrimitive?.doubleOrNull,
                        circle = obj["circle"]?.jsonPrimitive?.contentOrNull,
                        region = obj["region"]?.jsonPrimitive?.contentOrNull,
                        state = obj["state"]?.jsonPrimitive?.contentOrNull,
                        district = obj["district"]?.jsonPrimitive?.contentOrNull,
                        city = obj["city"]?.jsonPrimitive?.contentOrNull,
                        technology = obj["technology"]?.jsonPrimitive?.contentOrNull,
                        siteType = obj["siteType"]?.jsonPrimitive?.contentOrNull,
                        status = obj["status"]?.jsonPrimitive?.contentOrNull ?: "Active",
                        address = obj["address"]?.jsonPrimitive?.contentOrNull,
                        tags = obj["tags"]?.jsonPrimitive?.contentOrNull,
                        notes = obj["notes"]?.jsonPrimitive?.contentOrNull,
                        sourceFile = obj["sourceFile"]?.jsonPrimitive?.contentOrNull ?: "BackupRestore",
                        createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: exportedAt,
                        updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: exportedAt
                    )
                )
            }

            val fields = mutableListOf<SiteFieldEntity>()
            root["fields"]?.jsonArray?.forEach { el ->
                val obj = el.jsonObject
                val sapId = obj["sapId"]?.jsonPrimitive?.content ?: return@forEach
                val fieldKey = obj["fieldKey"]?.jsonPrimitive?.content ?: return@forEach
                val tabId = obj["tabId"]?.jsonPrimitive?.contentOrNull ?: "SPECS"
                fields.add(
                    SiteFieldEntity(
                        sapId = sapId,
                        fieldKey = fieldKey,
                        fieldValue = obj["fieldValue"]?.jsonPrimitive?.contentOrNull,
                        groupName = obj["groupName"]?.jsonPrimitive?.contentOrNull,
                        sortOrder = obj["sortOrder"]?.jsonPrimitive?.intOrNull,
                        tabId = tabId
                    )
                )
            }

            val incidents = mutableListOf<IncidentEntity>()
            root["incidents"]?.jsonArray?.forEach { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@forEach
                val sapId = obj["sapId"]?.jsonPrimitive?.content ?: return@forEach
                val occurredAt = obj["occurredAt"]?.jsonPrimitive?.longOrNull ?: exportedAt
                val title = obj["title"]?.jsonPrimitive?.content ?: ""
                incidents.add(
                    IncidentEntity(
                        id = id,
                        sapId = sapId,
                        occurredAt = occurredAt,
                        category = obj["category"]?.jsonPrimitive?.content ?: "Incident",
                        severity = obj["severity"]?.jsonPrimitive?.content ?: "Minor",
                        status = obj["status"]?.jsonPrimitive?.content ?: "Closed",
                        isOutage = obj["isOutage"]?.jsonPrimitive?.intOrNull ?: 0,
                        downAt = obj["downAt"]?.jsonPrimitive?.longOrNull,
                        restoredAt = obj["restoredAt"]?.jsonPrimitive?.longOrNull,
                        title = title,
                        description = obj["description"]?.jsonPrimitive?.contentOrNull,
                        actionTaken = obj["actionTaken"]?.jsonPrimitive?.contentOrNull,
                        engineer = obj["engineer"]?.jsonPrimitive?.contentOrNull,
                        ticketRef = obj["ticketRef"]?.jsonPrimitive?.contentOrNull,
                        gpsLat = obj["gpsLat"]?.jsonPrimitive?.doubleOrNull,
                        gpsLng = obj["gpsLng"]?.jsonPrimitive?.doubleOrNull,
                        createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: exportedAt,
                        updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: exportedAt
                    )
                )
            }

            val reasons = mutableListOf<ReasonEntity>()
            root["reasons"]?.jsonArray?.forEach { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@forEach
                val label = obj["label"]?.jsonPrimitive?.content ?: ""
                reasons.add(
                    ReasonEntity(
                        id = id,
                        label = label,
                        groupName = obj["groupName"]?.jsonPrimitive?.contentOrNull,
                        category = obj["category"]?.jsonPrimitive?.contentOrNull,
                        countsTowardDowntime = obj["countsTowardDowntime"]?.jsonPrimitive?.intOrNull ?: 1,
                        sortOrder = obj["sortOrder"]?.jsonPrimitive?.intOrNull,
                        isHidden = obj["isHidden"]?.jsonPrimitive?.intOrNull ?: 0,
                        isSeeded = obj["isSeeded"]?.jsonPrimitive?.intOrNull ?: 0,
                        updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull
                    )
                )
            }

            val tabs = mutableListOf<SiteTabEntity>()
            root["tabs"]?.jsonArray?.forEach { el ->
                val obj = el.jsonObject
                val tabId = obj["tabId"]?.jsonPrimitive?.content ?: return@forEach
                val displayName = obj["displayName"]?.jsonPrimitive?.content ?: tabId
                val sortOrder = obj["sortOrder"]?.jsonPrimitive?.intOrNull ?: 0
                val isSystem = obj["isSystem"]?.jsonPrimitive?.let { prim ->
                    prim.booleanOrNull ?: (prim.intOrNull == 1) ?: (prim.contentOrNull.equals("true", ignoreCase = true))
                } ?: false
                tabs.add(
                    SiteTabEntity(
                        tabId = tabId,
                        displayName = displayName,
                        sortOrder = sortOrder,
                        isSystem = isSystem
                    )
                )
            }

            val universalFields = mutableListOf<UniversalFieldEntity>()
            root["universalFields"]?.jsonArray?.forEach { el ->
                val obj = el.jsonObject
                val fieldKey = obj["fieldKey"]?.jsonPrimitive?.content ?: return@forEach
                val groupName = obj["groupName"]?.jsonPrimitive?.content ?: "General"
                val defaultValue = obj["defaultValue"]?.jsonPrimitive?.contentOrNull
                val sortOrder = obj["sortOrder"]?.jsonPrimitive?.intOrNull ?: 0
                val tabId = obj["tabId"]?.jsonPrimitive?.contentOrNull ?: "SPECS"
                universalFields.add(
                    UniversalFieldEntity(
                        tabId = tabId,
                        fieldKey = fieldKey,
                        groupName = groupName,
                        defaultValue = defaultValue,
                        sortOrder = sortOrder
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
                reasons = reasons,
                tabs = tabs,
                universalFields = universalFields
            )
        }

        fun deserializeBackupJson(jsonString: String): BackupData = parseBackupJson(jsonString)
    }

    suspend fun createFullBackup(): BackupData = withContext(Dispatchers.IO) {
        val now = clock.currentTimeMillis()
        val sites = siteDao.getAllSitesForBackup()
        val fields = fieldDao.getAllFields()
        val incidents = incidentDao.getAllIncidentsForBackup()
        val reasons = reasonDao.getAllReasonsList()
        val tabs = siteTabDao.getAllTabs()
        val universalFields = universalFieldDao.getAllUniversalFields()

        BackupData(
            version = CURRENT_VERSION,
            appName = APP_IDENTIFIER,
            exportedAt = now,
            sites = sites,
            fields = fields,
            incidents = incidents,
            reasons = reasons,
            tabs = tabs,
            universalFields = universalFields
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

    suspend fun generateAndVerifySafetyBackup(
        context: Context,
        tag: String = "pre_deletion"
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val backupData = createFullBackup()
            val json = serializeBackupJson(backupData)

            val dir = File(context.filesDir, "backups")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val dateStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(clock.currentTimeMillis()))
            val file = File(dir, "TowerDost_${tag}_$dateStamp.json")

            FileWriter(file).use { writer ->
                writer.write(json)
                writer.flush()
            }

            if (file.exists() && file.length() > 0L) {
                Result.success(file)
            } else {
                Result.failure(IllegalStateException("Backup file verification failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
                universalFieldDao.clearAll()
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
            if (data.tabs.isNotEmpty()) {
                siteTabDao.insertTabs(data.tabs)
            }
            if (data.universalFields.isNotEmpty()) {
                universalFieldDao.insertOrUpdateFields(data.universalFields)
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
            tabsRestored = data.tabs.size,
            universalFieldsRestored = data.universalFields.size,
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
