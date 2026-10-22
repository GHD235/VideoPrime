package org.schabi.newpipe.videoprime

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * VideoPrime: a TV channel whose live stream is DRM protected (Atresmedia, Mediaset), watched
 * inside the app through the broadcaster's own web player. The page plays it with the system's
 * Widevine like any browser would; nothing is decrypted or recorded by VideoPrime. If the
 * broadcaster asks to sign in, the session stays in the WebView's cookies.
 */
class TvWebActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        if (url == null || !url.startsWith("https://")) {
            finish()
            return
        }
        title = intent.getStringExtra(EXTRA_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            // ads of the players are sometimes served over http
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // some players refuse Android's WebView ("; wv"): present it as the Chrome it is
            settings.userAgentString = settings.userAgentString
                .replace("; wv", "")
                .replace(Regex(" Version/\\d+(\\.\\d+)*"), "")
            webViewClient = Client()
            webChromeClient = ChromeClient()
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // the sign-in of the broadcasters goes through their own account domains
            setAcceptThirdPartyCookies(webView, true)
        }
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(webView, MATCH)
        setContentView(root)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        fullscreenView != null -> fullscreenCallback?.onCustomViewHidden()
                        webView.canGoBack() -> webView.goBack()
                        else -> finish()
                    }
                }
            }
        )

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(url)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            CookieManager.getInstance().flush()
            root.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun setFullscreen(fullscreen: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // https pages stay here; "open in the app" links (intent:, market:...) and plain
            // http are ignored
            return request.url.scheme != "https"
        }
    }

    private inner class ChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            // only what DRM playback needs (the Widevine of the system), only to the broadcasters'
            // own players; never camera or mic
            val origin = request.origin
            val host = origin.host.orEmpty()
            val trusted = origin.scheme == "https" &&
                DRM_HOSTS.any { host == it || host.endsWith(".$it") }
            if (trusted && PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID in request.resources) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
            } else {
                request.deny()
            }
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            fullscreenCallback?.onCustomViewHidden()
            fullscreenView = view
            fullscreenCallback = callback
            root.addView(view, MATCH)
            setFullscreen(true)
        }

        override fun onHideCustomView() {
            fullscreenView?.let { root.removeView(it) }
            fullscreenView = null
            fullscreenCallback = null
            setFullscreen(false)
        }
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"

        /** Chrome first; any other browser with tabs after it. */
        private val PREFERRED_BROWSERS = listOf("com.android.chrome", "com.chrome.beta")

        /** The broadcasters whose web players may use Widevine. */
        private val DRM_HOSTS = listOf(
            "atresplayer.com",
            "atresmedia.com",
            "mitele.es",
            "mediaset.es",
            "mediasetinfinity.es"
        )
        private val MATCH = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        /**
         * Opens [url] in a Chrome tab over the app when possible: it is the real browser, so the
         * broadcaster's ads, sign-in and DRM work as on its website (inside a WebView Atresplayer
         * takes its ads failing to load for an ad blocker). Otherwise, in this activity.
         */
        @JvmStatic
        fun open(context: Context, url: String, title: String) {
            val browser = CustomTabsClient.getPackageName(context, PREFERRED_BROWSERS, false)
            if (browser != null) {
                val tab = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                    .setUrlBarHidingEnabled(true)
                    .build()
                tab.intent.setPackage(browser)
                try {
                    tab.launchUrl(context, url.toUri())
                    return
                } catch (_: ActivityNotFoundException) {
                    // the browser went away: the WebView below
                }
            }
            context.startActivity(
                Intent(context, TvWebActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_TITLE, title)
            )
        }
    }
}
