package app.telecom.sitetracker

import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.db.DistinctFieldSpec
import app.telecom.sitetracker.data.db.FieldDao
import app.telecom.sitetracker.data.db.SiteDao
import app.telecom.sitetracker.data.db.SiteTabDao
import app.telecom.sitetracker.data.db.UniversalFieldDao
import app.telecom.sitetracker.data.entity.SiteTabEntity
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import app.telecom.sitetracker.data.repo.SiteRepository
import app.telecom.sitetracker.ui.template.UniversalTemplateViewModel
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UniversalTemplateTabTest {

    private val testDispatcher = StandardTestDispatcher()

    private val storedTabsFlow = MutableStateFlow<List<SiteTabEntity>>(emptyList())
    private val storedUniversalFieldsFlow = MutableStateFlow<List<UniversalFieldEntity>>(emptyList())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createRepository(
        initialTabs: List<SiteTabEntity> = SiteTabEntity.DEFAULT_TABS,
        initialFields: List<UniversalFieldEntity> = emptyList()
    ): SiteRepository {
        val storedTabs = mutableListOf<SiteTabEntity>().apply { addAll(initialTabs) }
        val storedUniversalFields = mutableListOf<UniversalFieldEntity>().apply { addAll(initialFields) }

        storedTabsFlow.value = storedTabs.toList()
        storedUniversalFieldsFlow.value = storedUniversalFields.toList()

        fun syncTabsFlow() {
            storedTabsFlow.value = storedTabs.toList()
        }

        fun syncFieldsFlow() {
            storedUniversalFieldsFlow.value = storedUniversalFields.toList()
        }

        val fakeTabDao = object : SiteTabDao {
            override fun observeAllTabs() = storedTabsFlow.asStateFlow()
            override suspend fun getAllTabs() = storedTabs.sortedBy { it.sortOrder }
            override suspend fun getTabById(tabId: String) =
                storedTabs.find { it.tabId.equals(tabId, ignoreCase = true) }
            override suspend fun insertTab(tab: SiteTabEntity) {
                storedTabs.removeAll { it.tabId.equals(tab.tabId, ignoreCase = true) }
                storedTabs.add(tab)
                syncTabsFlow()
            }
            override suspend fun insertTabs(tabs: List<SiteTabEntity>) {
                tabs.forEach { tab ->
                    storedTabs.removeAll { it.tabId.equals(tab.tabId, ignoreCase = true) }
                    storedTabs.add(tab)
                }
                syncTabsFlow()
            }
            override suspend fun deleteTab(tabId: String) {
                storedTabs.removeAll { it.tabId.equals(tabId, ignoreCase = true) }
                syncTabsFlow()
            }
            override suspend fun getTabCount() = storedTabs.size
        }

        val fakeUniversalDao = object : UniversalFieldDao {
            override fun observeAllUniversalFields() = storedUniversalFieldsFlow.asStateFlow()
            override suspend fun getAllUniversalFields() = storedUniversalFields.toList()

            override fun observeUniversalFieldsForTab(tabId: String) =
                storedUniversalFieldsFlow.map { list ->
                    list.filter { it.tabId.equals(tabId, ignoreCase = true) }
                }

            override suspend fun getUniversalFieldsForTab(tabId: String) =
                storedUniversalFields.filter { it.tabId.equals(tabId, ignoreCase = true) }

            override suspend fun insertOrUpdateField(field: UniversalFieldEntity) {
                storedUniversalFields.removeAll {
                    it.tabId.equals(field.tabId, ignoreCase = true) &&
                        it.fieldKey.equals(field.fieldKey, ignoreCase = true)
                }
                storedUniversalFields.add(field)
                syncFieldsFlow()
            }

            override suspend fun insertOrUpdateFields(fields: List<UniversalFieldEntity>) {
                fields.forEach { field ->
                    storedUniversalFields.removeAll {
                        it.tabId.equals(field.tabId, ignoreCase = true) &&
                            it.fieldKey.equals(field.fieldKey, ignoreCase = true)
                    }
                    storedUniversalFields.add(field)
                }
                syncFieldsFlow()
            }

            override suspend fun deleteField(fieldKey: String) {
                storedUniversalFields.removeAll { it.fieldKey.equals(fieldKey, ignoreCase = true) }
                syncFieldsFlow()
            }

            override suspend fun deleteField(tabId: String, fieldKey: String) {
                storedUniversalFields.removeAll {
                    it.tabId.equals(tabId, ignoreCase = true) &&
                        it.fieldKey.equals(fieldKey, ignoreCase = true)
                }
                syncFieldsFlow()
            }

            override suspend fun deleteSection(groupName: String) {
                storedUniversalFields.removeAll { it.groupName.equals(groupName, ignoreCase = true) }
                syncFieldsFlow()
            }

            override suspend fun deleteSection(tabId: String, groupName: String) {
                storedUniversalFields.removeAll {
                    it.tabId.equals(tabId, ignoreCase = true) &&
                        it.groupName.equals(groupName, ignoreCase = true)
                }
                syncFieldsFlow()
            }

            override suspend fun deleteFieldsForTab(tabId: String) {
                storedUniversalFields.removeAll { it.tabId.equals(tabId, ignoreCase = true) }
                syncFieldsFlow()
            }

            override suspend fun clearAll() {
                storedUniversalFields.clear()
                syncFieldsFlow()
            }
        }

        val fakeFieldDao = Proxy.newProxyInstance(
            FieldDao::class.java.classLoader,
            arrayOf(FieldDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getDistinctSpecsFromSites" -> emptyList<DistinctFieldSpec>()
                "countSitesWithTab" -> 0
                else -> null
            }
        } as FieldDao

        val fakeSiteDao = Proxy.newProxyInstance(
            SiteDao::class.java.classLoader,
            arrayOf(SiteDao::class.java)
        ) { _, _, _ -> null } as SiteDao

        val fakeDb = object : AppDatabase() {
            override val transactionExecutor: java.util.concurrent.Executor =
                java.util.concurrent.Executor { it.run() }
            override fun beginTransaction() {}
            override fun setTransactionSuccessful() {}
            override fun endTransaction() {}
            override fun siteTabDao(): SiteTabDao = fakeTabDao
            override fun universalFieldDao(): UniversalFieldDao = fakeUniversalDao
            override fun fieldDao(): FieldDao = fakeFieldDao
            override fun siteDao(): SiteDao = fakeSiteDao
            override fun reasonDao() = throw UnsupportedOperationException()
            override fun incidentDao() = throw UnsupportedOperationException()
            override fun outageDao() = throw UnsupportedOperationException()
            override fun statsDao() = throw UnsupportedOperationException()
            override fun clearAllTables() {}
            override fun createInvalidationTracker(): androidx.room.InvalidationTracker =
                androidx.room.InvalidationTracker(this, "site_tabs")
            override fun createOpenHelper(config: androidx.room.DatabaseConfiguration) =
                throw UnsupportedOperationException()
        }

        return SiteRepository(fakeDb)
    }

    @Test
    fun testViewModelObservesTabsAndDefaultsToSpecs() = runTest(testDispatcher) {
        val repo = createRepository(
            initialTabs = listOf(
                SiteTabEntity.SYSTEM_SPECS,
                SiteTabEntity.SYSTEM_LOGS,
                SiteTabEntity.SYSTEM_OVERVIEW,
                SiteTabEntity("TENANT", "Tenant Info", 3, isSystem = false)
            )
        )

        val viewModel = UniversalTemplateViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("SPECS", state.selectedTabId)
        // Tabs should contain Specs and custom tab TENANT, excluding system tabs LOGS and OVERVIEW
        val tabIds = state.tabs.map { it.tabId }
        assertTrue("Must contain SPECS tab", tabIds.contains("SPECS"))
        assertTrue("Must contain TENANT tab", tabIds.contains("TENANT"))
        assertFalse("Must NOT contain LOGS tab in universal template", tabIds.contains("LOGS"))
        assertFalse("Must NOT contain OVERVIEW tab in universal template", tabIds.contains("OVERVIEW"))
    }

    @Test
    fun testSelectTabFiltersFieldsByTabId() = runTest(testDispatcher) {
        val repo = createRepository(
            initialTabs = listOf(
                SiteTabEntity.SYSTEM_SPECS,
                SiteTabEntity("TENANT", "Tenant Info", 1, isSystem = false)
            ),
            initialFields = listOf(
                UniversalFieldEntity(
                    fieldKey = "SMPS_MAKE",
                    groupName = "POWER",
                    defaultValue = null,
                    sortOrder = 1,
                    tabId = "SPECS"
                ),
                UniversalFieldEntity(
                    fieldKey = "TENANT_NAME",
                    groupName = "SHARING",
                    defaultValue = "Airtel",
                    sortOrder = 1,
                    tabId = "TENANT"
                )
            )
        )

        val viewModel = UniversalTemplateViewModel(repo)
        advanceUntilIdle()

        // By default on SPECS
        assertEquals("SPECS", viewModel.uiState.value.selectedTabId)
        assertEquals(1, viewModel.uiState.value.totalFieldCount)
        assertTrue(viewModel.uiState.value.groupedFields.containsKey("POWER"))
        assertFalse(viewModel.uiState.value.groupedFields.containsKey("SHARING"))

        // Select TENANT tab
        viewModel.selectTab("TENANT")
        advanceUntilIdle()

        assertEquals("TENANT", viewModel.uiState.value.selectedTabId)
        assertEquals(1, viewModel.uiState.value.totalFieldCount)
        assertFalse(viewModel.uiState.value.groupedFields.containsKey("POWER"))
        assertTrue(viewModel.uiState.value.groupedFields.containsKey("SHARING"))
        assertEquals("TENANT_NAME", viewModel.uiState.value.groupedFields["SHARING"]?.first()?.fieldKey)
    }

    @Test
    fun testCreateNewTabInsertsAndSelectsIt() = runTest(testDispatcher) {
        val repo = createRepository(
            initialTabs = listOf(SiteTabEntity.SYSTEM_SPECS)
        )

        val viewModel = UniversalTemplateViewModel(repo)
        advanceUntilIdle()

        viewModel.createNewTab("Solar Specs")
        advanceUntilIdle()

        assertEquals("SOLAR_SPECS", viewModel.uiState.value.selectedTabId)
        val allTabs = repo.getAllTabs()
        val createdTab = allTabs.find { it.tabId == "SOLAR_SPECS" }
        assertNotNull(createdTab)
        assertEquals("Solar Specs", createdTab?.displayName)
        assertFalse(createdTab?.isSystem ?: true)
    }

    @Test
    fun testConfirmAddFieldAssignsSelectedTabId() = runTest(testDispatcher) {
        val repo = createRepository(
            initialTabs = listOf(
                SiteTabEntity.SYSTEM_SPECS,
                SiteTabEntity("TENANT", "Tenant Info", 1, isSystem = false)
            )
        )

        val viewModel = UniversalTemplateViewModel(repo)
        advanceUntilIdle()

        viewModel.selectTab("TENANT")
        advanceUntilIdle()

        viewModel.startAddField("SHARING")
        viewModel.confirmAddField("OPERATOR", "Airtel")
        advanceUntilIdle()

        val tenantFields = repo.getUniversalFieldsForTab("TENANT")
        assertEquals(1, tenantFields.size)
        val field = tenantFields.first()
        assertEquals("OPERATOR", field.fieldKey)
        assertEquals("SHARING", field.groupName)
        assertEquals("Airtel", field.defaultValue)
        assertEquals("TENANT", field.tabId)

        // SPECS must have 0 fields
        val specsFields = repo.getUniversalFieldsForTab("SPECS")
        assertTrue(specsFields.isEmpty())
    }

    @Test
    fun testConfirmAddSectionAssignsSelectedTabId() = runTest(testDispatcher) {
        val repo = createRepository(
            initialTabs = listOf(
                SiteTabEntity.SYSTEM_SPECS,
                SiteTabEntity("TENANT", "Tenant Info", 1, isSystem = false)
            )
        )

        val viewModel = UniversalTemplateViewModel(repo)
        advanceUntilIdle()

        viewModel.selectTab("TENANT")
        advanceUntilIdle()

        viewModel.startAddSection()
        viewModel.confirmAddSection("AGREEMENT", "RENT_AMOUNT", "50000")
        advanceUntilIdle()

        val tenantFields = repo.getUniversalFieldsForTab("TENANT")
        assertEquals(1, tenantFields.size)
        val field = tenantFields.first()
        assertEquals("RENT_AMOUNT", field.fieldKey)
        assertEquals("AGREEMENT", field.groupName)
        assertEquals("50000", field.defaultValue)
        assertEquals("TENANT", field.tabId)
    }

    @Test
    fun testDeleteFieldIsScopedToSelectedTab() = runTest(testDispatcher) {
        val repo = createRepository(
            initialTabs = listOf(
                SiteTabEntity.SYSTEM_SPECS,
                SiteTabEntity("TENANT", "Tenant Info", 1, isSystem = false)
            ),
            initialFields = listOf(
                UniversalFieldEntity(
                    fieldKey = "STATUS",
                    groupName = "OPERATIONAL",
                    defaultValue = "ON AIR",
                    sortOrder = 1,
                    tabId = "SPECS"
                ),
                UniversalFieldEntity(
                    fieldKey = "STATUS",
                    groupName = "LEGAL",
                    defaultValue = "ACTIVE",
                    sortOrder = 1,
                    tabId = "TENANT"
                )
            )
        )

        val viewModel = UniversalTemplateViewModel(repo)
        advanceUntilIdle()

        viewModel.selectTab("TENANT")
        advanceUntilIdle()

        val tenantStatus = repo.getUniversalFieldsForTab("TENANT").first { it.fieldKey == "STATUS" }
        viewModel.requestDeleteField(tenantStatus)
        viewModel.confirmDeleteField()
        advanceUntilIdle()

        // TENANT status deleted
        val tenantRemaining = repo.getUniversalFieldsForTab("TENANT")
        assertTrue("TENANT STATUS should be deleted", tenantRemaining.isEmpty())

        // SPECS status still exists
        val specsRemaining = repo.getUniversalFieldsForTab("SPECS")
        assertEquals(1, specsRemaining.size)
        assertEquals("STATUS", specsRemaining.first().fieldKey)
        assertEquals("SPECS", specsRemaining.first().tabId)
    }

    @Test
    fun testDeleteSectionIsScopedToSelectedTab() = runTest(testDispatcher) {
        val repo = createRepository(
            initialTabs = listOf(
                SiteTabEntity.SYSTEM_SPECS,
                SiteTabEntity("TENANT", "Tenant Info", 1, isSystem = false)
            ),
            initialFields = listOf(
                UniversalFieldEntity(
                    fieldKey = "CONTACT_PHONE",
                    groupName = "INFO",
                    defaultValue = null,
                    sortOrder = 1,
                    tabId = "SPECS"
                ),
                UniversalFieldEntity(
                    fieldKey = "TENANT_CONTACT",
                    groupName = "INFO",
                    defaultValue = null,
                    sortOrder = 1,
                    tabId = "TENANT"
                )
            )
        )

        val viewModel = UniversalTemplateViewModel(repo)
        advanceUntilIdle()

        viewModel.selectTab("TENANT")
        advanceUntilIdle()

        viewModel.requestDeleteSection("INFO")
        viewModel.confirmDeleteSection()
        advanceUntilIdle()

        // TENANT INFO section deleted
        val tenantFields = repo.getUniversalFieldsForTab("TENANT")
        assertTrue("TENANT INFO section should be deleted", tenantFields.isEmpty())

        // SPECS INFO section untouched
        val specsFields = repo.getUniversalFieldsForTab("SPECS")
        assertEquals(1, specsFields.size)
        assertEquals("CONTACT_PHONE", specsFields.first().fieldKey)
        assertEquals("INFO", specsFields.first().groupName)
    }
}
