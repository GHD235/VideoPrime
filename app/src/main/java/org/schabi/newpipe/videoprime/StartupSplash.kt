package org.schabi.newpipe.videoprime

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.window.SplashScreenView
import androidx.annotation.RequiresApi
import org.schabi.newpipe.util.DeviceUtils

/**
 * PulsePlay: the start screen of Android 12+ (its logo is `pulseplay_splash_animated`).
 *
 * Opened from the launcher, the start screen stays while the Home reads its videos from the
 * phone, never longer than [MAX_WAIT_MILLIS]: the Home then appears already filled instead of
 * empty and filling in. The wait does not slow anything down: the app keeps starting behind it,
 * and the moment the Home is ready the start screen goes. Then the logo grows and fades while
 * the Home comes forward (a short zoom).
 */
object StartupSplash {
    private const val MAX_WAIT_MILLIS = 700L
    private const val TV_MAX_WAIT_MILLIS = 400L
    private const val EXIT_MILLIS = 320L
    private const val TV_EXIT_MILLIS = 200L
    private const val ICON_END_SCALE = 2.4f
    private const val CONTENT_START_SCALE = 0.95f

    @Volatile
    private var homeReady = false

    /** The Home has its videos (or knows it has none): the start screen may go. */
    @JvmStatic
    fun homeReady() {
        homeReady = true
    }

    /** Called from MainActivity.onCreate, after setContentView. */
    @JvmStatic
    fun install(activity: Activity, savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val tv = DeviceUtils.isTv(activity)
        val intent = activity.intent
        val fromLauncher = savedInstanceState == null &&
            intent?.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        if (fromLauncher) {
            homeReady = false
            keepWhileHomeLoads(activity, if (tv) TV_MAX_WAIT_MILLIS else MAX_WAIT_MILLIS)
        }
        activity.splashScreen.setOnExitAnimationListener { view -> exit(activity, view, tv) }
    }

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
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.remove()
            return
        }
        val millis = if (tv) TV_EXIT_MILLIS else EXIT_MILLIS
        val animators = mutableListOf<Animator>(
            ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f).apply {
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
        view.iconView?.let { scale(it, 1f, ICON_END_SCALE) }
        // the Home comes forward (not on the TV: its graphics chip is weak and the fade is enough)
        val content = if (tv) {
            null
        } else {
            activity.findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0)
        }
        content?.let { scale(it, CONTENT_START_SCALE, 1f) }
        AnimatorSet().apply {
            playTogether(animators)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    content?.scaleX = 1f
                    content?.scaleY = 1f
                    view.remove()
                }
            })
            start()
        }
    }
}
