package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clxmhcs.chinaunicom.R
import com.clxmhcs.chinaunicom.core.design.ChinaUnicomColors
import com.clxmhcs.chinaunicom.core.design.ChinaUnicomDimensions
import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.RefreshState
import com.clxmhcs.chinaunicom.core.model.ResourceDisplayKind
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VoicePackage
import com.clxmhcs.chinaunicom.core.model.VoiceSummaryGroup
import com.clxmhcs.chinaunicom.core.parser.FlowFormatter
import java.time.Instant
import kotlin.math.abs
import kotlin.math.round

private val VoicePageBackground = Color(0xFFF3F3F8)
private val VoiceBlue = Color(0xFF2F6FED)
private val VoiceSecondary = Color(0xFF7A7A80)
private val VoiceTertiary = Color(0xFF9A9AA0)
private val VoiceProgressBlue = Color(0xFF2673FA)

/**
 * UI-09 voice dashboard and display-settings route.
 *
 * The presentation/navigation contract follows iOS VoiceDashboardView.swift. Voice refresh remains
 * coupled to the flow domain; this file does not own network, parser, credential or cache authority.
 */
@Composable
fun VoiceDashboardScreen(
    flowViewModel: FlowViewModel,
) {
    val uiState by flowViewModel.uiState.collectAsState()
    val displayViewModel: VoiceDisplaySettingsViewModel = viewModel()
    var detailAccountID by rememberSaveable { mutableStateOf<String?>(null) }

    when (val state = uiState) {
        FlowUiState.Loading -> VoiceMessage("加载中…")
        is FlowUiState.Error -> VoiceMessage(state.message)
        is FlowUiState.Content -> {
            val selected = detailAccountID?.let { id ->
                state.accounts.firstOrNull { it.id.toString() == id }
            }
            if (selected != null) {
                VoiceDisplaySettingsScreen(
                    account = selected,
                    viewModel = displayViewModel,
                    onBack = { detailAccountID = null },
                )
            } else {
                VoiceDashboardContent(
                    state = state,
                    displayViewModel = displayViewModel,
                    onOpenAccount = { detailAccountID = it.id.toString() },
                )
            }
        }
    }
}

private data class VoiceAccountCardTheme(
    val accent: Color,
    val softAccent: Color,
)

private fun voiceAccountCardTheme(index: Int): VoiceAccountCardTheme = when (index % 4) {
    1 -> VoiceAccountCardTheme(Color(0xFF29AD6B), Color(0xFFD1F5DE))
    2 -> VoiceAccountCardTheme(Color(0xFFFAC714), Color(0xFFFFFAB8))
    3 -> VoiceAccountCardTheme(Color(0xFF8F63F2), Color(0xFFE8DBFF))
    else -> VoiceAccountCardTheme(Color(0xFFFF8F1F), Color(0xFFFFE0B8))
}

@Composable
private fun VoiceDashboardContent(
    state: FlowUiState.Content,
    displayViewModel: VoiceDisplaySettingsViewModel,
    onOpenAccount: (UnicomAccount) -> Unit,
) {
    val latest = state.accounts.mapNotNull { it.lastUpdatedAt }.maxOrNull()
    val pageBackground = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        ChinaUnicomColors.FlowDashboardTop,
                        pageBackground,
                        pageBackground,
                    ),
                    start = Offset.Zero,
                    end = Offset(1000f, 1500f),
                ),
            ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 18.dp,
                end = 16.dp,
                bottom = 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item(key = "header") { VoiceDashboardHeader(latest = latest) }

            if (state.accounts.isEmpty()) {
                item(key = "empty") { VoiceEmptyState() }
            } else {
                item(key = "cards") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        state.accounts.forEachIndexed { index, account ->
                            VoiceAccountCard(
                                account = account,
                                refreshState = state.appState.refreshState(account.id),
                                theme = voiceAccountCardTheme(index),
                                attribution = displayViewModel.cachedLocation(account.mobile),
                                onOpen = { onOpenAccount(account) },
                            )
                        }
                    }
                }
                item(key = "count") {
                    Text(
                        "共 ${state.accounts.size} 个号码",
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceDashboardHeader(latest: Instant?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "语音",
                modifier = Modifier.weight(1f),
                fontSize = 36.sp,
                lineHeight = 43.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                latest?.let { "已更新：${formatTime(it)}" } ?: "尚未更新",
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = Color(0xFF29AD6B),
                maxLines = 1,
            )
        }
        Text(
            "语音相关数据不单独执行刷新，其数据会随流量同步刷新。",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
            maxLines = 2,
        )
    }
}

