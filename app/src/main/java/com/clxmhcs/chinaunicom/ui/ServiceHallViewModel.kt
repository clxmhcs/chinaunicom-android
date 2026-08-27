package com.clxmhcs.chinaunicom.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.core.model.ServiceHallAction
import com.clxmhcs.chinaunicom.core.model.ServiceHallCategory
import com.clxmhcs.chinaunicom.core.model.ServiceHallCity
import com.clxmhcs.chinaunicom.core.model.ServiceHallCoordinate
import com.clxmhcs.chinaunicom.core.model.ServiceHallListItem
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.network.UnicomCookieCodec
import com.clxmhcs.chinaunicom.core.network.UnicomServiceHallClient
import com.clxmhcs.chinaunicom.data.CredentialStoreProvider
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServiceHallUiState(
    val accountID: UUID? = null,
    val maskedMobile: String = "",
    val cities: List<ServiceHallCity> = emptyList(),
    val selectedCity: ServiceHallCity? = null,
    val coordinate: ServiceHallCoordinate? = null,
    val category: ServiceHallCategory = ServiceHallCategory.SELF_OPERATED,
    val halls: List<ServiceHallListItem> = emptyList(),
    val actions: List<ServiceHallAction> = emptyList(),
    val searchText: String = "",
    val eSIMOnly: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val reachedEnd: Boolean = false,
    val locationStatus: String = "等待定位",
    val errorMessage: String? = null,
) {
    val visibleHalls: List<ServiceHallListItem>
        get() {
            val needle = searchText.trim()
            return halls.filter { hall ->
                val matchesSearch = needle.isEmpty() || hall.name.contains(needle, true) || hall.address.contains(needle, true)
                val matchesESIM = !eSIMOnly || hall.labels.any { it.contains("eSIM", true) }
                matchesSearch && matchesESIM
            }
        }
}

class ServiceHallViewModel(application: Application) : AndroidViewModel(application) {
    private val credentials = CredentialStoreProvider.create(application.applicationContext)
    private val client = UnicomServiceHallClient()
    private val locationManager = application.getSystemService(LocationManager::class.java)
    private val _state = MutableStateFlow(ServiceHallUiState())
    val state: StateFlow<ServiceHallUiState> = _state.asStateFlow()

    private var bootstrappedForAccount: UUID? = null
    private var pageIndex = 0

