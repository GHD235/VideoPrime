package org.schabi.newpipe.videoprime

import android.content.Context
import android.content.Intent
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.NavigationHelper

/**
 * VideoPrime: playlists are listened to like music: they play in the background player and the
 * music player opens on top, without a video screen in between.
 */
object MusicPlayback {
    @JvmStatic
    fun playAndOpen(context: Context, queue: PlayQueue) {
        NavigationHelper.playOnBackgroundPlayer(context, queue, false)
        context.startActivity(Intent(context, MusicPlayerActivity::class.java))
    }
}