@Composable
private fun VoiceEmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("☎", fontSize = 36.sp, color = MaterialTheme.colorScheme.primary)
            }
            Text("还没有联通号码", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "请先在“流量”页面添加号码，流量刷新时会同时读取语音余量。",
                fontSize = 13.sp,
                color = VoiceSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun VoiceAccountCard(
    account: UnicomAccount,
    refreshState: RefreshState,
    theme: VoiceAccountCardTheme,
    attribution: String?,
    onOpen: () -> Unit,
) {
    val settings = LocalAppSettings.current
    val shape = RoundedCornerShape(26.dp)
    val mobileText = if (settings.hideMobileMiddleDigits) voiceMaskedMobile(account.mobile) else account.mobile
    val cardSurface = MaterialTheme.colorScheme.surface
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val watermarkSide = with(LocalDensity.current) {
        (minOf(cardSize.width, cardSize.height) * 0.74f).toDp()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { cardSize = it }
            .alpha(if (refreshState is RefreshState.Loading) 0.90f else 1f)
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
                .size(watermarkSide),
            alpha = 0.045f,
        )

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 17.14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.china_unicom_knot_watermark),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(theme.accent),
                    modifier = Modifier.size(16.dp),
                )

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        mobileText,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    attribution?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "（$it）",
                            fontSize = 12.8.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (refreshState is RefreshState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.7.dp,
                        color = theme.accent,
                    )
                }

                account.lastUpdatedAt?.let {
                    Text(
                        formatTime(it),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                }
                Text("›", fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f))
            }

            if (account.packageName.isNotBlank()) {
                Text(
                    account.packageName,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = 2,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.2.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)),
            )

            when {
                account.resolvedVoicePackages.isEmpty() -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("☎  未识别到语音余量", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "当前联通余量接口没有为此号码返回带“分钟/语音”标记的资源。",
                            fontSize = 11.sp,
                            color = VoiceSecondary,
                        )
                    }
                }
                account.visibleVoicePackages.isEmpty() -> {
                    Text("所有语音权益均已隐藏", fontSize = 12.sp, color = VoiceSecondary)
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(15.43.dp)) {
                        account.visibleVoicePackages.forEach { packageValue ->
                            VoicePackageRow(packageValue = packageValue, showChevron = false)
                        }
                    }
                }
            }

            if (refreshState is RefreshState.Failed) {
                Text("⚠ ${refreshState.message}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun VoicePackageRow(
    packageValue: VoicePackage,
    showChevron: Boolean,
    onClick: (() -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    val trailingText = voiceRemainingText(packageValue)
    val modifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()

    Column(
        modifier = modifier.padding(vertical = if (showChevron) 6.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(6.86.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                packageValue.originalName,
                modifier = Modifier.weight(1f),
                fontSize = 10.29.sp,
                lineHeight = 13.71.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                trailingText,
                fontSize = 8.57.sp,
                lineHeight = 12.86.sp,
                color = VoiceSecondary,
                textAlign = TextAlign.End,
                maxLines = 2,
            )
            trailingAction?.invoke()
            if (showChevron && trailingAction == null) {
                Text(
                    "›",
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "已用 ${voiceMinuteText(packageValue.usedMinutes)}",
                fontSize = 8.57.sp,
                lineHeight = 12.29.sp,
                color = VoiceSecondary,
            )
            Text(
                if (packageValue.isShared) "共享" else "非共享",
                fontSize = 8.57.sp,
                lineHeight = 12.29.sp,
                color = Color(0xFF2196F3),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF2196F3).copy(alpha = 0.10f))
                    .padding(
                        horizontal = 3.dp,
                        vertical = 0.dp,
                    ),
            )
            Spacer(Modifier.weight(1f))
            voiceCleanEndDate(packageValue.endDateText)?.let {
                Text(
                    "有效期至 $it",
                    fontSize = 8.57.sp,
                    lineHeight = 12.29.sp,
                    color = VoiceTertiary,
                    maxLines = 1,
                )
            }
        }

        packageValue.usedFraction?.let { fraction ->
            VoiceProgressBar(fraction, compact = true)
        }
    }
}

@Composable
private fun VoiceProgressBar(fraction: Double, compact: Boolean = false) {
    val barHeight = if (compact) 4.29.dp else 5.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(CircleShape)
            .background(VoiceProgressBlue.copy(alpha = 0.08f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f))
                .height(barHeight)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(VoiceProgressBlue.copy(alpha = 0.82f), VoiceProgressBlue),
                    ),
                ),
        )
    }
}

