/*
 * SPDX-FileCopyrightText: 2018-2022 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.history.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.stream.StreamStatisticsEntry

@Dao
abstract class StreamHistoryDAO : BasicDAO<StreamHistoryEntity> {

    @Query("SELECT * FROM stream_history")
    abstract override fun getAll(): Flowable<List<StreamHistoryEntity>>

    @Query("DELETE FROM stream_history")
    abstract override fun deleteAll(): Int

    override fun listByService(serviceId: Int): Flowable<List<StreamHistoryEntity>> {
        throw UnsupportedOperationException()
    }

    @get:Query("SELECT * FROM streams INNER JOIN stream_history ON uid = stream_id ORDER BY access_date DESC")
    abstract val history: Flowable<MutableList<StreamHistoryEntry>>

    /** PulsePlay: the last [limit] views (for the car, which cannot receive the whole history). */
    @Query("SELECT * FROM streams INNER JOIN stream_history ON uid = stream_id ORDER BY access_date DESC LIMIT :limit")
    abstract fun getRecentHistory(limit: Int): Single<List<StreamHistoryEntry>>

    /** PulsePlay: the last view of one video, with the video. */
    @Query(
        "SELECT * FROM streams INNER JOIN stream_history ON uid = stream_id WHERE stream_id = :streamId " +
            "ORDER BY access_date DESC LIMIT 1"
    )
    abstract fun getLastViewWithStream(streamId: Long): Maybe<StreamHistoryEntry>

    @get:Query("SELECT * FROM streams INNER JOIN stream_history ON uid = stream_id ORDER BY uid ASC")
    abstract val historySortedById: Flowable<MutableList<StreamHistoryEntry>>

    @Query("SELECT * FROM stream_history WHERE stream_id = :streamId ORDER BY access_date DESC LIMIT 1")
    abstract fun getLatestEntry(streamId: Long): StreamHistoryEntity?

    /** PulsePlay: which of these addresses are in the history (watched before). */
    @Query(
        "SELECT DISTINCT streams.url FROM streams INNER JOIN stream_history " +
            "ON streams.uid = stream_history.stream_id " +
            "WHERE streams.service_id = :serviceId AND streams.url IN (:urls)"
    )
    // PulsePlay: with the service the (service_id, url) index finds each address at once
    abstract fun getWatchedUrls(serviceId: Int, urls: List<String>): List<String>

    @Query("DELETE FROM stream_history WHERE stream_id = :streamId")
    abstract fun deleteStreamHistory(streamId: Long): Int

    // Select the latest entry and watch count for each stream id on history table
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT * FROM streams

        INNER JOIN (
            SELECT stream_id, MAX(access_date) AS latestAccess, SUM(repeat_count) AS watchCount
            FROM stream_history
            GROUP BY stream_id
        )
        ON uid = stream_id

        LEFT JOIN (SELECT stream_id AS stream_id_alias, progress_time FROM stream_state )
        ON uid = stream_id_alias
        """
    )
    abstract fun getStatistics(): Flowable<MutableList<StreamStatisticsEntry>>
}
