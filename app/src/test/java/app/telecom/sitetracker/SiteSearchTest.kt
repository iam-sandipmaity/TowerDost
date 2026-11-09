package app.telecom.sitetracker

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
import app.telecom.sitetracker.data.repo.SiteRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSearchTest {

    @Test
    fun testBuildFtsQuerySanitization() {
        // Standard single term
        assertEquals("Kolkata*", SiteRepository.buildFtsQuery("Kolkata"))

        // Multiple words (whitespace split)
        assertEquals("Salt* Lake*", SiteRepository.buildFtsQuery("Salt Lake"))

        // Punctuation and dashes stripped so FTS doesn't interpret '-' as NOT operator
        assertEquals("WB* KOL* 1001*", SiteRepository.buildFtsQuery("WB-KOL-1001"))

        // Leading asterisks or quotes stripped
        assertEquals("delhi*", SiteRepository.buildFtsQuery("*\"delhi\"*"))

        // Slashes and special chars
        assertEquals("4G* 5G*", SiteRepository.buildFtsQuery("4G/5G"))

        // Empty and blank
        assertEquals("", SiteRepository.buildFtsQuery(""))
        assertEquals("", SiteRepository.buildFtsQuery("   "))
        assertEquals("", SiteRepository.buildFtsQuery("---***///"))
    }

    @Test
    fun testNormalizedSapIdSearchMatching() {
        val site1 = SiteEntity(
            sapId = "WB-KOL-1001",
            sapNorm = "WBKOL1001",
            name = "Kolkata Central Hub",
            city = "Kolkata"
        )
        val site2 = SiteEntity(
            sapId = "WB-KOL-1002",
            sapNorm = "WBKOL1002",
            name = "Kolkata South Hub",
            city = "Kolkata"
        )
        val site3 = SiteEntity(
            sapId = "WB-KOL-1003",
            sapNorm = "WBKOL1003",
            name = "Salt Lake Tower",
            city = "Kolkata"
        )

        val sites = listOf(site1, site2, site3)

        // Searching "Kolkata" matches all 3 sites
        val query = "Kolkata"
        val matches = sites.filter { s ->
            s.name?.contains(query, ignoreCase = true) == true ||
            s.city?.contains(query, ignoreCase = true) == true ||
            s.sapId.contains(query, ignoreCase = true)
        }
        assertEquals(3, matches.size)

        // Searching "wb kol 1002" with normalization matches site2
        val input = "wb kol 1002"
        val normQuery = SiteEntity.normalize(input)
        assertEquals("WBKOL1002", normQuery)
        val normMatches = sites.filter { it.sapNorm == normQuery }
        assertEquals(1, normMatches.size)
        assertEquals("WB-KOL-1002", normMatches.first().sapId)
    }

    @Test
    fun testMultipleResultsDeduplicationAndOrderPreserved() {
        val s1 = SiteEntity(sapId = "S1", sapNorm = "S1", name = "Connaught Hub 1")
        val s2 = SiteEntity(sapId = "S2", sapNorm = "S2", name = "Connaught Hub 2")
        val s3 = SiteEntity(sapId = "S3", sapNorm = "S3", name = "Connaught Hub 3")

        val directResults = listOf(s1, s2)
        val ftsResults = listOf(s2, s3)

        val combined = LinkedHashMap<String, SiteEntity>()
        for (site in directResults) {
            combined[site.sapId] = site
        }
        for (site in ftsResults) {
            if (!combined.containsKey(site.sapId)) {
                combined[site.sapId] = site
            }
        }

        val allResults = combined.values.toList()

        // All 3 matching sites are returned, no site is skipped
        assertEquals(3, allResults.size)
        assertEquals("S1", allResults[0].sapId)
        assertEquals("S2", allResults[1].sapId)
        assertEquals("S3", allResults[2].sapId)
    }

    private fun createMockDatabase(
        storedSites: List<SiteEntity>,
        directSearchResults: (String, String) -> List<SiteEntity> = { q, _ ->
            storedSites.filter {
                it.name?.contains(q, ignoreCase = true) == true ||
                it.sapId.contains(q, ignoreCase = true) ||
                it.city?.contains(q, ignoreCase = true) == true
            }
        },
        ftsSearchResults: (String) -> List<SiteEntity> = { _ -> emptyList() }
    ): AppDatabase {
        val fakeSiteDao = Proxy.newProxyInstance(
            SiteDao::class.java.classLoader,
            arrayOf(SiteDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "searchDirect" -> {
                    val query = args[0] as String
                    val normQuery = if (args.size > 1 && args[1] != null) args[1] as String else ""
                    directSearchResults(query, normQuery)
                }
                "searchFts" -> {
                    val query = args[0] as String
                    ftsSearchResults(query)
                }
                "getAllActiveSitesList" -> storedSites.filter { it.deletedAt == null }
                else -> null
            }
        } as SiteDao

        val fakeFieldDao = Proxy.newProxyInstance(FieldDao::class.java.classLoader, arrayOf(FieldDao::class.java)) { _, _, _ -> null } as FieldDao
        val fakeUniversalDao = Proxy.newProxyInstance(UniversalFieldDao::class.java.classLoader, arrayOf(UniversalFieldDao::class.java)) { _, _, _ -> null } as UniversalFieldDao
        val fakeTabDao = Proxy.newProxyInstance(SiteTabDao::class.java.classLoader, arrayOf(SiteTabDao::class.java)) { _, method, _ ->
            if (method.name == "observeAllTabs") flowOf(emptyList<Any>()) else null
        } as SiteTabDao

        return object : AppDatabase() {
            override val transactionExecutor = java.util.concurrent.Executor { it.run() }
            override fun siteDao(): SiteDao = fakeSiteDao
            override fun fieldDao(): FieldDao = fakeFieldDao
            override fun universalFieldDao(): UniversalFieldDao = fakeUniversalDao
            override fun reasonDao(): ReasonDao = Proxy.newProxyInstance(ReasonDao::class.java.classLoader, arrayOf(ReasonDao::class.java)) { _, _, _ -> null } as ReasonDao
            override fun incidentDao(): IncidentDao = Proxy.newProxyInstance(IncidentDao::class.java.classLoader, arrayOf(IncidentDao::class.java)) { _, _, _ -> null } as IncidentDao
            override fun outageDao(): OutageDao = Proxy.newProxyInstance(OutageDao::class.java.classLoader, arrayOf(OutageDao::class.java)) { _, _, _ -> null } as OutageDao
            override fun statsDao(): StatsDao = Proxy.newProxyInstance(StatsDao::class.java.classLoader, arrayOf(StatsDao::class.java)) { _, _, _ -> null } as StatsDao
            override fun siteTabDao(): SiteTabDao = fakeTabDao
            override fun clearAllTables() {}
            override fun createInvalidationTracker(): androidx.room.InvalidationTracker =
                androidx.room.InvalidationTracker(this, "sites")
            override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): androidx.sqlite.db.SupportSQLiteOpenHelper {
                throw UnsupportedOperationException()
            }
        }
    }

    @Test
    fun testSearchSitesReturnsAllMatchingSitesForName() = runTest {
        val sites = listOf(
            SiteEntity(sapId = "WB-KOL-1001", sapNorm = "WBKOL1001", name = "Kolkata Central Hub", city = "Kolkata"),
            SiteEntity(sapId = "WB-KOL-1002", sapNorm = "WBKOL1002", name = "Kolkata South Hub", city = "Kolkata"),
            SiteEntity(sapId = "WB-KOL-1003", sapNorm = "WBKOL1003", name = "Salt Lake Tower", city = "Kolkata"),
            SiteEntity(sapId = "DL-DEL-2001", sapNorm = "DLDEL2001", name = "Delhi Airport Hub", city = "Delhi")
        )

        val db = createMockDatabase(sites)
        val repo = SiteRepository(db)

        // Searching "Kolkata" must return all 3 Kolkata sites, not just 1
        val results = repo.searchSites("Kolkata")
        assertEquals(3, results.size)
        assertTrue(results.any { it.sapId == "WB-KOL-1001" })
        assertTrue(results.any { it.sapId == "WB-KOL-1002" })
        assertTrue(results.any { it.sapId == "WB-KOL-1003" })
        assertFalse(results.any { it.sapId == "DL-DEL-2001" })
    }

    @Test
    fun testSearchSitesMultiWordOutOfOrderFallback() = runTest {
        val sites = listOf(
            SiteEntity(sapId = "WB-01", sapNorm = "WB01", name = "Kolkata Central Hub"),
            SiteEntity(sapId = "WB-02", sapNorm = "WB02", name = "Kolkata South Hub"),
            SiteEntity(sapId = "DL-01", sapNorm = "DL01", name = "Delhi Hub")
        )

        // Direct SQL search fails for inverted words "Hub Kolkata", but tokenized fallback must catch it
        val db = createMockDatabase(
            storedSites = sites,
            directSearchResults = { query, _ ->
                if (query == "Hub Kolkata") emptyList() else sites.filter { it.name?.contains(query) == true }
            }
        )
        val repo = SiteRepository(db)

        val results = repo.searchSites("Hub Kolkata")
        assertEquals(2, results.size)
        assertTrue(results.any { it.sapId == "WB-01" })
        assertTrue(results.any { it.sapId == "WB-02" })
    }

    @Test
    fun testSearchSitesRobustWhenDirectSearchThrowsException() = runTest {
        val sites = listOf(
            SiteEntity(sapId = "WB-01", sapNorm = "WB01", name = "Kharagpur Station Tower"),
            SiteEntity(sapId = "WB-02", sapNorm = "WB02", name = "Kharagpur Town Hub")
        )

        // Direct search simulates SQLite runtime exception
        val db = createMockDatabase(
            storedSites = sites,
            directSearchResults = { _, _ ->
                throw android.database.sqlite.SQLiteException("ORDER BY term does not match any column in the result set")
            }
        )
        val repo = SiteRepository(db)

        // Even if direct search throws an SQLiteException, robust token matching across allActive must return all matching sites
        val results = repo.searchSites("Kharagpur")
        assertEquals(2, results.size)
        assertEquals("WB-01", results[0].sapId)
        assertEquals("WB-02", results[1].sapId)
    }
}

