package app.telecom.sitetracker.data.import

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Streamed reader for Excel (.xlsx) files using ZipFile and XmlPullParser.
 * Avoids DOM tree construction to run smoothly within tight memory limits (< 100 MB heap).
 * Does not use Apache POI.
 */
class XlsxReader(private val excelFile: File) {

    data class SheetInfo(
        val name: String,
        val targetPath: String,
        val isHidden: Boolean
    )

    data class SheetHeaderData(
        val headers: List<String>,
        val parentHeaders: List<String?>?,
        val firstDataRowIndex: Int
    )

    /**
     * Lists visible sheets in the workbook.
     */
    fun getSheets(): List<SheetInfo> {
        ZipFile(excelFile).use { zip ->
            val relsMap = readRelationships(zip)
            val wbEntry = zip.getEntry("xl/workbook.xml") ?: return emptyList()

            val parser = createXmlPullParser()
            parser.setInput(zip.getInputStream(wbEntry), "UTF-8")

            val sheets = mutableListOf<SheetInfo>()
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name") ?: "Sheet"
                    val rId = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                        ?: parser.getAttributeValue(null, "r:id")
                    val state = parser.getAttributeValue(null, "state")
                    val isHidden = state.equals("hidden", ignoreCase = true)

                    val target = relsMap[rId] ?: "worksheets/sheet1.xml"
                    val fullPath = if (target.startsWith("xl/")) target else "xl/$target"

                    sheets.add(SheetInfo(name = name, targetPath = fullPath, isHidden = isHidden))
                }
                eventType = parser.next()
            }

