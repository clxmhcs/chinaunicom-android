package com.clxmhcs.chinaunicom.capture

import java.util.UUID

enum class CaptureIpVersion {
    IPV4,
    IPV6,
}

enum class CaptureTransportProtocol {
    TCP,
    UDP,
    ICMP,
    ICMPV6,
    OTHER,
}

data class CaptureEndpoint(
    val address: String,
    val port: Int? = null,
)

data class CapturePacketMetadata(
    val ipVersion: CaptureIpVersion,
    val transportProtocol: CaptureTransportProtocol,
    val source: CaptureEndpoint,
    val destination: CaptureEndpoint,
    val packetLength: Int,
    val fragmented: Boolean = false,
)

data class CapturePacketSessionSnapshot(
    val sessionID: String,
    val startedAtEpochMillis: Long,
    val lastPacketAtEpochMillis: Long? = null,
    val packetCount: Long = 0,
    val byteCount: Long = 0,
    val tcpPacketCount: Long = 0,
    val udpPacketCount: Long = 0,
    val otherPacketCount: Long = 0,
)

/**
 * Process-local packet metadata authority for M14-B.
 *
 * Raw packet bytes are deliberately never retained here. Future UI can consume bounded metadata
 * and aggregate counters without gaining access to packet payloads or carrier credentials.
 */
object CapturePacketRuntime {
    private const val RECENT_PACKET_LIMIT = 128
    private val lock = Any()
    private val recentPackets = ArrayDeque<CapturePacketMetadata>(RECENT_PACKET_LIMIT)
    private var snapshot = newSnapshot()

    fun beginSession(nowEpochMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            recentPackets.clear()
            snapshot = CapturePacketSessionSnapshot(
                sessionID = UUID.randomUUID().toString(),
                startedAtEpochMillis = nowEpochMillis,
            )
        }
    }

    fun accept(packet: CapturePacketMetadata, nowEpochMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            if (recentPackets.size >= RECENT_PACKET_LIMIT) recentPackets.removeFirst()
            recentPackets.addLast(packet)
            snapshot = snapshot.copy(
                lastPacketAtEpochMillis = nowEpochMillis,
                packetCount = snapshot.packetCount + 1,
                byteCount = snapshot.byteCount + packet.packetLength,
                tcpPacketCount = snapshot.tcpPacketCount + if (packet.transportProtocol == CaptureTransportProtocol.TCP) 1 else 0,
                udpPacketCount = snapshot.udpPacketCount + if (packet.transportProtocol == CaptureTransportProtocol.UDP) 1 else 0,
                otherPacketCount = snapshot.otherPacketCount + if (
                    packet.transportProtocol != CaptureTransportProtocol.TCP &&
                    packet.transportProtocol != CaptureTransportProtocol.UDP
                ) 1 else 0,
            )
        }
    }

    fun snapshot(): CapturePacketSessionSnapshot = synchronized(lock) { snapshot }

    fun recentPackets(): List<CapturePacketMetadata> = synchronized(lock) { recentPackets.toList() }

    private fun newSnapshot(): CapturePacketSessionSnapshot = CapturePacketSessionSnapshot(
        sessionID = UUID.randomUUID().toString(),
        startedAtEpochMillis = System.currentTimeMillis(),
    )
}
