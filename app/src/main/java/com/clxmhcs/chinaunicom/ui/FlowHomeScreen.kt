package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clxmhcs.chinaunicom.R
import com.clxmhcs.chinaunicom.core.design.ChinaUnicomColors
import com.clxmhcs.chinaunicom.core.design.ChinaUnicomDimensions
import com.clxmhcs.chinaunicom.core.design.ChinaUnicomShapes
import com.clxmhcs.chinaunicom.core.model.BalanceRefreshState
import com.clxmhcs.chinaunicom.core.model.DisplayUnit
import com.clxmhcs.chinaunicom.core.model.FlowSummary
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.RefreshState
import com.clxmhcs.chinaunicom.core.model.RemainingFlowCategory
import com.clxmhcs.chinaunicom.core.model.RemainingFlowPackage
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.parser.FlowFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/**
 * UI-01 flow dashboard. The presentation is source-derived from the current iOS DashboardView /
 * AccountCardView while all data, refresh and navigation authority remains in the closed Android
 * ViewModel/repository layer.
 */
@Composable
fun FlowHomeScreen(
    flowViewModel: FlowViewModel,
) {
    val uiState by flowViewModel.uiState.collectAsState()
    var detailAccountID by rememberSaveable { mutableStateOf<String?>(null) }

    when (val state = uiState) {
        FlowUiState.Loading -> DashboardMessage("加载中…")
        is FlowUiState.Error -> DashboardMessage(state.message)
        is FlowUiState.Content -> {
            val detailAccount = detailAccountID?.let { id ->
                state.accounts.firstOrNull { it.id.toString() == id }
            }
            if (detailAccount != null) {
                FlowRemainingDetail(
                    account = detailAccount,
                    accounts = state.accounts,
                    onBack = { detailAccountID = null },
                    onSelectAccount = { detailAccountID = it.id.toString() },
                    onRefreshAccount = { flowViewModel.refreshAccount(detailAccount.id) },
                )
            } else {
                FlowDashboardContent(
                    state = state,
                    onRefreshAll = flowViewModel::refresh,
                    onRefreshBalance = flowViewModel::refreshHomeBalanceManually,
                    onRefreshAccount = flowViewModel::refreshAccount,
                    onOpenAccount = { detailAccountID = it.id.toString() },
                )
            }
        }
    }
}

private data class FlowAccountCardTheme(
    val accent: Color,
    val softAccent: Color,
)

private fun flowAccountCardTheme(index: Int): FlowAccountCardTheme = when (index % 4) {
    1 -> FlowAccountCardTheme(Color(0xFF29AD6B), Color(0xFFD1F5DE))
    2 -> FlowAccountCardTheme(Color(0xFFFAC714), Color(0xFFFFFAB8))
    3 -> FlowAccountCardTheme(Color(0xFF8F63F2), Color(0xFFE8DBFF))
    else -> FlowAccountCardTheme(Color(0xFFFF8F1F), Color(0xFFFFE0B8))
}

