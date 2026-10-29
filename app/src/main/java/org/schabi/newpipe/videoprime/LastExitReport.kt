package org.schabi.newpipe.videoprime

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction

/**
 * PulsePlay: when the app stopped responding ("no responde") the last time, Android 11+ keeps
 * what each thread was doing. Shown once as an error notification (its technical details carry
 * the main thread's trace), so that the cause is known instead of guessed.
 */
object LastExitReport {
    private const val TAG = "LastExitReport"
    private const val PREF_LAST_SEEN = "pulseplay_last_exit_seen"

    /** Trace text kept for the report (the main thread comes first). */
    private const val MAX_TRACE_CHARS = 60_000

    /** Must run off the main thread. */
    @JvmStatic
    fun check(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching { checkExits(context) }.onFailure { Log.w(TAG, "Could not read the last exits", it) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkExits(context: Context) {
        val manager = context.getSystemService<ActivityManager>() ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val lastSeen = prefs.getLong(PREF_LAST_SEEN, 0L)
        val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
        val newest = exits.maxOfOrNull { it.timestamp } ?: return
        // the first time, nothing old is reported
        if (lastSeen == 0L) {
            prefs.edit { putLong(PREF_LAST_SEEN, newest) }
            return
        }
        val anr = exits.filter { it.timestamp > lastSeen && it.reason == ApplicationExitInfo.REASON_ANR }
            .maxByOrNull { it.timestamp }
        prefs.edit { putLong(PREF_LAST_SEEN, maxOf(newest, lastSeen)) }
        if (anr == null) return
        val trace = anr.traceInputStream?.use { input ->
            input.bufferedReader().readText().take(MAX_TRACE_CHARS)
        } ?: anr.description.orEmpty()
        ErrorUtil.createNotification(
            context,
            ErrorInfo(
                arrayOf(mainThreadFirst(trace)),
                UserAction.SOMETHING_ELSE,
                "App not responding (${anr.description.orEmpty()})",
                null,
                R.string.pulseplay_last_anr
            )
        )
    }

    /** The trace with the main thread's block first: that is the one that hung. */
    private fun mainThreadFirst(trace: String): String {
        val start = trace.indexOf("\"main\"")
        if (start < 0) return trace
        val end = trace.indexOf("\n\n", start).let { if (it < 0) trace.length else it }
        return trace.substring(start, end) + "\n\n" + trace
    }
}
