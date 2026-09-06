package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.clxmhcs.chinaunicom.R
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.util.UUID

private val SingleWidgetBackground = Color(0xFFF2F2F7)
private val SingleWidgetSecondary = Color(0xFF8E8E93)
private val SingleWidgetSeparator = Color(0xFFE5E5EA)
private val SingleWidgetTertiary = Color(0xFFC7C7CC)
private val SingleWidgetAccent = Color(0xFF3478F6)
private val SingleWidgetUnicomRed = Color(0xFFFF3B30)

/** Visual/source parity for iOS `WidgetInformationSettingsView`. */
@Composable
internal fun IosSingleWidgetSettingsScreen(
    accounts: List<UnicomAccount>,
    viewModel: SettingsM11CViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.widgetState.collectAsState()
    val configuration = state.single
    val settings = LocalAppSettings.current
    val selected = configuration.selectedAccountID?.let { id -> accounts.firstOrNull { it.id == id } }
    var showAccountPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SingleWidgetBackground),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 110.dp),
    ) {
        item {
            SingleWidgetHeader(onBack)
            Spacer(Modifier.height(20.dp))
            Text(
                text = "显示内容",
                color = SingleWidgetSecondary,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 18.dp, bottom = 9.dp),
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(24.dp),
            ) {
                Column {
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable { showAccountPicker = true }
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "组件号码",
                                color = Color.Black,
                                fontSize = 17.sp,
                                lineHeight = 21.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = selected?.let { displayMobileNumber(it.mobile, settings) }
                                    ?: "自动选择首个卡片号码",
                                color = SingleWidgetSecondary,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(8.dp))
                            SingleWidgetChevronDown(expanded = showAccountPicker)
                        }

                        if (showAccountPicker) {
                            SingleWidgetAccountPopup(
                                accounts = accounts,
                                selectedAccountID = configuration.selectedAccountID,
                                hideMobile = settings.hideMobileMiddleDigits,
                                locationFor = viewModel::cachedLocation,
                                onSelect = { id ->
                                    showAccountPicker = false
                                    viewModel.saveSingleWidget(configuration.copy(selectedAccountID = id))
                                },
                                onDismiss = { showAccountPicker = false },
                            )
                        }
                    }

                    Divider(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        thickness = 0.7.dp,
                        color = SingleWidgetSeparator,
                    )
                    SingleWidgetSwitchRow(
                        label = "显示今日用量",
                        checked = configuration.showsTodayUsage,
                        onCheckedChange = {
                            viewModel.saveSingleWidget(configuration.copy(showsTodayUsage = it))
                        },
                    )
                    Divider(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        thickness = 0.7.dp,
                        color = SingleWidgetSeparator,
                    )
                    SingleWidgetSwitchRow(
                        label = "显示话费余额",
                        checked = configuration.showsBalance,
                        onCheckedChange = {
                            viewModel.saveSingleWidget(configuration.copy(showsBalance = it))
                        },
                    )
                }
            }
        }

        item {
            Text(
                text = "这里控制中号组件顶部信息和刷新时使用的号码。",
                color = SingleWidgetSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 9.dp),
            )
        }
    }
}

@Composable
private fun SingleWidgetHeader(onBack: () -> Unit) {
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
            text = "组件信息编辑",
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
private fun SingleWidgetSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.Black,
            fontSize = 17.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.92f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SingleWidgetAccent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE9E9EA),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun SingleWidgetChevronDown(expanded: Boolean) {
    Canvas(
        modifier = Modifier
            .size(18.dp)
            .rotate(if (expanded) 180f else 0f),
    ) {
        val stroke = 1.7.dp.toPx()
        drawLine(
            color = SingleWidgetTertiary,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.40f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.62f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = SingleWidgetTertiary,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.62f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.40f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun SingleWidgetAccountPopup(
    accounts: List<UnicomAccount>,
    selectedAccountID: UUID?,
    hideMobile: Boolean,
    locationFor: (String) -> String?,
    onSelect: (UUID?) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, with(density) { 46.dp.roundToPx() }),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(modifier = Modifier.widthIn(min = 252.dp, max = 326.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp),
                color = Color(0xFFF9F9FB),
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 10.dp,
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SingleWidgetPopupRow(
                        selected = selectedAccountID == null,
                        onClick = { onSelect(null) },
                    ) {
                        Text(
                            text = "自动选择首个卡片号码",
                            color = Color.Black,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            maxLines = 1,
                        )
                    }
                    accounts.forEach { account ->
                        Divider(
                            modifier = Modifier.padding(start = 44.dp),
                            thickness = 0.7.dp,
                            color = SingleWidgetSeparator,
                        )
                        SingleWidgetPopupRow(
                            selected = selectedAccountID == account.id,
                            onClick = { onSelect(account.id) },
                        ) {
                            Image(
                                painter = painterResource(R.drawable.china_unicom_knot_watermark),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(SingleWidgetUnicomRed),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = buildString {
                                    append(if (hideMobile) maskMobileNumberForWidgetPicker(account.mobile) else account.mobile)
                                    val location = locationFor(account.mobile)?.trim().orEmpty()
                                    if (location.isNotEmpty()) append(" （$location）")
                                },
                                color = Color.Black,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 24.dp)
                    .size(18.dp)
                    .rotate(45f)
                    .background(Color(0xFFF9F9FB), RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun SingleWidgetPopupRow(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selected) "✓" else "",
            color = Color.Black,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(20.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.width(8.dp))
        content()
    }
}

private fun maskMobileNumberForWidgetPicker(value: String): String {
    val digits = value.filter(Char::isDigit)
    return if (digits.length >= 11) {
        digits.take(3) + " **** " + digits.takeLast(4)
    } else {
        value
    }
}
