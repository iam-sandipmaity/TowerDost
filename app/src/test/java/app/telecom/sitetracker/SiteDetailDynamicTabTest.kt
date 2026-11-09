package app.telecom.sitetracker

import android.content.Context
import app.telecom.sitetracker.data.backup.BackupData
import app.telecom.sitetracker.data.backup.BackupManager
import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.db.FieldDao
import app.telecom.sitetracker.data.db.IncidentDao
import app.telecom.sitetracker.data.db.OutageDao
import app.telecom.sitetracker.data.db.SiteDao
import app.telecom.sitetracker.data.db.SiteTabDao
import app.telecom.sitetracker.data.db.UniversalFieldDao
import app.telecom.sitetracker.data.entity.IncidentEntity
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.entity.SiteTabEntity
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import app.telecom.sitetracker.data.repo.IncidentRepository
import app.telecom.sitetracker.data.repo.OutageRepository
import app.telecom.sitetracker.data.repo.SiteRepository
import app.telecom.sitetracker.ui.sitedetail.SiteDetailViewModel
import app.telecom.sitetracker.ui.sitedetail.SpecDraftItem
import app.telecom.sitetracker.ui.sitedetail.SpecDraftSection
import app.telecom.sitetracker.util.AppClock
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SiteDetailDynamicTabTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testClock = object : AppClock {
        override fun currentTimeMillis(): Long = 1700000000000L
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private data class Fixture(
        val repo: SiteRepository,
        val incidentRepo: IncidentRepository,
        val outageRepo: OutageRepository,
        val backupManager: BackupManager,
        val storedSites: MutableList<SiteEntity>,
        val storedTabs: MutableList<SiteTabEntity>,
        val storedFields: MutableList<SiteFieldEntity>,
        val storedUniversal: MutableList<UniversalFieldEntity>
    )

    private fun createFixture(): Fixture {
        val storedSites = mutableListOf<SiteEntity>()
        val storedTabs = mutableListOf<SiteTabEntity>().apply { addAll(SiteTabEntity.DEFAULT_TABS) }
        val storedFields = mutableListOf<SiteFieldEntity>()
        val storedUniversal = mutableListOf<UniversalFieldEntity>()

        val fakeSiteDao = Proxy.newProxyInstance(
            SiteDao::class.java.classLoader,
            arrayOf(SiteDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getSiteBySapId" -> {
                    val id = args[0] as String
                    storedSites.find { it.sapId == id }
                }
                "getSiteBySapNorm" -> {
                    val norm = args[0] as String
                    storedSites.find { it.sapNorm == norm }
                }
                "getAllSitesForBackup" -> storedSites.toList()
                "updateSite" -> Unit
                "deleteFts" -> Unit
                "insertFts" -> Unit
                else -> null
            }
        } as SiteDao

        val fakeTabDao = object : SiteTabDao {
            override fun observeAllTabs() = flowOf(storedTabs.sortedBy { it.sortOrder })
            override suspend fun getAllTabs() = storedTabs.sortedBy { it.sortOrder }
            override suspend fun getTabById(tabId: String) =
                storedTabs.find { it.tabId.equals(tabId, ignoreCase = true) }
            override suspend fun insertTab(tab: SiteTabEntity) {
                storedTabs.removeAll { it.tabId.equals(tab.tabId, ignoreCase = true) }
                storedTabs.add(tab)
            }
            override suspend fun insertTabs(tabs: List<SiteTabEntity>) {
                tabs.forEach { insertTab(it) }
            }
            override suspend fun deleteTab(tabId: String) {
                storedTabs.removeAll { it.tabId.equals(tabId, ignoreCase = true) }
            }
            override suspend fun getTabCount() = storedTabs.size
        }

        val fakeFieldDao = Proxy.newProxyInstance(
            FieldDao::class.java.classLoader,
            arrayOf(FieldDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getFieldsForSite" -> {
                    val id = args[0] as String
                    storedFields.filter { it.sapId == id }
                }
                "getFieldsForSiteAndTab" -> {
                    val id = args[0] as String
                    val tab = args[1] as String
                    storedFields.filter { it.sapId == id && it.tabId.equals(tab, ignoreCase = true) }
                }
                "replaceFieldsForSiteAndTab" -> {
                    val id = args[0] as String
                    val tab = args[1] as String
                    val newFields = args[2] as List<SiteFieldEntity>
                    storedFields.removeAll { it.sapId == id && it.tabId.equals(tab, ignoreCase = true) }
                    storedFields.addAll(newFields)
                    Unit
                }
                "deleteFieldsForTab" -> {
                    val tab = args[0] as String
                    storedFields.removeAll { it.tabId.equals(tab, ignoreCase = true) }
                    Unit
                }
                "countDistinctSitesForTab", "countSitesWithTab" -> {
                    val tab = args[0] as String
                    storedFields.filter { it.tabId.equals(tab, ignoreCase = true) }.map { it.sapId }.distinct().size
                }
                "getDistinctSpecsFromSites" -> emptyList<app.telecom.sitetracker.data.db.DistinctFieldSpec>()
                "getAllFields" -> storedFields.toList()
                "insertField" -> {
                    val f = args[0] as SiteFieldEntity
                    storedFields.add(f)
                    1L
                }
                "insertFields" -> {
                    val list = args[0] as List<SiteFieldEntity>
                    storedFields.addAll(list)
                    Unit
                }
                else -> null
            }
        } as FieldDao

        val fakeUniversalDao = Proxy.newProxyInstance(
            UniversalFieldDao::class.java.classLoader,
            arrayOf(UniversalFieldDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "observeAllUniversalFields" -> flowOf(storedUniversal)
                "getAllUniversalFields" -> storedUniversal
                "getUniversalFieldsForTab" -> {
                    val tab = args[0] as String
                    storedUniversal.filter { it.tabId.equals(tab, ignoreCase = true) }
                }
                "insertOrUpdateField" -> {
                    val f = args[0] as UniversalFieldEntity
                    storedUniversal.removeAll { it.tabId.equals(f.tabId, ignoreCase = true) && it.fieldKey.equals(f.fieldKey, ignoreCase = true) }
                    storedUniversal.add(f)
                    Unit
                }
                "insertOrUpdateFields" -> {
                    val list = args[0] as List<UniversalFieldEntity>
                    list.forEach { f ->
                        storedUniversal.removeAll { it.tabId.equals(f.tabId, ignoreCase = true) && it.fieldKey.equals(f.fieldKey, ignoreCase = true) }
                        storedUniversal.add(f)
                    }
                    Unit
                }
                "deleteFieldsForTab" -> {
                    val tab = args[0] as String
                    storedUniversal.removeAll { it.tabId.equals(tab, ignoreCase = true) }
                    Unit
                }
                else -> null
            }
        } as UniversalFieldDao

        val fakeIncidentDao = Proxy.newProxyInstance(
            IncidentDao::class.java.classLoader,
            arrayOf(IncidentDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "observeIncidentsForSite" -> flowOf(emptyList<IncidentEntity>())
                "getIncidentsForSite" -> flowOf(emptyList<IncidentEntity>())
                "getIncidentsListForSite" -> emptyList<IncidentEntity>()
                "getAllIncidentsForBackup" -> emptyList<IncidentEntity>()
                else -> null
            }
        } as IncidentDao

        val fakeOutageDao = Proxy.newProxyInstance(
            OutageDao::class.java.classLoader,
            arrayOf(OutageDao::class.java)
        ) { _, _, _ -> null } as OutageDao

        val fakeReasonDao = Proxy.newProxyInstance(
            app.telecom.sitetracker.data.db.ReasonDao::class.java.classLoader,
            arrayOf(app.telecom.sitetracker.data.db.ReasonDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getAllReasons", "getAllReasonsList" -> emptyList<app.telecom.sitetracker.data.entity.ReasonEntity>()
                else -> null
            }
        } as app.telecom.sitetracker.data.db.ReasonDao

        val fakeDb = object : AppDatabase() {
            override val transactionExecutor: java.util.concurrent.Executor =
                java.util.concurrent.Executor { it.run() }
            override fun beginTransaction() {}
            override fun setTransactionSuccessful() {}
            override fun endTransaction() {}
            override fun siteTabDao(): SiteTabDao = fakeTabDao
            override fun fieldDao(): FieldDao = fakeFieldDao
            override fun universalFieldDao(): UniversalFieldDao = fakeUniversalDao
            override fun siteDao(): SiteDao = fakeSiteDao
            override fun incidentDao(): IncidentDao = fakeIncidentDao
            override fun outageDao(): OutageDao = fakeOutageDao
            override fun reasonDao(): app.telecom.sitetracker.data.db.ReasonDao = fakeReasonDao
            override fun statsDao() = throw UnsupportedOperationException()
            override fun clearAllTables() {}
            override fun createInvalidationTracker(): androidx.room.InvalidationTracker =
                androidx.room.InvalidationTracker(this, "site_tabs")
            override fun createOpenHelper(config: androidx.room.DatabaseConfiguration) =
                throw UnsupportedOperationException()
        }

        val repo = SiteRepository(fakeDb)
        val incidentRepo = IncidentRepository(fakeDb)
        val outageRepo = OutageRepository(fakeDb)
        val bm = BackupManager(fakeDb, testClock)

        return Fixture(
            repo = repo,
            incidentRepo = incidentRepo,
            outageRepo = outageRepo,
            backupManager = bm,
            storedSites = storedSites,
            storedTabs = storedTabs,
            storedFields = storedFields,
            storedUniversal = storedUniversal
        )
    }

    private class TestContextWrapper(private val filesDirectory: File) : android.content.ContextWrapper(null) {
        override fun getFilesDir(): File = filesDirectory
        override fun getCacheDir(): File = filesDirectory
    }

    private fun createFakeContext(tempDir: File): Context = TestContextWrapper(tempDir)

    @Test
    fun testDynamicTabsLoadingAndSwitching() = runTest(testDispatcher) {
        val f = createFixture()
        f.storedSites.add(SiteEntity(sapId = "IN-1001", sapNorm = "IN1001", name = "Connaught Place"))
        f.repo.insertTab("TENANT_INFO", "Tenant Info")

        val viewModel = SiteDetailViewModel(
            sapId = "IN-1001",
            siteRepository = f.repo,
            incidentRepository = f.incidentRepo,
            outageRepository = f.outageRepo,
            backupManager = f.backupManager
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        if (state.errorMessage != null) {
            println("UI_STATE_ERROR: ${state.errorMessage}")
        }
        assertEquals("IN-1001", state.site?.sapId)
        assertTrue(state.tabs.any { it.tabId == "SPECS" })
        assertTrue(state.tabs.any { it.tabId == "LOGS" })
        assertTrue(state.tabs.any { it.tabId == "OVERVIEW" })
        assertTrue(state.tabs.any { it.tabId == "TENANT_INFO" })
        assertEquals("SPECS", state.selectedTabId)

        // Switch tab
        viewModel.selectTab("TENANT_INFO")
        advanceUntilIdle()
        assertEquals("TENANT_INFO", viewModel.uiState.value.selectedTabId)
    }

    @Test
    fun testOption1BHierarchicalSectionsInCustomTab() = runTest(testDispatcher) {
        val f = createFixture()
        f.storedSites.add(SiteEntity(sapId = "IN-1002", sapNorm = "IN1002", name = "Saket Hub"))
        f.repo.insertTab("EB_BILLING", "EB Billing")

        val viewModel = SiteDetailViewModel(
            sapId = "IN-1002",
            siteRepository = f.repo,
            incidentRepository = f.incidentRepo,
            outageRepository = f.outageRepo,
            backupManager = f.backupManager
        )
        advanceUntilIdle()

        viewModel.selectTab("EB_BILLING")
        advanceUntilIdle()

        // Edit specs inside custom tab with multiple hierarchical sections (Option 1B)
        viewModel.startEditingSpecs()
        viewModel.renameDraftSection(0, "MAIN METER")
        viewModel.updateDraftSpecItem(0, 0, "METER_NO", "EB-998877")
        viewModel.addDraftSection("SUB METER")
        viewModel.updateDraftSpecItem(1, 0, "CONSUMER_ID", "CNS-12345")
        viewModel.requestSaveSpecs()
        viewModel.confirmAndSaveSpecs()
        advanceUntilIdle()

        // Verify fields saved with tab_id = "EB_BILLING" across distinct hierarchical sections
        val ebFields = f.repo.getFieldsForSiteAndTab("IN-1002", "EB_BILLING")
        assertEquals(2, ebFields.size)
        assertEquals("METER_NO", ebFields[0].fieldKey)
        assertEquals("EB-998877", ebFields[0].fieldValue)
        assertEquals("EB_BILLING", ebFields[0].tabId)
        assertEquals("MAIN METER", ebFields[0].groupName)

        assertEquals("CONSUMER_ID", ebFields[1].fieldKey)
        assertEquals("CNS-12345", ebFields[1].fieldValue)
        assertEquals("EB_BILLING", ebFields[1].tabId)
        assertEquals("SUB METER", ebFields[1].groupName)

        // Verify SPECS tab fields remain empty
        val specFields = f.repo.getFieldsForSiteAndTab("IN-1002", "SPECS")
        assertTrue(specFields.isEmpty())
    }

    @Test
    fun testOption2APreDeletionBackupVerificationGate() = runTest(testDispatcher) {
        val f = createFixture()
        f.storedSites.add(SiteEntity(sapId = "IN-1003", sapNorm = "IN1003", name = "Noida DC"))
        f.repo.insertTab("SOLAR", "Solar Specs")
        f.storedFields.add(
            SiteFieldEntity(sapId = "IN-1003", fieldKey = "PANEL_KW", fieldValue = "10KW", tabId = "SOLAR", groupName = "SOLAR")
        )

        val tempDir = File.createTempFile("test_backup_dir", "").apply {
            delete()
            mkdirs()
        }
        val fakeContext = createFakeContext(tempDir)

        val viewModel = SiteDetailViewModel(
            sapId = "IN-1003",
            siteRepository = f.repo,
            incidentRepository = f.incidentRepo,
            outageRepository = f.outageRepo,
            backupManager = f.backupManager
        )
        advanceUntilIdle()

        // Request delete tab
        viewModel.requestDeleteTab("SOLAR")
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals("SOLAR", state.tabToDelete?.tabId)
        assertEquals(1, state.tabDeleteSiteCount)
        assertFalse("Delete button must be locked before backup verification", state.backupVerified)

        // Attempting to delete before backup must fail / remain locked
        viewModel.confirmDeleteTab(fakeContext)
        advanceUntilIdle()
        assertNotNull(f.repo.getTabById("SOLAR"))

        // Generate verified backup
        val backupJob = viewModel.generateSafetyBackup(fakeContext)
        backupJob.join()
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertTrue("Backup must be verified: ${state.errorMessage}", state.backupVerified)
        assertNotNull(state.backupFilePath)
        val backupFile = File(state.backupFilePath!!)
        assertTrue(backupFile.exists() && backupFile.length() > 0)

        // Now confirm delete tab
        val deleteJob = viewModel.confirmDeleteTab(fakeContext)
        deleteJob?.join()
        advanceUntilIdle()

        // Tab and fields must be deleted
        assertNull(f.repo.getTabById("SOLAR"))
        assertTrue(f.repo.getFieldsForSiteAndTab("IN-1003", "SOLAR").isEmpty())
        assertEquals("SPECS", viewModel.uiState.value.selectedTabId)

        tempDir.deleteRecursively()
    }
}
