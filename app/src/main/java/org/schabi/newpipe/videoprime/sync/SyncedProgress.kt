package org.schabi.newpipe.videoprime.sync

import android.os.Handler
import android.os.Looper
import org.schabi.newpipe.player.PlayerService

/**
 * The place where a video was left on another device can arrive a moment after the video was
 * opened here (the player had already read the older place): the player is told, and goes there
 * if it has only just started that video.
 */
internal object SyncedProgress {
    private val main = Handler(Looper.getMainLooper())

    /** @param places "serviceId|url" → milliseconds, as just written to the database */
    fun arrived(places: Map<String, Long>) {
        if (places.isEmpty() || PlayerService.getCurrentPlayer() == null) return
        main.post {
            val player = PlayerService.getCurrentPlayer() ?: return@post
            val info = player.currentStreamInfo.orElse(null) ?: return@post
            val place = places[key(info.serviceId, info.url)] ?: return@post
            player.adoptSyncedProgress(place)
        }
    }
}
