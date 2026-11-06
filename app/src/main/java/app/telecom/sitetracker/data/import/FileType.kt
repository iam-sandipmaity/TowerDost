package app.telecom.sitetracker.data.import

import java.io.InputStream

/**
 * File format detected from file stream content and magic bytes.
 */
enum class DetectedFormat(val displayName: String, val isSupported: Boolean) {
    XLSX("Excel Workbook (.xlsx)", true),
    CSV("Delimited Text (.csv / .tsv)", true),
    JSON("JSON Array (.json)", true),
    XLS_UNSUPPORTED("Legacy Excel (.xls)", false),
    UNKNOWN("Unknown Format", false)
}

object FileSniffer {

    private val ZIP_HEADER = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte())
    private val OLE_HEADER = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte())

    /**
     * Sniffs the file format using the first few bytes of the input stream.
     * Note: The stream should support mark/reset or be buffered.
     */
    fun sniffFormat(stream: InputStream): DetectedFormat {
        if (!stream.markSupported()) {
            return DetectedFormat.UNKNOWN
        }

        stream.mark(512)
        val header = ByteArray(512)
        val bytesRead = stream.read(header)
        stream.reset()

        if (bytesRead < 4) return DetectedFormat.UNKNOWN

        // 1. Check ZIP magic bytes (PK\x03\x04) -> XLSX
        if (header[0] == ZIP_HEADER[0] &&
            header[1] == ZIP_HEADER[1] &&
            header[2] == ZIP_HEADER[2] &&
            header[3] == ZIP_HEADER[3]
        ) {
            return DetectedFormat.XLSX
        }

        // 2. Check OLE magic bytes (D0 CF 11 E0) -> Legacy XLS
        if (header[0] == OLE_HEADER[0] &&
            header[1] == OLE_HEADER[1] &&
            header[2] == OLE_HEADER[2] &&
            header[3] == OLE_HEADER[3]
        ) {
            return DetectedFormat.XLS_UNSUPPORTED
        }

        // 3. Check JSON (skip UTF-8 BOM if present, look for [ or {)
        var offset = 0
        if (bytesRead >= 3 && header[0] == 0xEF.toByte() && header[1] == 0xBB.toByte() && header[2] == 0xBF.toByte()) {
            offset = 3
        }
        while (offset < bytesRead && (header[offset] == ' '.code.toByte() || header[offset] == '\t'.code.toByte() || header[offset] == '\n'.code.toByte() || header[offset] == '\r'.code.toByte())) {
            offset++
        }

        if (offset < bytesRead) {
            val firstChar = header[offset].toInt().toChar()
            if (firstChar == '[' || firstChar == '{') {
                return DetectedFormat.JSON
            }
        }

        // 4. Default to CSV if readable plain text
        return DetectedFormat.CSV
    }
}
