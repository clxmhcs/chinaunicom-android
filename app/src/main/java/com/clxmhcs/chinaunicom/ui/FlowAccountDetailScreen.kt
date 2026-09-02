package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clxmhcs.chinaunicom.core.model.DisplayUnit
import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.FlowSummary
import com.clxmhcs.chinaunicom.core.model.FlowSummaryGroup
import com.clxmhcs.chinaunicom.core.model.PackageCategory
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.ResourceDisplayKind
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.parser.FlowFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

private val FlowDetailBackground = Color(0xFFF3F3F8)
private val FlowDetailBlue = Color(0xFF2F6FED)
private val FlowDetailSecondary = Color(0xFF7A7A80)
private val FlowDetailTertiary = Color(0xFF9A9AA0)
private val FlowDetailGreen = Color(0xFF59C7A0)
private val FlowTagPink = Color(0xFFFF4777)
private val FlowTagPurple = Color(0xFF8668D8)

/** UI-07: Android counterpart of iOS AccountDetailView.swift. */
@Composable
internal fun FlowAccountDetailScreen(
    account: UnicomAccount,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val settings = LocalAppSettings.current
    val displaySettingsViewModel: FlowDisplaySettingsViewModel = viewModel()
    var menuExpanded by remember { mutableStateOf(false) }
    var showsDisplaySettings by rememberSaveable(account.id.toString()) { mutableStateOf(false) }
    var showsEditCard by rememberSaveable(account.id.toString()) { mutableStateOf(false) }
    val formatter = remember(settings.displayUnit) { FlowFormatter(settings.displayUnit) }
    val mobile = if (settings.hideMobileMiddleDigits) flowDetailMaskMobile(account.mobile) else account.mobile

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FlowDetailBackground),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "nav") {
                FlowAccountDetailNavigationHeader(
                    mobile = mobile,
                    menuExpanded = menuExpanded,
                    onBack = onBack,
                    onMenuOpen = { menuExpanded = true },
                    onMenuDismiss = { menuExpanded = false },
                    onRefresh = {
                        menuExpanded = false
                        onRefresh()
                    },
                    onDisplaySettings = {
                        menuExpanded = false
                        showsDisplaySettings = true
                    },
                    onEditCard = {
                        menuExpanded = false
                        showsEditCard = true
                    },
                )
            }

            item(key = "refresh-time") {
                Text(
                    text = "刷新时间 ${flowDetailRefreshTime(account.lastUpdatedAt)}",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = FlowDetailTertiary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }

            item(key = "account-header") {
                FlowAccountHeaderCard(account = account, mobile = mobile)
            }

            item(key = "package-list") {
                FlowPackageListCard(
                    account = account,
                    formatter = formatter,
                    onManageDisplay = { showsDisplaySettings = true },
                )
            }
        }
    }

    if (showsDisplaySettings) {
        FlowDisplaySettingsDialog(
            account = account,
            displayUnit = settings.displayUnit,
            hideMobileMiddleDigits = settings.hideMobileMiddleDigits,
            viewModel = displaySettingsViewModel,
            onDismiss = { showsDisplaySettings = false },
        )
    }

    if (showsEditCard) {
        FlowEditCardDialog(
            account = account,
            viewModel = displaySettingsViewModel,
            onDismiss = { showsEditCard = false },
        )
    }
}

@Composable
private fun FlowAccountDetailNavigationHeader(
    mobile: String,
    menuExpanded: Boolean,
    onBack: () -> Unit,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onDisplaySettings: () -> Unit,
    onEditCard: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(52.dp)
                .clickable(onClick = onBack),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 7.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("‹", fontSize = 43.sp, lineHeight = 43.sp, fontWeight = FontWeight.Light)
            }
        }

        Text(
            text = mobile,
            modifier = Modifier.align(Alignment.Center),
            fontSize = 19.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            maxLines = 1,
        )

        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .clickable(onClick = onMenuOpen),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 7.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(31.dp)
                            .border(2.dp, FlowDetailBlue, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("•••", fontSize = 12.sp, color = FlowDetailBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = onMenuDismiss) {
                DropdownMenuItem(text = { Text("刷新") }, onClick = onRefresh)
                DropdownMenuItem(text = { Text("显示设置") }, onClick = onDisplaySettings)
                DropdownMenuItem(text = { Text("编辑卡片") }, onClick = onEditCard)
            }
        }
    }
}

