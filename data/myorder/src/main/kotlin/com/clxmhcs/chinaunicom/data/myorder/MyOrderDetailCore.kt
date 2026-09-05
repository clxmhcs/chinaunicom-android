package com.clxmhcs.chinaunicom.data.myorder

import com.clxmhcs.chinaunicom.core.model.MyOrder
import com.clxmhcs.chinaunicom.core.model.MyOrderDetailMode
import com.clxmhcs.chinaunicom.core.model.MyOrderDetailRequest
import com.clxmhcs.chinaunicom.core.network.UnicomClientProfile
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed class MyOrderDetailPreparationException(message: String) : Exception(message) {
    data object MissingAction : MyOrderDetailPreparationException("该订单没有可用的详情地址。")
    data object Unsupported : MyOrderDetailPreparationException("该订单类型的详情接口尚未接入。")
}

object MyOrderDetailRequestFactory {
    fun create(accountID: UUID, order: MyOrder): MyOrderDetailRequest {
        val actionURL = order.detailAction?.normalizedURL ?: throw MyOrderDetailPreparationException.MissingAction
        val mode = detailMode(order, actionURL)
        if (mode == MyOrderDetailMode.UNSUPPORTED) throw MyOrderDetailPreparationException.Unsupported
        return MyOrderDetailRequest(
            accountID = accountID,
            actionURL = actionURL,
            mode = mode,
            orderID = detailOrderID(order, actionURL, mode),
            serviceType = queryValue("serviceType", actionURL.toString()),
        )
    }

    fun detailMode(order: MyOrder, actionURL: URI): MyOrderDetailMode {
        val value = actionURL.toString().lowercase(Locale.ROOT)
        if (order.kind.rawValue == "renewal" || value.contains("broadordersdetail") || value.contains("broarenewalinfo")) {
            return MyOrderDetailMode.RENEWAL
        }
        if (order.kind.rawValue == "storefront" || value.contains("omo.10010.com") || value.contains("qryevaluateorderinfobyorderid")) {
            return MyOrderDetailMode.BUSINESS
        }
        return MyOrderDetailMode.UNSUPPORTED
    }

    fun detailOrderID(order: MyOrder, actionURL: URI, mode: MyOrderDetailMode): String {
        val names = if (mode == MyOrderDetailMode.RENEWAL) listOf("orderNo", "orderId") else listOf("orderId", "orderNo")
        names.forEach { name -> queryValue(name, actionURL.toString())?.let { return it } }
        return order.orderID
    }

    fun queryValue(name: String, source: String): String? {
        val normalized = source
            .replace("%26", "&", ignoreCase = true)
            .replace("%3D", "=", ignoreCase = true)
        val expression = Regex("(?:[?&])${Regex.escape(name)}=([^&#]+)", RegexOption.IGNORE_CASE)
        val raw = expression.find(normalized)?.groupValues?.getOrNull(1) ?: return null
        return runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrDefault(raw)
    }
}

/**
 * Platform-neutral source contract consumed by the Android WebView adapter.
 * It freezes the iOS ready-URL gates, request endpoints, sourcePage, sub-product rule and renewal default serviceType.
 */
object MyOrderDetailWebBridgeContract {
    fun userAgent(systemVersion: String): String = UnicomClientProfile.h5UserAgent(systemVersion)

    const val BLOCKED_EVALUATION_PATTERN = ".*queryEvaluateItem.*"
    const val ANDROID_BRIDGE_NAME = "myOrderDetailBridge"

    fun isReadyURL(url: URI?, mode: MyOrderDetailMode): Boolean {
        val host = url?.host?.lowercase(Locale.ROOT) ?: return false
        val path = url.path.orEmpty().lowercase(Locale.ROOT)
        return when (mode) {
            MyOrderDetailMode.BUSINESS -> host == "omo.10010.com" && path.contains("dbh-evaluate-fe")
            MyOrderDetailMode.RENEWAL -> host == "upayxx.10010.com" && path.contains("broadordersdetail") && !path.contains("broadordersdetailinit")
            MyOrderDetailMode.UNSUPPORTED -> false
        }
    }

    fun javaScript(request: MyOrderDetailRequest): String = when (request.mode) {
        MyOrderDetailMode.BUSINESS -> businessScript(request.orderID)
        MyOrderDetailMode.RENEWAL -> renewalScript(request.orderID, request.serviceType ?: "29")
        MyOrderDetailMode.UNSUPPORTED -> throw MyOrderDetailPreparationException.Unsupported
    }

    private fun businessScript(orderID: String): String {
        val orderLiteral = Json.encodeToString(orderID)
        return """
            (() => {
              const postSuccess = (payload) => window.$ANDROID_BRIDGE_NAME.postMessage(JSON.stringify(payload));
              const postFailure = (error) => {
                const message = error && error.message ? error.message : String(error || '订单详情接口执行失败');
                window.$ANDROID_BRIDGE_NAME.postMessage(JSON.stringify({ __bridgeError: message }));
              };
              (async () => {
                const requestJSON = async (url, options) => {
                  const response = await fetch(url, options);
                  const body = await response.text();
                  if (!response.ok) throw new Error(`HTTP ${'$'}{response.status}: ${'$'}{body.slice(0, 160)}`);
                  return body;
                };
                const detail = await requestJSON('/udbh/rest/portal/qryEvaluateOrderInfoByOrderId', {
                  method: 'POST', credentials: 'include',
                  headers: {'Accept':'application/json, text/plain, */*','Content-Type':'application/json'},
                  body: JSON.stringify({orderId: $orderLiteral, sourcePage: 'CJ_SOU_20000'})
                });
                let products = '';
                try {
                  const parsed = JSON.parse(detail);
                  if (parsed && parsed.code === '0000' && parsed.data && parsed.data.businessType === '120') {
                    products = await requestJSON('/udbh/rest/portal/querySubProducts', {
                      method: 'POST', credentials: 'include',
                      headers: {'Accept':'application/json, text/plain, */*','Content-Type':'application/json'},
                      body: JSON.stringify({orderId: $orderLiteral, pageNo: 1, pageSize: 100})
                    });
                  }
                } catch (_) { products = ''; }
                postSuccess({detail, products});
              })().catch(postFailure);
            })();
        """.trimIndent()
    }

    private fun renewalScript(orderID: String, serviceType: String): String {
        val orderLiteral = Json.encodeToString(orderID)
        val serviceLiteral = Json.encodeToString(serviceType)
        return """
            (() => {
              const postSuccess = (payload) => window.$ANDROID_BRIDGE_NAME.postMessage(JSON.stringify(payload));
              const postFailure = (error) => {
                const message = error && error.message ? error.message : String(error || '订单详情接口执行失败');
                window.$ANDROID_BRIDGE_NAME.postMessage(JSON.stringify({ __bridgeError: message }));
              };
              (async () => {
                const query = new URLSearchParams({orderNo: $orderLiteral, serviceType: $serviceLiteral});
                const response = await fetch(`/npfwap/NpfMobAppQuery/broadRenewalOrderHandle/broaRenewalInfo?${'$'}{query.toString()}`, {
                  method: 'GET', credentials: 'include', headers: {'Accept':'application/json, text/plain, */*'}
                });
                const detail = await response.text();
                if (!response.ok) throw new Error(`HTTP ${'$'}{response.status}: ${'$'}{detail.slice(0, 160)}`);
                postSuccess({detail});
              })().catch(postFailure);
            })();
        """.trimIndent()
    }
}
