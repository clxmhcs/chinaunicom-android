package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.clxmhcs.chinaunicom.core.design.ChinaUnicomColors
import com.clxmhcs.chinaunicom.core.design.ChinaUnicomDimensions
import com.clxmhcs.chinaunicom.core.design.ChinaUnicomShapes

@Composable
internal fun FlowDashboardPlaceholder() {
    M1FeaturePlaceholder(
        title = "流量",
        message = "M4 网络核心仅用于本地验收；完成 M4-F、M5 与 M6 后，流量首页将在 M7 接入真实账户状态。",
        useFlowGradient = true,
    )
}

@Composable
internal fun VoiceDashboardPlaceholder() {
    M1FeaturePlaceholder(
        title = "语音",
        message = "当前仅保留与 iOS 对应的导航和视觉层级；语音首页将在 M7 与流量刷新状态一起接入。",
        useFlowGradient = true,
    )
}

@Composable
internal fun ComprehensiveBusinessPlaceholder() {
    M1FeaturePlaceholder(
        title = "综合业务",
        message = "余额、余量、账单、已订业务和积分将在后续阶段接入统一数据层。",
    )
}

@Composable
internal fun OtherBusinessPlaceholder() {
    M1FeaturePlaceholder(
        title = "其它业务",
        message = "订单、套餐、资费、彩铃和电子受理单入口将在 M9-M10 迁移。",
    )
}

@Composable
internal fun SettingsPlaceholder() {
    M1FeaturePlaceholder(
        title = "设置",
        message = "账户、刷新策略、Widget 与自动化配置将在对应阶段接入。",
    )
}

@Composable
private fun M1FeaturePlaceholder(
    title: String,
    message: String,
    useFlowGradient: Boolean = false,
) {
    val background = MaterialTheme.colorScheme.background
    val modifier = if (useFlowGradient) {
        Modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    ChinaUnicomColors.FlowDashboardTop,
                    background,
                ),
            ),
        )
    } else {
        Modifier.background(background)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ChinaUnicomDimensions.PageHorizontal,
            top = ChinaUnicomDimensions.PageTop,
            end = ChinaUnicomDimensions.PageHorizontal,
            bottom = ChinaUnicomDimensions.PageBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(ChinaUnicomShapes.AccountCardRadius),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = ChinaUnicomDimensions.AccountCardHorizontal,
                        vertical = ChinaUnicomDimensions.AccountCardVertical,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Android-M1",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