@Composable
private fun FlowDashboardContent(
    state: FlowUiState.Content,
    onRefreshAll: () -> Unit,
    onRefreshBalance: () -> Unit,
    onRefreshAccount: (java.util.UUID) -> Unit,
    onOpenAccount: (UnicomAccount) -> Unit,
) {
    val latest = state.accounts.mapNotNull { it.lastUpdatedAt }.maxOrNull()
    val settings = LocalAppSettings.current
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        ChinaUnicomColors.FlowDashboardTop,
                        background,
                        background,
                    ),
                    start = Offset.Zero,
                    end = Offset(1000f, 1500f),
                ),
            ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ChinaUnicomDimensions.PageHorizontal,
                top = ChinaUnicomDimensions.PageTop,
                end = ChinaUnicomDimensions.PageHorizontal,
                bottom = ChinaUnicomDimensions.PageBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                FlowDashboardHeader(
                    latest = latest,
                    homeBalance = state.homeBalanceAccount?.balanceYuan?.takeIf { it.isFinite() },
                    balanceState = state.balanceState.balanceRefreshState,
                    isRefreshingAll = state.appState.isRefreshingAll,
                    hasAccounts = state.accounts.isNotEmpty(),
                    onRefreshAll = onRefreshAll,
                    onRefreshBalance = onRefreshBalance,
                )
            }

            if (state.accounts.isEmpty()) {
                item { FlowEmptyState() }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        state.accounts.forEachIndexed { index, account ->
                            FlowAccountCard(
                                account = account,
                                refreshState = state.appState.refreshState(account.id),
                                displayUnit = settings.displayUnit,
                                hideMobileMiddleDigits = settings.hideMobileMiddleDigits,
                                theme = flowAccountCardTheme(index),
                                onRefresh = { onRefreshAccount(account.id) },
                                onOpen = { onOpenAccount(account) },
                            )
                        }
                    }
                }
                item {
                    Text(
                        "共 ${state.accounts.size} 个号码",
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 12.86.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowDashboardHeader(
    latest: Instant?,
    homeBalance: Double?,
    balanceState: BalanceRefreshState,
    isRefreshingAll: Boolean,
    hasAccounts: Boolean,
    onRefreshAll: () -> Unit,
    onRefreshBalance: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "流量",
                    fontSize = 30.86.sp,
                    lineHeight = 43.sp,
                    fontWeight = FontWeight(467),
                )
                FlowBalancePill(
                    balance = homeBalance,
                    state = balanceState,
                    onClick = onRefreshBalance,
                )
            }

            FlowRefreshAllPill(
                loading = isRefreshingAll,
                enabled = hasAccounts && !isRefreshingAll,
                onClick = onRefreshAll,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "流量数据刷新时，会同步刷新语音相关数据。",
                modifier = Modifier.weight(1f),
                fontSize = 10.29.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                maxLines = 2,
            )
            Text(
                latest?.let { "已更新：${formatTime(it)}" } ?: "尚未更新",
                fontSize = 10.29.sp,
                lineHeight = 16.sp,
                color = Color(0xFF29AD6B),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FlowBalancePill(
    balance: Double?,
    state: BalanceRefreshState,
    onClick: () -> Unit,
) {
    val pillBrush = when (state) {
        BalanceRefreshState.LOADING -> Brush.linearGradient(
            listOf(
                Color(0x85FF4057),
                Color(0x85FF8F38),
                Color(0x855FC77F),
                Color(0x8533A8FA),
                Color(0x85756BF9),
            ),
        )
        BalanceRefreshState.FAILED -> Brush.horizontalGradient(
            listOf(Color.Red.copy(alpha = 0.10f), Color(0xFF29AD6B).copy(alpha = 0.12f)),
        )
        BalanceRefreshState.IDLE -> Brush.horizontalGradient(
            listOf(
                Color(0x6BEDE0FF),
                Color(0x57EDE0FF),
                Color(0x4DE6F7E0),
                Color(0x66E6F7E0),
            ),
        )
    }
    val text = balance?.let { "[余额：${String.format(Locale.US, "%.2f", it)}元]" } ?: "[余额：--]"

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(pillBrush)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text,
            fontSize = 15.43.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
            color = Color.Red,
            maxLines = 1,
        )
        Text(
            "›",
            fontSize = 15.43.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight(467),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun FlowRefreshAllPill(
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .height(ChinaUnicomDimensions.CompactActionHeight)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        shape = CircleShape,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    "↻",
                    fontSize = 15.43.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                if (loading) "更新中" else "刷新全部",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled || loading) 1f else 0.42f),
            )
        }
    }
}

