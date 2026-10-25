package org.schabi.newpipe.database.feed.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import java.time.OffsetDateTime
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.feed.model.RecentUpload
import org.schabi.newpipe.database.feed.model.SubscriptionFreshness
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity

@Dao
abstract class FeedDAO {
    @Query("DELETE FROM feed")
    abstract fun deleteAll(): Int

    /**
     * @param groupId          the group id to get feed streams of; use
     *                         [FeedGroupEntity.GROUP_ALL_ID] to not filter by group
     * @param includePlayed    if false, only return all of the live, never-played or non-finished
     *                         feed streams (see `@see` items); if true no filter is applied
     * @param includePartiallyPlayed if false, exclude streams that have been partially
     *                         played, keeping only never-played streams or streams whose
     *                         playback position is within the start/end playback thresholds;
     *                         if true, no partial-playback filter is applied
     * @param uploadDateBefore get only streams uploaded before this date (useful to filter out
     *                         future streams); use null to not filter by upload date
     * @return the feed streams filtered according to the conditions provided in the parameters
     * @see StreamStateEntity.isFinished()
     * @see StreamStateEntity.PLAYBACK_FINISHED_END_MILLISECONDS
     * @see StreamStateEntity.PLAYBACK_SAVE_THRESHOLD_START_MILLISECONDS
     */
    @Query(
        """
        SELECT s.*, sst.progress_time
        FROM streams s

        LEFT JOIN stream_state sst
        ON s.uid = sst.stream_id
        
        LEFT JOIN stream_history sh
        ON s.uid = sh.stream_id
        
        INNER JOIN feed f
        ON s.uid = f.stream_id

        LEFT JOIN feed_group_subscription_join fgs
        ON (
            :groupId <> ${FeedGroupEntity.GROUP_ALL_ID}
            AND fgs.subscription_id = f.subscription_id
        )

        WHERE (
            :groupId = ${FeedGroupEntity.GROUP_ALL_ID}
            OR fgs.group_id = :groupId
        )
        AND (
            :includePlayed
            OR sh.stream_id IS NULL
            OR sst.stream_id IS NULL
            OR sst.progress_time < s.duration * 1000 - ${StreamStateEntity.PLAYBACK_FINISHED_END_MILLISECONDS}
            OR sst.progress_time < s.duration * 1000 * 3 / 4
            OR s.stream_type = 'LIVE_STREAM'
            OR s.stream_type = 'AUDIO_LIVE_STREAM'
        )
        AND (
            :includePartiallyPlayed
            OR sh.stream_id IS NULL
            OR sst.stream_id IS NULL
            OR (sst.progress_time <= ${StreamStateEntity.PLAYBACK_SAVE_THRESHOLD_START_MILLISECONDS}
            AND sst.progress_time <= s.duration * 1000 / 4)
            OR (sst.progress_time >= s.duration * 1000 - ${StreamStateEntity.PLAYBACK_FINISHED_END_MILLISECONDS}
            AND sst.progress_time >= s.duration * 1000 * 3 / 4)
        )
        AND (
            :uploadDateBefore IS NULL
            OR s.upload_date IS NULL
            OR s.upload_date < :uploadDateBefore
        )

        ORDER BY s.upload_date IS NULL DESC, s.upload_date DESC, s.uploader ASC
        LIMIT 500
        """
    )
    abstract fun getStreams(
        groupId: Long,
        includePlayed: Boolean,
        includePartiallyPlayed: Boolean,
        uploadDateBefore: OffsetDateTime?
    ): Maybe<List<StreamWithState>>

    /**
     * Remove links to streams that are older than the given date
     * **but keep at least one stream per uploader**.
     *
     * One stream per uploader is kept because it is needed as reference
     * when fetching new streams to check if they are new or not.
     * @param offsetDateTime the newest date to keep, older streams are removed
     */
    @Query(
        """
        DELETE FROM feed
        WHERE feed.stream_id IN (SELECT uid from (
              SELECT s.uid,
              (SELECT MAX(upload_date)
                    FROM streams s1
                    INNER JOIN feed f1
                    ON s1.uid = f1.stream_id
                    WHERE f1.subscription_id = f.subscription_id) max_upload_date
              FROM streams s
              INNER JOIN feed f
              ON s.uid = f.stream_id
        
              WHERE s.upload_date < :offsetDateTime
              AND   s.upload_date <> max_upload_date))
        """
    )
    abstract fun unlinkStreamsOlderThan(offsetDateTime: OffsetDateTime)