private data class VoiceResourceTarget(
    val packageKey: String,
    val title: String,
)

@Composable
private fun VoiceDisplaySettingsScreen(
    account: UnicomAccount,
    viewModel: VoiceDisplaySettingsViewModel,
    onBack: () -> Unit,
) {
    val settings = LocalAppSettings.current
    val formatter = remember(settings.displayUnit) { FlowFormatter(settings.displayUnit) }
    val mobile = if (settings.hideMobileMiddleDigits) voiceMaskedMobile(account.mobile) else account.mobile
    var editMode by rememberSaveable(account.id.toString()) { mutableStateOf(false) }
    var resourceTarget by remember { mutableStateOf<VoiceResourceTarget?>(null) }
    var selectedGroup by remember { mutableStateOf<VoiceSummaryGroup?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoicePageBackground),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 118.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "nav") {
                VoiceDisplayNavigationHeader(
                    mobile = mobile,
                    editMode = editMode,
                    onBack = onBack,
                    onToggleEdit = { editMode = !editMode },
                )
            }

            if (account.ambiguousResourceGroups.isNotEmpty()) {
                item(key = "ambiguous-title") { VoiceSectionTitle("需确认资源类型") }
                item(key = "ambiguous-card") {
                    VoiceAmbiguousResourceCard(
                        account = account,
                        formatter = formatter,
                        onSelect = { key, title -> resourceTarget = VoiceResourceTarget(key, title) },
                    )
                }
                item(key = "ambiguous-footer") {
                    Text(
                        "这些业务名称同时出现在流量和语音中。进入候选项后可随时调整“资源类型”，选错后也能从这里改回。",
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        color = VoiceSecondary,
                    )
                }
            }

            item(key = "voice-title") { VoiceSectionTitle("语音包明细") }
            item(key = "voice-card") {
                VoicePackageManagementCard(
                    account = account,
                    editMode = editMode,
                    onSelect = { packageValue ->
                        resourceTarget = VoiceResourceTarget(packageValue.id, packageValue.originalName)
                    },
                    onHide = { packageValue ->
                        viewModel.setVoicePackageHidden(account.id, packageValue.id, true)
                    },
                )
            }

            item(key = "group-title") { VoiceSectionTitle("语音包分类") }
            item(key = "group-card") {
                VoiceSummaryGroupsCard(
                    account = account,
                    editMode = editMode,
                    onOpen = { selectedGroup = it },
                    onAdd = { viewModel.addVoiceSummaryGroup(account.id) },
                    onMove = { group, delta -> viewModel.moveVoiceSummaryGroup(account.id, group.id, delta) },
                    onDelete = { group -> viewModel.deleteVoiceSummaryGroup(account.id, group.id) },
                )
            }
            item(key = "group-footer") {
                Text(
                    "进入分类后可手动输入分类名称，并用复选框勾选多个语音包。分类按当前号码独立保存，供后续通知设置选择调用。",
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    color = VoiceSecondary,
                )
            }

            if (account.hiddenVoicePackages.isNotEmpty()) {
                item(key = "hidden-title") { VoiceSectionTitle("已隐藏语音包") }
                item(key = "hidden-card") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                            account.hiddenVoicePackages.forEachIndexed { index, packageValue ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        packageValue.originalName,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    TextButton(
                                        onClick = { viewModel.setVoicePackageHidden(account.id, packageValue.id, false) },
                                    ) { Text("恢复") }
                                }
                                if (index < account.hiddenVoicePackages.lastIndex) Divider(color = Color.Black.copy(alpha = 0.10f))
                            }
                        }
                    }
                }
            }
        }
    }

    resourceTarget?.let { target ->
        VoiceResourceKindDialog(
            title = target.title,
            onDismiss = { resourceTarget = null },
            onSelect = { kind ->
                viewModel.setResourceKind(account.id, target.packageKey, kind)
                resourceTarget = null
            },
        )
    }

    selectedGroup?.let { group ->
        VoiceSummaryGroupEditDialog(
            account = account,
            group = group,
            onDismiss = { selectedGroup = null },
            onSave = { replacement ->
                viewModel.updateVoiceSummaryGroup(account.id, replacement)
                selectedGroup = null
            },
            onDelete = {
                viewModel.deleteVoiceSummaryGroup(account.id, group.id)
                selectedGroup = null
            },
        )
    }
}

