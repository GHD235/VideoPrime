package org.schabi.newpipe.videoprime

import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerService

/** VideoPrime: pauses the player after some minutes, to fall asleep listening to music. */
object SleepTimer {
    private val handler = Handler(Looper.getMainLooper())
    private var player = WeakReference<Player>(null)

    /** When the player will be paused, in [System.currentTimeMillis] time, or 0 if never. */
    var endsAt: Long = 0
        private set

    private val pause = Runnable {
        endsAt = 0
        (player.get() ?: PlayerService.getCurrentPlayer())?.pause()
    }

    /** The player to pause, known while the music player screen is open. */
    fun attach(player: Player?) {
        if (player != null) this.player = WeakReference(player)
    }

    fun start(minutes: Int) {
        cancel()
        endsAt = System.currentTimeMillis() + minutes * 60_000L
        handler.postDelayed(pause, minutes * 60_000L)
    }

    fun cancel() {
        handler.removeCallbacks(pause)
        endsAt = 0
    }
}
