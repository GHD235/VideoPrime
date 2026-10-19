package org.schabi.newpipe.videoprime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
    val sleepMinutesLeft: Int = 0
)

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
    val onAddToQueue: () -> Unit
)

/**
 * VideoPrime: the music player, inspired by Musicolet: big artwork and big buttons that are easy
 * to hit (also in the car), and the user's queues on top to jump between them.
 */
@Composable
fun MusicPlayerScreen(state: MusicPlayerState, actions: MusicPlayerActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 16.dp)
    ) {
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (state.thumbnailUrl != null) {
                    AsyncImage(
                        model = state.thumbnailUrl,
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

        Row(
            modifier = Modifier.padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.artist,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMillis.coerceAtLeast(1)
    val value = dragging ?: (state.positionMillis.toFloat() / duration).coerceIn(0f, 1f)
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
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Slider(
            value = value,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            enabled = !state.live && state.durationMillis > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = VideoPrimeColors.Accent,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        Row(Modifier.padding(horizontal = 8.dp)) {
            Text(
                time((value * duration).toLong()),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (state.live) stringResource(R.string.duration_live) else time(state.durationMillis),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
