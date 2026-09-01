package com.clxmhcs.chinaunicom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clxmhcs.chinaunicom.core.model.BillItemSection
import com.clxmhcs.chinaunicom.core.model.BillMonth
import com.clxmhcs.chinaunicom.core.model.PhoneBillSnapshot
import com.clxmhcs.chinaunicom.core.model.PhoneBillSummary
import com.clxmhcs.chinaunicom.core.model.UnavailableBalanceDetail
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.UserBill
import com.clxmhcs.chinaunicom.data.phonebill.PhoneBillCachePolicy
import com.clxmhcs.chinaunicom.data.phonebill.PhoneBillLoadState
import com.clxmhcs.chinaunicom.data.phonebill.PhoneBillPolicyProvider
import com.clxmhcs.chinaunicom.data.settings.PhoneBillRefreshPolicy
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BillRed = Color(0xFFE81C2D)
private val BillDeepRed = Color(0xFFD11424)
private val BillPageBackground = Color(0xFFF2F2F7)
private val BillSecondary = Color(0xFF8E8E93)
private val BillDivider = Color(0x1A000000)
private val ShanghaiZone: ZoneId = ZoneId.of("Asia/Shanghai")
private val BillTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private enum class BillScope(val title: String) {
    USER("用户"), ACCOUNT("账户")
}

/**
 * UI-06. Presentation is source-derived from iOS PhoneBillView.swift + PhoneBillComponents.swift.
 * PhoneBillStore remains the only billing data/network/cache authority.
 */
@Composable
internal fun IosPhoneBillScreen(
    account: UnicomAccount,
    businessViewModel: ComprehensiveBusinessViewModel,
    onBack: () -> Unit,
) {
    val state by businessViewModel.phoneBillState.collectAsState()
    val policy by businessViewModel.phoneBillRefreshPolicy.collectAsState()
    val settings = LocalAppSettings.current
    var scope by rememberSaveable { mutableStateOf(BillScope.USER.name) }
    var showsUnavailableBalanceDetail by rememberSaveable { mutableStateOf(false) }
    val displayMobile = if (settings.hideMobileMiddleDigits) compactMaskMobile(account.mobile) else account.mobile

    LaunchedEffect(account.id) { businessViewModel.loadPhoneBill(account) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BillPageBackground),
    ) {
        PhoneBillHeader(
            mobile = displayMobile,
            months = state.months,
            selectedMonth = state.requestedMonth ?: state.selectedMonth,
            loading = state.loadState is PhoneBillLoadState.Loading,
            onBack = onBack,
            onRefresh = { businessViewModel.refreshPhoneBill(account) },
            onSelectMonth = { businessViewModel.selectPhoneBillMonth(it, account) },
        )

        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    state.snapshot != null -> {
                        val snapshot = state.snapshot!!
                        PhoneBillContent(
                            snapshot = snapshot,
                            refreshPolicy = policy,
                            selectedScope = BillScope.valueOf(scope),
                            hideMobileMiddleDigits = settings.hideMobileMiddleDigits,
                            onScopeChange = { scope = it.name },
                            onOpenUnavailableBalance = { showsUnavailableBalanceDetail = true },
                        )
                    }
                    state.loadState is PhoneBillLoadState.Failed -> {
                        PhoneBillFailureState(
                            message = (state.loadState as PhoneBillLoadState.Failed).message,
                            onRetry = { businessViewModel.refreshPhoneBill(account) },
                        )
                    }
                    else -> PhoneBillInitialLoadingState()
                }
            }

            if (state.loadState is PhoneBillLoadState.Loading && state.snapshot != null) {
                PhoneBillLoadingOverlay()
            }
        }
    }

    if (showsUnavailableBalanceDetail) {
        UnavailableBalanceDetailDialog(
            detail = account.unavailableBalanceDetail ?: UnavailableBalanceDetail(
                currentBalance = null,
                unavailableLimitFee = null,
                frozenFee = null,
                totalUnavailable = null,
                limitItems = emptyList(),
                frozenItems = emptyList(),
            ),
            displayMobile = displayMobile,
            onDismiss = { showsUnavailableBalanceDetail = false },
        )
    }
}

@Composable
private fun PhoneBillHeader(
    mobile: String,
    months: List<BillMonth>,
    selectedMonth: BillMonth?,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectMonth: (BillMonth) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(BillRed, BillDeepRed),
                    start = Offset.Zero,
                    end = Offset(1000f, 700f),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                "‹",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .clickable(onClick = onBack),
                fontSize = 46.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    "我的账单",
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    mobile,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.86f),
                )
            }

            Text(
                "↻",
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(44.dp)
                    .clickable(enabled = !loading, onClick = onRefresh),
                fontSize = 34.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = if (loading) 0.45f else 1f),
                textAlign = TextAlign.Center,
            )
        }

        PhoneBillMonthSelector(
            months = months,
            selectedMonth = selectedMonth,
            onSelectMonth = onSelectMonth,
        )
    }
}

