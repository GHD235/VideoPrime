package org.schabi.newpipe.videoprime

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.twitch.TwitchLiveChannels
import org.schabi.newpipe.extractor.services.twitch.linkHandler.TwitchChannelLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.image.CoilHelper
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * VideoPrime: tells when a subscribed Twitch channel (with notifications on) goes live.
 * Called from the background worker; each live stream is told only once.
 */
object LiveNotifications {
    private const val PREF_LIVE_NOW = "videoprime_twitch_live_now"

    /** Must run off the main thread: it asks Twitch who is live. */
    fun check(context: Context) {
        val logins = NewPipeDatabase.getInstance(context).subscriptionDAO().getAll()
            .blockingFirst(emptyList())
            .filter {
                it.serviceId == ServiceList.Twitch.serviceId &&
                    it.notificationMode == NotificationMode.ENABLED
            }
            .mapNotNull {
                runCatching { TwitchChannelLinkHandlerFactory.getInstance().getId(it.url) }
                    .getOrNull()
            }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (logins.isEmpty()) {
            prefs.edit { remove(PREF_LIVE_NOW) }
            return
        }

        val live = TwitchLiveChannels.fetch(logins)
        val before = prefs.getStringSet(PREF_LIVE_NOW, emptySet()).orEmpty()
        live.filter { it.url !in before }.forEach { notify(context, it) }
        prefs.edit { putStringSet(PREF_LIVE_NOW, live.map { it.url }.toSet()) }
    }

    private fun notify(context: Context, item: StreamInfoItem) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val avatar = ImageStrategy.choosePreferredImage(item.uploaderAvatars)
        val icon = CoilHelper.loadBitmapBlocking(context, avatar, R.drawable.ic_live_tv)
        val notification = NotificationCompat.Builder(
            context,
            context.getString(R.string.streams_notification_channel_id)
        )
            .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
            .setLargeIcon(icon)
            .setContentTitle(context.getString(R.string.videoprime_live_now, item.uploaderName))
            .setContentText(item.name)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(
                PendingIntentCompat.getActivity(
                    context,
                    item.url.hashCode(),
                    NavigationHelper.getStreamIntent(context, item.serviceId, item.url, item.name),
                    PendingIntent.FLAG_UPDATE_CURRENT,
                    false
                )
            )
            .build()
        runCatching { manager.notify(item.url.hashCode(), notification) }
    }
}
