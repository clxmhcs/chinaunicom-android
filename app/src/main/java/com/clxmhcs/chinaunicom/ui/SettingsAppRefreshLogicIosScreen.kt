package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clxmhcs.chinaunicom.automation.AutomationCoordinator
import com.clxmhcs.chinaunicom.data.balance.AndroidSharedBalanceCacheStores
import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories
import com.clxmhcs.chinaunicom.data.settings.BalanceRefreshIntervalSynchronizer
import com.clxmhcs.chinaunicom.data.settings.BalanceRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.CachedBusinessEntryMode
import com.clxmhcs.chinaunicom.data.settings.IntegralRefreshCycleMode
import com.clxmhcs.chinaunicom.data.settings.IntegralRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.MyPackageRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.OrderRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.OrderedBusinessRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.PageEntryRefreshMode
import com.clxmhcs.chinaunicom.data.settings.PhoneBillRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.RebateGiftRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.VideoRingRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.WidgetRefreshPolicy

private val RefreshPageBackground = Color(0xFFF2F2F7)
private val RefreshSecondary = Color(0xFF8E8E93)
private val RefreshSeparator = Color(0xFFE5E5EA)
private val RefreshTertiary = Color(0xFFC7C7CC)
private val RefreshAccent = Color(0xFF3478F6)
private val RefreshDestructive = Color(0xFFFF453A)
private val RefreshOrange = Color(0xFFFF9500)

private enum class AppRefreshEditorPage {
    OVERVIEW,
    QUOTA,
    BALANCE,
    ORDERED,
    VIDEO_RING,
    RECEIPT,
    ORDERS,
    PACKAGE,
    PHONE_BILL,
    REBATE_GIFT,
    INTEGRAL,
    WIDGET,
    SHORTCUT,
}

private enum class RefreshGlyph {
    SLIDERS,
    QUOTA,
    BALANCE,
    ORDERED,
    VIDEO_RING,
    RECEIPT,
    ORDERS,
    PACKAGE,
    PHONE_BILL,
    GIFT,
    INTEGRAL,
    WIDGET,
    SHORTCUT,
    RESTORE,
}

private data class AppRefreshDraft(
    val quota: QuotaRefreshPolicy,
    val balance: BalanceRefreshPolicy,
    val ordered: OrderedBusinessRefreshPolicy,
    val videoRing: VideoRingRefreshPolicy,
    val orders: OrderRefreshPolicy,
    val myPackage: MyPackageRefreshPolicy,
    val phoneBill: PhoneBillRefreshPolicy,
    val rebateGift: RebateGiftRefreshPolicy,
    val integral: IntegralRefreshPolicy,
    val widget: WidgetRefreshPolicy,
) {
    companion object {
        fun defaults() = AppRefreshDraft(
            quota = QuotaRefreshPolicy(),
            balance = BalanceRefreshPolicy(),
            ordered = OrderedBusinessRefreshPolicy(),
            videoRing = VideoRingRefreshPolicy(),
            orders = OrderRefreshPolicy(),
            myPackage = MyPackageRefreshPolicy(),
            phoneBill = PhoneBillRefreshPolicy(),
            rebateGift = RebateGiftRefreshPolicy(),
            integral = IntegralRefreshPolicy(),
            widget = WidgetRefreshPolicy(),
        )
    }
}

