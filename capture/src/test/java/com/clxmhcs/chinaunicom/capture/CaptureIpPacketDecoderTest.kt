package com.clxmhcs.chinaunicom.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureIpPacketDecoderTest {
    @Test
    fun decodesIpv4UdpEndpointsWithoutRetainingPayload() {
        val packet = byteArrayOf(
            0x45, 0x00, 0x00, 0x1c, 0x12, 0x34, 0x00, 0x00,
            0x40, 0x11, 0x00, 0x00, 0xc0.toByte(), 0x00, 0x02, 0x0a,
            0xc6.toByte(), 0x33, 0x64, 0x14, 0x30, 0x39, 0x00, 0x35,
            0x00, 0x08, 0x00, 0x00,
        )

        val decoded = requireNotNull(CaptureIpPacketDecoder.decode(packet))

        assertEquals(CaptureIpVersion.IPV4, decoded.ipVersion)
        assertEquals(CaptureTransportProtocol.UDP, decoded.transportProtocol)
        assertEquals("192.0.2.10", decoded.source.address)
        assertEquals(12345, decoded.source.port)
        assertEquals("198.51.100.20", decoded.destination.address)
        assertEquals(53, decoded.destination.port)
        assertEquals(28, decoded.packetLength)
        assertFalse(decoded.fragmented)
    }

    @Test
    fun nonInitialIpv4FragmentDoesNotInventTransportPorts() {
        val packet = byteArrayOf(
            0x45, 0x00, 0x00, 0x18, 0x12, 0x34, 0x00, 0x01,
            0x40, 0x06, 0x00, 0x00, 0xc0.toByte(), 0x00, 0x02, 0x0a,
            0xc6.toByte(), 0x33, 0x64, 0x14, 0x01, 0xbb.toByte(), 0xd9.toByte(), 0x03,
        )

        val decoded = requireNotNull(CaptureIpPacketDecoder.decode(packet))

        assertEquals(CaptureTransportProtocol.TCP, decoded.transportProtocol)
        assertTrue(decoded.fragmented)
        assertNull(decoded.source.port)
        assertNull(decoded.destination.port)
    }

    @Test
    fun decodesDirectIpv6TcpEndpoints() {
        val packet = ByteArray(60)
        packet[0] = 0x60
        packet[4] = 0x00
        packet[5] = 0x14
        packet[6] = 0x06
        packet[7] = 0x40
        val source = byteArrayOf(
            0x20, 0x01, 0x0d, 0xb8.toByte(), 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 1,
        )
        val destination = byteArrayOf(
            0x20, 0x01, 0x0d, 0xb8.toByte(), 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 2,
        )
        source.copyInto(packet, destinationOffset = 8)
        destination.copyInto(packet, destinationOffset = 24)
        packet[40] = 0x01
        packet[41] = 0xbb.toByte()
        packet[42] = 0xd9.toByte()
        packet[43] = 0x03

        val decoded = requireNotNull(CaptureIpPacketDecoder.decode(packet))

        assertEquals(CaptureIpVersion.IPV6, decoded.ipVersion)
        assertEquals(CaptureTransportProtocol.TCP, decoded.transportProtocol)
        assertEquals(443, decoded.source.port)
        assertEquals(55555, decoded.destination.port)
        assertEquals(60, decoded.packetLength)
        assertTrue(decoded.source.address.isNotBlank())
        assertTrue(decoded.destination.address.isNotBlank())
    }

    @Test
    fun rejectsTruncatedOrUnknownIpPackets() {
        assertNull(CaptureIpPacketDecoder.decode(byteArrayOf(0x45, 0x00)))
        assertNull(CaptureIpPacketDecoder.decode(byteArrayOf(0x10, 0x00, 0x00, 0x00)))
    }
}
