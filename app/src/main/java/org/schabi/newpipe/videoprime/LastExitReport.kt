package org.schabi.newpipe.videoprime

import android.app.Activity
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import java.io.InputStream
import java.lang.ref.WeakReference
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.util.DeviceUtils

/**
 * PulsePlay: when the app stopped responding ("no responde") or was closed by a crash or for lack
 * of memory the last time, Android 11+ keeps why (and, for a hang, what each thread was doing).
 * Shown once, so that the cause is known instead of guessed: as an error notification on a phone,
 * and on a TV (where notifications are hardly seen) as the error screen the next time the app is
 * opened.
 */
object LastExitReport {
    private const val TAG = "LastExitReport"
    private const val PREF_LAST_SEEN = "pulseplay_last_exit_seen"

    /** Trace text kept for the report (the main thread comes first). */
    private const val MAX_TRACE_CHARS = 30_000

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
        // the first time, nothing from before is reported (also when Android has no exits yet)
        if (lastSeen == 0L) {
            prefs.edit { putLong(PREF_LAST_SEEN, System.currentTimeMillis()) }
            return
        }
        val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
        val newest = exits.maxOfOrNull { it.timestamp } ?: return
        val exit = exits.filter { it.timestamp > lastSeen && it.reason in REPORTED }
            .maxByOrNull { it.timestamp }
        if (exit != null) {
            // only a hang has a readable trace (a native crash's is a binary tombstone)
            val trace = if (exit.reason != ApplicationExitInfo.REASON_ANR) {
                null
            } else {
                runCatching {
                    exit.traceInputStream?.use(::readBounded)
                }.getOrNull()
            }
            report(context, exit, deviceSummary(context, manager) + "\n\n" + (trace?.let(::mainThreadFirst) ?: ""))
            if (DeviceUtils.isTv(context)) {
                // marked once shown (showPending): a report never seen comes back next time
                pendingSeen = maxOf(newest, lastSeen)
                Handler(Looper.getMainLooper()).post {
                    shownOn?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }?.let(::showPending)
                }
                return
            }
        }
        // marked after telling: a failure before does not lose the report
        prefs.edit { putLong(PREF_LAST_SEEN, maxOf(newest, lastSeen)) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun report(context: Context, exit: ApplicationExitInfo, details: String) {
        val anr = exit.reason == ApplicationExitInfo.REASON_ANR
        val info = ErrorInfo(
            arrayOf(details),
            UserAction.SOMETHING_ELSE,
            (if (anr) "App not responding" else "App closed: ${reasonName(exit.reason)}") +
                " (${exit.description.orEmpty()})",
            null,
            if (anr) R.string.pulseplay_last_anr else R.string.pulseplay_last_exit
        )
        if (DeviceUtils.isTv(context)) {
            pending = info
        } else {
            ErrorUtil.createNotification(context, info)
        }
    }

    /** Waiting for the app's main screen to show it (TV). */
    @Volatile
    private var pending: ErrorInfo? = null

    @Volatile
    private var pendingSeen = 0L

    /** The main screen, if the report is found after it was shown. */
    @Volatile
    private var shownOn: WeakReference<Activity>? = null

    /** On a TV, the report of the last closing, once, when the main screen is shown. */
    @JvmStatic
    fun showPending(activity: Activity) {
        shownOn = WeakReference(activity)
        val info = pending ?: return
        pending = null
        runCatching { activity.startActivity(ErrorUtil.getErrorActivityIntent(activity, info)) }
            .onSuccess {
                val seen = pendingSeen
                PreferenceManager.getDefaultSharedPreferences(activity)
                    .edit { putLong(PREF_LAST_SEEN, seen) }
            }
            .onFailure { Log.w(TAG, "Could not show the last exit", it) }
    }

    /** What the device has: a TV with little memory explains a closing by itself. */
    private fun deviceSummary(context: Context, manager: ActivityManager): String {
        val memory = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        return "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n" +
            "RAM: ${memory.totalMem / MB} MB (free ${memory.availMem / MB} MB), " +
            "app limit ${manager.memoryClass} MB (large ${manager.largeMemoryClass} MB), " +
            "low-RAM device: ${manager.isLowRamDevice}, TV: ${DeviceUtils.isTv(context)}"
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource use"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization failure"
        else -> "reason $reason"
    }

    private const val MB = 1024L * 1024L

    @RequiresApi(Build.VERSION_CODES.R)
    private val REPORTED = setOf(
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE
    )

    /** The trace with the main thread's block first: that is the one that hung. */
    private fun mainThreadFirst(trace: String): String {
        val start = trace.indexOf("\"main\"")
        if (start < 0) return trace
        val end = trace.indexOf("\n\n", start).let { if (it < 0) trace.length else it }
        return (trace.substring(start, end) + "\n\n" + trace).take(MAX_TRACE_CHARS)
    }

    /** Reads at most [MAX_TRACE_CHARS]: a full trace can be several MB. */
    private fun readBounded(input: InputStream): String {
        val reader = input.bufferedReader()
        val out = StringBuilder()
        val buffer = CharArray(BUFFER_CHARS)
        while (out.length < MAX_TRACE_CHARS) {
            val n = reader.read(buffer, 0, minOf(buffer.size, MAX_TRACE_CHARS - out.length))
            if (n <= 0) break
            out.append(buffer, 0, n)
        }
        return out.toString()
    }

    private const val BUFFER_CHARS = 4096
}