/** iOS-source visual/interaction parity for `AppRefreshLogicSettingsView`. */
@Composable
fun AppRefreshLogicSettingsScreen(
    settingsViewModel: SettingsRootViewModel,
    onBack: () -> Unit,
) {
    val quota by settingsViewModel.quotaRefreshPolicy.collectAsState()
    val balance by settingsViewModel.balanceRefreshPolicy.collectAsState()
    val ordered by settingsViewModel.orderedBusinessRefreshPolicy.collectAsState()
    val videoRing by settingsViewModel.videoRingRefreshPolicy.collectAsState()
    val orders by settingsViewModel.orderRefreshPolicy.collectAsState()
    val myPackage by settingsViewModel.myPackageRefreshPolicy.collectAsState()
    val phoneBill by settingsViewModel.phoneBillRefreshPolicy.collectAsState()
    val rebateGift by settingsViewModel.rebateGiftRefreshPolicy.collectAsState()
    val integral by settingsViewModel.integralRefreshPolicy.collectAsState()
    val context = LocalContext.current.applicationContext
    val refreshRepository = remember(context) {
        AndroidSettingsRepositories.refreshLogic(
            context,
            BalanceRefreshIntervalSynchronizer(
                AndroidSharedBalanceCacheStores.create(context)::setRefreshIntervalMinutes,
            ),
        )
    }
    val initialWidget = remember(refreshRepository) { refreshRepository.loadWidgetRefreshPolicy() }
    val initialDraft = remember {
        AppRefreshDraft(
            quota = quota,
            balance = balance,
            ordered = ordered,
            videoRing = videoRing,
            orders = orders,
            myPackage = myPackage,
            phoneBill = phoneBill,
            rebateGift = rebateGift,
            integral = integral,
            widget = initialWidget,
        )
    }
    var saved by remember { mutableStateOf(initialDraft) }
    var draft by remember { mutableStateOf(initialDraft) }
    var page by remember { mutableStateOf(AppRefreshEditorPage.OVERVIEW) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var showSavedConfirmation by remember { mutableStateOf(false) }

    fun saveCurrent(value: AppRefreshDraft): Boolean {
        val results = listOf(
            refreshRepository.saveQuotaRefreshPolicy(value.quota).persisted,
            refreshRepository.saveBalanceRefreshPolicy(value.balance).persisted,
            refreshRepository.saveOrderedBusinessRefreshPolicy(value.ordered).persisted,
            refreshRepository.saveVideoRingRefreshPolicy(value.videoRing).persisted,
            refreshRepository.saveOrderRefreshPolicy(value.orders).persisted,
            refreshRepository.saveMyPackageRefreshPolicy(value.myPackage).persisted,
            refreshRepository.savePhoneBillRefreshPolicy(value.phoneBill).persisted,
            refreshRepository.saveRebateGiftRefreshPolicy(value.rebateGift).persisted,
            refreshRepository.saveIntegralRefreshPolicy(value.integral).persisted,
            refreshRepository.saveWidgetRefreshPolicy(value.widget).persisted,
        )
        if (results.all { it }) {
            AutomationCoordinator.synchronize(context, replaceExisting = true)
            settingsViewModel.reloadAfterMaintenance()
            return true
        }
        return false
    }

    val save: () -> Unit = {
        if (draft != saved && saveCurrent(draft)) {
            saved = draft
            showSavedConfirmation = true
        }
    }

    when (page) {
        AppRefreshEditorPage.OVERVIEW -> AppRefreshOverview(
            draft = draft,
            hasUnsavedChanges = draft != saved,
            onBack = onBack,
            onSave = save,
            onOpen = { page = it },
            onDiscard = { draft = saved },
            onRestore = { showRestoreConfirmation = true },
        )
        AppRefreshEditorPage.QUOTA -> QuotaRefreshDraftEditor(
            policy = draft.quota,
            onChange = { draft = draft.copy(quota = it) },
            onBack = { page = AppRefreshEditorPage.OVERVIEW },
            onSave = save,
            hasUnsavedChanges = draft != saved,
        )
        AppRefreshEditorPage.BALANCE -> BalanceRefreshDraftEditor(
            policy = draft.balance,
            onChange = { draft = draft.copy(balance = it) },
            onBack = { page = AppRefreshEditorPage.OVERVIEW },
            onSave = save,
            hasUnsavedChanges = draft != saved,
        )
        AppRefreshEditorPage.ORDERED -> OrderedRefreshDraftEditor(
            policy = draft.ordered,
            onChange = { draft = draft.copy(ordered = it) },
            onBack = { page = AppRefreshEditorPage.OVERVIEW },
            onSave = save,
            hasUnsavedChanges = draft != saved,
        )
        AppRefreshEditorPage.VIDEO_RING -> VideoRingRefreshDraftEditor(
            policy = draft.videoRing,
            onChange = { draft = draft.copy(videoRing = it) },
            onBack = { page = AppRefreshEditorPage.OVERVIEW },
            onSave = save,
            hasUnsavedChanges = draft != saved,
        )
        AppRefreshEditorPage.RECEIPT -> ReceiptRefreshInfoPage(
            onBack = { page = AppRefreshEditorPage.OVERVIEW },
        )
        AppRefreshEditorPage.ORDERS -> OrdersRefreshDraftEditor(
            policy = draft.orders,
            onChange = { draft = draft.copy(orders = it) },
            onBack = { page = AppRefreshEditorPage.OVERVIEW },
            onSave = save,
            hasUnsavedChanges = draft != saved,
        )
        AppRefreshEditorPage.PACKAGE -> PackageRefreshDraftEditor(
            policy = draft.myPackage,
            onChange = { draft = draft.copy(myPackage = it) },
            onBack = { page = AppRefreshEditorPage.OVERVIEW },
            onSave = save,
            hasUnsavedChanges = draft != saved,
        )
        AppRefreshEditorPage.PHONE_BILL -> PhoneBillRefreshDraftEditor(
            policy = draft.phoneBill,
            onChange = { draft = draft.copy(phoneBill = it) },
            onBack = { page = AppRefreshEditorPage.OVERVIEW },
            onSave = save,
            hasUnsavedChanges = draft != saved,
        )
        AppRefreshEditorPage.REBATE_GIFT -> RebateGiftRefreshDraftEditor(
            policy = draft.rebateGift,
            onChange = { draft = draft.copy(rebateGift = it) },
            onBack = { page = AppRefreshEditorPage.OVERVIEW },
            onSave = save,
            hasUnsavedChanges = draft != saved,
        )
        AppRefreshEditorPage.INTEGRAL -> IntegralRefreshDraftEditor(
            policy = draft.integral,
            onChange = { draft = draft.copy(integral = it) },
            onBack = { page = AppRefreshEditorPage.OVERVIEW },
            onSave = save,
            hasUnsavedChanges = draft != saved,
        )
        AppRefreshEditorPage.WIDGET -> WidgetRefreshDraftEditor(
            policy = draft.widget,
            onChange = { draft = draft.copy(widget = it.normalized()) },
            onBack = { page = AppRefreshEditorPage.OVERVIEW },
            onSave = save,
            hasUnsavedChanges = draft != saved,
        )
        AppRefreshEditorPage.SHORTCUT -> ShortcutRefreshInfoPage(
            onBack = { page = AppRefreshEditorPage.OVERVIEW },
        )
    }

    if (showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmation = false },
            title = { Text("恢复默认刷新逻辑？") },
            text = { Text("所有刷新策略编辑项会恢复为当前 App 已确认的默认逻辑。") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirmation = false
                    val defaults = AppRefreshDraft.defaults()
                    if (saveCurrent(defaults)) {
                        draft = defaults
                        saved = defaults
                    }
                }) { Text("恢复", color = RefreshDestructive) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmation = false }) { Text("取消") }
            },
        )
    }

    if (showSavedConfirmation) {
        AlertDialog(
            onDismissRequest = { showSavedConfirmation = false },
            title = { Text("已保存") },
            text = { Text("刷新策略已保存。各业务只响应自己的设置变化；进入页面、前台检查、计划时间等规则会在各自对应触发点执行。") },
            confirmButton = {
                TextButton(onClick = { showSavedConfirmation = false }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun AppRefreshOverview(
    draft: AppRefreshDraft,
    hasUnsavedChanges: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpen: (AppRefreshEditorPage) -> Unit,
    onDiscard: () -> Unit,
    onRestore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(RefreshPageBackground),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            RefreshPageHeader("App 刷新逻辑", onBack, onSave, hasUnsavedChanges)
            Spacer(Modifier.height(28.dp))
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
                    RefreshGlyphView(RefreshGlyph.SLIDERS, 28.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("刷新逻辑统一配置", fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "刷新策略保存后，各业务只响应自己的设置变化；当前打开且支持即时重算的页面会重新判定，其余规则在下一次对应触发点生效。",
                            color = RefreshSecondary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        item { RefreshSectionTitle("首页与基础数据") }
        item {
            RefreshRowsCard(
                listOf(
                    RefreshRowModel(RefreshGlyph.QUOTA, "首页余量", quotaSummary(draft.quota), AppRefreshEditorPage.QUOTA),
                    RefreshRowModel(RefreshGlyph.BALANCE, "余额", balanceSummary(draft.balance), AppRefreshEditorPage.BALANCE),
                ),
                onOpen,
            )
            Spacer(Modifier.height(28.dp))
        }

        item { RefreshSectionTitle("业务页面") }
        item {
            RefreshRowsCard(
                listOf(
                    RefreshRowModel(RefreshGlyph.ORDERED, "已订业务", cachedModeTitleForRefresh(draft.ordered.entryMode), AppRefreshEditorPage.ORDERED),
                    RefreshRowModel(RefreshGlyph.VIDEO_RING, "视频彩铃", pageModeTitleForRefresh(draft.videoRing.entryMode), AppRefreshEditorPage.VIDEO_RING),
                    RefreshRowModel(RefreshGlyph.RECEIPT, "电子受理单", "当月 1小时", AppRefreshEditorPage.RECEIPT),
                    RefreshRowModel(RefreshGlyph.ORDERS, "我的订单", if (draft.orders.refreshOnEntry) "进入时刷新" else "仅手动", AppRefreshEditorPage.ORDERS),
                    RefreshRowModel(RefreshGlyph.PACKAGE, "我的套餐", pageModeTitleForRefresh(draft.myPackage.entryMode), AppRefreshEditorPage.PACKAGE),
                    RefreshRowModel(RefreshGlyph.PHONE_BILL, "话费账单", "当月 ${minutesText(draft.phoneBill.currentMonthCacheMinutes)}", AppRefreshEditorPage.PHONE_BILL),
                    RefreshRowModel(RefreshGlyph.GIFT, "返费与赠费", rebateGiftSummary(draft.rebateGift), AppRefreshEditorPage.REBATE_GIFT),
                    RefreshRowModel(RefreshGlyph.INTEGRAL, "积分", integralSummary(draft.integral), AppRefreshEditorPage.INTEGRAL),
                ),
                onOpen,
            )
            Spacer(Modifier.height(28.dp))
        }

        item { RefreshSectionTitle("系统扩展") }
        item {
            RefreshRowsCard(
                listOf(
                    RefreshRowModel(RefreshGlyph.WIDGET, "桌面组件", widgetSummary(draft.widget), AppRefreshEditorPage.WIDGET),
                    RefreshRowModel(RefreshGlyph.SHORTCUT, "快捷指令", "执行时间由系统自动化控制", AppRefreshEditorPage.SHORTCUT),
                ),
                onOpen,
            )
            Spacer(Modifier.height(28.dp))
        }

        if (hasUnsavedChanges) {
            item {
                Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(22.dp)) {
                    Column {
                        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("!", color = RefreshOrange, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            Text("存在未保存的刷新逻辑修改", color = RefreshOrange, fontSize = 15.sp)
                        }
                        Divider(color = RefreshSeparator)
                        TextButton(modifier = Modifier.fillMaxWidth(), onClick = onDiscard) {
                            Text("放弃未保存修改")
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onRestore),
                color = Color.White,
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RefreshGlyphView(RefreshGlyph.RESTORE, 26.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("恢复默认刷新逻辑", color = RefreshDestructive, fontSize = 17.sp)
                }
            }
            Text(
                "恢复默认只重置本页刷新策略，不删除账号、凭据、账单、积分、套餐或其它缓存数据。",
                color = RefreshSecondary,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
    }
}

private data class RefreshRowModel(
    val glyph: RefreshGlyph,
    val title: String,
    val detail: String,
    val page: AppRefreshEditorPage,
)

@Composable
private fun RefreshRowsCard(rows: List<RefreshRowModel>, onOpen: (AppRefreshEditorPage) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(24.dp)) {
        Column {
            rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(66.dp).clickable { onOpen(row.page) }.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RefreshGlyphView(row.glyph, 27.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(row.title, color = Color.Black, fontSize = 17.sp, lineHeight = 21.sp)
                        Text(row.detail, color = RefreshSecondary, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1)
                    }
                    RefreshChevron()
                }
                if (index != rows.lastIndex) {
                    Divider(modifier = Modifier.padding(start = 58.dp, end = 18.dp), color = RefreshSeparator, thickness = 0.7.dp)
                }
            }
        }
    }
}

@Composable
private fun RefreshSectionTitle(title: String) {
    Text(
        title,
        color = RefreshSecondary,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 18.dp, bottom = 9.dp),
    )
}

@Composable
private fun RefreshPageHeader(
    title: String,
    onBack: () -> Unit,
    onSave: (() -> Unit)? = null,
    saveEnabled: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = onBack, modifier = Modifier.size(44.dp), shape = CircleShape, color = Color.White) {
            Box(contentAlignment = Alignment.Center) { RefreshBackChevron() }
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = Color.Black,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (onSave != null) {
            Surface(
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier.width(68.dp).height(44.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("保存", color = if (saveEnabled) RefreshAccent else RefreshTertiary, fontSize = 16.sp)
                }
            }
        } else {
            Spacer(Modifier.width(44.dp))
        }
    }
}

@Composable
private fun RefreshBackChevron() {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 2.7.dp.toPx()
        drawLine(Color(0xFF1C1C1E), androidx.compose.ui.geometry.Offset(size.width * .62f, size.height * .18f), androidx.compose.ui.geometry.Offset(size.width * .30f, size.height * .50f), stroke, StrokeCap.Round)
        drawLine(Color(0xFF1C1C1E), androidx.compose.ui.geometry.Offset(size.width * .30f, size.height * .50f), androidx.compose.ui.geometry.Offset(size.width * .62f, size.height * .82f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun RefreshChevron() {
    Canvas(Modifier.size(18.dp)) {
        val stroke = 2.dp.toPx()
        drawLine(RefreshTertiary, androidx.compose.ui.geometry.Offset(size.width * .38f, size.height * .26f), androidx.compose.ui.geometry.Offset(size.width * .62f, size.height * .50f), stroke, StrokeCap.Round)
        drawLine(RefreshTertiary, androidx.compose.ui.geometry.Offset(size.width * .62f, size.height * .50f), androidx.compose.ui.geometry.Offset(size.width * .38f, size.height * .74f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun RefreshGlyphView(kind: RefreshGlyph, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val c = RefreshAccent
        val w = size.toPx()
        val h = size.toPx()
        val stroke = 1.8.dp.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(c, androidx.compose.ui.geometry.Offset(w * x1, h * y1), androidx.compose.ui.geometry.Offset(w * x2, h * y2), stroke, StrokeCap.Round)
        fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = false) {
            if (fill) drawCircle(c, w * r, androidx.compose.ui.geometry.Offset(w * cx, h * cy))
            else drawCircle(c, w * r, androidx.compose.ui.geometry.Offset(w * cx, h * cy), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        }
        when (kind) {
            RefreshGlyph.SLIDERS -> {
                line(.10f,.28f,.90f,.28f); line(.10f,.50f,.90f,.50f); line(.10f,.72f,.90f,.72f)
                circle(.62f,.28f,.07f); circle(.36f,.50f,.07f); circle(.70f,.72f,.07f)
            }
            RefreshGlyph.QUOTA -> {
                val bars = listOf(.25f to .45f, .42f to .28f, .59f to .18f, .76f to .36f)
                bars.forEach { (x, top) -> line(x, .82f, x, top) }; line(.15f,.83f,.86f,.83f)
            }
            RefreshGlyph.BALANCE -> { circle(.50f,.50f,.39f); line(.34f,.31f,.50f,.48f); line(.66f,.31f,.50f,.48f); line(.50f,.48f,.50f,.70f); line(.36f,.51f,.64f,.51f) }
            RefreshGlyph.ORDERED -> { circle(.23f,.33f,.08f); line(.18f,.33f,.22f,.38f); line(.22f,.38f,.29f,.27f); line(.42f,.31f,.88f,.31f); circle(.23f,.68f,.08f); line(.42f,.68f,.88f,.68f) }
            RefreshGlyph.VIDEO_RING -> { line(.15f,.30f,.83f,.30f); line(.15f,.30f,.15f,.73f); line(.15f,.73f,.83f,.73f); line(.83f,.30f,.83f,.73f); circle(.51f,.52f,.08f); line(.58f,.32f,.58f,.50f); line(.58f,.32f,.70f,.29f) }
            RefreshGlyph.RECEIPT -> { line(.28f,.14f,.68f,.14f); line(.68f,.14f,.82f,.28f); line(.82f,.28f,.82f,.86f); line(.82f,.86f,.28f,.86f); line(.28f,.86f,.28f,.14f); line(.68f,.14f,.68f,.30f); line(.68f,.30f,.82f,.30f); line(.40f,.48f,.70f,.48f); line(.40f,.62f,.70f,.62f) }
            RefreshGlyph.ORDERS -> { line(.22f,.28f,.50f,.14f); line(.50f,.14f,.78f,.28f); line(.22f,.28f,.50f,.44f); line(.78f,.28f,.50f,.44f); line(.22f,.28f,.22f,.66f); line(.78f,.28f,.78f,.66f); line(.22f,.66f,.50f,.84f); line(.78f,.66f,.50f,.84f); line(.50f,.44f,.50f,.84f) }
            RefreshGlyph.PACKAGE -> { line(.18f,.32f,.50f,.16f); line(.50f,.16f,.82f,.32f); line(.18f,.32f,.50f,.49f); line(.82f,.32f,.50f,.49f); line(.18f,.50f,.50f,.67f); line(.82f,.50f,.50f,.67f); line(.18f,.68f,.50f,.84f); line(.82f,.68f,.50f,.84f) }
            RefreshGlyph.PHONE_BILL -> { line(.25f,.14f,.75f,.14f); line(.75f,.14f,.75f,.86f); line(.75f,.86f,.25f,.86f); line(.25f,.86f,.25f,.14f); circle(.36f,.36f,.025f,true); circle(.36f,.51f,.025f,true); circle(.36f,.66f,.025f,true); line(.46f,.36f,.65f,.36f); line(.46f,.51f,.65f,.51f); line(.46f,.66f,.65f,.66f) }
            RefreshGlyph.GIFT -> { line(.18f,.40f,.82f,.40f); line(.23f,.40f,.23f,.84f); line(.77f,.40f,.77f,.84f); line(.23f,.84f,.77f,.84f); line(.50f,.28f,.50f,.84f); line(.16f,.28f,.84f,.28f); circle(.39f,.20f,.10f); circle(.61f,.20f,.10f) }
            RefreshGlyph.INTEGRAL -> { circle(.50f,.50f,.39f); val pts = listOf(.50f to .23f,.58f to .41f,.78f to .42f,.62f to .55f,.67f to .75f,.50f to .64f,.33f to .75f,.38f to .55f,.22f to .42f,.42f to .41f,.50f to .23f); pts.zipWithNext().forEach { (a,b) -> line(a.first,a.second,b.first,b.second) } }
            RefreshGlyph.WIDGET -> { listOf(.18f to .18f,.55f to .18f,.18f to .55f,.55f to .55f).forEach { (x,y) -> line(x,y,x+.27f,y); line(x,y,x,y+.27f); line(x+.27f,y,x+.27f,y+.27f); line(x,y+.27f,x+.27f,y+.27f) } }
            RefreshGlyph.SHORTCUT -> { line(.30f,.20f,.30f,.80f); line(.70f,.20f,.70f,.80f); line(.18f,.40f,.82f,.40f); line(.18f,.60f,.82f,.60f); circle(.30f,.40f,.09f); circle(.70f,.60f,.09f) }
            RefreshGlyph.RESTORE -> { line(.28f,.24f,.15f,.24f); line(.15f,.24f,.15f,.11f); val path = androidx.compose.ui.graphics.Path(); path.moveTo(w*.18f,h*.25f); path.cubicTo(w*.38f,h*.05f,w*.76f,h*.12f,w*.82f,h*.43f); path.cubicTo(w*.90f,h*.78f,w*.52f,h*.94f,w*.28f,h*.76f); drawPath(path,c,style=androidx.compose.ui.graphics.drawscope.Stroke(stroke,cap=StrokeCap.Round)) }
        }
    }
}

@Composable
private fun RefreshDetailScaffold(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(RefreshPageBackground),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { RefreshPageHeader(title, onBack, onSave, saveEnabled); Spacer(Modifier.height(14.dp)) }
        item { content() }
    }
}

@Composable
private fun DraftCard(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(24.dp)) {
        Column { content() }
    }
}

@Composable
private fun DraftSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = RefreshAccent),
        )
    }
}

@Composable
private fun DraftNumberRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) { Text(label, fontSize = 16.sp); Text(value, color = RefreshSecondary, fontSize = 12.sp) }
        TextButton(onClick = onMinus) { Text("−") }
        TextButton(onClick = onPlus) { Text("+") }
    }
}

