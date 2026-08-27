package com.clxmhcs.chinaunicom.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.broadbandaccount.BroadbandAccountInfo
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private enum class ReceiptMode { HUB, TARGETS, BROWSER, SAVED }

@Composable
fun ElectronicReceiptScreen(
    mobileAccounts: List<UnicomAccount>,
    broadbandAccounts: List<BroadbandAccountInfo>,
    viewModel: ElectronicReceiptViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var mode by remember { mutableStateOf(ReceiptMode.HUB) }
    var manualRefreshSerial by remember { mutableIntStateOf(0) }
    var viewer by remember { mutableStateOf<SavedElectronicReceipt?>(null) }
    val directoryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.setExportDirectory(uri)
    }

    LaunchedEffect(mobileAccounts, broadbandAccounts) {
        viewModel.reconcileTargets(mobileAccounts, broadbandAccounts)
    }

    viewer?.let { item ->
        ElectronicReceiptPdfViewer(
            file = viewModel.pdfFile(item),
            title = "电子受理单 ${item.dateText}",
            onBack = { viewer = null },
        )
        return
    }

    when (mode) {
        ReceiptMode.HUB -> {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack) { Text("返回") }
                Text("电子受理单", style = MaterialTheme.typography.headlineSmall)
                Text("手机账号与独立宽带共用联通官方电子受理单 H5；登录态按所选账号隔离。", style = MaterialTheme.typography.bodySmall)
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = state.targets.isNotEmpty()) { mode = ReceiptMode.TARGETS },
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("电子受理单查询", style = MaterialTheme.typography.titleMedium)
                        Text("可查询 ${state.targets.size} 个账号", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { mode = ReceiptMode.SAVED },
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("已保存的电子受理单", style = MaterialTheme.typography.titleMedium)
                        Text("本机已保存 ${state.savedReceipts.size} 份 PDF", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(onClick = { directoryLauncher.launch(null) }) { Text("选择 PDF 导出目录") }
                if (state.exportDirectoryUri != null) {
                    Text("已启用导出目录", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = viewModel::clearExportDirectory) { Text("取消自动导出") }
                }
                state.statusMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        ReceiptMode.TARGETS -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = { mode = ReceiptMode.HUB }) { Text("返回") }
                    Text("选择查询账号", style = MaterialTheme.typography.titleLarge)
                }
                if (state.targets.isEmpty()) Text("暂无可用手机或独立宽带账号", modifier = Modifier.padding(20.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.targets, key = { it.id }) { target ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable {
                                viewModel.activate(target.id)
                                mode = ReceiptMode.BROWSER
                            },
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 1.dp,
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(target.menuText, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (target.kind == ElectronicReceiptTargetKind.BROADBAND) "独立宽带 · H5 loginType 03" else "手机账号 · H5 loginType 01",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        ReceiptMode.BROWSER -> {
            val target = state.targets.firstOrNull { it.id == state.selectedTargetID }
            val session = viewModel.activeWebSession()?.takeIf { it.targetID == state.activeTargetID }
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = {
                        viewModel.reportPdfCandidate(null)
                        mode = ReceiptMode.TARGETS
                    }) { Text("返回") }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("电子受理单查询", style = MaterialTheme.typography.titleMedium)
                        Text(target?.maskedNumber.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { manualRefreshSerial += 1 }, enabled = session != null) { Text("刷新查询") }
                }
                state.pdfCandidate?.let { candidate ->
                    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), tonalElevation = 2.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("检测到受理单 PDF · ${candidate.acceptDate.take(8)}", modifier = Modifier.weight(1f))
                            Button(
                                enabled = !state.isSavingPdf,
                                onClick = {
                                    val browserCookie = CookieManager.getInstance().getCookie(candidate.urlString)
                                    viewModel.savePdf(candidate, browserCookie)
                                },
                            ) { Text(if (state.isSavingPdf) "保存中…" else "保存 PDF") }
                        }
                    }
                }
                state.statusMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) }
                state.errorMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error) }
                when {
                    state.isActivating -> Text("正在激活联通登录态…", modifier = Modifier.padding(20.dp))
                    session == null -> Text("登录态尚未就绪", modifier = Modifier.padding(20.dp))
                    else -> ElectronicReceiptWebView(
                        session = session,
                        activationSerial = state.activationSerial,
                        manualRefreshSerial = manualRefreshSerial,
                        onPdfCandidate = viewModel::reportPdfCandidate,
                        onError = viewModel::reportBrowserError,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        ReceiptMode.SAVED -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = { mode = ReceiptMode.HUB }) { Text("返回") }
                    Text("已保存的电子受理单", style = MaterialTheme.typography.titleLarge)
                }
                if (state.savedReceipts.isEmpty()) Text("暂无已保存 PDF", modifier = Modifier.padding(20.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.savedReceipts, key = { it.id }) { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 1.dp,
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("电子受理单 · ${item.dateText}", style = MaterialTheme.typography.titleMedium)
                                Text("账号 ${item.maskedNumber} · 订单 ${item.orderID.takeLast(10)}", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { viewer = item }) { Text("查看") }
                                    OutlinedButton(onClick = { viewModel.exportReceipt(item.id) }) { Text("导出") }
                                    OutlinedButton(onClick = { viewModel.deleteReceipt(item.id) }) { Text("删除") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ElectronicReceiptWebView(
    session: ElectronicReceiptWebSession,
    activationSerial: Long,
    manualRefreshSerial: Int,
    onPdfCandidate: (ElectronicReceiptPdfCandidate?) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var webView by remember(session.targetID, activationSerial) { mutableStateOf<WebView?>(null) }

    DisposableEffect(session.targetID, activationSerial) {
        onDispose {
            webView?.stopLoading()
            webView?.removeJavascriptInterface(RECEIPT_NATIVE_BRIDGE)
            webView?.destroy()
            webView = null
            CookieManager.getInstance().removeAllCookies(null)
        }
    }

    LaunchedEffect(manualRefreshSerial) {
        if (manualRefreshSerial > 0) webView?.evaluateJavascript(MANUAL_REFRESH_SCRIPT, null)
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                webView = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.userAgentString = session.userAgent
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.setSupportMultipleWindows(false)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                settings.allowFileAccess = false
                settings.allowContentAccess = true

                val nativeBridge = ReceiptNativeBridge(this, session)
                addJavascriptInterface(nativeBridge, RECEIPT_NATIVE_BRIDGE)
                webChromeClient = ReceiptBridgeChromeClient(session)
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        view.evaluateJavascript(BRIDGE_BOOTSTRAP_SCRIPT, null)
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        ElectronicReceiptPdfCandidate.from(url)?.let(onPdfCandidate)
                        return !isAllowedReceiptUrl(url)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        view.evaluateJavascript(BRIDGE_BOOTSTRAP_SCRIPT, null)
                        view.evaluateJavascript(verificationTriggerScript(session), null)
                        ElectronicReceiptPdfCandidate.from(url)?.let(onPdfCandidate)
                        view.evaluateJavascript(PDF_CANDIDATE_PROBE_SCRIPT) { raw ->
                            val candidateURL = decodeJavascriptString(raw)
                            ElectronicReceiptPdfCandidate.from(candidateURL)?.let(onPdfCandidate)
                        }
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) onError("电子受理单页面加载失败：${error.description}")
                    }
                }

                val manager = CookieManager.getInstance().apply { setAcceptCookie(true) }
                manager.setAcceptThirdPartyCookies(this, true)
                manager.removeAllCookies {
                    seedReceiptCookies(manager, session.cookieHeader) {
                        manager.flush()
                        post {
                            loadUrl(
                                ElectronicReceiptViewModel.RECEIPT_ENTRY_URL,
                                mapOf(
                                    "Cookie" to session.cookieHeader,
                                    "User-Agent" to session.userAgent,
                                    "Referer" to "https://img.client.10010.com/search2020/index.html#/",
                                    "Cache-Control" to "no-cache",
                                ),
                            )
                        }
                    }
                }
            }
        },
    )
}

