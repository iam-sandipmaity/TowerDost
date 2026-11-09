package app.telecom.sitetracker

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import app.telecom.sitetracker.data.db.AppDatabase
import app.telecom.sitetracker.data.db.FieldDao
import app.telecom.sitetracker.data.db.IncidentDao
import app.telecom.sitetracker.data.db.OutageDao
import app.telecom.sitetracker.data.db.ReasonDao
import app.telecom.sitetracker.data.db.SiteDao
import app.telecom.sitetracker.data.db.SiteTabDao
import app.telecom.sitetracker.data.db.StatsDao
import app.telecom.sitetracker.data.db.UniversalFieldDao
import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.entity.SiteFieldEntity
import app.telecom.sitetracker.data.entity.SiteTabEntity
import app.telecom.sitetracker.data.entity.UniversalFieldEntity
import app.telecom.sitetracker.data.import.TargetField
import app.telecom.sitetracker.data.merger.MergeStrategy
import app.telecom.sitetracker.data.merger.OverwritePolicy
import app.telecom.sitetracker.data.merger.VlookupColumnMapping
import app.telecom.sitetracker.data.merger.VlookupEngine
import app.telecom.sitetracker.util.AppClock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

class VlookupCustomTabTest {

    @Suppress("OVERRIDE_DEPRECATION")
    private class TestDatabase(
        val initialSites: List<SiteEntity> = emptyList(),
        val initialTabs: List<SiteTabEntity> = SiteTabEntity.DEFAULT_TABS,
        val initialFields: List<SiteFieldEntity> = emptyList(),
        val initialUniversalFields: List<UniversalFieldEntity> = emptyList()
    ) : AppDatabase() {

        val storedSites = mutableListOf<SiteEntity>().apply { addAll(initialSites) }
        val storedTabs = mutableListOf<SiteTabEntity>().apply { addAll(initialTabs) }
        val storedFields = mutableListOf<SiteFieldEntity>().apply { addAll(initialFields) }
        val storedUniversalFields = mutableListOf<UniversalFieldEntity>().apply { addAll(initialUniversalFields) }
        val ftsInserts = mutableListOf<app.telecom.sitetracker.data.entity.SiteFts>()

        var inTx = false

        override val transactionExecutor: Executor = Executor { it.run() }
        @Suppress("DEPRECATION")
        override fun beginTransaction() { inTx = true }
        @Suppress("DEPRECATION")
        override fun setTransactionSuccessful() {}
        @Suppress("DEPRECATION")
        override fun endTransaction() { inTx = false }
        override fun inTransaction(): Boolean = inTx

        override fun clearAllTables() {}
        override fun createInvalidationTracker(): InvalidationTracker = InvalidationTracker(this, "site_tabs")
        override fun createOpenHelper(config: DatabaseConfiguration) = throw UnsupportedOperationException()

        private val fakeSiteDao = Proxy.newProxyInstance(
            SiteDao::class.java.classLoader,
            arrayOf(SiteDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getAllActiveSitesList" -> storedSites.toList()
                "getSiteBySapId" -> storedSites.find { it.sapId == args[0] }
                "getSiteBySapNorm" -> storedSites.find { it.sapNorm == args[0] }
                "insertSite" -> {
                    storedSites.add(args[0] as SiteEntity)
                    Unit
                }
                "updateSite" -> {
                    val updated = args[0] as SiteEntity
                    storedSites.removeAll { it.sapId == updated.sapId }
                    storedSites.add(updated)
                    Unit
                }
                "deleteFts" -> Unit
                "insertFts" -> {
                    @Suppress("UNCHECKED_CAST")
                    val list = args[0] as List<app.telecom.sitetracker.data.entity.SiteFts>
                    ftsInserts.addAll(list)
                    Unit
                }
                else -> null
            }
        } as SiteDao

        private val fakeTabDao = object : SiteTabDao {
            override fun observeAllTabs() = flowOf(storedTabs.sortedBy { it.sortOrder })
            override suspend fun getAllTabs(): List<SiteTabEntity> = storedTabs.sortedBy { it.sortOrder }
            override suspend fun getTabById(tabId: String): SiteTabEntity? =
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
            override suspend fun getTabCount(): Int = storedTabs.size
        }

        private val fakeFieldDao = Proxy.newProxyInstance(
            FieldDao::class.java.classLoader,
            arrayOf(FieldDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getFieldsForSite" -> {
                    val sap = args[0] as String
                    storedFields.filter { it.sapId == sap }
                }
                "getFieldsForSiteAndTab" -> {
                    val sap = args[0] as String
                    val tabId = args[1] as String
                    storedFields.filter { it.sapId == sap && it.tabId.equals(tabId, ignoreCase = true) }
                }
                "insertField" -> {
                    val f = args[0] as SiteFieldEntity
                    val newId = (storedFields.maxOfOrNull { it.id } ?: 0L) + 1L
                    val saved = f.copy(id = newId)
                    storedFields.add(saved)
                    newId
                }
                "insertFields" -> {
                    @Suppress("UNCHECKED_CAST")
                    val list = args[0] as List<SiteFieldEntity>
                    list.forEach { f ->
                        val newId = (storedFields.maxOfOrNull { it.id } ?: 0L) + 1L
                        storedFields.add(f.copy(id = newId))
                    }
                    Unit
                }
                "updateField" -> {
                    val f = args[0] as SiteFieldEntity
                    storedFields.removeAll { it.id == f.id || (it.sapId == f.sapId && it.fieldKey.equals(f.fieldKey, ignoreCase = true) && it.tabId.equals(f.tabId, ignoreCase = true)) }
                    storedFields.add(f)
                    Unit
                }
                "getAllFields" -> storedFields.toList()
                else -> null
            }
        } as FieldDao

