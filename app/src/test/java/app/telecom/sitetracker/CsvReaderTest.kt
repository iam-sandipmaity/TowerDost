package app.telecom.sitetracker

import app.telecom.sitetracker.data.import.CsvReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

class CsvReaderTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun testDelimiterAutoDetection() {
        val commaCsv = listOf("sap_id,name,lat,lng", "DEL01,Site 1,28.5,77.2")
        assertEquals(',', CsvReader.detectDelimiter(commaCsv))

        val semiCsv = listOf("sap_id;name;lat;lng", "MUM01;Site 2;19.1;72.8")
        assertEquals(';', CsvReader.detectDelimiter(semiCsv))

        val tsv = listOf("sap_id\tname\tlat\tlng", "KOL01\tSite 3\t22.5\t88.3")
        assertEquals('\t', CsvReader.detectDelimiter(tsv))

        val pipeCsv = listOf("sap_id|name|lat|lng", "CHN01|Site 4|13.0|80.2")
        assertEquals('|', CsvReader.detectDelimiter(pipeCsv))
    }

    @Test
    fun testMultiLineQuotedFields() {
        val file = tempFolder.newFile("multiline.csv")
        val content = "sap_id,name,notes\n" +
                "DEL-101,\"Site Alpha\",\"First line\nSecond line of notes\nThird line\"\n" +
                "DEL-102,\"Site Beta with \"\"Escaped Quotes\"\"\",\"Single line note\""
        file.writeText(content, StandardCharsets.UTF_8)

        val reader = CsvReader(file)
        val headers = reader.readHeaders()
        assertEquals(listOf("sap_id", "name", "notes"), headers.headers)

        val rows = mutableListOf<List<String>>()
        reader.streamRecords(headers.delimiter, headers.charset) { row ->
            rows.add(row)
        }

        // Header + 2 data rows
        assertEquals(3, rows.size)

        // Check multi-line notes content
        val alphaNotes = rows[1][2]
        assertTrue(alphaNotes.contains("First line"))
        assertTrue(alphaNotes.contains("Second line of notes"))
        assertTrue(alphaNotes.contains("Third line"))

        // Check escaped quotes
        val betaName = rows[2][1]
        assertEquals("Site Beta with \"Escaped Quotes\"", betaName)
    }

    @Test
    fun testEncodingDetectionWithBom() {
        val file = tempFolder.newFile("bom.csv")
        val content = "\uFEFFsap_id,name\nKOL-001,Kolkata North"
        file.writeBytes(content.toByteArray(StandardCharsets.UTF_8))

        val reader = CsvReader(file)
        val headerData = reader.readHeaders()
        assertEquals(StandardCharsets.UTF_8, headerData.charset)
        assertEquals("sap_id", headerData.headers[0])
    }
}
