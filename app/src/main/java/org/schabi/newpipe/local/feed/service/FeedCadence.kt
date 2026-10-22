package org.schabi.newpipe.local.feed.service

import java.time.Duration
import java.time.OffsetDateTime
import org.schabi.newpipe.extractor.ServiceList

/**
 * VideoPrime: how often the background check looks at each channel. Channels that publish often
 * are checked every time (every 15 minutes), quiet ones less often, so that new YouTube videos
 * arrive fast without asking YouTube for hundreds of channels every quarter of an hour.
 */
object FeedCadence {
    /**
     * Channels with the bell: every pulse of the alarm (10 minutes, see CheckAlarm). YouTube's
     * feed is cached up to 15 minutes, so a new video shows up within 10-25 minutes.
     */
    val BELL: Duration = Duration.ofMinutes(10)

    /** Published in the last week: every check. */
    val ACTIVE: Duration = Duration.ofMinutes(15)

    /** Published in the last month: every hour. */
    val RECENT: Duration = Duration.ofHours(1)

    /** Quiet for over a month (or nothing known): every three hours. */
    val QUIET: Duration = Duration.ofHours(3)

    /** Twitch channels: their lives are told by the live check, their videos can wait. */
    val TWITCH: Duration = Duration.ofHours(1)

    /**
     * On mobile data, never more often than this. The RSS check is small (about 5 KB a channel),
     * so mobile data gets the same 15 minutes as Wi-Fi.
     */
    val METERED_MIN: Duration = Duration.ofMinutes(15)

    /** The background work does not run at the exact minute: a little earlier still counts. */
    private val SLACK: Duration = Duration.ofMinutes(3)

    /**
     * @param bell whether the user wants to be told of this channel's videos: then it is looked
     * at in every check (by RSS, about 5 KB), however rarely it publishes
     */
    fun interval(
        serviceId: Int,
        latestUpload: OffsetDateTime?,
        now: OffsetDateTime,
        metered: Boolean,
        bell: Boolean = false
    ): Duration {
        val base = when {
            serviceId != ServiceList.YouTube.serviceId -> TWITCH
            bell -> BELL
            latestUpload == null -> QUIET
            latestUpload.isAfter(now.minusDays(7)) -> ACTIVE
            latestUpload.isAfter(now.minusDays(30)) -> RECENT
            else -> QUIET
        }
        return if (metered && base < METERED_MIN) METERED_MIN else base
    }

    fun isDue(
        now: OffsetDateTime,
        serviceId: Int,
        lastUpdated: OffsetDateTime?,
        latestUpload: OffsetDateTime?,
        metered: Boolean,
        bell: Boolean = false
    ): Boolean {
        if (lastUpdated == null) return true
        val wait = interval(serviceId, latestUpload, now, metered, bell).minus(SLACK)
        return !lastUpdated.isAfter(now.minus(wait))
    }
}
