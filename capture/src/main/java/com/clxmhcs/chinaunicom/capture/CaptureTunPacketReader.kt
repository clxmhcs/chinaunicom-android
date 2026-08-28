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
 * The reader never stores packet payloads. Every successful read is decoded immediately into
 * bounded metadata and the reusable byte buffer is overwritten by the next read.
 */
internal class CaptureTunPacketReader(
    tunnelInterface: ParcelFileDescriptor,
    private val onPacket: (CapturePacketMetadata) -> Unit,
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
