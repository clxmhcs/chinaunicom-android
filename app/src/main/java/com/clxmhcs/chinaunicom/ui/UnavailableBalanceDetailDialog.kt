package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.clxmhcs.chinaunicom.R
import com.clxmhcs.chinaunicom.core.model.FrozenBalanceItem
import com.clxmhcs.chinaunicom.core.model.UnavailableBalanceDetail
import com.clxmhcs.chinaunicom.core.model.UnavailableLimitItem
import java.math.BigDecimal

private val UnavailableRed = Color(0xFFE81C2D)
private val UnavailableSecondary = Color(0xFF8E8E93)
private val UnavailableDivider = Color(0x1A000000)

/**
 * UI-06 subpage. Mirrors iOS PhoneBillComponents.UnavailableBalanceDetailView.
 * All values come from the existing balance authority stored on UnicomAccount.
 */
@Composable
internal fun UnavailableBalanceDetailDialog(
    detail: UnavailableBalanceDetail,
    displayMobile: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        var activeFrozenInfoItemID by remember { mutableStateOf<String?>(null) }
        val showLimit = shouldShowUnavailableCard(detail.unavailableLimitFee, detail.limitItems.isNotEmpty())
        val showFrozen = shouldShowUnavailableCard(detail.frozenFee, detail.frozenItems.isNotEmpty())

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF52E38),
                            Color(0xFFFF8C94),
                            Color(0xFFFFD6D6),
                        ),
                    ),
                ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                UnavailableBalanceHeader(
                    displayMobile = displayMobile,
                    onDismiss = onDismiss,
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp)
                        .padding(bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (showLimit) {
                        UnavailableLimitCard(detail)
                    }
                    if (showFrozen) {
                        FrozenBalanceCard(
                            detail = detail,
                            activeInfoItemID = activeFrozenInfoItemID,
                            onInfoItemChange = { activeFrozenInfoItemID = it },
                        )
                    }
                    if (!showLimit && !showFrozen) {
                        NoUnavailableBalanceCard()
                    }
                }
            }
        }
    }
}

@Composable
private fun UnavailableBalanceHeader(
    displayMobile: String,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(58.dp)
            .padding(horizontal = 2.5.dp),
    ) {
        Text(
            "‹",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(44.dp)
                .clickable(onClick = onDismiss),
            fontSize = 42.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.Light,
            color = Color.White,
            textAlign = TextAlign.Center,
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "剩余话费",
                fontSize = 14.4.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            if (displayMobile.isNotBlank()) {
                Text(
                    displayMobile,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.86f),
                )
            }
        }

        Text(
            "×",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(44.dp)
                .clickable(onClick = onDismiss),
            fontSize = 38.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.Light,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UnavailableLimitCard(detail: UnavailableBalanceDetail) {
    UnavailableInfoCard(
        title = "未使用定向金额",
        amount = detail.displayUnavailableLimitFee,
    ) {
        if (detail.limitItems.isEmpty()) {
            EmptyUnavailableRows()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                detail.limitItems.forEach { item ->
                    UnavailableLimitItemContent(item)
                }
            }
        }
    }
}

@Composable
private fun UnavailableLimitItemContent(item: UnavailableLimitItem) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        UnavailableBulletRow(
            title = "定向金额名称",
            value = item.depositName ?: "暂无",
        )
        UnavailablePlainRow("用户号码", item.belongSerialNumber ?: "暂无")
        UnavailablePlainRow("未用金额", item.unavailableLimitFee ?: "0.00")
        UnavailablePlainRow("失效时间", item.endCycle ?: "暂无")
    }
}

