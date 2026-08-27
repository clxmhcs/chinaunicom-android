package com.clxmhcs.chinaunicom.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.clxmhcs.chinaunicom.core.model.AppointmentTicketSlot
import com.clxmhcs.chinaunicom.core.model.ServiceHallCategory
import com.clxmhcs.chinaunicom.core.model.ServiceHallListItem
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.util.Locale
import java.util.UUID

@Composable
fun NearbyServiceHallScreen(
    accounts: List<UnicomAccount>,
    preferredAccountID: UUID?,
    viewModel: ServiceHallViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val appointmentState by viewModel.appointmentState.collectAsState()
    val context = LocalContext.current
    var selectedHall by remember { mutableStateOf<ServiceHallListItem?>(null) }
    var appointmentHall by remember { mutableStateOf<ServiceHallListItem?>(null) }
    var cityMenuExpanded by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        viewModel.onLocationPermissionResult(
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true,
        )
    }

    LaunchedEffect(accounts, preferredAccountID) {
        viewModel.bootstrap(accounts, preferredAccountID)
    }
    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onLocationPermissionResult(true)
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    appointmentHall?.let { hall ->
        ServiceHallAppointmentContent(
            hall = hall,
            state = appointmentState,
            onBack = {
                viewModel.clearAppointment()
                appointmentHall = null
            },
            onSelectSlot = viewModel::selectAppointmentSlot,
            onSubmit = { viewModel.submitAppointment(hall) },
            onRetry = { viewModel.openAppointment(hall) },
        )
        return
    }

    if (selectedHall != null) {
        ServiceHallDetailContent(
            hall = selectedHall!!,
            onBack = { selectedHall = null },
            onNavigate = {
                val hall = selectedHall ?: return@ServiceHallDetailContent
                val uri = when {
                    hall.latitude != null && hall.longitude != null -> Uri.parse("geo:${hall.latitude},${hall.longitude}?q=${Uri.encode(hall.name)}")
                    hall.address.isNotBlank() -> Uri.parse("geo:0,0?q=${Uri.encode(hall.address)}")
                    else -> null
                }
                uri?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, it)) } }
            },
            onAppointment = {
                val hall = selectedHall ?: return@ServiceHallDetailContent
                appointmentHall = hall
                viewModel.openAppointment(hall)
            },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Column(modifier = Modifier.weight(1f)) {
                Text("附近营业厅", style = MaterialTheme.typography.titleLarge)
                Text(
                    "账号 ${state.maskedMobile.ifBlank { "未选择" }} · ${state.locationStatus}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = viewModel::reload, enabled = state.selectedCity != null) { Text("刷新") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column {
                OutlinedButton(onClick = { cityMenuExpanded = true }, enabled = state.cities.isNotEmpty()) {
                    Text(state.selectedCity?.cityName ?: "城市")
                }
                DropdownMenu(expanded = cityMenuExpanded, onDismissRequest = { cityMenuExpanded = false }) {
                    state.cities.forEach { city ->
                        DropdownMenuItem(
                            text = { Text("${city.provinceName} ${city.cityName}".trim()) },
                            onClick = {
                                cityMenuExpanded = false
                                viewModel.selectCity(city)
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = state.searchText,
                onValueChange = viewModel::setSearchText,
                label = { Text("搜索营业厅或地址") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.category == ServiceHallCategory.SELF_OPERATED,
                onClick = { viewModel.selectCategory(ServiceHallCategory.SELF_OPERATED) },
                label = { Text("自营厅") },
            )
            FilterChip(
                selected = state.category == ServiceHallCategory.PARTNER,
                onClick = { viewModel.selectCategory(ServiceHallCategory.PARTNER) },
                label = { Text("合作厅") },
            )
            FilterChip(
                selected = state.eSIMOnly,
                onClick = viewModel::toggleESIM,
                label = { Text("eSIM办理") },
            )
        }

        state.errorMessage?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        if (state.isLoading && state.halls.isEmpty()) {
            Text("正在查找附近营业厅…", modifier = Modifier.padding(20.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.visibleHalls, key = { it.id }) { hall ->
                ServiceHallCard(hall = hall, onClick = { selectedHall = hall })
            }
            item {
                if (state.visibleHalls.isEmpty() && !state.isLoading && state.errorMessage == null) {
                    Text("没有符合当前条件的营业厅", modifier = Modifier.padding(20.dp))
                }
                if (!state.reachedEnd && state.halls.isNotEmpty()) {
                    Button(
                        onClick = viewModel::loadMore,
                        enabled = !state.isLoadingMore,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    ) {
                        Text(if (state.isLoadingMore) "加载中…" else "加载更多")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ServiceHallCard(hall: ServiceHallListItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(hall.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                hall.distanceMeters?.let { Text(distanceText(it), style = MaterialTheme.typography.bodySmall) }
            }
            if (hall.address.isNotBlank()) Text(hall.address, style = MaterialTheme.typography.bodyMedium)
            val meta = listOf(hall.businessStatus, hall.businessHours, hall.ratingText.takeIf { it.isNotBlank() }?.let { "评分 $it" }.orEmpty())
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hall.labels.isNotEmpty()) Text(hall.labels.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ServiceHallDetailContent(
    hall: ServiceHallListItem,
    onBack: () -> Unit,
    onNavigate: () -> Unit,
    onAppointment: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text("返回营业厅列表") }
        Text(hall.name, style = MaterialTheme.typography.headlineSmall)
        if (hall.address.isNotBlank()) Text("地址：${hall.address}")
        hall.distanceMeters?.let { Text("距离：${distanceText(it)}") }
        if (hall.businessHours.isNotBlank()) Text("营业时间：${hall.businessHours}")
        if (hall.businessStatus.isNotBlank()) Text("营业状态：${hall.businessStatus}")
        if (hall.ratingText.isNotBlank()) Text("评分：${hall.ratingText}")
        if (hall.labels.isNotEmpty()) Text("标签：${hall.labels.joinToString("、")}")
        Button(onClick = onNavigate, enabled = hall.address.isNotBlank() || (hall.latitude != null && hall.longitude != null)) {
            Text("导航到营业厅")
        }
        Button(onClick = onAppointment, enabled = hall.supportsAppointment != false) {
            Text("预约取号")
        }
        Text("预约取号使用联通原生预约接口，不再跳转外部浏览器。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ServiceHallAppointmentContent(
    hall: ServiceHallListItem,
    state: ServiceHallAppointmentUiState,
    onBack: () -> Unit,
    onSelectSlot: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Column(modifier = Modifier.weight(1f)) {
                Text("线下预约", style = MaterialTheme.typography.titleLarge)
                Text(hall.name, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (hall.address.isNotBlank()) Text("地址：${hall.address}", modifier = Modifier.padding(horizontal = 16.dp))
        if (hall.businessHours.isNotBlank()) Text("营业时间：${hall.businessHours}", modifier = Modifier.padding(horizontal = 16.dp))
        if (state.business.isNotBlank()) Text("预约业务：${state.business}", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        state.orderDescription?.takeIf { it.isNotBlank() }?.let {
            Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
        }
        state.successMessage?.let {
            Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary)
        }
        state.errorMessage?.let {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(it, color = MaterialTheme.colorScheme.error)
                if (state.slots.isEmpty()) OutlinedButton(onClick = onRetry) { Text("重试") }
            }
        }
        if (state.isLoading) {
            Text("正在查询可预约时间…", modifier = Modifier.padding(20.dp))
        } else {
            Text("预约到厅时间", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.slots, key = { it.id }) { slot ->
                    AppointmentSlotRow(
                        slot = slot,
                        selected = state.selectedSlotID == slot.id,
                        onClick = { onSelectSlot(slot.id) },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
            Button(
                onClick = onSubmit,
                enabled = state.selectedSlot != null && !state.isSubmitting,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(if (state.isSubmitting) "正在提交预约…" else "提交预约")
            }
        }
    }
}

@Composable
private fun AppointmentSlotRow(
    slot: AppointmentTicketSlot,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = slot.isAvailable,
        label = {
            val remaining = slot.remainingCount?.let { " · 剩余 $it 个" }.orEmpty()
            Text("${slot.day} ${slot.timeText}$remaining")
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

private fun distanceText(meters: Double): String = if (meters >= 1000) {
    String.format(Locale.CHINA, "%.1f km", meters / 1000.0)
} else {
    "${meters.toInt()} m"
}
