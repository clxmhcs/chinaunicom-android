package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
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
 *
 * iOS uses a 430pt-wide reference layout. Android logical widths are commonly narrower,
 * so this screen applies a small Android visual compensation to the fixed iOS point values.
 * Data, visibility rules and balance authority remain unchanged.
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
                        .padding(horizontal = 8.5.dp)
                        .padding(bottom = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(15.5.dp),
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
            .height(33.dp)
            .padding(horizontal = 2.dp),
    ) {
        Text(
            "‹",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(38.dp)
                .clickable(onClick = onDismiss),
            fontSize = 36.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Light,
            color = Color.White,
            textAlign = TextAlign.Center,
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.5.dp),
        ) {
            Text(
                "剩余话费",
                fontSize = 12.4.sp,
                lineHeight = 15.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            if (displayMobile.isNotBlank()) {
                Text(
                    displayMobile,
                    fontSize = 10.sp,
                    lineHeight = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 1,
                )
            }
        }

        Text(
            "×",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(38.dp)
                .clickable(onClick = onDismiss),
            fontSize = 33.sp,
            lineHeight = 38.sp,
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
            Column(verticalArrangement = Arrangement.spacedBy(15.5.dp)) {
                detail.limitItems.forEach { item ->
                    UnavailableLimitItemContent(item)
                }
            }
        }
    }
}

@Composable
private fun UnavailableLimitItemContent(item: UnavailableLimitItem) {
    Column(verticalArrangement = Arrangement.spacedBy(9.5.dp)) {
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
                            modifier = Modifier.padding(vertical = 12.dp),
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                .size(72.dp)
                .align(Alignment.TopStart)
                .offset(x = (-10).dp, y = (-10).dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(75.dp)
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
            modifier = Modifier.padding(horizontal = 19.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(15.5.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    title,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(13.5.dp),
                ) {
                    Text(
                        "¥",
                        fontSize = 10.sp,
                        lineHeight = 15.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = UnavailableRed,
                    )
                    Text(
                        amount,
                        fontSize = 12.4.sp,
                        lineHeight = 16.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = UnavailableRed,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(15.5.dp))
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
        horizontalArrangement = Arrangement.spacedBy(8.5.dp),
    ) {
        Canvas(
            modifier = Modifier
                .padding(top = 6.5.dp)
                .size(4.5.dp),
        ) {
            drawCircle(
                color = UnavailableRed,
                style = Stroke(width = 1.2.dp.toPx()),
            )
        }

        Text(
            title,
            modifier = Modifier.width(110.dp),
            fontSize = 12.4.sp,
            lineHeight = 16.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
        )

        Spacer(Modifier.width(8.5.dp))

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(3.5.dp),
        ) {
            Text(
                value,
                modifier = Modifier.weight(1f),
                fontSize = 12.4.sp,
                lineHeight = 16.5.sp,
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
                Spacer(Modifier.width(12.dp))
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
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(0xFFC7C7CC))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "!",
                fontSize = 8.5.sp,
                lineHeight = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, 15),
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
            .width(202.dp)
            .shadow(8.5.dp, RoundedCornerShape(5.dp)),
        shape = RoundedCornerShape(5.dp),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.5.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
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
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            title,
            modifier = Modifier.width(46.dp),
            fontSize = 9.3.sp,
            lineHeight = 12.sp,
            color = UnavailableSecondary,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            fontSize = 9.3.sp,
            lineHeight = 12.sp,
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
        horizontalArrangement = Arrangement.spacedBy(8.5.dp),
    ) {
        Spacer(Modifier.width(4.5.dp))
        Text(
            title,
            modifier = Modifier.width(110.dp),
            fontSize = 12.4.sp,
            lineHeight = 16.5.sp,
            color = UnavailableSecondary,
        )
        Spacer(Modifier.width(8.5.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(3.5.dp),
        ) {
            Text(
                value,
                modifier = Modifier.weight(1f),
                fontSize = 12.4.sp,
                lineHeight = 16.5.sp,
                color = Color.Black,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun EmptyUnavailableRows() {
    Text(
        "暂无明细",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        fontSize = 12.4.sp,
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
            .padding(horizontal = 15.5.dp, vertical = 29.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.5.dp),
    ) {
        Text(
            "✓",
            fontSize = 24.sp,
            lineHeight = 26.sp,
            color = UnavailableRed.copy(alpha = 0.72f),
        )
        Text(
            "暂无不可用金额",
            fontSize = 14.5.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
        )
        Text(
            "当前没有未使用定向金额或账户未返金额",
            fontSize = 12.sp,
            lineHeight = 15.5.sp,
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
