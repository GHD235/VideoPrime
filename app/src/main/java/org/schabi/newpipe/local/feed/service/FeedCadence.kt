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
    /** Published in the last week: every check. */
    val ACTIVE: Duration = Duration.ofMinutes(15)

    /** Published in the last month: every hour. */
    val RECENT: Duration = Duration.ofHours(1)

    /** Quiet for over a month (or nothing known): every three hours. */
    val QUIET: Duration = Duration.ofHours(3)

    /** Twitch channels: their lives are told by the live check, their videos can wait. */
    val TWITCH: Duration = Duration.ofHours(1)

    /** On mobile data, never more often than this. */
    val METERED_MIN: Duration = Duration.ofMinutes(30)

    /** The background work does not run at the exact minute: a little earlier still counts. */
    private val SLACK: Duration = Duration.ofMinutes(3)

    fun interval(serviceId: Int, latestUpload: OffsetDateTime?, now: OffsetDateTime, metered: Boolean): Duration {
        val base = when {
            serviceId != ServiceList.YouTube.serviceId -> TWITCH
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
        metered: Boolean
    ): Boolean {
        if (lastUpdated == null) return true
        val wait = interval(serviceId, latestUpload, now, metered).minus(SLACK)
        return !lastUpdated.isAfter(now.minus(wait))
    }
}