@Composable
private fun DraftChoiceRow(label: String, value: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(58.dp).clickable(onClick = onClick).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp)
        Text(value, color = RefreshSecondary, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp)); RefreshChevron()
    }
}

@Composable private fun CardDivider() = Divider(modifier = Modifier.padding(horizontal = 18.dp), color = RefreshSeparator, thickness = .7.dp)

@Composable
private fun QuotaRefreshDraftEditor(policy: QuotaRefreshPolicy, onChange: (QuotaRefreshPolicy) -> Unit, onBack: () -> Unit, onSave: () -> Unit, hasUnsavedChanges: Boolean) =
    RefreshDetailScaffold("首页余量", onBack, onSave, hasUnsavedChanges) {
        DraftCard {
            DraftSwitchRow("启用自动刷新", policy.automaticRefreshEnabled) { onChange(policy.copy(automaticRefreshEnabled = it)) }; CardDivider()
            DraftSwitchRow("App 冷启动时检查", policy.refreshOnColdLaunch) { onChange(policy.copy(refreshOnColdLaunch = it)) }; CardDivider()
            DraftSwitchRow("重新进入前台时检查", policy.refreshOnForeground) { onChange(policy.copy(refreshOnForeground = it)) }; CardDivider()
            DraftNumberRow("最短刷新间隔", minutesText(policy.minimumIntervalMinutes), { onChange(policy.copy(minimumIntervalMinutes = (policy.minimumIntervalMinutes - 30).coerceAtLeast(30))) }, { onChange(policy.copy(minimumIntervalMinutes = (policy.minimumIntervalMinutes + 30).coerceAtMost(180))) }); CardDivider()
            DraftNumberRow("账号刷新间隔", "${policy.accountGapSeconds}秒", { onChange(policy.copy(accountGapSeconds = (policy.accountGapSeconds - 1).coerceAtLeast(0))) }, { onChange(policy.copy(accountGapSeconds = (policy.accountGapSeconds + 1).coerceAtMost(60))) })
        }
    }

