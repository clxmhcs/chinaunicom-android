package com.clxmhcs.chinaunicom.capture

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * M14-E HAR export over the bounded structured HTTP messages already published by M14-C.
 *
 * CaptureHttpMessage intentionally has no request/response body field, so this exporter can never
 * export body bytes. Sensitive headers were already redacted before publication and remain redacted
 * here. The result is an on-demand, metadata-only HAR 1.2 document intended for explicit user export.
 */
object CaptureHarExporter {
    fun encode(messages: List<CaptureHttpMessage>): ByteArray {
        require(messages.isNotEmpty()) { "暂无可导出的抓包记录" }
        return buildString {
            append("{\n  \"log\": {\n")
            append("    \"version\": \"1.2\",\n")
            append("    \"creator\": {\"name\": \"ChinaUnicom CaptureTool\", \"version\": \"1.0\"},\n")
            append("    \"entries\": [\n")
            messages.forEachIndexed { index, message ->
                appendEntry(message, indent = "      ")
                if (index != messages.lastIndex) append(',')
                append('\n')
            }
            append("    ]\n  }\n}\n")
        }.toByteArray(StandardCharsets.UTF_8)
    }

    fun defaultFileName(nowEpochMillis: Long = System.currentTimeMillis()): String {
        val formatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        return "capture-${formatter.format(Date(nowEpochMillis))}.har"
    }

    private fun StringBuilder.appendEntry(message: CaptureHttpMessage, indent: String) {
        val request = message.kind == CaptureHttpMessageKind.REQUEST
        val requestMethod = if (request) message.method.orEmpty() else ""
        val requestUrl = if (request) requestUrl(message) else ""
        val responseStatus = if (message.kind == CaptureHttpMessageKind.RESPONSE) message.statusCode ?: 0 else 0
        val requestHeaders = if (request) message.headers else emptyMap()
        val responseHeaders = if (message.kind == CaptureHttpMessageKind.RESPONSE) message.headers else emptyMap()

        append(indent).append("{\n")
        append(indent).append("  \"startedDateTime\": ")
        appendJsonString(iso8601(message.capturedAtEpochMillis))
        append(",\n")
        append(indent).append("  \"time\": 0,\n")
        append(indent).append("  \"_captureMessageKind\": ")
        appendJsonString(message.kind.name.lowercase())
        append(",\n")
        append(indent).append("  \"_captureStreamId\": ")
        appendJsonString(message.streamID)
        append(",\n")
        append(indent).append("  \"request\": {\n")
        append(indent).append("    \"method\": ")
        appendJsonString(requestMethod)
        append(",\n")
        append(indent).append("    \"url\": ")
        appendJsonString(requestUrl)
        append(",\n")
        append(indent).append("    \"httpVersion\": \"HTTP/1.1\",\n")
        append(indent).append("    \"headers\": ")
        appendHeaderArray(requestHeaders)
        append(",\n")
        append(indent).append("    \"queryString\": [], \"cookies\": [], \"headersSize\": -1, \"bodySize\": -1\n")
        append(indent).append("  },\n")
        append(indent).append("  \"response\": {\n")
        append(indent).append("    \"status\": ").append(responseStatus).append(",\n")
        append(indent).append("    \"statusText\": \"\",\n")
        append(indent).append("    \"httpVersion\": \"HTTP/1.1\",\n")
        append(indent).append("    \"headers\": ")
        appendHeaderArray(responseHeaders)
        append(",\n")
        append(indent).append("    \"cookies\": [], \"content\": {\"size\": -1, \"mimeType\": \"application/octet-stream\"}, \"redirectURL\": \"\", \"headersSize\": -1, \"bodySize\": -1\n")
        append(indent).append("  },\n")
        append(indent).append("  \"cache\": {},\n")
        append(indent).append("  \"timings\": {\"send\": 0, \"wait\": 0, \"receive\": 0}\n")
        append(indent).append('}')
    }

    private fun requestUrl(message: CaptureHttpMessage): String {
        val target = message.target.orEmpty()
        if (target.startsWith("http://") || target.startsWith("https://")) return target
        val host = message.host.orEmpty()
        if (host.isEmpty()) return target
        val path = when {
            target.isEmpty() -> "/"
            target.startsWith('/') -> target
            else -> "/$target"
        }
        return "http://$host$path"
    }

    private fun StringBuilder.appendHeaderArray(headers: Map<String, String>) {
        append('[')
        headers.entries
            .sortedBy { it.key.lowercase() }
            .forEachIndexed { index, entry ->
                if (index > 0) append(',')
                append("{\"name\":")
                appendJsonString(entry.key)
                append(",\"value\":")
                appendJsonString(entry.value)
                append('}')
            }
        append(']')
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }

    private fun iso8601(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMillis))
}
