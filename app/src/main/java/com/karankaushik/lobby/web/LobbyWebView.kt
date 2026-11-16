package com.karankaushik.lobby.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.karankaushik.lobby.BrowserCommand
import com.karankaushik.lobby.BrowserController
import com.karankaushik.lobby.policy.SitePolicy
import java.io.ByteArrayInputStream

class LobbyJsBridge(private val controller: BrowserController) {
    @android.webkit.JavascriptInterface
    fun postPopup(kind: String) {
        controller.onPopupBlocked(true)
    }

    @android.webkit.JavascriptInterface
    fun postHls(payload: String) {
        controller.onHlsMessage(payload)
    }

    @android.webkit.JavascriptInterface
    fun allowedHosts(): String = controller.allowedHostsJson()

    @android.webkit.JavascriptInterface
    fun downloadsEnabled(): Boolean = controller.downloadsEnabled()
}

@SuppressLint("SetJavaScriptEnabled")
fun createLobbyWebView(context: Context, controller: BrowserController): SwipeRefreshLayout {
    val webView = WebView(context)
    val swipe = SwipeRefreshLayout(context)
    webView.setBackgroundColor(0xFF0E120E.toInt())
    webView.isVerticalScrollBarEnabled = true
    val settings = webView.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.loadsImagesAutomatically = true
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportMultipleWindows(true)
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.setGeolocationEnabled(false)
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        settings.safeBrowsingEnabled = true
    }
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    controller.userAgent = settings.userAgentString
    webView.addJavascriptInterface(LobbyJsBridge(controller), "LobbyBridge")
    installDocumentScript(webView)
    webView.webViewClient = LobbyWebClient(controller, webView)
    webView.webChromeClient = LobbyChromeClient(controller, webView)
    webView.setDownloadListener { _, _, _, _, _ -> controller.onDownloadBlocked() }
    swipe.setOnRefreshListener { controller.reload() }
    swipe.setColorSchemeColors(0xFF95FF50.toInt())
    swipe.setProgressBackgroundColorSchemeColor(0xFF1A221A.toInt())
    webView.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    swipe.addView(webView)
    return swipe
}

fun applyBrowserCommand(swipe: SwipeRefreshLayout, command: BrowserCommand, controller: BrowserController) {
    val webView = swipe.getChildAt(0) as? WebView ?: return
    when (command) {
        BrowserCommand.Back -> if (webView.canGoBack()) webView.goBack()
        BrowserCommand.Forward -> if (webView.canGoForward()) webView.goForward()
        BrowserCommand.Stop -> {
            webView.stopLoading()
            swipe.isRefreshing = false
        }
        BrowserCommand.Reload -> {
            swipe.isRefreshing = true
            if (webView.url.isNullOrBlank()) {
                webView.loadUrl(controller.selectedSource.home)
            } else {
                webView.reload()
            }
        }
        is BrowserCommand.Load -> webView.loadUrl(command.url)
        BrowserCommand.ClearData -> {
            CookieManager.getInstance().removeAllCookies {
                webView.post {
                    webView.clearCache(true)
                    webView.clearHistory()
                    WebStorage.getInstance().deleteAllData()
                    webView.loadUrl(controller.selectedSource.home)
                }
            }
            webView.clearCache(true)
            webView.clearHistory()
            WebStorage.getInstance().deleteAllData()
        }
    }
}

private fun installDocumentScript(webView: WebView) {
    val script = PageScripts.source()
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        }
    }
}

private class LobbyWebClient(
    private val controller: BrowserController,
    private val webView: WebView
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url?.toString() ?: return true
        if (SitePolicy.hasDangerousExtension(url)) {
            controller.onDownloadBlocked()
            return true
        }
        val mainFrame = request.isForMainFrame
        return when (SitePolicy.decision(url, mainFrame, controller.selectedSource.allowedHosts)) {
            SitePolicy.Decision.ALLOW -> false
            SitePolicy.Decision.BLOCK_POPUP -> {
                controller.onPopupBlocked(mainFrame)
                true
            }
        }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url ?: return null
        val host = url.host
        if (host != null && SitePolicy.isBlockedHost(host)) {
            return emptyResponse()
        }
        val target = url.toString()
        if (!request.isForMainFrame && SitePolicy.hasDangerousExtension(target)) {
            controller.onDownloadBlocked()
            return emptyResponse()
        }
        return null
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        controller.onPageStarted(url)
        view.evaluateJavascript(PageScripts.source(), null)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        val parent = view.parent
        if (parent is SwipeRefreshLayout) parent.isRefreshing = false
        controller.onPageFinished(url, view.title)
        controller.onHistory(view.canGoBack(), view.canGoForward(), url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        controller.onHistory(view.canGoBack(), view.canGoForward(), url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: android.webkit.WebResourceError
    ) {
        if (!request.isForMainFrame) return
        if (error.errorCode == ERROR_UNKNOWN) return
        controller.onError(messageFor(error.errorCode))
    }

    private fun messageFor(code: Int): String = when (code) {
        ERROR_HOST_LOOKUP, ERROR_CONNECT, ERROR_IO -> "You're offline. Check the connection and try again."
        ERROR_TIMEOUT -> "The connection timed out."
        ERROR_FAILED_SSL_HANDSHAKE -> "The secure connection was rejected."
        else -> "This page didn't load."
    }

    private fun emptyResponse() = WebResourceResponse(
        "text/plain",
        "utf-8",
        ByteArrayInputStream(ByteArray(0))
    )
}

private class LobbyChromeClient(
    private val controller: BrowserController,
    private val webView: WebView
) : WebChromeClient() {
    private var customView: View? = null
    private var customCallback: CustomViewCallback? = null

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        controller.onProgress(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        controller.onTitle(title, view.url)
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message
    ): Boolean {
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val popup = WebView(view.context)
        popup.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(popupView: WebView, request: WebResourceRequest): Boolean {
                val target = request.url?.toString()
                if (target != null && SitePolicy.isFirstPartyPage(target, controller.selectedSource.allowedHosts)) {
                    webView.loadUrl(target)
                } else {
                    controller.onPopupBlocked(true)
                }
                popupView.destroy()
                return true
            }
        }
        transport.webView = popup
        resultMsg.sendToTarget()
        return true
    }

    override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
        result.cancel()
        return true
    }

    override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
        result.cancel()
        return true
    }

    override fun onJsPrompt(
        view: WebView,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResult
    ): Boolean {
        result.cancel()
        return true
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        val decor = webView.context.findActivity()?.window?.decorView as? FrameLayout ?: run {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customCallback = callback
        decor.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onHideCustomView() {
        val decor = webView.context.findActivity()?.window?.decorView as? FrameLayout
        customView?.let { decor?.removeView(it) }
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
