package org.schabi.newpipe.database.feed.model

import java.time.OffsetDateTime

/** VideoPrime: a recent video of a channel that was not watched, to count what is new. */
data class RecentUpload(
    val subscriptionId: Long,
    val uploadDate: OffsetDateTime?
)