@Composable
private fun FlowAccountHeaderCard(account: UnicomAccount, mobile: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(FlowDetailBlue.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("▣", fontSize = 23.sp, color = FlowDetailBlue, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    mobile,
                    fontSize = 19.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    account.packageName.ifBlank { "联通套餐" },
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = 2,
                )
            }

            Text(
                when {
                    !account.lastErrorMessage.isNullOrBlank() -> "刷新失败"
                    account.lastUpdatedAt != null -> "刷新成功"
                    else -> ""
                },
                fontSize = 12.sp,
                color = FlowDetailTertiary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FlowPackageListCard(
    account: UnicomAccount,
    formatter: FlowFormatter,
    onManageDisplay: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("套餐包明细", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "管理显示",
                    modifier = Modifier.clickable(onClick = onManageDisplay),
                    fontSize = 13.sp,
                    color = FlowDetailBlue,
                )
            }

            Spacer(Modifier.height(15.dp))

            if (account.visibleDetailPackages.isEmpty()) {
                Text(
                    if (account.packages.isEmpty()) "此号码未订购流量包，套餐内也未包含流量。" else "所有套餐包均已隐藏",
                    modifier = Modifier.padding(vertical = 16.dp),
                    fontSize = 13.sp,
                    color = FlowDetailSecondary,
                )
            } else {
                account.visibleDetailPackages.forEachIndexed { index, packageValue ->
                    FlowPackageDetailRow(account, packageValue, formatter)
                    if (index < account.visibleDetailPackages.lastIndex) {
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowPackageDetailRow(
    account: UnicomAccount,
    packageValue: FlowPackage,
    formatter: FlowFormatter,
) {
    val quotaType = account.quotaType(packageValue)
    val fraction = packageValue.detailDisplayFraction(quotaType)
    val remainingText = if (quotaType == QuotaType.UNLIMITED) "∞ 不限量" else formatter.string(packageValue.remainingMB)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    account.displayName(packageValue),
                    fontSize = 14.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    FlowTinyTag(if (quotaType == QuotaType.UNLIMITED) "不限量" else "有限", FlowDetailBlue)
                    FlowTinyTag(flowCategoryShortTitle(account.category(packageValue)), FlowDetailBlue)
                    packageValue.resolvedShareScope.title?.let { FlowTinyTag(it, FlowTagPink) }
                    packageValue.resolvedCarryForwardScope.title?.let { FlowTinyTag(it, FlowTagPurple) }
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    remainingText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = 1,
                )
                Text(
                    if (quotaType == QuotaType.UNLIMITED) "已用 ${formatter.string(packageValue.usedMB)}" else "剩余",
                    fontSize = 9.sp,
                    color = FlowDetailSecondary,
                    maxLines = 1,
                )
            }
            Text("›", modifier = Modifier.padding(top = 4.dp), fontSize = 27.sp, color = FlowDetailSecondary)
        }

        if (!packageValue.endDateText.isNullOrBlank()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "有效期：${packageValue.endDateText}",
                    modifier = Modifier.weight(1f),
                    fontSize = 11.5.sp,
                    color = FlowDetailSecondary,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .width(302.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
                ) {
                    if (fraction != null && fraction > 0.0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f))
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(FlowDetailGreen),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowTinyTag(text: String, tint: Color) {
    Text(text, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)
}

/** UI-07: Android counterpart of iOS PackageDisplaySettingsView.swift. */
@Composable
private fun FlowDisplaySettingsDialog(
    account: UnicomAccount,
    displayUnit: DisplayUnit,
    hideMobileMiddleDigits: Boolean,
    viewModel: FlowDisplaySettingsViewModel,
    onDismiss: () -> Unit,
) {
    var editMode by rememberSaveable { mutableStateOf(false) }
    var selectedGroupID by rememberSaveable { mutableStateOf<String?>(null) }
    var resourceChoiceKey by rememberSaveable { mutableStateOf<String?>(null) }
    val formatter = remember(displayUnit) { FlowFormatter(displayUnit) }

    LaunchedEffect(account.id) {
        viewModel.materializeSummaryGroups(account.id)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x55000000)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp))
                    .background(FlowDetailBackground),
            ) {
                FlowDisplaySettingsTopBar(
                    editMode = editMode,
                    onToggleEdit = { editMode = !editMode },
                    onDone = onDismiss,
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    item(key = "preview") {
                        FlowDisplaySection(title = "首页预览") {
                            FlowPreviewAccountCard(
                                account = account,
                                formatter = formatter,
                                hideMobileMiddleDigits = hideMobileMiddleDigits,
                            )
                        }
                    }

                    if (account.ambiguousResourceGroups.isNotEmpty()) {
                        item(key = "ambiguous") {
                            FlowDisplaySection(
                                title = "需确认资源类型",
                                footer = "这些业务名称同时出现在流量和语音中。进入候选项后可随时调整“资源类型”，选错后也能从这里改回。",
                            ) {
                                account.ambiguousResourceGroups.forEach { group ->
                                    Text(
                                        group.displayName,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = FlowDetailSecondary,
                                    )
                                    group.flowPackages.forEach { packageValue ->
                                        FlowAmbiguousResourceRow(
                                            name = packageValue.originalName,
                                            kind = "流量候选",
                                            value = if (packageValue.remainingMB != null) "剩余 ${formatter.string(packageValue.remainingMB)}" else "--",
                                            glyph = "▥",
                                            onClick = { resourceChoiceKey = packageValue.id },
                                        )
                                    }
                                    group.voicePackages.forEach { voicePackage ->
                                        FlowAmbiguousResourceRow(
                                            name = voicePackage.originalName,
                                            kind = "语音候选",
                                            value = voicePackage.remainingMinutes?.let { "剩余 ${formatMinutesForDisplay(it)}" } ?: "--",
                                            glyph = "●",
                                            onClick = { resourceChoiceKey = voicePackage.id },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "summaries") {
                        FlowDisplaySection(
                            title = "首页流量统计",
                            footer = "进入分类后，用复选框勾选多个同类流量包。App 会自动汇总已用量和总量，并在首页显示蓝色条状进度。",
                        ) {
                            account.configuredSummaryGroups.forEach { group ->
                                FlowSummaryManagementRow(
                                    account = account,
                                    group = group,
                                    formatter = formatter,
                                    editMode = editMode,
                                    onOpen = { selectedGroupID = group.id },
                                    onMoveUp = { viewModel.moveSummaryGroup(account.id, group.id, -1) },
                                    onMoveDown = { viewModel.moveSummaryGroup(account.id, group.id, 1) },
                                )
                                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            }
                            Text(
                                "+  新建统计分类",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.addSummaryGroup(account.id) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                fontSize = 15.sp,
                                color = FlowDetailBlue,
                            )
                        }
                    }

                    item(key = "packages") {
                        FlowDisplaySection(
                            title = "流量包明细",
                            footer = "这里保留联通返回的原始流量包。可调整显示顺序或隐藏；首页统计由上方的多选分类控制。",
                        ) {
                            account.visibleDetailPackages.forEach { packageValue ->
                                FlowPackageManagementRow(
                                    account = account,
                                    packageValue = packageValue,
                                    formatter = formatter,
                                    editMode = editMode,
                                    onHide = { viewModel.setPackageHidden(account.id, packageValue.id, true) },
                                    onMoveUp = { viewModel.moveVisiblePackage(account.id, packageValue.id, -1) },
                                    onMoveDown = { viewModel.moveVisiblePackage(account.id, packageValue.id, 1) },
                                )
                                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            }
                        }
                    }

                    if (account.hiddenPackages.isNotEmpty()) {
                        item(key = "hidden") {
                            FlowDisplaySection(title = "已隐藏流量包") {
                                account.hiddenPackages.forEach { packageValue ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(account.displayName(packageValue), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                            Text(packageValue.originalName, fontSize = 11.sp, color = FlowDetailSecondary)
                                        }
                                        Text(
                                            "恢复",
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(9.dp))
                                                .border(1.dp, FlowDetailBlue.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                                                .clickable { viewModel.setPackageHidden(account.id, packageValue.id, false) }
                                                .padding(horizontal = 12.dp, vertical = 7.dp),
                                            fontSize = 12.sp,
                                            color = FlowDetailBlue,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedGroupID?.let { groupID ->
        val group = account.configuredSummaryGroups.firstOrNull { it.id == groupID }
        if (group != null) {
            FlowSummaryGroupEditorDialog(
                account = account,
                group = group,
                viewModel = viewModel,
                onDismiss = { selectedGroupID = null },
            )
        }
    }

    resourceChoiceKey?.let { key ->
        FlowResourceKindDialog(
            packageKey = key,
            onChoose = { kind ->
                viewModel.setResourceKind(account.id, key, kind)
                resourceChoiceKey = null
            },
            onDismiss = { resourceChoiceKey = null },
        )
    }
}

@Composable
private fun FlowDisplaySettingsTopBar(editMode: Boolean, onToggleEdit: () -> Unit, onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .padding(horizontal = 18.dp),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable(onClick = onToggleEdit),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 5.dp,
        ) {
            Text(
                if (editMode) "结束编辑" else "编辑",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                fontSize = 16.sp,
                color = FlowDetailBlue,
            )
        }

        Text(
            "显示内容",
            modifier = Modifier.align(Alignment.Center),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable(onClick = onDone),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 5.dp,
        ) {
            Text(
                "完成",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = FlowDetailBlue,
            )
        }
    }
}

@Composable
private fun FlowDisplaySection(
    title: String,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, modifier = Modifier.padding(start = 18.dp), fontSize = 14.sp, color = FlowDetailSecondary)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp,
        ) {
            Column { content() }
        }
        if (footer != null) {
            Text(
                footer,
                modifier = Modifier.padding(horizontal = 18.dp),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = FlowDetailSecondary,
            )
        }
    }
}

@Composable
private fun FlowPreviewAccountCard(
    account: UnicomAccount,
    formatter: FlowFormatter,
    hideMobileMiddleDigits: Boolean,
) {
    val mobile = if (hideMobileMiddleDigits) flowDetailMaskMobile(account.mobile) else account.mobile
    val selectedIDs = account.visibleSummaryGroups.flatMap { it.packageKeys }.toSet()
    val selected = account.visibleDetailPackages.filter { it.id in selectedIDs }
    val used = selected.sumOf { it.safeUsedMB }
    val total = selected.mapNotNull { it.totalMB }.sum().takeIf { it > 0.0 }
    val cardShape = RoundedCornerShape(26.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surface)
            .drawWithCache {
                val gradient = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFE0B8).copy(alpha = 0.75f), Color(0xFFFFE0B8).copy(alpha = 0.35f), Color.Transparent),
                    start = Offset(size.width, 0f),
                    end = Offset(size.width * 0.50f, size.height * 0.50f),
                )
                onDrawBehind { drawRect(gradient) }
            }
            .padding(horizontal = 18.dp, vertical = 17.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("⌘", color = Color(0xFFFF8F1F), fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text(mobile, modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(account.lastUpdatedAt?.let { flowDetailTimeOnly(it) } ?: "--", fontSize = 13.sp, color = FlowDetailSecondary)
            }
            Text(account.packageName.ifBlank { "联通套餐" }, fontSize = 14.sp, color = FlowDetailSecondary)
            if (selected.isNotEmpty()) {
                Text(
                    "［ 已用：${formatter.string(used)}，总流量：${total?.let(formatter::string) ?: "不限量"} ］",
                    fontSize = 14.sp,
                    color = Color(0xFFFF8F1F),
                )
            }
            account.visibleSummaryGroups.forEach { group ->
                FlowPreviewSummaryRow(account.summary(group), formatter)
            }
        }
    }
}

@Composable
private fun FlowPreviewSummaryRow(summary: FlowSummary, formatter: FlowFormatter) {
    val used = summary.usedMB.coerceAtLeast(0.0)
    val fraction = if (summary.isUnlimited) {
        val step = 100.0 * 1024.0
        val shownTotal = ceil(used / step).coerceAtLeast(1.0) * step
        (used / shownTotal).coerceIn(0.0, 1.0)
    } else summary.usedFraction ?: 0.0

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${summary.name}：", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("已用 ${formatter.string(summary.usedMB)}", fontSize = 11.sp, color = FlowDetailSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                if (summary.isUnlimited) "不限量" else "剩余 ${formatter.string(summary.remainingMB)}/共 ${formatter.string(summary.totalMB)}",
                fontSize = 11.sp,
                color = FlowDetailSecondary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
                .background(FlowDetailBlue.copy(alpha = 0.12f)),
        ) {
            if (fraction > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f))
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(FlowDetailBlue),
                )
            }
        }
    }
}

