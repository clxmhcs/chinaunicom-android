package com.clxmhcs.chinaunicom.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.ui.navigation.RootTab
import java.util.UUID

private const val ORDERED_ROUTE = "comprehensive/ordered/{accountId}"
private const val BILL_ROUTE = "comprehensive/bill/{accountId}"
private const val FLOW_ROUTE = "comprehensive/flow/{accountId}"
private const val VOICE_ROUTE = "comprehensive/voice/{accountId}"
private const val INTEGRAL_ROUTE = "comprehensive/integral/{accountId}"
private const val ORDERED_BUSINESS_HUB_ROUTE = "other/ordered-business"
private const val OTHER_VIDEO_RING_ROUTE = "other/video-ring"
private const val OTHER_VIDEO_RING_DETAIL_ROUTE = "other/video-ring/{accountId}"
private const val OTHER_ELECTRONIC_RECEIPT_ROUTE = "other/electronic-receipt"
private const val MY_ORDER_ROUTE = "other/my-order"
private const val MY_ORDER_DETAIL_ROUTE = "other/my-order/detail/{accountId}/{orderId}"
private const val MY_PACKAGE_ROUTE = "other/my-package"
private const val OTHER_INTEGRAL_ROUTE = "other/integral"
private const val OTHER_INTEGRAL_DETAIL_ROUTE = "other/integral/{accountId}"
private const val OTHER_PHONE_BILL_ROUTE = "other/phone-bill"
private const val OTHER_PHONE_BILL_DETAIL_ROUTE = "other/phone-bill/{accountId}"
private const val OTHER_REBATE_GIFT_ROUTE = "other/rebate-gift"
private const val OTHER_REBATE_GIFT_DETAIL_ROUTE = "other/rebate-gift/{accountId}"
private const val OTHER_TARIFF_ZONE_ROUTE = "other/tariff-zone"
private const val OTHER_SERVICE_HALL_ROUTE = "other/service-hall"
private const val SETTINGS_CREDENTIALS_ROUTE = "settings/credentials"
private const val SETTINGS_REFRESH_ROUTE = "settings/app-refresh"

