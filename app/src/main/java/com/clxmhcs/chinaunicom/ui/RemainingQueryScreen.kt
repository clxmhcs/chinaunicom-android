package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clxmhcs.chinaunicom.core.model.DisplayUnit
import com.clxmhcs.chinaunicom.core.model.RemainingFlowCategory
import com.clxmhcs.chinaunicom.core.model.RemainingFlowPackage
import com.clxmhcs.chinaunicom.core.model.RemainingMember
import com.clxmhcs.chinaunicom.core.model.RemainingMemberRole
import com.clxmhcs.chinaunicom.core.model.RemainingMemberUsage
import com.clxmhcs.chinaunicom.core.model.RemainingSMSPackage
import com.clxmhcs.chinaunicom.core.model.RemainingVoicePackage
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.parser.FlowFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/**
 * UI-02 remaining-query presentation, derived from iOS RemainingQueryView.swift.
 *
 * This screen is intentionally snapshot-only. Entering it never starts a carrier request and it
 * does not own refresh authority; the closed repository/ViewModel flow remains the sole source of
 * RemainingQuerySnapshot data.
 */
@Composable
internal fun IosRemainingQueryScreen(
    account: UnicomAccount,
    onBack: () -> Unit,
) {
    val settings = LocalAppSettings.current
    var selectedTab by remember(account.id) { mutableStateOf(RemainingQueryTab.FLOW) }
    val snapshot = account.remainingQuerySnapshot
    val mobile = if (settings.hideMobileMiddleDigits) remainingMaskMobile(account.mobile) else account.mobile
    val groupedBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(groupedBackground),
    ) {
        RemainingQueryHeader(
            mobile = mobile,
            onBack = onBack,
        )
        RemainingQueryTabBar(
            selected = selectedTab,
            onSelect = { selectedTab = it },
        )
        RemainingDivider()

        if (snapshot == null) {
            RemainingSnapshotEmptyState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            when (selectedTab) {
                RemainingQueryTab.FLOW -> RemainingFlowTab(
                    account = account,
                    modifier = Modifier.weight(1f),
                )
                RemainingQueryTab.VOICE -> RemainingCountQuotaTab(
                    configuration = RemainingCountQuotaConfiguration.voice(account),
                    modifier = Modifier.weight(1f),
                )
                RemainingQueryTab.SMS -> RemainingCountQuotaTab(
                    configuration = RemainingCountQuotaConfiguration.sms(account),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private enum class RemainingQueryTab(val title: String) {
    FLOW("流量"),
    VOICE("语音"),
    SMS("短信"),
}

@Composable
private fun RemainingQueryHeader(
    mobile: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Text("‹ 返回", fontSize = 15.sp)
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                "余量查询",
                fontSize = 17.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                mobile,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun RemainingQueryTabBar(
    selected: RemainingQueryTab,
    onSelect: (RemainingQueryTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        RemainingQueryTab.entries.forEach { tab ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(tab) }
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    tab.title,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = if (selected == tab) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected == tab) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                    },
                )
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected == tab) MaterialTheme.colorScheme.primary else Color.Transparent,
                        ),
                )
            }
        }
    }
}

