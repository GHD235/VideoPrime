package org.schabi.newpipe.videoprime

import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * PulsePlay: when a video ends and nothing else is in the queue (the TV mock-up "al acabar"),
 * the next suggested video starts by itself after a short count, like on YouTube: "Ver ahora",
 * "Cancelar" and "Volver a ver", with the ones after it underneath. Used by the main player only
 * (on the TV, the tablet and the phone alike); the background player has its own radio.
 */
class PlayerEndCountdown(private val player: Player, private val root: ViewGroup) {

    private var view: ComposeView? = null

    /** The player's controls under the count, kept out of the TV remote's reach meanwhile. */
    private val blocked = mutableListOf<Pair<ViewGroup, Int>>()

    val isShowing: Boolean
        get() = view != null

    /** Shows the count for what comes after the current video; false when nothing is known. */
    fun show(): Boolean {
        hide()
        val info = player.currentStreamInfo.orElse(null) ?: return false
        val queue = player.playQueue ?: return false
        val inQueue = queue.streams.map { it.url }.toSet()
        val next = info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .filter { it.url != info.url && it.url !in inQueue && !it.isShortFormContent }
            .distinctBy { it.url }
            .take(MAX_ITEMS)
        if (next.isEmpty()) return false

        val finished = info.name
        val compose = ComposeView(root.context).apply {
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent {
                VideoPrimeTheme(dark = true) {
                    Countdown(
                        finished = finished,
                        next = next,
                        onPlay = { play(it) },
                        onCancel = { hide() },
                        onReplay = {
                            hide()
                            // straight on ExoPlayer: Player.play() on a finished queue goes back
                            // to its first item, not to the start of this video
                            player.exoPlayer?.let {
                                it.seekTo(0)
                                it.play()
                            }
                        }
                    )
                }
            }
        }
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i) as? ViewGroup ?: continue
            blocked += child to child.descendantFocusability
            child.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        root.addView(compose)
        compose.bringToFront()
        view = compose
        return true
    }

    fun hide() {
        view?.let { root.removeView(it) }
        view = null
        blocked.forEach { (group, focusability) -> group.descendantFocusability = focusability }
        blocked.clear()
    }

    private fun play(item: StreamInfoItem) {
        hide()
        val queue = player.playQueue ?: return
        val queueItem = SinglePlayQueue(item).item ?: return
        queue.append(listOf(queueItem))
        player.selectQueueItem(queueItem)
    }

    @Composable
    private fun Countdown(
        finished: String,
        next: List<StreamInfoItem>,
        onPlay: (StreamInfoItem) -> Unit,
        onCancel: () -> Unit,
        onReplay: () -> Unit
    ) {
        val first = next.first()
        var left by remember { mutableIntStateOf(SECONDS) }
        val play by rememberUpdatedState(onPlay)
        LaunchedEffect(first.url) {
            while (left > 0) {
                delay(1000)
                left -= 1
            }
            play(first)
        }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        val wide = LocalConfiguration.current.screenWidthDp >= 600
        val pill = RoundedCornerShape(999.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE6080710))
                .clickable(enabled = false) {}
                .padding(horizontal = if (wide) 48.dp else 16.dp, vertical = if (wide) 32.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (wide) 20.dp else 10.dp)
        ) {
            Text(
                stringResource(R.string.pulseplay_end_finished, finished),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (wide) 32.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(if (wide) 420.dp else 200.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(VideoPrimeColors.DarkSurface),
                    contentAlignment = Alignment.Center
                ) {
                    SmoothImage(
                        ImageStrategy.choosePreferredImage(first.thumbnails),
                        Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .size(if (wide) 96.dp else 56.dp)
                            .clip(CircleShape)
                            .background(Color(0x99000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { left / SECONDS.toFloat() },
                            modifier = Modifier.fillMaxSize(),
                            color = VideoPrimeColors.New,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            strokeWidth = 5.dp
                        )
                        Text(
                            left.toString(),
                            fontSize = if (wide) 34.sp else 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 6.dp)
                ) {
                    Text(
                        stringResource(R.string.pulseplay_end_next_in, left).uppercase(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = VideoPrimeColors.New
                    )
                    Text(
                        first.name,
                        fontSize = if (wide) 26.sp else 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    first.uploaderName?.let {
                        Text(it, fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .focusRequester(focus)
                                .focusRing(pill)
                                .clip(pill)
                                .background(
                                    Brush.horizontalGradient(listOf(VideoPrimeColors.Accent, Color(0xFFA21CAF)))
                                )
                                .clickable { onPlay(first) }
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(
                                stringResource(R.string.pulseplay_end_watch_now),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        OutlinedPill(stringResource(R.string.cancel), onCancel)
                        OutlinedPill(stringResource(R.string.pulseplay_end_watch_again), onReplay)
                    }
                }
            }
            if (wide && next.size > 1) {
                Text(
                    stringResource(R.string.pulseplay_end_after).uppercase(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(next.drop(1), key = { it.url }) { item ->
                        Column(
                            modifier = Modifier
                                .width(200.dp)
                                .focusRing(RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPlay(item) },
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SmoothImage(
                                ImageStrategy.choosePreferredImage(item.thumbnails),
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(VideoPrimeColors.DarkSurface)
                            )
                            Text(
                                item.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun OutlinedPill(text: String, onClick: () -> Unit) {
        val pill = RoundedCornerShape(999.dp)
        Box(
            modifier = Modifier
                .focusRing(pill)
                .clip(pill)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), pill)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text(text, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }

    private companion object {
        const val SECONDS = 5
        const val MAX_ITEMS = 8
    }
}
