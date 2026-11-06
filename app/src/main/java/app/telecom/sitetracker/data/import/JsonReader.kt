package app.telecom.sitetracker.data.import

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Reader for JSON site master files ([ { "sap_id": "...", ... } ]).
 */
class JsonReader(private val file: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    data class JsonHeaderData(
        val headers: List<String>
    )

    fun readHeaders(): JsonHeaderData {
        val root = parseRoot()
        val headers = linkedSetOf<String>()

        for (item in root) {
            if (item is JsonObject) {
                headers.addAll(item.keys)
            }
        }

        return JsonHeaderData(headers.toList())
    }

    fun readPreviewRows(headers: List<String>, maxRows: Int = 5): List<List<String>> {
        val root = parseRoot()
        val result = mutableListOf<List<String>>()

        for (item in root.take(maxRows)) {
            if (item is JsonObject) {
                val row = headers.map { key ->
                    item[key]?.jsonPrimitive?.content ?: ""
                }
                result.add(row)
            }
        }

        return result
    }

    fun streamRecords(headers: List<String>, onRow: (List<String>) -> Unit) {
        val root = parseRoot()
        for (item in root) {
            if (item is JsonObject) {
                val row = headers.map { key ->
                    item[key]?.jsonPrimitive?.content ?: ""
                }
                onRow(row)
            }
        }
    }

    private fun parseRoot(): JsonArray {
        val content = FileInputStream(file).use { stream ->
            InputStreamReader(stream, StandardCharsets.UTF_8).readText()
        }
        val element = json.parseToJsonElement(content)
        return when (element) {
            is JsonArray -> element
            is JsonObject -> JsonArray(listOf(element))
            else -> JsonArray(emptyList())
        }
    }
}
