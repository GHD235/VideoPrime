package org.schabi.newpipe.videoprime

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.local.playlist.LocalPlaylistManager

/**
 * PulsePlay: the heart of the music player. The favourites are an ordinary playlist of the
 * library (so they play, sync, go offline and show in the car like any other); it is made the
 * first time a song is marked and found again by its id (or made anew if the user deleted it).
 */
object Favorites {
    private const val PREF_PLAYLIST = "pulseplay_favorites_playlist"

    private fun playlistId(context: Context): Long {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val dao = NewPipeDatabase.getInstance(context).playlistDAO()
        val id = prefs.getLong(PREF_PLAYLIST, -1L)
        if (id >= 0 && dao.exists(id)) return id
        // made on another device and brought by the sync, or this device's data was restored:
        // the list with the favourites' name is the one
        val byName = dao.newestIdNamed(context.getString(R.string.pulseplay_favorites)) ?: return -1L
        prefs.edit { putLong(PREF_PLAYLIST, byName) }
        return byName
    }

    @WorkerThread
    @JvmStatic
    fun isFavorite(context: Context, url: String): Boolean {
        val id = playlistId(context)
        return id >= 0 && NewPipeDatabase.getInstance(context).playlistStreamDAO().countOf(id, url) > 0
    }

    /** Marks or unmarks the song. Returns whether it is a favourite now. */
    @WorkerThread
    @JvmStatic
    @Synchronized
    fun toggle(context: Context, stream: StreamEntity): Boolean {
        val app = context.applicationContext
        val database = NewPipeDatabase.getInstance(app)
        val id = playlistId(app)
        if (id >= 0 && database.playlistStreamDAO().countOf(id, stream.url) > 0) {
            database.playlistStreamDAO().removeStream(id, stream.url)
            return false
        }
        val manager = LocalPlaylistManager(database)
        if (id >= 0) {
            manager.appendToPlaylist(id, listOf(stream)).blockingGet()
        } else {
            val name = app.getString(R.string.pulseplay_favorites)
            manager.createPlaylist(name, listOf(stream)).blockingGet()
            // createPlaylist answers with the rows of the songs: the list is found by its name
            val newId = database.playlistDAO().newestIdNamed(name)
            if (newId != null) {
                PreferenceManager.getDefaultSharedPreferences(app).edit { putLong(PREF_PLAYLIST, newId) }
            }
        }
        return true
    }
}
