package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clxmhcs.chinaunicom.R
import com.clxmhcs.chinaunicom.core.model.AppSettings
import com.clxmhcs.chinaunicom.core.model.PhoneCarrierCorrection
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.broadbandaccount.BroadbandAccountInfo

private val CarrierCorrectionBackground = Color(0xFFF2F2F7)
private val CarrierCorrectionSecondary = Color(0xFF8E8E93)
private val CarrierCorrectionSeparator = Color(0xFFE5E5EA)
private val CarrierCorrectionBlue = Color(0xFF3478F6)
private val CarrierCorrectionRed = Color(0xFFFF3B30)

/**
 * Visual/source parity for iOS `CarrierCorrectionView` / `CarrierCorrectionRowView`.
 *
 * The correction remains display-only. It does not alter account identity, login,
 * query targets, refresh targets, quota data, Cookie/session state or credentials.
 */
@Composable
internal fun IosCarrierCorrectionRefinedScreen(
    mobileAccounts: List<UnicomAccount>,
    broadbandAccounts: List<BroadbandAccountInfo>,
    viewModel: SettingsM11CViewModel,
    onBack: () -> Unit,
) {
    val attribution by viewModel.attributionState.collectAsState()
    val settings = LocalAppSettings.current
    val sortedMobile = remember(mobileAccounts) { mobileAccounts.sortedBy { it.sortOrder } }
    val sortedBroadband = remember(broadbandAccounts) {
        broadbandAccounts.sortedWith(compareByDescending<BroadbandAccountInfo> { it.updatedAt }.thenBy { it.serviceNumber })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CarrierCorrectionBackground),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            CarrierCorrectionHeader(onBack)
            Spacer(Modifier.size(28.dp))
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(26.dp),
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "用于修正携号转网、异网号段或融合号码导致的运营商识别偏差。",
                        color = CarrierCorrectionSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    Text(
                        text = "这里只影响 App 与组件设置中的号码图标和运营商展示，不影响登录、查询、刷新和套餐数据。",
                        color = CarrierCorrectionSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
            Text(
                text = "已保存号码",
                color = CarrierCorrectionSecondary,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 18.dp, top = 24.dp, bottom = 10.dp),
            )
        }

        if (sortedMobile.isEmpty() && sortedBroadband.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(26.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("暂无已保存号码", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "保存手机或宽带号码后可在这里修正运营商显示。",
                            color = CarrierCorrectionSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        } else {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(26.dp),
                ) {
                    Column {
                        var renderedIndex = 0
                        val totalRows = sortedMobile.size + sortedBroadband.size

                        sortedMobile.forEach { account ->
                            val number = account.mobile
                            val correction = attribution.corrections[number.filter(Char::isDigit)]
                                ?: PhoneCarrierCorrection.AUTOMATIC
                            val location = viewModel.cachedLocation(number)?.trim()?.takeIf { it.isNotEmpty() }
                            val displayedCarrier = correction.carrier?.displayName
                                ?: viewModel.resolvedCarrierTitle(number)
                            CarrierCorrectionRow(
                                displayNumber = displayMobileNumber(number, settings),
                                location = location,
                                detailText = account.packageName.trim().ifEmpty { "手机号码" },
                                automaticText = "自动识别：${viewModel.automaticCarrierTitle(number).let { if (it == "未知") "未知" else it }}号段",
                                currentText = "当前显示：$displayedCarrier${if (correction == PhoneCarrierCorrection.AUTOMATIC) "" else "（手动纠正）"}",
                                displayedCarrier = displayedCarrier,
                                correction = correction,
                                onCorrectionSelected = { viewModel.setCorrection(number, it) },
                            )
                            renderedIndex += 1
                            if (renderedIndex < totalRows) {
                                Divider(
                                    modifier = Modifier.padding(start = 54.dp, end = 18.dp),
                                    thickness = 0.7.dp,
                                    color = CarrierCorrectionSeparator,
                                )
                            }
                        }

                        sortedBroadband.forEach { account ->
                            val number = account.serviceNumber
                            val correction = attribution.corrections[number.filter(Char::isDigit)]
                                ?: PhoneCarrierCorrection.AUTOMATIC
                            val automatic = viewModel.automaticCarrierTitle(number)
                            val displayedCarrier = correction.carrier?.displayName
                                ?: viewModel.resolvedCarrierTitle(number).let { if (it == "未知") "其它" else it }
                            CarrierCorrectionRow(
                                displayNumber = displayBroadbandNumber(number, settings),
                                location = account.areaCode.trim().takeIf { it.isNotEmpty() },
                                detailText = account.displayName.trim().ifEmpty { "宽带号码" },
                                automaticText = "号码类型：宽带号",
                                currentText = "当前显示：$displayedCarrier${if (correction == PhoneCarrierCorrection.AUTOMATIC) "" else "（手动纠正）"}",
                                displayedCarrier = if (automatic == "未知" && correction == PhoneCarrierCorrection.AUTOMATIC) "其它" else displayedCarrier,
                                correction = correction,
                                onCorrectionSelected = { viewModel.setCorrection(number, it) },
                            )
                            renderedIndex += 1
                            if (renderedIndex < totalRows) {
                                Divider(
                                    modifier = Modifier.padding(start = 54.dp, end = 18.dp),
                                    thickness = 0.7.dp,
                                    color = CarrierCorrectionSeparator,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (attribution.corrections.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    color = Color.White,
                    shape = RoundedCornerShape(24.dp),
                ) {
                    TextButton(
                        onClick = viewModel::resetCorrections,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "重置全部纠正",
                            color = CarrierCorrectionRed,
                            fontSize = 16.sp,
                            lineHeight = 21.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CarrierCorrectionHeader(onBack: () -> Unit) {
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
                    val width = 2.7.dp.toPx()
                    drawLine(
                        color = Color(0xFF1C1C1E),
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.18f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.50f),
                        strokeWidth = width,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color(0xFF1C1C1E),
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.50f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.82f),
                        strokeWidth = width,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        Text(
            text = "号码归属纠正",
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
private fun CarrierCorrectionRow(
    displayNumber: String,
    location: String?,
    detailText: String,
    automaticText: String,
    currentText: String,
    displayedCarrier: String,
    correction: PhoneCarrierCorrection,
    onCorrectionSelected: (PhoneCarrierCorrection) -> Unit,
) {
    var menuExpanded by remember(displayNumber) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 108.dp)
            .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CarrierCorrectionIcon(displayedCarrier)
        Spacer(Modifier.size(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        append(displayNumber)
                        location?.trim()?.takeIf { it.isNotEmpty() }?.let { append("（$it）") }
                    },
                    color = Color.Black,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = detailText,
                color = CarrierCorrectionSecondary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(automaticText, color = CarrierCorrectionSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                Text(currentText, color = CarrierCorrectionSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }

        Box {
            TextButton(
                onClick = { menuExpanded = true },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Text(
                    correction.displayName,
                    color = CarrierCorrectionBlue,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.width(4.dp))
                CarrierCorrectionPickerChevron()
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier
                    .width(190.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White),
            ) {
                PhoneCarrierCorrection.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.displayName,
                                color = Color.Black,
                                fontSize = 17.sp,
                                lineHeight = 22.sp,
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier.width(22.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (option == correction) {
                                    Text("✓", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            if (option != correction) onCorrectionSelected(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CarrierCorrectionPickerChevron() {
    Canvas(Modifier.size(width = 12.dp, height = 19.dp)) {
        val stroke = 1.65.dp.toPx()
        val blue = CarrierCorrectionBlue
        drawLine(
            color = blue,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.39f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.23f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = blue,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.23f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.39f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = blue,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.61f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.77f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = blue,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.77f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.61f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun CarrierCorrectionIcon(carrier: String) {
    Box(
        modifier = Modifier.size(26.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (carrier == "联通") {
            Image(
                painter = painterResource(R.drawable.china_unicom_knot_watermark),
                contentDescription = "中国联通",
                colorFilter = ColorFilter.tint(CarrierCorrectionRed),
                modifier = Modifier.size(20.dp),
            )
        } else {
            val (label, color) = when (carrier) {
                "移动" -> "移" to Color(0xFF34C759)
                "电信" -> "电" to Color(0xFF3478F6)
                "广电" -> "广" to Color(0xFFFF9500)
                else -> "其" to CarrierCorrectionSecondary
            }
            Surface(
                modifier = Modifier.size(22.dp),
                shape = CircleShape,
                color = color.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