@Composable
private fun VoiceDisplayNavigationHeader(
    mobile: String,
    editMode: Boolean,
    onBack: () -> Unit,
    onToggleEdit: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(44.dp)
                .clickable(onClick = onBack),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 7.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("‹", fontSize = 37.sp, lineHeight = 37.sp, fontWeight = FontWeight.Light)
            }
        }

        Text(
            mobile,
            modifier = Modifier.align(Alignment.Center),
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )

        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .height(44.dp)
                .width(74.dp)
                .clickable(onClick = onToggleEdit),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 7.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (editMode) "完成" else "编辑", fontSize = 14.5.sp, color = VoiceBlue)
            }
        }
    }
}

@Composable
private fun VoiceSectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 18.dp, top = 2.dp),
        fontSize = 15.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Medium,
        color = VoiceSecondary,
    )
}

@Composable
private fun VoiceAmbiguousResourceCard(
    account: UnicomAccount,
    formatter: FlowFormatter,
    onSelect: (String, String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            account.ambiguousResourceGroups.forEachIndexed { groupIndex, group ->
                Text(
                    group.displayName,
                    modifier = Modifier.padding(vertical = 6.dp),
                    fontSize = 10.5.sp,
                    lineHeight = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoiceSecondary,
                    maxLines = 2,
                )
                Divider(color = Color.Black.copy(alpha = 0.10f))
                group.flowPackages.forEach { packageValue ->
                    VoiceAmbiguousResourceRow(
                        name = packageValue.originalName,
                        kind = "流量候选",
                        value = voiceFlowCandidateText(packageValue, formatter),
                        isVoice = false,
                        onClick = { onSelect(packageValue.id, packageValue.originalName) },
                    )
                }
                group.voicePackages.forEach { packageValue ->
                    VoiceAmbiguousResourceRow(
                        name = packageValue.originalName,
                        kind = "语音候选",
                        value = voiceRemainingCompactText(packageValue),
                        isVoice = true,
                        onClick = { onSelect(packageValue.id, packageValue.originalName) },
                    )
                }
                if (groupIndex < account.ambiguousResourceGroups.lastIndex) Divider(color = Color.Black.copy(alpha = 0.10f))
            }
        }
    }
}

