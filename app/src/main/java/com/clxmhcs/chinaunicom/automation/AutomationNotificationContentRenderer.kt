package com.clxmhcs.chinaunicom.automation

import com.clxmhcs.chinaunicom.core.model.FlowSummary
import com.clxmhcs.chinaunicom.core.model.ShortcutNotificationTemplateSettings
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

internal data class AutomationUsageContext(
    val intervalStartAt: Instant? = null,
    val intervalUsedMB: Double? = null,
    val todayUsedMB: Double? = null,
)

internal data class AutomationNotificationContent(
    val title: String,
    val subtitle: String,
    val body: String,
)

/**
 * Pure M13 notification renderer.
 *
 * It consumes the M11 per-account templates and committed UnicomAccount state only. It does not
 * perform carrier networking, credential reads, persistence writes, or Widget publication.
 */
internal object AutomationNotificationContentRenderer {
    private val dataTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun render(
        account: UnicomAccount,
        settings: ShortcutNotificationTemplateSettings,
        usage: AutomationUsageContext = AutomationUsageContext(),
    ): AutomationNotificationContent? {
        if (!settings.notifyTraffic && !settings.notifyVoice && !settings.notifyBalance) return null

        val flowPackages = account.visibleDetailPackages
        val totalUsed = flowPackages.sumOf { max(0.0, it.usedMB ?: 0.0) }
        val totalRemaining = flowPackages.sumOf { max(0.0, it.remainingMB ?: 0.0) }
        val totals = flowPackages.mapNotNull { packageValue ->
            val rawTotal = packageValue.totalMB
            val rawUsed = packageValue.usedMB
            val rawRemaining = packageValue.remainingMB
            when {
                rawTotal != null && rawTotal.isFinite() && rawTotal > 0 -> max(0.0, rawTotal)
                rawUsed != null && rawRemaining != null -> max(0.0, rawUsed) + max(0.0, rawRemaining)
                else -> null
            }
        }
        val totalQuota = totals.takeIf { it.isNotEmpty() }?.sum()
        val primary = account.primaryPackage
        val primaryUsed = primary?.usedMB?.takeIf(Double::isFinite)?.let { max(0.0, it) } ?: totalUsed
        val primaryRemaining = primary?.remainingMB?.takeIf(Double::isFinite)?.let { max(0.0, it) } ?: totalRemaining
        val primaryTotal = primary?.totalMB?.takeIf(Double::isFinite)?.let { max(0.0, it) } ?: totalQuota
        val voiceRemaining = account.visibleVoicePackages
            .filterNot(::isExcludedVoicePackage)
            .takeIf { it.isNotEmpty() }
            ?.sumOf { max(0.0, it.remainingMinutes ?: 0.0) }
        val updatedAt = account.lastUpdatedAt ?: account.remainingQuerySnapshot?.updatedAt ?: Instant.now()
        val notificationNumber = maskedMobile(account.mobile)
        val packageName = account.packageName.trim().ifEmpty { "联通余量" }

        val variables = linkedMapOf(
            "[套餐名称]" to compactPlanName(packageName),
            "[完整套餐名称]" to packageName,
            "[手机号]" to notificationNumber,
            "[通知号码]" to notificationNumber,
            "[主流量.已用]" to flowText(primaryUsed),
            "[主流量.剩余]" to flowText(primaryRemaining),
            "[主流量.总量]" to primaryTotal?.let(::flowText).orEmpty().ifEmpty { "--" },
            "[流量共余]" to flowText(totalRemaining),
            "[总流量]" to totalQuota?.let(::flowText).orEmpty().ifEmpty { "--" },
            "[在线时长]" to onlineDurationText(usage, updatedAt),
            "[本次用量]" to usage.intervalUsedMB?.let { flowText(max(0.0, it)) } ?: "--",
            "[今日用量]" to usage.todayUsedMB?.let { flowText(max(0.0, it)) } ?: "--",
            "[语音余量]" to voiceRemaining?.let(::minutesText) ?: "--",
            "[账户余额]" to account.balanceYuan?.takeIf(Double::isFinite)?.let(::balanceText) ?: "--",
            "[余额]" to account.balanceYuan?.takeIf(Double::isFinite)?.let(::balanceText) ?: "--",
            "[数据截至]" to dataTimeFormatter.format(updatedAt.atZone(ZoneId.systemDefault())),
        )

        val title = replaceVariables(settings.titleTemplate, variables).trim().ifEmpty { "联通余量" }
        val subtitle = replaceVariables(settings.subtitleTemplate, variables).trim()
        val body = renderBody(
            template = settings.bodyTemplate,
            account = account,
            settings = settings,
            variables = variables,
            totalRemaining = totalRemaining,
            voiceRemaining = voiceRemaining,
        ).trim().ifEmpty { "未选择通知内容" }

        return AutomationNotificationContent(title = title, subtitle = subtitle, body = body)
    }

    fun failure(account: UnicomAccount?, message: String): AutomationNotificationContent {
        val subtitle = account?.mobile?.let(::maskedMobile).orEmpty()
        val reason = message.trim().ifEmpty { "未知错误" }.take(180)
        return AutomationNotificationContent(
            title = "联通余量查询失败",
            subtitle = subtitle,
            body = "原因：$reason\n请检查网络或重新登录后再试。",
        )
    }