private class ReceiptBridgeChromeClient(
    private val session: ElectronicReceiptWebSession,
) : WebChromeClient() {
    override fun onJsPrompt(
        view: WebView?,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResult?,
    ): Boolean {
        if (defaultValue != "MsJSBridge") return false
        val action = runCatching { JSONObject(message.orEmpty()).optString("action") }.getOrNull().orEmpty()
        val data: Any = bridgeData(action, session)
        val payload = JSONObject().put("data", data).toString().toByteArray(Charsets.UTF_8)
        result?.confirm(Base64.encodeToString(payload, Base64.NO_WRAP))
        return true
    }
}

private class ReceiptNativeBridge(
    private val webView: WebView,
    private val session: ElectronicReceiptWebSession,
) {
    @JavascriptInterface
    fun postMsBridge(message: String?) {
        val body = runCatching { JSONObject(message.orEmpty()) }.getOrNull() ?: return
        val action = body.optString("action")
        val callbackID = body.optString("callbackId")
        if (callbackID.isBlank()) return
        val data = bridgeData(action, session)
        val parameter = JSONObject().put("status", "success").put("data", data)
        val payload = JSONObject()
            .put("callbackId", callbackID)
            .put("isKeepAlive", false)
            .put("parameter", parameter)
            .toString()
        val encoded = Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val escaped = JSONObject.quote(encoded)
        webView.post {
            webView.evaluateJavascript("window.MsJSBridge&&window.MsJSBridge.callbackFromNative($escaped)", null)
        }
    }

    @JavascriptInterface
    fun postHost(message: String?) {
        val body = runCatching { JSONObject(message.orEmpty()) }.getOrNull() ?: return
        if (body.optString("action") == "verificationRequired") {
            webView.post { webView.evaluateJavascript("window.__cuForceReceiptVerification&&window.__cuForceReceiptVerification()", null) }
        }
    }
}

