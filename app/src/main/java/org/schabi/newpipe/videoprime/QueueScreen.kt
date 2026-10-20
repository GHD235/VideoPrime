package org.schabi.newpipe.videoprime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.schabi.newpipe.R
import org.schabi.newpipe.util.Localization

data class QueueEntry(
    val key: Int,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val thumbnailUrl: String?
)

data class QueueScreenState(
    val entries: List<QueueEntry> = emptyList(),
    val index: Int = 0,
    val playing: Boolean = false,
    val shuffled: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val positionMillis: Long = 0,
    val durationMillis: Long = 0,
    /** The saved queue this is, if any. */
    val source: String? = null,
    val coverColor: Int? = null
)

class QueueScreenActions(
    val onClose: () -> Unit,
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
    val onSaveQueue: () -> Unit,
    val onSelect: (Int) -> Unit,
    val onRemove: (Int) -> Unit,
    val onMove: (Int, Int) -> Unit,
    val onPlayNext: (Int) -> Unit,
    val onAddToPlaylist: () -> Unit,
    val onClassicView: () -> Unit
)

private val Cyan = Color(0xFF22D3EE)
private val CyanText = Color(0xFF67E8F9)
private val Violet = Color(0xFF8B5CF6)
private val Glass = Color(0x14FFFFFF)
private val GlassBorder = Color(0x26FFFFFF)
private val TextMain = Color(0xFFF4F2FA)
private val TextSoft = Color(0xFFCFC9E0)
private val TextMuted = Color(0xFFB9B3CC)
private val TextFaint = Color(0xFF8F88A6)
private val BarBackground = Color(0xFF1B1924)
private val SwipeRed = Color(0xFFC2333B)

private val ROW_HEIGHT = 64.dp
private val ROW_GAP = 6.dp
private const val COLOR_CHANGE_MILLIS = 900
private const val SWIPE_REMOVE_FRACTION = 0.35f

@Composable
fun QueueScreen(state: QueueScreenState, actions: QueueScreenActions) {
    // the glow takes the colour of the cover, violet while there is none
    val accent by animateColorAsState(
        state.coverColor?.let { Color(it) } ?: Violet,
        tween(COLOR_CHANGE_MILLIS),
        label = "accent"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VideoPrimeColors.DarkBackground)
    ) {
        Glow(accent)
        Column(modifier = Modifier.fillMaxSize()) {
            Header(state.source, actions)
            NowPlayingCard(state, accent)
            Chips(state, accent, actions)
            QueueList(state, actions, modifier = Modifier.weight(1f))
            ControlBar(state.playing, accent, actions)
        }
    }
}

@Composable
private fun Glow(accent: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val big = size.width * 0.55f
        drawCircle(
            Brush.radialGradient(
                listOf(accent.copy(alpha = 0.53f), accent.copy(alpha = 0f)),
                center = Offset(size.width * 0.28f, size.width * 0.18f),
                radius = big
            ),
            radius = big,
            center = Offset(size.width * 0.28f, size.width * 0.18f)
        )
        val small = size.width * 0.47f
        drawCircle(
            Brush.radialGradient(
                listOf(Cyan.copy(alpha = 0.27f), Cyan.copy(alpha = 0f)),
                center = Offset(size.width * 1.05f, size.width * 0.55f),
                radius = small
            ),
            radius = small,
            center = Offset(size.width * 1.05f, size.width * 0.55f)
        )
    }
}

@Composable
private fun GlassButton(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Glass)
            .border(1.dp, GlassBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = TextMain)
    }
}

@Composable
private fun Header(source: String?, actions: QueueScreenActions) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassButton(R.drawable.ic_expand_more, stringResource(R.string.close), actions.onClose)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.videoprime_playing_from).uppercase(),
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                color = TextMuted
            )
            Text(
                source ?: stringResource(R.string.videoprime_current_queue),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            GlassButton(R.drawable.ic_more_vert, stringResource(R.string.more_options)) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_to_playlist)) },
                    onClick = {
                        menu = false
                        actions.onAddToPlaylist()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.videoprime_classic_queue)) },
                    onClick = {
                        menu = false
                        actions.onClassicView()
                    }
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(url: String?, size: Int, corner: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(corner.dp))
            .background(Brush.linearGradient(listOf(Violet, Cyan))),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                tint = VideoPrimeColors.DarkBackground
            )
        }
    }
}

