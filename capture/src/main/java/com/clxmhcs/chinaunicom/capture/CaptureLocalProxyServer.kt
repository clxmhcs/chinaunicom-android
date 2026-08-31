package com.clxmhcs.chinaunicom.capture

import android.net.VpnService
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal data class CaptureProxyRequestHead(
    val method: String,
    val target: String,
    val version: String,
    val host: String,
    val port: Int,
    val originTarget: String,
    val headers: Map<String, String>,
) {
    val isConnect: Boolean get() = method.equals("CONNECT", ignoreCase = true)
}

internal object CaptureProxyRequestParser {
    fun parse(headerBytes: ByteArray): CaptureProxyRequestHead? {
        val text = String(headerBytes, StandardCharsets.ISO_8859_1)
        val lines = text.split("\r\n")
        val requestLine = lines.firstOrNull()?.trim().orEmpty()
        val parts = requestLine.split(' ', limit = 3)
        if (parts.size != 3) return null

        val method = parts[0].uppercase()
        val target = parts[1]
        val version = parts[2]
        if (!version.startsWith("HTTP/1.")) return null

        val headers = linkedMapOf<String, String>()
        for (line in lines.drop(1)) {
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            headers[line.substring(0, separator).trim()] = line.substring(separator + 1).trim()
            if (headers.size >= MAX_HEADER_COUNT) break
        }

        if (method == "CONNECT") {
            val authority = parseAuthority(target, DEFAULT_HTTPS_PORT) ?: return null
            return CaptureProxyRequestHead(
                method = method,
                target = target,
                version = version,
                host = authority.first,
                port = authority.second,
                originTarget = target,
                headers = headers,
            )
        }

        val absoluteUri = runCatching { URI(target) }.getOrNull()?.takeIf { it.isAbsolute }
        val hostHeader = headers.entries.firstOrNull { it.key.equals("Host", ignoreCase = true) }?.value
        val hostAndPort = when {
            absoluteUri?.host != null -> absoluteUri.host to normalizedPort(absoluteUri)
            hostHeader != null -> parseAuthority(hostHeader, DEFAULT_HTTP_PORT)
            else -> null
        } ?: return null

        val originTarget = when {
            target == "*" -> target
            absoluteUri != null -> buildString {
                append(absoluteUri.rawPath?.takeIf { it.isNotEmpty() } ?: "/")
                absoluteUri.rawQuery?.let { append('?').append(it) }
            }
            target.startsWith('/') -> target
            else -> "/$target"
        }

        return CaptureProxyRequestHead(
            method = method,
            target = target,
            version = version,
            host = hostAndPort.first,
            port = hostAndPort.second,
            originTarget = originTarget,
            headers = headers,
        )
    }

    fun rewriteForOrigin(headerBytes: ByteArray, request: CaptureProxyRequestHead): ByteArray {
        val text = String(headerBytes, StandardCharsets.ISO_8859_1)
        val lines = text.split("\r\n")
        val rewritten = buildString {
            append(request.method)
                .append(' ')
                .append(request.originTarget)
                .append(' ')
                .append(request.version)
                .append("\r\n")

            for (line in lines.drop(1)) {
                if (line.isEmpty()) break
                val name = line.substringBefore(':', missingDelimiterValue = line).trim()
                if (name.equals("Proxy-Connection", ignoreCase = true)) continue
                if (name.equals("Proxy-Authorization", ignoreCase = true)) continue
                append(line).append("\r\n")
            }
            append("\r\n")
        }
        return rewritten.toByteArray(StandardCharsets.ISO_8859_1)
    }

    private fun normalizedPort(uri: URI): Int {
        if (uri.port > 0) return uri.port
        return if (uri.scheme.equals("https", ignoreCase = true)) DEFAULT_HTTPS_PORT else DEFAULT_HTTP_PORT
    }

    private fun parseAuthority(value: String, defaultPort: Int): Pair<String, Int>? {
        val authority = value.trim()
        if (authority.isEmpty()) return null

        if (authority.startsWith('[')) {
            val closing = authority.indexOf(']')
            if (closing <= 1) return null
            val host = authority.substring(1, closing)
            val port = authority.substring(closing + 1)
                .removePrefix(":")
                .takeIf { it.isNotEmpty() }
                ?.toIntOrNull()
                ?: defaultPort
            if (port !in 1..65535) return null
            return host to port
        }

        val lastColon = authority.lastIndexOf(':')
        val hasSingleColon = lastColon > 0 && authority.indexOf(':') == lastColon
        val host = if (hasSingleColon) authority.substring(0, lastColon) else authority
        val port = if (hasSingleColon) {
            authority.substring(lastColon + 1).toIntOrNull() ?: return null
        } else {
            defaultPort
        }
        if (host.isBlank() || port !in 1..65535) return null
        return host to port
    }

    private const val MAX_HEADER_COUNT = 128
    private const val DEFAULT_HTTP_PORT = 80
    private const val DEFAULT_HTTPS_PORT = 443
}