@Composable
private fun PhoneBillMonthSelector(
    months: List<BillMonth>,
    selectedMonth: BillMonth?,
    onSelectMonth: (BillMonth) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .horizontalScroll(scrollState)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        months.forEach { month ->
            val selected = selectedMonth?.id == month.id
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .clickable { onSelectMonth(month) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    month.title,
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = Color.White.copy(alpha = if (selected) 1f else 0.68f),
                )
                Text(
                    month.subtitle,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = if (selected) 0.90f else 0.56f),
                )
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) Color.White else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun PhoneBillContent(
    snapshot: PhoneBillSnapshot,
    refreshPolicy: PhoneBillRefreshPolicy,
    selectedScope: BillScope,
    hideMobileMiddleDigits: Boolean,
    onScopeChange: (BillScope) -> Unit,
    onOpenUnavailableBalance: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BillDeepRed,
                        BillRed.copy(alpha = 0.76f),
                        BillPageBackground,
                    ),
                    endY = 470f,
                ),
            )
            .padding(top = 0.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BillSummaryCard(
            snapshot = snapshot,
            policy = refreshPolicy,
            modifier = Modifier.padding(horizontal = 7.dp),
            onOpenUnavailableBalance = onOpenUnavailableBalance,
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 7.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            shadowElevation = 0.dp,
        ) {
            Column {
                BillScopePicker(selectedScope = selectedScope, onScopeChange = onScopeChange)
                Divider(color = BillDivider)
                if (selectedScope == BillScope.USER) {
                    UserBillList(
                        userBills = snapshot.userBills,
                        hideMobileMiddleDigits = hideMobileMiddleDigits,
                    )
                } else {
                    AccountBillDetail(
                        sections = snapshot.accountSections,
                        summary = snapshot.summary,
                    )
                }
            }
        }
    }
}

@Composable
private fun BillSummaryCard(
    snapshot: PhoneBillSnapshot,
    policy: PhoneBillRefreshPolicy,
    modifier: Modifier = Modifier,
    onOpenUnavailableBalance: () -> Unit,
) {
    val nextQueryTime = remember(snapshot, policy) {
        PhoneBillCachePolicy(PhoneBillPolicyProvider { policy })
            .nextQueryTime(snapshot, Instant.now())
    }
    val queryTime = snapshot.queryTime?.takeIf { it.isNotBlank() } ?: billTimestamp(snapshot.fetchedAt)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(alpha = 0.08f))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BillRed.copy(alpha = 0.16f),
                            Color(0xFFFFEFE8).copy(alpha = 0.82f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(
                modifier = Modifier.padding(top = 0.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    "上次查询时间： $queryTime",
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    color = BillSecondary,
                )
                Text(
                    "下次查询时间： ${nextQueryTime?.let(::billTimestamp) ?: "未知"}",
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    color = BillSecondary,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "本月已消费（元）",
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "¥",
                        fontSize = 22.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                    )
                    Text(
                        snapshot.summary.realPayFee,
                        fontSize = 33.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 10.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenUnavailableBalance)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "不可用金额",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = BillSecondary,
            )
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(BillSecondary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "?",
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BillSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BillScopePicker(
    selectedScope: BillScope,
    onScopeChange: (BillScope) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        BillScope.entries.forEach { scope ->
            val selected = selectedScope == scope
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onScopeChange(scope) }
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    scope.title,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Color.Black else BillSecondary,
                )
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) BillRed else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun UserBillList(
    userBills: List<UserBill>,
    hideMobileMiddleDigits: Boolean,
) {
    var expandedIDs by remember { mutableStateOf(setOf<String>()) }

    if (userBills.isEmpty()) {
        Text(
            "暂无用户账单明细",
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
            fontSize = 14.sp,
            color = BillSecondary,
            textAlign = TextAlign.Center,
        )
        return
    }

    Column {
        userBills.forEachIndexed { index, userBill ->
            val expanded = userBill.id in expandedIDs
            UserBillRow(
                userBill = userBill,
                expanded = expanded,
                hideMobileMiddleDigits = hideMobileMiddleDigits,
                onClick = {
                    expandedIDs = if (expanded) expandedIDs - userBill.id else expandedIDs + userBill.id
                },
            )
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    BillDetailTable(userBill.sections)
                    BillTotalCard(BillTotalValues.fromUserBill(userBill))
                }
            }
            if (index < userBills.lastIndex) Divider(color = BillDivider)
        }
    }
}

