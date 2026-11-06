package app.telecom.sitetracker

import app.telecom.sitetracker.data.import.ColumnMapper
import app.telecom.sitetracker.data.import.TargetField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ColumnMapperTest {

    @Test
    fun testHeaderNormalization() {
        assertEquals("sap id", ColumnMapper.normalizeHeader("  SAP ID  "))
        assertEquals("site name", ColumnMapper.normalizeHeader("Site_Name"))
        assertEquals("energy status", ColumnMapper.normalizeHeader("Energy\n Status"))
        assertEquals("smps make", ColumnMapper.normalizeHeader("SMPS-Make"))
    }

    @Test
    fun testStandardFieldMatching() {
        assertEquals(TargetField.SAP_ID, ColumnMapper.matchField("SAP ID"))
        assertEquals(TargetField.SAP_ID, ColumnMapper.matchField("site_id"))
        assertEquals(TargetField.SAP_ID, ColumnMapper.matchField("Site Code"))

        assertEquals(TargetField.NAME, ColumnMapper.matchField("Site Name"))
        assertEquals(TargetField.LAT, ColumnMapper.matchField("Lat"))
        assertEquals(TargetField.LNG, ColumnMapper.matchField("Long"))

        assertEquals(TargetField.CIRCLE, ColumnMapper.matchField("CMP"))
        assertEquals(TargetField.CITY, ColumnMapper.matchField("JC Name"))
        assertEquals(TargetField.STATUS, ColumnMapper.matchField("Power Status"))
    }

    @Test
    fun testSpecGroupInference() {
        assertEquals("SMPS", ColumnMapper.inferSpecGroup("SMPS Make"))
        assertEquals("Battery Bank", ColumnMapper.inferSpecGroup("BM Rating"))
        assertEquals("Rectifier Module", ColumnMapper.inferSpecGroup("RM Faulty Count"))
        assertEquals("Diesel Generator", ColumnMapper.inferSpecGroup("DG Make"))
        assertEquals("Solar MPPT", ColumnMapper.inferSpecGroup("Total MPPT count"))
        assertEquals("GCU", ColumnMapper.inferSpecGroup("GCU Model"))
        assertEquals("Site Environment", ColumnMapper.inferSpecGroup("VEGETATION STATUS"))
        assertEquals("Technician & Contact", ColumnMapper.inferSpecGroup("Techinician Number"))
    }

    @Test
    fun testGenerateInitialMappingsFromTwoTierHeaders() {
        val headers = listOf("SL No.", "CMP", "SAP ID", "JC Name", "SMPS Make", "BM Rating")
        val parents = listOf(null, null, null, null, "SMPS", "BM")

        val mappings = ColumnMapper.generateInitialMappings(headers, parents)
        assertEquals(6, mappings.size)

        // SAP ID is mapped
        val sapMapping = mappings.firstOrNull { it.targetField == TargetField.SAP_ID }
        assertNotNull(sapMapping)

        // SMPS Make is custom spec with group SMPS
        val smpsMapping = mappings.firstOrNull { it.sourceHeader.contains("SMPS Make") }
        assertNotNull(smpsMapping)
        assertEquals(TargetField.CUSTOM_SPEC, smpsMapping!!.targetField)
        assertEquals("SMPS", smpsMapping.specGroup)
    }
}