@Composable
private fun FlowAccountCard(
    account: UnicomAccount,
    refreshState: RefreshState,
    displayUnit: DisplayUnit,
    hideMobileMiddleDigits: Boolean,
    theme: FlowAccountCardTheme,
    onRefresh: () -> Unit,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(ChinaUnicomShapes.AccountCardRadius)
    val formatter = remember(displayUnit) { FlowFormatter(displayUnit) }
    val summaries = account.visibleSummaryGroups.map(account::summary)
    val selectedUsage = remember(account, displayUnit) { selectedUsageText(account, formatter) }
    val mobileText = if (hideMobileMiddleDigits) maskMobile(account.mobile) else account.mobile
    val attribution = account.displayName.trim().takeIf {
        it.isNotEmpty() && it != account.mobile && it != account.packageName
    }
    val cardSurface = MaterialTheme.colorScheme.surface
    val lastErrorMessage = account.lastErrorMessage

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 15.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.05f),
            )
            .clip(shape)
            .background(cardSurface)
            .drawWithCache {
                val gradient = Brush.linearGradient(
                    colors = listOf(
                        theme.softAccent.copy(alpha = 0.68f),
                        theme.softAccent.copy(alpha = 0.32f),
                        Color.Transparent,
                    ),
                    start = Offset(size.width, 0f),
                    end = Offset(size.width * 0.50f, size.height * 0.50f),
                )
                onDrawBehind { drawRect(gradient) }
            }
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), shape)
            .clickable(onClick = onOpen),
    ) {
        Image(
            painter = painterResource(R.drawable.china_unicom_knot_watermark),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.48f),
            alpha = 0.045f,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ChinaUnicomDimensions.AccountCardHorizontal,
                    top = ChinaUnicomDimensions.AccountCardTop,
                    end = ChinaUnicomDimensions.AccountCardHorizontal,
                    bottom = ChinaUnicomDimensions.AccountCardBottom,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.china_unicom_knot_watermark),
                    contentDescription = "刷新此号码",
                    colorFilter = ColorFilter.tint(theme.accent),
                    modifier = Modifier
                        .size(17.dp)
                        .clickable(onClick = onRefresh),
                )

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        mobileText,
                        fontSize = 17.14.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                    )
                    attribution?.let {
                        Text(
                            it,
                            fontSize = 13.7.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            maxLines = 1,
                        )
                    }
                }

                when (refreshState) {
                    is RefreshState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = theme.accent,
                    )
                    is RefreshState.Failed -> Text(
                        "更新失败",
                        fontSize = 12.sp,
                        color = Color.Red,
                        maxLines = 1,
                    )
                    else -> account.lastUpdatedAt?.let {
                        Text(
                            formatTime(it),
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            maxLines = 1,
                        )
                    }
                }

                Text(
                    "›",
                    fontSize = 20.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                )
            }

            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.5.dp),
            ) {
                Text(
                    account.packageName.ifBlank { "联通套餐" },
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = 1,
                )
                selectedUsage?.let {
                    Text(
                        it,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFFFF8F1F),
                        maxLines = 1,
                    )
                }
            }

            when {
                summaries.isNotEmpty() -> {
                    Column(
                        modifier = Modifier.padding(top = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(17.dp),
                    ) {
                        summaries.forEach { summary ->
                            FlowSummaryUsageRow(summary = summary, formatter = formatter)
                        }
                    }
                }
                refreshState is RefreshState.Loading -> {
                    FlowLoadingPlaceholder(modifier = Modifier.padding(top = 16.dp))
                }
                else -> {
                    FlowCardEmptyContent(
                        account = account,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }

            if (refreshState is RefreshState.Failed && lastErrorMessage?.isNotBlank() == true) {
                Text(
                    lastErrorMessage,
                    modifier = Modifier.padding(top = 14.dp),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun selectedUsageText(account: UnicomAccount, formatter: FlowFormatter): String? {
    val selectedIDs = account.visibleSummaryGroups.flatMap { it.packageKeys }.toSet()
    val selected = account.visibleDetailPackages.filter { it.id in selectedIDs }
    if (selected.isEmpty()) return null

    val usedMB = selected.sumOf { it.safeUsedMB }
    val totalText = if (selected.any { account.quotaType(it) == QuotaType.UNLIMITED }) {
        "不限量"
    } else {
        val totals = selected.mapNotNull { packageValue ->
            val packageUsedMB = packageValue.usedMB
            val packageRemainingMB = packageValue.remainingMB
            packageValue.totalMB?.takeIf { it > 0 }
                ?: if (packageUsedMB != null && packageRemainingMB != null) {
                    packageUsedMB.coerceAtLeast(0.0) + packageRemainingMB.coerceAtLeast(0.0)
                } else null
        }
        formatter.string(totals.takeIf { it.isNotEmpty() }?.sum())
    }
    return "［ 已用：${formatter.string(usedMB)} ，总流量：$totalText ］"
}

@Composable
private fun FlowSummaryUsageRow(
    summary: FlowSummary,
    formatter: FlowFormatter,
) {
    val blue = Color(0xFF2673FA)
    val tint = if (summary.isExhausted) Color.Red else blue
    val fraction = if (summary.isUnlimited) {
        val safeUsed = summary.usedMB.coerceAtLeast(0.0)
        val step = 100.0 * 1024.0
        val displayedTotal = ceil(safeUsed / step).coerceAtLeast(1.0) * step
        (safeUsed / displayedTotal).coerceIn(0.0, 1.0)
    } else {
        summary.usedFraction ?: 0.0
    }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${summary.name}：",
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
            )
            Text(
                "已用 ${formatter.string(summary.usedMB)}",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (summary.isUnlimited) {
                    "不限量"
                } else {
                    "剩余 ${formatter.string(summary.remainingMB)}/共 ${formatter.string(summary.totalMB)}"
                },
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 1,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.13f)),
        ) {
            if (fraction > 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.toFloat())
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    tint.copy(alpha = if (summary.isUnlimited) 0.72f else 0.82f),
                                    tint,
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun FlowLoadingPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .width(130.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(9.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                )
            }
        }
    }
}

@Composable
private fun FlowCardEmptyContent(account: UnicomAccount, modifier: Modifier = Modifier) {
    val notSubscribed = account.packages.isEmpty() && account.quotaResourceStatus?.name == "NOT_SUBSCRIBED"
    val title = when {
        notSubscribed -> "此号码未订购流量包，套餐内也未包含流量。"
        account.packages.isEmpty() -> "尚未获取余量"
        else -> "尚未配置首页流量分类"
    }
    val message = when {
        notSubscribed -> null
        account.packages.isEmpty() -> account.lastErrorMessage ?: "刷新号码后获取联通流量包。"
        else -> "进入号码详情的“显示设置”，新建分类并勾选要汇总的流量包。"
    }

    Column(modifier = modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
        message?.let {
            Text(
                it,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun FlowEmptyState() {
    val shape = RoundedCornerShape(ChinaUnicomShapes.EmptyCardRadius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f), shape)
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(ChinaUnicomDimensions.EmptyStateIcon)
                .clip(RoundedCornerShape(ChinaUnicomShapes.EmptyIconRadius))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("SIM", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight(467))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("还没有联通号码", fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Normal)
            Text(
                "手动填写手机号和已有 Cookie。凭据只保存在本机安全存储中。",
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
            )
        }
        Text(
            "请在“设置 > 数据与安全 > 凭据信息新增 / 编辑”中新增号码。",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
            textAlign = TextAlign.Center,
        )
    }
}

private fun maskMobile(value: String): String {
    val digits = value.filter(Char::isDigit)
    if (digits.length < 7) return value
    return digits.replaceRange(3, 7, "****")
}

@Composable
private fun FlowRemainingDetail(
    account: UnicomAccount,
    accounts: List<UnicomAccount>,
    onBack: () -> Unit,
    onSelectAccount: (UnicomAccount) -> Unit,
    onRefreshAccount: () -> Unit,
) {
    // RemainingQueryView.swift is a cached-snapshot presentation and does not own refresh or
    // account-switching authority. Keep the legacy signature so UI-01 call sites stay unchanged,
    // but delegate UI-02 presentation to the iOS-parity screen.
    @Suppress("UNUSED_VARIABLE")
    val legacyAuthority = Triple(accounts, onSelectAccount, onRefreshAccount)
    IosRemainingQueryScreen(account = account, onBack = onBack)
}

@Composable
internal fun AccountSelector(
    accounts: List<UnicomAccount>,
    selected: UnicomAccount,
    onSelect: (UnicomAccount) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("当前号码：${selected.mobile}　切换")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.mobile) },
                    onClick = {
                        expanded = false
                        onSelect(account)
                    },
                )
            }
        }
    }
}