@Composable
private fun NowPlayingCard(state: QueueScreenState, accent: Color) {
    val shape = RoundedCornerShape(24.dp)
    val current = state.entries.getOrNull(state.index)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .shadow(28.dp, shape, clip = false, ambientColor = accent, spotColor = accent)
            .clip(shape)
            .background(VideoPrimeColors.DarkBackground)
            .background(Glass)
            .border(1.dp, accent.copy(alpha = 0.55f), shape)
    ) {
        // the new song slides in from below, the old one leaves upwards
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith
                    (slideOutVertically { -it / 2 } + fadeOut())
            },
            contentKey = { it?.key },
            label = "now playing"
        ) { entry ->
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Thumbnail(entry?.thumbnailUrl, 84, 18)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.videoprime_now_playing).uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = CyanText
                    )
                    Text(
                        entry?.title.orEmpty(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextMain,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        entry?.artist.orEmpty(),
                        fontSize = 14.sp,
                        color = TextSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        EqualizerBars(state.playing)
                        if (state.durationMillis > 0) {
                            Text(
                                Localization.getDurationString(state.positionMillis / 1000) + " / " +
                                    Localization.getDurationString(state.durationMillis / 1000),
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
        // a thin line with how far the song has gone
        val fraction = if (state.durationMillis > 0) {
            (state.positionMillis.toFloat() / state.durationMillis).coerceIn(0f, 1f)
        } else {
            0f
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color(0x1AFFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(accent, Cyan)))
            )
        }
    }
}

/** Little bars that dance while the music plays. */
@Composable
private fun EqualizerBars(playing: Boolean) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val periods = listOf(520, 380, 610, 450)
    val rest = listOf(0.45f, 0.8f, 0.6f, 0.9f)
    Row(
        modifier = Modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        periods.forEachIndexed { i, period ->
            val level by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(period), AnimationRepeatMode.Reverse),
                label = "bar $i"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(if (playing) level else rest[i] * 0.4f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CyanText)
            )
        }
    }
}