@Composable
private fun RemainingSnapshotEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("▱", fontSize = 25.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
            Text("暂无余量详情缓存", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "请先在首页完成一次余量刷新。进入本页面不会单独联网查询。",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RemainingFlowTab(
    account: UnicomAccount,
    modifier: Modifier = Modifier,
) {
    val snapshot = account.remainingQuerySnapshot ?: return
    var expandedCategories by remember(account.id, snapshot.updatedAt) {
        mutableStateOf(emptySet<RemainingFlowCategory>())
    }
    val formatter = remember { FlowFormatter(DisplayUnit.AUTOMATIC) }
    val categoryOrder = listOf(
        RemainingFlowCategory.GENERAL,
        RemainingFlowCategory.EXCLUSIVE,
        RemainingFlowCategory.OTHER,
    )

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 14.dp,
            top = 12.dp,
            end = 14.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (snapshot.members.isNotEmpty()) {
            item(key = "members") {
                RemainingMemberBanner(snapshot.members)
            }
        }

        if (snapshot.flowSummaries.isNotEmpty()) {
            item(key = "flow-summaries") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    categoryOrder.forEach { category ->
                        val summary = snapshot.flowSummaries.firstOrNull { it.category == category }
                        if (summary != null) {
                            RemainingFlowSummaryCard(
                                category = category,
                                remainingMB = summary.remainingMB,
                                usedMB = summary.usedMB,
                                packages = remainingPackagesForCategory(snapshot.flowPackages, category),
                                formatter = formatter,
                            )
                        }
                    }
                }
            }
        }

        categoryOrder.forEach { category ->
            val packages = remainingPackagesForCategory(snapshot.flowPackages, category)
            val summary = snapshot.flowSummaries.firstOrNull { it.category == category }
            if (summary != null || packages.isNotEmpty()) {
                item(key = "flow-category:${category.rawValue}") {
                    RemainingFlowSectionCard(
                        category = category,
                        summaryRemainingMB = summary?.remainingMB,
                        summaryUsedMB = summary?.usedMB,
                        packages = packages,
                        formatter = formatter,
                        isExpanded = expandedCategories.contains(category),
                        onToggleExpansion = {
                            expandedCategories = if (expandedCategories.contains(category)) {
                                expandedCategories - category
                            } else {
                                expandedCategories + category
                            }
                        },
                    )
                }
            }
        }

        if (snapshot.flowPackages.isEmpty()) {
            item(key = "flow-empty") {
                RemainingInlineEmptyState(
                    title = "暂无流量套餐",
                    description = "本次首页刷新没有返回可展示的流量套餐。",
                )
            }
        }

        item(key = "flow-updated") {
            RemainingUpdatedAt(snapshot.updatedAt)
        }
    }
}

