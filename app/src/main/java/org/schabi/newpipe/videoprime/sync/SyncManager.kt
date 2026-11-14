package org.schabi.newpipe.videoprime.sync

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import androidx.room.InvalidationTracker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
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
    private const val CHANGE_DELAY_SECONDS = 4L
    private const val PUSH_BATCH = 200
    private const val PUSH_BATCH_CHARS = 1_000_000 // the server takes 2 MB per upload

    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "PulsePlaySync").apply { isDaemon = true }
    }

    @Volatile
    private var scheduled: ScheduledFuture<*>? = null

    @SuppressLint("StaticFieldLeak") // the application context
    @Volatile
    private var app: Context? = null

    @Volatile
    private var sections: List<SyncSection> = emptyList()

    @Volatile
    private var topicFollowed = false

    @Volatile
    private var watching = false

    /** A sync is running; another one asked for meanwhile runs right after it. */
    @Volatile
    private var running = false

    @Volatile
    private var rerunDelay: Long? = null

    /**
     * The kinds that may have changed here since the last upload (null: any, as when the app
     * starts). Only those are read and compared: a sync started by another device's change, or
     * by the place of a video saved every few seconds, went through everything each time (the
     * whole history, every list...) and Android closed the app in the background for using too
     * much processor.
     */
    private val dirtyKinds = HashSet<String>()

    /** Everything is compared at the next upload (not known to be clean). */
    private var dirtyAll = true
    private var cleanChecked = false
    private const val KEY_CLEAN = "clean"
    private const val KEY_LAST_FULL = "last_full"
    private const val FULL_EVERY_MILLIS = 24 * 60 * 60 * 1000L

    @Synchronized
    private fun markDirty(vararg kinds: String) {
        dirtyKinds.addAll(kinds)
        // remembered on disk: Android closes the app in the background and a push starts it
        // again; without this every such start compared everything
        val context = app ?: return
        if (prefs(context).getBoolean(KEY_CLEAN, false)) {
            prefs(context).edit(commit = true) { putBoolean(KEY_CLEAN, false) }
        }
    }

    /** The kinds to compare now (null: all of them). */
    @Synchronized
    private fun takeDirty(context: Context): Set<String>? {
        if (!cleanChecked) {
            cleanChecked = true
            val prefs = prefs(context)
            // all of it once a day too: names and pictures of videos change without a sign
            val fullDue = System.currentTimeMillis() - prefs.getLong(KEY_LAST_FULL, 0) > FULL_EVERY_MILLIS
            if (prefs.getBoolean(KEY_CLEAN, false) && !fullDue) dirtyAll = false
        }
        val taken = if (dirtyAll) null else HashSet(dirtyKinds)
        dirtyAll = false
        dirtyKinds.clear()
        return taken
    }

    @Synchronized
    private fun giveBackDirty(kinds: Set<String>?) {
        if (kinds == null) dirtyAll = true else dirtyKinds.addAll(kinds)
    }

    /** An upload went well: if nothing changed meanwhile, the next start need not compare all. */
    @Synchronized
    private fun uploadedDirty(context: Context, kinds: Set<String>?) {
        prefs(context).edit {
            if (kinds == null) putLong(KEY_LAST_FULL, System.currentTimeMillis())
            if (!dirtyAll && dirtyKinds.isEmpty()) putBoolean(KEY_CLEAN, true)
        }
    }

    /** Until when an upload of only a video's place does not wake the other devices. */
    @Volatile
    private var quietUntil = 0L
    private const val QUIET_MILLIS = 20_000L

    /**
     * The place of the video that is playing, saved as it plays: it goes up (a device opened
     * later finds it) but the other devices are not woken for it every half a minute.
     */
    @JvmStatic
    fun quietChange() {
        quietUntil = android.os.SystemClock.elapsedRealtime() + QUIET_MILLIS
    }

    /** A change the other devices should hear of now (pause, closing the player). */
    @JvmStatic
    fun loudChange() {
        quietUntil = 0
    }

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
        // the playlists' sync ids stay: joining again must not make every playlist a new one
        prefs.edit(commit = true) {
            for (key in listOf(KEY_TOKEN, KEY_GROUP, KEY_DEVICE, KEY_TOPIC, KEY_VERSION, KEY_LAST_SYNC, KEY_LAST_ERROR, KEY_CLEAN, KEY_LAST_FULL)) {
                remove(key)
            }
        }
        stateDir(context).deleteRecursively()
        giveBackDirty(null)
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
        // waited for (on Firebase's thread, which Android keeps awake meanwhile): with the app
        // in the background a sync left for later was frozen half way and the change only
        // came in when the app was opened
        try {
            executor.submit { runSync() }.get(PUSH_WAIT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "Sync after a push not finished in time", e)
        }
    }

    private const val PUSH_WAIT_SECONDS = 15L

    /** Something changed here: upload it soon (several changes together). */
    @JvmStatic
    fun changed() {
        if (app?.let(::isLinked) == true) syncSoon(CHANGE_DELAY_SECONDS)
    }

    /** Something of [kind] changed here that no database table or setting tells of. */
    @JvmStatic
    fun changed(kind: String) {
        if (app?.let(::isLinked) != true) return
        markDirty(kind)
        changed()
    }

    @JvmStatic
    fun syncNow(context: Context) {
        app = context.applicationContext
        syncSoon(0)
    }

    private const val FLUSH_WORK = "pulseplay_sync_flush"
    private const val FLUSH_WAIT_SECONDS = 90L

    /**
     * Something that must reach the other devices even if the app is closing right now (where a
     * video was left when its player closes): uploaded by a job of its own, which Android keeps
     * alive or runs as soon as it can.
     */
    @JvmStatic
    fun flush(context: Context) {
        val appContext = context.applicationContext
        app = appContext
        if (!isLinked(appContext)) return
        try {
            val builder = OneTimeWorkRequestBuilder<FlushWorker>()
            // on Android 11 and older an expedited work shows a notification each time
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            val request = builder
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            loudChange()
            // right now while the app is still alive, and the job in case it is not for long
            syncSoon(0)
            WorkManager.getInstance(appContext)
                .enqueueUniqueWork(FLUSH_WORK, ExistingWorkPolicy.REPLACE, request)
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule the upload", e)
            syncSoon(0)
        }
    }

    class FlushWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            app = applicationContext
            if (!isLinked(applicationContext)) return Result.success()
            return try {
                // on the sync thread, as every other sync: never two at once
                executor.submit { runSync() }.get(FLUSH_WAIT_SECONDS, TimeUnit.SECONDS)
                Result.success()
            } catch (e: Exception) {
                Log.w(TAG, "Upload before closing failed", e)
                Result.success()
            }
        }
    }

    @Synchronized
    private fun syncSoon(delaySeconds: Long) {
        if (running) {
            // asked while syncing (a change, a ping, a retry): it may not be in this sync
            rerunDelay = minOf(rerunDelay ?: delaySeconds, delaySeconds)
            return
        }
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
        if (key != null && section != null && key in section.keys) {
            markDirty(section.kind)
            changed()
        }
    }

    private fun watchChanges() {
        val context = app ?: return
        if (watching) return
        watching = true
        sections = sections(context)
        NewPipeDatabase.getInstance(context).invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(tables) {
                override fun onInvalidated(tables: Set<String>) {
                    for (table in tables) {
                        when (table) {
                            "subscriptions" -> markDirty("sub")
                            "stream_history", "stream_state" -> markDirty("hist")
                            "playlists", "playlist_stream_join" -> markDirty("plist")
                            "remote_playlists" -> markDirty("rplist")
                            else -> markDirty("sub", "hist", "plist", "rplist")
                        }
                    }
                    changed()
                }
            }
        )
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .registerOnSharedPreferenceChangeListener(preferenceListener)
        QueueStore.onSaved = {
            markDirty("queue")
            changed()
        }
    }

    private fun sections(context: Context): List<SyncSection> {
        val database = NewPipeDatabase.getInstance(context)
        return listOf(
            SubscriptionSection(database),
            HistorySection(database),
            PlaylistSection(database, SyncIds(prefs(context))),
            RemotePlaylistSection(database),
            QueueSection(QueueStore(context)),
            PreferenceSection(context),
            VisitSection(context)
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
        running = true
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
        } catch (e: TooBigException) {
            prefs(context).edit { putString(KEY_LAST_ERROR, e.message) }
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed", e)
            failed(context, e)
        } finally {
            synchronized(this) {
                running = false
                rerunDelay?.let {
                    rerunDelay = null
                    syncSoon(it)
                }
            }
        }
    }

    private fun failed(context: Context, e: Exception) {
        prefs(context).edit { putString(KEY_LAST_ERROR, e.message ?: e.javaClass.simpleName) }
        // tried again later (another change or the app opening also start a sync)
        syncSoon(RETRY_SECONDS)
    }

    private const val RETRY_SECONDS = 5 * 60L

    /** The server's limits (see push-server/src/lib.js), a little below them. */
    private const val MAX_KEY = 500
    private const val MAX_VALUE = 950_000

    /** Something could not be uploaded (a list with thousands of songs): said in Ajustes, no retry. */
    private class TooBigException : Exception("A list is too big to sync")

    private fun pull(context: Context, token: String) {
        val prefs = prefs(context)
        val me = prefs.getString(KEY_DEVICE, null)
        var since = prefs.getLong(KEY_VERSION, 0)
        // just joined (nothing synced yet): what the group has comes in (this device's own things
        // are added to it afterwards, not lost), but not what the others deleted long ago
        val joining = stateDir(context).list().isNullOrEmpty()
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
                val marks = SyncedState(markFile(context, kind))
                val changes = HashMap<String, JSONObject?>()
                // what came in, when it is not exactly what is kept: the mix goes up afterwards
                val incoming = HashMap<String, JSONObject>()
                for ((key, value) in items.associateBy({ it.key }, { it.value })) {
                    val local = now[key]
                    if (joining) {
                        if (value != null) changes[key] = value
                    } else if (hash(local) == synced[key]) {
                        changes[key] = value
                    } else if (local != null && value != null) {
                        // both changed it: the section may know how to keep both changes
                        val merged = section.merge(local, value, marks[key]) ?: continue
                        changes[key] = merged
                        incoming[key] = value
                        markDirty(kind)
                    }
                }
                if (changes.isNotEmpty()) {
                    section.apply(changes)
                    val after = section.snapshot()
                    snapshots[kind] = after
                    for ((key, value) in changes) {
                        synced[key] = hash(incoming[key] ?: after[key])
                        marks[key] = section.mark(incoming[key] ?: value)
                    }
                    synced.save()
                    if (section.mark(JSONObject()) != null) marks.save()
                }
            }
            since = page.version
            prefs.edit(commit = true) { putLong(KEY_VERSION, since) }
        } while (page.more)
        if (joining) {
            // joined: from now on, deletions from the other devices apply here too, even if
            // this device had nothing of its own to write down yet
            for (section in sections) {
                val file = stateFile(context, section.kind)
                if (!file.exists()) SyncedState(file).save()
            }
        }
    }

    private fun push(context: Context, token: String) {
        val pending = ArrayList<Pair<SyncedState, Triple<String, String, JSONObject?>>>()
        var tooBig = false
        val dirty = takeDirty(context)
        var uploaded = false
        try {
            tooBig = pushKinds(context, token, dirty, pending)
            uploaded = true
            uploadedDirty(context, dirty)
        } finally {
            // not uploaded (no network...): still to do next time
            if (!uploaded) giveBackDirty(dirty)
        }
        if (pending.isNotEmpty()) Log.i(TAG, "Uploaded ${pending.size} changes")
        if (tooBig) throw TooBigException()
    }

    /** @return whether something was too big to upload */
    private fun pushKinds(
        context: Context,
        token: String,
        dirty: Set<String>?,
        pending: ArrayList<Pair<SyncedState, Triple<String, String, JSONObject?>>>
    ): Boolean {
        var tooBig = false
        for (section in sections) {
            if (dirty != null && section.kind !in dirty) continue
            val synced = SyncedState(stateFile(context, section.kind))
            val now = section.snapshot()
            for ((key, value) in now) {
                if (synced[key] == hash(value)) continue
                if (key.length > MAX_KEY || value.toString().length > MAX_VALUE) {
                    // the server would refuse the whole upload: this one stays on this device
                    Log.w(TAG, "Too big to sync: ${section.kind} $key")
                    tooBig = true
                    continue
                }
                pending += synced to Triple(section.kind, key, value)
            }
            for (key in synced.keys() - now.keys) pending += synced to Triple(section.kind, key, null)
        }
        val pushMarks = HashMap<String, SyncedState>()
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
            // only where a video is, as it plays: the others are not woken for it
            val quiet = android.os.SystemClock.elapsedRealtime() < quietUntil &&
                pending.size <= 2 && pending.all { it.second.first == "hist" }
            SyncApi.push(token, batch.map { it.second }, ping = end == pending.size && !quiet)
            for ((synced, item) in batch) synced[item.second] = hash(item.third)
            batch.map { it.first }.distinct().forEach { it.save() }
            // and what decides the next conflict (see SyncSection.mark)
            for ((kind, items) in batch.map { it.second }.groupBy { it.first }) {
                val section = sections.firstOrNull { it.kind == kind } ?: continue
                if (section.mark(JSONObject()) == null) continue
                val marks = pushMarks.getOrPut(kind) { SyncedState(markFile(context, kind)) }
                for (item in items) marks[item.second] = section.mark(item.third)
                marks.save()
            }
            start = end
        }
        return tooBig
    }

    private fun token(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    private fun stateDir(context: Context) = File(context.applicationContext.filesDir, "pulseplay_sync")

    private fun stateFile(context: Context, kind: String) = File(stateDir(context), "$kind.json")

    private fun markFile(context: Context, kind: String) = File(stateDir(context), "$kind.marks.json")

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