    internal fun primaryUsedMB(account: UnicomAccount): Double =
        account.visibleDetailPackages.sumOf { max(0.0, it.usedMB ?: 0.0) }

    private fun renderBody(
        template: String,
        account: UnicomAccount,
        settings: ShortcutNotificationTemplateSettings,
        variables: Map<String, String>,
        totalRemaining: Double,
        voiceRemaining: Double?,
    ): String {
        val groups = account.configuredSummaryGroups.map { group -> group to account.summary(group) }
        val shorthand = mapOf(
            "国内余" to flowGroupText(groups, listOf("国内", "全国")),
            "省内余" to flowGroupText(groups, listOf("省内", "畅游")),
            "小区余" to flowGroupText(groups, listOf("小区", "本地")),
            "校区余" to flowGroupText(groups, listOf("校区")),
            "校园余" to flowGroupText(groups, listOf("校园")),
            "流量共余" to flowText(totalRemaining),
            "语音余" to voiceRemaining?.let(::minutesText),
        )

        return template.lines().mapNotNull { line ->
            val renderedSegments = line.split(';', '；').mapNotNull { sourceSegment ->
                val segment = sourceSegment.trim()
                if (segment.isEmpty()) return@mapNotNull null

                val shorthandKey = shorthand.keys.firstOrNull { segment.contains(it) }
                if (shorthandKey != null) {
                    if (shorthandKey == "语音余" && !settings.notifyVoice) return@mapNotNull null
                    if (shorthandKey != "语音余" && !settings.notifyTraffic) return@mapNotNull null
                    val value = shorthand[shorthandKey]
                    if (value == null) return@mapNotNull null
                    return@mapNotNull replaceVariables(
                        segment.replace(shorthandKey, shorthandKey + value),
                        variables,
                    )
                }

                if (!settings.notifyBalance && (segment.contains("[余额]") || segment.contains("[账户余额]"))) {
                    return@mapNotNull null
                }
                replaceVariables(segment, variables).trim().takeIf(String::isNotEmpty)
            }
            renderedSegments.joinToString("；").takeIf(String::isNotEmpty)
        }.joinToString("\n")
    }

    private fun flowGroupText(
        groups: List<Pair<com.clxmhcs.chinaunicom.core.model.FlowSummaryGroup, FlowSummary>>,
        keywords: List<String>,
    ): String? {
        val match = groups.firstOrNull { (group, _) -> keywords.any(group.name::contains) }?.second ?: return null
        val value = if (match.isUnlimited) match.usedMB else match.remainingMB
        return value?.takeIf(Double::isFinite)?.let { flowText(max(0.0, it)) }
    }

    private fun replaceVariables(source: String, variables: Map<String, String>): String {
        var result = source
        variables.forEach { (key, value) -> result = result.replace(key, value) }
        return result
    }

    private fun onlineDurationText(usage: AutomationUsageContext, updatedAt: Instant): String {
        val start = usage.intervalStartAt ?: return "首次查询"
        val minutes = Duration.between(start, updatedAt).toMinutes().coerceAtLeast(0)
        val hours = minutes / 60
        val remainder = minutes % 60
        return when {
            hours > 0 && remainder > 0 -> "${hours}小时${remainder}分"
            hours > 0 -> "${hours}小时"
            minutes > 0 -> "${minutes}分钟"
            else -> "小于1分钟"
        }
    }

    private fun flowText(mb: Double): String {
        if (!mb.isFinite()) return "--"
        val safe = max(0.0, mb)
        return if (safe >= 1024.0) {
            val gb = safe / 1024.0
            val decimals = if (safe >= 100 * 1024.0) 1 else 2
            decimalText(gb, decimals) + "G"
        } else {
            val decimals = when {
                safe >= 1000 -> 0
                safe >= 100 -> 1
                else -> 2
            }
            decimalText(safe, decimals) + "M"
        }
    }

    private fun minutesText(value: Double): String {
        val rounded = value.toLong()
        return if (abs(value - rounded.toDouble()) < 0.0001) rounded.toString()
        else decimalText(value, 1)
    }

    private fun balanceText(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun decimalText(value: Double, decimals: Int): String {
        val format = "%.$decimals" + "f"
        val raw = String.format(Locale.US, format, value)
        return if (raw.contains('.')) raw.trimEnd('0').trimEnd('.') else raw
    }

    private fun maskedMobile(value: String): String {
        val digits = value.filter(Char::isDigit)
        return if (digits.length >= 7) digits.take(3) + "****" + digits.takeLast(4) else value
    }

    private fun compactPlanName(value: String): String {
        val normalized = value
            .replace("中国联通", "")
            .replace("38元", "")
            .replace('（', '(')
            .replace('）', ')')
            .replace("套餐套餐", "套餐")
            .trim()
            .ifEmpty { "联通余量" }
        return if (normalized.length <= 18) normalized else normalized.take(18) + "…"
    }

    private fun isExcludedVoicePackage(packageValue: com.clxmhcs.chinaunicom.core.model.VoicePackage): Boolean {
        val normalized = packageValue.originalName.replace(" ", "").replace('（', '(').replace('）', ')')
        return normalized.contains("全国一家语音", ignoreCase = true) ||
            normalized.contains("全国一家亲语音", ignoreCase = true)
    }
}
