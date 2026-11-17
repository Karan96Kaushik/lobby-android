package com.bayonetbarontech.lobby.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.KeyEvent
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
import com.bayonetbarontech.lobby.BrowserCommand
import com.bayonetbarontech.lobby.BrowserController
import com.bayonetbarontech.lobby.policy.SitePolicy
import com.bayonetbarontech.lobby.remote.RemoteCommand
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

fun SwipeRefreshLayout.lobbyWebView(): WebView? {
    for (index in 0 until childCount) {
        val child = getChildAt(index)
        if (child is WebView) return child
    }
    return null
}

fun applyBrowserCommand(swipe: SwipeRefreshLayout, command: BrowserCommand, controller: BrowserController) {
    val webView = swipe.lobbyWebView() ?: return
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

fun applyRemoteCommand(swipe: SwipeRefreshLayout, command: RemoteCommand, controller: BrowserController) {
    val webView = swipe.lobbyWebView()
    when (command) {
        is RemoteCommand.Url -> {
            val error = controller.openRemoteUrl(command.url)
            if (error != null) controller.showNotice(error)
        }
        is RemoteCommand.Key -> when (command.name) {
            "back" -> if (VideoFullscreen.isShowing()) VideoFullscreen.requestExit() else controller.goBack()
            "forward" -> controller.goForward()
            "home" -> controller.goHome()
            "reload" -> controller.reload()
            "stop" -> controller.stop()
            "up" -> sendPlayerKey(webView, "up", KeyEvent.KEYCODE_DPAD_UP)
            "down" -> sendPlayerKey(webView, "down", KeyEvent.KEYCODE_DPAD_DOWN)
            "left" -> sendPlayerKey(webView, "left", KeyEvent.KEYCODE_DPAD_LEFT)
            "right" -> sendPlayerKey(webView, "right", KeyEvent.KEYCODE_DPAD_RIGHT)
            "select" -> sendPlayerKey(webView, "select", KeyEvent.KEYCODE_DPAD_CENTER)
            "pageup" -> webView?.let { tapKey(it, KeyEvent.KEYCODE_PAGE_UP) }
            "pagedown" -> webView?.let { tapKey(it, KeyEvent.KEYCODE_PAGE_DOWN) }
            "play" -> sendPlayerKey(webView, "play", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "seekback" -> sendPlayerKey(webView, "seekback", KeyEvent.KEYCODE_MEDIA_REWIND)
            "seekfwd" -> sendPlayerKey(webView, "seekfwd", KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            "seekback5" -> webView?.evaluateJavascript("window.__lobbySeek && window.__lobbySeek(-300)", null)
            "seekfwd5" -> webView?.evaluateJavascript("window.__lobbySeek && window.__lobbySeek(300)", null)
            "mute" -> sendPlayerKey(webView, "mute", KeyEvent.KEYCODE_VOLUME_MUTE)
            "fullscreen" -> webView?.let { dispatchFullscreenKey(it, KeyEvent.KEYCODE_F11) }
            "exitfullscreen" -> webView?.let { view ->
                dispatchFullscreenKey(view, KeyEvent.KEYCODE_F10)
                if (VideoFullscreen.isShowing()) VideoFullscreen.requestExit()
            }
        }
    }
}

private fun sendPlayerKey(webView: WebView?, name: String, keyCode: Int) {
    if (VideoFullscreen.isShowing()) {
        webView?.evaluateJavascript("window.__lobbyRemoteKey&&window.__lobbyRemoteKey('$name')", null)
    } else {
        webView?.let { tapKey(it, keyCode) }
    }
}

private fun dispatchFullscreenKey(webView: WebView, keyCode: Int) {
    webView.isFocusable = true
    webView.isFocusableInTouchMode = true
    if (!webView.hasFocus()) webView.requestFocus()
    val downAt = SystemClock.uptimeMillis()
    webView.dispatchKeyEvent(KeyEvent(downAt, downAt, KeyEvent.ACTION_DOWN, keyCode, 0))
    webView.dispatchKeyEvent(KeyEvent(downAt, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0))
}

private fun tapKey(webView: WebView, keyCode: Int) {
    val target = VideoFullscreen.keyTarget() ?: webView
    target.isFocusable = true
    target.isFocusableInTouchMode = true
    if (!target.hasFocus()) target.requestFocus()
    val downAt = SystemClock.uptimeMillis()
    target.dispatchKeyEvent(KeyEvent(downAt, downAt, KeyEvent.ACTION_DOWN, keyCode, 0))
    target.dispatchKeyEvent(KeyEvent(downAt, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0))
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
        return when (SitePolicy.decision(url, mainFrame, controller.navigationHosts())) {
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
        if (sameDocument(url, view.url)) {
            controller.consumeFireTvEntryScript(url)?.let { script ->
                view.evaluateJavascript(script, null)
            }
        }
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

    private fun sameDocument(finished: String?, current: String?): Boolean {
        if (finished.isNullOrBlank() || current.isNullOrBlank()) return false
        return finished.substringBefore('#') == current.substringBefore('#')
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
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        decor.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        view.requestFocus()
        VideoFullscreen.enter(webView, view, ::onHideCustomView)
    }

    override fun onHideCustomView() {
        if (customView == null && customCallback == null) return
        val decor = webView.context.findActivity()?.window?.decorView as? FrameLayout
        customView?.let { decor?.removeView(it) }
        customView = null
        val callback = customCallback
        customCallback = null
        VideoFullscreen.clear(webView)
        callback?.onCustomViewHidden()
    }
}

/**
 * Fullscreen video is a separate view over the page. The Fire TV remote would
 * otherwise move focus in the controls hidden underneath it.
 */
object VideoFullscreen {
    private var webView: WebView? = null
    private var surface: View? = null
    private var exit: (() -> Unit)? = null

    fun enter(webView: WebView, surface: View, exit: () -> Unit) {
        this.webView = webView
        this.surface = surface
        this.exit = exit
    }

    fun clear(webView: WebView) {
        if (this.webView !== webView) return
        this.webView = null
        this.surface = null
        this.exit = null
    }

    fun isShowing(): Boolean = webView != null

    fun keyTarget(): View? = surface

    fun requestExit() {
        exit?.invoke()
    }

    fun dispatch(event: KeyEvent): Boolean {
        val page = webView ?: return false
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) exit?.invoke()
            return true
        }
        val name = commandFor(code) ?: return false
        if (name == "up" || name == "down" || name == "left" || name == "right" || name == "select") {
            if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0 && name == "select") return true
            page.evaluateJavascript("window.__lobbyRemoteKey&&window.__lobbyRemoteKey('$name')", null)
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if ((name == "play" || name == "pause") && event.repeatCount > 0) return true
        page.evaluateJavascript("window.__lobbyRemoteKey&&window.__lobbyRemoteKey('$name')", null)
        return true
    }

    private fun commandFor(code: Int): String? = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> "up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> "select"
        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "play"
        KeyEvent.KEYCODE_MEDIA_PAUSE -> "pause"
        KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "seekback"
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT -> "seekfwd"
        KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE -> "mute"
        else -> null
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
