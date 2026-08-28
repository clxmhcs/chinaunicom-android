package com.clxmhcs.chinaunicom.capture

import java.net.InetAddress

object CaptureIpPacketDecoder {
    fun decode(packet: ByteArray, length: Int = packet.size): CapturePacketMetadata? {
        if (length <= 0 || length > packet.size) return null
        return when (unsigned(packet[0]) ushr 4) {
            4 -> decodeIpv4(packet, length)
            6 -> decodeIpv6(packet, length)
            else -> null
        }
    }

    private fun decodeIpv4(packet: ByteArray, length: Int): CapturePacketMetadata? {
        if (length < IPV4_MIN_HEADER) return null
        val headerLength = (unsigned(packet[0]) and 0x0F) * 4
        if (headerLength < IPV4_MIN_HEADER || headerLength > length) return null

        val declaredLength = u16(packet, 2)
        if (declaredLength < headerLength) return null
        val packetLength = minOf(length, declaredLength)
        val protocolNumber = unsigned(packet[9])
        val fragmentField = u16(packet, 6)
        val fragmentOffset = fragmentField and IPV4_FRAGMENT_OFFSET_MASK
        val moreFragments = (fragmentField and IPV4_MORE_FRAGMENTS_FLAG) != 0
        val fragmented = fragmentOffset != 0 || moreFragments
        val allowPorts = fragmentOffset == 0
        val ports = if (allowPorts) readPorts(packet, headerLength, packetLength, protocolNumber) else null

        return CapturePacketMetadata(
            ipVersion = CaptureIpVersion.IPV4,
            transportProtocol = protocol(protocolNumber, ipv6 = false),
            source = CaptureEndpoint(ipv4(packet, 12), ports?.first),
            destination = CaptureEndpoint(ipv4(packet, 16), ports?.second),
            packetLength = packetLength,
            fragmented = fragmented,
        )
    }

    private fun decodeIpv6(packet: ByteArray, length: Int): CapturePacketMetadata? {
        if (length < IPV6_HEADER_LENGTH) return null
        val payloadLength = u16(packet, 4)
        val declaredLength = IPV6_HEADER_LENGTH + payloadLength
        if (declaredLength < IPV6_HEADER_LENGTH) return null
        val packetLength = minOf(length, declaredLength)
        val nextHeader = unsigned(packet[6])
        val ports = readPorts(packet, IPV6_HEADER_LENGTH, packetLength, nextHeader)

        return CapturePacketMetadata(
            ipVersion = CaptureIpVersion.IPV6,
            transportProtocol = protocol(nextHeader, ipv6 = true),
            source = CaptureEndpoint(ipv6(packet, 8), ports?.first),
            destination = CaptureEndpoint(ipv6(packet, 24), ports?.second),
            packetLength = packetLength,
            fragmented = nextHeader == IPV6_FRAGMENT_HEADER,
        )
    }

    private fun readPorts(
        packet: ByteArray,
        transportOffset: Int,
        packetLength: Int,
        protocolNumber: Int,
    ): Pair<Int, Int>? {
        if (protocolNumber != PROTOCOL_TCP && protocolNumber != PROTOCOL_UDP) return null
        if (transportOffset + 4 > packetLength) return null
        return u16(packet, transportOffset) to u16(packet, transportOffset + 2)
    }

    private fun protocol(value: Int, ipv6: Boolean): CaptureTransportProtocol = when (value) {
        PROTOCOL_TCP -> CaptureTransportProtocol.TCP
        PROTOCOL_UDP -> CaptureTransportProtocol.UDP
        PROTOCOL_ICMP -> CaptureTransportProtocol.ICMP
        PROTOCOL_ICMPV6 -> CaptureTransportProtocol.ICMPV6
        else -> CaptureTransportProtocol.OTHER
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

    private fun unsigned(value: Byte): Int = value.toInt() and 0xFF

    private const val IPV4_MIN_HEADER = 20
    private const val IPV6_HEADER_LENGTH = 40
    private const val IPV4_FRAGMENT_OFFSET_MASK = 0x1FFF
    private const val IPV4_MORE_FRAGMENTS_FLAG = 0x2000
    private const val IPV6_FRAGMENT_HEADER = 44
    private const val PROTOCOL_ICMP = 1
    private const val PROTOCOL_TCP = 6
    private const val PROTOCOL_UDP = 17
    private const val PROTOCOL_ICMPV6 = 58
}
