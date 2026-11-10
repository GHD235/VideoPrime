package org.schabi.newpipe.videoprime

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView

/**
 * PulsePlay (TV): the settings use the whole width: sections on the left, options in the middle
 * and on the right the name and explanation of the option under the remote's focus, in big
 * letters (on the row itself the explanation is small and cut to a few lines).
 */
object SettingsWide {
    /** Starts following the focus inside [options]. The answer stops it. */
    @JvmStatic
    fun watchFocus(activity: Activity, options: View, title: TextView, text: TextView): Runnable {
        val listener = ViewTreeObserver.OnGlobalFocusChangeListener { _, focused ->
            if (focused == null || !isInside(focused, options)) return@OnGlobalFocusChangeListener
            val name = focused.findViewById<TextView>(android.R.id.title)?.text
            if (name.isNullOrBlank()) return@OnGlobalFocusChangeListener
            title.text = name
            text.text = focused.findViewById<TextView>(android.R.id.summary)
                ?.takeIf { it.visibility == View.VISIBLE }?.text ?: ""
        }
        val observer = activity.window.decorView.viewTreeObserver
        observer.addOnGlobalFocusChangeListener(listener)
        return Runnable {
            val current = activity.window.decorView.viewTreeObserver
            if (current.isAlive) current.removeOnGlobalFocusChangeListener(listener)
        }
    }

    private fun isInside(view: View, ancestor: View): Boolean {
        var parent = view.parent
        while (parent is ViewGroup) {
            if (parent === ancestor) return true
            parent = parent.parent
        }
        return false
    }
}
