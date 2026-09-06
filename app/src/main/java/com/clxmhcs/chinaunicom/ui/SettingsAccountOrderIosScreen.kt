package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clxmhcs.chinaunicom.R
import com.clxmhcs.chinaunicom.core.model.AppSettings
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.util.UUID
import kotlin.math.roundToInt

private val AccountOrderBackground = Color(0xFFF2F2F7)
private val AccountOrderSecondary = Color(0xFF8E8E93)
private val AccountOrderSeparator = Color(0xFFE5E5EA)
private val AccountOrderHandle = Color(0xFFC7C7CC)
private val AccountOrderUnicomRed = Color(0xFFFF3B30)

/**
 * Visual/source parity for iOS `AccountCardOrderView`.
 *
 * iOS uses `Image("ReceiptSIMCardIcon")` from
 * `Assets.xcassets/ReceiptSIMCardIcon.imageset/ReceiptSIMCardIcon.png`, rendered
 * with `scaledToFit()` in a 24 x 24 pt frame. Android packages a downsampled copy
 * of that exact source artwork as `R.drawable.receipt_sim_card_icon`; the
 * downsampling only removes pixels that cannot be displayed at this UI size.
 *
 * The existing SettingsRootViewModel::moveAccount authority remains unchanged:
 * dragging computes a relative destination and commits once when the gesture ends.
 */
@Composable
internal fun IosAccountOrderRefinedScreen(
    accounts: List<UnicomAccount>,
    settings: AppSettings,
    onMove: (UUID, Int) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AccountOrderBackground),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            AccountOrderHeader(onBack)
            Spacer(Modifier.height(20.dp))
            Text(
                text = "首页卡片顺序",
                color = AccountOrderSecondary,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 18.dp, bottom = 9.dp),
            )
        }

        if (accounts.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(
                        text = "暂无手机账号",
                        color = AccountOrderSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        } else {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column {
                        accounts.forEachIndexed { index, account ->
                            AccountOrderRow(
                                account = account,
                                index = index,
                                lastIndex = accounts.lastIndex,
                                settings = settings,
                                onMove = onMove,
                            )
                            if (index < accounts.lastIndex) {
                                Divider(
                                    modifier = Modifier.padding(start = 54.dp, end = 18.dp),
                                    thickness = 0.7.dp,
                                    color = AccountOrderSeparator,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "按住右侧拖动按钮调整顺序。修改会立即保存，并同步用于首页显示和刷新全部的号码顺序。",
                color = AccountOrderSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 9.dp),
            )
        }
    }
}

@Composable
private fun AccountOrderHeader(onBack: () -> Unit) {
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
                    val stroke = Stroke(width = 2.7.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(
                        color = Color(0xFF1C1C1E),
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.18f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.50f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color(0xFF1C1C1E),
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.50f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.82f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        Text(
            text = "自定义排序",
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
private fun AccountOrderRow(
    account: UnicomAccount,
    index: Int,
    lastIndex: Int,
    settings: AppSettings,
    onMove: (UUID, Int) -> Unit,
) {
    val density = LocalDensity.current
    val rowStepPx = with(density) { 58.dp.toPx() }
    var accumulatedDrag by remember(account.id) { mutableFloatStateOf(0f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .padding(start = 18.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.receipt_sim_card_icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayMobileNumber(account.mobile, settings),
                    color = Color.Black,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Normal,
                )
                Spacer(Modifier.size(5.dp))
                // Formal mobile accounts in this app are China Unicom login accounts.
                Image(
                    painter = painterResource(R.drawable.china_unicom_knot_watermark),
                    contentDescription = "中国联通",
                    colorFilter = ColorFilter.tint(AccountOrderUnicomRed),
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = account.displayName.ifBlank { "归属地未知" },
                color = AccountOrderSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 6.dp, top = 1.dp),
            )
        }

        Canvas(
            modifier = Modifier
                .size(width = 42.dp, height = 44.dp)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> accumulatedDrag += delta },
                    onDragStarted = { accumulatedDrag = 0f },
                    onDragStopped = {
                        val rawSteps = (accumulatedDrag / rowStepPx).roundToInt()
                        val steps = rawSteps.coerceIn(-index, lastIndex - index)
                        accumulatedDrag = 0f
                        if (steps != 0) onMove(account.id, steps)
                    },
                ),
        ) {
            val lineWidth = size.width * 0.46f
            val left = (size.width - lineWidth) / 2f
            val right = left + lineWidth
            val stroke = 1.55.dp.toPx()
            listOf(0.34f, 0.50f, 0.66f).forEach { yFraction ->
                drawLine(
                    color = AccountOrderHandle,
                    start = androidx.compose.ui.geometry.Offset(left, size.height * yFraction),
                    end = androidx.compose.ui.geometry.Offset(right, size.height * yFraction),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