@Composable
private fun RemainingFlowCategorySection(
    category: RemainingFlowCategory,
    summaryRemainingMB: Double?,
    summaryUsedMB: Double?,
    packages: List<RemainingFlowPackage>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val formatter = remember { FlowFormatter(DisplayUnit.AUTOMATIC) }
    val visible = if (isExpanded) packages else packages.take(2)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(categoryTitle(category), modifier = Modifier.weight(1f), fontWeight = FontWeight.Normal)
                summaryRemainingMB?.let { Text("剩余 ${formatter.string(it)}", style = MaterialTheme.typography.bodySmall) }
            }
            summaryUsedMB?.let { Text("已用 ${formatter.string(it)}", style = MaterialTheme.typography.labelSmall) }
            if (visible.isEmpty()) {
                Text("暂无该类型套餐", style = MaterialTheme.typography.bodySmall)
            } else {
                visible.forEach { packageValue ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(packageValue.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (packageValue.resolvedIsUnlimited) "不限量" else "剩余 ${formatter.string(packageValue.remainingMB)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            listOfNotNull(
                                if (packageValue.isShared) "共享" else "非共享",
                                packageValue.endDateText?.takeIf { it.isNotBlank() }?.let { "有效期 $it" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            if (packages.size > 2) {
                TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isExpanded) "收起" else "查看更多（${packages.size}）")
                }
            }
        }
    }
}

