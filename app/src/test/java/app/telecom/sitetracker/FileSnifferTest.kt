package app.telecom.sitetracker

import app.telecom.sitetracker.data.import.DetectedFormat
import app.telecom.sitetracker.data.import.FileSniffer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class FileSnifferTest {

    @Test
    fun testSniffZipAsXlsx() {
        val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00)
        val stream = ByteArrayInputStream(zipHeader)
        assertEquals(DetectedFormat.XLSX, FileSniffer.sniffFormat(stream))
    }

    @Test
    fun testSniffLegacyXlsAsUnsupported() {
        val oleHeader = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(), 0x00, 0x00)
        val stream = ByteArrayInputStream(oleHeader)
        assertEquals(DetectedFormat.XLS_UNSUPPORTED, FileSniffer.sniffFormat(stream))
    }

    @Test
    fun testSniffJsonArray() {
        val json = "[{\"sap_id\":\"DEL101\"}]".toByteArray()
        val stream = ByteArrayInputStream(json)
        assertEquals(DetectedFormat.JSON, FileSniffer.sniffFormat(stream))
    }

    @Test
    fun testSniffCsv() {
        val csv = "sap_id,name,lat,lng\nDEL101,Delhi North,28.6,77.2".toByteArray()
        val stream = ByteArrayInputStream(csv)
        assertEquals(DetectedFormat.CSV, FileSniffer.sniffFormat(stream))
    }
}
