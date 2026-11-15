package org.schabi.newpipe.videoprime

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.window.SplashScreenView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import org.schabi.newpipe.R
import org.schabi.newpipe.util.DeviceUtils

/**
 * PulsePlay: the start screen with the animated logo (`pulseplay_splash_animated`). Android 12+
 * draws it itself; on Android 7-11 (many TVs) the app draws the same logo over its first screen.
 *
 * Opened from the launcher (also the TV's), the start screen stays while the Home reads its
 * videos from the phone, as long as that takes (with a safety limit, [MAX_WAIT_MILLIS]): the Home then appears already
 * filled instead of empty and filling in. The wait does not slow anything down: the app keeps
 * starting behind it, and the moment the Home is ready the start screen goes. Then the logo
 * grows and fades while the Home comes forward (a short zoom; on the TV only a fade).
 */
object StartupSplash {
    /**
     * No fixed time: the start screen lasts exactly until the Home has its videos (and the
     * pictures of the first cards if they come at once, see [HomeWarmup]). This is only a safety
     * limit so that it can never stay long; its logo animation loops for as long as it shows.
     */
    private const val MAX_WAIT_MILLIS = 2000L
    private const val TV_MAX_WAIT_MILLIS = 2000L
    private const val EXIT_MILLIS = 280L
    private const val TV_EXIT_MILLIS = 220L

    /** Frames closer than this count as smooth (60 Hz = 16,7 ms; some margin). */
    private const val SMOOTH_FRAME_NANOS = 24_000_000L
    private const val SMOOTH_FRAMES_NEEDED = 3
    private const val MAX_SMOOTH_WAIT_NANOS = 180_000_000L

    /**
     * Adaptive: when the Home was ready this soon (it was already in memory, opened again a
     * moment later) the logo goes quickly, with a short exit and almost no wait.
     */
    private const val QUICK_READY_MILLIS = 450L
    private const val QUICK_EXIT_MILLIS = 180L
    private const val TV_QUICK_EXIT_MILLIS = 150L
    private const val QUICK_SMOOTH_WAIT_NANOS = 60_000_000L

    /** When the start screen began and when the Home said it was ready (uptime ms). */
    @Volatile
    private var shownAt = 0L

    @Volatile
    private var readyAt = 0L

    /** The Home was ready soon after the start screen appeared (or there was nothing to wait for). */
    private fun wasQuick(): Boolean = shownAt == 0L || readyAt == 0L || readyAt - shownAt <= QUICK_READY_MILLIS
    private const val BACKUP_START_MILLIS = 600L
    private const val ICON_END_SCALE = 2.4f
    private const val CONTENT_START_SCALE = 0.95f
    private const val ICON_SIZE_DP = 240
    private const val POLL_MILLIS = 40L
    private const val TV_WARM_SHOW_MILLIS = 550L
    private const val OVERLAY_TAG = "pulseplay_start_screen"
    private const val MAIN_ACTIVITY = "org.schabi.newpipe.MainActivity"

    @Volatile
    private var homeReady = false

    /** The start screen is still waiting for the Home (its first pictures may be loaded). */
    @JvmStatic
    fun isWaiting(): Boolean = !homeReady

    /** The Home has its videos (or knows it has none): the start screen may go. */
    @JvmStatic
    fun homeReady() {
        if (!homeReady) readyAt = SystemClock.uptimeMillis()
        homeReady = true
    }