private fun bridgeData(action: String, session: ElectronicReceiptWebSession): Any = when (action) {
    "isLogin" -> true
    "fontSizeModel" -> "0"
    "getLanguageInfo" -> "chinese"
    "isEdop" -> false
    "getClientInfo" -> clientInfo(session)
    else -> JSONObject()
}

private fun clientInfo(session: ElectronicReceiptWebSession): JSONObject {
    val cookies = JSONArray()
    session.cookieHeader.split(';').forEach { part ->
        val index = part.indexOf('=')
        if (index <= 0) return@forEach
        val name = part.substring(0, index).trim().lowercase(Locale.ROOT)
        val value = part.substring(index + 1).trim()
        if (name.isNotEmpty() && value.isNotEmpty()) cookies.put(JSONObject().put("key", name).put("value", value))
    }
    return JSONObject()
        .put("currentPhoneNumber", session.serviceNumber)
        .put("loginType", session.loginType)
        .put("isLogin", true)
        .put("isLoginOn", "1")
        .put("clientVersion", "12.1300")
        .put("appVersion", "12.1300")
        .put("provinceCode_1", session.provinceCode)
        .put("cityCode_1", session.cityCode)
        .put("locateProvinceCode", session.provinceCode)
        .put("locateCityCode", session.cityCode)
        .put("statusBarHeight", 0)
        .put("deviceId", session.deviceCode)
        .put("deviceCode", session.deviceCode)
        .put("devicedId", session.deviceCode)
        .put("imei", session.deviceCode)
        .put("deviceBrand", "iphone")
        .put("cookies", cookies)
}

private fun seedReceiptCookies(manager: CookieManager, header: String, onComplete: () -> Unit) {
    val pairs = header.split(';').map(String::trim).filter { it.contains('=') }
    if (pairs.isEmpty()) {
        onComplete()
        return
    }
    val targets = buildList {
        pairs.forEach { pair ->
            add("https://10010.com/" to "$pair; Domain=.10010.com; Path=/; Secure")
            listOf(
                "https://imgxx.client.10010.com/",
                "https://img.client.10010.com/",
                "https://mxx.client.10010.com/",
                "https://m.client.10010.com/",
                "https://loginhl.10010.com/",
                "https://smartad.10010.com/",
            ).forEach { host -> add(host to "$pair; Path=/; Secure") }
        }
    }
    val remaining = AtomicInteger(targets.size)
    targets.forEach { (url, value) ->
        manager.setCookie(url, value) {
            if (remaining.decrementAndGet() == 0) onComplete()
        }
    }
}

private fun isAllowedReceiptUrl(url: String): Boolean {
    val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return false
    if (uri.scheme in listOf("about", "data", "blob")) return true
    if (uri.scheme !in listOf("http", "https")) return false
    val host = uri.host?.lowercase(Locale.ROOT) ?: return false
    return host == "10010.com" || host.endsWith(".10010.com") ||
        host == "chinaunicom.cn" || host.endsWith(".chinaunicom.cn") ||
        host == "newbuy.chinaunicom.cn" || host.endsWith(".newbuy.chinaunicom.cn")
}

private fun decodeJavascriptString(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value == "null" || value == "undefined" || value.isEmpty()) return ""
    return runCatching { JSONArray("[$value]").optString(0) }.getOrDefault(value.trim('"'))
}

