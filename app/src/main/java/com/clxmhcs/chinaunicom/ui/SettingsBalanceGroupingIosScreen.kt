package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clxmhcs.chinaunicom.R
import com.clxmhcs.chinaunicom.core.model.AppSettings
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.balance.BalanceAccountGroup
import java.util.UUID

private val BalanceGroupingBackground = Color(0xFFF2F2F7)
private val BalanceGroupingSecondary = Color(0xFF8E8E93)
private val BalanceGroupingSeparator = Color(0xFFE5E5EA)
private val BalanceGroupingChevron = Color(0xFF7C7C80)
private val BalanceGroupingAccent = Color(0xFF3478F6)
private val BalanceGroupingDanger = Color(0xFFFF3B30)
private val BalanceGroupingUnicomRed = Color(0xFFFF3B30)

/**
 * Visual/source parity for iOS `BalanceAccountGroupingView`.
 *
 * Membership mutations remain immediate and authoritative in BalanceRepository, exactly as the
 * current iOS AppStore does. "保存并收起" only collapses the group card; it does not introduce a
 * second draft store. Accounts already assigned to another group are intentionally locked here,
 * matching the current iOS UI contract, even though the repository itself remains defensive.
 */
@Composable
internal fun IosBalanceGroupingRefinedScreen(
    accounts: List<UnicomAccount>,
    groups: List<BalanceAccountGroup>,
    settings: AppSettings,
    locationFor: (String) -> String?,
    isUnicomFor: (String) -> Boolean,
    balanceRefreshIntervalMinutes: Int,
    onAddGroup: () -> Unit,
    onDeleteGroup: (UUID) -> Unit,
    onToggleMember: (UUID, UUID) -> Unit,
    onBack: () -> Unit,
) {
    var expandedGroupIDs by remember { mutableStateOf<Set<UUID>>(emptySet()) }
    var pendingExpandAfterAdd by remember { mutableStateOf(false) }
    var previousIDsBeforeAdd by remember { mutableStateOf<Set<UUID>>(emptySet()) }
    var pendingDeletionGroupID by remember { mutableStateOf<UUID?>(null) }

    LaunchedEffect(groups.map { it.id }) {
        if (pendingExpandAfterAdd) {
            val added = groups.firstOrNull { it.id !in previousIDsBeforeAdd }
            if (added != null) {
                expandedGroupIDs = expandedGroupIDs + added.id
                pendingExpandAfterAdd = false
                previousIDsBeforeAdd = emptySet()
            }
        }
        expandedGroupIDs = expandedGroupIDs.intersect(groups.map { it.id }.toSet())
    }

    val assignedAccountIDs = remember(groups) { groups.flatMap { it.memberAccountIDs }.toSet() }
    val standaloneAccounts = remember(accounts, assignedAccountIDs) {
        accounts.filter { it.id !in assignedAccountIDs }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BalanceGroupingBackground),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            BalanceGroupingHeader(onBack)
            Spacer(Modifier.height(20.dp))
            BalanceGroupingInfoCard(
                "把实际属于同一合账账户的号码放进同一个合账组。有效合账组的余额与账单统一使用“设置 > 刷新 > 余额/账单 刷新号码编辑”确定的账务代表号码；首页余额号码属于该组时优先使用首页号码。余额结果仍作为组内号码共同的余额缓存。未加入有效合账组的号码会独立查询。",
            )
        }

        groups.forEachIndexed { index, group ->
            item(key = group.id) {
                BalanceGroupingGroupCard(
                    group = group,
                    groupIndex = index,
                    accounts = accounts,
                    groups = groups,
                    settings = settings,
                    locationFor = locationFor,
                    isUnicomFor = isUnicomFor,
                    expanded = group.id in expandedGroupIDs,
                    onToggleExpanded = {
                        expandedGroupIDs = if (group.id in expandedGroupIDs) {
                            expandedGroupIDs - group.id
                        } else {
                            expandedGroupIDs + group.id
                        }
                    },
                    onToggleMember = onToggleMember,
                    onSaveAndCollapse = { expandedGroupIDs = expandedGroupIDs - group.id },
                    onDelete = { pendingDeletionGroupID = group.id },
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                previousIDsBeforeAdd = groups.map { it.id }.toSet()
                                pendingExpandAfterAdd = true
                                onAddGroup()
                            }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BalanceGroupingPlusIcon()
                        Spacer(Modifier.size(14.dp))
                        Text(
                            text = if (groups.isEmpty()) "新增合账组" else "新增另一个合账组",
                            color = BalanceGroupingAccent,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
                Text(
                    text = if (groups.isEmpty()) {
                        "至少选择 2 个号码后，该组才会按合账规则工作。"
                    } else {
                        "已经属于其它合账组的号码会锁定为不可选；需要先回到原合账组取消选择后，才能加入新组。"
                    },
                    color = BalanceGroupingSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }
        }

        if (standaloneAccounts.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        text = "独立号码",
                        color = BalanceGroupingSecondary,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 18.dp),
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Column {
                            standaloneAccounts.forEachIndexed { index, account ->
                                BalanceGroupingIdentityRow(
                                    account = account,
                                    settings = settings,
                                    locationFor = locationFor,
                                    isUnicomFor = isUnicomFor,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                                )
                                if (index < standaloneAccounts.lastIndex) {
                                    Divider(
                                        modifier = Modifier.padding(start = 72.dp, end = 18.dp),
                                        thickness = 0.7.dp,
                                        color = BalanceGroupingSeparator,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            BalanceGroupingInfoCard(
                "余额自动刷新统一按 ${balanceRefreshIntervalMinutes} 分钟计算：App 打开或从后台回到前台时，距离该余额单元上次成功刷新不足 ${balanceRefreshIntervalMinutes} 分钟则不请求；达到 ${balanceRefreshIntervalMinutes} 分钟才刷新。App 持续在前台时也以每个余额单元上次成功刷新时间为基准继续每 ${balanceRefreshIntervalMinutes} 分钟维护一次。App 被关闭后不会继续刷新。",
            )
        }
    }

    pendingDeletionGroupID?.let { groupID ->
        val groupName = groups.firstOrNull { it.id == groupID }?.name?.trim().orEmpty().ifEmpty { "合账组" }
        AlertDialog(
            onDismissRequest = { pendingDeletionGroupID = null },
            title = { Text("删除${groupName}") },
            text = { Text("删除后，该组号码将恢复为独立号码。确定删除此合账组吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeletionGroupID = null
                        expandedGroupIDs = expandedGroupIDs - groupID
                        onDeleteGroup(groupID)
                    },
                ) {
                    Text("删除", color = BalanceGroupingDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletionGroupID = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun BalanceGroupingHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(22.dp)) {
                    val stroke = 2.7.dp.toPx()
                    drawLine(
                        color = Color(0xFF1C1C1E),
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.18f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.50f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color(0xFF1C1C1E),
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.50f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.82f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        Text(
            text = "合账号码选择",
            modifier = Modifier.weight(1f),
            color = Color.Black,
            textAlign = TextAlign.Center,
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(44.dp))
    }
}

@Composable
private fun BalanceGroupingInfoCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
    ) {
        Text(
            text = text,
            color = BalanceGroupingSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
        )
    }
}

@Composable
private fun BalanceGroupingGroupCard(
    group: BalanceAccountGroup,
    groupIndex: Int,
    accounts: List<UnicomAccount>,
    groups: List<BalanceAccountGroup>,
    settings: AppSettings,
    locationFor: (String) -> String?,
    isUnicomFor: (String) -> Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleMember: (UUID, UUID) -> Unit,
    onSaveAndCollapse: () -> Unit,
    onDelete: () -> Unit,
) {
    val groupName = group.name.trim().ifEmpty { "合账组 ${groupIndex + 1}" }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = groupName,
                        color = Color.Black,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (group.memberAccountIDs.size >= 2) {
                            "已保存 ${group.memberAccountIDs.size} 个号码"
                        } else {
                            "请选择至少 2 个号码"
                        },
                        color = BalanceGroupingSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
                BalanceGroupingChevron(expanded)
            }

            if (expanded) {
                Divider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    thickness = 0.7.dp,
                    color = BalanceGroupingSeparator,
                )

                accounts.forEachIndexed { index, account ->
                    val selected = account.id in group.memberAccountIDs
                    val lockedByOtherGroup = groups.any { other ->
                        other.id != group.id && account.id in other.memberAccountIDs
                    }
                    val enabled = selected || !lockedByOtherGroup

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { onToggleMember(account.id, group.id) }
                            .alpha(if (enabled) 1f else 0.35f)
                            .padding(start = 18.dp, end = 18.dp, top = 13.dp, bottom = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BalanceGroupingCheckCircle(selected = selected)
                        Spacer(Modifier.size(12.dp))
                        BalanceGroupingIdentityRow(
                            account = account,
                            settings = settings,
                            locationFor = locationFor,
                            isUnicomFor = isUnicomFor,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (index < accounts.lastIndex) {
                        Divider(
                            modifier = Modifier.padding(start = 72.dp, end = 18.dp),
                            thickness = 0.7.dp,
                            color = BalanceGroupingSeparator,
                        )
                    }
                }

                Divider(
                    modifier = Modifier.padding(start = 72.dp, end = 18.dp),
                    thickness = 0.7.dp,
                    color = BalanceGroupingSeparator,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSaveAndCollapse)
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BalanceGroupingCheckCircle(selected = true)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "保存并收起",
                        color = BalanceGroupingAccent,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                    )
                }

                Divider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    thickness = 0.7.dp,
                    color = BalanceGroupingSeparator,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BalanceGroupingTrashIcon()
                    Spacer(Modifier.size(14.dp))
                    Text(
                        text = "删除此合账组",
                        color = BalanceGroupingDanger,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                    )
                }

                Text(
                    text = if (group.memberAccountIDs.size >= 2) {
                        "余额刷新时只请求其中 1 个号码。选择完成后点击“保存并收起”。"
                    } else {
                        "当前选择不足 2 个号码，暂按独立号码处理。"
                    },
                    color = BalanceGroupingSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                )
            } else {
                Text(
                    text = if (group.memberAccountIDs.size >= 2) {
                        "余额刷新时只请求其中 1 个号码。点击合账组可再次展开修改。"
                    } else {
                        "当前选择不足 2 个号码，暂按独立号码处理。"
                    },
                    color = BalanceGroupingSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp, top = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun BalanceGroupingIdentityRow(
    account: UnicomAccount,
    settings: AppSettings,
    locationFor: (String) -> String?,
    isUnicomFor: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val location = locationFor(account.mobile)?.trim()?.takeIf { it.isNotEmpty() }
        ?: account.displayName.trim().takeIf { it.isNotEmpty() && it != "联通号码" }
        ?: "归属地未知"

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isUnicomFor(account.mobile)) {
            Image(
                painter = painterResource(R.drawable.china_unicom_knot_watermark),
                contentDescription = "中国联通",
                colorFilter = ColorFilter.tint(BalanceGroupingUnicomRed),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(9.dp))
        }
        Text(
            text = displayBalanceGroupingMobile(account.mobile, settings),
            color = Color.Black,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "（${location}）",
            color = BalanceGroupingSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun BalanceGroupingCheckCircle(selected: Boolean) {
    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (selected) {
                drawCircle(color = BalanceGroupingAccent)
                val stroke = 2.dp.toPx()
                drawLine(
                    color = Color.White,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.27f, size.height * 0.51f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.68f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White,
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.68f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.73f, size.height * 0.33f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            } else {
                drawCircle(
                    color = Color(0xFFC7C7CC),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun BalanceGroupingChevron(expanded: Boolean) {
    Canvas(Modifier.size(width = 28.dp, height = 28.dp)) {
        val stroke = 1.7.dp.toPx()
        val y = if (expanded) 0.58f else 0.42f
        val direction = if (expanded) -1f else 1f
        drawLine(
            color = BalanceGroupingChevron,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * y),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * (y + direction * 0.18f)),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = BalanceGroupingChevron,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * (y + direction * 0.18f)),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.70f, size.height * y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun BalanceGroupingPlusIcon() {
    Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.8.dp.toPx()
            drawCircle(
                color = BalanceGroupingAccent,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            drawLine(
                color = BalanceGroupingAccent,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.28f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.72f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = BalanceGroupingAccent,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.50f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.50f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun BalanceGroupingTrashIcon() {
    Canvas(Modifier.size(27.dp)) {
        val stroke = 1.8.dp.toPx()
        val color = BalanceGroupingAccent
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.30f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.75f, size.height * 0.30f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.20f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.60f, size.height * 0.20f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        val left = size.width * 0.32f
        val right = size.width * 0.68f
        val top = size.height * 0.35f
        val bottom = size.height * 0.82f
        drawLine(color, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left + size.width * 0.05f, bottom), stroke, StrokeCap.Round)
        drawLine(color, androidx.compose.ui.geometry.Offset(right, top), androidx.compose.ui.geometry.Offset(right - size.width * 0.05f, bottom), stroke, StrokeCap.Round)
        drawLine(color, androidx.compose.ui.geometry.Offset(left + size.width * 0.05f, bottom), androidx.compose.ui.geometry.Offset(right - size.width * 0.05f, bottom), stroke, StrokeCap.Round)
    }
}

private fun displayBalanceGroupingMobile(mobile: String, settings: AppSettings): String {
    if (!settings.hideMobileMiddleDigits) return mobile
    val digits = mobile.filter(Char::isDigit)
    return if (digits.length >= 7) {
        "${digits.take(3)} **** ${digits.takeLast(4)}"
    } else {
        mobile
    }
}
