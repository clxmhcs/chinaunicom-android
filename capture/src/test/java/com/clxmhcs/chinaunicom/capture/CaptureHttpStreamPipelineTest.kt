package com.clxmhcs.chinaunicom.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureHttpStreamPipelineTest {
    @Test
    fun reconstructsSplitRequestAndRedactsSensitiveHeaders() {
        val pipeline = CaptureHttpStreamPipeline()
        val first = "GET /quota?phone=masked HTTP/1.1\r\nHost: example.test\r\nAuthorization: Bearer secret\r\nCookie: sid=secret\r\nUser-Agent: test"
        val second = "\r\n\r\nBODY-MUST-NOT-BE-PUBLISHED"

        assertNull(pipeline.accept(segment(1000, first)))
        val message = requireNotNull(pipeline.accept(segment(1000 + first.toByteArray().size, second)))

        assertEquals(CaptureHttpMessageKind.REQUEST, message.kind)
        assertEquals("GET", message.method)
        assertEquals("/quota?phone=masked", message.target)
        assertEquals("example.test", message.host)
        assertEquals(CaptureHttpHeaderParser.REDACTED, message.headers["Authorization"])
        assertEquals(CaptureHttpHeaderParser.REDACTED, message.headers["Cookie"])
        assertEquals("test", message.headers["User-Agent"])
        assertTrue(message.toString().contains("BODY-MUST-NOT-BE-PUBLISHED").not())
    }

    @Test
    fun reconstructsResponseHeader() {
        val pipeline = CaptureHttpStreamPipeline()
        val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nSet-Cookie: sid=secret\r\n\r\n{\"x\":1}"

        val message = requireNotNull(pipeline.accept(segment(2000, response)))

        assertEquals(CaptureHttpMessageKind.RESPONSE, message.kind)
        assertEquals(200, message.statusCode)
        assertEquals("application/json", message.headers["Content-Type"])
        assertEquals(CaptureHttpHeaderParser.REDACTED, message.headers["Set-Cookie"])
    }

    @Test
    fun flushesLaterPendingSegmentWhenGapArrives() {
        val pipeline = CaptureHttpStreamPipeline()
        val a = "GET / HTTP/1.1\r\n"
        val b = "Host: ex"
        val c = "ample.test\r\n\r\n"
        val aBytes = a.toByteArray()
        val bBytes = b.toByteArray()

        assertNull(pipeline.accept(segment(3000, a)))
        assertNull(pipeline.accept(segment(3000 + aBytes.size + bBytes.size, c)))
        val message = requireNotNull(pipeline.accept(segment(3000 + aBytes.size, b)))

        assertEquals("example.test", message.host)
    }

    @Test
    fun streamCountIsBounded() {
        val pipeline = CaptureHttpStreamPipeline(maxStreams = 2)

        assertNull(pipeline.accept(segment(1, "GET /one", streamID = "one")))
        assertNull(pipeline.accept(segment(1, "GET /two", streamID = "two")))
        assertNull(pipeline.accept(segment(1, "GET /three", streamID = "three")))

        assertEquals(1, pipeline.droppedStreamCount)
    }

    @Test
    fun runtimeKeepsOnlyBoundedStructuredMessages() {
        CaptureHttpRuntime.beginSession()
        repeat(140) { index ->
            CaptureHttpRuntime.accept(
                segment(
                    sequence = 1000,
                    text = "GET /$index HTTP/1.1\r\nHost: example.test\r\n\r\n",
                    streamID = "stream-$index",
                ),
            )
        }

        val snapshot = CaptureHttpRuntime.snapshot()
        val messages = CaptureHttpRuntime.recentMessages()
        assertEquals(140, snapshot.messageCount)
        assertEquals(140, snapshot.requestCount)
        assertEquals(128, messages.size)
        assertEquals("/12", messages.first().target)
        assertEquals("/139", messages.last().target)
    }

    private fun segment(sequence: Int, text: String, streamID: String = "client:1234>server:80") =
        CaptureTcpSegment(
            streamID = streamID,
            source = CaptureEndpoint("192.0.2.2", 1234),
            destination = CaptureEndpoint("192.0.2.3", 80),
            sequenceNumber = sequence.toLong(),
            flags = 0x18,
            payload = text.toByteArray(),
        )
}
