package com.clxmhcs.chinaunicom.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.clxmhcs.chinaunicom.core.model.WidgetDualSide
import com.clxmhcs.chinaunicom.core.model.WidgetDualSlotKind
import com.clxmhcs.chinaunicom.data.integral.AndroidIntegralDiskCache
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max

class SingleQuotaGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 110.dp), DpSize(250.dp, 220.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = AndroidWidgetSnapshotStore(context).loadSingle()
        provideContent { SingleQuotaContent(snapshot) }
    }
}

class SingleQuotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleQuotaGlanceWidget()
}

class DualQuotaGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = AndroidWidgetSnapshotStore(context).loadDual()
        val integralCache = AndroidIntegralDiskCache(context)
        val leftIntegral = snapshot?.left?.let { integralCache.load(it.accountID)?.snapshot?.totalAvailable }
        val rightIntegral = snapshot?.right?.let { integralCache.load(it.accountID)?.snapshot?.totalAvailable }
        provideContent { DualQuotaContent(snapshot, leftIntegral, rightIntegral) }
    }
}

class DualQuotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DualQuotaGlanceWidget()
}

class GlanceWidgetUpdateNotifier(private val context: Context) : WidgetUpdateNotifier {
    override suspend fun requestAll() {
        SingleQuotaGlanceWidget().updateAll(context.applicationContext)
        DualQuotaGlanceWidget().updateAll(context.applicationContext)
    }
}

@Composable
private fun SingleQuotaContent(snapshot: WidgetQuotaSnapshot?) {
    val context = LocalContext.current
    val size = LocalSize.current
    val isLarge = size.height >= 180.dp
    Column(
        modifier = GlanceModifier.fillMaxSize().background(ColorProvider(Color.White)).padding(12.dp),
    ) {
        if (snapshot == null) {
            Text("联通余量", style = titleStyle())
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("请先打开 App 添加并刷新号码", modifier = GlanceModifier.defaultWeight(), style = secondaryStyle())
                Text(
                    "刷新",
                    modifier = GlanceModifier.clickable(singleRefreshAction(context)),
                    style = secondaryStyle(),
                )
            }
            return@Column
        }
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(snapshot.displayName.ifBlank { snapshot.mobile }, style = titleStyle())
                Text(snapshot.mobile, style = secondaryStyle())
            }
            Text("${formatTime(snapshot.updatedAt)} 更新", style = secondaryStyle())
            Spacer(GlanceModifier.width(6.dp))
            Text(
                "刷新",
                modifier = GlanceModifier.clickable(singleRefreshAction(context)),
                style = secondaryStyle(),
            )
        }
        Spacer(GlanceModifier.height(7.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Metric("今日", formatToday(snapshot.todayUsageGB), GlanceModifier.defaultWeight())
            Metric("余额", snapshot.balanceYuan?.let { "%.2f元".format(Locale.CHINA, max(0.0, it)) } ?: "--", GlanceModifier.defaultWeight())
        }
        if (isLarge) {
            Spacer(GlanceModifier.height(8.dp))
            val items = snapshot.items.take(6)
            for (row in items.chunked(3)) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    row.forEach { item ->
                        QuotaCell(item, GlanceModifier.defaultWeight())
                    }
                    repeat(3 - row.size) { Spacer(GlanceModifier.defaultWeight()) }
                }
                Spacer(GlanceModifier.height(6.dp))
            }
        } else {
            Spacer(GlanceModifier.height(6.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                snapshot.items.take(3).forEach { item -> QuotaCell(item, GlanceModifier.defaultWeight()) }
            }
        }
    }
}

