package org.schabi.newpipe.videoprime.sync

import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.videoprime.QueueStore

/**
 * PulsePlay: one kind of thing that is synced. [snapshot] lists everything this device has now,
 * by a key that means the same on every device; [apply] makes this device match a change from
 * another one (a null value: it was deleted there). What changed is found by comparing
 * snapshots, so the app's own code that writes these things needs no changes.
 */
internal interface SyncSection {
    val kind: String

    fun snapshot(): Map<String, JSONObject>

    fun apply(changes: Map<String, JSONObject?>)
}

/** Reads a whole query (small tables, run on the sync's thread). */
private inline fun <T> SupportSQLiteDatabase.rows(sql: String, args: Array<Any?> = emptyArray(), read: (Cursor) -> T): List<T> = query(sql, args).use { cursor ->
    val list = ArrayList<T>(cursor.count)
    while (cursor.moveToNext()) list += read(cursor)
    list
}

private fun Cursor.str(index: Int): String? = if (isNull(index)) null else getString(index)

private fun key(serviceId: Int, url: String) = "$serviceId|$url"

private fun splitKey(key: String): Pair<Int, String>? {
    val bar = key.indexOf('|')
    if (bar <= 0) return null
    val serviceId = key.substring(0, bar).toIntOrNull() ?: return null
    return serviceId to key.substring(bar + 1)
}

/** The parts of a stream that every device needs to show it (in history, playlists). */
private fun streamJson(c: Cursor, at: Int): JSONObject = JSONObject()
    .put("s", c.getInt(at))
    .put("u", c.getString(at + 1))
    .put("t", c.str(at + 2).orEmpty())
    .put("ty", c.str(at + 3).orEmpty())
    .put("d", c.getLong(at + 4))
    .put("up", c.str(at + 5).orEmpty())
    .put("uu", c.str(at + 6).orEmpty())
    .put("th", c.str(at + 7).orEmpty())

private const val STREAM_COLUMNS =
    "s.service_id, s.url, s.title, s.stream_type, s.duration, s.uploader, s.uploader_url, s.thumbnail_url"