    fun bootstrap(accounts: List<UnicomAccount>, preferredAccountID: UUID?) {
        val account = preferredAccountID?.let { id -> accounts.firstOrNull { it.id == id && it.isEnabled } }
            ?: accounts.firstOrNull { it.isEnabled }
        if (account == null) {
            _state.update { it.copy(errorMessage = "没有可用于营业厅查询的联通手机号") }
            return
        }
        if (bootstrappedForAccount == account.id && _state.value.cities.isNotEmpty()) return
        bootstrappedForAccount = account.id
        _state.value = ServiceHallUiState(accountID = account.id, maskedMobile = mask(account.mobile), isLoading = true)
        loadCities(account)
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) {
            _state.update { it.copy(locationStatus = "未授权定位，按登录城市查询") }
            return
        }
        requestDeviceLocation()
    }

    fun requestDeviceLocation() {
        val app = getApplication<Application>()
        val fine = app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            _state.update { it.copy(locationStatus = "需要定位权限") }
            return
        }
        _state.update { it.copy(locationStatus = "正在定位…") }
        val provider = when {
            fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            _state.update { it.copy(locationStatus = "定位服务不可用，按城市中心查询") }
            return
        }
        runCatching {
            locationManager.getCurrentLocation(
                provider,
                CancellationSignal(),
                app.mainExecutor,
            ) { location ->
                if (location == null) {
                    _state.update { it.copy(locationStatus = "未取得当前位置，按城市中心查询") }
                } else {
                    val coordinate = ServiceHallCoordinate(location.longitude, location.latitude)
                    _state.update { it.copy(coordinate = coordinate, locationStatus = "已使用当前位置") }
                    chooseNearestCityAndReload(coordinate)
                }
            }
        }.onFailure {
            _state.update { state -> state.copy(locationStatus = "定位失败，按城市中心查询") }
        }
    }

    fun selectCity(city: ServiceHallCity) {
        val coordinate = city.coordinateOrNull() ?: _state.value.coordinate
        _state.update { it.copy(selectedCity = city, coordinate = coordinate, halls = emptyList(), errorMessage = null) }
        reload()
    }

    fun selectCategory(category: ServiceHallCategory) {
        if (_state.value.category == category) return
        _state.update { it.copy(category = category, halls = emptyList(), errorMessage = null) }
        reload()
    }

    fun setSearchText(value: String) = _state.update { it.copy(searchText = value) }
    fun toggleESIM() = _state.update { it.copy(eSIMOnly = !it.eSIMOnly) }

    fun reload() {
        val accountID = _state.value.accountID ?: return
        val city = _state.value.selectedCity ?: return
        val coordinate = _state.value.coordinate ?: city.coordinateOrNull() ?: return
        pageIndex = 0
        _state.update { it.copy(isLoading = true, isLoadingMore = false, halls = emptyList(), reachedEnd = false, errorMessage = null) }
        fetchPage(accountID, city, coordinate, 0, append = false)
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || current.reachedEnd) return
        val accountID = current.accountID ?: return
        val city = current.selectedCity ?: return
        val coordinate = current.coordinate ?: city.coordinateOrNull() ?: return
        val next = pageIndex + 1
        _state.update { it.copy(isLoadingMore = true) }
        fetchPage(accountID, city, coordinate, next, append = true)
    }

    private fun loadCities(account: UnicomAccount) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val stored = credentials.read(account.id) ?: error("账号缺少登录凭据")
                val result = client.fetchCities(stored)
                result.updatedCredentials?.let { credentials.save(account.id, it) }
                val cookie = UnicomCookieCodec.normalize(result.updatedCredentials?.cookie ?: stored.cookie)
                Triple(result.cities, cityCodeFromCookie(cookie), provinceCodeFromCookie(cookie))
            }.onSuccess { (cities, cookieCity, cookieProvince) ->
                val coordinate = _state.value.coordinate
                val selected = when {
                    coordinate != null -> cities.minByOrNull { it.distanceTo(coordinate) }
                    cookieCity != null -> cities.firstOrNull { it.cityCode == cookieCity && (cookieProvince == null || it.provinceCode == cookieProvince) }
                    else -> null
                } ?: cities.firstOrNull()
                val queryCoordinate = coordinate ?: selected?.coordinateOrNull()
                _state.update {
                    it.copy(
                        cities = cities,
                        selectedCity = selected,
                        coordinate = queryCoordinate,
                        isLoading = false,
                        errorMessage = if (cities.isEmpty()) "联通未返回可用城市" else null,
                    )
                }
                if (selected != null && queryCoordinate != null) reload()
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, errorMessage = safeMessage(error)) }
            }
        }
    }

    private fun chooseNearestCityAndReload(coordinate: ServiceHallCoordinate) {
        val cities = _state.value.cities
        if (cities.isEmpty()) return
        val selected = cities.minByOrNull { it.distanceTo(coordinate) } ?: return
        _state.update { it.copy(selectedCity = selected, coordinate = coordinate) }
        reload()
    }

    private fun fetchPage(
        accountID: UUID,
        city: ServiceHallCity,
        coordinate: ServiceHallCoordinate,
        page: Int,
        append: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val stored = credentials.read(accountID) ?: error("账号缺少登录凭据")
                val result = client.fetchOverview(
                    credentials = stored,
                    provinceCode = city.provinceCode,
                    cityCode = city.cityCode,
                    coordinate = coordinate,
                    category = _state.value.category,
                    pageIndex = page,
                )
                result.updatedCredentials?.let { credentials.save(accountID, it) }
                result.overview
            }.onSuccess { overview ->
                pageIndex = page
                _state.update { current ->
                    val merged = if (append) {
                        (current.halls + overview.halls).distinctBy { it.id }
                    } else overview.halls
                    current.copy(
                        halls = merged,
                        actions = overview.actions,
                        isLoading = false,
                        isLoadingMore = false,
                        reachedEnd = overview.halls.isEmpty(),
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, isLoadingMore = false, errorMessage = safeMessage(error)) }
            }
        }
    }

    private fun ServiceHallCity.coordinateOrNull(): ServiceHallCoordinate? {
        val cityLongitude = longitude ?: return null
        val cityLatitude = latitude ?: return null
        return ServiceHallCoordinate(cityLongitude, cityLatitude)
    }

    private fun ServiceHallCity.distanceTo(target: ServiceHallCoordinate): Double {
        val source = coordinateOrNull() ?: return Double.MAX_VALUE
        return haversine(source, target)
    }

    private fun haversine(a: ServiceHallCoordinate, b: ServiceHallCoordinate): Double {
        val radius = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
        return radius * 2 * atan2(sqrt(h), sqrt(1 - h))
    }

    private fun cityCodeFromCookie(cookie: String): String? = provinceCityCookie(cookie)?.second
    private fun provinceCodeFromCookie(cookie: String): String? = provinceCityCookie(cookie)?.first

    private fun provinceCityCookie(cookie: String): Pair<String, String>? {
        val map = cookie.split(';').mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) null else part.substring(0, index).trim().lowercase() to part.substring(index + 1).trim()
        }.toMap()
        for (key in listOf("city", "mallcity", "usercity", "cdn_area", "gipgeo")) {
            val pieces = map[key]?.split('|') ?: continue
            if (pieces.size >= 2 && pieces[0].isNotBlank() && pieces[1].isNotBlank()) return pieces[0] to pieces[1]
        }
        return null
    }

    private fun mask(value: String): String {
        val text = value.trim()
        return if (text.length >= 8) text.take(3) + "****" + text.takeLast(4) else text
    }

    private fun safeMessage(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: "营业厅查询失败"
}
