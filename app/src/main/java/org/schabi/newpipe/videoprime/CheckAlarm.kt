package org.schabi.newpipe.videoprime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import org.schabi.newpipe.local.feed.notifications.NotificationHelper
import org.schabi.newpipe.local.feed.notifications.NotificationWorker

/**
 * VideoPrime: the pulse of the notifications. Android's scheduled work waits for the phone's
 * maintenance windows when it sleeps (Doze), which can be hours; an exact alarm that is allowed
 * while the phone sleeps wakes the app every few minutes (Android lets it fire once every 9 or
 * so minutes in Doze). Each pulse asks Twitch who is live right away (a single small request,
 * allowed by the alarm) and starts an expedited check of the YouTube channels that are due.
 */
object CheckAlarm {
    private const val TAG = "VideoPrimeAlarm"
    private const val INTERVAL_MILLIS = 10 * 60 * 1000L
    private const val REQUEST_CODE = 0x7631
    private const val TWITCH_MAX_MILLIS = 7_000L

    /** Plans the next pulse (replaces the one planned, if any). */
    @JvmStatic
    fun schedule(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(app)
        if (!NotificationHelper.areNewStreamsNotificationsEnabled(app)) {
            alarms.cancel(pending)
            return
        }
        val at = SystemClock.elapsedRealtime() + INTERVAL_MILLIS
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                AlarmManagerCompat.setExactAndAllowWhileIdle(
                    alarms,
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    at,
                    pending
                )
            } else {
                // without the exact alarm permission (Android 12, if the user took it away)
                AlarmManagerCompat.setAndAllowWhileIdle(
                    alarms,
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    at,
                    pending
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not plan the next check", e)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, Receiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** The pulse: Twitch now, YouTube in an expedited work; then the next pulse. */
    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val app = context.applicationContext
            schedule(app)
            NotificationWorker.runExpedited(app)
            val result = goAsync()
            Thread {
                // the alarm lets the app use the network for a few seconds, even in Doze; a
                // receiver may not take more than about 10 s, so Twitch gets at most 7
                val check = Thread {
                    try {
                        LiveNotifications.check(app)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not check Twitch live channels", e)
                    }
                }
                check.start()
                try {
                    check.join(TWITCH_MAX_MILLIS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                } finally {
                    result.finish()
                }
            }.start()
        }
    }

    /** After a restart of the phone or an update of the app, the pulse is planned again. */
    class RestartReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                    schedule(context)
                    NotificationWorker.initialize(context)
                }
            }
        }
    }
}
