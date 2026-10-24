package org.schabi.newpipe.videoprime

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.util.function.Consumer
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry
import org.schabi.newpipe.util.ThemeHelper

/**
 * VideoPrime: the "add to a playlist" panel as a sheet from the bottom, like the other menus:
 * a "new playlist" row and the local playlists with their cover, name and number of videos.
 * The playlists that already have the video are dimmed and say so.
 */
class PlaylistPicker(
    context: Context,
    private val onNew: Runnable,
    private val onPick: Consumer<PlaylistDuplicatesEntry>
) {
    /** null while loading. */
    private var shown by mutableStateOf<List<PlaylistDuplicatesEntry>?>(null)
    private val dark = !ThemeHelper.isLightThemeSelected(context)

    val view = ComposeView(context).apply {
        setContent { VideoPrimeTheme(dark = dark) { Sheet() } }
    }

    fun setPlaylists(list: List<PlaylistDuplicatesEntry>) {
        shown = list
    }

    @Composable
    private fun Sheet() {
        val surface = if (dark) VideoPrimeColors.DarkSurface else VideoPrimeColors.LightBackground
        val raised = if (dark) VideoPrimeColors.DarkSurfaceHigh else VideoPrimeColors.LightSurface
        val text = if (dark) VideoPrimeColors.DarkText else VideoPrimeColors.LightText
        val muted = if (dark) VideoPrimeColors.DarkTextMuted else VideoPrimeColors.LightTextMuted
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(surface)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(muted.copy(alpha = 0.5f))
            )
            Text(
                stringResource(R.string.add_to_playlist),
                color = text,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(raised)
                    .clickable { onNew.run() }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        tint = text,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    stringResource(R.string.create_playlist),
                    color = text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val list = shown
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(list, key = { it.uid }) { playlist ->
                        PlaylistRow(playlist, text, muted, raised)
                    }
                }
            }
        }
    }

    @Composable
    private fun PlaylistRow(
        playlist: PlaylistDuplicatesEntry,
        text: androidx.compose.ui.graphics.Color,
        muted: androidx.compose.ui.graphics.Color,
        raised: androidx.compose.ui.graphics.Color
    ) {
        val contains = playlist.timesStreamIsContained > 0
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onPick.accept(playlist) }
                .padding(10.dp)
                .alpha(if (contains) 0.55f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(raised),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.thumbnailUrl.isNullOrEmpty()) {
                    Icon(
                        painterResource(R.drawable.ic_playlist_play),
                        contentDescription = null,
                        tint = muted,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    AsyncImage(
                        model = playlist.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.orderingName.orEmpty(),
                    color = text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (contains) {
                        stringResource(R.string.videoprime_playlist_contains)
                    } else {
                        pluralStringResource(
                            R.plurals.videoprime_playlist_videos,
                            playlist.streamCount.toInt(),
                            playlist.streamCount.toInt()
                        )
                    },
                    color = if (contains) VideoPrimeColors.New else muted,
                    fontSize = 13.sp
                )
            }
        }
    }
}
