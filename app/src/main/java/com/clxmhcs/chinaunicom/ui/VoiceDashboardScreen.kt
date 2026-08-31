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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clxmhcs.chinaunicom.R
import com.clxmhcs.chinaunicom.core.design.ChinaUnicomColors
import com.clxmhcs.chinaunicom.core.design.ChinaUnicomDimensions
import com.clxmhcs.chinaunicom.core.design.ChinaUnicomShapes
import com.clxmhcs.chinaunicom.core.model.RefreshState
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VoicePackage
import java.time.Instant
import kotlin.math.abs
import kotlin.math.round

/**
 * UI-03 voice dashboard.
 *
 * Presentation is source-derived from the current iOS VoiceDashboardView / AccountCardView.
 * Voice refresh, parser and repository authority remains in the closed FlowViewModel/M7 layer.
 */
@Composable
fun VoiceDashboardScreen(
    flowViewModel: FlowViewModel,
) {
    val uiState by flowViewModel.uiState.collectAsState()
    var detailAccountID by rememberSaveable { mutableStateOf<String?>(null) }

    when (val state = uiState) {
        FlowUiState.Loading -> VoiceMessage("加载中…")
        is FlowUiState.Error -> VoiceMessage(state.message)
        is FlowUiState.Content -> {
            val selected = detailAccountID?.let { id ->
                state.accounts.firstOrNull { it.id.toString() == id }
            }
            if (selected != null) {
                VoiceAccountDetail(
                    account = selected,
                    accounts = state.accounts,
                    refreshState = state.appState.refreshState(selected.id),
                    onBack = { detailAccountID = null },
                    onSelectAccount = { detailAccountID = it.id.toString() },
                )
            } else {
                VoiceDashboardContent(
                    state = state,
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
    onOpenAccount: (UnicomAccount) -> Unit,
) {
    val latest = state.accounts.mapNotNull { it.lastUpdatedAt }.maxOrNull()
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
            item { VoiceDashboardHeader(latest = latest) }

            if (state.accounts.isEmpty()) {
                item { VoiceEmptyState() }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        state.accounts.forEachIndexed { index, account ->
                            VoiceAccountCard(
                                account = account,
                                refreshState = state.appState.refreshState(account.id),
                                theme = voiceAccountCardTheme(index),
                                onOpen = { onOpenAccount(account) },
                            )
                        }
                    }
                }
                item {
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
private fun VoiceDashboardHeader(
    latest: Instant?,
) {
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
        shape = RoundedCornerShape(ChinaUnicomShapes.EmptyCardRadius),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 40.dp),
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
                Text(
                    "☎",
                    fontSize = 36.sp,
                    lineHeight = 40.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "还没有联通号码",
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "请先在“流量”页面添加号码，流量刷新时会同时读取语音余量。",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun VoiceAccountCard(
    account: UnicomAccount,
    refreshState: RefreshState,
    theme: VoiceAccountCardTheme,
    onOpen: () -> Unit,
) {
    val settings = LocalAppSettings.current
    val shape = RoundedCornerShape(ChinaUnicomShapes.VoiceCardRadius)
    val mobileText = if (settings.hideMobileMiddleDigits) {
        voiceMaskedMobile(account.mobile)
    } else {
        account.mobile
    }
    val attribution = account.displayName.trim().takeIf {
        it.isNotEmpty() && it != account.mobile && it != account.packageName
    }
    val cardSurface = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
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
                .fillMaxWidth(0.45f),
            alpha = 0.045f,
        )

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                    attribution?.let {
                        Text(
                            it,
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

                Text(
                    "›",
                    fontSize = 16.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f),
                )
            }

            if (account.packageName.isNotBlank()) {
                Text(
                    account.packageName,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
                        Text(
                            "☎  未识别到语音余量",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "当前联通余量接口没有为此号码返回带“分钟/语音”标记的资源。",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        )
                    }
                }

                account.visibleVoicePackages.isEmpty() -> {
                    Text(
                        "所有语音权益均已隐藏",
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        account.visibleVoicePackages.forEach { VoicePackageRow(it) }
                    }
                }
            }

            if (refreshState is RefreshState.Failed) {
                Text(
                    "⚠ ${refreshState.message}",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun VoicePackageRow(packageValue: VoicePackage) {
    val tint = Color(0xFF2673FA)
    val trailingText = if (packageValue.isUnlimited) {
        "不限量"
    } else {
        packageValue.totalMinutes?.let {
            "剩余 ${voiceMinuteText(packageValue.remainingMinutes)}/共 ${voiceMinuteText(it)}"
        } ?: "剩余 ${voiceMinuteText(packageValue.remainingMinutes)}"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                packageValue.originalName,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                trailingText,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                textAlign = TextAlign.End,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "已用 ${voiceMinuteText(packageValue.usedMinutes)}",
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )

            Text(
                if (packageValue.isShared) "共享" else "非共享",
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Blue.copy(alpha = 0.10f))
                    .padding(horizontal = 3.dp),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = Color.Blue,
            )

            Spacer(modifier = Modifier.weight(1f))

            cleanedVoiceEndDateText(packageValue.endDateText)?.let {
                Text(
                    "有效期至 $it",
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    maxLines = 1,
                )
            }
        }

        packageValue.usedFraction?.let { rawFraction ->
            val fraction = rawFraction.toFloat().coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.08f)),
            ) {
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(tint.copy(alpha = 0.82f), tint),
                                ),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceAccountDetail(
    account: UnicomAccount,
    accounts: List<UnicomAccount>,
    refreshState: RefreshState,
    onBack: () -> Unit,
    onSelectAccount: (UnicomAccount) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("返回") }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("语音详情", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(account.mobile, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { AccountSelector(accounts = accounts, selected = account, onSelect = onSelectAccount) }
        item {
            Text(
                "语音不提供单独刷新按钮；请在“流量”页刷新此号码或刷新全部。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (account.visibleVoicePackages.isEmpty()) {
            item {
                VoiceMessageCard(
                    if (account.resolvedVoicePackages.isEmpty()) {
                        "未识别到语音余量。"
                    } else {
                        "所有语音权益均已隐藏。"
                    },
                )
            }
        } else {
            items(account.visibleVoicePackages, key = { it.id }) { packageValue ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column(modifier = Modifier.padding(14.dp)) { VoicePackageRow(packageValue) }
                }
            }
        }

        account.remainingQuerySnapshot?.voice?.let { voice ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("联通余量快照", fontWeight = FontWeight.SemiBold)
                        Text("剩余 ${formatMinutes(voice.remainingMinutes)}", style = MaterialTheme.typography.bodyMedium)
                        Text("已用 ${formatMinutes(voice.usedMinutes)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (refreshState is RefreshState.Failed) {
            item { VoiceMessageCard("最近一次流量/语音同步刷新失败：${refreshState.message}") }
        }
    }
}

@Composable
private fun VoiceMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun VoiceMessageCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
    ) {
        Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun voiceMinuteText(value: Double?): String {
    val safe = value?.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: return "--"
    val rounded = round(safe)
    if (abs(safe - rounded) < 0.005) {
        return "${rounded.toInt()} 分钟"
    }
    return String.format(java.util.Locale.US, "%.2f", safe)
        .trimEnd('0')
        .trimEnd('.') + " 分钟"
}

private fun cleanedVoiceEndDateText(value: String?): String? {
    var text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (text.endsWith("到期")) {
        text = text.dropLast(2).trim()
    }
    return text.takeIf { it.isNotEmpty() }
}

private fun voiceMaskedMobile(value: String): String {
    if (value.length < 7) return value
    return value.replaceRange(3, 7, "****")
}
