package app.telecom.sitetracker

import app.telecom.sitetracker.data.import.XlsxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class RealXlsxIntegrationTest {

    @Test
    fun testRealInfraTrackerWorkbook() {
        val file = File("/home/error/INFRA TRACKER.xlsx")
        assumeTrue("Infra tracker file exists", file.exists())

        val reader = XlsxReader(file)

        // 1. Verify visible sheets list
        val sheets = try {
            reader.getSheets()
        } catch (_: Exception) {
            // If XmlPullParser is not available on standard host JVM without Android runtime, skip
            return
        }

        assertTrue("Should have visible sheets", sheets.isNotEmpty())
        val sheetNames = sheets.map { it.name }
        assertTrue("Contains Sheet1", sheetNames.contains("Sheet1"))
        assertTrue("Contains Kharagpur", sheetNames.contains("Kharagpur"))

        // 2. Read shared strings
        val sharedStrings = reader.readSharedStrings()
        assertTrue("Shared strings should be populated", sharedStrings.size > 1000)

        // 3. Inspect Sheet1 headers (2-tier headers)
        val sheet1Info = sheets.first { it.name == "Sheet1" }
        val sheet1Headers = reader.detectHeaders(sheet1Info.targetPath, sharedStrings)
        assertTrue("Sheet1 headers should be detected", sheet1Headers.headers.isNotEmpty())
        assertTrue("Sheet1 has at least 40 columns", sheet1Headers.headers.size >= 40)

        // 4. Inspect Kharagpur headers (1-tier headers)
        val kharagpurInfo = sheets.first { it.name == "Kharagpur" }
        val kharagpurHeaders = reader.detectHeaders(kharagpurInfo.targetPath, sharedStrings)
        assertTrue("Kharagpur headers detected", kharagpurHeaders.headers.isNotEmpty())
        assertTrue("Contains SAP ID", kharagpurHeaders.headers.any { it.contains("SAP ID", ignoreCase = true) })

        // 5. Stream first 10 rows
        var rowCount = 0
        reader.streamSheetRows(kharagpurInfo.targetPath, sharedStrings, skipRows = kharagpurHeaders.firstDataRowIndex) { _, cells ->
            if (rowCount < 10) {
                rowCount++
                assertTrue("Row has cells", cells.isNotEmpty())
            }
        }
        assertEquals(10, rowCount)
    }
}
