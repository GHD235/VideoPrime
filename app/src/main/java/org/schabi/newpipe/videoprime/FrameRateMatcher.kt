package org.schabi.newpipe.videoprime

import android.app.Activity
import android.util.Log
import android.view.Display
import androidx.preference.PreferenceManager
import kotlin.math.abs
import kotlin.math.roundToInt
import org.schabi.newpipe.R
import org.schabi.newpipe.util.DeviceUtils

/**
 * PulsePlay: on the TV, the screen's refresh rate follows the video's frame rate (what Kodi or
 * SmartTube call "auto frame rate"). European videos are made at 25 or 50 frames a second and a
 * TV runs at 60 Hz: some frames stay on screen longer than others and movement judders. With the
 * TV at 50 Hz every frame lasts the same. Only a mode of the same resolution is used, and the TV's
 * own rate comes back when the video closes.
 */
object FrameRateMatcher {
    private const val TAG = "FrameRateMatcher"

    /** How close refresh / fps must be to a whole number (23.976 fps on 59.94 Hz, etc.). */
    private const val TOLERANCE = 0.01f

    @JvmStatic
    fun isEnabled(activity: Activity): Boolean = DeviceUtils.isTv(activity) &&
        PreferenceManager.getDefaultSharedPreferences(activity)
            .getBoolean(activity.getString(R.string.pulseplay_match_frame_rate_key), true)

    /** Switches the screen to the best mode for [fps], if there is one and it is not in use. */
    @JvmStatic
    fun match(activity: Activity, fps: Float) {
        if (fps <= 1f || !isEnabled(activity)) return
        runCatching {
            @Suppress("DEPRECATION")
            val display: Display = activity.windowManager.defaultDisplay
            val current = display.mode
            val best = display.supportedModes
                .filter {
                    it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight &&
                        fits(it.refreshRate, fps)
                }
                // the highest whole multiple: 50 Hz rather than 25 for a 25 fps video
                .maxByOrNull { it.refreshRate } ?: return
            if (fits(current.refreshRate, fps) && current.refreshRate >= best.refreshRate) return
            val window = activity.window
            val params = window.attributes
            if (params.preferredDisplayModeId == best.modeId) return
            params.preferredDisplayModeId = best.modeId
            window.attributes = params
            Log.i(TAG, "${fps}fps: ${current.refreshRate}Hz -> ${best.refreshRate}Hz")
        }.onFailure { Log.w(TAG, "Could not change the refresh rate", it) }
    }

    /** Gives the screen back its usual mode. */
    @JvmStatic
    fun reset(activity: Activity) {
        runCatching {
            val window = activity.window
            val params = window.attributes
            if (params.preferredDisplayModeId != 0) {
                params.preferredDisplayModeId = 0
                window.attributes = params
            }
        }
    }

    private fun fits(refreshRate: Float, fps: Float): Boolean {
        val ratio = refreshRate / fps
        return ratio >= 1f - TOLERANCE && abs(ratio - ratio.roundToInt()) < TOLERANCE * ratio
    }
}
