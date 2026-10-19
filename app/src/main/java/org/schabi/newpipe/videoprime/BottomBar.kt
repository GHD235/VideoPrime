package org.schabi.newpipe.videoprime

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.schabi.newpipe.R
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.util.NavigationHelper

/**
 * VideoPrime: the bottom navigation bar of the app. It belongs to the activity, is shown on
 * every screen (like YouTube or Spotify) and is drawn above the player's bottom sheet, so the
 * mini player sits on top of the bar. It slides away while the player is expanded.
 *
 * Choosing a section from another screen (a channel, the downloads...) goes back to the main
 * screen and opens that section there.
 */
object BottomBar {
    /** Opens a section of the main screen. */
    fun interface SectionSelector {
        fun select(section: Int)
    }

    /** Set while the main screen is visible. */
    private var sectionSelector: SectionSelector? = null

    /** The section to open when the main screen comes back, or -1. */
    private var pendingSection = -1

    /** Whether the collapsed mini player is on screen, above the bar. */
    private var miniPlayerShown = false

    @JvmStatic
    fun view(activity: Activity): BottomNavigationView? = activity.findViewById(R.id.main_bottom_navigation)

    /** @return the height the bar takes at the bottom of the screen */
    @JvmStatic
    fun shownHeight(activity: Activity): Int {
        val bar = view(activity) ?: return 0
        if (bar.visibility != View.VISIBLE) return 0
        if (bar.height > 0) return bar.height
        return activity.resources.getDimensionPixelSize(R.dimen.videoprime_bottom_bar_height)
    }

    /** Shows the bar for good and makes its sections work from any screen. */
    @JvmStatic
    fun install(activity: FragmentActivity) {
        val bar = view(activity) ?: return
        // a recreated activity starts without a mini player or a section waiting to open
        miniPlayerShown = false
        pendingSection = -1
        bar.visibility = View.VISIBLE
        val goTo: (Int) -> Unit = { section ->
            val selector = sectionSelector
            if (selector != null) {
                selector.select(section)
            } else {
                pendingSection = section
                NavigationHelper.gotoMainFragment(activity.supportFragmentManager)
            }
        }
        bar.setOnItemSelectedListener { item ->
            goTo(item.itemId)
            true
        }
        bar.setOnItemReselectedListener { item ->
            // from a channel or the downloads, the section's own button brings it back
            if (sectionSelector == null) goTo(item.itemId)
        }
        // the bar steps aside while the keyboard is open (searching, renaming...), as it
        // would otherwise float above the keyboard
        val root = activity.window.decorView
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val keyboard = insets.isVisible(WindowInsetsCompat.Type.ime())
            val wanted = if (keyboard) View.GONE else View.VISIBLE
            if (bar.visibility != wanted) {
                bar.visibility = wanted
                bar.post { updateSpace(activity) }
            }
            ViewCompat.onApplyWindowInsets(view, insets)
        }
        bar.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                updateSpace(activity)
            }
        }
        updateSpace(activity)
    }

    /**
     * Called by the main screen while it is visible, so that the bar switches its sections
     * directly.
     *
     * @return the section that was chosen from another screen, to open now, or -1
     */
    @JvmStatic
    fun attachMainScreen(selector: SectionSelector): Int {
        sectionSelector = selector
        val pending = pendingSection
        pendingSection = -1
        return pending
    }

    @JvmStatic
    fun detachMainScreen() {
        sectionSelector = null
    }

    /** Marks a section as the current one in the bar. */
    @JvmStatic
    fun check(activity: Activity, section: Int) {
        val menu = view(activity)?.menu ?: return
        if (section in 0 until menu.size()) menu.getItem(section).isChecked = true
    }

    /** Called by the player when the mini player appears or disappears. */
    @JvmStatic
    fun setMiniPlayerShown(activity: FragmentActivity, shown: Boolean) {
        miniPlayerShown = shown
        updateSpace(activity)
    }

    /**
     * Leaves room at the bottom of the screens for the bar and the mini player, and keeps the
     * mini player right above the bar.
     */
    private fun updateSpace(activity: FragmentActivity) {
        val holder = activity.findViewById<ViewGroup>(R.id.fragment_holder) ?: return
        val mini = if (miniPlayerShown) {
            activity.resources.getDimensionPixelSize(R.dimen.mini_player_height)
        } else {
            0
        }
        val bottom = shownHeight(activity) + mini
        if (holder.paddingBottom != bottom) {
            holder.setPadding(holder.paddingLeft, holder.paddingTop, holder.paddingRight, bottom)
        }
        val player = activity.supportFragmentManager.findFragmentById(R.id.fragment_player_holder)
        (player as? VideoDetailFragment)?.updatePeekHeight()
        onSheetSlide(activity, sheetOffset(activity))
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
}