@Composable
private fun BalanceRefreshDraftEditor(policy: BalanceRefreshPolicy, onChange: (BalanceRefreshPolicy) -> Unit, onBack: () -> Unit, onSave: () -> Unit, hasUnsavedChanges: Boolean) =
    RefreshDetailScaffold("余额", onBack, onSave, hasUnsavedChanges) {
        DraftCard {
            DraftSwitchRow("启用自动刷新", policy.automaticRefreshEnabled) { onChange(policy.copy(automaticRefreshEnabled = it)) }; CardDivider()
            DraftSwitchRow("重新进入前台时检查", policy.checkOnForeground) { onChange(policy.copy(checkOnForeground = it)) }; CardDivider()
            DraftNumberRow("余额缓存时间", minutesText(policy.intervalMinutes), { onChange(policy.copy(intervalMinutes = (policy.intervalMinutes - 15).coerceAtLeast(1))) }, { onChange(policy.copy(intervalMinutes = (policy.intervalMinutes + 15).coerceAtMost(1440))) }); CardDivider()
            DraftNumberRow("失败后重试间隔", minutesText(policy.failureRetryMinutes), { onChange(policy.copy(failureRetryMinutes = (policy.failureRetryMinutes - 5).coerceAtLeast(1))) }, { onChange(policy.copy(failureRetryMinutes = (policy.failureRetryMinutes + 5).coerceAtMost(1440))) })
        }
    }

