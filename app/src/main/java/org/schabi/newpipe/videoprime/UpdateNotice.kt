package org.schabi.newpipe.videoprime

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.R
import org.schabi.newpipe.util.DeviceUtils

/**
 * PulsePlay: on a TV, notifications are hardly seen, so a new version is offered inside the app
 * when it opens ("Más tarde": again the next time the app starts). Updating downloads and
 * installs it from here (UpdateActivity), as the notification does on the phone.
 */
object UpdateNotice {
    /** The update already offered while the app runs. */
    @Volatile
    private var offered: String? = null

    @JvmStatic
    fun offerOnTv(activity: AppCompatActivity) {
        if (!DeviceUtils.isTv(activity)) return
        val app = activity.applicationContext
        activity.lifecycleScope.launch {
            val url = withContext(Dispatchers.IO) {
                // asks GitHub at most every 3 hours; a failure just leaves what is known
                runCatching { UpdateChecker.checkIfDue(app) }
                UpdateChecker.pendingUpdate(app)
            } ?: return@launch
            if (url == offered || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                return@launch
            }
            offered = url
            AlertDialog.Builder(activity)
                .setTitle(R.string.videoprime_update_title)
                .setMessage(R.string.pulseplay_update_tv_text)
                .setPositiveButton(R.string.pulseplay_update_now) { _, _ ->
                    activity.startActivity(
                        Intent(activity, UpdateActivity::class.java).putExtra(UpdateActivity.EXTRA_URL, url)
                    )
                }
                .setNegativeButton(R.string.pulseplay_update_later, null)
                .show()
                // the remote's focus starts on "Actualizar"
                .getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
        }
    }
}
