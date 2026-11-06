package app.telecom.sitetracker

import app.telecom.sitetracker.data.import.XlsxReader
import org.junit.Assert.assertEquals
import org.junit.Test

class XlsxColumnTest {

    @Test
    fun testParseCellColumn() {
        assertEquals(0, XlsxReader.parseCellColumn("A1"))
        assertEquals(1, XlsxReader.parseCellColumn("B2"))
        assertEquals(2, XlsxReader.parseCellColumn("C3"))
        assertEquals(25, XlsxReader.parseCellColumn("Z1"))
        assertEquals(26, XlsxReader.parseCellColumn("AA1"))
        assertEquals(27, XlsxReader.parseCellColumn("AB1"))
        assertEquals(46, XlsxReader.parseCellColumn("AU1"))
    }
}
