package com.clxmhcs.chinaunicom.capture

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureTcpSegmentDecoderTest {
    @Test
    fun decodesIpv4TcpPayloadWithoutPersistingPacket() {
        val payload = "GET / HTTP/1.1\r\n\r\n".toByteArray()
        val packet = ipv4TcpPacket(sequence = 0x01020304, payload = payload)

        val segment = requireNotNull(CaptureTcpSegmentDecoder.decode(packet))

        assertEquals("192.0.2.2", segment.source.address)
        assertEquals(43210, segment.source.port)
        assertEquals("192.0.2.3", segment.destination.address)
        assertEquals(80, segment.destination.port)
        assertEquals(0x01020304L, segment.sequenceNumber)
        assertEquals("192.0.2.2:43210>192.0.2.3:80", segment.streamID)
        assertArrayEquals(payload, segment.payload)
        assertTrue(segment.payload !== packet)
    }

    @Test
    fun rejectsFragmentedIpv4ForTcpReassembly() {
        val packet = ipv4TcpPacket(sequence = 100, payload = "abc".toByteArray())
        packet[6] = 0x20
        packet[7] = 0x00

        assertNull(CaptureTcpSegmentDecoder.decode(packet))
    }

    @Test
    fun ignoresNonTcpPackets() {
        val packet = ipv4TcpPacket(sequence = 100, payload = byteArrayOf())
        packet[9] = 17

        assertNull(CaptureTcpSegmentDecoder.decode(packet))
    }

    private fun ipv4TcpPacket(sequence: Int, payload: ByteArray): ByteArray {
        val packet = ByteArray(40 + payload.size)
        packet[0] = 0x45
        writeU16(packet, 2, packet.size)
        packet[8] = 64
        packet[9] = 6
        packet[12] = 192.toByte()
        packet[13] = 0
        packet[14] = 2
        packet[15] = 2
        packet[16] = 192.toByte()
        packet[17] = 0
        packet[18] = 2
        packet[19] = 3

        writeU16(packet, 20, 43210)
        writeU16(packet, 22, 80)
        writeU32(packet, 24, sequence.toLong() and 0xFFFF_FFFFL)
        packet[32] = 0x50
        packet[33] = 0x18
        payload.copyInto(packet, destinationOffset = 40)
        return packet
    }

    private fun writeU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 1] = (value and 0xFF).toByte()
    }

    private fun writeU32(target: ByteArray, offset: Int, value: Long) {
        target[offset] = ((value ushr 24) and 0xFF).toByte()
        target[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 3] = (value and 0xFF).toByte()
    }
}
