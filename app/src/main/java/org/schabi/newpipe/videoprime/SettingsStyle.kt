package org.schabi.newpipe.videoprime

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.util.ThemeHelper

/**
 * PulsePlay: the settings screens drawn as groups of options inside rounded cards (the same
 * options and screens of always: only how they look changes). Every run of options between two
 * group titles is one card, with a thin line between its rows.
 */
object SettingsStyle {
    private const val SIDE_DP = 12
    private const val RADIUS_DP = 18
    private const val GAP_DP = 10

    @JvmStatic
    fun apply(fragment: PreferenceFragmentCompat) {
        val list = fragment.listView ?: return
        val density = list.resources.displayMetrics.density
        val side = (SIDE_DP * density).toInt()
        list.setPadding(side, (GAP_DP * density).toInt(), side, (2 * GAP_DP * density).toInt())
        list.clipToPadding = false
        // only once per list (the view may be styled again when it is recreated)
        for (i in 0 until list.itemDecorationCount) {
            if (list.getItemDecorationAt(i) is Cards) return
        }
        list.addItemDecoration(Cards(ThemeHelper.isLightThemeSelected(list.context), density))
    }

    private class Cards(light: Boolean, private val density: Float) : RecyclerView.ItemDecoration() {
        private val card = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (light) 0xFFF1EEF9.toInt() else 0xFF1A1824.toInt()
        }
        private val line = Paint().apply {
            color = if (light) 0x14000000 else 0x14FFFFFF
            strokeWidth = density
        }
        private val radius = RADIUS_DP * density
        private val path = Path()
        private val rect = RectF()

        @SuppressLint("RestrictedApi")
        private fun preferenceAt(parent: RecyclerView, position: Int): Preference? {
            val adapter = parent.adapter as? PreferenceGroupAdapter ?: return null
            if (position < 0 || position >= adapter.itemCount) return null
            return adapter.getItem(position)
        }

        /** An option (not a group's title): it is part of a card. */
        private fun inCard(parent: RecyclerView, position: Int): Boolean {
            val preference = preferenceAt(parent, position) ?: return false
            return preference !is PreferenceCategory
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION || !inCard(parent, position)) return
            // air under the last row of a card
            if (!inCard(parent, position + 1)) outRect.bottom = (GAP_DP * density).toInt()
        }

        override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val position = parent.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION || !inCard(parent, position)) continue
                val first = !inCard(parent, position - 1)
                val last = !inCard(parent, position + 1)
                val top = if (first) radius else 0f
                val bottom = if (last) radius else 0f
                rect.set(
                    child.left.toFloat(),
                    child.top + child.translationY,
                    child.right.toFloat(),
                    child.bottom + child.translationY
                )
                path.rewind()
                path.addRoundRect(
                    rect,
                    floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom),
                    Path.Direction.CW
                )
                canvas.drawPath(path, card)
                if (!first) {
                    canvas.drawLine(rect.left + radius, rect.top, rect.right - radius, rect.top, line)
                }
            }
        }
    }
}
