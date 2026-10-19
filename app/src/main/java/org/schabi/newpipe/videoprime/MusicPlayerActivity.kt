package org.schabi.newpipe.videoprime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player.REPEAT_MODE_ALL
import com.google.android.exoplayer2.Player.REPEAT_MODE_ONE
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.PlayQueueActivity
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.event.PlayerEventListener
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * VideoPrime: full screen player for music and for anything playing in the background (downloads,
 * online playlists, videos as audio). The queue editor of NewPipe is one tap away.
 */
class MusicPlayerActivity : AppCompatActivity(), PlayerEventListener {
    private var player: Player? = null
    private var bound = false
    private var state by mutableStateOf(MusicPlayerState())
    private lateinit var store: QueueStore

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val player = (service as? PlayerService.LocalBinder)?.service?.player
            if (player == null || player.playQueue == null || player.exoPlayerIsNull()) {
                finish()
                return
            }
            this@MusicPlayerActivity.player = player
            SleepTimer.attach(player)
            player.setActivityListener(this@MusicPlayerActivity)
            onQueueUpdate(player.playQueue)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            player = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.setTheme(this, ServiceHelper.getSelectedServiceId(this))
        store = QueueStore(this)
        val dark = !ThemeHelper.isLightThemeSelected(this)
        val actions = MusicPlayerActions(
            onClose = ::finish,
            onPlayPause = {
                player?.playPause()
                // answer the tap at once; the player confirms it right after
                state = state.copy(playing = !state.playing)
            },
            onPrevious = { player?.playPrevious() },
            onNext = { player?.playNext() },
            onShuffle = { player?.toggleShuffleModeEnabled() },
            onRepeat = { player?.cycleNextRepeatMode() },
            onSeek = { player?.seekTo(it) },
            onQueue = { queue ->
                if (queue.id != state.activeQueueId) {
                    QueueSwitcher.switchTo(this, store, queue)
                    refreshQueues()
                }
            },
            onNewQueue = {
                QueueSwitcher.saveCurrent(this, store)?.let { name ->
                    Toast.makeText(this, getString(R.string.queues_saved, name), Toast.LENGTH_SHORT)
                        .show()
                }
                refreshQueues()
            },
            onSleepTimer = ::showSleepTimer,
            onOpenQueue = { startActivity(Intent(this, PlayQueueActivity::class.java)) },
            onAddToQueue = ::addCurrentToQueue
        )
        setContentView(
            ComposeView(this).apply {
                setContent {
                    VideoPrimeTheme(dark = dark) {
                        // the status bar takes the colour of the screen, not the app's violet
                        val surface = MaterialTheme.colorScheme.background
                        val background = (
                            state.coverColor?.let {
                                androidx.compose.ui.graphics.Color(it).copy(alpha = COVER_TINT_ALPHA)
                                    .compositeOver(surface)
                            } ?: surface
                            ).toArgb()
                        SideEffect {
                            window.statusBarColor = background
                            WindowCompat.getInsetsController(window, window.decorView)
                                .isAppearanceLightStatusBars = !dark
                        }
                        MusicPlayerScreen(state, actions)
                    }
                }
            }
        )
        bound = bindService(
            Intent(this, PlayerService::class.java).setAction(PlayerService.BIND_PLAYER_HOLDER_ACTION),
            connection,
            Context.BIND_AUTO_CREATE
        )
        refreshQueues()
    }

    override fun onResume() {
        super.onResume()
        refreshQueues()
    }

    override fun onDestroy() {
        player?.removeActivityListener(this)
        if (bound) unbindService(connection)
        super.onDestroy()
    }

    private fun refreshQueues() {
        val content = store.load()
        state = state.copy(
            queues = content.queues.sortedBy { it.id },
            activeQueueId = content.activeId,
            sleepMinutesLeft = sleepMinutesLeft()
        )
    }

    private fun sleepMinutesLeft(): Int {
        val left = SleepTimer.endsAt - System.currentTimeMillis()
        return if (left > 0) ((left + 59_999) / 60_000).toInt() else 0
    }

    private fun showSleepTimer() {
        val minutes = intArrayOf(15, 30, 45, 60, 90)
        val labels = minutes.map { getString(R.string.music_sleep_minutes, it) } +
            if (SleepTimer.endsAt > 0) listOf(getString(R.string.music_sleep_off)) else emptyList()
        AlertDialog.Builder(this)
            .setTitle(R.string.music_sleep_timer)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < minutes.size) {
                    SleepTimer.attach(player)
                    SleepTimer.start(minutes[which])
                } else {
                    SleepTimer.cancel()
                }
                state = state.copy(sleepMinutesLeft = sleepMinutesLeft())
            }
            .show()
    }

    /** Adds what is playing to one of the saved queues. */
    private fun addCurrentToQueue() {
        val item = player?.playQueue?.item ?: return
        val saved = store.load().queues.sortedBy { it.id }
        if (saved.isEmpty()) {
            Toast.makeText(this, R.string.queues_empty, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.downloads_to_queue)
            .setItems(saved.map { it.name }.toTypedArray()) { _, which ->
                store.append(
                    saved[which].id,
                    listOf(
                        SavedItem(
                            serviceId = item.serviceId,
                            url = item.url,
                            title = item.title,
                            uploader = item.uploader,
                            duration = item.duration,
                            streamType = item.streamType,
                            thumbnailUrl = ImageStrategy.choosePreferredImage(item.thumbnails)
                        )
                    )
                )
                Toast.makeText(
                    this,
                    getString(R.string.downloads_added_to_queue, saved[which].name),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun updateFromQueue(queue: PlayQueue?) {
        if (queue == null) return
        val item = queue.item
        val next = queue.getItem(queue.index + 1)
        state = state.copy(
            title = item?.title ?: state.title,
            artist = item?.uploader?.removeSuffix(" - Topic") ?: state.artist,
            thumbnailUrl = item?.let { ImageStrategy.choosePreferredImage(it.thumbnails) },
            index = queue.index,
            size = queue.size(),
            nextTitle = next?.title,
            live = item?.streamType == StreamType.LIVE_STREAM ||
                item?.streamType == StreamType.AUDIO_LIVE_STREAM
        )
    }

    override fun onQueueUpdate(queue: PlayQueue?) = updateFromQueue(queue)

    /** The cover the player has loaded, whose colour tints the screen. */
    private var coverBitmap: Bitmap? = null

    private fun updateCoverColor() {
        val bitmap = player?.thumbnail
        if (bitmap === coverBitmap) return
        coverBitmap = bitmap
        state = state.copy(coverColor = CoverArt.mainColor(bitmap))
    }

    override fun onPlaybackUpdate(
        state: Int,
        repeatMode: Int,
        shuffled: Boolean,
        parameters: PlaybackParameters?
    ) {
        this.state = this.state.copy(
            // the state the player reports: isPlaying is not updated yet at this point
            playing = when (state) {
                Player.STATE_PLAYING, Player.STATE_BUFFERING, Player.STATE_BLOCKED -> true
                else -> false
            },
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
            playing = player?.playWhenReady ?: state.playing,
            sleepMinutesLeft = sleepMinutesLeft()
        )
    }

    override fun onMetadataUpdate(info: StreamInfo?, queue: PlayQueue?) {
        updateFromQueue(queue)
        if (info != null) {
            state = state.copy(
                title = info.name,
                artist = info.uploaderName?.removeSuffix(" - Topic") ?: state.artist
            )
        }
        refreshQueues()
    }

    override fun onServiceStopped() {
        finish()
    }
}