@Composable
private fun FrozenBalanceCard(
    detail: UnavailableBalanceDetail,
    activeInfoItemID: String?,
    onInfoItemChange: (String?) -> Unit,
) {
    UnavailableInfoCard(
        title = "账户未返金额",
        amount = detail.displayFrozenFee,
    ) {
        if (detail.frozenItems.isEmpty()) {
            EmptyUnavailableRows()
        } else {
            Column {
                detail.frozenItems.forEachIndexed { index, item ->
                    FrozenBalanceItemContent(
                        item = item,
                        infoPresented = activeInfoItemID == item.id,
                        onInfoClick = {
                            onInfoItemChange(if (activeInfoItemID == item.id) null else item.id)
                        },
                        onInfoDismiss = { onInfoItemChange(null) },
                    )
                    if (index < detail.frozenItems.lastIndex) {
                        Divider(
                            modifier = Modifier.padding(vertical = 14.dp),
                            color = UnavailableDivider,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FrozenBalanceItemContent(
    item: FrozenBalanceItem,
    infoPresented: Boolean,
    onInfoClick: () -> Unit,
    onInfoDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        UnavailableBulletRow(
            title = "活动名称",
            value = item.actionName ?: "暂无",
            infoItem = item,
            infoPresented = infoPresented,
            onInfoClick = onInfoClick,
            onInfoDismiss = onInfoDismiss,
        )
        UnavailablePlainRow("活动对应号码", item.serialNumber ?: "暂无")
        UnavailablePlainRow("总金额", item.actionMoney ?: "0.00")
        UnavailablePlainRow("已返金额", item.usedMoney ?: "0.00")
        UnavailablePlainRow("未返金额", item.leftMoney ?: "0.00")
    }
}

@Composable
private fun UnavailableInfoCard(
    title: String,
    amount: String,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White),
    ) {
        Image(
            painter = painterResource(R.drawable.china_unicom_knot_watermark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(UnavailableRed),
            alpha = 0.06f,
            modifier = Modifier
                .size(84.dp)
                .align(Alignment.TopStart)
                .padding(start = 0.dp, top = 0.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            UnavailableRed.copy(alpha = 0.045f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    title,
                    fontSize = 16.3.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "¥",
                        fontSize = 11.5.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UnavailableRed,
                    )
                    Text(
                        amount,
                        fontSize = 14.4.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UnavailableRed,
                    )
                    Spacer(Modifier.width(18.dp))
                }
            }
            content()
        }
    }
}

@Composable
private fun UnavailableBulletRow(
    title: String,
    value: String,
    infoItem: FrozenBalanceItem? = null,
    infoPresented: Boolean = false,
    onInfoClick: (() -> Unit)? = null,
    onInfoDismiss: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(5.dp)
                .background(Color.Transparent, CircleShape)
                .then(
                    Modifier.background(Color.Transparent, CircleShape),
                ),
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = UnavailableRed,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4.dp.toPx()),
                )
            }
        }

        Text(
            title,
            modifier = Modifier.width(128.dp),
            fontSize = 14.4.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
        )

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.weight(1.25f),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                value,
                modifier = Modifier.weight(1f),
                fontSize = 14.4.sp,
                lineHeight = 19.sp,
                color = Color.Black,
                textAlign = TextAlign.End,
            )
            if (infoItem != null) {
                FrozenBalanceInfoButton(
                    item = infoItem,
                    expanded = infoPresented,
                    onClick = onInfoClick ?: {},
                    onDismiss = onInfoDismiss ?: {},
                )
            } else {
                Spacer(Modifier.width(14.dp))
            }
        }
    }
}

@Composable
private fun FrozenBalanceInfoButton(
    item: FrozenBalanceItem,
    expanded: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(0xFFC7C7CC))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "!",
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, 18),
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true),
            ) {
                FrozenBalanceInfoPopover(item)
            }
        }
    }
}

@Composable
private fun FrozenBalanceInfoPopover(item: FrozenBalanceItem) {
    Surface(
        modifier = Modifier
            .width(236.dp)
            .shadow(10.dp, RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UnavailableInfoPopoverRow("活动名称", item.actionName ?: "暂无")
            UnavailableInfoPopoverRow("办理渠道", item.actionDepart ?: "暂无")
            UnavailableInfoPopoverRow("生失效时间", unavailableCycleText(item))
        }
    }
}

@Composable
private fun UnavailableInfoPopoverRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            modifier = Modifier.width(54.dp),
            fontSize = 10.8.sp,
            lineHeight = 14.sp,
            color = UnavailableSecondary,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            fontSize = 10.8.sp,
            lineHeight = 14.sp,
            color = Color.Black,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun UnavailablePlainRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.width(5.dp))
        Text(
            title,
            modifier = Modifier.width(128.dp),
            fontSize = 14.4.sp,
            lineHeight = 19.sp,
            color = UnavailableSecondary,
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.weight(1.25f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                value,
                modifier = Modifier.weight(1f),
                fontSize = 14.4.sp,
                lineHeight = 19.sp,
                color = Color.Black,
                textAlign = TextAlign.End,
            )
            Spacer(Modifier.width(14.dp))
        }
    }
}

@Composable
private fun EmptyUnavailableRows() {
    Text(
        "暂无明细",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        fontSize = 14.4.sp,
        color = UnavailableSecondary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun NoUnavailableBalanceCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(horizontal = 18.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "✓",
            fontSize = 28.sp,
            lineHeight = 30.sp,
            color = UnavailableRed.copy(alpha = 0.72f),
        )
        Text(
            "暂无不可用金额",
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
        )
        Text(
            "当前没有未使用定向金额或账户未返金额",
            fontSize = 14.sp,
            lineHeight = 18.sp,
            color = UnavailableSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private fun shouldShowUnavailableCard(amount: String?, hasItems: Boolean): Boolean {
    val normalized = amount?.trim()?.takeIf { it.isNotEmpty() } ?: return hasItems
    return (normalized.replace(",", "").toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO
}

private fun unavailableCycleText(item: FrozenBalanceItem): String {
    val start = formatUnavailableCycle(item.startCycle)
    val end = formatUnavailableCycle(item.endCycle)
    return when {
        start != null && end != null -> "$start-$end"
        start != null -> start
        end != null -> end
        else -> "暂无"
    }
}

private fun formatUnavailableCycle(value: String?): String? = value
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.replace("年", ".")
    ?.replace("月", "")
    ?.replace("-", ".")
