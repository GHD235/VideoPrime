package org.schabi.newpipe.videoprime

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
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
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.schabi.newpipe.R
import org.schabi.newpipe.util.external_communication.ShareUtils

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
    private lateinit var chromeButton: TextView
    private val handler = Handler(Looper.getMainLooper())

    /** The live is playing and only the player is shown, full screen. */
    private var playerOnly = false

    /** Once the broadcast plays, everything but the player is hidden (checked every second). */
    private val playerOnlyCheck = object : Runnable {
        override fun run() {
            webView.evaluateJavascript(PLAYER_ONLY_JS) { result ->
                if (result == "true" && !playerOnly && !isFinishing) {
                    playerOnly = true
                    root.removeView(chromeButton)
                    setFullscreen(true)
                }
            }
            handler.postDelayed(this, PLAYER_ONLY_CHECK_MILLIS)
        }
    }

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
        // a way out if the web player does not work here: the same page in Chrome
        chromeButton = TextView(this).apply {
            text = getString(R.string.tv_open_in_chrome)
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            val pad = (8 * resources.displayMetrics.density).toInt()
            setPadding(pad * 2, pad, pad * 2, pad)
            setOnClickListener {
                openInChrome(this@TvWebActivity, webView.url ?: url)
                finish()
            }
        }
        root.addView(
            chromeButton,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            )
        )
        setContentView(root)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        fullscreenView != null -> fullscreenCallback?.onCustomViewHidden()
                        playerOnly -> finish()
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
        if (::webView.isInitialized) {
            webView.onResume()
            handler.removeCallbacks(playerOnlyCheck)
            handler.post(playerOnlyCheck)
        }
    }

    override fun onPause() {
        handler.removeCallbacks(playerOnlyCheck)
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
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
            setFullscreen(playerOnly)
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

        @JvmStatic
        fun open(context: Context, url: String, title: String) {
            context.startActivity(
                Intent(context, TvWebActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_TITLE, title)
            )
        }

        /** [url] in a Chrome tab over the app, or in the default browser. */
        private fun openInChrome(context: Context, url: String) {
            val browser = CustomTabsClient.getPackageName(context, PREFERRED_BROWSERS, false)
            val tab = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                .build()
            browser?.let { tab.intent.setPackage(it) }
            try {
                tab.launchUrl(context, url.toUri())
            } catch (_: ActivityNotFoundException) {
                ShareUtils.openUrlInBrowser(context, url)
            }
        }

        private const val PLAYER_ONLY_CHECK_MILLIS = 1000L

        /**
         * When a video of the page is playing, its player (the outermost element around the video
         * with about its size, so that its controls and ads stay) is fixed over the whole page.
         * Returns true when done. Before playing nothing changes, so a cookie notice or a sign-in
         * can still be answered by the user.
         */
        private const val PLAYER_ONLY_JS = """(function() {
  var videos = document.querySelectorAll('video');
  var v = null;
  for (var i = 0; i < videos.length; i++) {
    var c = videos[i], b = c.getBoundingClientRect();
    if (c.currentTime > 0 && !c.paused && !c.ended && c.readyState > 2 && b.width > 200) { v = c; break; }
  }
  if (!v) return false;
  var old = document.querySelector('.vp-player');
  if (old && old.contains(v)) return true;
  if (old) old.classList.remove('vp-player');
  var rv = v.getBoundingClientRect();
  var p = v;
  while (p.parentElement && p.parentElement !== document.body) {
    var r = p.parentElement.getBoundingClientRect();
    if (r.height > rv.height * 1.3 + 4 || r.width > rv.width * 1.3 + 4) break;
    p = p.parentElement;
  }
  p.classList.add('vp-player');
  if (!document.getElementById('vp-style')) {
    var s = document.createElement('style');
    s.id = 'vp-style';
    s.textContent = '.vp-player{position:fixed!important;left:0!important;top:0!important;' +
      'width:100vw!important;height:100vh!important;max-width:none!important;' +
      'max-height:none!important;margin:0!important;z-index:2147483647!important;' +
      'background:#000!important;transform:none!important}' +
      '.vp-player video{width:100%!important;height:100%!important;object-fit:contain!important}' +
      'html,body{overflow:hidden!important}';
    document.head.appendChild(s);
  }
  return true;
})()"""
    }
}