private fun verificationTriggerScript(session: ElectronicReceiptWebSession): String {
    val identity = JSONObject()
        .put("serviceNumber", session.serviceNumber)
        .put("loginType", session.loginType)
        .toString()
    return """
(function(){
  const identity=$identity;
  window.__cuReceiptVerificationIdentity=identity;
  function rootVM(){const root=document.querySelector('#app');return root&&root.__vue__;}
  function find(vm,depth){
    if(!vm||depth>14)return null;
    if(('showVarify' in vm)&&typeof vm.frequentFetch==='function'&&(typeof vm.willQuery==='function'||typeof vm.chaxunsix==='function'))return vm;
    const children=vm.${'$'}children||[];for(let i=0;i<children.length;i++){const match=find(children[i],depth+1);if(match)return match;}return null;
  }
  function assign(vm,key,value){if(!vm||value===undefined||value===null)return;if(typeof vm.${'$'}set==='function')vm.${'$'}set(vm,key,value);else vm[key]=value;}
  function amend(vm,values){if(vm&&typeof vm.amend==='function')vm.amend(values);else if(vm&&vm.${'$'}store&&typeof vm.${'$'}store.commit==='function')vm.${'$'}store.commit('amend',values);else if(vm&&vm.${'$'}store&&vm.${'$'}store.state)Object.assign(vm.${'$'}store.state,values);}
  function masked(value){const text=String(value||'');return text.length>7?text.slice(0,3)+'****'+text.slice(-4):text;}
  function sync(vm){if(!vm)return;const current=window.__cuReceiptVerificationIdentity||identity;assign(vm,'loginNumber',current.serviceNumber||'');assign(vm,'loginType',current.loginType||'01');if(!vm.resetNumber)assign(vm,'resetNumber',masked(current.serviceNumber));amend(vm,{interNo:'hasLoggedOn',loginType:current.loginType||'01'});}
  function hasData(vm){return !!((Array.isArray(vm.datalists1)&&vm.datalists1.length)||(Array.isArray(vm.datalists2)&&vm.datalists2.length)||(vm.${'$'}store&&vm.${'$'}store.state&&Array.isArray(vm.${'$'}store.state.datalist)&&vm.${'$'}store.state.datalist.length));}
  function resolved(vm){if(!vm)return false;if(vm.showVarify||vm.showIdLogin||hasData(vm))return true;const route=String(location.hash||location.href||'').toLowerCase();return route.indexOf('unbindquery')>=0||route.indexOf('pdfviwes')>=0||route.indexOf('pdfviews')>=0;}
  function force(vm){if(!vm)return false;sync(vm);assign(vm,'errShow',false);assign(vm,'kongshow',false);assign(vm,'nokongshow',false);assign(vm,'showIdLogin',false);assign(vm,'showFaceRegister',false);assign(vm,'checkerr',false);assign(vm,'showNumbererr',false);assign(vm,'showovererrtime',false);assign(vm,'showVarify',true);if(vm.${'$'}store&&vm.${'$'}store.state)vm.${'$'}store.state.showVarify=null;if(typeof vm.${'$'}pageLoading==='function')vm.${'$'}pageLoading(false);document.body.style.overflow='auto';if(typeof vm.${'$'}forceUpdate==='function')vm.${'$'}forceUpdate();return true;}
  window.__cuForceReceiptVerification=function(){return force(find(rootVM(),0));};
  const vm=find(rootVM(),0);if(vm)sync(vm);
  if(!window.__cuReceiptVerificationObserverInstalled){
    window.__cuReceiptVerificationObserverInstalled=true;
    function report(text){if(String(text||'').indexOf('ECS0436')>=0)setTimeout(function(){window.__cuForceReceiptVerification();},0);}
    const originalFetch=window.fetch;if(typeof originalFetch==='function'){window.fetch=function(){const args=arguments;return originalFetch.apply(this,args).then(function(response){try{response.clone().text().then(report).catch(function(){});}catch(e){}return response;});};}
    const open=XMLHttpRequest.prototype.open,send=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.open=function(method,url){this.__cuReceiptURL=String(url||'');return open.apply(this,arguments);};XMLHttpRequest.prototype.send=function(){this.addEventListener('load',function(){report(this.responseText);});return send.apply(this,arguments);};
  }
  if(window.__cuReceiptVerificationTriggerInstalled)return;
  window.__cuReceiptVerificationTriggerInstalled=true;
  const started=Date.now();
  setTimeout(function(){const current=find(rootVM(),0);if(!current||resolved(current))return;sync(current);if(typeof current.frequentFetch==='function'){try{current.frequentFetch();}catch(e){}}},1200);
  const watchdog=setInterval(function(){const current=find(rootVM(),0);const elapsed=Date.now()-started;if(!current){if(elapsed>=12000)clearInterval(watchdog);return;}sync(current);if(resolved(current)){clearInterval(watchdog);return;}if(elapsed>=8000){force(current);clearInterval(watchdog);}},250);
})();
""".trimIndent()
}

private const val RECEIPT_NATIVE_BRIDGE = "ElectronicReceiptNative"