    @Query(
        """
        DELETE FROM feed
        
        WHERE feed.subscription_id = :subscriptionId

        AND feed.stream_id IN (
            SELECT s.uid FROM streams s

            INNER JOIN feed f
            ON s.uid = f.stream_id

            WHERE s.stream_type = "LIVE_STREAM" OR s.stream_type = "AUDIO_LIVE_STREAM"
        )
        """
    )
    abstract fun unlinkOldLivestreams(subscriptionId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insert(feedEntity: FeedEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertAll(entities: List<FeedEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    internal abstract fun insertLastUpdated(lastUpdatedEntity: FeedLastUpdatedEntity): Long

    @Update(onConflict = OnConflictStrategy.IGNORE)
    internal abstract fun updateLastUpdated(lastUpdatedEntity: FeedLastUpdatedEntity)

    @Transaction
    open fun setLastUpdatedForSubscription(lastUpdatedEntity: FeedLastUpdatedEntity) {
        val id = insertLastUpdated(lastUpdatedEntity)

        if (id == -1L) {
            updateLastUpdated(lastUpdatedEntity)
        }
    }

    @Query(
        """
        SELECT MIN(lu.last_updated) FROM feed_last_updated lu

        INNER JOIN feed_group_subscription_join fgs
        ON fgs.subscription_id = lu.subscription_id AND fgs.group_id = :groupId
        """
    )
    abstract fun oldestSubscriptionUpdate(groupId: Long): Flowable<List<OffsetDateTime?>>

    @Query("SELECT MIN(last_updated) FROM feed_last_updated")
    abstract fun oldestSubscriptionUpdateFromAll(): Flowable<List<OffsetDateTime?>>

    @Query("SELECT COUNT(*) FROM feed_last_updated WHERE last_updated IS NULL")
    abstract fun notLoadedCount(): Flowable<Long>

    @Query(
        """
        SELECT COUNT(*) FROM subscriptions s
        
        INNER JOIN feed_group_subscription_join fgs
        ON s.uid = fgs.subscription_id AND fgs.group_id = :groupId

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id 

        WHERE lu.last_updated IS NULL
        """
    )
    abstract fun notLoadedCountForGroup(groupId: Long): Flowable<Long>

    /**
     * VideoPrime: when each channel was last checked and when it last published, to check the
     * active channels more often than the quiet ones.
     */
    @Query(
        """
        SELECT s.uid AS subscriptionId, s.service_id AS serviceId, lu.last_updated AS lastUpdated,
            (SELECT MAX(st.upload_date) FROM feed f
            INNER JOIN streams st ON st.uid = f.stream_id
            WHERE f.subscription_id = s.uid) AS latestUpload
        FROM subscriptions s

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id
        """
    )
    abstract fun getSubscriptionFreshness(): List<SubscriptionFreshness>

    /**
     * VideoPrime: the videos (not live streams) published since [since] and never watched, by
     * channel: what each channel has new since the last visit is counted from these.
     */
    @Query(
        """
        SELECT f.subscription_id AS subscriptionId, s.upload_date AS uploadDate
        FROM feed f
        INNER JOIN streams s ON s.uid = f.stream_id
        WHERE s.upload_date >= :since AND s.upload_date <= :now
        AND s.stream_type NOT IN ('LIVE_STREAM', 'AUDIO_LIVE_STREAM')
        AND NOT EXISTS (SELECT 1 FROM stream_history h WHERE h.stream_id = s.uid)
        """
    )
    abstract fun getRecentUnwatched(since: OffsetDateTime, now: OffsetDateTime): Flowable<List<RecentUpload>>

    /**
     * VideoPrime: the same videos as [getRecentUnwatched], for one channel: the ones marked NUEVO
     * inside it, so the mark and the number on its avatar always agree.
     */
    @Query(
        """
        SELECT s.url FROM feed f
        INNER JOIN streams s ON s.uid = f.stream_id
        INNER JOIN subscriptions sub ON sub.uid = f.subscription_id
        WHERE sub.service_id = :serviceId AND sub.url = :channelUrl
        AND s.upload_date > :since AND s.upload_date <= :now
        AND s.stream_type NOT IN ('LIVE_STREAM', 'AUDIO_LIVE_STREAM')
        AND NOT EXISTS (SELECT 1 FROM stream_history h WHERE h.stream_id = s.uid)
        """
    )
    abstract fun getUnwatchedUrlsSince(
        serviceId: Int,
        channelUrl: String,
        since: OffsetDateTime,
        now: OffsetDateTime
    ): List<String>

    /** VideoPrime: the channels with a live stream in the feed, to see when it ends. */
    @Query(
        """
        SELECT DISTINCT f.subscription_id FROM feed f
        INNER JOIN streams s ON s.uid = f.stream_id
        WHERE s.stream_type = "LIVE_STREAM" OR s.stream_type = "AUDIO_LIVE_STREAM"
        """
    )
    abstract fun getSubscriptionsWithLivestreams(): List<Long>

    /**
     * VideoPrime: before the extractor fix, the YouTube RSS feed stored Shorts with their
     * youtube.com/shorts/ URL, next to the same Short with its watch URL: unlink those copies.
     */
    @Query(
        """
        DELETE FROM feed WHERE feed.stream_id IN (
            SELECT uid FROM streams WHERE url LIKE 'https://www.youtube.com/shorts/%'
        )
        """
    )
    abstract fun unlinkShortsUrlDuplicates(): Int

    @Query(
        """
        SELECT s.* FROM subscriptions s

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id 

        WHERE lu.last_updated IS NULL OR lu.last_updated < :outdatedThreshold
        """
    )
    abstract fun getAllOutdated(outdatedThreshold: OffsetDateTime): Flowable<List<SubscriptionEntity>>

    @Query(
        """
        SELECT s.* FROM subscriptions s

        INNER JOIN feed_group_subscription_join fgs
        ON s.uid = fgs.subscription_id AND fgs.group_id = :groupId

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id

        WHERE lu.last_updated IS NULL OR lu.last_updated < :outdatedThreshold
        """
    )
    abstract fun getAllOutdatedForGroup(groupId: Long, outdatedThreshold: OffsetDateTime): Flowable<List<SubscriptionEntity>>

    @Query(
        """
        SELECT s.* FROM subscriptions s

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id

        WHERE 
            (lu.last_updated IS NULL OR lu.last_updated < :outdatedThreshold)
            AND s.notification_mode = :notificationMode
        """
    )
    abstract fun getOutdatedWithNotificationMode(
        outdatedThreshold: OffsetDateTime,
        @NotificationMode notificationMode: Int
    ): Flowable<List<SubscriptionEntity>>
}