@Composable
private fun OrderedRefreshDraftEditor(policy: OrderedBusinessRefreshPolicy, onChange: (OrderedBusinessRefreshPolicy) -> Unit, onBack: () -> Unit, onSave: () -> Unit, hasUnsavedChanges: Boolean) =
    RefreshDetailScaffold("已订业务", onBack, onSave, hasUnsavedChanges) {
        DraftCard {
            DraftChoiceRow("进入页面策略", cachedModeTitleForRefresh(policy.entryMode)) { val values = CachedBusinessEntryMode.entries; onChange(policy.copy(entryMode = values[(values.indexOf(policy.entryMode)+1)%values.size])) }; CardDivider()
            DraftNumberRow("缓存有效期", "${policy.cacheValidityHours}小时", { onChange(policy.copy(cacheValidityHours = (policy.cacheValidityHours - 1).coerceAtLeast(1))) }, { onChange(policy.copy(cacheValidityHours = (policy.cacheValidityHours + 1).coerceAtMost(720))) }); CardDivider()
            DraftSwitchRow("无缓存时自动查询", policy.noCacheAutoQuery) { onChange(policy.copy(noCacheAutoQuery = it)) }; CardDivider()
            DraftNumberRow("刷新全部号码间隔", "${policy.refreshAllAccountGapSeconds}秒", { onChange(policy.copy(refreshAllAccountGapSeconds = (policy.refreshAllAccountGapSeconds - 1).coerceAtLeast(0))) }, { onChange(policy.copy(refreshAllAccountGapSeconds = (policy.refreshAllAccountGapSeconds + 1).coerceAtMost(60))) })
        }
    }

