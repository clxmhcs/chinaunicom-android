package com.clxmhcs.chinaunicom.data.myorder

import com.clxmhcs.chinaunicom.core.login.MyOrderDetailCredentialLifecycle
import com.clxmhcs.chinaunicom.core.model.MyOrder
import com.clxmhcs.chinaunicom.core.model.MyOrderDetailContent
import com.clxmhcs.chinaunicom.core.model.MyOrderDetailMode
import com.clxmhcs.chinaunicom.core.model.MyOrderDetailRequest
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MyOrderDetailStoreState(
    val request: MyOrderDetailRequest? = null,
    val content: MyOrderDetailContent? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class MyOrderDetailStore(
    private val credentialLifecycle: MyOrderDetailCredentialLifecycle,
    private val parser: MyOrderDetailParser = MyOrderDetailParser(),
) {
    private val _state = MutableStateFlow(MyOrderDetailStoreState())
    val state: StateFlow<MyOrderDetailStoreState> = _state.asStateFlow()

    private var activeOrderID: String? = null

    fun prepare(account: UnicomAccount, order: MyOrder, force: Boolean = false) {
        if (!force && activeOrderID == order.id) return
        activeOrderID = order.id
        _state.value = MyOrderDetailStoreState()

        try {
            val request = MyOrderDetailRequestFactory.create(account.id, order)
            credentialLifecycle.requireCookieHeader(account.id)
            _state.value = MyOrderDetailStoreState(request = request, isLoading = true)
        } catch (error: Exception) {
            _state.value = MyOrderDetailStoreState(errorMessage = error.message ?: error::class.java.simpleName)
        }
    }

    fun reload(account: UnicomAccount, order: MyOrder) {
        activeOrderID = null
        prepare(account, order, force = true)
    }

    fun receiveBridgeText(result: Result<String>, requestID: UUID) {
        val request = _state.value.request
        if (request?.id != requestID) return

        _state.value = try {
            val bridgeText = result.getOrThrow()
            MyOrderDetailStoreState(
                request = request,
                content = parser.parse(bridgeText, request.mode),
                isLoading = false,
                errorMessage = null,
            )
        } catch (error: Exception) {
            MyOrderDetailStoreState(
                request = request,
                content = null,
                isLoading = false,
                errorMessage = error.message ?: error::class.java.simpleName,
            )
        }
    }

    val headerTitle: String
        get() = when (_state.value.request?.mode) {
            MyOrderDetailMode.BUSINESS -> "业务订单详情"
            MyOrderDetailMode.RENEWAL,
            MyOrderDetailMode.UNSUPPORTED,
            null -> "订单详情"
        }
}
