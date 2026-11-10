package org.schabi.newpipe.videoprime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ThemeHelper

/**
 * PulsePlay: the top of a video's description: its figures first (views, likes, date) as three
 * tiles, then its chapters with their minute (tapping one jumps there if that video is playing).
 * The description's text and the rest of its details follow below as before.
 */
object DescriptionHeader {
    private const val MAX_CHAPTERS = 12

    private class Chapter(val title: String, val seconds: Int, val image: String?)

    @JvmStatic
    fun bind(view: ComposeView, info: StreamInfo?) {
        if (info == null) return
        val context = view.context
        val stats = ArrayList<Pair<String, String>>()
        if (info.viewCount >= 0) {
            stats.add(
                Localization.shortCount(context, info.viewCount) to context.getString(R.string.pulseplay_stat_views)
            )
        }
        if (info.likeCount >= 0) {
            stats.add(
                Localization.shortCount(context, info.likeCount) to context.getString(R.string.pulseplay_stat_likes)
            )
        }
        info.uploadDate?.let { date ->
            runCatching {
                val day = date.offsetDateTime().toLocalDate()
                stats.add(
                    day.format(DateTimeFormatter.ofPattern("d MMM")) to day.year.toString()
                )
            }.onFailure {
                runCatching {
                    stats.add(
                        date.offsetDateTime().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)) to ""
                    )
                }
            }
        }
        val chapters = info.streamSegments.orEmpty().take(MAX_CHAPTERS).map {
            Chapter(it.title.orEmpty(), it.startTimeSeconds, it.previewUrl)
        }
        if (stats.isEmpty() && chapters.isEmpty()) return
        val url = info.url
        val dark = !ThemeHelper.isLightThemeSelected(context)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            VideoPrimeTheme(dark = dark) {
                Header(stats, chapters) { seconds ->
                    // only when this is the video that plays (the description may be of another)
                    val player = PlayerService.getCurrentPlayer()
                    if (player != null && player.playQueue?.item?.url == url) {
                        player.seekTo(seconds * 1000L)
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(stats: List<Pair<String, String>>, chapters: List<Chapter>, onChapter: (Int) -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (stats.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.forEach { (value, label) ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                value,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            if (label.isNotEmpty()) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            if (chapters.isNotEmpty()) {
                Text(
                    stringResource(R.string.pulseplay_chapters).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                chapters.forEach { chapter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .focusRing(RoundedCornerShape(10.dp))
                            .clickable { onChapter(chapter.seconds) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier
                                .width(72.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            if (chapter.image != null) SmoothImage(chapter.image)
                        }
                        Text(
                            Localization.getDurationString(chapter.seconds.toLong()),
                            color = VideoPrimeColors.New,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            chapter.title,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