@Composable
private fun VideoRingRefreshDraftEditor(policy: VideoRingRefreshPolicy, onChange: (VideoRingRefreshPolicy) -> Unit, onBack: () -> Unit, onSave: () -> Unit, hasUnsavedChanges: Boolean) =
    RefreshDetailScaffold("视频彩铃", onBack, onSave, hasUnsavedChanges) {
        DraftCard {
            DraftChoiceRow("进入页面策略", pageModeTitleForRefresh(policy.entryMode)) { val values = PageEntryRefreshMode.entries; onChange(policy.copy(entryMode = values[(values.indexOf(policy.entryMode)+1)%values.size])) }; CardDivider()
            DraftNumberRow("缓存有效期", minutesText(policy.cacheValidityMinutes), { onChange(policy.copy(cacheValidityMinutes = (policy.cacheValidityMinutes - 5).coerceAtLeast(1))) }, { onChange(policy.copy(cacheValidityMinutes = (policy.cacheValidityMinutes + 5).coerceAtMost(1440))) })
        }
    }

@Composable
private fun ReceiptRefreshInfoPage(onBack: () -> Unit) = RefreshDetailScaffold("电子受理单", onBack, {}, false) {
    DraftCard { Text("电子受理单当前沿用已经闭环的 M10 缓存与页面刷新逻辑；Android 现有 schema-3 尚未暴露独立电子受理单编辑域。", color = RefreshSecondary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(18.dp)) }
}

@Composable
private fun OrdersRefreshDraftEditor(policy: OrderRefreshPolicy, onChange: (OrderRefreshPolicy) -> Unit, onBack: () -> Unit, onSave: () -> Unit, hasUnsavedChanges: Boolean) =
    RefreshDetailScaffold("我的订单", onBack, onSave, hasUnsavedChanges) { DraftCard { DraftSwitchRow("进入页面自动刷新", policy.refreshOnEntry) { onChange(policy.copy(refreshOnEntry = it)) } } }

@Composable
private fun PackageRefreshDraftEditor(policy: MyPackageRefreshPolicy, onChange: (MyPackageRefreshPolicy) -> Unit, onBack: () -> Unit, onSave: () -> Unit, hasUnsavedChanges: Boolean) =
    RefreshDetailScaffold("我的套餐", onBack, onSave, hasUnsavedChanges) {
        DraftCard {
            DraftChoiceRow("进入页面策略", pageModeTitleForRefresh(policy.entryMode)) { val values = PageEntryRefreshMode.entries; onChange(policy.copy(entryMode = values[(values.indexOf(policy.entryMode)+1)%values.size])) }; CardDivider()
            DraftNumberRow("缓存有效期", minutesText(policy.cacheValidityMinutes), { onChange(policy.copy(cacheValidityMinutes = (policy.cacheValidityMinutes - 5).coerceAtLeast(1))) }, { onChange(policy.copy(cacheValidityMinutes = (policy.cacheValidityMinutes + 5).coerceAtMost(1440))) })
        }
    }

