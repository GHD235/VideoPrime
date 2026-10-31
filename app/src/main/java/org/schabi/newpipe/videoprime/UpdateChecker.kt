package org.schabi.newpipe.videoprime

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe

/**
 * VideoPrime: tells when a newer APK has been published on GitHub (the "nightly" release),
 * with a notification that downloads it. Checked from the background worker, at most twice a
 * day, and once per new version.
 */
object UpdateChecker {
    private const val RELEASE_URL =
        "https://api.github.com/repos/GHD235/VideoPrime/releases/tags/nightly"
    private val APK_NAME = Regex("""(?:PulsePlay|VideoPrime)_.+_([0-9a-f]{7,40})\.apk""")
    private const val PREF_LAST_CHECK = "videoprime_update_last_check"
    private const val PREF_NOTIFIED = "videoprime_update_notified"
    private const val PREF_PENDING_COMMIT = "pulseplay_update_pending_commit"
    private const val PREF_PENDING_URL = "pulseplay_update_pending_url"

    // the repository was renamed from VideoPrime to PulsePlay: both addresses are ours
    private val DOWNLOAD_PREFIXES = listOf(
        "https://github.com/GHD235/PulsePlay/releases/download/",
        "https://github.com/GHD235/VideoPrime/releases/download/"
    )

    /** A fallback: with instant notifications on, a new version is told as it is published. */
    private const val CHECK_INTERVAL_MILLIS = 3 * 60 * 60 * 1000L
    private const val NOTIFICATION_ID = 2_000_001

    /** Must run off the main thread. */
    fun checkIfDue(context: Context) {
        // builds made outside GitHub's CI do not know which APK they are
        if (BuildConfig.GIT_COMMIT.isEmpty()) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(PREF_LAST_CHECK, 0) < CHECK_INTERVAL_MILLIS) return
        prefs.edit { putLong(PREF_LAST_CHECK, now) }

        val release = JsonParser.`object`().from(
            NewPipe.getDownloader().get(RELEASE_URL).responseBody()
        )
        val asset = release.getArray("assets")
            .filterIsInstance<JsonObject>()
            .firstOrNull { APK_NAME.matches(it.getString("name", "")) } ?: return
        val commit = APK_NAME.find(asset.getString("name"))!!.groupValues[1]
        notifyIfNew(context, commit, asset.getString("browser_download_url"))
    }

    /**
     * PulsePlay: the address of a newer APK this app has heard of, or null (also once that
     * version is the one installed).
     */
    @JvmStatic
    fun pendingUpdate(context: Context): String? {
        val current = BuildConfig.GIT_COMMIT
        if (current.isEmpty()) return null
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val commit = prefs.getString(PREF_PENDING_COMMIT, null) ?: return null
        if (commit.startsWith(current) || current.startsWith(commit)) return null
        return prefs.getString(PREF_PENDING_URL, null)
    }

    /** The push server says a new APK was published (right after the build). */
    fun onPush(context: Context, commit: String, apkUrl: String) {
        if (BuildConfig.GIT_COMMIT.isEmpty() || commit.isEmpty()) return
        // only our own releases
        if (DOWNLOAD_PREFIXES.none { apkUrl.startsWith(it) } || !APK_NAME.matches(apkUrl.substringAfterLast('/'))) return
        notifyIfNew(context, commit, apkUrl)
    }

    @Synchronized
    private fun notifyIfNew(context: Context, commit: String, apkUrl: String) {
        val current = BuildConfig.GIT_COMMIT
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (commit.startsWith(current) || current.startsWith(commit)) {
            // the latest is the one installed: an older one remembered must not be offered
            prefs.edit {
                remove(PREF_PENDING_COMMIT)
                remove(PREF_PENDING_URL)
            }
            return
        }
        // remembered for the notice inside the app (on a TV notifications are hardly seen)
        prefs.edit {
            putString(PREF_PENDING_COMMIT, commit)
            putString(PREF_PENDING_URL, apkUrl)
        }
        if (prefs.getString(PREF_NOTIFIED, null) == commit) return
        // only marked as told once the notification really shows (it is tried again later)
        if (notify(context, apkUrl)) {
            prefs.edit { putString(PREF_NOTIFIED, commit) }
        }
    }

    /** @return whether the notification was shown */
    private fun notify(context: Context, apkUrl: String): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        // downloaded inside the app, then Android's installer (see UpdateActivity)
        val download = Intent(context, UpdateActivity::class.java)
            .putExtra(UpdateActivity.EXTRA_URL, apkUrl)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val notification = NotificationCompat.Builder(
            context,
            context.getString(R.string.app_update_notification_channel_id)
        )
            .setSmallIcon(R.drawable.ic_newpipe_update)
            .setContentTitle(context.getString(R.string.videoprime_update_title))
            .setContentText(context.getString(R.string.videoprime_update_text))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntentCompat.getActivity(
                    context,
                    NOTIFICATION_ID,
                    download,
                    PendingIntent.FLAG_UPDATE_CURRENT,
                    false
                )
            )
            .build()
        return runCatching { manager.notify(NOTIFICATION_ID, notification) }.isSuccess
    }
}