@Composable
private fun FlowAmbiguousResourceRow(name: String, kind: String, value: String, glyph: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(FlowDetailBlue.copy(alpha = 0.09f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, color = FlowDetailBlue, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(kind, fontSize = 11.5.sp, color = FlowDetailSecondary)
        }
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FlowDetailSecondary, textAlign = TextAlign.End)
        Text("›", fontSize = 25.sp, color = FlowDetailTertiary)
    }
}

@Composable
private fun FlowSummaryManagementRow(
    account: UnicomAccount,
    group: FlowSummaryGroup,
    formatter: FlowFormatter,
    editMode: Boolean,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val summary = account.summary(group)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(FlowDetailBlue.copy(alpha = 0.09f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("▥", fontSize = 18.sp, color = FlowDetailBlue, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(group.name, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    summary.packageCount == 0 -> "尚未选择流量包"
                    summary.isUnlimited -> "${formatter.string(summary.usedMB)} / 不限量"
                    else -> "${formatter.string(summary.usedMB)} / ${formatter.string(summary.totalMB)}"
                },
                fontSize = 11.5.sp,
                color = FlowDetailSecondary,
            )
            Text("已选 ${summary.packageCount} 个流量包", fontSize = 10.5.sp, color = FlowDetailTertiary)
        }
        if (!group.isVisibleOnHome) Text("首页隐藏", fontSize = 11.sp, color = FlowDetailSecondary)
        if (editMode) {
            Text("↑", modifier = Modifier.clickable(onClick = onMoveUp).padding(5.dp), color = FlowDetailBlue)
            Text("↓", modifier = Modifier.clickable(onClick = onMoveDown).padding(5.dp), color = FlowDetailBlue)
        } else {
            Text("›", fontSize = 25.sp, color = FlowDetailTertiary)
        }
    }
}

