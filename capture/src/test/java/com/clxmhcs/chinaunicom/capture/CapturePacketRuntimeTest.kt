package com.clxmhcs.chinaunicom.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CapturePacketRuntimeTest {
    @Test
    fun sessionTracksCountsAndBoundsRecentMetadata() {
        CapturePacketRuntime.beginSession(nowEpochMillis = 1_000L)
        repeat(130) { index ->
            CapturePacketRuntime.accept(
                packet = CapturePacketMetadata(
                    ipVersion = CaptureIpVersion.IPV4,
                    transportProtocol = if (index % 2 == 0) CaptureTransportProtocol.TCP else CaptureTransportProtocol.UDP,
                    source = CaptureEndpoint("192.0.2.10", 10_000 + index),
                    destination = CaptureEndpoint("192.0.2.20", 443),
                    packetLength = 100,
                ),
                nowEpochMillis = 2_000L + index,
            )
        }

        val snapshot = CapturePacketRuntime.snapshot()
        assertEquals(130L, snapshot.packetCount)
        assertEquals(13_000L, snapshot.byteCount)
        assertEquals(65L, snapshot.tcpPacketCount)
        assertEquals(65L, snapshot.udpPacketCount)
        assertEquals(0L, snapshot.otherPacketCount)
        assertEquals(128, CapturePacketRuntime.recentPackets().size)
    }
}
