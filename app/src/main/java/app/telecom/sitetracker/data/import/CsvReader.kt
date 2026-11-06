package app.telecom.sitetracker.data.import

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Robust CSV/TSV parser supporting delimiter auto-detection,
 * multi-line quoted fields (RFC 4180), and automatic encoding detection.
 */
class CsvReader(private val file: File) {

    data class CsvHeaderData(
        val headers: List<String>,
        val delimiter: Char,
        val charset: Charset
    )

    companion object {
        private val CANDIDATE_DELIMITERS = charArrayOf(',', ';', '\t', '|')

        /**
         * Detects the best delimiter by counting candidates across the first lines.
         */
        fun detectDelimiter(sampleLines: List<String>): Char {
            if (sampleLines.isEmpty()) return ','

            var bestDelimiter = ','
            var maxConsistencyScore = -1

            for (delim in CANDIDATE_DELIMITERS) {
                val counts = sampleLines.map { countDelimiterOutsideQuotes(it, delim) }
                val firstLineCount = counts.firstOrNull() ?: 0

                if (firstLineCount > 0) {
                    // Check how many lines have the exact same count
                    val matchCount = counts.count { it == firstLineCount }
                    val score = matchCount * 100 + firstLineCount
                    if (score > maxConsistencyScore) {
                        maxConsistencyScore = score
                        bestDelimiter = delim
                    }
                }
            }

            return bestDelimiter
        }

        private fun countDelimiterOutsideQuotes(line: String, delim: Char): Int {
            var count = 0
            var inQuotes = false
            for (ch in line) {
                if (ch == '"') {
                    inQuotes = !inQuotes
                } else if (ch == delim && !inQuotes) {
                    count++
                }
            }
            return count
        }

        /**
         * Sniffs character encoding (UTF-8 BOM vs UTF-8 vs Windows-1252).
         */
        fun detectEncoding(file: File): Charset {
            FileInputStream(file).use { stream ->
                val bom = ByteArray(3)
                val read = stream.read(bom)
                if (read >= 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte()) {
                    return StandardCharsets.UTF_8
                }
            }

            // Check if file is valid UTF-8
            return try {
                FileInputStream(file).use { stream ->
                    val buffer = ByteArray(4096)
                    val len = stream.read(buffer)
                    if (len > 0) {
                        val decoder = StandardCharsets.UTF_8.newDecoder()
                        decoder.decode(java.nio.ByteBuffer.wrap(buffer, 0, len))
                    }
                }
                StandardCharsets.UTF_8
            } catch (_: Exception) {
                // Fall back to Windows-1252 (very common for Indian telecom Excel exports)
                Charset.forName("windows-1252")
            }
        }
    }

    /**
     * Inspects headers and detected format.
     */
    fun readHeaders(): CsvHeaderData {
        val charset = detectEncoding(file)
        val sampleLines = mutableListOf<String>()

        BufferedReader(InputStreamReader(FileInputStream(file), charset)).use { reader ->
            var count = 0
            while (count < 10) {
                val line = reader.readLine() ?: break
                if (line.isNotBlank()) {
                    sampleLines.add(line)
                    count++
                }
            }
        }

        val delimiter = detectDelimiter(sampleLines)
        val firstLine = sampleLines.firstOrNull() ?: ""
        val headers = parseLine(firstLine, delimiter).map { it.trim().removePrefix("\uFEFF") }

        return CsvHeaderData(headers, delimiter, charset)
    }

    /**
     * Reads first N rows for preview.
     */
    fun readPreviewRows(delimiter: Char, charset: Charset, maxRows: Int = 5): List<List<String>> {
        val preview = mutableListOf<List<String>>()
        var isHeader = true

        streamRecords(delimiter, charset) { row ->
            if (isHeader) {
                isHeader = false
            } else if (preview.size < maxRows) {
                preview.add(row)
            }
        }
        return preview
    }

    /**
     * Streams all rows from the CSV file handling multi-line quoted fields.
     */
    fun streamRecords(
        delimiter: Char,
        charset: Charset,
        onRow: (List<String>) -> Unit
    ) {
        BufferedReader(InputStreamReader(FileInputStream(file), charset)).use { reader ->
            val currentRow = mutableListOf<String>()
            val currentField = StringBuilder()
            var inQuotes = false

            var charCode = reader.read()
            while (charCode != -1) {
                val c = charCode.toChar()

                when {
                    c == '"' -> {
                        if (inQuotes) {
                            // Check next char for double quote escaping ("")
                            reader.mark(1)
                            val nextCode = reader.read()
                            if (nextCode != -1 && nextCode.toChar() == '"') {
                                currentField.append('"')
                            } else {
                                inQuotes = false
                                reader.reset()
                            }
                        } else {
                            inQuotes = true
                        }
                    }
                    c == delimiter && !inQuotes -> {
                        currentRow.add(currentField.toString().trim())
                        currentField.setLength(0)
                    }
                    (c == '\r' || c == '\n') && !inQuotes -> {
                        // End of record
                        if (c == '\r') {
                            reader.mark(1)
                            val nextCode = reader.read()
                            if (nextCode != -1 && nextCode.toChar() != '\n') {
                                reader.reset()
                            }
                        }
                        currentRow.add(currentField.toString().trim())
                        currentField.setLength(0)

                        if (currentRow.any { it.isNotEmpty() }) {
                            onRow(ArrayList(currentRow))
                        }
                        currentRow.clear()
                    }
                    else -> {
                        currentField.append(c)
                    }
                }
                charCode = reader.read()
            }

            // Flush last record if present
            if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
                currentRow.add(currentField.toString().trim())
                if (currentRow.any { it.isNotEmpty() }) {
                    onRow(currentRow)
                }
            }
        }
    }

    private fun parseLine(line: String, delimiter: Char): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == delimiter && !inQuotes) {
                fields.add(current.toString().trim())
                current.setLength(0)
            } else {
                current.append(c)
            }
            i++
        }
        fields.add(current.toString().trim())
        return fields
    }
}
