package org.schabi.newpipe.videoprime.sync

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import androidx.room.InvalidationTracker
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.videoprime.InstantPush
import org.schabi.newpipe.videoprime.QueueStore

/**
 * PulsePlay: keeps the user's phone, tablet and TV the same: subscriptions with their bells,
 * history with the playback position, playlists, saved queues and the main settings.
 *
 * The devices form a group on our server (see [SyncApi]); a new one joins with a code shown on
 * one already in it. Each change made here is found by comparing what this device has with what
 * it last synced ([SyncSection.snapshot] against hashes kept in files), uploaded a few seconds
 * later, and the server pings the other devices, which fetch it at once. Changes from the others
 * are applied unless this device changed the same thing and has not uploaded it yet (then this
 * device's version is uploaded and wins). Everything runs on one background thread.
 */
object SyncManager {
    private const val TAG = "PulsePlaySync"
    private const val PREFS = "pulseplay_sync"
    private const val KEY_TOKEN = "token"
    private const val KEY_GROUP = "group"
    private const val KEY_DEVICE = "device"
    private const val KEY_TOPIC = "topic"
    private const val KEY_VERSION = "version"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_LAST_ERROR = "last_error"

    /** A change waits this long for the ones that usually come with it (the position while playing). */
    private const val CHANGE_DELAY_SECONDS = 10L
    private const val PUSH_BATCH = 200
    private const val PUSH_BATCH_CHARS = 1_000_000

    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "PulsePlaySync").apply { isDaemon = true }
    }

    @Volatile
    private var scheduled: ScheduledFuture<*>? = null

    @SuppressLint("StaticFieldLeak") // the application context
    @Volatile
    private var app: Context? = null
    private var sections: List<SyncSection> = emptyList()
    private var topicFollowed = false
    private var watching = false

    private fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @JvmStatic
    fun isAvailable(): Boolean = SyncApi.isConfigured()

    @JvmStatic
    fun isLinked(context: Context): Boolean = prefs(context).getString(KEY_TOKEN, null) != null

    @JvmStatic
    fun lastSync(context: Context): Long = prefs(context).getLong(KEY_LAST_SYNC, 0)

    @JvmStatic
    fun lastError(context: Context): String? = prefs(context).getString(KEY_LAST_ERROR, null)

    /** The app started: when linked, watch for changes and catch up with the other devices. */
    @JvmStatic
    fun start(context: Context) {
        app = context.applicationContext
        if (!isLinked(context)) return
        executor.execute {
            watchChanges()
            followTopic()
        }
        syncSoon(0)
    }

    // region linking

    /** This device starts a new group (its data is uploaded as the group's first). */
    @WorkerThread
    fun createGroup(context: Context) = link(context, SyncApi.create(deviceName()))

    /** This device joins the group of the device that showed [code]. */
    @WorkerThread
    fun joinGroup(context: Context, code: String) = link(context, SyncApi.join(code, deviceName()))

    @WorkerThread
    fun newCode(context: Context): Pair<String, Long> = SyncApi.code(token(context) ?: error("Not linked"))

    @WorkerThread
    fun devices(context: Context): List<SyncApi.Device> = SyncApi.devices(token(context) ?: error("Not linked"))

    /** This device leaves its group: its data stays as it is, it just stops syncing. */
    @WorkerThread
    fun leave(context: Context) {
        token(context)?.let { token -> runCatching { SyncApi.leave(token) } }
        forgetGroup(context)
    }

    private fun link(context: Context, linked: SyncApi.Linked) {
        forgetGroup(context)
        prefs(context).edit(commit = true) {
            putString(KEY_TOKEN, linked.token)
            putString(KEY_GROUP, linked.group)
            putString(KEY_DEVICE, linked.device)
            putString(KEY_TOPIC, linked.topic)
            putLong(KEY_VERSION, 0)
        }
        start(context)
    }

    private fun forgetGroup(context: Context) {
        val prefs = prefs(context)
        val topic = prefs.getString(KEY_TOPIC, null)
        if (topic != null && InstantPush.isConfigured()) {
            runCatching {
                InstantPush.initialize(context)
                Tasks.await(FirebaseMessaging.getInstance().unsubscribeFromTopic(topic), 20, TimeUnit.SECONDS)
            }
        }
        prefs.edit(commit = true) { clear() }
        stateDir(context).deleteRecursively()
        topicFollowed = false
    }

    private fun deviceName(): String = listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL)
        .filter { it.isNotBlank() }.distinct().joinToString(" ")
    // endregion

    // region when to sync

    /** A push from the server: another device changed something. */
    @JvmStatic
    fun onPush(context: Context, data: Map<String, String>) {
        app = context.applicationContext
        if (!isLinked(context) || data["d"] == prefs(context).getString(KEY_DEVICE, null)) return
        syncSoon(0)
    }

    /** Something changed here: upload it soon (several changes together). */
    @JvmStatic
    fun changed() {
        if (app?.let(::isLinked) == true) syncSoon(CHANGE_DELAY_SECONDS)
    }

    @JvmStatic
    fun syncNow(context: Context) {
        app = context.applicationContext
        syncSoon(0)
    }

    @Synchronized
    private fun syncSoon(delaySeconds: Long) {
        val current = scheduled
        // an earlier sync already on its way is not pushed back by every new change
        if (current != null && !current.isDone && current.getDelay(TimeUnit.SECONDS) <= delaySeconds) return
        current?.cancel(false)
        scheduled = executor.schedule({ runSync() }, delaySeconds, TimeUnit.SECONDS)
    }

    private val tables = arrayOf(
        // not "streams": the feed writes it all the time; history and playlists have their own
        "subscriptions",
        "stream_history",
        "stream_state",
        "playlists",
        "playlist_stream_join",
        "remote_playlists"
    )

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val section = sections.filterIsInstance<PreferenceSection>().firstOrNull()
        if (key != null && section != null && key in section.keys) changed()
    }

    private fun watchChanges() {
        val context = app ?: return
        if (watching) return
        watching = true
        sections = sections(context)
        NewPipeDatabase.getInstance(context).invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(tables) {
                override fun onInvalidated(tables: Set<String>) = changed()
            }
        )
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .registerOnSharedPreferenceChangeListener(preferenceListener)
        QueueStore.onSaved = { changed() }
    }

    private fun sections(context: Context): List<SyncSection> {
        val database = NewPipeDatabase.getInstance(context)
        return listOf(
            SubscriptionSection(database),
            HistorySection(database),
            PlaylistSection(database, SyncIds(prefs(context))),
            RemotePlaylistSection(database),
            QueueSection(QueueStore(context)),
            PreferenceSection(context)
        )
    }

    private fun followTopic() {
        val context = app ?: return
        if (topicFollowed || !InstantPush.isConfigured()) return
        val topic = prefs(context).getString(KEY_TOPIC, null) ?: return
        try {
            InstantPush.initialize(context)
            Tasks.await(FirebaseMessaging.getInstance().subscribeToTopic(topic), 20, TimeUnit.SECONDS)
            topicFollowed = true
        } catch (e: Exception) {
            Log.w(TAG, "Could not follow the group's pushes", e)
        }
    }

    /** Firebase gave this install a new token: the group's topic is followed again. */
    @JvmStatic
    fun onNewFirebaseToken() {
        topicFollowed = false
        if (app?.let(::isLinked) == true) executor.execute { followTopic() }
    }
    // endregion

    // region the sync itself
    private fun runSync() {
        val context = app ?: return
        val token = token(context) ?: return
        try {
            watchChanges()
            followTopic()
            pull(context, token)
            push(context, token)
            prefs(context).edit {
                putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                remove(KEY_LAST_ERROR)
            }
        } catch (e: SyncApi.HttpException) {
            Log.w(TAG, "Sync failed", e)
            if (e.code == 401) {
                // removed from the group (from another device): this one stops syncing
                forgetGroup(context)
            } else {
                failed(context, e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed", e)
            failed(context, e)
        }
    }

    private fun failed(context: Context, e: Exception) {
        prefs(context).edit { putString(KEY_LAST_ERROR, e.message ?: e.javaClass.simpleName) }
        // tried again later (another change or the app opening also start a sync)
        syncSoon(RETRY_SECONDS)
    }

    private const val RETRY_SECONDS = 5 * 60L

    private fun pull(context: Context, token: String) {
        val prefs = prefs(context)
        val me = prefs.getString(KEY_DEVICE, null)
        var since = prefs.getLong(KEY_VERSION, 0)
        // just joined: what the group has comes in (this device's own things are added to it
        // afterwards, not lost), but not what the others deleted long ago
        val joining = since == 0L
        val snapshots = HashMap<String, Map<String, JSONObject>>()
        do {
            val page = SyncApi.pull(token, since)
            val others = page.items.filter { it.device != me }
            for ((kind, items) in others.groupBy { it.kind }) {
                val section = sections.firstOrNull { it.kind == kind } ?: continue
                val synced = SyncedState(stateFile(context, kind))
                val now = snapshots.getOrPut(kind) { section.snapshot() }
                // the last change of each key in the page; what this device changed and has not
                // uploaded yet is kept (it goes up next and wins)
                val changes = items.associateBy({ it.key }, { it.value })
                    .filter { (key, value) ->
                        if (joining) value != null else hash(now[key]) == synced[key]
                    }
                if (changes.isNotEmpty()) {
                    section.apply(changes)
                    val after = section.snapshot()
                    snapshots[kind] = after
                    for (key in changes.keys) synced[key] = hash(after[key])
                    synced.save()
                }
            }
            since = page.version
            prefs.edit(commit = true) { putLong(KEY_VERSION, since) }
        } while (page.more)
    }

    private fun push(context: Context, token: String) {
        val pending = ArrayList<Pair<SyncedState, Triple<String, String, JSONObject?>>>()
        for (section in sections) {
            val synced = SyncedState(stateFile(context, section.kind))
            val now = section.snapshot()
            for ((key, value) in now) {
                if (synced[key] != hash(value)) pending += synced to Triple(section.kind, key, value)
            }
            for (key in synced.keys() - now.keys) pending += synced to Triple(section.kind, key, null)
        }
        var start = 0
        while (start < pending.size) {
            var end = start
            var chars = 0
            while (end < pending.size && end - start < PUSH_BATCH) {
                chars += pending[end].second.third?.toString()?.length ?: 0
                if (chars > PUSH_BATCH_CHARS && end > start) break
                end++
            }
            val batch = pending.subList(start, end)
            SyncApi.push(token, batch.map { it.second }, ping = end == pending.size)
            for ((synced, item) in batch) synced[item.second] = hash(item.third)
            batch.map { it.first }.distinct().forEach { it.save() }
            start = end
        }
        if (pending.isNotEmpty()) Log.i(TAG, "Uploaded ${pending.size} changes")
    }

    private fun token(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    private fun stateDir(context: Context) = File(context.applicationContext.filesDir, "pulseplay_sync")

    private fun stateFile(context: Context, kind: String) = File(stateDir(context), "$kind.json")

    private fun hash(value: JSONObject?): String? = value?.let {
        val digest = MessageDigest.getInstance("SHA-1").digest(it.toString().toByteArray())
        digest.take(10).joinToString("") { b -> "%02x".format(b) }
    }

    /** What this device last synced of one kind: key → hash of the value (null: deleted). */
    private class SyncedState(private val file: File) {
        private val map: MutableMap<String, String> = if (file.exists()) {
            runCatching {
                val json = JSONObject(file.readText())
                json.keys().asSequence().associateWith { json.getString(it) }.toMutableMap()
            }.getOrDefault(mutableMapOf())
        } else {
            mutableMapOf()
        }

        operator fun get(key: String): String? = map[key]

        operator fun set(key: String, hash: String?) {
            if (hash == null) map.remove(key) else map[key] = hash
        }

        fun keys(): Set<String> = map.keys.toSet()

        fun save() {
            file.parentFile?.mkdirs()
            org.schabi.newpipe.videoprime.writeAtomically(file, JSONObject(map as Map<*, *>).toString())
        }
    }
    // endregion
}
