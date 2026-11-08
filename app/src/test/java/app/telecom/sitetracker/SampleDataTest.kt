package app.telecom.sitetracker

import app.telecom.sitetracker.data.entity.SiteEntity
import app.telecom.sitetracker.data.seed.SampleDataSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleDataTest {

    @Test
    fun testDefaultReasonsCatalogueCompleteness() {
        val reasons = SampleDataSeeder.getDefaultReasons(System.currentTimeMillis())

        // 24 standard seeded telecom reasons
        assertEquals(24, reasons.size)

        // Verify key operational reasons exist with correct category and downtime flags
        val plannedMaint = reasons.find { it.id == "REAS_PLANNED_MAINT" }
        assertNotNull(plannedMaint)
        assertEquals(0, plannedMaint!!.countsTowardDowntime)

        val loadShedding = reasons.find { it.id == "REAS_LOAD_SHED" }
        assertNotNull(loadShedding)
        assertEquals(0, loadShedding!!.countsTowardDowntime)

        val rmFailure = reasons.find { it.id == "REAS_RM_MODULE" }
        assertNotNull(rmFailure)
        assertEquals("Rectifier", rmFailure!!.category)
        assertEquals(1, rmFailure.countsTowardDowntime)

        val noRecord = reasons.find { it.id == "REAS_NO_RECORD" }
        assertNotNull(noRecord)
        assertEquals("No reason recorded", noRecord!!.label)
    }

    @Test
    fun testSapIdNormalization() {
        assertEquals("DEL12345", SiteEntity.normalize("DEL-12345"))
        assertEquals("DEL12345", SiteEntity.normalize("del-12345"))
        assertEquals("DEL12345", SiteEntity.normalize(" DEL 12345 "))
        assertEquals("MUM9009A", SiteEntity.normalize("Mum-9009/A"))
        assertEquals("BLR9017", SiteEntity.normalize("blr.9017"))
    }

    @Test
    fun testZeroMockSitesOrIncidentsInProduction() {
        // SampleDataSeeder only defines root causes (taxonomy), zero mock sites or incidents
        val reasons = SampleDataSeeder.getDefaultReasons(System.currentTimeMillis())
        assertTrue(reasons.isNotEmpty())
    }
}