@Composable
private fun UserBillRow(
    userBill: UserBill,
    expanded: Boolean,
    hideMobileMiddleDigits: Boolean,
    onClick: () -> Unit,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180),
        label = "billChevron",
    )
    val displayMobile = if (hideMobileMiddleDigits) compactMaskMobile(userBill.mobile) else userBill.mobile.replace(" ", "")
    val badge = billAccountBadge(userBill)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserOutlineIcon()

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                displayMobile,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                maxLines = 1,
            )
            badge?.let { BillAccountBadgeView(it) }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "当月应付：",
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = BillSecondary,
                maxLines = 1,
            )
            Text(
                userBill.payable,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = BillRed,
                maxLines = 1,
            )
            Text(
                "⌄",
                modifier = Modifier.rotate(arrowRotation),
                fontSize = 20.sp,
                lineHeight = 20.sp,
                color = Color(0xFFC7C7CC),
            )
        }
    }
}

@Composable
private fun UserOutlineIcon() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = Stroke(width = 2.0.dp.toPx())
        drawCircle(
            color = BillRed,
            radius = 4.8.dp.toPx(),
            center = Offset(size.width / 2f, 6.6.dp.toPx()),
            style = stroke,
        )
        drawArc(
            color = BillRed,
            startAngle = 195f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(2.0.dp.toPx(), 11.5.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(20.dp.toPx(), 12.dp.toPx()),
            style = stroke,
        )
        drawLine(
            color = BillRed,
            start = Offset(3.2.dp.toPx(), 21.5.dp.toPx()),
            end = Offset(20.8.dp.toPx(), 21.5.dp.toPx()),
            strokeWidth = 2.0.dp.toPx(),
        )
    }
}

private enum class BillAccountBadge(val title: String, val foreground: Color) {
    BROADBAND("宽", Color(0xFF4789D1)),
    VIRTUAL("虚", Color(0xFF8F5CD1)),
}

private fun billAccountBadge(userBill: UserBill): BillAccountBadge? {
    val compact = userBill.mobile.replace(" ", "")
    if (!compact.startsWith("0")) return null
    if (userBill.virtualUserTag?.trim() == "1") return BillAccountBadge.VIRTUAL
    return when (compact.count { it == '*' }) {
        10 -> BillAccountBadge.VIRTUAL
        5 -> BillAccountBadge.BROADBAND
        else -> null
    }
}

@Composable
private fun BillAccountBadgeView(badge: BillAccountBadge) {
    Text(
        badge.title,
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(badge.foreground.copy(alpha = 0.08f))
            .padding(horizontal = 2.dp, vertical = 0.dp),
        fontSize = 9.4.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = badge.foreground,
    )
}

@Composable
private fun AccountBillDetail(
    sections: List<BillItemSection>,
    summary: PhoneBillSummary,
) {
    Column(
        modifier = Modifier.padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        BillDetailTable(sections)
        BillTotalCard(BillTotalValues.fromSummary(summary))
    }
}

