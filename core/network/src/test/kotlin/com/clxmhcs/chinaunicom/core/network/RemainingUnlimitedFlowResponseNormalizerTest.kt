package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.RemainingFlowCategory
import com.clxmhcs.chinaunicom.core.model.RemainingFlowPackage
import com.clxmhcs.chinaunicom.core.model.RemainingQuerySnapshot
import com.clxmhcs.chinaunicom.core.model.RemainingSMSSnapshot
import com.clxmhcs.chinaunicom.core.model.RemainingVoiceSnapshot
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemainingUnlimitedFlowResponseNormalizerTest {
    @Test
    fun strongIosUnlimitedSignatureUsesSummaryLimitValueInGb() {
        val packageValue = RemainingFlowPackage(
            id = "p1",
            name = "国内通用流量",
            category = RemainingFlowCategory.GENERAL,
            totalMB = 0.0,
            usedMB = 5120.0,
            remainingMB = 0.0,
            isShared = true,
            memberUsages = emptyList(),
            endDateText = null,
            feePolicyID = "F001",
            rawType = null,
            rawCode = null,
            isUnlimited = false,
            speedLimitMB = null,
        )
        val snapshot = RemainingQuerySnapshot(
            updatedAt = Instant.EPOCH,
            members = emptyList(),
            flowSummaries = emptyList(),
            flowPackages = listOf(packageValue),
            sharedFlowMemberTotals = emptyList(),
            voice = RemainingVoiceSnapshot(null, null, emptyList(), emptyList()),
            sms = RemainingSMSSnapshot(null, null, emptyList(), emptyList()),
        )
        val response = """
            {
              "data": {
                "summary": {"limitValue":10,"limitSpeed":"1"},
                "resources": [{"details":[{
                  "feePolicyId":"F001","feePolicyName":"国内通用流量","flowType":"1",
                  "limited":"1","total":0,"use":5120,"remain":-5120
                }]}]
              }
            }
        """.trimIndent().encodeToByteArray()

        val normalized = RemainingUnlimitedFlowResponseNormalizer().normalize(snapshot, response)
        val result = normalized.flowPackages.single()
        assertTrue(result.isUnlimited == true)
        assertEquals(10.0 * 1024.0, result.speedLimitMB ?: 0.0, 0.0001)
        assertNull(result.totalMB)
        assertNull(result.remainingMB)
    }
}
