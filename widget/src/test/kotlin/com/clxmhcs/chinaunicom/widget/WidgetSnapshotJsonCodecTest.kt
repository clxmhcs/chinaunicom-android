package com.clxmhcs.chinaunicom.widget

import com.clxmhcs.chinaunicom.core.model.WidgetDualSlotKind
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WidgetSnapshotJsonCodecTest {
    @Test
    fun roundTripsSingleAndDualSnapshots() {
        val accountID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val time = Instant.parse("2026-08-28T04:00:00Z")
        val single = WidgetQuotaSnapshot(
            accountID = accountID,
            mobile = "100****0000",
            displayName = "fixture",
            packageName = "package",
            todayUsageGB = 0.25,
            balanceYuan = 10.5,
            updatedAt = time,
            items = listOf(WidgetQuotaSnapshotItem("国内流量", remaining = 2.0, total = 5.0, used = 3.0, unit = WidgetSnapshotUnit.GIGABYTE)),
        )
        val dualAccount = WidgetDualAccountSnapshot(
            accountID = accountID,
            mobileSuffix = "0000",
            todayUsageGB = 0.25,
            balanceYuan = 10.5,
            updatedAt = time,
            items = listOf(WidgetDualDashboardItem("left-1", "国内流量", WidgetDualSlotKind.FLOW, 2.0, 5.0, 3.0, false)),
        )
        val archive = AndroidWidgetSnapshotStore.Archive(
            single = single,
            dual = WidgetDualSnapshot(dualAccount, null, time),
        )

        val codec = WidgetSnapshotJsonCodec()
        val decoded = codec.decode(codec.encode(archive))
        assertNotNull(decoded)
        assertEquals(single, decoded?.single)
        assertEquals(archive.dual, decoded?.dual)
    }
}
