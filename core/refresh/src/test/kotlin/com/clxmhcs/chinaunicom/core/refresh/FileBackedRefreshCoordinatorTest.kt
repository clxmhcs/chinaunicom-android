package com.clxmhcs.chinaunicom.core.refresh

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileBackedRefreshCoordinatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val account = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private var directory: File? = null

    @After
    fun cleanUp() {
        directory?.deleteRecursively()
    }

    @Test
    fun separateInstancesShareLeaseAndSuccessfulCache() {
        val now = Instant.parse("2026-08-21T10:00:00Z")
        val first = coordinator()
        val lease = (first.request(account, RefreshSource.AUTOMATIC, now) as RefreshDecision.Granted).lease

        assertTrue(coordinator().request(account, RefreshSource.MANUAL, now.plusSeconds(1)) is RefreshDecision.InFlight)
        assertTrue(first.complete(lease, "durable", now.plusSeconds(2)))

        val restored = coordinator().request(account, RefreshSource.AUTOMATIC, now.plusSeconds(3)) as RefreshDecision.Cached
        assertEquals("durable", restored.entry.value)
    }

    @Test
    fun expiredLeaseCanBeReplacedButOldOwnerCannotCommit() {
        val now = Instant.parse("2026-08-21T10:00:00Z")
        val first = coordinator()
        val oldLease = (first.request(account, RefreshSource.AUTOMATIC, now) as RefreshDecision.Granted).lease
        val newLease = (coordinator().request(account, RefreshSource.AUTOMATIC, now.plusSeconds(121)) as RefreshDecision.Granted).lease

        assertFalse(first.complete(oldLease, "stale", now.plusSeconds(122)))
        assertTrue(coordinator().complete(newLease, "new", now.plusSeconds(122)))
        assertEquals("new", coordinator().latest(account)?.value)
    }

    private fun coordinator(): FileBackedRefreshCoordinator<String> = FileBackedRefreshCoordinator(
        storageDirectory = directory ?: Files.createTempDirectory("refresh-coordinator-").toFile().also { directory = it },
        valueCodec = StringCodec,
        zoneId = zone,
    )

    private object StringCodec : RefreshValueCodec<String> {
        override fun encode(value: String): ByteArray = value.toByteArray(StandardCharsets.UTF_8)
        override fun decode(bytes: ByteArray): String = bytes.toString(StandardCharsets.UTF_8)
    }
}