@Composable
private fun FlowPackageManagementRow(
    account: UnicomAccount,
    packageValue: FlowPackage,
    formatter: FlowFormatter,
    editMode: Boolean,
    onHide: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(account.displayName(packageValue), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
            Text(
                listOfNotNull(
                    if (account.quotaType(packageValue) == QuotaType.UNLIMITED) "不限量" else "有限",
                    flowCategoryShortTitle(account.category(packageValue)),
                    packageValue.resolvedShareScope.title,
                ).joinToString(" · "),
                fontSize = 11.sp,
                color = FlowDetailSecondary,
            )
        }
        Text(
            if (account.quotaType(packageValue) == QuotaType.UNLIMITED) "不限量" else formatter.string(packageValue.remainingMB),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = FlowDetailSecondary,
        )
        if (editMode) {
            Text("隐藏", modifier = Modifier.clickable(onClick = onHide).padding(5.dp), fontSize = 11.sp, color = Color.Red)
            Text("↑", modifier = Modifier.clickable(onClick = onMoveUp).padding(5.dp), color = FlowDetailBlue)
            Text("↓", modifier = Modifier.clickable(onClick = onMoveDown).padding(5.dp), color = FlowDetailBlue)
        } else {
            Text("›", fontSize = 24.sp, color = FlowDetailTertiary)
        }
    }
}

