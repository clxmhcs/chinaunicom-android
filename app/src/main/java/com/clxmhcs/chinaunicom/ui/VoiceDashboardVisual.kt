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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clxmhcs.chinaunicom.R
import com.clxmhcs.chinaunicom.core.model.RefreshState
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VoicePackage
import java.util.Locale
import kotlin.math.abs

/**
 * UI-03 voice dashboard presentation derived from iOS VoiceDashboardView.swift.
 *
 * Voice remains read-only here: no separate refresh path is introduced. The existing flow refresh
 * coordinator remains the only authority that updates flow + voice data together.
 */
@Composable
internal fun IosVoiceDashboardContent(
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
                        Color(0xFFF2F7FF),
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
                start = 16.dp,
                top = 18.dp,
                end = 16.dp,
                bottom = 26.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item(key = "voice-header") {
                VoiceIosHeader(latestText = latest?.let { "已更新：${formatTime(it)}" } ?: "尚未更新")
            }

            if (state.accounts.isEmpty()) {
                item(key = "voice-empty") {
                    VoiceIosEmptyState()
                }
            } else {
                item(key = "voice-account-stack") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        state.accounts.forEachIndexed { index, account ->
                            VoiceIosAccountCard(
                                account = account,
                                refreshState = state.appState.refreshState(account.id),
                                theme = voiceCardTheme(index),
                                onOpen = { onOpenAccount(account) },
                            )
                        }
                    }
                }
                item(key = "voice-account-count") {
                    Text(
                        "共 ${state.accounts.size} 个号码",
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceIosHeader(latestText: String) {
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
                latestText,
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
private fun VoiceIosEmptyState() {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 40.dp),
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
            Text(
                "☎",
                fontSize = 34.sp,
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

private data class VoiceCardTheme(
    val accent: Color,
    val softAccent: Color,
)

private fun voiceCardTheme(index: Int): VoiceCardTheme = when (index % 4) {
    1 -> VoiceCardTheme(Color(0xFF29AD6B), Color(0xFFD1F5DE))
    2 -> VoiceCardTheme(Color(0xFFFAC714), Color(0xFFFFFAB8))
    3 -> VoiceCardTheme(Color(0xFF8F63F2), Color(0xFFE8DBFF))
    else -> VoiceCardTheme(Color(0xFFFF8F1F), Color(0xFFFFE0B8))
}

@Composable
private fun VoiceIosAccountCard(
    account: UnicomAccount,
    refreshState: RefreshState,
    theme: VoiceCardTheme,
    onOpen: () -> Unit,
) {
    val settings = LocalAppSettings.current
    val shape = RoundedCornerShape(26.dp)
    val mobileText = if (settings.hideMobileMiddleDigits) voiceMaskMobile(account.mobile) else account.mobile
    val attribution = account.displayName.trim().takeIf {
        it.isNotEmpty() && it != account.mobile && it != account.packageName
    }
    val visiblePackages = account.visibleVoicePackages
    val cardSurface = MaterialTheme.colorScheme.surface

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
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), shape)
            .clickable(onClick = onOpen),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            theme.softAccent.copy(alpha = 0.68f),
                            theme.softAccent.copy(alpha = 0.32f),
                            Color.Transparent,
                        ),
                        start = Offset(1200f, 0f),
                        end = Offset(550f, 650f),
                    ),
                ),
        )

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
                .padding(20.dp),
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
                        )
                    }
                }

                if (refreshState is RefreshState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = theme.accent,
                    )
                }

                account.lastUpdatedAt?.let {
                    Text(
                        formatTime(it),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                }

                Text(
                    "›",
                    fontSize = 18.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                )
            }

            if (account.packageName.isNotBlank()) {
                Text(
                    account.packageName,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
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
                        Text(
                            "☎  未识别到语音余量",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "当前联通余量接口没有为此号码返回带“分钟/语音”标记的资源。",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        )
                    }
                }
                visiblePackages.isEmpty() -> {
                    Text(
                        "所有语音权益均已隐藏",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        visiblePackages.forEach { packageValue ->
                            VoiceIosPackageRow(packageValue)
                        }
                    }
                }
            }

            if (refreshState is RefreshState.Failed) {
                Text(
                    "⚠ ${refreshState.message}",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color.Red,
                )
            }
        }
    }
}

@Composable
private fun VoiceIosPackageRow(packageValue: VoicePackage) {
    val tint = Color(0xFF2673FA)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                packageValue.originalName,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            Text(
                voiceTrailingText(packageValue),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                textAlign = TextAlign.End,
                maxLines = 2,
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
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 1,
            )
            Text(
                if (packageValue.isShared) "共享" else "非共享",
                modifier = Modifier
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.10f))
                    .padding(horizontal = 3.dp),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = tint,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            cleanedVoiceEndDate(packageValue.endDateText)?.let { endDate ->
                Text(
                    "有效期至 $endDate",
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    maxLines = 1,
                )
            }
        }

        packageValue.usedFraction?.let { fraction ->
            VoiceIosProgressBar(
                fraction = fraction,
                tint = tint,
            )
        }
    }
}

@Composable
private fun VoiceIosProgressBar(fraction: Double, tint: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.08f)),
    ) {
        val safe = fraction.coerceIn(0.0, 1.0)
        if (safe > 0.0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safe.toFloat())
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(tint.copy(alpha = 0.82f), tint),
                        ),
                    ),
            )
        }
    }
}

private fun voiceTrailingText(packageValue: VoicePackage): String {
    if (packageValue.isUnlimited) return "不限量"
    val remaining = voiceMinuteText(packageValue.remainingMinutes)
    val total = packageValue.totalMinutes
    return if (total != null && total.isFinite()) {
        "剩余 $remaining/共 ${voiceMinuteText(total)}"
    } else {
        "剩余 $remaining"
    }
}

private fun voiceMinuteText(value: Double?): String {
    if (value == null || !value.isFinite()) return "--"
    val safe = value.coerceAtLeast(0.0)
    val rounded = safe.toLong()
    return if (abs(safe - rounded.toDouble()) < 0.005) {
        "$rounded 分钟"
    } else {
        val text = String.format(Locale.US, "%.2f", safe).trimEnd('0').trimEnd('.')
        "$text 分钟"
    }
}

private fun cleanedVoiceEndDate(value: String?): String? {
    var text = value?.trim().orEmpty()
    if (text.isEmpty()) return null
    if (text.endsWith("到期")) text = text.dropLast(2).trim()
    return text.takeIf { it.isNotEmpty() }
}

private fun voiceMaskMobile(value: String): String {
    val digits = value.filter(Char::isDigit)
    if (digits.length < 7) return value
    return digits.replaceRange(3, 7, "****")
}
