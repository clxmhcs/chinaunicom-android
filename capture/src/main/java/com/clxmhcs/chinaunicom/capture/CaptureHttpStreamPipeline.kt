package com.clxmhcs.chinaunicom.capture

import java.nio.charset.StandardCharsets
import java.util.TreeMap
import java.util.UUID

enum class CaptureHttpMessageKind {
    REQUEST,
    RESPONSE,
}

data class CaptureHttpMessage(
    val messageID: String,
    val capturedAtEpochMillis: Long,
    val streamID: String,
    val kind: CaptureHttpMessageKind,
    val method: String? = null,
    val target: String? = null,
    val statusCode: Int? = null,
    val host: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

data class CaptureHttpSessionSnapshot(
    val messageCount: Long = 0,
    val requestCount: Long = 0,
    val responseCount: Long = 0,
    val droppedStreamCount: Long = 0,
)

/**
 * M14-C passive TCP/HTTP reconstruction.
 *
 * This intentionally reconstructs header bytes only. Message bodies are not published or retained.
 * Each directional TCP stream is bounded and process-local, and completed HTTP headers are removed
 * immediately after parsing.
 */
internal class CaptureHttpStreamPipeline(
    private val maxStreams: Int = MAX_STREAMS,
    private val maxStreamBytes: Int = MAX_STREAM_BYTES,
    private val maxPendingSegments: Int = MAX_PENDING_SEGMENTS,
) {
    private data class StreamState(
        var nextSequence: Long? = null,
        var buffer: ByteArray = ByteArray(0),
        val pending: TreeMap<Long, ByteArray> = TreeMap(),
    )

    private val streams = LinkedHashMap<String, StreamState>(16, 0.75f, true)
    var droppedStreamCount: Long = 0
        private set

    fun clear() {
        streams.clear()
        droppedStreamCount = 0
    }

    fun accept(segment: CaptureTcpSegment): CaptureHttpMessage? {
        if (segment.payload.isEmpty()) return null
        val state = streams[segment.streamID] ?: createState(segment.streamID)
        appendOrdered(state, segment.sequenceNumber, segment.payload)
        if (state.buffer.size > maxStreamBytes) {
            streams.remove(segment.streamID)
            droppedStreamCount += 1
            return null
        }

        val headerEnd = findHeaderEnd(state.buffer)
        if (headerEnd < 0) return null
        val headerBytes = state.buffer.copyOfRange(0, headerEnd)
        streams.remove(segment.streamID)
        return CaptureHttpHeaderParser.parse(segment.streamID, headerBytes)
    }

    private fun createState(streamID: String): StreamState {
        if (streams.size >= maxStreams) {
            val eldest = streams.entries.firstOrNull()?.key
            if (eldest != null) {
                streams.remove(eldest)
                droppedStreamCount += 1
            }
        }
        return StreamState().also { streams[streamID] = it }
    }

    private fun appendOrdered(state: StreamState, sequence: Long, payload: ByteArray) {
        val expected = state.nextSequence
        if (expected == null) {
            state.buffer = payload.copyOf()
            state.nextSequence = advanceSequence(sequence, payload.size)
            flushPending(state)
            return
        }

        when {
            sequence == expected -> {
                state.buffer = state.buffer + payload
                state.nextSequence = advanceSequence(expected, payload.size)
                flushPending(state)
            }
            sequence < expected -> {
                val overlap = (expected - sequence).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                if (overlap < payload.size) {
                    val suffix = payload.copyOfRange(overlap, payload.size)
                    state.buffer = state.buffer + suffix
                    state.nextSequence = advanceSequence(expected, suffix.size)
                    flushPending(state)
                }
            }
            else -> {
                if (state.pending.size >= maxPendingSegments) {
                    state.pending.pollLastEntry()
                    droppedStreamCount += 1
                }
                state.pending.putIfAbsent(sequence, payload.copyOf())
            }
        }
    }

    private fun flushPending(state: StreamState) {
        while (true) {
            val expected = state.nextSequence ?: return
            val entry = state.pending.firstEntry() ?: return
            if (entry.key > expected) return
            state.pending.pollFirstEntry()
            val overlap = (expected - entry.key).coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (overlap >= entry.value.size) continue
            val suffix = entry.value.copyOfRange(overlap, entry.value.size)
            state.buffer = state.buffer + suffix
            state.nextSequence = advanceSequence(expected, suffix.size)
            if (state.buffer.size > maxStreamBytes) return
        }
    }

    private fun findHeaderEnd(bytes: ByteArray): Int {
        if (bytes.size < HEADER_SEPARATOR.size) return -1
        val maxIndex = minOf(bytes.size - HEADER_SEPARATOR.size, MAX_HEADER_BYTES)
        for (index in 0..maxIndex) {
            var matches = true
            for (offset in HEADER_SEPARATOR.indices) {
                if (bytes[index + offset] != HEADER_SEPARATOR[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return index + HEADER_SEPARATOR.size
        }
        return -1
    }

    private fun advanceSequence(sequence: Long, amount: Int): Long =
        (sequence + amount.toLong()) and 0xFFFF_FFFFL

    companion object {
        internal const val MAX_STREAMS = 64
        internal const val MAX_STREAM_BYTES = 64 * 1024
        internal const val MAX_PENDING_SEGMENTS = 16
        internal const val MAX_HEADER_BYTES = 32 * 1024
        private val HEADER_SEPARATOR = byteArrayOf(13, 10, 13, 10)
    }
}

internal object CaptureHttpHeaderParser {
    private val requestMethods = setOf(
        "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "CONNECT",
    )
    private val sensitiveHeaders = setOf(
        "authorization",
        "proxy-authorization",
        "cookie",
        "set-cookie",
        "x-auth-token",
        "x-api-key",
        "proxy-authenticate",
    )

    fun parse(streamID: String, headerBytes: ByteArray): CaptureHttpMessage? {
        if (headerBytes.size > CaptureHttpStreamPipeline.MAX_HEADER_BYTES + 4) return null
        val text = headerBytes.toString(StandardCharsets.ISO_8859_1)
        val lines = text.split("\r\n")
        val firstLine = lines.firstOrNull()?.trim().orEmpty()
        if (firstLine.isEmpty()) return null
        val headers = parseHeaders(lines.drop(1))
        val host = headers.entries.firstOrNull { it.key.equals("host", ignoreCase = true) }?.value
        val now = System.currentTimeMillis()

        if (firstLine.startsWith("HTTP/1.")) {
            val status = firstLine.split(' ').getOrNull(1)?.toIntOrNull() ?: return null
            return CaptureHttpMessage(
                messageID = UUID.randomUUID().toString(),
                capturedAtEpochMillis = now,
                streamID = streamID,
                kind = CaptureHttpMessageKind.RESPONSE,
                statusCode = status,
                headers = headers,
            )
        }

        val parts = firstLine.split(' ')
        if (parts.size < 3) return null
        val method = parts[0].uppercase()
        if (method !in requestMethods || !parts[2].startsWith("HTTP/1.")) return null
        return CaptureHttpMessage(
            messageID = UUID.randomUUID().toString(),
            capturedAtEpochMillis = now,
            streamID = streamID,
            kind = CaptureHttpMessageKind.REQUEST,
            method = method,
            target = parts[1].take(MAX_TARGET_LENGTH),
            host = host?.take(MAX_HEADER_VALUE_LENGTH),
            headers = headers,
        )
    }

    private fun parseHeaders(lines: List<String>): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        for (line in lines) {
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val name = line.substring(0, separator).trim().take(MAX_HEADER_NAME_LENGTH)
            if (name.isEmpty()) continue
            val normalizedName = name.lowercase()
            val rawValue = line.substring(separator + 1).trim()
            val value = if (normalizedName in sensitiveHeaders) {
                REDACTED
            } else {
                rawValue.take(MAX_HEADER_VALUE_LENGTH)
            }
            headers[name] = value
            if (headers.size >= MAX_HEADER_COUNT) break
        }
        return headers
    }

    internal const val REDACTED = "[REDACTED]"
    private const val MAX_HEADER_COUNT = 96
    private const val MAX_HEADER_NAME_LENGTH = 96
    private const val MAX_HEADER_VALUE_LENGTH = 1024
    private const val MAX_TARGET_LENGTH = 2048
}

object CaptureHttpRuntime {
    private const val RECENT_MESSAGE_LIMIT = 128
    private val lock = Any()
    private var pipeline = CaptureHttpStreamPipeline()
    private val recentMessages = ArrayDeque<CaptureHttpMessage>(RECENT_MESSAGE_LIMIT)
    private var snapshot = CaptureHttpSessionSnapshot()

    fun beginSession() {
        synchronized(lock) {
            pipeline = CaptureHttpStreamPipeline()
            recentMessages.clear()
            snapshot = CaptureHttpSessionSnapshot()
        }
    }

    fun accept(segment: CaptureTcpSegment) {
        synchronized(lock) {
            val beforeDropped = pipeline.droppedStreamCount
            val message = pipeline.accept(segment)
            val droppedDelta = pipeline.droppedStreamCount - beforeDropped
            if (message != null) {
                if (recentMessages.size >= RECENT_MESSAGE_LIMIT) recentMessages.removeFirst()
                recentMessages.addLast(message)
                snapshot = snapshot.copy(
                    messageCount = snapshot.messageCount + 1,
                    requestCount = snapshot.requestCount + if (message.kind == CaptureHttpMessageKind.REQUEST) 1 else 0,
                    responseCount = snapshot.responseCount + if (message.kind == CaptureHttpMessageKind.RESPONSE) 1 else 0,
                )
            }
            if (droppedDelta > 0) {
                snapshot = snapshot.copy(droppedStreamCount = snapshot.droppedStreamCount + droppedDelta)
            }
        }
    }

    fun snapshot(): CaptureHttpSessionSnapshot = synchronized(lock) { snapshot }

    fun recentMessages(): List<CaptureHttpMessage> = synchronized(lock) { recentMessages.toList() }
}