internal object CaptureProxyFilter {
    fun shouldRecord(configuration: CaptureConfiguration, request: CaptureProxyRequestHead): Boolean {
        val normalized = configuration.normalized()
        val host = normalizeHost(request.host)
        val configuredHosts = buildList {
            normalized.targetHost?.let(::add)
            addAll(normalized.additionalHosts)
        }.map(::normalizeHost).filter { it.isNotEmpty() }

        val hostMatches = normalized.captureAllHosts || configuredHosts.any { candidate ->
            host == candidate || host.endsWith(".$candidate")
        }
        if (!hostMatches) return false

        val path = normalized.targetPath ?: return true
        if (request.isConnect) return false
        return request.originTarget.startsWith(path)
    }

    private fun normalizeHost(value: String): String =
        value.trim().lowercase().trim('.')
}

/**
 * Source-parity HTTP proxy used by M14-F.
 *
 * The server binds only to loopback. Upstream sockets are explicitly protected from the VPN before
 * connecting. CONNECT traffic is relayed byte-for-byte and never inspected after the HTTP CONNECT
 * handshake. Plain HTTP is forwarded with proxy-only headers removed; only the already-redacted
 * structured header model is published to CaptureHttpRuntime.
 */
internal class CaptureLocalProxyServer(
    private val vpnService: VpnService,
    private val configurationProvider: () -> CaptureConfiguration,
    private val port: Int = DEFAULT_PORT,
) : Closeable {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val workers = ThreadPoolExecutor(
        MIN_WORKERS,
        MAX_WORKERS,
        WORKER_KEEP_ALIVE_SECONDS,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(MAX_QUEUED_CONNECTIONS),
        { task -> Thread(task, "ChinaUnicom-CaptureProxyWorker").apply { isDaemon = true } },
    )

    fun start() {
        if (!running.compareAndSet(false, true)) return
        try {
            val server = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName(BIND_HOST), port), LISTEN_BACKLOG)
            }
            serverSocket = server
            acceptThread = Thread(::acceptLoop, "ChinaUnicom-CaptureProxyAccept").apply {
                isDaemon = true
                start()
            }
        } catch (error: Throwable) {
            running.set(false)
            runCatching { serverSocket?.close() }
            serverSocket = null
            workers.shutdownNow()
            throw error
        }
    }

    override fun close() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
        workers.shutdownNow()
    }

    private fun acceptLoop() {
        try {
            while (running.get()) {
                val client = serverSocket?.accept() ?: break
                configureClient(client)
                try {
                    workers.execute { handleClient(client) }
                } catch (_: RejectedExecutionException) {
                    runCatching { client.close() }
                }
            }
        } catch (error: SocketException) {
            if (running.get()) throw error
        } catch (error: Throwable) {
            if (running.get()) throw error
        }
    }

    private fun handleClient(client: Socket) {
        client.use { clientSocket ->
            val clientInput = BufferedInputStream(clientSocket.getInputStream())
            val clientOutput = BufferedOutputStream(clientSocket.getOutputStream())
            val initial = readHeader(clientInput) ?: return
            val request = CaptureProxyRequestParser.parse(initial.headerBytes) ?: return
            val recordFlow = CaptureProxyFilter.shouldRecord(configurationProvider(), request)
            val streamID = "proxy:${UUID.randomUUID()}"

            createProtectedUpstream(request.host, request.port).use { upstream ->
                if (request.isConnect) {
                    handleConnect(
                        clientInput = clientInput,
                        clientOutput = clientOutput,
                        upstream = upstream,
                        initial = initial,
                        streamID = streamID,
                        recordFlow = recordFlow,
                    )
                } else {
                    handlePlainHttp(
                        clientInput = clientInput,
                        clientOutput = clientOutput,
                        upstream = upstream,
                        initial = initial,
                        request = request,
                        streamID = streamID,
                        recordFlow = recordFlow,
                    )
                }
            }
        }
    }

    private fun handleConnect(
        clientInput: BufferedInputStream,
        clientOutput: BufferedOutputStream,
        upstream: Socket,
        initial: HeaderReadResult,
        streamID: String,
        recordFlow: Boolean,
    ) {
        if (recordFlow) publishHeader(streamID, initial.headerBytes)

        clientOutput.write(CONNECT_ESTABLISHED_BYTES)
        clientOutput.flush()
        if (recordFlow) publishHeader(streamID, CONNECT_ESTABLISHED_BYTES)

        val upstreamInput = BufferedInputStream(upstream.getInputStream())
        val upstreamOutput = BufferedOutputStream(upstream.getOutputStream())
        if (initial.remainder.isNotEmpty()) {
            upstreamOutput.write(initial.remainder)
            upstreamOutput.flush()
        }

        val upstreamToClient = Thread(
            {
                runCatching { relay(upstreamInput, clientOutput) }
                runCatching { upstream.shutdownInput() }
            },
            "ChinaUnicom-CaptureProxyConnectDownstream",
        ).apply {
            isDaemon = true
            start()
        }

        runCatching { relay(clientInput, upstreamOutput) }
        runCatching { upstream.shutdownOutput() }
        runCatching { upstreamToClient.join(RELAY_JOIN_MILLIS) }
    }

    private fun handlePlainHttp(
        clientInput: BufferedInputStream,
        clientOutput: BufferedOutputStream,
        upstream: Socket,
        initial: HeaderReadResult,
        request: CaptureProxyRequestHead,
        streamID: String,
        recordFlow: Boolean,
    ) {
        val upstreamInput = BufferedInputStream(upstream.getInputStream())
        val upstreamOutput = BufferedOutputStream(upstream.getOutputStream())
        val rewrittenHeader = CaptureProxyRequestParser.rewriteForOrigin(initial.headerBytes, request)

        upstreamOutput.write(rewrittenHeader)
        if (initial.remainder.isNotEmpty()) upstreamOutput.write(initial.remainder)
        upstreamOutput.flush()
        if (recordFlow) publishHeader(streamID, initial.headerBytes)

        val clientToUpstream = Thread(
            {
                runCatching { relay(clientInput, upstreamOutput) }
                runCatching { upstream.shutdownOutput() }
            },
            "ChinaUnicom-CaptureProxyHttpUpstream",
        ).apply {
            isDaemon = true
            start()
        }

        val response = readHeader(upstreamInput)
        if (response != null) {
            if (recordFlow) publishHeader(streamID, response.headerBytes)
            clientOutput.write(response.headerBytes)
            if (response.remainder.isNotEmpty()) clientOutput.write(response.remainder)
            clientOutput.flush()
            relay(upstreamInput, clientOutput)
        }

        runCatching { clientToUpstream.join(RELAY_JOIN_MILLIS) }
    }

    private fun createProtectedUpstream(host: String, port: Int): Socket {
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = SOCKET_READ_TIMEOUT_MILLIS
            if (!vpnService.protect(socket)) {
                throw IOException("无法保护抓包代理上游 Socket")
            }
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS)
            return socket
        } catch (error: Throwable) {
            runCatching { socket.close() }
            throw error
        }
    }

    private fun configureClient(socket: Socket) {
        socket.tcpNoDelay = true
        socket.soTimeout = SOCKET_READ_TIMEOUT_MILLIS
    }

    private fun publishHeader(streamID: String, headerBytes: ByteArray) {
        CaptureHttpHeaderParser.parse(streamID, headerBytes)?.let(CaptureHttpRuntime::publish)
    }

    private fun relay(input: BufferedInputStream, output: BufferedOutputStream) {
        val buffer = ByteArray(RELAY_BUFFER_BYTES)
        while (running.get()) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
            output.flush()
        }
    }

    private fun readHeader(input: BufferedInputStream): HeaderReadResult? {
        val collected = ByteArrayOutputStream(4096)
        val chunk = ByteArray(4096)
        while (collected.size() <= MAX_HEADER_BYTES) {
            val count = input.read(chunk)
            if (count < 0) return if (collected.size() == 0) null else throw IOException("HTTP 头提前结束")
            if (count == 0) continue
            collected.write(chunk, 0, count)
            val bytes = collected.toByteArray()
            val end = findHeaderEnd(bytes)
            if (end >= 0) {
                return HeaderReadResult(
                    headerBytes = bytes.copyOfRange(0, end),
                    remainder = bytes.copyOfRange(end, bytes.size),
                )
            }
        }
        throw IOException("HTTP 头超过 ${MAX_HEADER_BYTES / 1024} KiB 限制")
    }

    private fun findHeaderEnd(bytes: ByteArray): Int {
        if (bytes.size < HEADER_SEPARATOR.size) return -1
        for (index in 0..bytes.size - HEADER_SEPARATOR.size) {
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

    private data class HeaderReadResult(
        val headerBytes: ByteArray,
        val remainder: ByteArray,
    )

    companion object {
        const val BIND_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 9090

        private const val LISTEN_BACKLOG = 32
        private const val MIN_WORKERS = 2
        private const val MAX_WORKERS = 8
        private const val MAX_QUEUED_CONNECTIONS = 64
        private const val WORKER_KEEP_ALIVE_SECONDS = 30L
        private const val CONNECT_TIMEOUT_MILLIS = 15_000
        private const val SOCKET_READ_TIMEOUT_MILLIS = 60_000
        private const val RELAY_JOIN_MILLIS = 1_000L
        private const val RELAY_BUFFER_BYTES = 32 * 1024
        private const val MAX_HEADER_BYTES = 32 * 1024
        private val HEADER_SEPARATOR = byteArrayOf(13, 10, 13, 10)
        private val CONNECT_ESTABLISHED_BYTES =
            "HTTP/1.1 200 Connection Established\r\nProxy-Agent: ChinaUnicom-Capture\r\n\r\n"
                .toByteArray(StandardCharsets.ISO_8859_1)
    }
}