@Composable
private fun RemainingMemberBanner(members: List<RemainingMember>) {
    val subtitle = remember(members) { remainingMemberBannerSubtitle(members) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("♟", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("成员使用情况", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    maxLines = 2,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("详情", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Text("›", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun RemainingFlowSummaryCard(
    category: RemainingFlowCategory,
    remainingMB: Double,
    usedMB: Double,
    packages: List<RemainingFlowPackage>,
    formatter: FlowFormatter,
) {
    val tint = remainingCategoryTint(category)
    val unlimited = packages.firstOrNull { it.resolvedIsUnlimited }
    Surface(
        modifier = Modifier.width(142.dp),
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.07f),
        border = androidx.compose.foundation.BorderStroke(0.6.dp, tint.copy(alpha = 0.10f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(remainingCategoryGlyph(category), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tint)
                Text(remainingCategoryTitle(category), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            if (unlimited != null) {
                Text(
                    remainingUnlimitedText(unlimited.speedLimitMB, formatter),
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("剩余：", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f))
                    Text(
                        formatter.string(remainingMB),
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            Text(
                "已用 ${formatter.string(usedMB)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RemainingFlowSectionCard(
    category: RemainingFlowCategory,
    summaryRemainingMB: Double?,
    summaryUsedMB: Double?,
    packages: List<RemainingFlowPackage>,
    formatter: FlowFormatter,
    isExpanded: Boolean,
    onToggleExpansion: () -> Unit,
) {
    val tint = remainingCategoryTint(category)
    val visiblePackages = if (isExpanded) packages else packages.take(2)
    val unlimited = packages.firstOrNull { it.resolvedIsUnlimited }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tint.copy(alpha = 0.045f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.09f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(remainingCategoryGlyph(category), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tint)
                }
                Text(
                    remainingCategoryTitle(category),
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (unlimited != null) {
                        Text(
                            remainingUnlimitedText(unlimited.speedLimitMB, formatter),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    } else if (summaryRemainingMB != null) {
                        Text(
                            "剩余 ${formatter.string(summaryRemainingMB)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                    if (summaryUsedMB != null) {
                        Text(
                            "已用 ${formatter.string(summaryUsedMB)}",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                            maxLines = 1,
                        )
                    }
                }
            }

            if (visiblePackages.isEmpty()) {
                Text(
                    "暂无该类型流量套餐",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                )
            } else {
                visiblePackages.forEachIndexed { index, packageValue ->
                    RemainingFlowPackageRow(packageValue, tint, formatter)
                    if (index != visiblePackages.lastIndex) RemainingInsetDivider()
                }
            }

            if (packages.size > 2) {
                RemainingInsetDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleExpansion)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (isExpanded) "收起" else "查看更多",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (isExpanded) "⌃" else "⌄",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RemainingFlowPackageRow(
    packageValue: RemainingFlowPackage,
    tint: Color,
    formatter: FlowFormatter,
) {
    val fraction = remainingFlowProgress(packageValue)
    Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                packageValue.name,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
            )
            if (packageValue.isShared) RemainingTag("共享", tint)
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                "已用 ${formatter.string(packageValue.usedMB)}",
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (packageValue.resolvedIsUnlimited) {
                    remainingUnlimitedText(packageValue.speedLimitMB, formatter)
                } else {
                    "剩余 ${formatter.string(packageValue.remainingMB)} / 共 ${remainingFlowTotalText(packageValue, formatter)}"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                maxLines = 1,
            )
        }

        RemainingProgressBar(fraction = fraction, tint = tint)

        packageValue.endDateText?.takeIf { it.isNotBlank() }?.let { endDate ->
            Text(
                "有效期：$endDate",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }
    }
}

private data class RemainingCountPackageUi(
    val id: String,
    val name: String,
    val total: Double?,
    val used: Double?,
    val remaining: Double?,
    val isShared: Boolean,
    val memberUsages: List<RemainingMemberUsage>,
    val endDateText: String?,
)

private enum class RemainingCountKind(val unitText: String) {
    VOICE("分钟"),
    SMS("条"),
}

private data class RemainingCountQuotaConfiguration(
    val kind: RemainingCountKind,
    val members: List<RemainingMember>,
    val parentRemaining: Double?,
    val parentUsed: Double?,
    val sharedPackages: List<RemainingCountPackageUi>,
    val unsharedPackages: List<RemainingCountPackageUi>,
    val updatedAt: Instant,
    val remainingTitle: String,
    val usedTitle: String,
    val sectionTitle: String,
    val emptyTitle: String,
    val emptyDescription: String,
) {
    companion object {
        fun voice(account: UnicomAccount): RemainingCountQuotaConfiguration {
            val snapshot = requireNotNull(account.remainingQuerySnapshot)
            return RemainingCountQuotaConfiguration(
                kind = RemainingCountKind.VOICE,
                members = snapshot.members,
                parentRemaining = snapshot.voice.remainingMinutes,
                parentUsed = snapshot.voice.usedMinutes,
                sharedPackages = snapshot.voice.packages.map(RemainingVoicePackage::toCountUi),
                unsharedPackages = snapshot.voice.unsharedPackages.map(RemainingVoicePackage::toCountUi),
                updatedAt = snapshot.updatedAt,
                remainingTitle = "剩余语音",
                usedTitle = "已用语音",
                sectionTitle = "语音套餐",
                emptyTitle = "暂无语音套餐",
                emptyDescription = "本次首页刷新没有返回可展示的语音套餐。",
            )
        }

        fun sms(account: UnicomAccount): RemainingCountQuotaConfiguration {
            val snapshot = requireNotNull(account.remainingQuerySnapshot)
            return RemainingCountQuotaConfiguration(
                kind = RemainingCountKind.SMS,
                members = snapshot.members,
                parentRemaining = snapshot.sms.remainingCount,
                parentUsed = snapshot.sms.usedCount,
                sharedPackages = snapshot.sms.packages.map(RemainingSMSPackage::toCountUi),
                unsharedPackages = snapshot.sms.unsharedPackages.map(RemainingSMSPackage::toCountUi),
                updatedAt = snapshot.updatedAt,
                remainingTitle = "剩余短信",
                usedTitle = "已用短信",
                sectionTitle = "短信套餐",
                emptyTitle = "暂无短信套餐",
                emptyDescription = "本次首页刷新没有返回可展示的短信套餐。",
            )
        }
    }
}

private fun RemainingVoicePackage.toCountUi() = RemainingCountPackageUi(
    id = id,
    name = name,
    total = totalMinutes,
    used = usedMinutes,
    remaining = remainingMinutes,
    isShared = isShared,
    memberUsages = memberUsages,
    endDateText = endDateText,
)

private fun RemainingSMSPackage.toCountUi() = RemainingCountPackageUi(
    id = id,
    name = name,
    total = totalCount,
    used = usedCount,
    remaining = remainingCount,
    isShared = isShared,
    memberUsages = memberUsages,
    endDateText = endDateText,
)

@Composable
private fun RemainingCountQuotaTab(
    configuration: RemainingCountQuotaConfiguration,
    modifier: Modifier = Modifier,
) {
    var selectedMemberID by remember(configuration.updatedAt) { mutableStateOf<String?>(null) }
    val members = remember(configuration.members) { remainingSortedMembers(configuration.members) }
    val selectedMember = members.firstOrNull { it.id == selectedMemberID }
        ?: members.firstOrNull { it.isCurrentLogin == true }
        ?: members.firstOrNull { it.role == RemainingMemberRole.PRIMARY }
        ?: members.firstOrNull()
    val usesUnshared = if (selectedMember == null) {
        true
    } else if (members.any { it.isCurrentLogin == true }) {
        selectedMember.isCurrentLogin == true
    } else {
        selectedMember.role == RemainingMemberRole.PRIMARY
    }
    val visiblePackages = configuration.sharedPackages + if (usesUnshared) configuration.unsharedPackages else emptyList()
    val displayedRemaining = if (usesUnshared) {
        configuration.parentRemaining ?: remainingSum(visiblePackages.map { it.remaining })
    } else {
        remainingSum(configuration.sharedPackages.map { it.remaining })
    }
    val displayedUsed = if (usesUnshared) {
        configuration.parentUsed ?: remainingSum(visiblePackages.map { it.used })
    } else {
        remainingSum(configuration.sharedPackages.map { it.used })
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 14.dp,
            top = 12.dp,
            end = 14.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "overview") {
            RemainingCountOverviewCard(
                configuration = configuration,
                members = members,
                selectedMember = selectedMember,
                selectedMemberID = selectedMemberID,
                onSelectMember = { selectedMemberID = it },
                remaining = displayedRemaining,
                used = displayedUsed,
            )
        }

        item(key = "packages") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(configuration.sectionTitle, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (visiblePackages.isEmpty()) {
                    RemainingInlineEmptyState(
                        title = configuration.emptyTitle,
                        description = configuration.emptyDescription,
                    )
                } else {
                    visiblePackages.forEach { packageValue ->
                        RemainingCountPackageCard(
                            packageValue = packageValue,
                            kind = configuration.kind,
                        )
                    }
                }
            }
        }

        item(key = "updated") {
            RemainingUpdatedAt(configuration.updatedAt)
        }
    }
}

@Composable
private fun RemainingCountOverviewCard(
    configuration: RemainingCountQuotaConfiguration,
    members: List<RemainingMember>,
    selectedMember: RemainingMember?,
    selectedMemberID: String?,
    onSelectMember: (String) -> Unit,
    remaining: Double?,
    used: Double?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            RemainingMemberPicker(
                members = members,
                selectedMember = selectedMember,
                selectedMemberID = selectedMemberID,
                onSelectMember = onSelectMember,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            )
            RemainingInsetDivider()
            Row(
                modifier = Modifier.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemainingCountMetric(
                    title = configuration.remainingTitle,
                    value = remaining,
                    unitText = configuration.kind.unitText,
                    tint = Color(0xFFF0597D),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                )
                RemainingCountMetric(
                    title = configuration.usedTitle,
                    value = used,
                    unitText = configuration.kind.unitText,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RemainingMemberPicker(
    members: List<RemainingMember>,
    selectedMember: RemainingMember?,
    selectedMemberID: String?,
    onSelectMember: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = members.size > 1) { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("▣", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(
                remainingMemberDisplayLabel(selectedMember),
                modifier = Modifier.weight(1f),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            )
            if (members.size > 1) {
                Text("⌄", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            members.forEach { member ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (member.id == (selectedMemberID ?: selectedMember?.id)) {
                                "✓ ${remainingMemberDisplayLabel(member)}"
                            } else {
                                remainingMemberDisplayLabel(member)
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelectMember(member.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun RemainingCountMetric(
    title: String,
    value: Double?,
    unitText: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(title, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                remainingNumber(value),
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                unitText,
                modifier = Modifier.padding(bottom = 3.dp),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
            )
        }
    }
}

@Composable
private fun RemainingCountPackageCard(
    packageValue: RemainingCountPackageUi,
    kind: RemainingCountKind,
) {
    val tint = if (packageValue.isShared) Color(0xFFF25780) else Color(0xFF5294ED)
    val fraction = remainingCountProgress(packageValue)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(packageValue.name, modifier = Modifier.weight(1f), fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
                RemainingTag(if (packageValue.isShared) "共享" else "独享", tint)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    "已用 ${remainingCountText(packageValue.used, kind)}",
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "剩余 ${remainingCountText(packageValue.remaining, kind)} / 共 ${remainingCountText(packageValue.total, kind)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    maxLines = 1,
                )
            }
            RemainingProgressBar(fraction = fraction, tint = tint)
            if (packageValue.isShared && packageValue.memberUsages.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    packageValue.memberUsages.forEach { usage ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                remainingMemberUsageLabel(usage),
                                modifier = Modifier.weight(1f),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                            )
                            Text(
                                "已用 ${remainingCountText(usage.usedValue, kind)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                            )
                        }
                    }
                }
            }
            packageValue.endDateText?.takeIf { it.isNotBlank() }?.let { endDate ->
                Text(
                    "有效期：$endDate",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun RemainingTag(text: String, tint: Color) {
    Text(
        text,
        modifier = Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.09f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Medium,
        color = tint,
    )
}

@Composable
private fun RemainingProgressBar(fraction: Double, tint: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
    ) {
        if (fraction > 0.0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.0, 1.0).toFloat())
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.72f)),
            )
        }
    }
}

@Composable
private fun RemainingInlineEmptyState(
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(
            description,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RemainingUpdatedAt(date: Instant) {
    Text(
        "数据更新于 ${remainingUpdatedTimeFormatter.format(date.atZone(ZoneId.systemDefault()))}",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RemainingDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
    )
}

@Composable
private fun RemainingInsetDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
    )
}

private fun remainingPackagesForCategory(
    packages: List<RemainingFlowPackage>,
    category: RemainingFlowCategory,
): List<RemainingFlowPackage> = when (category) {
    RemainingFlowCategory.GENERAL -> packages.filter { it.category == RemainingFlowCategory.GENERAL }
    RemainingFlowCategory.EXCLUSIVE -> packages.filter { it.category == RemainingFlowCategory.EXCLUSIVE }
    RemainingFlowCategory.OTHER -> packages.filter {
        it.category == RemainingFlowCategory.OTHER ||
            it.category == RemainingFlowCategory.UNKNOWN ||
            it.category == null
    }
    RemainingFlowCategory.UNKNOWN -> emptyList()
}

private fun remainingCategoryTitle(category: RemainingFlowCategory): String = when (category) {
    RemainingFlowCategory.GENERAL -> "通用流量"
    RemainingFlowCategory.EXCLUSIVE -> "专属流量"
    RemainingFlowCategory.OTHER, RemainingFlowCategory.UNKNOWN -> "其他流量"
}

private fun remainingCategoryTint(category: RemainingFlowCategory): Color = when (category) {
    RemainingFlowCategory.GENERAL -> Color(0xFF338CF5)
    RemainingFlowCategory.EXCLUSIVE -> Color(0xFFFA6E94)
    RemainingFlowCategory.OTHER, RemainingFlowCategory.UNKNOWN -> Color(0xFF8C7DDB)
}

private fun remainingCategoryGlyph(category: RemainingFlowCategory): String = when (category) {
    RemainingFlowCategory.GENERAL -> "◎"
    RemainingFlowCategory.EXCLUSIVE -> "◇"
    RemainingFlowCategory.OTHER, RemainingFlowCategory.UNKNOWN -> "•••"
}

private fun remainingMemberBannerSubtitle(members: List<RemainingMember>): String {
    val first = members.firstOrNull() ?: return "暂无成员号卡信息"
    val suffix = first.maskedNumber.filter(Char::isDigit).takeLast(4)
    return when {
        members.size == 1 && suffix.isEmpty() -> "当前账户共1张号卡"
        members.size == 1 -> "账户下尾号${suffix}共1张号卡"
        suffix.isEmpty() -> "当前账户共${members.size}张号卡"
        else -> "账户下尾号${suffix}等${members.size}张号卡"
    }
}

private fun remainingSortedMembers(members: List<RemainingMember>): List<RemainingMember> = members.sortedWith(
    compareBy<RemainingMember> {
        when {
            it.isCurrentLogin == true -> 0
            it.role == RemainingMemberRole.PRIMARY -> 1
            it.role == RemainingMemberRole.SECONDARY -> 2
            else -> 3
        }
    }.thenBy { it.maskedNumber },
)

private fun remainingMemberDisplayLabel(member: RemainingMember?): String {
    if (member == null) return "当前号码"
    val role = when (member.role) {
        RemainingMemberRole.PRIMARY -> "主卡"
        RemainingMemberRole.SECONDARY -> "副卡"
        RemainingMemberRole.UNKNOWN -> null
    }
    return listOfNotNull(member.maskedNumber, role).joinToString(" · ")
}

private fun remainingMemberUsageLabel(usage: RemainingMemberUsage): String {
    val role = when (usage.role) {
        RemainingMemberRole.PRIMARY -> "主卡"
        RemainingMemberRole.SECONDARY -> "副卡"
        RemainingMemberRole.UNKNOWN -> null
    }
    return listOfNotNull(usage.maskedNumber, role).joinToString(" · ")
}

private fun remainingFlowProgress(packageValue: RemainingFlowPackage): Double {
    val used = packageValue.usedMB?.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
    if (packageValue.resolvedIsUnlimited) {
        val tenGB = 10.0 * 1024.0
        val hundredGB = 100.0 * 1024.0
        val reference = when {
            used <= tenGB -> tenGB
            used <= hundredGB -> hundredGB
            else -> ceil(used / hundredGB) * hundredGB
        }
        return (used / reference).coerceIn(0.0, 1.0)
    }
    val total = packageValue.totalMB?.takeIf { it.isFinite() && it > 0.0 }
        ?: run {
            val remaining = packageValue.remainingMB?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)
            if (remaining != null) used + remaining else null
        }
    return if (total == null || total <= 0.0) 0.0 else (used / total).coerceIn(0.0, 1.0)
}

private fun remainingCountProgress(packageValue: RemainingCountPackageUi): Double {
    val used = packageValue.used?.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
    val total = packageValue.total?.takeIf { it.isFinite() && it > 0.0 }
        ?: run {
            val remaining = packageValue.remaining?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)
            if (remaining != null) used + remaining else null
        }
    return if (total == null || total <= 0.0) 0.0 else (used / total).coerceIn(0.0, 1.0)
}

private fun remainingFlowTotalText(packageValue: RemainingFlowPackage, formatter: FlowFormatter): String {
    val total = packageValue.totalMB?.takeIf { it.isFinite() && it >= 0.0 }
        ?: run {
            val used = packageValue.usedMB?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)
            val remaining = packageValue.remainingMB?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)
            if (used != null && remaining != null) used + remaining else null
        }
    return formatter.string(total)
}

private fun remainingUnlimitedText(speedLimitMB: Double?, formatter: FlowFormatter): String {
    val speedLimit = speedLimitMB?.takeIf { it.isFinite() && it > 0.0 } ?: return "不限量"
    return "不限量·${formatter.string(speedLimit)}限速"
}

private fun remainingCountText(value: Double?, kind: RemainingCountKind): String {
    val number = remainingNumber(value)
    return if (number == "--") number else "$number ${kind.unitText}"
}

private fun remainingNumber(value: Double?): String {
    if (value == null || !value.isFinite()) return "--"
    val safe = value.coerceAtLeast(0.0)
    return if (abs(safe - safe.toLong()) < 0.0001) {
        safe.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", safe)
    }
}

private fun remainingSum(values: List<Double?>): Double? {
    val finite = values.mapNotNull { it?.takeIf(Double::isFinite) }
    return finite.takeIf { it.isNotEmpty() }?.sum()
}

private fun remainingMaskMobile(value: String): String {
    val digits = value.filter(Char::isDigit)
    if (digits.length < 7) return value
    return digits.replaceRange(3, 7, "****")
}

private val remainingUpdatedTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
