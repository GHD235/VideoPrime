package org.schabi.newpipe.videoprime

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.schabi.newpipe.R
import org.schabi.newpipe.fragments.detail.VideoDetailFragment

/**
 * VideoPrime: the main screen's bottom navigation bar. It belongs to the activity and is drawn
 * above the player's bottom sheet, so the mini player sits on top of the bar (like YouTube or
 * Spotify) instead of under it. The bar slides away while the player is expanded.
 */
object BottomBar {
    @JvmStatic
    fun view(activity: Activity): BottomNavigationView? = activity.findViewById(R.id.main_bottom_navigation)

    /** @return the height the bar takes at the bottom of the screen, 0 if it is not shown */
    @JvmStatic
    fun shownHeight(activity: Activity): Int {
        val bar = view(activity) ?: return 0
        if (bar.visibility != View.VISIBLE) return 0
        if (bar.height > 0) return bar.height
        return activity.resources.getDimensionPixelSize(R.dimen.videoprime_bottom_bar_height)
    }

    /** Shows the bar while the main screen is visible, and hides it everywhere else. */
    @JvmStatic
    fun setShown(activity: FragmentActivity, shown: Boolean) {
        val bar = view(activity) ?: return
        bar.visibility = if (shown) View.VISIBLE else View.GONE
        bar.post { onSheetSlide(activity, sheetOffset(activity)) }
        updatePlayerPeek(activity)
        bar.post { updatePlayerPeek(activity) }
    }

    /**
     * Moves the bar out of the way as the player expands.
     *
     * @param slideOffset 0 with the player collapsed or hidden, 1 with it expanded
     */
    @JvmStatic
    fun onSheetSlide(activity: Activity, slideOffset: Float) {
        val bar = view(activity) ?: return
        bar.translationY = bar.height * slideOffset.coerceIn(0f, 1f)
    }

    private fun sheetOffset(activity: Activity): Float {
        val sheet = activity.findViewById<FrameLayout>(R.id.fragment_player_holder) ?: return 0f
        return if (BottomSheetBehavior.from(sheet).state == BottomSheetBehavior.STATE_EXPANDED) {
            1f
        } else {
            0f
        }
    }

    private fun updatePlayerPeek(activity: FragmentActivity) {
        val player = activity.supportFragmentManager
            .findFragmentById(R.id.fragment_player_holder)
        (player as? VideoDetailFragment)?.updatePeekHeight()
    }
}
