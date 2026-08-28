package com.clxmhcs.chinaunicom.automation

import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.FlowSummaryGroup
import com.clxmhcs.chinaunicom.core.model.PackageCategory
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.ShortcutNotificationTemplateSettings
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VoicePackage
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationNotificationContentRendererTest {
    @Test
    fun rendersSavedTemplateAgainstCommittedAccountState() {
        val account = sampleAccount()
        val settings = ShortcutNotificationTemplateSettings(
            notifyTraffic = true,
            notifyVoice = true,
            notifyBalance = true,
            titleTemplate = "[套餐名称](已用[主流量.已用])",
            subtitleTemplate = "[通知号码] [数据截至] 在线[在线时长]",
            bodyTemplate = "国内余;流量共余\n语音余;余额[余额]",
        )

        val rendered = AutomationNotificationContentRenderer.render(
            account = account,
            settings = settings,
            usage = AutomationUsageContext(
                intervalStartAt = Instant.parse("2026-08-28T00:00:00Z"),
                intervalUsedMB = 1024.0,
                todayUsedMB = 2048.0,
            ),
        )

        requireNotNull(rendered)
        assertTrue(rendered.title.contains("校园套餐"))
        assertTrue(rendered.title.contains("2G"))
        assertTrue(rendered.subtitle.contains("186****9025"))
        assertTrue(rendered.subtitle.contains("1小时"))
        assertTrue(rendered.body.contains("国内余8G"))
        assertTrue(rendered.body.contains("流量共余8G"))
        assertTrue(rendered.body.contains("语音余60"))
        assertTrue(rendered.body.contains("余额23.68"))
    }

    @Test
    fun allSuccessContentDisabledProducesNoNotification() {
        val rendered = AutomationNotificationContentRenderer.render(
            account = sampleAccount(),
            settings = ShortcutNotificationTemplateSettings(
                notifyTraffic = false,
                notifyVoice = false,
                notifyBalance = false,
            ),
        )

        assertNull(rendered)
    }

    @Test
    fun failureContentMasksMobileAndKeepsReasonBounded() {
        val content = AutomationNotificationContentRenderer.failure(
            account = sampleAccount(),
            message = "network unavailable",
        )

        assertEquals("联通余量查询失败", content.title)
        assertEquals("186****9025", content.subtitle)
        assertTrue(content.body.contains("network unavailable"))
    }

    private fun sampleAccount(): UnicomAccount = UnicomAccount(
        displayName = "测试号码",
        mobile = "18600009025",
        packageName = "校园套餐",
        packages = listOf(
            FlowPackage(
                id = "domestic",
                originalName = "国内流量",
                totalMB = 10 * 1024.0,
                usedMB = 2 * 1024.0,
                remainingMB = 8 * 1024.0,
                detectedQuotaType = QuotaType.LIMITED,
                detectedCategory = PackageCategory.GENERAL,
                isShared = false,
            ),
        ),
        voicePackages = listOf(
            VoicePackage(
                id = "voice",
                originalName = "国内语音",
                totalMinutes = 100.0,
                usedMinutes = 40.0,
                remainingMinutes = 60.0,
                isUnlimited = false,
                isShared = false,
            ),
        ),
        balanceYuan = 23.68,
        summaryGroups = listOf(
            FlowSummaryGroup(
                name = "国内流量",
                packageKeys = listOf("domestic"),
                sortOrder = 0,
            ),
        ),
        lastUpdatedAt = Instant.parse("2026-08-28T01:00:00Z"),
    )
}
