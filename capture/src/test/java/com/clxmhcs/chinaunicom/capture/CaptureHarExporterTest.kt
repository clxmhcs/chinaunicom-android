package com.clxmhcs.chinaunicom.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureHarExporterTest {
    @Test
    fun exportsStructuredMetadataWithoutBodies() {
        val messages = listOf(
            CaptureHttpMessage(
                messageID = "request-1",
                capturedAtEpochMillis = 1_700_000_000_000L,
                streamID = "10.0.0.2:12345>93.184.216.34:80",
                kind = CaptureHttpMessageKind.REQUEST,
                method = "GET",
                target = "/hello?q=1",
                host = "example.com",
                headers = linkedMapOf(
                    "Host" to "example.com",
                    "Cookie" to CaptureHttpHeaderParser.REDACTED,
                ),
            ),
            CaptureHttpMessage(
                messageID = "response-1",
                capturedAtEpochMillis = 1_700_000_000_100L,
                streamID = "93.184.216.34:80>10.0.0.2:12345",
                kind = CaptureHttpMessageKind.RESPONSE,
                statusCode = 200,
                headers = mapOf("Set-Cookie" to CaptureHttpHeaderParser.REDACTED),
            ),
        )

        val json = CaptureHarExporter.encode(messages).toString(Charsets.UTF_8)

        assertTrue(json.contains("\"version\": \"1.2\""))
        assertTrue(json.contains("http://example.com/hello?q=1"))
        assertTrue(json.contains("[REDACTED]"))
        assertTrue(json.contains("\"status\": 200"))
        assertTrue(json.contains("\"_captureMessageKind\": \"request\""))
        assertFalse(json.contains("postData"))
        assertFalse(json.contains("requestBody"))
        assertFalse(json.contains("responseBody"))
        assertFalse(json.contains("secret-cookie"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyHistory() {
        CaptureHarExporter.encode(emptyList())
    }

    @Test
    fun defaultFileNameUsesHarExtension() {
        assertTrue(CaptureHarExporter.defaultFileName(0L).endsWith(".har"))
    }
}
