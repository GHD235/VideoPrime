package org.schabi.newpipe.videoprime

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.NavigationHelper

/**
 * VideoPrime: switching between the user's saved queues, shared by the queues screen and the
 * music player. The queue that was playing is saved (item and position) before playing another.
 */
object QueueSwitcher {
    /** The queue that is playing and the position in its current item, or null. */
    fun playing(): Pair<PlayQueue, Long>? {
        val player = PlayerService.getCurrentPlayer() ?: return null
        val queue = player.playQueue ?: return null
        val position = if (player.exoPlayerIsNull()) 0L else player.exoPlayer.currentPosition
        return queue to position
    }

    /** Saves the playing queue into the active saved queue, so that it can be resumed. */
    fun rememberActive(content: QueueStore.Content): QueueStore.Content {
        val activeId = content.activeId ?: return content
        val (current, position) = playing() ?: return content
        val active = content.queues.firstOrNull { it.id == activeId } ?: return content
        val updated = SavedQueue.from(active.id, active.name, current, position)
        return content.copy(queues = content.queues.map { if (it.id == activeId) updated else it })
    }

    /** Plays a saved queue where it was left, in the kind of player that is open. */
    fun switchTo(activity: Activity, store: QueueStore, queue: SavedQueue) {
        if (queue.items.isEmpty()) return
        val content = rememberActive(store.load())
        val target = content.queues.firstOrNull { it.id == queue.id } ?: queue
        store.save(content.copy(activeId = target.id))

        val playQueue = target.toPlayQueue()
        when (PlayerService.getCurrentPlayer()?.playerType ?: PlayerHolder.getInstance().type) {
            PlayerType.MAIN -> (activity as? AppCompatActivity)?.let {
                NavigationHelper.playOnMainPlayer(it, playQueue)
            } ?: NavigationHelper.playOnBackgroundPlayer(activity, playQueue, false)

            PlayerType.POPUP -> NavigationHelper.playOnPopupPlayer(activity, playQueue, false)

            else -> NavigationHelper.playOnBackgroundPlayer(activity, playQueue, false)
        }
    }

    /**
     * Saves the playing queue as a new saved queue, which becomes the active one.
     *
     * @return the new queue's name, or null if nothing is playing
     */
    fun saveCurrent(activity: Activity, store: QueueStore): String? {
        val (current, position) = playing() ?: return null
        val content = rememberActive(store.load())
        val id = System.currentTimeMillis()
        val name = activity.getString(R.string.queues_default_name, content.queues.size + 1)
        val queue = SavedQueue.from(id, name, current, position)
        store.save(QueueStore.Content(content.queues + queue, id))
        return name
    }
}