@Composable
private fun BillDetailTable(sections: List<BillItemSection>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "消费明细",
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BillTableText("费用项", Modifier.weight(1.5f), FontWeight.SemiBold, Color.Black, TextAlign.Start)
            BillTableText("原价", Modifier.weight(0.7f), FontWeight.SemiBold, Color.Black, TextAlign.End)
            BillTableText("优惠", Modifier.weight(0.8f), FontWeight.SemiBold, Color.Black, TextAlign.End)
            BillTableText("实际消费", Modifier.weight(0.9f), FontWeight.SemiBold, Color.Black, TextAlign.End)
        }
        Divider(color = BillDivider)

        sections.forEach { section ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(BillRed),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    section.title,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            section.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BillTableText(item.name, Modifier.weight(1.5f), FontWeight.Normal, BillSecondary, TextAlign.Start)
                    BillTableText(item.originalFee, Modifier.weight(0.7f), FontWeight.Normal, BillSecondary, TextAlign.End)
                    BillTableText(item.discount, Modifier.weight(0.8f), FontWeight.Normal, BillRed, TextAlign.End)
                    BillTableText(item.realFee, Modifier.weight(0.9f), FontWeight.SemiBold, Color.Black, TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun BillTableText(
    text: String,
    modifier: Modifier,
    weight: FontWeight,
    color: Color,
    align: TextAlign,
) {
    Text(
        text,
        modifier = modifier,
        fontSize = 14.sp,
        lineHeight = 17.sp,
        fontWeight = weight,
        color = color,
        textAlign = align,
        overflow = TextOverflow.Ellipsis,
        maxLines = 2,
    )
}

private data class BillTotalValues(
    val totalPrice: String,
    val totalDiscount: String,
    val totalRealFee: String,
    val totalAdjustAfter: String?,
    val totalAcctDiscnt: String?,
    val totalLateFee: String?,
    val allRebates: String?,
    val actualPayable: String,
) {
    companion object {
        fun fromSummary(value: PhoneBillSummary) = BillTotalValues(
            totalPrice = value.totalPrice,
            totalDiscount = value.totalDiscount,
            totalRealFee = value.totalRealFee,
            totalAdjustAfter = value.totalAdjustAfter,
            totalAcctDiscnt = value.totalAcctDiscnt,
            totalLateFee = value.totalLateFee,
            allRebates = value.allRebates ?: money(decimal(value.realPayFee) - decimal(value.totalRealFee)),
            actualPayable = value.realPayFeeP ?: value.realPayFee,
        )

        fun fromUserBill(value: UserBill) = BillTotalValues(
            totalPrice = value.totalPrice ?: money(value.allItems.sumOf { decimal(it.originalFee) }),
            totalDiscount = value.totalDiscount ?: money(value.allItems.sumOf { decimal(it.discount) }),
            totalRealFee = value.totalRealFee ?: money(value.allItems.sumOf { decimal(it.realFee) }),
            totalAdjustAfter = value.totalAdjustAfter,
            totalAcctDiscnt = value.totalAcctDiscnt,
            totalLateFee = value.totalLateFee,
            allRebates = value.allRebates,
            actualPayable = value.realPayFeeP ?: value.payable,
        )

        private fun decimal(value: String): BigDecimal = value.replace(",", "").toBigDecimalOrNull() ?: BigDecimal.ZERO
        private fun money(value: BigDecimal): String = value.setScale(2, RoundingMode.HALF_UP).toPlainString()
    }
}

@Composable
private fun BillTotalCard(values: BillTotalValues) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BillRed.copy(alpha = 0.035f))
            .padding(horizontal = 8.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BillTotalRow("合计:", values.totalRealFee, values.totalDiscount, values.totalPrice)
        values.totalAcctDiscnt?.takeUnless(::isZeroMoney)?.let { BillTotalSingleRow("账户优惠", it) }
        values.allRebates?.takeUnless(::isZeroMoney)?.let { BillTotalSingleRow("返赠合计", it) }
        values.totalAdjustAfter?.takeUnless(::isZeroMoney)?.let { BillTotalSingleRow("调增减项", it) }
        values.totalLateFee?.takeUnless(::isZeroMoney)?.let { BillTotalSingleRow("违约金", it) }
        BillTotalSingleRow("实际应付", values.actualPayable, bold = true, red = false)
    }
}

@Composable
private fun BillTotalRow(title: String, actual: String, discount: String, original: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(original, modifier = Modifier.weight(0.8f), fontSize = 15.sp, textAlign = TextAlign.End)
        Text(discount, modifier = Modifier.weight(0.8f), fontSize = 15.sp, color = BillRed, textAlign = TextAlign.End)
        Text(actual, modifier = Modifier.weight(0.9f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun BillTotalSingleRow(title: String, value: String, bold: Boolean = false, red: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f))
        Text(
            title,
            modifier = Modifier.weight(1.5f),
            fontSize = 15.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
        Text(
            value,
            modifier = Modifier.weight(0.9f),
            fontSize = 15.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = if (red) BillRed else Color.Black,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun PhoneBillInitialLoadingState() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(7.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(color = BillRed, strokeWidth = 2.dp)
            Text("正在查询账单", fontSize = 14.sp, color = BillSecondary)
        }
    }
}

@Composable
private fun PhoneBillFailureState(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(7.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("账单查询失败", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(message, fontSize = 13.sp, color = BillSecondary, textAlign = TextAlign.Center)
            Text(
                "重新刷新",
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(BillRed)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                fontSize = 14.sp,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun PhoneBillLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(58.dp),
            color = BillRed,
            strokeWidth = 5.dp,
        )
    }
}

private fun compactMaskMobile(value: String): String {
    val compact = value.replace(" ", "")
    if (compact.count { it.isDigit() } < 7 || '*' in compact) return compact
    return if (compact.length >= 7) compact.replaceRange(3, 7, "****") else compact
}

private fun billTimestamp(value: Instant): String = BillTimestampFormatter.format(value.atZone(ShanghaiZone))

private fun isZeroMoney(value: String): Boolean =
    value.replace(",", "").toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) == 0
