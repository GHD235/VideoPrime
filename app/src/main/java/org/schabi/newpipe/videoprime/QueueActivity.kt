package org.schabi.newpipe.videoprime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player.REPEAT_MODE_ALL
import com.google.android.exoplayer2.Player.REPEAT_MODE_ONE
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.player.PlayQueueActivity
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.event.PlayerEventListener
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * VideoPrime: the queue ("Up next") with the neon glass look: what plays in a glowing card, the
 * next songs below (drag to reorder, swipe to remove) and the controls at the bottom. NewPipe's
 * queue editor stays one menu tap away ("Classic view").
 */
class QueueActivity : AppCompatActivity(), PlayerEventListener {
    private var player: Player? = null
    private var bound = false
    private var queueEvents: Disposable? = null
    private var watchedQueue: PlayQueue? = null
    private var state by mutableStateOf(QueueScreenState())
    private lateinit var store: QueueStore

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val playerService = (service as? PlayerService.LocalBinder)?.service ?: run {
                finish()
                return
            }
            attach(playerService, 0)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            player = null
        }
    }

    private fun attach(playerService: PlayerService, attempt: Int) {
        val player = playerService.player
        if (player == null || player.playQueue == null) {
            if (attempt < ATTACH_ATTEMPTS && !isFinishing) {
                window.decorView.postDelayed({ attach(playerService, attempt + 1) }, ATTACH_DELAY_MILLIS)
            } else {
                finish()
            }
            return
        }
        this.player = player
        player.setActivityListener(this)
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.setTheme(this, ServiceHelper.getSelectedServiceId(this))
        store = QueueStore(this)
        // the neon look needs the dark ground, whatever the app's theme
        window.statusBarColor = VideoPrimeColors.DarkBackground.toArgb()
        window.navigationBarColor = VideoPrimeColors.DarkBackground.toArgb()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val actions = QueueScreenActions(
            onClose = ::finish,
            onPlayPause = {
                player?.let {
                    it.playPause()
                    state = state.copy(playing = it.playWhenReady)
                }
            },
            onPrevious = { player?.playPrevious() },
            onNext = { player?.playNext() },
            onShuffle = { player?.toggleShuffleModeEnabled() },
            onRepeat = { player?.cycleNextRepeatMode() },
            onSaveQueue = {
                QueueSwitcher.saveCurrent(this, store)?.let { name ->
                    Toast.makeText(this, getString(R.string.queues_saved, name), Toast.LENGTH_SHORT)
                        .show()
                }
                refresh()
            },
            onSelect = { index ->
                player?.let { p ->
                    p.playQueue?.getItem(index)?.let { p.selectQueueItem(it) }
                }
            },
            onRemove = { index -> player?.playQueue?.remove(index) },
            onMove = { from, to ->
                val queue = player?.playQueue
                if (queue != null && from != to && from in 0 until queue.size() && to in 0 until queue.size()) {
                    queue.move(from, to)
                }
            },
            onPlayNext = { index ->
                val queue = player?.playQueue
                if (queue != null && index > queue.index + 1) {
                    queue.move(index, queue.index + 1)
                }
            },
            onAddToPlaylist = {
                player?.let { PlaylistDialog.showForPlayQueue(it, supportFragmentManager) }
            },
            onClassicView = { startActivity(Intent(this, PlayQueueActivity::class.java)) }
        )
        setContentView(
            ComposeView(this).apply {
                setContent {
                    VideoPrimeTheme(dark = true) {
                        QueueScreen(state, actions)
                    }
                }
            }
        )
        bound = bindService(
            Intent(this, PlayerService::class.java).setAction(PlayerService.BIND_PLAYER_HOLDER_ACTION),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onResume() {
        super.onResume()
        // the player has room for one screen only: take it back when returning from another one
        player?.let {
            it.setActivityListener(this)
            refresh()
        }
    }

    override fun onDestroy() {
        queueEvents?.dispose()
        player?.removeActivityListener(this)
        if (bound) unbindService(connection)
        super.onDestroy()
    }

    /** Follows the queue's own edits (moves, removals, shuffle), which the player does not report. */
    private fun watch(queue: PlayQueue?) {
        if (queue === watchedQueue) return
        queueEvents?.dispose()
        watchedQueue = queue
        queueEvents = queue?.broadcastReceiver
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe({ refresh() }, { })
    }

    private fun refresh() {
        val player = player ?: return
        val queue = player.playQueue ?: return
        watch(queue)
        val content = store.load()
        state = state.copy(
            entries = queue.streams.map { item ->
                QueueEntry(
                    key = System.identityHashCode(item),
                    title = item.title,
                    artist = item.uploader.removeSuffix(" - Topic"),
                    durationSeconds = item.duration,
                    thumbnailUrl = ImageStrategy.choosePreferredImage(item.thumbnails)
                )
            },
            index = queue.index,
            shuffled = queue.isShuffled,
            source = content.queues.firstOrNull { it.id == content.activeId }
                ?.takeIf { QueueSwitcher.isSameQueue(it, queue) }?.name,
            playing = player.playWhenReady
        )
        updateCoverColor()
    }

    private var coverBitmap: Bitmap? = null

    private fun updateCoverColor() {
        val bitmap = player?.thumbnail
        if (bitmap === coverBitmap) return
        coverBitmap = bitmap
        state = state.copy(coverColor = CoverArt.mainColor(bitmap))
    }

    override fun onQueueUpdate(queue: PlayQueue?) = refresh()

    override fun onPlaybackUpdate(
        state: Int,
        repeatMode: Int,
        shuffled: Boolean,
        parameters: PlaybackParameters?
    ) {
        this.state = this.state.copy(
            playing = player?.let { it.playWhenReady && state != Player.STATE_COMPLETED }
                ?: (state == Player.STATE_PLAYING || state == Player.STATE_BUFFERING),
            shuffled = shuffled,
            repeat = when (repeatMode) {
                REPEAT_MODE_ONE -> RepeatMode.ONE
                REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            }
        )
    }

    override fun onProgressUpdate(currentProgress: Int, duration: Int, bufferPercent: Int) {
        updateCoverColor()
        state = state.copy(
            positionMillis = currentProgress.toLong(),
            durationMillis = duration.toLong(),
            playing = player?.playWhenReady ?: state.playing
        )
    }

    override fun onMetadataUpdate(info: StreamInfo?, queue: PlayQueue?) = refresh()

    override fun onServiceStopped() {
        finish()
    }

    companion object {
        private const val ATTACH_ATTEMPTS = 25
        private const val ATTACH_DELAY_MILLIS = 200L
    }
}