    /** Called from MainActivity.onCreate, after setContentView. */
    @JvmStatic
    fun install(activity: Activity, savedInstanceState: Bundle?) {
        val tv = DeviceUtils.isTv(activity)
        val fromLauncher = savedInstanceState == null && isLauncherIntent(activity.intent)
        val maxWait = if (tv) TV_MAX_WAIT_MILLIS else MAX_WAIT_MILLIS
        if (tv) {
            // TVs draw the app's own start screen at any Android version (their launchers do
            // not all show the system one; values-television-v31 leaves it plain dark)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                activity.splashScreen.setOnExitAnimationListener { it.remove() }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && fromLauncher) {
                homeReady = false
                shownAt = SystemClock.uptimeMillis()
                readyAt = 0L
                showOwnSplash(activity, maxWait, 0, tv)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (fromLauncher) {
                homeReady = false
                shownAt = SystemClock.uptimeMillis()
                readyAt = 0L
                keepWhileHomeLoads(activity, maxWait)
            }
            activity.splashScreen.setOnExitAnimationListener { view -> exit(activity, view, tv) }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && fromLauncher) {
            homeReady = false
            shownAt = SystemClock.uptimeMillis()
            readyAt = 0L
            showOwnSplash(activity, maxWait, 0, tv)
        }
    }

    private var startedActivities = 0
    private var wentToBackground = false

