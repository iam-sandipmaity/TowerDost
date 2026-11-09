package app.telecom.sitetracker

import android.content.Context
import android.content.ContextWrapper
import androidx.sqlite.db.SupportSQLiteDatabase
import app.telecom.sitetracker.data.backup.BackupData
import app.telecom.sitetracker.data.backup.BackupManager
import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.db.FieldDao
import app.telecom.sitetracker.data.db.IncidentDao
import app.telecom.sitetracker.data.db.ReasonDao
import app.telecom.sitetracker.data.db.SiteDao
import app.telecom.sitetracker.data.db.SiteTabDao
import app.telecom.sitetracker.data.db.UniversalFieldDao
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.ReasonEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.entity.SiteTabEntity
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import app.telecom.sitetracker.util.FakeAppClock
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabDeletionBackupTest {

    private class TestContextWrapper(private val filesDirectory: File) : ContextWrapper(null) {
        override fun getFilesDir(): File = filesDirectory
        override fun getCacheDir(): File = filesDirectory
    }

    private fun createFakeDatabase(
        sites: List<SiteEntity> = emptyList(),
        fields: List<SiteFieldEntity> = emptyList(),
        incidents: List<IncidentEntity> = emptyList(),
        reasons: List<ReasonEntity> = emptyList(),
        tabs: List<SiteTabEntity> = emptyList(),
        universalFields: List<UniversalFieldEntity> = emptyList()
    ): AppDatabase {
        val fakeSiteDao = Proxy.newProxyInstance(
            SiteDao::class.java.classLoader,
            arrayOf(SiteDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAllSitesForBackup" -> sites
                else -> null
            }
        } as SiteDao

        val fakeFieldDao = Proxy.newProxyInstance(
            FieldDao::class.java.classLoader,
            arrayOf(FieldDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAllFields" -> fields
                else -> null
            }
        } as FieldDao

        val fakeIncidentDao = Proxy.newProxyInstance(
            IncidentDao::class.java.classLoader,
            arrayOf(IncidentDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAllIncidentsForBackup" -> incidents
                else -> null
            }
        } as IncidentDao

        val fakeReasonDao = Proxy.newProxyInstance(
            ReasonDao::class.java.classLoader,
            arrayOf(ReasonDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAllReasonsList" -> reasons
                else -> null
            }
        } as ReasonDao

        val fakeTabDao = Proxy.newProxyInstance(
            SiteTabDao::class.java.classLoader,
            arrayOf(SiteTabDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAllTabs" -> tabs
                else -> null
            }
        } as SiteTabDao

        val fakeUniversalDao = Proxy.newProxyInstance(
            UniversalFieldDao::class.java.classLoader,
            arrayOf(UniversalFieldDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAllUniversalFields" -> universalFields
                else -> null
            }
        } as UniversalFieldDao

        return object : AppDatabase() {
            override fun siteDao(): SiteDao = fakeSiteDao
            override fun fieldDao(): FieldDao = fakeFieldDao
            override fun incidentDao(): IncidentDao = fakeIncidentDao
            override fun reasonDao(): ReasonDao = fakeReasonDao
            override fun siteTabDao(): SiteTabDao = fakeTabDao
            override fun universalFieldDao(): UniversalFieldDao = fakeUniversalDao
            override fun outageDao() = throw UnsupportedOperationException()
            override fun statsDao() = throw UnsupportedOperationException()
            override fun clearAllTables() {}
            override fun createInvalidationTracker(): androidx.room.InvalidationTracker =
                androidx.room.InvalidationTracker(this, "site_tabs")
            override fun createOpenHelper(config: androidx.room.DatabaseConfiguration) =
                throw UnsupportedOperationException()
        }
    }

    @Test
    fun testSerializeAndDeserializeTabsAndUniversalFieldsRoundTrip() {
        val tabs = listOf(
            SiteTabEntity.SYSTEM_SPECS,
            SiteTabEntity.SYSTEM_LOGS,
            SiteTabEntity.SYSTEM_OVERVIEW,
            SiteTabEntity(
                tabId = "TENANT_INFO",
                displayName = "Tenant Info",
                sortOrder = 3,
                isSystem = false
            )
        )

        val universalFields = listOf(
            UniversalFieldEntity(
                tabId = "SPECS",
                fieldKey = "DG_CAPACITY",
                groupName = "POWER",
                defaultValue = "25 kVA",
                sortOrder = 1
            ),
            UniversalFieldEntity(
                tabId = "TENANT_INFO",
                fieldKey = "TENANT_NAME",
                groupName = "SHARING",
                defaultValue = "Airtel",
                sortOrder = 1
            )
        )

        val fields = listOf(
            SiteFieldEntity(
                sapId = "DEL-001",
                fieldKey = "DG_CAPACITY",
                fieldValue = "30 kVA",
                groupName = "POWER",
                sortOrder = 1,
                tabId = "SPECS"
            ),
            SiteFieldEntity(
                sapId = "DEL-001",
                fieldKey = "TENANT_NAME",
                fieldValue = "Jio",
                groupName = "SHARING",
                sortOrder = 1,
                tabId = "TENANT_INFO"
            )
        )

        val backupData = BackupData(
            version = 2,
            appName = "TowerDost",
            exportedAt = 1786800000000L,
            sites = listOf(
                SiteEntity(
                    sapId = "DEL-001",
                    sapNorm = "del001",
                    name = "Connaught Hub"
                )
            ),
            fields = fields,
            incidents = emptyList(),
            reasons = emptyList(),
            tabs = tabs,
            universalFields = universalFields
        )

        val jsonString = BackupManager.serializeBackupJson(backupData)

        // Verify JSON contents
        assertTrue(jsonString.contains("\"tabs\""))
        assertTrue(jsonString.contains("\"universalFields\""))
        assertTrue(jsonString.contains("TENANT_INFO"))
        assertTrue(jsonString.contains("Tenant Info"))
        assertTrue(jsonString.contains("TENANT_NAME"))
        assertTrue(jsonString.contains("SHARING"))

        // Deserialize via deserializeBackupJson
        val deserialized = BackupManager.deserializeBackupJson(jsonString)

        assertEquals(4, deserialized.tabs.size)
        assertEquals("TENANT_INFO", deserialized.tabs[3].tabId)
        assertEquals("Tenant Info", deserialized.tabs[3].displayName)
        assertEquals(3, deserialized.tabs[3].sortOrder)
        assertFalse(deserialized.tabs[3].isSystem)

        assertEquals(2, deserialized.universalFields.size)
        assertEquals("TENANT_INFO", deserialized.universalFields[1].tabId)
        assertEquals("TENANT_NAME", deserialized.universalFields[1].fieldKey)
        assertEquals("SHARING", deserialized.universalFields[1].groupName)
        assertEquals("Airtel", deserialized.universalFields[1].defaultValue)

        assertEquals(2, deserialized.fields.size)
        assertEquals("SPECS", deserialized.fields[0].tabId)
        assertEquals("TENANT_INFO", deserialized.fields[1].tabId)
    }

    @Test
    fun testBackwardsCompatibilityWithV1Backup() {
        val v1Json = """
            {
              "version": 1,
              "appName": "TowerDost",
              "exportedAt": 1786700000000,
              "sites": [
                {
                  "sapId": "MUM-101",
                  "sapNorm": "mum101",
                  "name": "Bandra Tower"
                }
              ],
              "fields": [
                {
                  "sapId": "MUM-101",
                  "fieldKey": "TOWER_HEIGHT",
                  "fieldValue": "40m",
                  "groupName": "STRUCTURE"
                }
              ],
              "incidents": [],
              "reasons": []
            }
        """.trimIndent()

        val parsed = BackupManager.deserializeBackupJson(v1Json)

        assertEquals(1, parsed.version)
        assertEquals(1, parsed.sites.size)
        assertEquals("MUM-101", parsed.sites[0].sapId)

        assertEquals(1, parsed.fields.size)
        assertEquals("TOWER_HEIGHT", parsed.fields[0].fieldKey)
        // Default tabId for backward compatibility
        assertEquals("SPECS", parsed.fields[0].tabId)

        // Missing tabs and universalFields default to empty lists
        assertNotNull(parsed.tabs)
        assertTrue(parsed.tabs.isEmpty())
        assertNotNull(parsed.universalFields)
        assertTrue(parsed.universalFields.isEmpty())
    }

    @Test
    fun testGenerateAndVerifySafetyBackupSuccess() = runTest {
        val tempDir = Files.createTempDirectory("safety_backup_test").toFile()
        tempDir.deleteOnExit()

        val fakeContext = TestContextWrapper(tempDir)
        val clock = FakeAppClock(1786800000000L)

        val sites = listOf(SiteEntity(sapId = "KOL-201", sapNorm = "kol201", name = "Salt Lake"))
        val tabs = listOf(SiteTabEntity("CUSTOM_TAB", "Custom Tab", 3, false))
        val universalFields = listOf(
            UniversalFieldEntity("CUSTOM_KEY", "GROUP1", "VAL1", 1, "CUSTOM_TAB")
        )

        val fakeDb = createFakeDatabase(
            sites = sites,
            tabs = tabs,
            universalFields = universalFields
        )

        val backupManager = BackupManager(fakeDb, clock)

        // Verify default tag parameter is "pre_deletion"
        val resultDefaultTag = backupManager.generateAndVerifySafetyBackup(fakeContext)
        assertTrue(resultDefaultTag.isSuccess)
        assertTrue(resultDefaultTag.getOrThrow().name.contains("pre_deletion"))

        val result = backupManager.generateAndVerifySafetyBackup(fakeContext, "custom_tag")

        assertTrue("Safety backup must return success", result.isSuccess)
        val file = result.getOrThrow()

        assertTrue("Backup file must exist", file.exists())
        assertTrue("Backup file length must be > 0", file.length() > 0)
        assertTrue("Filename must contain tag", file.name.contains("custom_tag"))
        assertTrue("File must end with .json", file.name.endsWith(".json"))

        // Read and deserialize content to ensure full DB state was written
        val content = file.readText()
        val parsed = BackupManager.deserializeBackupJson(content)

        assertEquals(1, parsed.sites.size)
        assertEquals("KOL-201", parsed.sites[0].sapId)
        assertEquals(1, parsed.tabs.size)
        assertEquals("CUSTOM_TAB", parsed.tabs[0].tabId)
        assertEquals(1, parsed.universalFields.size)
        assertEquals("CUSTOM_KEY", parsed.universalFields[0].fieldKey)
    }

    @Test
    fun testGenerateAndVerifySafetyBackupFailureOnZeroBytes() = runTest {
        val nonWritableDir = File("/non_existent_path_cannot_write_here_xyz/123")
        val fakeContext = TestContextWrapper(nonWritableDir)
        val clock = FakeAppClock(1786800000000L)

        val fakeDb = createFakeDatabase()
        val backupManager = BackupManager(fakeDb, clock)

        val result = backupManager.generateAndVerifySafetyBackup(fakeContext, "pre_deletion")

        assertTrue("Result must be failure when filesystem cannot be written", result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun testRestoreFromBackupJsonWithTabsAndUniversalFields() = runTest {
        val restoredTabs = mutableListOf<SiteTabEntity>()
        val restoredUniversalFields = mutableListOf<UniversalFieldEntity>()

        val fakeTabDao = Proxy.newProxyInstance(
            SiteTabDao::class.java.classLoader,
            arrayOf(SiteTabDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertTabs" -> {
                    @Suppress("UNCHECKED_CAST")
                    restoredTabs.addAll(args[0] as List<SiteTabEntity>)
                    Unit
                }
                else -> null
            }
        } as SiteTabDao

        val fakeUniversalDao = Proxy.newProxyInstance(
            UniversalFieldDao::class.java.classLoader,
            arrayOf(UniversalFieldDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertOrUpdateFields" -> {
                    @Suppress("UNCHECKED_CAST")
                    restoredUniversalFields.addAll(args[0] as List<UniversalFieldEntity>)
                    Unit
                }
                "clearAll" -> Unit
                else -> null
            }
        } as UniversalFieldDao

        val fakeSiteDao = Proxy.newProxyInstance(
            SiteDao::class.java.classLoader,
            arrayOf(SiteDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "deleteAllSites", "clearFts", "insertSites", "insertFts" -> Unit
                else -> null
            }
        } as SiteDao

        val fakeFieldDao = Proxy.newProxyInstance(
            FieldDao::class.java.classLoader,
            arrayOf(FieldDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "deleteAllFields", "insertFields" -> Unit
                else -> null
            }
        } as FieldDao

        val fakeIncidentDao = Proxy.newProxyInstance(
            IncidentDao::class.java.classLoader,
            arrayOf(IncidentDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "deleteAllIncidents", "insertIncidents" -> Unit
                else -> null
            }
        } as IncidentDao

        val fakeReasonDao = Proxy.newProxyInstance(
            ReasonDao::class.java.classLoader,
            arrayOf(ReasonDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "insertReasons" -> Unit
                else -> null
            }
        } as ReasonDao

        val fakeDb = object : AppDatabase() {
            override val transactionExecutor: java.util.concurrent.Executor =
                java.util.concurrent.Executor { it.run() }
            override fun beginTransaction() {}
            override fun setTransactionSuccessful() {}
            override fun endTransaction() {}
            override fun siteDao(): SiteDao = fakeSiteDao
            override fun fieldDao(): FieldDao = fakeFieldDao
            override fun incidentDao(): IncidentDao = fakeIncidentDao
            override fun reasonDao(): ReasonDao = fakeReasonDao
            override fun siteTabDao(): SiteTabDao = fakeTabDao
            override fun universalFieldDao(): UniversalFieldDao = fakeUniversalDao
            override fun outageDao() = throw UnsupportedOperationException()
            override fun statsDao() = throw UnsupportedOperationException()
            override fun clearAllTables() {}
            override fun createInvalidationTracker(): androidx.room.InvalidationTracker =
                androidx.room.InvalidationTracker(this, "site_tabs")
            override fun createOpenHelper(config: androidx.room.DatabaseConfiguration) =
                throw UnsupportedOperationException()
        }

        val clock = FakeAppClock(1786800000000L)
        val backupManager = BackupManager(fakeDb, clock)

        val backupData = BackupData(
            version = 2,
            appName = "TowerDost",
            exportedAt = 1786800000000L,
            sites = emptyList(),
            fields = emptyList(),
            incidents = emptyList(),
            reasons = emptyList(),
            tabs = listOf(SiteTabEntity("AUDIT", "Audit", 5, false)),
            universalFields = listOf(UniversalFieldEntity("AUDITOR", "GENERAL", "John Doe", 1, "AUDIT"))
        )

        val json = BackupManager.serializeBackupJson(backupData)
        val restoreResult = backupManager.restoreFromBackupJson(json, cleanOverwrite = true)

        assertEquals(1, restoreResult.tabsRestored)
        assertEquals(1, restoreResult.universalFieldsRestored)
        assertEquals(1, restoredTabs.size)
        assertEquals("AUDIT", restoredTabs[0].tabId)
        assertEquals(1, restoredUniversalFields.size)
        assertEquals("AUDITOR", restoredUniversalFields[0].fieldKey)
        assertEquals("AUDIT", restoredUniversalFields[0].tabId)
    }
}
