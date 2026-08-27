package com.clxmhcs.chinaunicom.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.webkit.CookieManager
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
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.userAgentString = session.userAgent
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.setSupportMultipleWindows(false)
                webChromeClient = ReceiptBridgeChromeClient(session)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        ElectronicReceiptPdfCandidate.from(url)?.let(onPdfCandidate)
                        return !isAllowedReceiptUrl(url)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
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
                manager.removeAllCookies {
                    seedReceiptCookies(manager, session.cookieHeader)
                    manager.flush()
                    post {
                        loadUrl(
                            ElectronicReceiptViewModel.RECEIPT_ENTRY_URL,
                            mapOf(
                                "Cookie" to session.cookieHeader,
                                "User-Agent" to session.userAgent,
                                "Referer" to "https://img.client.10010.com/search2020/index.html#/",
                            ),
                        )
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
        val data: Any = when (action) {
            "isLogin" -> true
            "fontSizeModel" -> "0"
            "getLanguageInfo" -> "chinese"
            "isEdop" -> false
            "getClientInfo" -> clientInfo(session)
            else -> JSONObject()
        }
        val payload = JSONObject().put("data", data).toString().toByteArray(Charsets.UTF_8)
        result?.confirm(Base64.encodeToString(payload, Base64.NO_WRAP))
        return true
    }
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
        .put("deviceBrand", "android")
        .put("cookies", cookies)
}

private fun seedReceiptCookies(manager: CookieManager, header: String) {
    val hosts = listOf(
        "https://m.client.10010.com/",
        "https://mxx.client.10010.com/",
        "https://img.client.10010.com/",
        "https://imgxx.client.10010.com/",
        "https://loginhl.10010.com/",
        "https://smartad.10010.com/",
    )
    header.split(';').map(String::trim).filter { it.contains('=') }.forEach { pair ->
        hosts.forEach { host -> manager.setCookie(host, "$pair; Path=/; Secure") }
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