@Composable
private fun VoiceAmbiguousResourceRow(
    name: String,
    kind: String,
    value: String,
    isVoice: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(VoiceBlue.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            VoiceCandidateGlyph(isVoice = isVoice)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(kind, fontSize = 10.5.sp, lineHeight = 13.sp, color = VoiceSecondary)
        }
        Text(
            value,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = VoiceSecondary,
            maxLines = 1,
            textAlign = TextAlign.End,
        )
        Text(
            "›",
            fontSize = 14.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
        )
    }
}

@Composable
private fun VoiceCandidateGlyph(isVoice: Boolean) {
    Canvas(modifier = Modifier.size(17.dp)) {
        if (isVoice) {
            val stroke = size.minDimension * 0.19f
            drawLine(
                color = VoiceBlue,
                start = Offset(size.width * 0.25f, size.height * 0.18f),
                end = Offset(size.width * 0.20f, size.height * 0.48f),
                strokeWidth = stroke,
            )
            drawLine(
                color = VoiceBlue,
                start = Offset(size.width * 0.20f, size.height * 0.48f),
                end = Offset(size.width * 0.53f, size.height * 0.80f),
                strokeWidth = stroke,
            )
            drawLine(
                color = VoiceBlue,
                start = Offset(size.width * 0.53f, size.height * 0.80f),
                end = Offset(size.width * 0.82f, size.height * 0.74f),
                strokeWidth = stroke,
            )
        } else {
            val barWidth = size.width * 0.18f
            val gap = size.width * 0.10f
            val bottom = size.height * 0.84f
            val left = size.width * 0.14f
            listOf(0.42f, 0.66f, 0.88f).forEachIndexed { index, heightFraction ->
                val h = size.height * heightFraction
                drawRoundRect(
                    color = VoiceBlue,
                    topLeft = Offset(left + index * (barWidth + gap), bottom - h),
                    size = androidx.compose.ui.geometry.Size(barWidth, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.35f, barWidth * 0.35f),
                )
            }
        }
    }
}

@Composable
private fun VoicePackageManagementCard(
    account: UnicomAccount,
    editMode: Boolean,
    onSelect: (VoicePackage) -> Unit,
    onHide: (VoicePackage) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
            if (account.visibleVoicePackages.isEmpty()) {
                Text(
                    if (account.resolvedVoicePackages.isEmpty()) "尚未识别到语音权益" else "所有语音权益均已隐藏",
                    modifier = Modifier.padding(vertical = 18.dp),
                    fontSize = 13.sp,
                    color = VoiceSecondary,
                )
            } else {
                account.visibleVoicePackages.forEachIndexed { index, packageValue ->
                    VoicePackageRow(
                        packageValue = packageValue,
                        showChevron = true,
                        onClick = { onSelect(packageValue) },
                        trailingAction = if (editMode) {
                            { TextButton(onClick = { onHide(packageValue) }) { Text("隐藏", color = MaterialTheme.colorScheme.error) } }
                        } else null,
                    )
                    if (index < account.visibleVoicePackages.lastIndex) Divider(color = Color.Black.copy(alpha = 0.10f))
                }
            }
        }
    }
}

@Composable
private fun VoiceSummaryGroupsCard(
    account: UnicomAccount,
    editMode: Boolean,
    onOpen: (VoiceSummaryGroup) -> Unit,
    onAdd: () -> Unit,
    onMove: (VoiceSummaryGroup, Int) -> Unit,
    onDelete: (VoiceSummaryGroup) -> Unit,
) {
    val groups = account.voiceSummaryGroups.orEmpty().sortedWith(compareBy<VoiceSummaryGroup> { it.sortOrder }.thenBy { it.name })
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
            if (groups.isEmpty()) {
                Text("尚未创建语音包分类", modifier = Modifier.padding(vertical = 14.dp), fontSize = 13.sp, color = VoiceSecondary)
            } else {
                groups.forEachIndexed { index, group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(group) }
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(group.name.ifBlank { "未命名分类" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("已选 ${group.packageKeys.size} 个语音包", fontSize = 11.sp, color = VoiceSecondary)
                        }
                        if (editMode) {
                            TextButton(onClick = { onMove(group, -1) }) { Text("↑") }
                            TextButton(onClick = { onMove(group, 1) }) { Text("↓") }
                            TextButton(onClick = { onDelete(group) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        } else {
                            Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f))
                        }
                    }
                    if (index < groups.lastIndex) Divider(color = Color.Black.copy(alpha = 0.10f))
                }
            }
            Divider(color = Color.Black.copy(alpha = 0.10f))
            TextButton(onClick = onAdd) { Text("＋  新建语音分类", color = VoiceBlue) }
        }
    }
}

