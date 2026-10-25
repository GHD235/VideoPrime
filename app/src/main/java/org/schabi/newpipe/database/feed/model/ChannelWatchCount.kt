package org.schabi.newpipe.database.feed.model

/** VideoPrime: how many times the user played videos of a subscribed channel (from the history). */
data class ChannelWatchCount(
    val subscriptionId: Long,
    val watchCount: Long
)
