package org.schabi.newpipe.videoprime

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.text.format.DateUtils
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

/**
 * VideoPrime: what the background checks for new videos and live streams are doing, to show it in
 * the notification settings and to ask again for the background permission when the checks stop
 * (battery savers, above all Xiaomi's, stop them without telling).
 */
object BackgroundStatus {
    private const val LAST_YOUTUBE_KEY = "videoprime_last_youtube_check"
    private const val LAST_TWITCH_KEY = "videoprime_last_twitch_check"

    /** Without a check for this long, the checks are not running as they should. */
    const val STALE_MILLIS = 2 * 60 * 60 * 1000L

    @JvmStatic
    fun markYouTubeCheck(context: Context) = mark(context, LAST_YOUTUBE_KEY)

    @JvmStatic
    fun markTwitchCheck(context: Context) = mark(context, LAST_TWITCH_KEY)

    private fun mark(context: Context, key: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putLong(key, System.currentTimeMillis())
        }
    }

    private fun last(context: Context, key: String): Long = PreferenceManager.getDefaultSharedPreferences(context).getLong(key, 0L)

    /** Whether the last check is too old (0 = never checked yet: not stale). */
    @JvmStatic
    fun isStale(context: Context): Boolean {
        val last = maxOf(last(context, LAST_YOUTUBE_KEY), last(context, LAST_TWITCH_KEY))
        // without network the checks wait for it: that is not the phone stopping the app
        val online = context.getSystemService(ConnectivityManager::class.java)?.activeNetwork != null
        return online && last != 0L && System.currentTimeMillis() - last > STALE_MILLIS
    }

    /** The lines of the "State of the notifications" setting. */
    @JvmStatic
    fun summary(context: Context): String {
        val lines = mutableListOf(
            context.getString(R.string.videoprime_status_youtube, ago(context, last(context, LAST_YOUTUBE_KEY))),
            context.getString(R.string.videoprime_status_twitch, ago(context, last(context, LAST_TWITCH_KEY))),
            context.getString(
                if (BackgroundPermission.isAllowed(context)) {
                    R.string.videoprime_status_battery_ok
                } else {
                    R.string.videoprime_status_battery_restricted
                }
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            if (activityManager?.isBackgroundRestricted == true) {
                lines += context.getString(R.string.videoprime_status_background_restricted)
            }
            context.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket?.let {
                lines += context.getString(R.string.videoprime_status_bucket, bucketName(context, it))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarms = context.getSystemService(AlarmManager::class.java)
            if (alarms?.canScheduleExactAlarms() == false) {
                lines += context.getString(R.string.videoprime_status_no_exact_alarms)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = manager?.getNotificationChannel(
                context.getString(R.string.streams_notification_channel_id)
            )
            if (channel != null && channel.importance < NotificationManager.IMPORTANCE_HIGH) {
                lines += context.getString(R.string.videoprime_status_no_popups)
            }
        }
        return lines.joinToString("\n")
    }

    /** Whether new videos would not pop up (their channel's importance was lowered). */
    @JvmStatic
    fun popupsOff(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(context.getString(R.string.streams_notification_channel_id))
        return channel != null && channel.importance < NotificationManager.IMPORTANCE_HIGH
    }

    private fun ago(context: Context, millis: Long): String = if (millis == 0L) {
        context.getString(R.string.videoprime_status_never)
    } else {
        DateUtils.getRelativeTimeSpanString(
            millis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    private fun bucketName(context: Context, bucket: Int): String = context.getString(
        when {
            bucket <= UsageStatsManager.STANDBY_BUCKET_ACTIVE -> R.string.videoprime_bucket_active
            bucket <= UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> R.string.videoprime_bucket_working
            bucket <= UsageStatsManager.STANDBY_BUCKET_FREQUENT -> R.string.videoprime_bucket_frequent
            bucket <= UsageStatsManager.STANDBY_BUCKET_RARE -> R.string.videoprime_bucket_rare
            else -> R.string.videoprime_bucket_restricted
        }
    )
}
