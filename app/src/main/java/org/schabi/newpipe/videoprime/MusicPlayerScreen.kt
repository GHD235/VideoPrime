package org.schabi.newpipe.videoprime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.schabi.newpipe.R

enum class RepeatMode { OFF, ONE, ALL }

data class MusicPlayerState(
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String? = null,
    val playing: Boolean = false,
    val positionMillis: Long = 0,
    val durationMillis: Long = 0,
    val live: Boolean = false,
    val shuffled: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val index: Int = 0,
    val size: Int = 0,
    val nextTitle: String? = null,
    val queues: List<SavedQueue> = emptyList(),
    val activeQueueId: Long? = null,
    /** Minutes left on the sleep timer, or 0 if it is off. */
    val sleepMinutesLeft: Int = 0,
    /** Main colour of the cover, to tint the top of the screen, or null. */
    val coverColor: Int? = null,
    /** The queue around the current song, only filled on wide screens (tablet). */
    val upNext: List<UpNextSong> = emptyList()
)

data class UpNextSong(val index: Int, val title: String, val artist: String, val thumbnailUrl: String?)

/** How strongly the cover's colour tints the top of the music player. */
const val COVER_TINT_ALPHA = 0.8f

class MusicPlayerActions(
    val onClose: () -> Unit,
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onQueue: (SavedQueue) -> Unit,
    val onNewQueue: () -> Unit,
    val onSleepTimer: () -> Unit,
    val onOpenQueue: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onJump: (Int) -> Unit = { }
)

/**
 * VideoPrime: the music player, inspired by Musicolet: big artwork and big buttons that are easy
 * to hit (also in the car), and the user's queues on top to jump between them.
 */
@Composable
fun MusicPlayerScreen(state: MusicPlayerState, actions: MusicPlayerActions) {
    // the colour of the cover fills the screen and changes smoothly with the song
    val cover by animateColorAsState(
        targetValue = state.coverColor?.let { Color(it) } ?: Color.Transparent,
        animationSpec = tween(COLOR_CHANGE_MILLIS),
        label = "cover color"
    )
    val background = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .background(
            Brush.verticalGradient(
                0f to cover.copy(alpha = COVER_TINT_ALPHA * cover.alpha),
                0.55f to cover.copy(alpha = COVER_TINT_ALPHA * 0.45f * cover.alpha),
                1f to cover.copy(alpha = COVER_TINT_ALPHA * 0.2f * cover.alpha)
            )
        )
    // PulsePlay: on a tablet in landscape the queue sits on the right, always in view
    if (isWideMusicPlayer(LocalConfiguration.current.screenWidthDp)) {
        Row(modifier = background) {
            PlayerColumn(state, actions, Modifier.weight(1f).fillMaxHeight())
            UpNextPanel(state, actions)
        }
    } else {
        PlayerColumn(state, actions, background)
    }
}

/** Whether the screen is wide enough for the cover and the queue side by side. */
fun isWideMusicPlayer(screenWidthDp: Int) = screenWidthDp >= WIDE_PLAYER_DP

private const val WIDE_PLAYER_DP = 840

@Composable
private fun PlayerColumn(state: MusicPlayerState, actions: MusicPlayerActions, modifier: Modifier) {
    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = actions.onClose) {
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = stringResource(R.string.close)
                )
            }
            Text(
                if (state.size > 0) {
                    stringResource(R.string.music_position, state.index + 1, state.size)
                } else {
                    ""
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Box {
                IconButton(onClick = actions.onSleepTimer) {
                    Icon(
                        painterResource(R.drawable.ic_bedtime),
                        contentDescription = stringResource(R.string.music_sleep_timer),
                        tint = if (state.sleepMinutesLeft > 0) {
                            VideoPrimeColors.Accent
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                if (state.sleepMinutesLeft > 0) {
                    Text(
                        "${state.sleepMinutesLeft}'",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(VideoPrimeColors.Accent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp)
                    )
                }
            }
        }

        QueueChips(state, actions)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            // the cover slides in the direction of the change (next / previous) and fades
            AnimatedContent(
                targetState = Pair(state.index, state.thumbnailUrl),
                transitionSpec = {
                    val forward = targetState.first >= initialState.first
                    val sign = if (forward) 1 else -1
                    (
                        slideInHorizontally(tween(SONG_CHANGE_MILLIS)) { it / 3 * sign } +
                            fadeIn(tween(SONG_CHANGE_MILLIS))
                        ) togetherWith (
                        slideOutHorizontally(tween(SONG_CHANGE_MILLIS)) { -it / 3 * sign } +
                            fadeOut(tween(SONG_CHANGE_MILLIS / 2))
                        )
                },
                contentAlignment = Alignment.Center,
                label = "cover"
            ) { (_, thumbnailUrl) ->
                Cover(thumbnailUrl)
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = Pair(state.title, state.artist),
                transitionSpec = { fadeIn(tween(SONG_CHANGE_MILLIS)) togetherWith fadeOut(tween(SONG_CHANGE_MILLIS / 2)) },
                modifier = Modifier.weight(1f),
                label = "title"
            ) { (title, artist) ->
                TitleAndArtist(title, artist)
            }
            IconButton(onClick = actions.onAddToQueue) {
                Icon(
                    painterResource(R.drawable.ic_playlist_add),
                    contentDescription = stringResource(R.string.downloads_to_queue)
                )
            }
        }

        SeekBar(state, actions.onSeek)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(
                icon = R.drawable.ic_shuffle,
                label = R.string.music_shuffle,
                size = 52,
                active = state.shuffled,
                onClick = actions.onShuffle
            )
            ControlButton(R.drawable.ic_previous, R.string.music_previous, 64, onClick = actions.onPrevious)
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(VideoPrimeColors.Accent)
                    .clickable(onClick = actions.onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                    contentDescription = stringResource(
                        if (state.playing) R.string.pause else R.string.play
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            ControlButton(R.drawable.ic_next, R.string.music_next, 64, onClick = actions.onNext)
            ControlButton(
                icon = if (state.repeat == RepeatMode.ONE) {
                    R.drawable.ic_repeat_one
                } else {
                    R.drawable.ic_repeat
                },
                label = R.string.music_repeat,
                size = 52,
                active = state.repeat != RepeatMode.OFF,
                onClick = actions.onRepeat
            )
        }

        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = actions.onOpenQueue)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(R.drawable.ic_playlist_play), contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                state.nextTitle?.let { stringResource(R.string.music_up_next, it) }
                    ?: stringResource(R.string.music_queue),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun UpNextPanel(state: MusicPlayerState, actions: MusicPlayerActions) {
    Column(
        modifier = Modifier
            .width(380.dp)
            .fillMaxHeight()
            .padding(top = 16.dp, end = 16.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = actions.onOpenQueue)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.music_queue),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            Icon(painterResource(R.drawable.ic_playlist_play), contentDescription = null)
        }
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                end = 12.dp,
                bottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(state.upNext, key = { it.index }) { song ->
                UpNextRow(song, current = song.index == state.index) { actions.onJump(song.index) }
            }
        }
    }
}