@Composable
private fun DualQuotaContent(
    snapshot: WidgetDualSnapshot?,
    leftIntegral: Int?,
    rightIntegral: Int?,
) {
    Row(
        modifier = GlanceModifier.fillMaxSize().background(ColorProvider(Color.White)).padding(9.dp),
    ) {
        DualAccountPanel(
            account = snapshot?.left,
            integralValue = leftIntegral,
            side = WidgetDualSide.LEFT,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(8.dp))
        DualAccountPanel(
            account = snapshot?.right,
            integralValue = rightIntegral,
            side = WidgetDualSide.RIGHT,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun DualAccountPanel(
    account: WidgetDualAccountSnapshot?,
    integralValue: Int?,
    side: WidgetDualSide,
    modifier: GlanceModifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        if (account == null) {
            Text("未绑定号码", style = titleStyle())
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("请在 App 设置中配置", modifier = GlanceModifier.defaultWeight(), style = secondaryStyle())
                Text(
                    "刷新",
                    modifier = GlanceModifier.clickable(dualRefreshAction(context, side)),
                    style = tinyStyle(),
                )
            }
            return@Column
        }
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("••••${account.mobileSuffix}", modifier = GlanceModifier.defaultWeight(), style = titleStyle())
            Text(formatTime(account.updatedAt), style = secondaryStyle())
            Spacer(GlanceModifier.width(3.dp))
            Text(
                "刷新",
                modifier = GlanceModifier.clickable(dualRefreshAction(context, side)),
                style = tinyStyle(),
            )
        }
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text("今日 ${formatToday(account.todayUsageGB)}", modifier = GlanceModifier.defaultWeight(), style = secondaryStyle())
            Text("余额 ${account.balanceYuan?.let { "%.2f".format(Locale.CHINA, max(0.0, it)) } ?: "--"}", style = secondaryStyle())
        }
        Spacer(GlanceModifier.height(4.dp))
        val visible = account.items.take(6)
        visible.chunked(3).take(2).forEach { row ->
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                row.forEach { item ->
                    DualCell(item, integralValue, GlanceModifier.defaultWeight())
                }
                repeat(3 - row.size) { Spacer(GlanceModifier.defaultWeight()) }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: GlanceModifier) {
    Column(modifier = modifier) {
        Text(label, style = secondaryStyle())
        Text(value, style = valueStyle())
    }
}

@Composable
private fun QuotaCell(item: WidgetQuotaSnapshotItem, modifier: GlanceModifier) {
    Column(modifier = modifier.padding(end = 3.dp)) {
        Text(item.titleTop, maxLines = 1, style = secondaryStyle())
        val value = when (item.unit) {
            WidgetSnapshotUnit.GIGABYTE -> formatFlow(item.remaining)
            WidgetSnapshotUnit.MINUTE -> "%.0f分".format(Locale.CHINA, max(0.0, item.remaining))
        }
        Text(value, maxLines = 1, style = valueStyle())
    }
}

@Composable
private fun DualCell(item: WidgetDualDashboardItem, integralValue: Int?, modifier: GlanceModifier) {
    Column(modifier = modifier.padding(end = 2.dp)) {
        Text(item.title, maxLines = 1, style = tinyStyle())
        val value = when (item.kind) {
            WidgetDualSlotKind.INTEGRAL -> integralValue?.coerceAtLeast(0)?.toString() ?: "暂无"
            WidgetDualSlotKind.FLOW -> if (item.isUnlimited) "不限量" else item.remaining?.let(::formatFlow) ?: "--"
            WidgetDualSlotKind.VOICE -> item.remaining?.let { "%.0f分".format(Locale.CHINA, max(0.0, it)) } ?: "--"
        }
        Text(value, maxLines = 1, style = tinyValueStyle())
    }
}

private fun singleRefreshAction(context: Context) = actionSendBroadcast(
    action = WidgetRefreshActionContract.ACTION_REFRESH_SINGLE,
    componentName = ComponentName(context.packageName, WidgetRefreshActionContract.RECEIVER_CLASS_NAME),
)

private fun dualRefreshAction(context: Context, side: WidgetDualSide) = actionSendBroadcast(
    action = when (side) {
        WidgetDualSide.LEFT -> WidgetRefreshActionContract.ACTION_REFRESH_DUAL_LEFT
        WidgetDualSide.RIGHT -> WidgetRefreshActionContract.ACTION_REFRESH_DUAL_RIGHT
    },
    componentName = ComponentName(context.packageName, WidgetRefreshActionContract.RECEIVER_CLASS_NAME),
)

private fun formatToday(gb: Double): String {
    if (!gb.isFinite()) return "--"
    val normalized = max(0.0, gb)
    val mb = normalized * 1024.0
    return when {
        mb < 10 -> "%.2fM".format(Locale.CHINA, floor(mb * 100.0) / 100.0)
        mb < 100 -> "%.1fM".format(Locale.CHINA, floor(mb * 10.0) / 10.0)
        mb < 1024 -> "${floor(mb).toInt()}M"
        else -> "%.2fG".format(Locale.CHINA, normalized)
    }
}

private fun formatFlow(gb: Double): String {
    val value = max(0.0, gb)
    return if (value < 1.0) "%.0fM".format(Locale.CHINA, value * 1024.0) else "%.2fG".format(Locale.CHINA, value)
}

private fun formatTime(instant: java.time.Instant): String = TIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()))

private fun titleStyle() = TextStyle(color = ColorProvider(Color(0xFF171717)), fontSize = 14.sp, fontWeight = FontWeight.Bold)
private fun valueStyle() = TextStyle(color = ColorProvider(Color(0xFF171717)), fontSize = 13.sp, fontWeight = FontWeight.Medium)
private fun secondaryStyle() = TextStyle(color = ColorProvider(Color(0xFF666666)), fontSize = 10.sp)
private fun tinyStyle() = TextStyle(color = ColorProvider(Color(0xFF6A6A6A)), fontSize = 8.sp)
private fun tinyValueStyle() = TextStyle(color = ColorProvider(Color(0xFF222222)), fontSize = 9.sp, fontWeight = FontWeight.Medium)

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
