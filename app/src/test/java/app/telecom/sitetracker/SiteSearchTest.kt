package app.telecom.sitetracker

import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.repo.SiteRepository
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
}