        private val fakeUniversalFieldDao = object : UniversalFieldDao {
            override fun observeAllUniversalFields() = flowOf(storedUniversalFields.sortedBy { it.sortOrder })
            override suspend fun getAllUniversalFields(): List<UniversalFieldEntity> = storedUniversalFields.sortedBy { it.sortOrder }
            override fun observeUniversalFieldsForTab(tabId: String) =
                flowOf(storedUniversalFields.filter { it.tabId.equals(tabId, ignoreCase = true) }.sortedBy { it.sortOrder })
            override suspend fun getUniversalFieldsForTab(tabId: String): List<UniversalFieldEntity> =
                storedUniversalFields.filter { it.tabId.equals(tabId, ignoreCase = true) }.sortedBy { it.sortOrder }
            override suspend fun insertOrUpdateField(field: UniversalFieldEntity) {
                storedUniversalFields.removeAll {
                    it.tabId.equals(field.tabId, ignoreCase = true) && it.fieldKey.equals(field.fieldKey, ignoreCase = true)
                }
                storedUniversalFields.add(field)
            }
            override suspend fun insertOrUpdateFields(fields: List<UniversalFieldEntity>) {
                fields.forEach { insertOrUpdateField(it) }
            }
            override suspend fun deleteField(fieldKey: String) {
                storedUniversalFields.removeAll { it.fieldKey.equals(fieldKey, ignoreCase = true) }
            }
            override suspend fun deleteField(tabId: String, fieldKey: String) {
                storedUniversalFields.removeAll {
                    it.tabId.equals(tabId, ignoreCase = true) && it.fieldKey.equals(fieldKey, ignoreCase = true)
                }
            }
            override suspend fun deleteSection(groupName: String) {
                storedUniversalFields.removeAll { it.groupName.equals(groupName, ignoreCase = true) }
            }
            override suspend fun deleteSection(tabId: String, groupName: String) {
                storedUniversalFields.removeAll {
                    it.tabId.equals(tabId, ignoreCase = true) && it.groupName.equals(groupName, ignoreCase = true)
                }
            }
            override suspend fun deleteFieldsForTab(tabId: String) {
                storedUniversalFields.removeAll { it.tabId.equals(tabId, ignoreCase = true) }
            }
            override suspend fun clearAll() {
                storedUniversalFields.clear()
            }
        }

