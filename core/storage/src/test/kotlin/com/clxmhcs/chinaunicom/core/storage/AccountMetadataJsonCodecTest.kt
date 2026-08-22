package com.clxmhcs.chinaunicom.core.storage

import com.clxmhcs.chinaunicom.core.model.CarryForwardScope
import com.clxmhcs.chinaunicom.core.model.DisplayPlacement
import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.FlowSummaryGroup
import com.clxmhcs.chinaunicom.core.model.FrozenBalanceItem
import com.clxmhcs.chinaunicom.core.model.PackageCategory
import com.clxmhcs.chinaunicom.core.model.PackageDisplayPreference
import com.clxmhcs.chinaunicom.core.model.QuotaResourceStatus
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.RemainingFlowCategory
import com.clxmhcs.chinaunicom.core.model.RemainingFlowPackage
import com.clxmhcs.chinaunicom.core.model.RemainingFlowSummary
import com.clxmhcs.chinaunicom.core.model.RemainingMember
import com.clxmhcs.chinaunicom.core.model.RemainingMemberRole
import com.clxmhcs.chinaunicom.core.model.RemainingMemberUsage
import com.clxmhcs.chinaunicom.core.model.RemainingQuerySnapshot
import com.clxmhcs.chinaunicom.core.model.RemainingSMSPackage
import com.clxmhcs.chinaunicom.core.model.RemainingSMSSnapshot
import com.clxmhcs.chinaunicom.core.model.RemainingVoicePackage
import com.clxmhcs.chinaunicom.core.model.RemainingVoiceSnapshot
import com.clxmhcs.chinaunicom.core.model.ResourceDisplayKind
import com.clxmhcs.chinaunicom.core.model.ShareScope
import com.clxmhcs.chinaunicom.core.model.UnavailableBalanceDetail
import com.clxmhcs.chinaunicom.core.model.UnavailableLimitItem
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VoicePackage
import com.clxmhcs.chinaunicom.core.model.VoicePackageIdentityHint
import com.clxmhcs.chinaunicom.core.model.VoiceSummaryGroup
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AccountMetadataJsonCodecTest {
    private val codec = AccountMetadataJsonCodec()

    @Test
    fun fullAccountRoundTripsWithoutCredentials() {
        val usage = RemainingMemberUsage("138****8000", RemainingMemberRole.PRIMARY, 123.5, true)
        val account = UnicomAccount(
            id = UUID.fromString("11111111-2222-3333-4444-555555555555"),
            displayName = "主卡",
            mobile = "13800138000",
            packageName = "校园沃派",
            packages = listOf(
                FlowPackage(
                    id = "flow-1",
                    originalName = "国内流量",
                    totalMB = 2048.0,
                    usedMB = 512.25,
                    remainingMB = 1535.75,
                    detectedQuotaType = QuotaType.LIMITED,
                    detectedCategory = PackageCategory.GENERAL,
                    isShared = true,
                    shareScope = ShareScope.SHARED,
                    carryForwardScope = CarryForwardScope.INCLUDED,
                    currentMonthTotalMB = 1024.0,
                    carryForwardTotalMB = 1024.0,
                    endDateText = "2026-08-31",
                    rawType = "3",
                    rawCode = "code-flow",
                ),
            ),
            voicePackages = listOf(
                VoicePackage("voice-1", "国内语音", 300.0, 20.0, 280.0, false, false, "2026-08-31", "1", "voice-code"),
            ),
            remainingQuerySnapshot = RemainingQuerySnapshot(
                updatedAt = Instant.parse("2026-08-22T01:00:00Z"),
                members = listOf(RemainingMember("138****8000", "secret-member", RemainingMemberRole.PRIMARY, true)),
                flowSummaries = listOf(RemainingFlowSummary(RemainingFlowCategory.GENERAL, 1535.75, 512.25)),
                flowPackages = listOf(
                    RemainingFlowPackage(
                        id = "rq-flow",
                        name = "共享流量",
                        category = RemainingFlowCategory.GENERAL,
                        totalMB = 2048.0,
                        usedMB = 512.25,
                        remainingMB = 1535.75,
                        isShared = true,
                        memberUsages = listOf(usage),
                        endDateText = "2026-08-31",
                        feePolicyID = "fee",
                        rawType = "3",
                        rawCode = "rq",
                        isUnlimited = false,
                        speedLimitMB = null,
                    ),
                ),
                sharedFlowMemberTotals = listOf(usage),
                voice = RemainingVoiceSnapshot(
                    remainingMinutes = 280.0,
                    usedMinutes = 20.0,
                    packages = listOf(
                        RemainingVoicePackage("rv", "语音", 300.0, 20.0, 280.0, false, listOf(usage), null, null, null, null),
                    ),
                    unsharedPackages = emptyList(),
                ),
                sms = RemainingSMSSnapshot(
                    remainingCount = 90.0,
                    usedCount = 10.0,
                    packages = listOf(
                        RemainingSMSPackage("sms", "短信", 100.0, 10.0, 90.0, false, listOf(usage), null, null, null, null),
                    ),
                    unsharedPackages = emptyList(),
                ),
            ),
            balanceYuan = 88.66,
            balanceUpdatedAt = Instant.parse("2026-08-22T01:05:00Z"),
            unavailableBalanceDetail = UnavailableBalanceDetail(
                currentBalance = "88.66",
                unavailableLimitFee = "1.00",
                frozenFee = "2.00",
                totalUnavailable = "3.00",
                limitItems = listOf(UnavailableLimitItem("押金", "1.00", "13800138000", "202608", "detail", "style")),
                frozenItems = listOf(FrozenBalanceItem("冻结", "1", "2.00", "0", "2.00", "10010", "202608", "202609")),
            ),
            displayPreferences = listOf(
                PackageDisplayPreference(
                    packageKey = "flow-1",
                    alias = "主流量",
                    resourceKindOverride = ResourceDisplayKind.FLOW,
                    quotaTypeOverride = QuotaType.LIMITED,
                    categoryOverride = PackageCategory.GENERAL,
                    placement = DisplayPlacement.PRIMARY,
                    includeInSummary = true,
                    sortOrder = 0,
                ),
            ),
            summaryGroups = listOf(FlowSummaryGroup("group-1", "国内流量", listOf("flow-1"), true, 0)),
            voiceSummaryGroups = listOf(
                VoiceSummaryGroup(
                    id = "voice-group",
                    name = "语音",
                    packageKeys = listOf("voice-1"),
                    packageIdentityHints = mapOf(
                        "voice-1" to VoicePackageIdentityHint("国内语音", "1", "voice-code", false, false, 300.0),
                    ),
                    sortOrder = 0,
                ),
            ),
            quotaResourceStatus = QuotaResourceStatus.AVAILABLE,
            lastUpdatedAt = Instant.parse("2026-08-22T01:10:00Z"),
            lastErrorMessage = "previous error",
            isEnabled = true,
            sortOrder = 2,
        )

        val encoded = codec.encode(listOf(account))
        val decoded = codec.decode(encoded)

        assertEquals(listOf(account), decoded)
        val text = encoded.decodeToString()
        assertFalse(text.contains("\"cookie\""))
        assertFalse(text.contains("tokenOnline"))
        assertFalse(text.contains("token_online"))
        assertFalse(text.contains("\"appID\""))
    }

    @Test
    fun nullableVoiceAndSummaryConfigurationRoundTrip() {
        val account = UnicomAccount(
            displayName = "联通号码",
            mobile = "18600000000",
            voicePackages = null,
            summaryGroups = null,
            voiceSummaryGroups = null,
            quotaResourceStatus = null,
        )
        assertEquals(account, codec.decode(codec.encode(listOf(account))).single())
    }
}