@Composable
private fun Chip(text: String, icon: Int?, filled: Boolean, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(if (filled) accent else Color(0x0DFFFFFF))
            .border(1.dp, if (filled) accent else Color(0x2EFFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun Chips(state: QueueScreenState, accent: Color, actions: QueueScreenActions) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Chip(stringResource(R.string.music_shuffle), R.drawable.ic_shuffle, state.shuffled, accent, actions.onShuffle)
        Chip(
            stringResource(
                when (state.repeat) {
                    RepeatMode.OFF -> R.string.videoprime_repeat_off
                    RepeatMode.ALL -> R.string.videoprime_repeat_all
                    RepeatMode.ONE -> R.string.videoprime_repeat_one
                }
            ),
            if (state.repeat == RepeatMode.ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat,
            state.repeat != RepeatMode.OFF,
            accent,
            actions.onRepeat
        )
        Chip(stringResource(R.string.videoprime_save_queue), null, false, accent, actions.onSaveQueue)
    }
}

/** "1 h 22 min" or "22 min". */
private fun totalTime(seconds: Long): String {
    val minutes = (seconds + 59) / 60
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

@Composable
private fun QueueList(state: QueueScreenState, actions: QueueScreenActions, modifier: Modifier) {
    val upcoming = remember(state.entries, state.index) { state.entries.drop(state.index + 1) }
    val played = remember(state.entries, state.index) { state.entries.take(state.index) }
    var showPlayed by remember { mutableStateOf(false) }

    // while dragging (and until the queue confirms the move) the list shows this order
    var localOrder by remember { mutableStateOf<List<QueueEntry>?>(null) }
    var draggingKey by remember { mutableStateOf<Int?>(null) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(upcoming) {
        if (draggingKey == null) localOrder = null
    }
    val shown = localOrder ?: upcoming
    val step = with(LocalDensity.current) { (ROW_HEIGHT + ROW_GAP).toPx() }

    val current = state.entries.getOrNull(state.index)
    val leftInCurrent = ((current?.durationSeconds ?: 0) - state.positionMillis / 1000).coerceAtLeast(0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            stringResource(R.string.videoprime_up_next),
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextMain,
            modifier = Modifier.weight(1f)
        )
        Text(
            pluralStringResource(R.plurals.videoprime_songs, upcoming.size, upcoming.size) + " · " +
                totalTime(upcoming.sumOf { it.durationSeconds.coerceAtLeast(0) } + leftInCurrent),
            fontSize = 13.sp,
            color = TextMuted
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP)
    ) {
        if (played.isNotEmpty()) {
            item(key = "played") {
                Text(
                    stringResource(R.string.videoprime_played, played.size),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextFaint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showPlayed = !showPlayed }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            if (showPlayed) {
                itemsIndexed(played, key = { _, e -> "p" + e.key }) { i, entry ->
                    Box(modifier = Modifier.animateItem().graphicsLayer { alpha = 0.5f }) {
                        SongRow(entry, i + 1, dragHandle = null, onClick = { actions.onSelect(i) }, onLongClick = null)
                    }
                }
            }
        }
        items(shown, key = { it.key }) { entry ->
            val dragged = entry.key == draggingKey
            val position = shown.indexOf(entry)
            Box(
                modifier = (if (dragged) Modifier else Modifier.animateItem())
                    .zIndex(if (dragged) 1f else 0f)
                    .graphicsLayer {
                        if (dragged) {
                            translationY = dragOffset
                            scaleX = 1.03f
                            scaleY = 1.03f
                        }
                    }
            ) {
                val handle = Modifier.pointerInput(entry.key) {
                    detectDragGestures(
                        onDragStart = {
                            val order = localOrder ?: upcoming
                            localOrder = order
                            draggingKey = entry.key
                            dragFrom = order.indexOfFirst { it.key == entry.key }
                            dragOffset = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val order = localOrder ?: return@detectDragGestures
                            dragOffset += amount.y
                            val at = order.indexOfFirst { it.key == entry.key }
                            if (dragOffset > step / 2 && at < order.lastIndex) {
                                localOrder = order.toMutableList().apply { add(at + 1, removeAt(at)) }
                                dragOffset -= step
                            } else if (dragOffset < -step / 2 && at > 0) {
                                localOrder = order.toMutableList().apply { add(at - 1, removeAt(at)) }
                                dragOffset += step
                            }
                        },
                        onDragEnd = {
                            val to = localOrder?.indexOfFirst { it.key == entry.key } ?: -1
                            val from = dragFrom
                            draggingKey = null
                            dragOffset = 0f
                            if (from >= 0 && to >= 0 && from != to) {
                                val base = state.index + 1
                                actions.onMove(base + from, base + to)
                            } else {
                                localOrder = null
                            }
                        },
                        onDragCancel = {
                            draggingKey = null
                            dragOffset = 0f
                            localOrder = null
                        }
                    )
                }
                val queueIndex = state.index + 1 + position
                SwipeToRemove(onRemove = { actions.onRemove(queueIndex) }) {
                    SongRow(
                        entry,
                        number = queueIndex + 1,
                        dragHandle = handle,
                        onClick = { actions.onSelect(queueIndex) },
                        onLongClick = Pair(
                            { actions.onPlayNext(queueIndex) },
                            { actions.onRemove(queueIndex) }
                        )
                    )
                }
            }
        }
    }
}

/** Swiping a song to either side takes it out of the queue. */
@Composable
private fun SwipeToRemove(onRemove: () -> Unit, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val width = constraints.maxWidth.toFloat()
        if (offset.value != 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SwipeRed.copy(alpha = (abs(offset.value) / (width * SWIPE_REMOVE_FRACTION)).coerceIn(0.3f, 1f))),
                contentAlignment = if (offset.value > 0) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offset.value }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (abs(offset.value) > width * SWIPE_REMOVE_FRACTION) {
                                    offset.animateTo(if (offset.value > 0) width else -width, tween(160))
                                    onRemove()
                                    offset.snapTo(0f)
                                } else {
                                    offset.animateTo(0f)
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + amount) }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
private fun SongRow(
    entry: QueueEntry,
    number: Int,
    dragHandle: Modifier?,
    onClick: () -> Unit,
    /** Play next, remove; null for songs already played. */
    onLongClick: Pair<() -> Unit, () -> Unit>?
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .clip(RoundedCornerShape(16.dp))
                .background(VideoPrimeColors.DarkBackground)
                .background(Color(0x0DFFFFFF))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (onLongClick != null) ({ menu = true }) else null
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                number.toString(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextFaint,
                modifier = Modifier.width(26.dp),
                maxLines = 1
            )
            Thumbnail(entry.thumbnailUrl, 48, 12)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (entry.durationSeconds > 0) {
                        entry.artist + " · " + Localization.getDurationString(entry.durationSeconds)
                    } else {
                        entry.artist
                    },
                    fontSize = 13.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (dragHandle != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .then(dragHandle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_drag_handle),
                        contentDescription = stringResource(R.string.videoprime_drag_to_reorder),
                        tint = TextFaint
                    )
                }
            }
        }
        if (onLongClick != null) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.videoprime_play_next)) },
                    onClick = {
                        menu = false
                        onLongClick.first()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.videoprime_remove_from_queue)) },
                    onClick = {
                        menu = false
                        onLongClick.second()
                    }
                )
            }
        }
    }
}

@Composable
private fun ControlBar(playing: Boolean, accent: Color, actions: QueueScreenActions) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)
            .fillMaxWidth()
            .height(68.dp)
            .clip(shape)
            .background(BarBackground)
            .border(1.dp, Color(0x1FFFFFFF), shape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .clickable(onClick = actions.onPrevious),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_previous), stringResource(R.string.previous_stream), tint = TextMain)
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(18.dp, CircleShape, clip = false, ambientColor = accent, spotColor = accent)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent, Cyan)))
                .clickable(onClick = actions.onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            // the icon turns at once, with a quick cross-fade
            AnimatedContent(targetState = playing, label = "play pause") { isPlaying ->
                Icon(
                    painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                    stringResource(if (isPlaying) R.string.pause else R.string.play),
                    tint = VideoPrimeColors.DarkBackground,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .clickable(onClick = actions.onNext),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_next), stringResource(R.string.next_stream), tint = TextMain)
        }
    }
}
