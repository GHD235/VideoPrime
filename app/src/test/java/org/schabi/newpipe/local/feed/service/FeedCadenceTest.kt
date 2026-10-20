package org.schabi.newpipe.local.feed.service

import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedCadenceTest {
    private val now = OffsetDateTime.of(2026, 10, 20, 12, 0, 0, 0, ZoneOffset.UTC)
    private val youtube = 0
    private val twitch = 5

    @Test
    fun activeChannelIsCheckedEveryQuarterOfAnHour() {
        val yesterday = now.minusDays(1)
        assertEquals(FeedCadence.ACTIVE, FeedCadence.interval(youtube, yesterday, now, false))
        assertTrue(FeedCadence.isDue(now, youtube, now.minusMinutes(15), yesterday, false))
        // the background work may run a little early
        assertTrue(FeedCadence.isDue(now, youtube, now.minusMinutes(13), yesterday, false))
        assertFalse(FeedCadence.isDue(now, youtube, now.minusMinutes(5), yesterday, false))
    }

    @Test
    fun channelsThatPublishLessAreCheckedLess() {
        val twoWeeksAgo = now.minusDays(14)
        assertEquals(FeedCadence.RECENT, FeedCadence.interval(youtube, twoWeeksAgo, now, false))
        assertFalse(FeedCadence.isDue(now, youtube, now.minusMinutes(30), twoWeeksAgo, false))
        assertTrue(FeedCadence.isDue(now, youtube, now.minusMinutes(60), twoWeeksAgo, false))

        val twoMonthsAgo = now.minusDays(60)
        assertEquals(FeedCadence.QUIET, FeedCadence.interval(youtube, twoMonthsAgo, now, false))
        assertFalse(FeedCadence.isDue(now, youtube, now.minusHours(2), twoMonthsAgo, false))
        assertTrue(FeedCadence.isDue(now, youtube, now.minusHours(3), twoMonthsAgo, false))
    }

    @Test
    fun neverCheckedOrUnknownChannels() {
        assertTrue(FeedCadence.isDue(now, youtube, null, null, false))
        assertTrue(FeedCadence.isDue(now, youtube, null, now.minusDays(1), true))
        assertEquals(FeedCadence.QUIET, FeedCadence.interval(youtube, null, now, false))
    }

    @Test
    fun mobileDataWaitsAtLeastHalfAnHour() {
        val yesterday = now.minusDays(1)
        assertEquals(FeedCadence.METERED_MIN, FeedCadence.interval(youtube, yesterday, now, true))
        assertFalse(FeedCadence.isDue(now, youtube, now.minusMinutes(15), yesterday, true))
        assertTrue(FeedCadence.isDue(now, youtube, now.minusMinutes(30), yesterday, true))
        // longer intervals stay as they are
        assertEquals(FeedCadence.QUIET, FeedCadence.interval(youtube, now.minusDays(90), now, true))
    }

    @Test
    fun twitchChannelsHourly() {
        assertEquals(FeedCadence.TWITCH, FeedCadence.interval(twitch, now.minusHours(1), now, false))
        assertFalse(FeedCadence.isDue(now, twitch, now.minusMinutes(20), now.minusHours(1), false))
    }
}
