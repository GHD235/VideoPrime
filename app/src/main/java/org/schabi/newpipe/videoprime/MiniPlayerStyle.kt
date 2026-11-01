package org.schabi.newpipe.videoprime

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import org.schabi.newpipe.R
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.views.FocusOverlayView

/**
 * PulsePlay: the mini player at the bottom (what plays while browsing) with the app's look, as
 * in the mock-ups: a dark bar, the thumbnail rounded, the title in bold and play/pause in a violet
 * circle, with round rings for the TV remote instead of its square box.
 */
object MiniPlayerStyle {
    private const val BAR_DARK = "#17151F"
    private const val ACCENT = "#7C3AED"

    @JvmStatic
    fun apply(
        root: View,
        thumbnail: ImageButton,
        title: TextView,
        channel: TextView,
        playPause: ImageButton,
        queue: ImageButton,
        close: ImageButton
    ) {
        val context = root.context
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val dark = !ThemeHelper.isLightThemeSelected(context)
        if (dark) {
            root.setBackgroundColor(Color.parseColor(BAR_DARK))
        }

        // a 16:9 rounded thumbnail, centred in the 60 dp row
        (thumbnail.layoutParams as? RelativeLayout.LayoutParams)?.let {
            it.width = dp(96)
            it.height = dp(54)
            it.topMargin = dp(3)
            it.marginStart = dp(8)
            thumbnail.layoutParams = it
        }
        thumbnail.setPadding(0, 0, 0, 0)
        thumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
        ChannelVisits.roundCorners(thumbnail)
        (thumbnail.parent as? ViewGroup)?.findViewById<View>(R.id.overlay_metadata_layout)
            ?.let { it.setPadding(dp(12), 0, dp(4), 0) }

        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        channel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        channel.alpha = 0.7f

        // play/pause: a violet circle with a white icon
        playPause.layoutParams = playPause.layoutParams.apply {
            width = dp(44)
            height = dp(44)
        }
        (playPause.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.marginStart = dp(6)
            it.marginEnd = dp(6)
        }
        playPause.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(ACCENT))
        }
        playPause.imageTintList = ColorStateList.valueOf(Color.WHITE)
        val ring = AppCompatResources.getDrawable(context, R.drawable.pulseplay_focus_ring_round)
        playPause.foreground = ring

        for (button in listOf(queue, close)) {
            button.foreground = AppCompatResources.getDrawable(context, R.drawable.pulseplay_focus_ring_pill)
            if (dark) button.imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
        FocusOverlayView.drawsOwnFocus(playPause, queue, close)
    }
}
