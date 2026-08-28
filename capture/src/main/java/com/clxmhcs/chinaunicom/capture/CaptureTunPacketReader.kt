package com.clxmhcs.chinaunicom.capture

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

/**
 * Reads raw IP packets from a duplicated TUN descriptor.
 *
 * Raw packet bytes stay inside one reusable read buffer. M14-B metadata is emitted first; M14-C may
 * synchronously copy only the current TCP payload into an ephemeral segment for bounded HTTP header
 * reconstruction. Neither raw packets nor HTTP bodies are persisted.
 */
internal class CaptureTunPacketReader(
    tunnelInterface: ParcelFileDescriptor,
    private val onPacket: (CapturePacketMetadata) -> Unit,
    private val onTcpSegment: (CaptureTcpSegment) -> Unit = {},
    private val onFailure: (Throwable) -> Unit,
) : Closeable {
    private val running = AtomicBoolean(false)
    private val descriptor = ParcelFileDescriptor.dup(tunnelInterface.fileDescriptor)
    private var thread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread(::readLoop, "ChinaUnicom-CaptureTunReader").apply {
            isDaemon = true
            start()
        }
    }

    override fun close() {
        running.set(false)
        runCatching { descriptor.close() }
        thread?.interrupt()
        thread = null
    }

    private fun readLoop() {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        try {
            while (running.get()) {
                val count = try {
                    Os.read(descriptor.fileDescriptor, buffer, 0, buffer.size)
                } catch (error: ErrnoException) {
                    when (error.errno) {
                        OsConstants.EAGAIN, OsConstants.EINTR -> {
                            LockSupport.parkNanos(RETRY_DELAY_NANOS)
                            continue
                        }
                        else -> throw error
                    }
                }

                if (count <= 0) {
                    LockSupport.parkNanos(RETRY_DELAY_NANOS)
                    continue
                }

                CaptureIpPacketDecoder.decode(buffer, count)?.let(onPacket)
                CaptureTcpSegmentDecoder.decode(buffer, count)?.let(onTcpSegment)
            }
        } catch (error: Throwable) {
            if (running.get()) onFailure(error)
        }
    }

    companion object {
        private const val MAX_PACKET_SIZE = 65_535
        private const val RETRY_DELAY_NANOS = 5_000_000L
    }
}