@Composable
private fun RootBottomTabBar(
    currentRoute: String?,
    onSelect: (RootTab) -> Unit,
) {
    val selectedBlue = Color(0xFF2E66F2)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RootTab.entries.forEach { tab ->
                    val selected = when (tab) {
                        RootTab.Comprehensive -> currentRoute?.startsWith(RootTab.Comprehensive.route) == true || currentRoute?.startsWith("comprehensive/") == true
                        RootTab.OtherBusiness -> currentRoute == tab.route || currentRoute?.startsWith("other/") == true
                        RootTab.Settings -> currentRoute == tab.route || currentRoute?.startsWith("settings/") == true
                        else -> currentRoute == tab.route
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        onClick = { onSelect(tab) },
                        shape = RoundedCornerShape(26.dp),
                        color = if (selected) selectedBlue.copy(alpha = 0.085f) else Color.Transparent,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = tab.glyph,
                                fontSize = 20.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selected) selectedBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                            )
                            Text(
                                text = tab.label,
                                fontSize = 10.5.sp,
                                lineHeight = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) selectedBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChinaUnicomApp() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val flowViewModel: FlowViewModel = viewModel()
    val comprehensiveViewModel: ComprehensiveBusinessViewModel = viewModel()
    val myOrderViewModel: MyOrderViewModel = viewModel()
    val broadbandAccountViewModel: BroadbandAccountViewModel = viewModel()
    val rebateAndGiftViewModel: RebateAndGiftViewModel = viewModel()
    val tariffZoneViewModel: TariffZoneViewModel = viewModel()
    val videoRingViewModel: VideoRingViewModel = viewModel()
    val electronicReceiptViewModel: ElectronicReceiptViewModel = viewModel()
    val serviceHallViewModel: ServiceHallViewModel = viewModel()
    val settingsViewModel: SettingsRootViewModel = viewModel()
    val appSettings by settingsViewModel.appSettings.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, flowViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> flowViewModel.onForeground()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> flowViewModel.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            flowViewModel.onBackground()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    CompositionLocalProvider(LocalAppSettings provides appSettings) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                RootBottomTabBar(
                    currentRoute = currentRoute,
                    onSelect = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(RootTab.Flow.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = RootTab.Flow.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(RootTab.Flow.route) { FlowHomeScreen(flowViewModel) }
                composable(RootTab.Voice.route) { VoiceDashboardScreen(flowViewModel) }
                composable(RootTab.Comprehensive.route) {
                    ComprehensiveBusinessScreen(
                        flowViewModel = flowViewModel,
                        businessViewModel = comprehensiveViewModel,
                        onOpenOrderedBusiness = { navController.navigate("comprehensive/ordered/$it") },
                        onOpenPhoneBill = { navController.navigate("comprehensive/bill/$it") },
                        onOpenFlow = { navController.navigate("comprehensive/flow/$it") },
                        onOpenVoice = { navController.navigate("comprehensive/voice/$it") },
                        onOpenIntegral = { navController.navigate("comprehensive/integral/$it") },
                    )
                }
                composable(ORDERED_ROUTE) { entry ->
                    BusinessAccountDestination(flowViewModel, entry.arguments?.getString("accountId")) { account, _ ->
                        OrderedBusinessEntryScreen(account, comprehensiveViewModel) { navController.popBackStack() }
                    }
                }
                composable(BILL_ROUTE) { entry ->
                    BusinessAccountDestination(flowViewModel, entry.arguments?.getString("accountId")) { account, _ ->
                        PhoneBillEntryScreen(account, comprehensiveViewModel) { navController.popBackStack() }
                    }
                }
                composable(FLOW_ROUTE) { entry ->
                    BusinessAccountDestination(flowViewModel, entry.arguments?.getString("accountId")) { account, accounts ->
                        ComprehensiveRemainingEntryScreen(account, accounts, false, flowViewModel) { navController.popBackStack() }
                    }
                }
                composable(VOICE_ROUTE) { entry ->
                    BusinessAccountDestination(flowViewModel, entry.arguments?.getString("accountId")) { account, accounts ->
                        ComprehensiveRemainingEntryScreen(account, accounts, true, flowViewModel) { navController.popBackStack() }
                    }
                }
                composable(INTEGRAL_ROUTE) { entry ->
                    BusinessAccountDestination(flowViewModel, entry.arguments?.getString("accountId")) { account, accounts ->
                        IntegralEntryScreen(account, accounts.map { it.id }, comprehensiveViewModel) { navController.popBackStack() }
                    }
                }
                composable(RootTab.OtherBusiness.route) {
                    OtherBusinessScreen(
                        onOpenOrderedBusiness = { navController.navigate(ORDERED_BUSINESS_HUB_ROUTE) },
                        onOpenVideoRing = { navController.navigate(OTHER_VIDEO_RING_ROUTE) },
                        onOpenElectronicReceipt = { navController.navigate(OTHER_ELECTRONIC_RECEIPT_ROUTE) },
                        onOpenMyOrder = { navController.navigate(MY_ORDER_ROUTE) },
                        onOpenMyPackage = { navController.navigate(MY_PACKAGE_ROUTE) },
                        onOpenIntegral = { navController.navigate(OTHER_INTEGRAL_ROUTE) },
                        onOpenPhoneBill = { navController.navigate(OTHER_PHONE_BILL_ROUTE) },
                        onOpenRebateAndGift = { navController.navigate(OTHER_REBATE_GIFT_ROUTE) },
                        onOpenTariffZone = { navController.navigate(OTHER_TARIFF_ZONE_ROUTE) },
                        onOpenNearbyServiceHall = { navController.navigate(OTHER_SERVICE_HALL_ROUTE) },
                    )
                }
                composable(ORDERED_BUSINESS_HUB_ROUTE) {
                    val flowState by flowViewModel.uiState.collectAsState()
                    val broadbandState by broadbandAccountViewModel.state.collectAsState()
                    val mobileAccounts = (flowState as? FlowUiState.Content)?.accounts.orEmpty()
                    val accounts = mobileAccounts + broadbandState.accounts.map { it.toUnicomAccount() }
                    OtherOrderedBusinessScreen(
                        accounts = accounts,
                        businessViewModel = comprehensiveViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(OTHER_VIDEO_RING_ROUTE) {
                    val flowState by flowViewModel.uiState.collectAsState()
                    val mobileAccounts = (flowState as? FlowUiState.Content)?.accounts.orEmpty()
                    VideoRingAccountSelectionScreen(
                        accounts = mobileAccounts,
                        onBack = { navController.popBackStack() },
                        onOpenAccount = { navController.navigate("other/video-ring/$it") },
                    )
                }
                composable(OTHER_VIDEO_RING_DETAIL_ROUTE) { entry ->
                    BusinessAccountDestination(flowViewModel, entry.arguments?.getString("accountId")) { account, _ ->
                        VideoRingMemberCenterScreen(
                            account = account,
                            viewModel = videoRingViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(OTHER_ELECTRONIC_RECEIPT_ROUTE) {
                    val flowState by flowViewModel.uiState.collectAsState()
                    val broadbandState by broadbandAccountViewModel.state.collectAsState()
                    val mobileAccounts = (flowState as? FlowUiState.Content)?.accounts.orEmpty()
                    ElectronicReceiptScreen(
                        mobileAccounts = mobileAccounts,
                        broadbandAccounts = broadbandState.accounts,
                        viewModel = electronicReceiptViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(MY_ORDER_ROUTE) {
                    val flowState by flowViewModel.uiState.collectAsState()
                    val accounts = (flowState as? FlowUiState.Content)?.accounts.orEmpty()
                    MyOrderScreen(
                        accounts = accounts,
                        viewModel = myOrderViewModel,
                        onBack = { navController.popBackStack() },
                        onOpenDetail = { account, order ->
                            navController.navigate("other/my-order/detail/${account.id}/${Uri.encode(order.id)}")
                        },
                    )
                }
                composable(MY_ORDER_DETAIL_ROUTE) { entry ->
                    BusinessAccountDestination(flowViewModel, entry.arguments?.getString("accountId")) { account, _ ->
                        val orderState by myOrderViewModel.state.collectAsState()
                        val orderID = entry.arguments?.getString("orderId")?.let(Uri::decode)
                        val order = orderState.orders.firstOrNull { it.id == orderID }
                        if (order == null) {
                            Text("订单不存在，请返回列表重新加载")
                        } else {
                            MyOrderDetailScreen(account, order, myOrderViewModel) { navController.popBackStack() }
                        }
                    }
                }
                composable(MY_PACKAGE_ROUTE) {
                    val flowState by flowViewModel.uiState.collectAsState()
                    val broadbandState by broadbandAccountViewModel.state.collectAsState()
                    val mobileAccounts = (flowState as? FlowUiState.Content)?.accounts.orEmpty()
                    val accounts = mobileAccounts + broadbandState.accounts.map { it.toUnicomAccount() }
                    val myPackageViewModel: MyPackageViewModel = viewModel()
                    MyPackageScreen(
                        accounts = accounts,
                        viewModel = myPackageViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(OTHER_INTEGRAL_ROUTE) {
                    val flowState by flowViewModel.uiState.collectAsState()
                    val mobileAccounts = (flowState as? FlowUiState.Content)?.accounts.orEmpty()
                    OtherIntegralAccountSelectionScreen(
                        accounts = mobileAccounts,
                        onBack = { navController.popBackStack() },
                        onOpenAccount = { navController.navigate("other/integral/$it") },
                    )
                }
                composable(OTHER_INTEGRAL_DETAIL_ROUTE) { entry ->
                    BusinessAccountDestination(flowViewModel, entry.arguments?.getString("accountId")) { account, accounts ->
                        IntegralEntryScreen(
                            account = account,
                            allAccountIDs = accounts.map { it.id },
                            businessViewModel = comprehensiveViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(OTHER_PHONE_BILL_ROUTE) {
                    val flowState by flowViewModel.uiState.collectAsState()
                    val mobileAccounts = (flowState as? FlowUiState.Content)?.accounts.orEmpty()
                    OtherPhoneBillAccountSelectionScreen(
                        accounts = mobileAccounts,
                        representativeAccountID = flowViewModel::financialRepresentativeAccountID,
                        onBack = { navController.popBackStack() },
                        onOpenAccount = { navController.navigate("other/phone-bill/$it") },
                    )
                }
                composable(OTHER_PHONE_BILL_DETAIL_ROUTE) { entry ->
                    BusinessAccountDestination(flowViewModel, entry.arguments?.getString("accountId")) { account, _ ->
                        PhoneBillEntryScreen(
                            account = account,
                            businessViewModel = comprehensiveViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(OTHER_REBATE_GIFT_ROUTE) {
                    val flowState by flowViewModel.uiState.collectAsState()
                    val mobileAccounts = (flowState as? FlowUiState.Content)?.accounts.orEmpty()
                    OtherRebateAndGiftAccountSelectionScreen(
                        accounts = mobileAccounts,
                        onBack = { navController.popBackStack() },
                        onOpenAccount = { navController.navigate("other/rebate-gift/$it") },
                    )
                }
                composable(OTHER_REBATE_GIFT_DETAIL_ROUTE) { entry ->
                    BusinessAccountDestination(flowViewModel, entry.arguments?.getString("accountId")) { account, _ ->
                        RebateAndGiftScreen(
                            account = account,
                            viewModel = rebateAndGiftViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(OTHER_TARIFF_ZONE_ROUTE) {
                    val flowState by flowViewModel.uiState.collectAsState()
                    val mobileAccounts = (flowState as? FlowUiState.Content)?.accounts.orEmpty()
                    TariffZoneScreen(
                        accounts = mobileAccounts,
                        viewModel = tariffZoneViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(OTHER_SERVICE_HALL_ROUTE) {
                    val flowState by flowViewModel.uiState.collectAsState()
                    val content = flowState as? FlowUiState.Content
                    NearbyServiceHallScreen(
                        accounts = content?.accounts.orEmpty(),
                        preferredAccountID = content?.homeBalanceAccount?.id,
                        viewModel = serviceHallViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(RootTab.Settings.route) {
                    SettingsRootVisualScreen(
                        settingsViewModel = settingsViewModel,
                        onOpenCredentials = { navController.navigate(SETTINGS_CREDENTIALS_ROUTE) },
                        onOpenRefreshLogic = { navController.navigate(SETTINGS_REFRESH_ROUTE) },
                    )
                }
                composable(SETTINGS_CREDENTIALS_ROUTE) {
                    SettingsAccountScreen(
                        flowViewModel = flowViewModel,
                        broadbandViewModel = broadbandAccountViewModel,
                    )
                }
                composable(SETTINGS_REFRESH_ROUTE) {
                    AppRefreshLogicSettingsScreen(
                        settingsViewModel = settingsViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

@Composable
private fun BusinessAccountDestination(
    flowViewModel: FlowViewModel,
    rawAccountID: String?,
    content: @Composable (UnicomAccount, List<UnicomAccount>) -> Unit,
) {
    val state by flowViewModel.uiState.collectAsState()
    val accounts = (state as? FlowUiState.Content)?.accounts.orEmpty()
    val accountID: UUID? = rawAccountID?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
    val account: UnicomAccount? = accountID?.let { id -> accounts.firstOrNull { it.id == id } }
    if (account == null) Text("号码不存在或尚未加载") else content(account, accounts)
}
