package com.clxmhcs.chinaunicom.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.clxmhcs.chinaunicom.core.model.MyOrder
import com.clxmhcs.chinaunicom.core.model.MyOrderBusinessDetail
import com.clxmhcs.chinaunicom.core.model.MyOrderDetailContent
import com.clxmhcs.chinaunicom.core.model.MyOrderDetailRequest
import com.clxmhcs.chinaunicom.core.model.MyOrderRenewalDetail
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.myorder.MyOrderDetailWebBridgeContract
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun MyOrderDetailScreen(
    account: UnicomAccount,
    order: MyOrder,
    viewModel: MyOrderViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.detailState.collectAsState()

    LaunchedEffect(account.id, order.id) {
        viewModel.prepareDetail(account, order)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(onClick = onBack, modifier = Modifier.padding(12.dp)) { Text("返回") }
        Text(
            text = "订单详情",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        when {
            state.errorMessage != null -> {
                Text("加载失败：${state.errorMessage}", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.reloadDetail(account, order) }, modifier = Modifier.padding(16.dp)) { Text("重试") }
            }
            state.content != null -> MyOrderNativeDetail(state.content!!)
            state.isLoading && state.request != null -> {
                val activeRequest = state.request!!
                CircularProgressIndicator(modifier = Modifier.padding(20.dp))
                Text("正在通过联通订单页读取详情…", modifier = Modifier.padding(horizontal = 16.dp))
                MyOrderDetailWebBridge(
                    request = activeRequest,
                    cookieHeaderProvider = { viewModel.requireDetailCookieHeader(account.id) },
                    onResult = { result -> viewModel.receiveDetailBridgeText(result, activeRequest.id) },
                )
            }
            else -> Text("正在准备订单详情…", modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun MyOrderNativeDetail(content: MyOrderDetailContent) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (content) {
            is MyOrderDetailContent.Business -> businessItems(content.detail)
            is MyOrderDetailContent.Renewal -> renewalItems(content.detail)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.businessItems(detail: MyOrderBusinessDetail) {
    item { Text(detail.businessName, style = MaterialTheme.typography.titleLarge) }
    field("产品", detail.productName)
    field("业务号码", detail.mobile)
    field("受理名称", detail.acceptName)
    field("受理编号", detail.acceptNumber)
    field("渠道", detail.channelName)
    field("办理时间", detail.handleTime)
    field("创建时间", detail.createTime)
    field("网络类型", detail.networkName)
    field("地区", listOf(detail.provinceName, detail.areaName).filter { it.isNotBlank() }.joinToString(" "))
    if (detail.subProducts.isNotEmpty()) {
        item { Text("关联产品", style = MaterialTheme.typography.titleMedium) }
        detail.subProducts.forEach { product ->
            item(key = product.id) {
                Column {
                    Text(product.productName)
                    if (product.statusName.isNotBlank()) Text("状态：${product.statusName}")
                    if (product.startTime.isNotBlank() || product.endTime.isNotBlank()) {
                        Text("有效期：${product.startTime} - ${product.endTime}")
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renewalItems(detail: MyOrderRenewalDetail) {
    item { Text(detail.productName, style = MaterialTheme.typography.titleLarge) }
    field("订单号", detail.orderNo)
    field("业务类型", detail.serviceType)
    field("创建时间", detail.createTime)
    field("生效时间", detail.actionStartTime)
    field("支付完成", detail.paymentTime)
    field("更新时间", detail.updateTime)
    detail.amountFen?.let { fen -> field("金额", "¥%.2f".format(fen / 100.0)) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.field(label: String, value: String) {
    if (value.isNotBlank()) item { Text("$label：$value") }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MyOrderDetailWebBridge(
    request: MyOrderDetailRequest,
    cookieHeaderProvider: () -> String,
    onResult: (Result<String>) -> Unit,
) {
    val context = LocalContext.current
    val holder = remember(request.id) { HostedOrderBridgeHolder(request, cookieHeaderProvider, onResult) }
    DisposableEffect(holder) {
        onDispose { holder.dispose() }
    }
    AndroidView(
        factory = { holder.create(context) },
        modifier = Modifier.size(1.dp),
    )
}

private class HostedOrderBridgeHolder(
    private val request: MyOrderDetailRequest,
    private val cookieHeaderProvider: () -> String,
    private val onResult: (Result<String>) -> Unit,
) {
    private var webView: WebView? = null
    private var cookieNames: List<String> = emptyList()
    private val completed = AtomicBoolean(false)
    private val injected = AtomicBoolean(false)

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: android.content.Context): WebView {
        val view = WebView(context)
        webView = view
        try {
            val cookieHeader = cookieHeaderProvider()
            cookieNames = installCookies(cookieHeader)
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                userAgentString = MyOrderDetailWebBridgeContract.USER_AGENT
            }
            view.addJavascriptInterface(Bridge { text -> complete(Result.success(text)) }, MyOrderDetailWebBridgeContract.ANDROID_BRIDGE_NAME)
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                    !trusted(request?.url?.host)

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url?.toString().orEmpty()
                    if (url.contains("queryEvaluateItem", ignoreCase = true)) {
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    val current = runCatching { url?.let(::URI) }.getOrNull()
                    if (MyOrderDetailWebBridgeContract.isReadyURL(current, request.mode) && injected.compareAndSet(false, true)) {
                        view.postDelayed({
                            if (!completed.get()) {
                                runCatching { view.evaluateJavascript(MyOrderDetailWebBridgeContract.javaScript(request), null) }
                                    .onFailure { complete(Result.failure(it)) }
                            }
                        }, 800L)
                    }
                }
            }
            view.loadUrl(request.actionURL.toString(), mapOf("Cookie" to cookieHeader))
        } catch (error: Exception) {
            view.post { complete(Result.failure(error)) }
        }
        return view
    }

    private fun trusted(host: String?): Boolean {
        val value = host?.lowercase().orEmpty()
        return value == "10010.com" || value.endsWith(".10010.com")
    }

    private fun installCookies(header: String): List<String> {
        val manager = android.webkit.CookieManager.getInstance()
        manager.setAcceptCookie(true)
        val names = mutableListOf<String>()
        header.split(';').forEach { raw ->
            val pair = raw.trim()
            val separator = pair.indexOf('=')
            if (separator <= 0) return@forEach
            val name = pair.substring(0, separator).trim()
            if (name.isEmpty()) return@forEach
            names += name
            manager.setCookie("https://10010.com", "$pair; Domain=.10010.com; Path=/; Secure")
        }
        manager.flush()
        return names.distinct()
    }

    private fun expireCookies() {
        val manager = android.webkit.CookieManager.getInstance()
        cookieNames.forEach { name ->
            manager.setCookie("https://10010.com", "$name=; Max-Age=0; Domain=.10010.com; Path=/; Secure")
        }
        manager.flush()
        cookieNames = emptyList()
    }

    private fun complete(result: Result<String>) {
        if (!completed.compareAndSet(false, true)) return
        val deliver = {
            expireCookies()
            onResult(result)
        }
        webView?.post(deliver) ?: deliver()
    }

    fun dispose() {
        expireCookies()
        webView?.apply {
            stopLoading()
            removeJavascriptInterface(MyOrderDetailWebBridgeContract.ANDROID_BRIDGE_NAME)
            loadUrl("about:blank")
            clearHistory()
            destroy()
        }
        webView = null
    }

    private class Bridge(private val callback: (String) -> Unit) {
        @JavascriptInterface
        fun postMessage(value: String) = callback(value)
    }
}