/** Adds the stream if this device does not have it, and returns its local id. */
private fun upsertStream(db: SupportSQLiteDatabase, stream: JSONObject): Long? {
    val url = stream.optString("u").ifEmpty { return null }
    val serviceId = stream.optInt("s", -1).takeIf { it >= 0 } ?: return null
    // INSERT OR IGNORE + UPDATE, not an upsert: Android before 11 has an SQLite without it
    db.execSQL(
        """INSERT OR IGNORE INTO streams (service_id, url, title, stream_type, duration, uploader, uploader_url, thumbnail_url)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
        arrayOf<Any?>(
            serviceId,
            url,
            stream.optString("t"),
            stream.optString("ty").ifEmpty { "VIDEO_STREAM" },
            stream.optLong("d"),
            stream.optString("up"),
            stream.optString("uu").ifEmpty { null },
            stream.optString("th").ifEmpty { null }
        )
    )
    val duration = stream.optLong("d")
    db.execSQL(
        """UPDATE streams SET title = ?, duration = CASE WHEN ? > 0 THEN ? ELSE duration END,
             thumbnail_url = COALESCE(NULLIF(?, ''), thumbnail_url)
           WHERE service_id = ? AND url = ?""",
        arrayOf<Any?>(stream.optString("t"), duration, duration, stream.optString("th"), serviceId, url)
    )
    return db.rows("SELECT uid FROM streams WHERE service_id = ? AND url = ?", arrayOf(serviceId, url)) { it.getLong(0) }
        .firstOrNull()
}

internal class SubscriptionSection(private val database: AppDatabase) : SyncSection {
    override val kind = "sub"

    override fun snapshot(): Map<String, JSONObject> = database.openHelper.readableDatabase
        .rows("SELECT service_id, url, name, avatar_url, notification_mode FROM subscriptions WHERE url IS NOT NULL") { c ->
            key(c.getInt(0), c.getString(1)) to JSONObject()
                .put("n", c.str(2).orEmpty())
                .put("a", c.str(3).orEmpty())
                .put("b", c.getInt(4))
        }.toMap()

    override fun apply(changes: Map<String, JSONObject?>) = database.runInTransaction {
        val db = database.openHelper.writableDatabase
        for ((key, value) in changes) {
            val (serviceId, url) = splitKey(key) ?: continue
            if (value == null) {
                db.execSQL("DELETE FROM subscriptions WHERE service_id = ? AND url = ?", arrayOf<Any?>(serviceId, url))
            } else {
                val avatar = value.optString("a").ifEmpty { null }
                db.execSQL(
                    "INSERT OR IGNORE INTO subscriptions (service_id, url, name, avatar_url, notification_mode) VALUES (?, ?, ?, ?, ?)",
                    arrayOf<Any?>(serviceId, url, value.optString("n"), avatar, value.optInt("b"))
                )
                db.execSQL(
                    """UPDATE subscriptions SET name = ?, avatar_url = COALESCE(?, avatar_url), notification_mode = ?
                       WHERE service_id = ? AND url = ?""",
                    arrayOf<Any?>(value.optString("n"), avatar, value.optInt("b"), serviceId, url)
                )
            }
        }
    }
}

/** What was watched, when, how often, and where it was left (the playback position). */
internal class HistorySection(private val database: AppDatabase) : SyncSection {
    override val kind = "hist"

    override fun snapshot(): Map<String, JSONObject> = database.openHelper.readableDatabase.rows(
        """SELECT $STREAM_COLUMNS, MAX(h.access_date), SUM(h.repeat_count), st.progress_time
           FROM streams s JOIN stream_history h ON h.stream_id = s.uid
           LEFT JOIN stream_state st ON st.stream_id = s.uid
           GROUP BY s.uid"""
    ) { c ->
        key(c.getInt(0), c.getString(1)) to streamJson(c, 0)
            .put("w", c.getLong(8))
            .put("r", c.getLong(9))
            .put("p", if (c.isNull(10)) 0L else c.getLong(10))
    }.toMap()

    override fun apply(changes: Map<String, JSONObject?>) = database.runInTransaction {
        val db = database.openHelper.writableDatabase
        for ((key, value) in changes) {
            val (serviceId, url) = splitKey(key) ?: continue
            if (value == null) {
                val uid = db.rows("SELECT uid FROM streams WHERE service_id = ? AND url = ?", arrayOf(serviceId, url)) { it.getLong(0) }
                    .firstOrNull() ?: continue
                db.execSQL("DELETE FROM stream_history WHERE stream_id = ?", arrayOf<Any?>(uid))
                db.execSQL("DELETE FROM stream_state WHERE stream_id = ?", arrayOf<Any?>(uid))
                continue
            }
            val uid = upsertStream(db, value) ?: continue
            // one entry with the latest access and the total count: the same snapshot everywhere
            db.execSQL("DELETE FROM stream_history WHERE stream_id = ?", arrayOf<Any?>(uid))
            db.execSQL(
                "INSERT INTO stream_history (stream_id, access_date, repeat_count) VALUES (?, ?, ?)",
                arrayOf<Any?>(uid, value.optLong("w"), value.optLong("r", 1))
            )
            val progress = value.optLong("p")
            if (progress > 0) {
                db.execSQL("INSERT OR REPLACE INTO stream_state (stream_id, progress_time) VALUES (?, ?)", arrayOf<Any?>(uid, progress))
            } else {
                db.execSQL("DELETE FROM stream_state WHERE stream_id = ?", arrayOf<Any?>(uid))
            }
        }
    }
}

/**
 * The user's own playlists. Their ids differ on every device, so each gets a random sync id
 * (kept in [ids], sync id → local id) the first time it is seen.
 */
internal class PlaylistSection(private val database: AppDatabase, private val ids: SyncIds) : SyncSection {
    override val kind = "plist"

    override fun snapshot(): Map<String, JSONObject> {
        val db = database.openHelper.readableDatabase
        val playlists = db.rows("SELECT uid, name FROM playlists") { it.getLong(0) to it.str(1).orEmpty() }
        val items = HashMap<Long, JSONArray>()
        db.rows(
            """SELECT j.playlist_id, $STREAM_COLUMNS FROM playlist_stream_join j
               JOIN streams s ON s.uid = j.stream_id ORDER BY j.playlist_id, j.join_index"""
        ) { c -> items.getOrPut(c.getLong(0)) { JSONArray() }.put(streamJson(c, 1)) }
        return playlists.associate { (uid, name) ->
            ids.syncIdOf(uid) to JSONObject().put("n", name).put("i", items[uid] ?: JSONArray())
        }
    }

    override fun apply(changes: Map<String, JSONObject?>) = database.runInTransaction {
        val db = database.openHelper.writableDatabase
        for ((syncId, value) in changes) {
            val existing = ids.localIdOf(syncId)?.takeIf { uid ->
                db.rows("SELECT 1 FROM playlists WHERE uid = ?", arrayOf(uid)) { true }.isNotEmpty()
            }
            if (value == null) {
                existing?.let { db.execSQL("DELETE FROM playlists WHERE uid = ?", arrayOf<Any?>(it)) }
                ids.forget(syncId)
                continue
            }
            val streams = value.optJSONArray("i") ?: JSONArray()
            val streamIds = (0 until streams.length()).mapNotNull { upsertStream(db, streams.getJSONObject(it)) }
            val uid = existing ?: run {
                db.execSQL(
                    "INSERT INTO playlists (name, is_thumbnail_permanent, thumbnail_stream_id, display_index) VALUES (?, 0, -1, -1)",
                    arrayOf<Any?>(value.optString("n"))
                )
                db.rows("SELECT last_insert_rowid()") { it.getLong(0) }.first().also { ids.link(syncId, it) }
            }
            db.execSQL("UPDATE playlists SET name = ? WHERE uid = ?", arrayOf<Any?>(value.optString("n"), uid))
            db.execSQL("DELETE FROM playlist_stream_join WHERE playlist_id = ?", arrayOf<Any?>(uid))
            streamIds.forEachIndexed { index, streamId ->
                db.execSQL(
                    "INSERT INTO playlist_stream_join (playlist_id, stream_id, join_index) VALUES (?, ?, ?)",
                    arrayOf<Any?>(uid, streamId, index)
                )
            }
            // the cover follows the first video, unless the user picked one on this device
            db.execSQL(
                "UPDATE playlists SET thumbnail_stream_id = ? WHERE uid = ? AND is_thumbnail_permanent = 0",
                arrayOf<Any?>(streamIds.firstOrNull() ?: -1L, uid)
            )
        }
    }
}

/** YouTube playlists the user saved (bookmarks). */
internal class RemotePlaylistSection(private val database: AppDatabase) : SyncSection {
    override val kind = "rplist"

    override fun snapshot(): Map<String, JSONObject> = database.openHelper.readableDatabase
        .rows("SELECT service_id, url, name, thumbnail_url, uploader, stream_count FROM remote_playlists WHERE url IS NOT NULL") { c ->
            key(c.getInt(0), c.getString(1)) to JSONObject()
                .put("n", c.str(2).orEmpty())
                .put("th", c.str(3).orEmpty())
                .put("up", c.str(4).orEmpty())
                .put("c", if (c.isNull(5)) -1L else c.getLong(5))
        }.toMap()

    override fun apply(changes: Map<String, JSONObject?>) = database.runInTransaction {
        val db = database.openHelper.writableDatabase
        for ((key, value) in changes) {
            val (serviceId, url) = splitKey(key) ?: continue
            if (value == null) {
                db.execSQL("DELETE FROM remote_playlists WHERE service_id = ? AND url = ?", arrayOf<Any?>(serviceId, url))
            } else {
                val thumbnail = value.optString("th").ifEmpty { null }
                val count = value.optLong("c", -1).takeIf { it >= 0 }
                db.execSQL(
                    """INSERT OR IGNORE INTO remote_playlists (service_id, url, name, thumbnail_url, uploader, display_index, stream_count)
                       VALUES (?, ?, ?, ?, ?, -1, ?)""",
                    arrayOf<Any?>(serviceId, url, value.optString("n"), thumbnail, value.optString("up"), count)
                )
                db.execSQL(
                    """UPDATE remote_playlists SET name = ?, thumbnail_url = ?, uploader = ?, stream_count = ?
                       WHERE service_id = ? AND url = ?""",
                    arrayOf<Any?>(value.optString("n"), thumbnail, value.optString("up"), count, serviceId, url)
                )
            }
        }
    }
}

/** The saved play queues (Biblioteca → Colas). Which one plays stays each device's own. */
internal class QueueSection(private val store: QueueStore) : SyncSection {
    override val kind = "queue"

    override fun snapshot(): Map<String, JSONObject> = store.load().queues.associate { queue -> queue.id.toString() to store.queueToJson(queue) }

    override fun apply(changes: Map<String, JSONObject?>) = store.update { content ->
        val queues = content.queues.associateBy { it.id.toString() }.toMutableMap()
        for ((key, value) in changes) {
            if (value == null) {
                queues.remove(key)
            } else {
                runCatching { store.parseQueue(value) }.getOrNull()?.let { queues[key] = it }
            }
        }
        val list = queues.values.sortedBy { it.id }
        content.copy(queues = list, activeId = content.activeId?.takeIf { id -> list.any { it.id == id } })
    }
}

/** The settings that mean the same on every device (not storage, battery or this screen's). */
internal class PreferenceSection(context: Context) : SyncSection {
    override val kind = "pref"
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val keys: Set<String> = SYNCED_KEYS.map(context::getString).toSet()

    override fun snapshot(): Map<String, JSONObject> = prefs.all
        .filterKeys { it in keys }
        .mapNotNull { (key, value) ->
            when (value) {
                is Boolean -> key to JSONObject().put("ty", "b").put("v", value)
                is Int -> key to JSONObject().put("ty", "i").put("v", value)
                is Long -> key to JSONObject().put("ty", "l").put("v", value)
                is Float -> key to JSONObject().put("ty", "f").put("v", value.toDouble())
                is String -> key to JSONObject().put("ty", "s").put("v", value)
                else -> null
            }
        }.toMap()

    override fun apply(changes: Map<String, JSONObject?>) = prefs.edit {
        for ((key, value) in changes) {
            if (key !in keys) continue
            if (value == null) {
                remove(key)
                continue
            }
            when (value.optString("ty")) {
                "b" -> putBoolean(key, value.optBoolean("v"))
                "i" -> putInt(key, value.optInt("v"))
                "l" -> putLong(key, value.optLong("v"))
                "f" -> putFloat(key, value.optDouble("v").toFloat())
                "s" -> putString(key, value.optString("v"))
            }
        }
    }

    companion object {
        private val SYNCED_KEYS = listOf(
            R.string.videoprime_normalize_volume_key,
            R.string.videoprime_auto_radio_key,
            R.string.auto_queue_key,
            R.string.autoplay_key,
            R.string.seek_duration_key,
            R.string.use_inexact_seek_key,
            R.string.minimize_on_exit_key,
            R.string.default_resolution_key,
            R.string.default_popup_resolution_key,
            R.string.show_higher_resolutions_key,
            R.string.default_video_format_key,
            R.string.default_audio_format_key,
            R.string.show_comments_key,
            R.string.show_next_video_key,
            R.string.show_description_key,
            R.string.content_language_key,
            R.string.content_country_key,
            R.string.enable_watch_history_key,
            R.string.enable_playback_resume_key,
            R.string.enable_playback_state_lists_key,
            R.string.playback_speed_key,
            R.string.playback_pitch_key,
            R.string.playback_skip_silence_key,
            R.string.image_quality_key,
            R.string.theme_key
        )
    }
}

/** Sync id ↔ local id of the user's playlists, kept in the sync's own preferences. */
internal class SyncIds(private val prefs: SharedPreferences) {
    private val map: MutableMap<String, Long> = JSONObject(prefs.getString(KEY, "{}") ?: "{}").let { json ->
        json.keys().asSequence().associateWith { json.getLong(it) }.toMutableMap()
    }

    @Synchronized
    fun syncIdOf(localId: Long): String = map.entries.firstOrNull { it.value == localId }?.key
        ?: UUID.randomUUID().toString().also { link(it, localId) }

    @Synchronized
    fun localIdOf(syncId: String): Long? = map[syncId]

    @Synchronized
    fun link(syncId: String, localId: Long) {
        map.entries.removeAll { it.value == localId }
        map[syncId] = localId
        save()
    }

    @Synchronized
    fun forget(syncId: String) {
        if (map.remove(syncId) != null) save()
    }

    private fun save() = prefs.edit { putString(KEY, JSONObject(map as Map<*, *>).toString()) }

    companion object {
        private const val KEY = "playlist_ids"
    }
}