    /**
     * TV: the app coming back to the front (opened again from the TV's home screen while it
     * was still alive behind, the usual case there: Android then only brings it forward, with
     * no new start and no new intent) also shows the logo, for the time the animation needs;
     * the Home is already there and updates itself behind it. Not for the app's own screens
     * (settings, update...) nor a recreation.
     */
    @JvmStatic
    fun watchReturns(app: Application) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !DeviceUtils.isTv(app)) return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                val back = startedActivities == 0 && wentToBackground
                startedActivities++
                if (back) {
                    wentToBackground = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                        activity.javaClass.name == MAIN_ACTIVITY
                    ) {
                        showOwnSplash(activity, TV_MAX_WAIT_MILLIS, TV_WARM_SHOW_MILLIS, true)
                    }
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0 && !activity.isChangingConfigurations) {
                    wentToBackground = true
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun isLauncherIntent(intent: Intent?) = intent?.action == Intent.ACTION_MAIN && (
        intent.hasCategory(Intent.CATEGORY_LAUNCHER) ||
            intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        )

    private fun keepWhileHomeLoads(activity: Activity, maxWait: Long) {
        val content = activity.findViewById<View>(android.R.id.content) ?: return
        val start = SystemClock.uptimeMillis()
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val go = homeReady || SystemClock.uptimeMillis() - start >= maxWait ||
                    activity.isFinishing
                if (go) content.viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
                // (false = no frame yet: Android keeps its start screen and asks again next frame)
                return go
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun exit(activity: Activity, view: SplashScreenView, tv: Boolean) {
        if (!animationsOn()) {
            view.remove()
            return
        }
        animateOut(activity, view, view.iconView, tv) { view.remove() }
    }

    /**
     * Android 7-11: the logo over the first screen, on the app's dark background (the window
     * shows only that colour before, see values-v24/pulseplay_opening.xml). The Home draws
     * behind it meanwhile.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun showOwnSplash(activity: Activity, maxWait: Long, minShow: Long, tv: Boolean) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        // (one at a time)
        if (decor.findViewWithTag<View>(OVERLAY_TAG) != null) return
        val drawable = ContextCompat.getDrawable(activity, R.drawable.pulseplay_splash_animated)
            ?: return
        val size = (ICON_SIZE_DP * activity.resources.displayMetrics.density).toInt()
        val icon = ImageView(activity).apply {
            setImageDrawable(drawable)
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
        }
        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(ContextCompat.getColor(activity, R.color.dark_background_color))
            // the start screen takes the taps and the remote's presses while it is there (with
            // the focus: keys go to the focused view), without the TV's focus frame around it
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            setTag(R.id.pulseplay_own_focus_ring, true)
            tag = OVERLAY_TAG
            setOnKeyListener { _, _, _ -> true }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(icon)
        }
        decor.addView(
            overlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        overlay.requestFocus()
        (drawable as? Animatable)?.start()
        val start = SystemClock.uptimeMillis()
        val remove = {
            (drawable as? Animatable)?.stop()
            decor.removeView(overlay)
        }
        overlay.post(object : Runnable {
            override fun run() {
                if (!overlay.isAttachedToWindow) return
                val shown = SystemClock.uptimeMillis() - start
                if (activity.isFinishing || shown >= maxWait || (homeReady && shown >= minShow)) {
                    if (activity.isFinishing || !animationsOn()) {
                        remove()
                    } else {
                        animateOut(activity, overlay, icon, tv, remove)
                    }
                } else {
                    overlay.postDelayed(this, POLL_MILLIS)
                }
            }
        })
    }

    /** Off when the user turned animations off (only known on Android 8+). */
    private fun animationsOn() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    /**
     * The logo grows and fades with its screen while the Home comes forward. It starts once the
     * app draws smoothly: right after the Home appears its first frames are heavy (the cards are
     * built, the first pictures decoded) and an animation started then jumps; the logo waits on
     * screen a few frames (450 ms at most) instead.
     */
    private fun animateOut(
        activity: Activity,
        screen: View,
        icon: View?,
        tv: Boolean,
        onEnd: () -> Unit
    ) {
        // onEnd runs once, whatever happens (a cancelled animation, a destroyed screen...)
        var ended = false
        val endOnce = {
            if (!ended) {
                ended = true
                runCatching { onEnd() }
            }
        }
        var started = false
        val start = {
            if (!started) {
                started = true
                if (activity.isDestroyed || activity.isFinishing) {
                    endOnce()
                } else {
                    startAnimateOut(activity, screen, icon, tv, endOnce)
                }
            }
        }
        // with the screen off or the app sent away no frames come: then it goes anyway
        // (without frames an animation would not move either: then the logo just goes)
        Handler(Looper.getMainLooper()).postDelayed({
            if (!started && !activity.hasWindowFocus()) {
                started = true
                endOnce()
            } else {
                start()
            }
        }, BACKUP_START_MILLIS)
        val choreographer = Choreographer.getInstance()
        var first = 0L
        var last = 0L
        var smooth = 0
        val maxSmooth = if (wasQuick()) QUICK_SMOOTH_WAIT_NANOS else MAX_SMOOTH_WAIT_NANOS
        choreographer.postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (started) return
                if (first == 0L) first = frameTimeNanos
                smooth = if (last != 0L && frameTimeNanos - last <= SMOOTH_FRAME_NANOS) smooth + 1 else 0
                last = frameTimeNanos
                if (smooth >= SMOOTH_FRAMES_NEEDED || frameTimeNanos - first >= maxSmooth) {
                    start()
                } else {
                    choreographer.postFrameCallback(this)
                }
            }
        })
    }

    private fun startAnimateOut(
        activity: Activity,
        screen: View,
        icon: View?,
        tv: Boolean,
        onEnd: () -> Unit
    ) {
        val quick = wasQuick()
        val millis = when {
            tv && quick -> TV_QUICK_EXIT_MILLIS
            tv -> TV_EXIT_MILLIS
            quick -> QUICK_EXIT_MILLIS
            else -> EXIT_MILLIS
        }
        val animators = mutableListOf<Animator>(
            ObjectAnimator.ofFloat(screen, View.ALPHA, 1f, 0f).apply {
                startDelay = millis / 5
                duration = millis - millis / 5
                interpolator = AccelerateInterpolator()
            }
        )
        fun scale(target: View, from: Float, to: Float) {
            for (property in listOf(View.SCALE_X, View.SCALE_Y)) {
                animators += ObjectAnimator.ofFloat(target, property, from, to).apply {
                    duration = millis
                    interpolator = DecelerateInterpolator()
                }
            }
        }
        icon?.let { scale(it, 1f, ICON_END_SCALE) }
        // the Home comes forward (not on the TV: its graphics chip is weak and the fade is enough)
        val content = if (tv) {
            null
        } else {
            activity.findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)
        }
        content?.let { scale(it, CONTENT_START_SCALE, 1f) }
        // the fading screen is drawn once into a layer instead of piece by piece every frame
        screen.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        AnimatorSet().apply {
            playTogether(animators)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    screen.setLayerType(View.LAYER_TYPE_NONE, null)
                    content?.scaleX = 1f
                    content?.scaleY = 1f
                    onEnd()
                }
            })
            start()
        }
    }
}