@Composable
private fun PhoneBillRefreshDraftEditor(policy: PhoneBillRefreshPolicy, onChange: (PhoneBillRefreshPolicy) -> Unit, onBack: () -> Unit, onSave: () -> Unit, hasUnsavedChanges: Boolean) =
    RefreshDetailScaffold("话费账单", onBack, onSave, hasUnsavedChanges) {
        DraftCard {
            DraftNumberRow("本月缓存", minutesText(policy.currentMonthCacheMinutes), { onChange(policy.copy(currentMonthCacheMinutes = (policy.currentMonthCacheMinutes - 5).coerceAtLeast(1))) }, { onChange(policy.copy(currentMonthCacheMinutes = (policy.currentMonthCacheMinutes + 5).coerceAtMost(1440))) }); CardDivider()
            DraftNumberRow("历史月份缓存", "${policy.historicalCacheDays}天", { onChange(policy.copy(historicalCacheDays = (policy.historicalCacheDays - 1).coerceAtLeast(1))) }, { onChange(policy.copy(historicalCacheDays = (policy.historicalCacheDays + 1).coerceAtMost(365))) }); CardDivider()
            DraftNumberRow("每月复查日期", "${policy.monthlyRecheckDay}日", { onChange(policy.copy(monthlyRecheckDay = (policy.monthlyRecheckDay - 1).coerceAtLeast(1))) }, { onChange(policy.copy(monthlyRecheckDay = (policy.monthlyRecheckDay + 1).coerceAtMost(28))) }); CardDivider()
            DraftNumberRow("每月复查小时", "%02d:00".format(policy.monthlyRecheckHour), { onChange(policy.copy(monthlyRecheckHour = (policy.monthlyRecheckHour - 1).coerceAtLeast(0))) }, { onChange(policy.copy(monthlyRecheckHour = (policy.monthlyRecheckHour + 1).coerceAtMost(23))) })
        }
    }

@Composable
private fun RebateGiftRefreshDraftEditor(policy: RebateGiftRefreshPolicy, onChange: (RebateGiftRefreshPolicy) -> Unit, onBack: () -> Unit, onSave: () -> Unit, hasUnsavedChanges: Boolean) =
    RefreshDetailScaffold("返费与赠费", onBack, onSave, hasUnsavedChanges) {
        DraftCard {
            DraftSwitchRow("启用自动刷新", policy.automaticRefreshEnabled) { onChange(policy.copy(automaticRefreshEnabled = it)) }; CardDivider()
            DraftNumberRow("每月刷新日期", "${policy.monthlyRefreshDay}日", { onChange(policy.copy(monthlyRefreshDay = (policy.monthlyRefreshDay - 1).coerceAtLeast(1))) }, { onChange(policy.copy(monthlyRefreshDay = (policy.monthlyRefreshDay + 1).coerceAtMost(28))) }); CardDivider()
            DraftNumberRow("每月刷新小时", "%02d:00".format(policy.monthlyRefreshHour), { onChange(policy.copy(monthlyRefreshHour = (policy.monthlyRefreshHour - 1).coerceAtLeast(0))) }, { onChange(policy.copy(monthlyRefreshHour = (policy.monthlyRefreshHour + 1).coerceAtMost(23))) }); CardDivider()
            DraftSwitchRow("无缓存时立即查询", policy.queryImmediatelyWhenNoCache) { onChange(policy.copy(queryImmediatelyWhenNoCache = it)) }
        }
    }

@Composable
private fun IntegralRefreshDraftEditor(policy: IntegralRefreshPolicy, onChange: (IntegralRefreshPolicy) -> Unit, onBack: () -> Unit, onSave: () -> Unit, hasUnsavedChanges: Boolean) =
    RefreshDetailScaffold("积分", onBack, onSave, hasUnsavedChanges) {
        DraftCard {
            DraftSwitchRow("启用自动刷新", policy.automaticRefreshEnabled) { onChange(policy.copy(automaticRefreshEnabled = it)) }; CardDivider()
            DraftChoiceRow("刷新周期", integralModeTitleForRefresh(policy.cycleMode)) { val values = IntegralRefreshCycleMode.entries; onChange(policy.copy(cycleMode = values[(values.indexOf(policy.cycleMode)+1)%values.size])) }; CardDivider()
            DraftNumberRow("每月刷新日期", "${policy.monthlyRefreshDay}日", { onChange(policy.copy(monthlyRefreshDay = (policy.monthlyRefreshDay - 1).coerceAtLeast(1))) }, { onChange(policy.copy(monthlyRefreshDay = (policy.monthlyRefreshDay + 1).coerceAtMost(28))) }); CardDivider()
            DraftNumberRow("每月刷新小时", "%02d:00".format(policy.monthlyRefreshHour), { onChange(policy.copy(monthlyRefreshHour = (policy.monthlyRefreshHour - 1).coerceAtLeast(0))) }, { onChange(policy.copy(monthlyRefreshHour = (policy.monthlyRefreshHour + 1).coerceAtMost(23))) }); CardDivider()
            DraftNumberRow("固定周期", "${policy.fixedIntervalHours}小时", { onChange(policy.copy(fixedIntervalHours = (policy.fixedIntervalHours - 1).coerceAtLeast(1))) }, { onChange(policy.copy(fixedIntervalHours = (policy.fixedIntervalHours + 1).coerceAtMost(720))) }); CardDivider()
            DraftSwitchRow("进入积分页时检查", policy.checkOnEntry) { onChange(policy.copy(checkOnEntry = it)) }
        }
    }