@Composable
private fun FlowSummaryGroupEditorDialog(
    account: UnicomAccount,
    group: FlowSummaryGroup,
    viewModel: FlowDisplaySettingsViewModel,
    onDismiss: () -> Unit,
) {
    var name by remember(group.id, group.name) { mutableStateOf(group.name) }
    var selected by remember(group.id, group.packageKeys) { mutableStateOf(group.packageKeys.toSet()) }
    var visible by remember(group.id, group.isVisibleOnHome) { mutableStateOf(group.isVisibleOnHome) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("编辑统计分类", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("分类名称") }, singleLine = true)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("在首页卡片显示", modifier = Modifier.weight(1f))
                    Switch(checked = visible, onCheckedChange = { visible = it })
                }
                Text("选择流量包（可多选）", fontSize = 13.sp, color = FlowDetailSecondary)
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(account.visibleDetailPackages, key = { it.id }) { packageValue ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (packageValue.id in selected) selected - packageValue.id else selected + packageValue.id
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = packageValue.id in selected,
                                onCheckedChange = {
                                    selected = if (it) selected + packageValue.id else selected - packageValue.id
                                },
                            )
                            Text(account.displayName(packageValue), modifier = Modifier.weight(1f), fontSize = 13.sp)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        viewModel.deleteSummaryGroup(account.id, group.id)
                        onDismiss()
                    }) { Text("删除", color = Color.Red) }
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = {
                        viewModel.updateSummaryGroup(
                            account.id,
                            group.copy(
                                name = name.trim().ifBlank { "未命名分类" },
                                packageKeys = account.visibleDetailPackages.filter { it.id in selected }.map { it.id },
                                isVisibleOnHome = visible,
                            ),
                        )
                        onDismiss()
                    }) { Text("保存") }
                }
            }
        }
    }
}

