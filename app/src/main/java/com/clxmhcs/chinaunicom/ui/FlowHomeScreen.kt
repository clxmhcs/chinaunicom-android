package com.clxmhcs.chinaunicom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clxmhcs.chinaunicom.core.model.DisplayUnit
import com.clxmhcs.chinaunicom.core.parser.flowPackageDisplayText
import com.clxmhcs.chinaunicom.ui.components.UnicomAccountCard
import com.clxmhcs.chinaunicom.ui.components.UnicomBottomNavigationBar
import com.clxmhcs.chinaunicom.ui.components.UnicomHeader
import com.clxmhcs.chinaunicom.ui.components.UnicomQuotaCard

/**
 * M4-G2-C7 rough flow shell.
 *
 * Quota formatting/business semantics are owned by core parser/model code;
 * this screen only renders already-prepared presentation text.
 */
@Composable
fun FlowHomeScreen(
    flowViewModel: FlowViewModel = viewModel()
) {
    val uiState by flowViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            UnicomHeader(title = "流量")

            when (val state = uiState) {
                FlowUiState.Loading -> {
                    Text(text = "加载中...")
                }

                is FlowUiState.Error -> {
                    Text(text = state.message)
                }

                is FlowUiState.Content -> {
                    val account = state.overview.accounts.firstOrNull()
                    if (account != null) {
                        UnicomAccountCard(
                            number = account.mobile,
                            location = null,
                            planName = account.packageName.takeIf { it.isNotBlank() },
                            balance = account.balanceYuan?.let { "${it}元" },
                        )

                        account.packages.forEach { quota ->
                            val display = flowPackageDisplayText(
                                account = account,
                                packageValue = quota,
                                unit = DisplayUnit.AUTOMATIC,
                            )

                            UnicomQuotaCard(
                                title = display.title,
                                subtitle = account.mobile,
                                remaining = display.remainingText,
                                detail = display.detailText,
                                progress = display.progress?.toFloat() ?: 0f,
                            )
                        }
                    }
                }
            }
        }

        UnicomBottomNavigationBar(selected = "流量")
    }
}
