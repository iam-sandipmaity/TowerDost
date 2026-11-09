package app.telecom.sitetracker

import androidx.sqlite.db.SupportSQLiteDatabase
import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.db.FieldDao
import app.telecom.sitetracker.data.db.SiteDao
import app.telecom.sitetracker.data.db.SiteTabDao
import app.telecom.sitetracker.data.db.UniversalFieldDao
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.entity.SiteTabEntity
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import app.telecom.sitetracker.data.repo.SiteRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SiteTabDatabaseTest {

    @Test
    fun testMigration3To4VersionsAndSqlExecution() {
        assertEquals(3, AppDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, AppDatabase.MIGRATION_3_4.endVersion)

        val executedSql = mutableListOf<String>()
        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_3_4.migrate(dbProxy)

        assertTrue("Expected SQL queries to be executed", executedSql.isNotEmpty())

        val combinedSql = executedSql.joinToString("\n")

        // 1. Creates site_tabs table
        assertTrue(
            "Migration must create site_tabs table",
            combinedSql.contains("CREATE TABLE IF NOT EXISTS `site_tabs`") ||
                combinedSql.contains("CREATE TABLE IF NOT EXISTS site_tabs")
        )
        assertTrue(
            "site_tabs must have tab_id, display_name, sort_order, is_system",
            combinedSql.contains("`tab_id`") &&
                combinedSql.contains("`display_name`") &&
                combinedSql.contains("`sort_order`") &&
                combinedSql.contains("`is_system`")
        )

        // 2. Seeds default system tabs: SPECS, LOGS, OVERVIEW
        assertTrue("Must seed SPECS tab", combinedSql.contains("SPECS"))
        assertTrue("Must seed LOGS tab", combinedSql.contains("LOGS"))
        assertTrue("Must seed OVERVIEW tab", combinedSql.contains("OVERVIEW"))

        // 3. Alters site_fields adding tab_id column defaulting to 'SPECS'
        assertTrue(
            "Must alter site_fields to add tab_id column",
            combinedSql.contains("ALTER TABLE `site_fields` ADD COLUMN `tab_id`") ||
                combinedSql.contains("ALTER TABLE site_fields ADD COLUMN tab_id")
        )

        // 4. Upgrades universal_fields to composite primary key (tab_id, field_key)
        assertTrue(
            "Must create universal_fields_new with composite primary key",
            combinedSql.contains("universal_fields_new") &&
                combinedSql.contains("PRIMARY KEY(`tab_id`, `field_key`)")
        )

        // 5. Copies existing universal fields
        assertTrue(
            "Must copy existing data from universal_fields",
            combinedSql.contains("INSERT INTO `universal_fields_new`") &&
                combinedSql.contains("SELECT 'SPECS'")
        )

        // 6. Drops old table and renames
        assertTrue(
            "Must drop old universal_fields and rename new table",
            combinedSql.contains("DROP TABLE `universal_fields`") &&
                combinedSql.contains("ALTER TABLE `universal_fields_new` RENAME TO `universal_fields`")
        )
    }

    @Test
    fun testDefaultSystemTabsDefinition() {
        assertEquals("SPECS", SiteTabEntity.SYSTEM_SPECS.tabId)
        assertEquals("Specs", SiteTabEntity.SYSTEM_SPECS.displayName)
        assertEquals(0, SiteTabEntity.SYSTEM_SPECS.sortOrder)
        assertTrue(SiteTabEntity.SYSTEM_SPECS.isSystem)

        assertEquals("LOGS", SiteTabEntity.SYSTEM_LOGS.tabId)
        assertEquals("Logs", SiteTabEntity.SYSTEM_LOGS.displayName)
        assertEquals(1, SiteTabEntity.SYSTEM_LOGS.sortOrder)
        assertTrue(SiteTabEntity.SYSTEM_LOGS.isSystem)

        assertEquals("OVERVIEW", SiteTabEntity.SYSTEM_OVERVIEW.tabId)
        assertEquals("Overview", SiteTabEntity.SYSTEM_OVERVIEW.displayName)
        assertEquals(2, SiteTabEntity.SYSTEM_OVERVIEW.sortOrder)
        assertTrue(SiteTabEntity.SYSTEM_OVERVIEW.isSystem)

        val defaultTabs = SiteTabEntity.DEFAULT_TABS
        assertEquals(3, defaultTabs.size)
        assertEquals(listOf("SPECS", "LOGS", "OVERVIEW"), defaultTabs.map { it.tabId })
    }

    @Test
    fun testUniversalFieldEntityCompositeKeyNamespaceSeparation() {
        // Same field_key "STATUS" in two different tabs
        val specsStatus = UniversalFieldEntity(
            fieldKey = "STATUS",
            groupName = "OPERATIONAL",
            defaultValue = "ON AIR",
            sortOrder = 1,
            tabId = "SPECS"
        )

        val tenantStatus = UniversalFieldEntity(
            fieldKey = "STATUS",
            groupName = "SHARING",
            defaultValue = "ACTIVE",
            sortOrder = 1,
            tabId = "TENANT_INFO"
        )

        assertEquals("SPECS", specsStatus.tabId)
        assertEquals("TENANT_INFO", tenantStatus.tabId)
        assertEquals(specsStatus.fieldKey, tenantStatus.fieldKey)
        assertNotEquals(specsStatus, tenantStatus)

        // Default tabId should be "SPECS"
        val defaultField = UniversalFieldEntity(
            fieldKey = "BATTERY_SOH",
            groupName = "POWER",
            defaultValue = "100%",
            sortOrder = 2
        )
        assertEquals("SPECS", defaultField.tabId)
    }

    @Test
    fun testSiteFieldEntityTabIdSupportAndDefault() {
        val defaultField = SiteFieldEntity(
            id = 1L,
            sapId = "DEL-101",
            fieldKey = "SMPS_MAKE",
            fieldValue = "Delta 48V",
            groupName = "POWER"
        )
        assertEquals("SPECS", defaultField.tabId)

        val customTabField = SiteFieldEntity(
            id = 2L,
            sapId = "DEL-101",
            fieldKey = "TENANT_COUNT",
            fieldValue = "3",
            groupName = "TENANTS",
            tabId = "TENANT_INFO"
        )
        assertEquals("TENANT_INFO", customTabField.tabId)
    }

    @Test
    fun testFakeTabDaoCrudOperations() = runTest {
        val storedTabs = mutableListOf<SiteTabEntity>()
        storedTabs.addAll(SiteTabEntity.DEFAULT_TABS)

        val fakeDao = object : SiteTabDao {
            override fun observeAllTabs() = flowOf(storedTabs.sortedBy { it.sortOrder })
            override suspend fun getAllTabs() = storedTabs.sortedBy { it.sortOrder }
            override suspend fun getTabById(tabId: String) = storedTabs.find { it.tabId == tabId }
            override suspend fun insertTab(tab: SiteTabEntity) {
                storedTabs.removeAll { it.tabId == tab.tabId }
                storedTabs.add(tab)
            }
            override suspend fun insertTabs(tabs: List<SiteTabEntity>) {
                tabs.forEach { insertTab(it) }
            }
            override suspend fun deleteTab(tabId: String) {
                storedTabs.removeAll { it.tabId == tabId }
            }
            override suspend fun getTabCount() = storedTabs.size
        }

        val allTabs = fakeDao.getAllTabs()
        assertEquals(3, allTabs.size)
        assertEquals("SPECS", allTabs[0].tabId)

        // Insert custom tab
        val customTab = SiteTabEntity("TENANT_INFO", "Tenant Info", 3, isSystem = false)
        fakeDao.insertTab(customTab)

        val updatedTabs = fakeDao.getAllTabs()
        assertEquals(4, updatedTabs.size)
        assertEquals(customTab, fakeDao.getTabById("TENANT_INFO"))

        // Delete custom tab
        fakeDao.deleteTab("TENANT_INFO")
        assertEquals(3, fakeDao.getAllTabs().size)
        assertNull(fakeDao.getTabById("TENANT_INFO"))
    }

    @Test
    fun testSiteRepositoryTabMethodsAndSystemProtection() = runTest {
        val storedTabs = mutableListOf<SiteTabEntity>()
        storedTabs.addAll(SiteTabEntity.DEFAULT_TABS)

        val storedFields = mutableListOf(
            SiteFieldEntity(1L, "DEL-101", "SMPS", "Delta", "POWER", 1, "SPECS"),
            SiteFieldEntity(2L, "DEL-101", "TENANT_A", "Airtel", "TENANTS", 1, "TENANT_INFO"),
            SiteFieldEntity(3L, "DEL-102", "TENANT_B", "Jio", "TENANTS", 1, "TENANT_INFO")
        )

        val fakeTabDao = object : SiteTabDao {
            override fun observeAllTabs() = flowOf(storedTabs.sortedBy { it.sortOrder })
            override suspend fun getAllTabs() = storedTabs.sortedBy { it.sortOrder }
            override suspend fun getTabById(tabId: String) = storedTabs.find { it.tabId == tabId }
            override suspend fun insertTab(tab: SiteTabEntity) {
                storedTabs.removeAll { it.tabId == tab.tabId }
                storedTabs.add(tab)
            }
            override suspend fun insertTabs(tabs: List<SiteTabEntity>) {
                tabs.forEach { insertTab(it) }
            }
            override suspend fun deleteTab(tabId: String) {
                storedTabs.removeAll { it.tabId == tabId }
            }
            override suspend fun getTabCount() = storedTabs.size
        }

        val fakeFieldDao = Proxy.newProxyInstance(
            FieldDao::class.java.classLoader,
            arrayOf(FieldDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getFieldsForSiteAndTab" -> {
                    val sapId = args[0] as String
                    val tabId = args[1] as String
                    storedFields.filter { it.sapId == sapId && it.tabId == tabId }
                }
                "deleteFieldsForTab" -> {
                    val tabId = args[0] as String
                    storedFields.removeAll { it.tabId == tabId }
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
                "deleteFieldsForTab" -> Unit
                else -> null
            }
        } as UniversalFieldDao

        val fakeSiteDao = Proxy.newProxyInstance(
            SiteDao::class.java.classLoader,
            arrayOf(SiteDao::class.java)
        ) { _, _, _ -> null } as SiteDao

        // Create a test DB providing our fake DAOs
        val fakeDb = object : AppDatabase() {
            override fun siteTabDao(): SiteTabDao = fakeTabDao
            override fun fieldDao(): FieldDao = fakeFieldDao
            override fun universalFieldDao(): UniversalFieldDao = fakeUniversalDao
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

        val repo = SiteRepository(fakeDb)

        // 1. observeAllTabs and getAllTabs
        val tabs = repo.getAllTabs()
        assertEquals(3, tabs.size)
        assertEquals("SPECS", tabs[0].tabId)

        val flowTabs = repo.observeAllTabs().first()
        assertEquals(3, flowTabs.size)

        // 2. getFieldsForSiteAndTab
        val specsFields = repo.getFieldsForSiteAndTab("DEL-101", "SPECS")
        assertEquals(1, specsFields.size)
        assertEquals("SMPS", specsFields[0].fieldKey)

        val tenantFields = repo.getFieldsForSiteAndTab("DEL-101", "TENANT_INFO")
        assertEquals(1, tenantFields.size)
        assertEquals("TENANT_A", tenantFields[0].fieldKey)

        // 3. insertTab
        repo.insertTab("TENANT_INFO", "Tenant Info")
        val tabsWithNew = repo.getAllTabs()
        assertEquals(4, tabsWithNew.size)
        assertEquals("TENANT_INFO", tabsWithNew.last().tabId)
        assertEquals("Tenant Info", tabsWithNew.last().displayName)
        assertFalse(tabsWithNew.last().isSystem)

        // 4. deleteTab system protection
        try {
            repo.deleteTab("SPECS")
            fail("Must throw IllegalStateException when deleting system tab SPECS")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("system tab") == true)
        }

        // 5. deleteTab custom tab deletes tab and related fields
        repo.deleteTab("TENANT_INFO")
        assertEquals(3, repo.getAllTabs().size)
        val remainingFields = repo.getFieldsForSiteAndTab("DEL-101", "TENANT_INFO")
        assertTrue(remainingFields.isEmpty())
    }
}