        override fun siteDao(): SiteDao = fakeSiteDao
        override fun siteTabDao(): SiteTabDao = fakeTabDao
        override fun fieldDao(): FieldDao = fakeFieldDao
        override fun universalFieldDao(): UniversalFieldDao = fakeUniversalFieldDao
        override fun reasonDao(): ReasonDao = throw UnsupportedOperationException()
        override fun incidentDao(): IncidentDao = throw UnsupportedOperationException()
        override fun outageDao(): OutageDao = throw UnsupportedOperationException()
        override fun statsDao(): StatsDao = throw UnsupportedOperationException()
    }

    private val fakeClock = object : AppClock {
        override fun currentTimeMillis(): Long = 1700000000000L
    }

    @Test
    fun testVlookupCustomTabMergePersistsTabAndUniversalFieldAndSiteFields() = runTest {
        val existingSite = SiteEntity(
            sapId = "DEL-101",
            sapNorm = "DEL101",
            name = "Connaught Place Hub"
        )
        val db = TestDatabase(initialSites = listOf(existingSite))
        val engine = VlookupEngine(db, fakeClock)

        val rows = listOf(
            listOf("DEL-101", "EB-987654", "15 KW")
        )

        val mappings = listOf(
            VlookupColumnMapping(
                columnIndex = 0,
                sourceHeader = "SAP ID",
                targetField = TargetField.SAP_ID
            ),
            VlookupColumnMapping(
                columnIndex = 1,
                sourceHeader = "EB METER NO",
                targetField = TargetField.CUSTOM_SPEC,
                specGroup = "METERS",
                specKey = "METER_NO",
                tabId = "EB_BILLING",
                tabDisplayName = "EB Billing"
            ),
            VlookupColumnMapping(
                columnIndex = 2,
                sourceHeader = "SANCTIONED LOAD",
                targetField = TargetField.CUSTOM_SPEC,
                specGroup = "LOAD",
                specKey = "SANCTIONED_LOAD",
                tabId = "EB_BILLING",
                tabDisplayName = "EB Billing"
            )
        )

        val result = engine.executeMerge(
            rows = rows,
            keyIndex = 0,
            mappings = mappings,
            mergeStrategy = MergeStrategy.UPDATE_EXISTING_ONLY,
            overwritePolicy = OverwritePolicy.OVERWRITE_ALL
        )

        assertEquals(0, result.sitesUpdated)
        assertEquals(2, result.specsUpdated)

        // 1. Verify custom tab was inserted into site_tabs
        val allTabs = db.storedTabs
        val ebTab = allTabs.find { it.tabId == "EB_BILLING" }
        assertNotNull("EB_BILLING tab must be created in site_tabs", ebTab)
        assertEquals("EB Billing", ebTab?.displayName)
        assertFalse("Custom tab must not be marked system", ebTab!!.isSystem)

        // 2. Verify universal fields blueprint was registered
        val allUniversal = db.storedUniversalFields
        val meterUField = allUniversal.find { it.tabId == "EB_BILLING" && it.fieldKey == "METER_NO" }
        assertNotNull("METER_NO blueprint must be registered in universal_fields for EB_BILLING", meterUField)
        assertEquals("METERS", meterUField?.groupName)

        val loadUField = allUniversal.find { it.tabId == "EB_BILLING" && it.fieldKey == "SANCTIONED_LOAD" }
        assertNotNull("SANCTIONED_LOAD blueprint must be registered in universal_fields for EB_BILLING", loadUField)
        assertEquals("LOAD", loadUField?.groupName)

        // 3. Verify site_fields has entries with tab_id = "EB_BILLING"
        val siteFields = db.storedFields.filter { it.sapId == "DEL-101" }
        assertEquals(2, siteFields.size)

        val meterField = siteFields.find { it.fieldKey == "METER_NO" }
        assertNotNull(meterField)
        assertEquals("EB_BILLING", meterField?.tabId)
        assertEquals("METERS", meterField?.groupName)
        assertEquals("EB-987654", meterField?.fieldValue)

        val loadField = siteFields.find { it.fieldKey == "SANCTIONED_LOAD" }
        assertNotNull(loadField)
        assertEquals("EB_BILLING", loadField?.tabId)
        assertEquals("LOAD", loadField?.groupName)
        assertEquals("15 KW", loadField?.fieldValue)
    }

    @Test
    fun testCustomTabUpdatePreservesSameKeyInDifferentTab() = runTest {
        val existingSite = SiteEntity(
            sapId = "DEL-101",
            sapNorm = "DEL101",
            name = "Connaught Place Hub"
        )
        // Existing site already has "METER_NO" under SPECS tab, and "METER_NO" under EB_BILLING tab
        val existingSpecsField = SiteFieldEntity(
            id = 10L,
            sapId = "DEL-101",
            fieldKey = "METER_NO",
            fieldValue = "SPECS_OLD_METER",
            groupName = "POWER",
            tabId = "SPECS"
        )
        val existingEbField = SiteFieldEntity(
            id = 20L,
            sapId = "DEL-101",
            fieldKey = "METER_NO",
            fieldValue = "EB_OLD_METER",
            groupName = "METERS",
            tabId = "EB_BILLING"
        )

        val db = TestDatabase(
            initialSites = listOf(existingSite),
            initialTabs = SiteTabEntity.DEFAULT_TABS + listOf(SiteTabEntity("EB_BILLING", "EB Billing", 3, false)),
            initialFields = listOf(existingSpecsField, existingEbField)
        )
        val engine = VlookupEngine(db, fakeClock)

        // Incoming sheet updates EB_BILLING METER_NO to "EB_NEW_METER"
        val rows = listOf(
            listOf("DEL-101", "EB_NEW_METER")
        )
        val mappings = listOf(
            VlookupColumnMapping(columnIndex = 0, sourceHeader = "SAP ID", targetField = TargetField.SAP_ID),
            VlookupColumnMapping(
                columnIndex = 1,
                sourceHeader = "EB Meter",
                targetField = TargetField.CUSTOM_SPEC,
                specGroup = "METERS",
                specKey = "METER_NO",
                tabId = "EB_BILLING",
                tabDisplayName = "EB Billing"
            )
        )

        val result = engine.executeMerge(
            rows = rows,
            keyIndex = 0,
            mappings = mappings,
            mergeStrategy = MergeStrategy.UPDATE_EXISTING_ONLY,
            overwritePolicy = OverwritePolicy.OVERWRITE_ALL
        )

        assertEquals(1, result.specsUpdated)

        val specsFieldAfter = db.storedFields.find { it.sapId == "DEL-101" && it.tabId == "SPECS" && it.fieldKey == "METER_NO" }
        val ebFieldAfter = db.storedFields.find { it.sapId == "DEL-101" && it.tabId == "EB_BILLING" && it.fieldKey == "METER_NO" }

        // Specs tab must remain untouched
        assertEquals("SPECS_OLD_METER", specsFieldAfter?.fieldValue)
        // EB_BILLING tab must be updated
        assertEquals("EB_NEW_METER", ebFieldAfter?.fieldValue)
    }

    @Test
    fun testNewSiteCreationWithCustomTabAttributes() = runTest {
        val db = TestDatabase(initialSites = emptyList())
        val engine = VlookupEngine(db, fakeClock)

        val rows = listOf(
            listOf("MUM-505", "Mumbai Marine Drive", "Airtel 4G", "3 RRUS")
        )

        val mappings = listOf(
            VlookupColumnMapping(columnIndex = 0, sourceHeader = "SAP ID", targetField = TargetField.SAP_ID),
            VlookupColumnMapping(columnIndex = 1, sourceHeader = "Site Name", targetField = TargetField.NAME),
            VlookupColumnMapping(columnIndex = 2, sourceHeader = "Tenant", targetField = TargetField.CUSTOM_SPEC, specGroup = "AIRTEL", specKey = "TENANT_NAME", tabId = "TENANT_INFO", tabDisplayName = "Tenant Info"),
            VlookupColumnMapping(columnIndex = 3, sourceHeader = "RRU Count", targetField = TargetField.CUSTOM_SPEC, specGroup = "AIRTEL", specKey = "RRU_COUNT", tabId = "TENANT_INFO", tabDisplayName = "Tenant Info")
        )

        val result = engine.executeMerge(
            rows = rows,
            keyIndex = 0,
            mappings = mappings,
            mergeStrategy = MergeStrategy.UPDATE_AND_CREATE_NEW,
            overwritePolicy = OverwritePolicy.OVERWRITE_ALL
        )

        assertEquals(1, result.sitesCreated)
        assertEquals(2, result.specsUpdated)

        // Verify site created
        val createdSite = db.storedSites.find { it.sapId == "MUM-505" }
        assertNotNull(createdSite)
        assertEquals("Mumbai Marine Drive", createdSite?.name)

        // Verify TENANT_INFO tab created
        val tenantTab = db.storedTabs.find { it.tabId == "TENANT_INFO" }
        assertNotNull(tenantTab)
        assertEquals("Tenant Info", tenantTab?.displayName)

        // Verify universal fields created
        val universalTenant = db.storedUniversalFields.filter { it.tabId == "TENANT_INFO" }
        assertEquals(2, universalTenant.size)
        assertTrue(universalTenant.any { it.fieldKey == "TENANT_NAME" && it.groupName == "AIRTEL" })
        assertTrue(universalTenant.any { it.fieldKey == "RRU_COUNT" && it.groupName == "AIRTEL" })

        // Verify site_fields created with tabId = "TENANT_INFO"
        val siteFields = db.storedFields.filter { it.sapId == "MUM-505" }
        assertEquals(2, siteFields.size)
        assertTrue(siteFields.all { it.tabId == "TENANT_INFO" })
        assertTrue(siteFields.all { it.groupName == "AIRTEL" })
    }

    @Test
    fun testVlookupUpdateCoreFieldAndCustomTabSpecIncrementsBothCounters() = runTest {
        val existingSite = SiteEntity(
            sapId = "DEL-101",
            sapNorm = "DEL101",
            name = "Old Hub Name"
        )
        val db = TestDatabase(initialSites = listOf(existingSite))
        val engine = VlookupEngine(db, fakeClock)

        val rows = listOf(
            listOf("DEL-101", "New CP Hub Name", "METER-999")
        )

        val mappings = listOf(
            VlookupColumnMapping(columnIndex = 0, sourceHeader = "SAP ID", targetField = TargetField.SAP_ID),
            VlookupColumnMapping(columnIndex = 1, sourceHeader = "Site Name", targetField = TargetField.NAME),
            VlookupColumnMapping(
                columnIndex = 2,
                sourceHeader = "Meter No",
                targetField = TargetField.CUSTOM_SPEC,
                specGroup = "METERS",
                specKey = "METER_NO",
                tabId = "EB_BILLING",
                tabDisplayName = "EB Billing"
            )
        )

        val result = engine.executeMerge(
            rows = rows,
            keyIndex = 0,
            mappings = mappings,
            mergeStrategy = MergeStrategy.UPDATE_EXISTING_ONLY,
            overwritePolicy = OverwritePolicy.OVERWRITE_ALL
        )

        assertEquals(1, result.sitesUpdated)
        assertEquals(1, result.specsUpdated)

        val updatedSite = db.storedSites.find { it.sapId == "DEL-101" }
        assertEquals("New CP Hub Name", updatedSite?.name)

        val updatedField = db.storedFields.find { it.sapId == "DEL-101" && it.tabId == "EB_BILLING" }
        assertNotNull(updatedField)
        assertEquals("METER-999", updatedField?.fieldValue)
    }

    @Test
    fun testFillBlanksOnlyPreservesExistingCustomTabValues() = runTest {
        val existingSite = SiteEntity(
            sapId = "DEL-101",
            sapNorm = "DEL101",
            name = "Connaught Place Hub"
        )
        val existingMeterField = SiteFieldEntity(
            id = 1L,
            sapId = "DEL-101",
            fieldKey = "METER_NO",
            fieldValue = "PREEXISTING_METER_NO",
            groupName = "METERS",
            tabId = "EB_BILLING"
        )
        val existingTariffField = SiteFieldEntity(
            id = 2L,
            sapId = "DEL-101",
            fieldKey = "TARIFF",
            fieldValue = null,
            groupName = "METERS",
            tabId = "EB_BILLING"
        )

        val db = TestDatabase(
            initialSites = listOf(existingSite),
            initialTabs = SiteTabEntity.DEFAULT_TABS + listOf(SiteTabEntity("EB_BILLING", "EB Billing", 3, false)),
            initialFields = listOf(existingMeterField, existingTariffField)
        )
        val engine = VlookupEngine(db, fakeClock)

        val rows = listOf(
            listOf("DEL-101", "NEW_INCOMING_METER", "COMMERCIAL_LT")
        )

        val mappings = listOf(
            VlookupColumnMapping(columnIndex = 0, sourceHeader = "SAP ID", targetField = TargetField.SAP_ID),
            VlookupColumnMapping(
                columnIndex = 1,
                sourceHeader = "Meter No",
                targetField = TargetField.CUSTOM_SPEC,
                specGroup = "METERS",
                specKey = "METER_NO",
                tabId = "EB_BILLING",
                tabDisplayName = "EB Billing"
            ),
            VlookupColumnMapping(
                columnIndex = 2,
                sourceHeader = "Tariff",
                targetField = TargetField.CUSTOM_SPEC,
                specGroup = "METERS",
                specKey = "TARIFF",
                tabId = "EB_BILLING",
                tabDisplayName = "EB Billing"
            )
        )

        val result = engine.executeMerge(
            rows = rows,
            keyIndex = 0,
            mappings = mappings,
            mergeStrategy = MergeStrategy.UPDATE_EXISTING_ONLY,
            overwritePolicy = OverwritePolicy.FILL_BLANKS_ONLY
        )

        // Only TARIFF should be updated, METER_NO should be preserved
        assertEquals(1, result.specsUpdated)

        val meterAfter = db.storedFields.find { it.sapId == "DEL-101" && it.tabId == "EB_BILLING" && it.fieldKey == "METER_NO" }
        assertEquals("PREEXISTING_METER_NO", meterAfter?.fieldValue)

        val tariffAfter = db.storedFields.find { it.sapId == "DEL-101" && it.tabId == "EB_BILLING" && it.fieldKey == "TARIFF" }
        assertEquals("COMMERCIAL_LT", tariffAfter?.fieldValue)
    }

    @Test
    fun testNoDuplicateCustomTabsInsertedWhenTabAlreadyExists() = runTest {
        val existingSite = SiteEntity(sapId = "DEL-101", sapNorm = "DEL101")
        val existingCustomTab = SiteTabEntity(
            tabId = "EB_BILLING",
            displayName = "EB Billing",
            sortOrder = 3,
            isSystem = false
        )
        val db = TestDatabase(
            initialSites = listOf(existingSite),
            initialTabs = SiteTabEntity.DEFAULT_TABS + listOf(existingCustomTab)
        )
        val engine = VlookupEngine(db, fakeClock)

        val rows = listOf(listOf("DEL-101", "VAL_1"))
        val mappings = listOf(
            VlookupColumnMapping(columnIndex = 0, sourceHeader = "SAP ID", targetField = TargetField.SAP_ID),
            VlookupColumnMapping(
                columnIndex = 1,
                sourceHeader = "Field 1",
                targetField = TargetField.CUSTOM_SPEC,
                specGroup = "METERS",
                specKey = "FIELD_1",
                tabId = "eb_billing", // case-insensitive match
                tabDisplayName = "EB Billing"
            )
        )

        engine.executeMerge(
            rows = rows,
            keyIndex = 0,
            mappings = mappings,
            mergeStrategy = MergeStrategy.UPDATE_EXISTING_ONLY,
            overwritePolicy = OverwritePolicy.OVERWRITE_ALL
        )

        // Ensure no duplicate EB_BILLING tab was inserted
        val ebTabs = db.storedTabs.filter { it.tabId.equals("EB_BILLING", ignoreCase = true) }
        assertEquals(1, ebTabs.size)
    }

    @Test
    fun testVlookupColumnMappingDefaultTabValues() {
        val mapping = VlookupColumnMapping(
            columnIndex = 0,
            sourceHeader = "Test Header",
            targetField = TargetField.CUSTOM_SPEC
        )
        assertEquals("SPECS", mapping.tabId)
        assertEquals("Specs", mapping.tabDisplayName)
    }

    @Test
    fun testVlookupMergerUiStateWithAvailableTabs() {
        val defaultState = app.telecom.sitetracker.ui.merger.VlookupMergerUiState()
        assertTrue(defaultState.availableTabs.isEmpty())

        val customTab = SiteTabEntity("AUDIT", "Audit", 3, false)
        val populatedState = defaultState.copy(availableTabs = listOf(customTab))
        assertEquals(1, populatedState.availableTabs.size)
        assertEquals("AUDIT", populatedState.availableTabs.first().tabId)
    }

    @Test
    fun testCanonicalTabIdNormalizationCaseInsensitiveAndTrimmed() = runTest {
        val db = TestDatabase(initialSites = listOf(SiteEntity(sapId = "DEL-101", sapNorm = "DEL101")))
        val engine = VlookupEngine(db, fakeClock)

        // Column mapping with lowercase and whitespace in tabId
        val rows = listOf(listOf("DEL-101", "METER_XYZ"))
        val mappings = listOf(
            VlookupColumnMapping(columnIndex = 0, sourceHeader = "SAP ID", targetField = TargetField.SAP_ID),
            VlookupColumnMapping(
                columnIndex = 1,
                sourceHeader = "Meter No",
                targetField = TargetField.CUSTOM_SPEC,
                specGroup = "METERS",
                specKey = "METER_NO",
                tabId = " eb_billing ",
                tabDisplayName = "EB Billing"
            )
        )

        engine.executeMerge(
            rows = rows,
            keyIndex = 0,
            mappings = mappings,
            mergeStrategy = MergeStrategy.UPDATE_EXISTING_ONLY,
            overwritePolicy = OverwritePolicy.OVERWRITE_ALL
        )

        // 1. Verify SiteTabEntity tabId is canonically uppercase and trimmed
        val createdTab = db.storedTabs.find { it.displayName == "EB Billing" }
        assertNotNull(createdTab)
        assertEquals("EB_BILLING", createdTab?.tabId)

        // 2. Verify UniversalFieldEntity tabId is canonically uppercase and trimmed
        val universalField = db.storedUniversalFields.find { it.fieldKey == "METER_NO" }
        assertNotNull(universalField)
        assertEquals("EB_BILLING", universalField?.tabId)

        // 3. Verify SiteFieldEntity tabId is canonically uppercase and trimmed
        val siteField = db.storedFields.find { it.sapId == "DEL-101" && it.fieldKey == "METER_NO" }
        assertNotNull(siteField)
        assertEquals("EB_BILLING", siteField?.tabId)
    }

    @Test
    fun testCanonicalTabIdNormalizationMatchesExistingSpecsTab() = runTest {
        val db = TestDatabase(initialSites = listOf(SiteEntity(sapId = "DEL-101", sapNorm = "DEL101")))
        val engine = VlookupEngine(db, fakeClock)

        val rows = listOf(listOf("DEL-101", "15KVA"))
        val mappings = listOf(
            VlookupColumnMapping(columnIndex = 0, sourceHeader = "SAP ID", targetField = TargetField.SAP_ID),
            VlookupColumnMapping(
                columnIndex = 1,
                sourceHeader = "DG Rating",
                targetField = TargetField.CUSTOM_SPEC,
                specGroup = "DG",
                specKey = "DG_RATING",
                tabId = " specs ", // lowercase and padded, should match existing SPECS tab
                tabDisplayName = "Specs"
            )
        )

        engine.executeMerge(
            rows = rows,
            keyIndex = 0,
            mappings = mappings,
            mergeStrategy = MergeStrategy.UPDATE_EXISTING_ONLY,
            overwritePolicy = OverwritePolicy.OVERWRITE_ALL
        )

        // SiteFieldEntity must match canonical SPECS tab ID
        val siteField = db.storedFields.find { it.sapId == "DEL-101" && it.fieldKey == "DG_RATING" }
        assertNotNull(siteField)
        assertEquals("SPECS", siteField?.tabId)
    }

    @Test
    fun testUpdatingExistingFieldUpdatesInMemorySpecsForFts() = runTest {
        val existingSite = SiteEntity(
            sapId = "DEL-101",
            sapNorm = "DEL101",
            name = "Connaught Hub"
        )
        val existingSpec = SiteFieldEntity(
            id = 5L,
            sapId = "DEL-101",
            fieldKey = "BATTERY_AH",
            fieldValue = "200AH",
            groupName = "BATTERY",
            tabId = "SPECS"
        )
        val db = TestDatabase(
            initialSites = listOf(existingSite),
            initialFields = listOf(existingSpec)
        )
        val engine = VlookupEngine(db, fakeClock)

        val rows = listOf(listOf("DEL-101", "600AH"))
        val mappings = listOf(
            VlookupColumnMapping(columnIndex = 0, sourceHeader = "SAP ID", targetField = TargetField.SAP_ID),
            VlookupColumnMapping(
                columnIndex = 1,
                sourceHeader = "Battery Capacity",
                targetField = TargetField.CUSTOM_SPEC,
                specGroup = "BATTERY",
                specKey = "BATTERY_AH",
                tabId = "SPECS",
                tabDisplayName = "Specs"
            )
        )

        engine.executeMerge(
            rows = rows,
            keyIndex = 0,
            mappings = mappings,
            mergeStrategy = MergeStrategy.UPDATE_EXISTING_ONLY,
            overwritePolicy = OverwritePolicy.OVERWRITE_ALL
        )

        // FTS index must have received the updated 600AH value, not the stale 200AH
        assertTrue("FTS insert must have occurred", db.ftsInserts.isNotEmpty())
        val lastFts = db.ftsInserts.last()
        assertTrue("FTS fieldText must contain updated spec value 600AH", lastFts.fieldText?.contains("600AH") == true)
        assertFalse("FTS fieldText must not contain stale spec value 200AH", lastFts.fieldText?.contains("200AH") == true)
    }
}