            return sheets.filter { !it.isHidden }
        }
    }

    /**
     * Reads shared strings table into memory.
     */
    fun readSharedStrings(): List<String> {
        ZipFile(excelFile).use { zip ->
            val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
            val parser = createXmlPullParser()
            parser.setInput(zip.getInputStream(entry), "UTF-8")

            val list = mutableListOf<String>()
            var eventType = parser.eventType
            var inSi = false
            var inT = false
            val currentBuilder = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "si" -> {
                                inSi = true
                                currentBuilder.setLength(0)
                            }
                            "t" -> if (inSi) inT = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inSi && inT) {
                            currentBuilder.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "t" -> inT = false
                            "si" -> {
                                inSi = false
                                list.add(currentBuilder.toString())
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            return list
        }
    }

    /**
     * Inspects the sheet to detect single-tier vs two-tier headers.
     */
    fun detectHeaders(sheetPath: String, sharedStrings: List<String>): SheetHeaderData {
        ZipFile(excelFile).use { zip ->
            val entry = zip.getEntry(sheetPath) ?: return SheetHeaderData(emptyList(), null, 0)
            val rows = readFirstNRows(zip.getInputStream(entry), sharedStrings, n = 3)

            if (rows.isEmpty()) {
                return SheetHeaderData(emptyList(), null, 0)
            }

            val row1 = rows.getOrNull(0) ?: emptyMap()
            val row2 = rows.getOrNull(1) ?: emptyMap()

            val maxCol = maxOf(
                row1.keys.maxOrNull() ?: 0,
                row2.keys.maxOrNull() ?: 0
            )

            // Check if row 2 appears to be a subheader:
            // Signs: contains words like "make", "rating", "count", "status", "fault", or row 1 has gaps
            var subheaderScore = 0
            for (col in 0..maxCol) {
                val val2 = row2[col]?.trim()?.lowercase() ?: ""
                if (val2.contains("make") || val2.contains("rating") || val2.contains("count") ||
                    val2.contains("status") || val2.contains("details") || val2.contains("remarks") ||
                    val2.contains("model")
                ) {
                    subheaderScore++
                }
            }

            val isTwoTier = subheaderScore >= 3 && rows.size >= 2

            if (isTwoTier) {
                val combinedHeaders = mutableListOf<String>()
                val parentHeaders = mutableListOf<String?>()

                var currentParent = ""
                for (col in 0..maxCol) {
                    val p = row1[col]?.trim()
                    if (!p.isNullOrEmpty()) {
                        currentParent = p
                    }
                    val child = row2[col]?.trim() ?: ""
                    parentHeaders.add(if (currentParent.isNotEmpty()) currentParent else null)

                    val finalHeader = when {
                        child.isNotEmpty() && currentParent.isNotEmpty() && !child.startsWith(currentParent, ignoreCase = true) ->
                            "$currentParent $child"
                        child.isNotEmpty() -> child
                        currentParent.isNotEmpty() -> currentParent
                        else -> "Column_${col + 1}"
                    }
                    combinedHeaders.add(finalHeader)
                }
                return SheetHeaderData(combinedHeaders, parentHeaders, firstDataRowIndex = 2)
            } else {
                val headers = mutableListOf<String>()
                for (col in 0..maxCol) {
                    val h = row1[col]?.trim() ?: "Column_${col + 1}"
                    headers.add(if (h.isNotEmpty()) h else "Column_${col + 1}")
                }
                return SheetHeaderData(headers, null, firstDataRowIndex = 1)
            }
        }
    }

    /**
     * Reads preview data rows (e.g. up to maxRows).
     */
    fun readPreviewRows(
        sheetPath: String,
        sharedStrings: List<String>,
        firstDataRow: Int,
        maxRows: Int = 5
    ): List<List<String>> {
        ZipFile(excelFile).use { zip ->
            val entry = zip.getEntry(sheetPath) ?: return emptyList()
            return readRowsFromSheet(zip.getInputStream(entry), sharedStrings, skipRows = firstDataRow, maxRows = maxRows)
        }
    }

    /**
     * Streams rows from a given sheet XML input stream.
     */
    fun streamSheetRows(
        sheetPath: String,
        sharedStrings: List<String>,
        skipRows: Int,
        onRow: (rowIndex: Int, cells: Map<Int, String>) -> Unit
    ) {
        ZipFile(excelFile).use { zip ->
            val entry = zip.getEntry(sheetPath) ?: return
            val parser = createXmlPullParser()
            parser.setInput(zip.getInputStream(entry), "UTF-8")

            var eventType = parser.eventType
            var currentRowIndex = -1
            val currentRowMap = mutableMapOf<Int, String>()

            var currentCellCol = -1
            var currentCellType: String? = null
            var inValue = false
            var inInlineStr = false
            var inInlineText = false
            val textBuilder = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "row" -> {
                                val rAttr = parser.getAttributeValue(null, "r")
                                currentRowIndex = rAttr?.toIntOrNull()?.minus(1) ?: (currentRowIndex + 1)
                                currentRowMap.clear()
                            }
                            "c" -> {
                                val ref = parser.getAttributeValue(null, "r")
                                currentCellCol = parseCellColumn(ref)
                                currentCellType = parser.getAttributeValue(null, "t")
                                textBuilder.setLength(0)
                            }
                            "v" -> inValue = true
                            "is" -> inInlineStr = true
                            "t" -> if (inInlineStr) inInlineText = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inValue || (inInlineStr && inInlineText)) {
                            textBuilder.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "v" -> inValue = false
                            "is" -> inInlineStr = false
                            "t" -> inInlineText = false
                            "c" -> {
                                if (currentCellCol >= 0) {
                                    val raw = textBuilder.toString()
                                    val resolved = when (currentCellType) {
                                        "s" -> {
                                            val sIdx = raw.toIntOrNull()
                                            if (sIdx != null && sIdx in sharedStrings.indices) {
                                                sharedStrings[sIdx]
                                            } else raw
                                        }
                                        "b" -> if (raw == "1") "TRUE" else "FALSE"
                                        else -> raw
                                    }
                                    currentRowMap[currentCellCol] = resolved
                                }
                                currentCellCol = -1
                                currentCellType = null
                            }
                            "row" -> {
                                if (currentRowIndex >= skipRows && currentRowMap.isNotEmpty()) {
                                    onRow(currentRowIndex, currentRowMap)
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        }
    }

    private fun readRelationships(zip: ZipFile): Map<String, String> {
        val entry = zip.getEntry("xl/_rels/workbook.xml.rels") ?: return emptyMap()
        val parser = createXmlPullParser()
        parser.setInput(zip.getInputStream(entry), "UTF-8")

        val map = mutableMapOf<String, String>()
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                if (id != null && target != null) {
                    map[id] = target
                }
            }
            eventType = parser.next()
        }
        return map
    }

    private fun readFirstNRows(stream: InputStream, sharedStrings: List<String>, n: Int): List<Map<Int, String>> {
        val result = mutableListOf<Map<Int, String>>()
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        val currentRowMap = mutableMapOf<Int, String>()
        var currentCellCol = -1
        var currentCellType: String? = null
        var inValue = false
        val textBuilder = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT && result.size < n) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRowMap.clear()
                        "c" -> {
                            val ref = parser.getAttributeValue(null, "r")
                            currentCellCol = parseCellColumn(ref)
                            currentCellType = parser.getAttributeValue(null, "t")
                            textBuilder.setLength(0)
                        }
                        "v" -> inValue = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inValue) textBuilder.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> inValue = false
                        "c" -> {
                            if (currentCellCol >= 0) {
                                val raw = textBuilder.toString()
                                val resolved = if (currentCellType == "s") {
                                    val idx = raw.toIntOrNull()
                                    if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                                } else raw
                                currentRowMap[currentCellCol] = resolved
                            }
                        }
                        "row" -> {
                            result.add(HashMap(currentRowMap))
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return result
    }

    private fun readRowsFromSheet(
        stream: InputStream,
        sharedStrings: List<String>,
        skipRows: Int,
        maxRows: Int
    ): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var rowsSkipped = 0

        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        val currentRowMap = mutableMapOf<Int, String>()
        var currentCellCol = -1
        var currentCellType: String? = null
        var inValue = false
        val textBuilder = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT && result.size < maxRows) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRowMap.clear()
                        "c" -> {
                            val ref = parser.getAttributeValue(null, "r")
                            currentCellCol = parseCellColumn(ref)
                            currentCellType = parser.getAttributeValue(null, "t")
                            textBuilder.setLength(0)
                        }
                        "v" -> inValue = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inValue) textBuilder.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> inValue = false
                        "c" -> {
                            if (currentCellCol >= 0) {
                                val raw = textBuilder.toString()
                                val resolved = if (currentCellType == "s") {
                                    val idx = raw.toIntOrNull()
                                    if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                                } else raw
                                currentRowMap[currentCellCol] = resolved
                            }
                        }
                        "row" -> {
                            if (rowsSkipped < skipRows) {
                                rowsSkipped++
                            } else {
                                val maxCol = currentRowMap.keys.maxOrNull() ?: -1
                                val rowList = mutableListOf<String>()
                                for (c in 0..maxCol) {
                                    rowList.add(currentRowMap[c] ?: "")
                                }
                                result.add(rowList)
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return result
    }

    companion object {
        fun createXmlPullParser(): XmlPullParser {
            return try {
                Xml.newPullParser()
            } catch (_: Throwable) {
                val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = false
                factory.newPullParser()
            }
        }

        /**
         * Converts cell reference like "A1", "C3", "AA12" into zero-based column index.
         */
        fun parseCellColumn(ref: String?): Int {
            if (ref.isNullOrEmpty()) return -1
            var col = 0
            for (ch in ref) {
                if (ch in 'A'..'Z') {
                    col = col * 26 + (ch - 'A' + 1)
                } else if (ch in 'a'..'z') {
                    col = col * 26 + (ch - 'a' + 1)
                } else {
                    break // Reached row numbers
                }
            }
            return col - 1
        }
    }
}
