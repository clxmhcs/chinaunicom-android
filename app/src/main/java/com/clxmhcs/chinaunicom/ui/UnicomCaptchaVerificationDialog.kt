package com.clxmhcs.chinaunicom.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.clxmhcs.chinaunicom.core.network.UnicomLoginCaptchaChallenge
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject

@Composable
internal fun UnicomCaptchaVerificationDialog(
    challenge: UnicomLoginCaptchaChallenge,
    cookieHeader: String,
    userAgent: String,
    systemInfo: Map<String, String>,
    onResultToken: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var webView by remember(challenge.url) { mutableStateOf<WebView?>(null) }
    var errorMessage by remember(challenge.url) { mutableStateOf<String?>(null) }
    var loadStatus by remember(challenge.url) { mutableStateOf("准备加载验证页面") }

    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                    Text(
                        text = "安全验证",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(64.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = challenge.title,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = challenge.message,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "验证页面状态：$loadStatus",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                CaptchaWebView(
                    challenge = challenge,
                    cookieHeader = cookieHeader,
                    userAgent = userAgent,
                    systemInfo = systemInfo,
                    modifier = Modifier.weight(1f),
                    onWebViewReady = { webView = it },
                    onResultToken = onResultToken,
                    onStatus = { loadStatus = it },
                    onError = { errorMessage = it },
                )

                errorMessage?.let { message ->
                    Text(
                        text = "验证页面加载失败：$message",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView?.apply {
                stopLoading()
                removeJavascriptInterface(JS_BRIDGE_NAME)
                destroy()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CaptchaWebView(
    challenge: UnicomLoginCaptchaChallenge,
    cookieHeader: String,
    userAgent: String,
    systemInfo: Map<String, String>,
    modifier: Modifier,
    onWebViewReady: (WebView) -> Unit,
    onResultToken: (String) -> Unit,
    onStatus: (String) -> Unit,
    onError: (String) -> Unit,
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            lateinit var createdWebView: WebView
            val bridge = CaptchaBridge(
                challenge = challenge,
                systemInfo = systemInfo,
                webView = { createdWebView },
                onResultToken = onResultToken,
            )
            val mainFrameFinished = AtomicBoolean(false)
            createdWebView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.userAgentString = userAgent

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                addJavascriptInterface(bridge, JS_BRIDGE_NAME)
                val documentStartBridgeInstalled = installDocumentStartBridgeIfSupported(this)

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        if (!mainFrameFinished.get()) {
                            onStatus("正在加载验证页：$newProgress%")
                        }
                    }

                    override fun onJsPrompt(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        defaultValue: String?,
                        result: android.webkit.JsPromptResult?,
                    ): Boolean {
                        if (defaultValue != "MsJSBridge" || result == null) return false
                        val objectValue = runCatching { JSONObject(message.orEmpty()) }.getOrNull()
                        val action = objectValue?.optString("action").orEmpty()
                        result.confirm(encodedSyncBridgeResponse(bridgeData(action, challenge, systemInfo)))
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        mainFrameFinished.set(false)
                        val bridgeMode = if (documentStartBridgeInstalled) "document-start" else "fallback"
                        onStatus("主页面开始加载（bridge=$bridgeMode）")
                        schedulePageLoadWatchdog(
                            webView = view,
                            mainFrameFinished = mainFrameFinished,
                            onStatus = onStatus,
                            onError = onError,
                        )
                        if (!documentStartBridgeInstalled) {
                            view.evaluateJavascript(INTERCEPTOR_SCRIPT, null)
                        }
                    }

                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        super.onPageCommitVisible(view, url)
                        onStatus("主页面已提交显示，等待验证组件")
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        mainFrameFinished.set(true)
                        if (!documentStartBridgeInstalled) {
                            view.evaluateJavascript(INTERCEPTOR_SCRIPT, null)
                        }
                        onStatus("主页面加载完成，检查验证组件")
                        scheduleBlankPageProbe(view, onStatus, onError)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            mainFrameFinished.set(true)
                            val code = error?.errorCode
                            val detail = error?.description?.toString().orEmpty().ifBlank { "网络错误" }
                            onStatus("主页面网络失败")
                            onError("网络错误${code?.let { "($it)" }.orEmpty()}：$detail")
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (request?.isForMainFrame == true) {
                            mainFrameFinished.set(true)
                            onStatus("主页面 HTTP 失败")
                            onError("HTTP ${errorResponse?.statusCode ?: "未知"}")
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?,
                    ) {
                        mainFrameFinished.set(true)
                        handler?.cancel()
                        onStatus("SSL 证书校验失败")
                        onError("SSL 证书校验失败(${error?.primaryError ?: "未知"})")
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail?,
                    ): Boolean {
                        mainFrameFinished.set(true)
                        onStatus("WebView 渲染进程已退出")
                        onError(
                            if (detail?.didCrash() == true) {
                                "WebView 渲染进程崩溃"
                            } else {
                                "WebView 渲染进程被系统终止"
                            },
                        )
                        return true
                    }
                }

                installCookiesThenLoad(
                    webView = this,
                    cookieManager = cookieManager,
                    cookieHeader = cookieHeader,
                    targetUrl = challenge.url,
                    onStatus = onStatus,
                    onError = onError,
                )
            }
            onWebViewReady(createdWebView)
            createdWebView
        },
        update = { /* Initial load is intentionally owned by the cookie-install completion path. */ },
    )
}

private fun installDocumentStartBridgeIfSupported(webView: WebView): Boolean {
    if (!supportsDocumentStartScript()) return false
    return runCatching {
        WebViewCompat.addDocumentStartJavaScript(
            webView,
            INTERCEPTOR_SCRIPT,
            setOf("*"),
        )
        true
    }.getOrDefault(false)
}

private fun supportsDocumentStartScript(): Boolean =
    WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

private fun installCookiesThenLoad(
    webView: WebView,
    cookieManager: CookieManager,
    cookieHeader: String,
    targetUrl: String,
    onStatus: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val cookies = cookieHeader.split(';')
        .map(String::trim)
        .filter { it.contains('=') }

    if (cookies.isEmpty()) {
        onStatus("没有会话 Cookie，直接加载验证页")
        webView.loadUrl(targetUrl)
        return
    }

    onStatus("正在写入 ${cookies.size} 个会话 Cookie")
    val remaining = AtomicInteger(cookies.size)
    val failed = AtomicBoolean(false)
    cookies.forEach { cookie ->
        cookieManager.setCookie(
            UNICOM_COOKIE_SEED_URL,
            "$cookie; Domain=.10010.com; Path=/; Secure",
        ) { success ->
            if (!success) failed.set(true)
            if (remaining.decrementAndGet() == 0) {
                cookieManager.flush()
                webView.post {
                    if (failed.get()) {
                        onStatus("Cookie 写入不完整，仍尝试加载验证页")
                        onError("登录 Cookie 未全部写入 WebView")
                    } else {
                        onStatus("Cookie 写入完成，开始加载验证页")
                    }
                    webView.loadUrl(targetUrl)
                }
            }
        }
    }
}

private fun schedulePageLoadWatchdog(
    webView: WebView,
    mainFrameFinished: AtomicBoolean,
    onStatus: (String) -> Unit,
    onError: (String) -> Unit,
) {
    Handler(Looper.getMainLooper()).postDelayed({
        if (!webView.isAttachedToWindow || mainFrameFinished.get()) return@postDelayed
        onStatus("主页面加载超时")
        onError("主页面 ${PAGE_LOAD_TIMEOUT_MILLIS / 1_000} 秒内未完成加载")
    }, PAGE_LOAD_TIMEOUT_MILLIS)
}

private fun scheduleBlankPageProbe(
    webView: WebView,
    onStatus: (String) -> Unit,
    onError: (String) -> Unit,
) {
    Handler(Looper.getMainLooper()).postDelayed({
        probeRenderedPage(
            webView = webView,
            onResult = { result ->
                if (!result.isVisuallyEmpty) {
                    onStatus("验证组件已渲染")
                    return@probeRenderedPage
                }
                onStatus(
                    "DOM 已加载但尚未渲染组件（ready=${result.readyState}, child=${result.childCount}）",
                )
                Handler(Looper.getMainLooper()).postDelayed({
                    probeRenderedPage(
                        webView = webView,
                        onResult = { finalResult ->
                            if (finalResult.isVisuallyEmpty) {
                                onStatus("DOM 存在但验证组件仍未渲染")
                                onError(
                                    "页面已加载但验证组件未渲染" +
                                        "（ready=${finalResult.readyState}, child=${finalResult.childCount}, " +
                                        "html=${finalResult.htmlLength}, visible=${finalResult.visibleControlCount}）",
                                )
                            } else {
                                onStatus("验证组件已延迟渲染")
                            }
                        },
                        onProbeFailure = {
                            onStatus("无法读取验证页 DOM 状态")
                            onError("无法读取验证页 DOM 状态")
                        },
                    )
                }, SECOND_BLANK_PAGE_PROBE_DELAY_MILLIS)
            },
            onProbeFailure = {
                onStatus("无法读取验证页 DOM 状态")
                onError("无法读取验证页 DOM 状态")
            },
        )
    }, FIRST_BLANK_PAGE_PROBE_DELAY_MILLIS)
}

private data class CaptchaPageProbe(
    val readyState: String,
    val textLength: Int,
    val childCount: Int,
    val htmlLength: Int,
    val visibleControlCount: Int,
) {
    val isVisuallyEmpty: Boolean
        get() = textLength == 0 && visibleControlCount == 0
}

private fun probeRenderedPage(
    webView: WebView,
    onResult: (CaptchaPageProbe) -> Unit,
    onProbeFailure: () -> Unit,
) {
    if (!webView.isAttachedToWindow) return
    webView.evaluateJavascript(PAGE_PROBE_SCRIPT) { raw ->
        val value = raw?.trim('"').orEmpty()
        val parts = value.split('|')
        if (parts.size != 5) {
            onProbeFailure()
            return@evaluateJavascript
        }
        val result = CaptchaPageProbe(
            readyState = parts[0],
            textLength = parts[1].toIntOrNull() ?: -1,
            childCount = parts[2].toIntOrNull() ?: -1,
            htmlLength = parts[3].toIntOrNull() ?: -1,
            visibleControlCount = parts[4].toIntOrNull() ?: -1,
        )
        if (
            result.textLength < 0 ||
            result.childCount < 0 ||
            result.htmlLength < 0 ||
            result.visibleControlCount < 0
        ) {
            onProbeFailure()
        } else {
            onResult(result)
        }
    }
}

private class CaptchaBridge(
    private val challenge: UnicomLoginCaptchaChallenge,
    private val systemInfo: Map<String, String>,
    private val webView: () -> WebView,
    private val onResultToken: (String) -> Unit,
) {
    private val completed = AtomicBoolean(false)

    @JavascriptInterface
    fun postMessage(name: String, rawBody: String) {
        val objectValue = runCatching { JSONObject(rawBody) }.getOrNull() ?: return
        if (name == "unicomCaptcha") {
            complete(objectValue.optString("resultToken"))
            return
        }
        if (name != "MsJSBridge") return

        val action = objectValue.optString("action")
        if (action == "loginResultToken") {
            complete(objectValue.optJSONObject("parameter")?.optString("resultToken").orEmpty())
        }

        val callbackID = objectValue.optString("callbackId")
        if (callbackID.isBlank()) return
        val envelope = JSONObject()
            .put("callbackId", callbackID)
            .put(
                "parameter",
                JSONObject()
                    .put("status", "success")
                    .put("data", bridgeData(action, challenge, systemInfo))
                    .put("isKeepAlive", false),
            )
        val encoded = encodedBridgeEnvelope(envelope)
        webView().post {
            webView().evaluateJavascript(
                "window.MsJSBridge && window.MsJSBridge.callbackFromNative('$encoded');",
                null,
            )
        }
    }

    private fun complete(token: String) {
        val normalized = token.trim()
        if (normalized.isEmpty() || !completed.compareAndSet(false, true)) return
        webView().post { onResultToken(normalized) }
    }
}

private fun bridgeData(
    action: String,
    challenge: UnicomLoginCaptchaChallenge,
    systemInfo: Map<String, String>,
): Any = when (action) {
    "navigateParams" -> JSONObject().put("viewStrongValue", JSONObject(challenge.bridgePayload))
    "getSystemInfo" -> JSONObject(systemInfo)
    "getUserInfo" -> JSONObject(challenge.bridgePayload)
    "isLogin" -> true
    else -> JSONObject()
}

private fun encodedSyncBridgeResponse(data: Any): String =
    encodedBridgeEnvelope(JSONObject().put("data", data))

private fun encodedBridgeEnvelope(objectValue: JSONObject): String {
    val encoded = buildString {
        objectValue.toString().toByteArray(Charsets.UTF_8).forEach { byte ->
            val value = byte.toInt() and 0xff
            val isAlphaNumeric = value in 'A'.code..'Z'.code ||
                value in 'a'.code..'z'.code || value in '0'.code..'9'.code
            if (isAlphaNumeric) append(value.toChar()) else append("%%%02X".format(value))
        }
    }
    return Base64.encodeToString(encoded.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}

private const val JS_BRIDGE_NAME = "UnicomCaptchaNative"
private const val UNICOM_COOKIE_SEED_URL = "https://m.client.10010.com/"
private const val PAGE_LOAD_TIMEOUT_MILLIS = 12_000L
private const val FIRST_BLANK_PAGE_PROBE_DELAY_MILLIS = 1_500L
private const val SECOND_BLANK_PAGE_PROBE_DELAY_MILLIS = 3_500L

private const val PAGE_PROBE_SCRIPT = """
(function() {
  var b = document.body;
  if (!b) return [document.readyState || 'none', 0, 0, 0, 0].join('|');
  var candidates = b.querySelectorAll('iframe,canvas,img,button,input,textarea,select,video,[role="button"],[role="dialog"]');
  var visible = 0;
  for (var i = 0; i < candidates.length; i++) {
    var e = candidates[i];
    var r = e.getBoundingClientRect();
    var s = window.getComputedStyle(e);
    if (r.width > 1 && r.height > 1 && s.display !== 'none' && s.visibility !== 'hidden' && s.opacity !== '0') {
      visible++;
    }
  }
  return [
    document.readyState || 'unknown',
    (b.innerText || '').trim().length,
    b.childElementCount,
    (b.innerHTML || '').length,
    visible
  ].join('|');
})()
"""

private const val INTERCEPTOR_SCRIPT = """
(function() {
  if (window.__unicomCaptchaHookInstalled) return;
  window.__unicomCaptchaHookInstalled = true;

  function nativePost(name, value) {
    try {
      var body = typeof value === 'string' ? value : JSON.stringify(value);
      window.UnicomCaptchaNative.postMessage(name, body);
    } catch (_) {}
  }

  window.webkit = window.webkit || {};
  window.webkit.messageHandlers = window.webkit.messageHandlers || {};
  window.webkit.messageHandlers.unicomCaptcha = {
    postMessage: function(body) { nativePost('unicomCaptcha', body); }
  };
  window.webkit.messageHandlers.MsJSBridge = {
    postMessage: function(body) { nativePost('MsJSBridge', body); }
  };

  function inspect(value) {
    try {
      var object = typeof value === 'string' ? JSON.parse(value) : value;
      var token = object && object.data && object.data.resultToken;
      if (token) nativePost('unicomCaptcha', { resultToken: token });
    } catch (_) {}
  }

  var originalFetch = window.fetch;
  if (originalFetch) {
    window.fetch = function() {
      return originalFetch.apply(this, arguments).then(function(response) {
        try { response.clone().text().then(inspect); } catch (_) {}
        return response;
      });
    };
  }

  var originalOpen = XMLHttpRequest.prototype.open;
  var originalSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function(method, url) {
    this.__unicomURL = url || '';
    return originalOpen.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function() {
    this.addEventListener('load', function() {
      if ((this.__unicomURL || '').indexOf('validateTencentCaptcha') >= 0) {
        inspect(this.responseText);
      }
    });
    return originalSend.apply(this, arguments);
  };
})();
"""
