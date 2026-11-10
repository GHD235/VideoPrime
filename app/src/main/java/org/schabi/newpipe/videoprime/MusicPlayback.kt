package org.schabi.newpipe.videoprime

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.schabi.newpipe.player.PlayerService
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

    private const val SHUFFLE_TRIES = 25
    private const val SHUFFLE_WAIT_MILLIS = 200L

    /**
     * PulsePlay: plays the queue shuffled (the "Shuffle" button of a playlist): it starts on a
     * song picked at random and, once the player has taken the queue, its own shuffle is turned
     * on, so the shuffle button shows it and turning it off gives the playlist's order back.
     */
    @JvmStatic
    fun playShuffled(context: Context, queue: PlayQueue) {
        if (queue.size() > 1) queue.index = (0 until queue.size()).random()
        playAndOpen(context, queue)
        val handler = Handler(Looper.getMainLooper())
        var tries = 0
        handler.postDelayed(
            object : Runnable {
                override fun run() {
                    val player = PlayerService.getCurrentPlayer()
                    val playing = player?.playQueue
                    if (player != null && playing != null && !player.exoPlayerIsNull() &&
                        playing.size() > 0 && playing.item?.url == queue.item?.url
                    ) {
                        if (!playing.isShuffled) player.toggleShuffleModeEnabled()
                    } else if (++tries < SHUFFLE_TRIES) {
                        handler.postDelayed(this, SHUFFLE_WAIT_MILLIS)
                    }
                }
            },
            SHUFFLE_WAIT_MILLIS
        )
    }
}