@Composable
private fun FlowResourceKindDialog(
    packageKey: String,
    onChoose: (ResourceDisplayKind?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("资源类型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("选择该候选资源在本机的显示类型。", fontSize = 13.sp, color = FlowDetailSecondary)
                TextButton(onClick = { onChoose(null) }) { Text("自动识别") }
                TextButton(onClick = { onChoose(ResourceDisplayKind.FLOW) }) { Text("流量") }
                TextButton(onClick = { onChoose(ResourceDisplayKind.VOICE) }) { Text("语音") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun FlowEditCardDialog(
    account: UnicomAccount,
    viewModel: FlowDisplaySettingsViewModel,
    onDismiss: () -> Unit,
) {
    var name by remember(account.id, account.displayName) { mutableStateOf(account.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑卡片") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("卡片名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.updateAccountDisplayName(account.id, name)
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun flowCategoryShortTitle(category: PackageCategory): String = when (category) {
    PackageCategory.AUTOMATIC -> "自动"
    PackageCategory.GENERAL -> "通用"
    PackageCategory.DIRECTED -> "定向免流"
    PackageCategory.OTHER -> "其他"
}

private fun flowDetailMaskMobile(value: String): String {
    val digits = value.filter(Char::isDigit)
    return if (digits.length >= 7) "${digits.take(3)}****${digits.takeLast(4)}" else value
}

private fun flowDetailRefreshTime(value: Instant?): String {
    if (value == null) return "从未"
    return DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE)
        .withZone(ZoneId.systemDefault())
        .format(value)
}

private fun flowDetailTimeOnly(value: Instant): String =
    DateTimeFormatter.ofPattern("HH:mm", Locale.SIMPLIFIED_CHINESE)
        .withZone(ZoneId.systemDefault())
        .format(value)

private fun formatMinutesForDisplay(value: Double): String =
    if (kotlin.math.abs(value - value.toLong()) < 0.001) "${value.toLong()} 分钟" else String.format(Locale.US, "%.2f 分钟", value)