@Composable
private fun WidgetRefreshDraftEditor(policy: WidgetRefreshPolicy, onChange: (WidgetRefreshPolicy) -> Unit, onBack: () -> Unit, onSave: () -> Unit, hasUnsavedChanges: Boolean) =
    RefreshDetailScaffold("桌面组件", onBack, onSave, hasUnsavedChanges) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DraftCard {
                DraftSwitchRow("允许组件后台自动刷新", policy.automaticRefreshEnabled) { onChange(policy.copy(automaticRefreshEnabled = it)) }; CardDivider()
                DraftNumberRow("补偿窗口", "${policy.compensationMinutes}分钟", { onChange(policy.copy(compensationMinutes = policy.compensationMinutes - 1)) }, { onChange(policy.copy(compensationMinutes = policy.compensationMinutes + 1)) }); CardDivider()
                DraftNumberRow("失败重试", "${policy.failureRetrySeconds}秒", { onChange(policy.copy(failureRetrySeconds = policy.failureRetrySeconds - 5)) }, { onChange(policy.copy(failureRetrySeconds = policy.failureRetrySeconds + 5)) })
            }
            policy.scheduledMinutes.forEachIndexed { index, minute ->
                DraftCard {
                    DraftNumberRow(
                        "刷新时间 ${index + 1}", formatMinute(minute),
                        { val values = policy.scheduledMinutes.toMutableList(); values[index] = Math.floorMod(minute - 30, 1440); onChange(policy.copy(scheduledMinutes = values)) },
                        { val values = policy.scheduledMinutes.toMutableList(); values[index] = Math.floorMod(minute + 30, 1440); onChange(policy.copy(scheduledMinutes = values)) },
                    )
                    if (policy.scheduledMinutes.size > 1) {
                        TextButton(modifier = Modifier.fillMaxWidth(), onClick = { val values = policy.scheduledMinutes.toMutableList(); values.removeAt(index); onChange(policy.copy(scheduledMinutes = values)) }) { Text("删除") }
                    }
                }
            }
            TextButton(modifier = Modifier.fillMaxWidth(), onClick = { val next = ((policy.scheduledMinutes.lastOrNull() ?: 480) + 180) % 1440; onChange(policy.copy(scheduledMinutes = policy.scheduledMinutes + next)) }) { Text("增加刷新时间") }
        }
    }

@Composable
private fun ShortcutRefreshInfoPage(onBack: () -> Unit) = RefreshDetailScaffold("快捷指令", onBack, {}, false) {
    DraftCard { Text("快捷指令的执行时间由系统自动化控制，不由 App 刷新逻辑主动调度。", color = RefreshSecondary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(18.dp)) }
}

private fun quotaSummary(policy: QuotaRefreshPolicy) = if (policy.automaticRefreshEnabled) "自动 · ${minutesText(policy.minimumIntervalMinutes)}" else "仅手动"
private fun balanceSummary(policy: BalanceRefreshPolicy): String {
    val cache = minutesText(policy.intervalMinutes)
    return if (policy.automaticRefreshEnabled) "App自动 · 缓存 $cache" else "App自动关闭 · 缓存 $cache"
}
private fun rebateGiftSummary(policy: RebateGiftRefreshPolicy) = if (policy.automaticRefreshEnabled) "每月${policy.monthlyRefreshDay}日 %02d:00".format(policy.monthlyRefreshHour) else "仅手动"
private fun integralSummary(policy: IntegralRefreshPolicy): String {
    if (!policy.automaticRefreshEnabled) return "仅手动"
    return when (policy.cycleMode) {
        IntegralRefreshCycleMode.MONTHLY -> "每月${policy.monthlyRefreshDay}日 %02d:00".format(policy.monthlyRefreshHour)
        IntegralRefreshCycleMode.FIXED_INTERVAL -> "每 ${hoursText(policy.fixedIntervalHours)}"
        IntegralRefreshCycleMode.MANUAL_ONLY -> "仅手动"
    }
}
private fun widgetSummary(policy: WidgetRefreshPolicy) = if (policy.automaticRefreshEnabled) "每日 ${policy.scheduledMinutes.size} 个计划时间" else "自动刷新关闭"
private fun minutesText(value: Int) = when { value % 60 == 0 -> "${value / 60}小时"; else -> "${value}分钟" }
private fun hoursText(value: Int) = "${value}小时"
private fun formatMinute(value: Int): String { val minute = Math.floorMod(value, 1440); return "%02d:%02d".format(minute / 60, minute % 60) }
private fun cachedModeTitleForRefresh(mode: CachedBusinessEntryMode) = when (mode) {
    CachedBusinessEntryMode.CACHE_PREFERRED -> "优先使用缓存"
    CachedBusinessEntryMode.REFRESH_WHEN_EXPIRED -> "缓存过期后刷新"
    CachedBusinessEntryMode.EVERY_ENTRY -> "每次进入都刷新"
    CachedBusinessEntryMode.MANUAL_ONLY -> "仅手动刷新"
}
private fun pageModeTitleForRefresh(mode: PageEntryRefreshMode) = when (mode) {
    PageEntryRefreshMode.EVERY_ENTRY -> "进入页面时刷新"
    PageEntryRefreshMode.REFRESH_WHEN_EXPIRED -> "缓存过期后刷新"
    PageEntryRefreshMode.MANUAL_ONLY -> "仅手动刷新"
}
private fun integralModeTitleForRefresh(mode: IntegralRefreshCycleMode) = when (mode) {
    IntegralRefreshCycleMode.MONTHLY -> "每月"
    IntegralRefreshCycleMode.FIXED_INTERVAL -> "固定周期"
    IntegralRefreshCycleMode.MANUAL_ONLY -> "仅手动"
}