@Composable
private fun VoiceSnapshotSummary(account: UnicomAccount) {
    val snapshot = account.remainingQuerySnapshot ?: return
    val voice = snapshot.voice
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("语音余量", fontWeight = FontWeight.Normal)
            Text("剩余 ${formatMinutes(voice.remainingMinutes)} · 已用 ${formatMinutes(voice.usedMinutes)}", style = MaterialTheme.typography.bodySmall)
            (voice.packages + voice.unsharedPackages).distinctBy { it.id }.take(6).forEach { packageValue ->
                Text("${packageValue.name}：剩余 ${formatMinutes(packageValue.remainingMinutes)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DashboardMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun DashboardMessageCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
    ) {
        Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun categoryTitle(category: RemainingFlowCategory): String = when (category) {
    RemainingFlowCategory.GENERAL -> "通用流量"
    RemainingFlowCategory.EXCLUSIVE -> "定向流量"
    RemainingFlowCategory.OTHER -> "其他流量"
    RemainingFlowCategory.UNKNOWN -> "未分类流量"
}

internal fun formatMinutes(value: Double?): String {
    if (value == null || !value.isFinite()) return "--"
    val safe = value.coerceAtLeast(0.0)
    return if (kotlin.math.abs(safe - safe.toLong()) < 0.0001) "${safe.toLong()} 分钟"
    else String.format(Locale.US, "%.2f 分钟", safe)
}

private val dashboardTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun formatTime(value: Instant): String = dashboardTimeFormatter.format(value.atZone(ZoneId.systemDefault()))
