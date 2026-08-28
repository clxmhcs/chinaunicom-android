package com.clxmhcs.chinaunicom.capture

import java.net.InetAddress

/**
 * Ephemeral TCP segment extracted from one TUN packet.
 *
 * [payload] exists only for the synchronous M14-C stream pipeline. It is never written to disk,
 * SharedPreferences, SQLite, Room, logs, or the packet metadata runtime.
 */
data class CaptureTcpSegment(
    val streamID: String,
    val source: CaptureEndpoint,
    val destination: CaptureEndpoint,
    val sequenceNumber: Long,
    val flags: Int,
    val payload: ByteArray,
)

object CaptureTcpSegmentDecoder {
    fun decode(packet: ByteArray, length: Int = packet.size): CaptureTcpSegment? {
        if (length <= 0 || length > packet.size) return null
        return when (unsigned(packet[0]) ushr 4) {
            4 -> decodeIpv4(packet, length)
            6 -> decodeIpv6(packet, length)
            else -> null
        }
    }

    private fun decodeIpv4(packet: ByteArray, length: Int): CaptureTcpSegment? {
        if (length < IPV4_MIN_HEADER) return null
        val ipHeaderLength = (unsigned(packet[0]) and 0x0F) * 4
        if (ipHeaderLength < IPV4_MIN_HEADER || ipHeaderLength > length) return null
        if (unsigned(packet[9]) != PROTOCOL_TCP) return null

        val fragmentField = u16(packet, 6)
        val fragmentOffset = fragmentField and IPV4_FRAGMENT_OFFSET_MASK
        val moreFragments = (fragmentField and IPV4_MORE_FRAGMENTS_FLAG) != 0
        if (fragmentOffset != 0 || moreFragments) return null

        val declaredLength = u16(packet, 2)
        if (declaredLength < ipHeaderLength) return null
        val packetLength = minOf(length, declaredLength)
        return decodeTcp(
            packet = packet,
            tcpOffset = ipHeaderLength,
            packetLength = packetLength,
            sourceAddress = ipv4(packet, 12),
            destinationAddress = ipv4(packet, 16),
        )
    }

    private fun decodeIpv6(packet: ByteArray, length: Int): CaptureTcpSegment? {
        if (length < IPV6_HEADER_LENGTH) return null
        if (unsigned(packet[6]) != PROTOCOL_TCP) return null

        val payloadLength = u16(packet, 4)
        val declaredLength = IPV6_HEADER_LENGTH + payloadLength
        if (declaredLength < IPV6_HEADER_LENGTH) return null
        val packetLength = minOf(length, declaredLength)
        return decodeTcp(
            packet = packet,
            tcpOffset = IPV6_HEADER_LENGTH,
            packetLength = packetLength,
            sourceAddress = ipv6(packet, 8),
            destinationAddress = ipv6(packet, 24),
        )
    }

    private fun decodeTcp(
        packet: ByteArray,
        tcpOffset: Int,
        packetLength: Int,
        sourceAddress: String,
        destinationAddress: String,
    ): CaptureTcpSegment? {
        if (tcpOffset + TCP_MIN_HEADER > packetLength) return null
        val tcpHeaderLength = ((unsigned(packet[tcpOffset + 12]) ushr 4) and 0x0F) * 4
        if (tcpHeaderLength < TCP_MIN_HEADER) return null
        val payloadOffset = tcpOffset + tcpHeaderLength
        if (payloadOffset > packetLength) return null

        val source = CaptureEndpoint(sourceAddress, u16(packet, tcpOffset))
        val destination = CaptureEndpoint(destinationAddress, u16(packet, tcpOffset + 2))
        val sequenceNumber = u32(packet, tcpOffset + 4)
        val flags = unsigned(packet[tcpOffset + 13])
        val payload = if (payloadOffset == packetLength) {
            EMPTY_PAYLOAD
        } else {
            packet.copyOfRange(payloadOffset, packetLength)
        }

        return CaptureTcpSegment(
            streamID = "${endpointID(source)}>${endpointID(destination)}",
            source = source,
            destination = destination,
            sequenceNumber = sequenceNumber,
            flags = flags,
            payload = payload,
        )
    }

    private fun endpointID(endpoint: CaptureEndpoint): String = when {
        endpoint.address.contains(':') -> "[${endpoint.address}]:${endpoint.port ?: 0}"
        else -> "${endpoint.address}:${endpoint.port ?: 0}"
    }

    private fun ipv4(packet: ByteArray, offset: Int): String = buildString {
        repeat(4) { index ->
            if (index > 0) append('.')
            append(unsigned(packet[offset + index]))
        }
    }

    private fun ipv6(packet: ByteArray, offset: Int): String =
        InetAddress.getByAddress(packet.copyOfRange(offset, offset + 16)).hostAddress

    private fun u16(packet: ByteArray, offset: Int): Int =
        (unsigned(packet[offset]) shl 8) or unsigned(packet[offset + 1])

    private fun u32(packet: ByteArray, offset: Int): Long =
        ((unsigned(packet[offset]).toLong() shl 24) or
            (unsigned(packet[offset + 1]).toLong() shl 16) or
            (unsigned(packet[offset + 2]).toLong() shl 8) or
            unsigned(packet[offset + 3]).toLong()) and 0xFFFF_FFFFL

    private fun unsigned(value: Byte): Int = value.toInt() and 0xFF

    private const val IPV4_MIN_HEADER = 20
    private const val IPV6_HEADER_LENGTH = 40
    private const val TCP_MIN_HEADER = 20
    private const val IPV4_FRAGMENT_OFFSET_MASK = 0x1FFF
    private const val IPV4_MORE_FRAGMENTS_FLAG = 0x2000
    private const val PROTOCOL_TCP = 6
    private val EMPTY_PAYLOAD = ByteArray(0)
}