private const val BRIDGE_BOOTSTRAP_SCRIPT = """
(function(){
  window.webkit=window.webkit||{};window.webkit.messageHandlers=window.webkit.messageHandlers||{};
  window.webkit.messageHandlers.MsJSBridge={postMessage:function(body){try{window.ElectronicReceiptNative.postMsBridge(typeof body==='string'?body:JSON.stringify(body));}catch(e){}}};
  window.webkit.messageHandlers.ElectronicReceiptHost={postMessage:function(body){try{window.ElectronicReceiptNative.postHost(typeof body==='string'?body:JSON.stringify(body));}catch(e){}}};
})();
"""

private const val PDF_CANDIDATE_PROBE_SCRIPT = """
(function(){
  function findPDFViewModel(vm,depth){if(!vm||depth>8)return null;if(vm.id&&vm.date&&vm.${'$'}store)return vm;const children=vm.${'$'}children||[];for(let i=0;i<children.length;i++){const match=findPDFViewModel(children[i],depth+1);if(match)return match;}return null;}
  function valueOf(){for(let i=0;i<arguments.length;i++){const v=arguments[i];if(v!==undefined&&v!==null&&String(v).length>0)return String(v);}return '';}
  const root=document.querySelector('#app');const rootVM=root&&root.__vue__;const vm=findPDFViewModel(rootVM,0);const store=(vm&&vm.${'$'}store)||(rootVM&&rootVM.${'$'}store);const state=(store&&store.state)||{};const item=state.listitem||{};
  const orderID=valueOf(vm&&vm.id,item.orderid,item.orderId);const acceptDate=valueOf(vm&&vm.date,item.acceptdate,item.acceptDate);if(!orderID||!acceptDate)return '';
  const host=String(location.host||'').toLowerCase();let origin='https://m.client.10010.com';if(host.indexOf('imgxx.client.10010.com')>=0)origin='https://mxx.client.10010.com';else if(host.indexOf('hlbasic')>=0)origin='https://hlbasic.10010.com';
  const p=new URLSearchParams();p.set('orderId',orderID);p.set('acceptDate',acceptDate);p.set('busiorder',valueOf(vm&&vm.busiorder,item.busiorder));p.set('type',valueOf(vm&&vm.type,state.type));p.set('jumpProvinceCode',valueOf(state.jumpProvinceCode));p.set('servicenumber',valueOf(vm&&vm.servicenumber,state.servicenumber));
  return origin+'/servicequerybusiness/queryNoPaper/noPaperDetailPdfByUser?'+p.toString();
})();
"""

private const val MANUAL_REFRESH_SCRIPT = """
(function(){
  function find(vm,depth){if(!vm||depth>14)return null;if(typeof vm.chaxunsix==='function'||typeof vm.willQuery==='function')return vm;const children=vm.${'$'}children||[];for(let i=0;i<children.length;i++){const match=find(children[i],depth+1);if(match)return match;}return null;}
  const root=document.querySelector('#app');const vm=find(root&&root.__vue__,0);if(!vm)return 'missing';try{if(typeof vm.chaxunsix==='function')vm.chaxunsix();else if(typeof vm.willQuery==='function')vm.willQuery();else return 'missing';return 'triggered';}catch(e){return 'failed';}
})();
"""

@Composable
private fun ElectronicReceiptPdfViewer(
    file: File,
    title: String,
    onBack: () -> Unit,
) {
    val holder = remember(file.absolutePath) {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        descriptor to PdfRenderer(descriptor)
    }
    val renderer = holder.second
    var pageIndex by remember(file.absolutePath) { mutableIntStateOf(0) }
    var bitmap by remember(file.absolutePath) { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(renderer) {
        onDispose {
            renderer.close()
            holder.first.close()
        }
    }
    LaunchedEffect(pageIndex, renderer) {
        bitmap = withContext(Dispatchers.Default) {
            renderer.openPage(pageIndex).use { page ->
                val width = 1440
                val height = (width.toDouble() * page.height / page.width).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { output ->
                    page.render(output, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${pageIndex + 1}/${renderer.pageCount}")
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { if (pageIndex > 0) pageIndex -= 1 }, enabled = pageIndex > 0) { Text("上一页") }
            OutlinedButton(onClick = { if (pageIndex + 1 < renderer.pageCount) pageIndex += 1 }, enabled = pageIndex + 1 < renderer.pageCount) { Text("下一页") }
        }
        bitmap?.let { image ->
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit,
            )
        } ?: Text("正在渲染 PDF…", modifier = Modifier.padding(20.dp))
    }
}