@Composable
private fun VoiceResourceKindDialog(
    title: String,
    onDismiss: () -> Unit,
    onSelect: (ResourceDisplayKind?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("资源类型", color = VoiceSecondary)
                TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) { Text("自动识别") }
                TextButton(onClick = { onSelect(ResourceDisplayKind.FLOW) }, modifier = Modifier.fillMaxWidth()) { Text("流量") }
                TextButton(onClick = { onSelect(ResourceDisplayKind.VOICE) }, modifier = Modifier.fillMaxWidth()) { Text("语音") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun VoiceSummaryGroupEditDialog(
    account: UnicomAccount,
    group: VoiceSummaryGroup,
    onDismiss: () -> Unit,
    onSave: (VoiceSummaryGroup) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(group.id) { mutableStateOf(group.name) }
    var selectedKeys by remember(group.id) { mutableStateOf(group.packageKeys.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑语音分类") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                )
                account.resolvedVoicePackages.forEach { packageValue ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedKeys = if (packageValue.id in selectedKeys) selectedKeys - packageValue.id else selectedKeys + packageValue.id
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = packageValue.id in selectedKeys,
                            onCheckedChange = {
                                selectedKeys = if (it) selectedKeys + packageValue.id else selectedKeys - packageValue.id
                            },
                        )
                        Text(packageValue.originalName, modifier = Modifier.weight(1f), fontSize = 12.sp, maxLines = 2)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(group.copy(name = name.trim(), packageKeys = selectedKeys.toList()))
                },
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

private fun voiceFlowCandidateText(packageValue: FlowPackage, formatter: FlowFormatter): String {
    if (packageValue.detectedQuotaType.name == "UNLIMITED") return "不限量"
    packageValue.remainingMB?.let { return "剩余 ${formatter.string(it)}" }
    packageValue.totalMB?.let { return "共 ${formatter.string(it)}" }
    return "--"
}

private fun voiceRemainingCompactText(packageValue: VoicePackage): String =
    if (packageValue.isUnlimited) "不限量" else "剩余 ${voiceMinuteText(packageValue.remainingMinutes)}"

private fun voiceRemainingText(packageValue: VoicePackage): String {
    if (packageValue.isUnlimited) return "不限量"
    val remaining = voiceMinuteText(packageValue.remainingMinutes)
    return packageValue.totalMinutes?.let { "剩余 $remaining/共 ${voiceMinuteText(it)}" } ?: "剩余 $remaining"
}

private fun voiceMinuteText(value: Double?): String {
    val safe = value ?: 0.0
    val rounded = round(safe)
    val text = if (abs(safe - rounded) < 0.0001) rounded.toLong().toString() else String.format(java.util.Locale.US, "%.2f", safe).trimEnd('0').trimEnd('.')
    return "$text 分钟"
}

private fun voiceCleanEndDate(value: String?): String? {
    var text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (text.endsWith("到期")) text = text.dropLast(2).trim()
    return text.takeIf { it.isNotEmpty() }
}

private fun voiceMaskedMobile(value: String): String {
    val digits = value.filter(Char::isDigit)
    return if (digits.length >= 7) "${digits.take(3)} **** ${digits.takeLast(4)}" else value
}

@Composable
private fun VoiceMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, modifier = Modifier.padding(24.dp))
    }
}