@Composable
private fun UpNextRow(song: UpNextSong, current: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (current) VideoPrimeColors.Accent.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QueueChips(state: MusicPlayerState, actions: MusicPlayerActions) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
    ) {
        items(state.queues, key = { it.id }) { queue ->
            val active = queue.id == state.activeQueueId
            Chip(queue.name, active) { actions.onQueue(queue) }
        }
        item(key = "new") {
            Chip("+ " + stringResource(R.string.downloads_queue_new), false, actions.onNewQueue)
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(shape)
            .background(if (active) MaterialTheme.colorScheme.onSurface else Color.Transparent)
            .border(
                1.dp,
                if (active) Color.Transparent else MaterialTheme.colorScheme.outline,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SeekBar(state: MusicPlayerState, onSeek: (Long) -> Unit) {
    if (state.live) {
        // nothing to seek in a live stream: just say it is live
        Row(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFE53935)))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.duration_live),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    val duration = state.durationMillis
    // the player tells the position once a second: in between it advances here, frame by frame
    val ticking by produceState(state.positionMillis, state.positionMillis, state.playing) {
        value = state.positionMillis
        if (!state.playing) return@produceState
        val start = System.nanoTime()
        while (true) {
            withFrameNanos { now -> value = state.positionMillis + (now - start) / 1_000_000 }
        }
    }
    // while dragging the bar follows the finger; after letting go it stays where it was left
    // until the player confirms the new position (or a moment passes)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var pendingSeek by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(pendingSeek, state.positionMillis) {
        val target = pendingSeek ?: return@LaunchedEffect
        if (kotlin.math.abs(state.positionMillis - target) < SEEK_CONFIRM_MILLIS) {
            pendingSeek = null
        } else {
            kotlinx.coroutines.delay(SEEK_HOLD_MILLIS)
            pendingSeek = null
        }
    }
    val shown = when {
        duration <= 0 -> 0L
        dragFraction != null -> (dragFraction!! * duration).toLong()
        pendingSeek != null -> pendingSeek!!
        else -> ticking.coerceIn(0, duration)
    }
    val fraction = if (duration > 0) shown.toFloat() / duration else 0f
    val seek: (Float) -> Unit = { f ->
        val target = (f.coerceIn(0f, 1f) * duration).toLong()
        pendingSeek = target
        onSeek(target)
    }
    val accent = VideoPrimeColors.Accent
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val thumbColor = MaterialTheme.colorScheme.onSurface
    val dragging = dragFraction != null

    Column(Modifier.padding(horizontal = 28.dp, vertical = 4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(duration) {
                    if (duration <= 0) return@pointerInput
                    detectTapGestures { offset -> seek(offset.x / size.width) }
                }
                .pointerInput(duration) {
                    if (duration <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragFraction?.let(seek)
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height / 2
                val track = if (dragging) 6.dp.toPx() else 4.dp.toPx()
                drawLine(inactive, Offset(0f, y), Offset(size.width, y), track, StrokeCap.Round)
                val x = size.width * fraction.coerceIn(0f, 1f)
                drawLine(accent, Offset(0f, y), Offset(x, y), track, StrokeCap.Round)
                drawCircle(thumbColor, radius = if (dragging) 10.dp.toPx() else 7.dp.toPx(), center = Offset(x, y))
            }
        }
        Row {
            Text(
                time(shown),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                time(duration),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Cover(thumbnailUrl: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TitleAndArtist(title: String, artist: String) {
    Column {
        Text(
            title,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            artist,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** How long the cover and title take to change to the next song. */
private const val SONG_CHANGE_MILLIS = 320

/** How long the background takes to change to the colour of the new cover. */
private const val COLOR_CHANGE_MILLIS = 900

/** A seek counts as done when the player reports a position this close to it. */
private const val SEEK_CONFIRM_MILLIS = 1500L

/** How long the bar waits for the player to confirm a seek before following it again. */
private const val SEEK_HOLD_MILLIS = 2500L

@Composable
private fun ControlButton(
    icon: Int,
    label: Int,
    size: Int,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                if (size >= 64) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(icon),
            contentDescription = stringResource(label),
            tint = if (active) {
                VideoPrimeColors.Accent
            } else if (size >= 64) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(if (size >= 64) 30.dp else 24.dp)
        )
    }
}

private fun time(millis: Long): String {
    val seconds = millis / 1000
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
