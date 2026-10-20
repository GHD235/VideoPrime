package org.schabi.newpipe.database.feed.model

import java.time.OffsetDateTime

/** VideoPrime: when a channel was last checked for new videos and when it last published. */
data class SubscriptionFreshness(
    val subscriptionId: Long,
    val serviceId: Int,
    val lastUpdated: OffsetDateTime?,
    val latestUpload: OffsetDateTime?
)
