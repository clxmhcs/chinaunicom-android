package com.clxmhcs.chinaunicom.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
import androidx.compose.foundation.layout.weight
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
import com.clxmhcs.chinaunicom.core.network.UnicomLoginCaptchaChallenge
import java.util.concurrent.atomic.AtomicBoolean
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
                }

                CaptchaWebView(
                    challenge = challenge,
                    cookieHeader = cookieHeader,
                    userAgent = userAgent,
                    systemInfo = systemInfo,
                    modifier = Modifier.weight(1f),
                    onWebViewReady = { webView = it },
                    onResultToken = onResultToken,
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
            createdWebView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.userAgentString = userAgent

                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
                cookieHeader.split(';')
                    .map(String::trim)
                    .filter { it.contains('=') }
                    .forEach { cookieManager.setCookie(challenge.url, it) }
                cookieManager.flush()

                addJavascriptInterface(bridge, JS_BRIDGE_NAME)
                webChromeClient = object : WebChromeClient() {
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
                        view.evaluateJavascript(INTERCEPTOR_SCRIPT, null)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        view.evaluateJavascript(INTERCEPTOR_SCRIPT, null)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            onError(error?.description?.toString().orEmpty().ifBlank { "网络错误" })
                        }
                    }
                }
                loadUrl(challenge.url)
            }
            onWebViewReady(createdWebView)
            createdWebView
        },
        update = { view ->
            if (view.url.isNullOrBlank()) view.loadUrl(challenge.url)
        },
    )
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
