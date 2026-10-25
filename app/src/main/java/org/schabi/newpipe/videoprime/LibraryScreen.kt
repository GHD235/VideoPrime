package org.schabi.newpipe.videoprime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.schabi.newpipe.R

/** A playlist of the library: one of the user's, or a saved YouTube playlist. */
data class LibraryPlaylist(
    /** Local playlist id, or remote playlist id when [isRemote]. */
    val id: Long,
    val isRemote: Boolean,
    val name: String,
    /** For saved YouTube playlists: their channel. */
    val uploader: String?,
    val url: String?,
    val serviceId: Int,
    val streamCount: Long,
    /** Up to four thumbnails for the cover; one is shown big, four as a mosaic. */
    val covers: List<String>,
    val offline: Boolean
)

data class LibraryState(
    val loading: Boolean = true,
    val playlists: List<LibraryPlaylist> = emptyList()
)

class LibraryActions(
    val onDownloads: () -> Unit,
    val onOffline: () -> Unit,
    val onQueues: () -> Unit,
    val onHistory: () -> Unit,
    val onOpen: (LibraryPlaylist) -> Unit,
    val onMore: (LibraryPlaylist) -> Unit
)

/**
 * VideoPrime: the "Library" section. Shortcuts to the downloads, the offline music, the queues
 * and the history, and the playlists as a grid of covers.
 */
@Composable
fun LibraryScreen(state: LibraryState, actions: LibraryActions) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        item(key = "shortcuts", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Shortcut(R.drawable.ic_file_download, R.string.downloads, actions.onDownloads)
                Shortcut(R.drawable.ic_cloud_download, R.string.offline_title, actions.onOffline)
                Shortcut(R.drawable.ic_playlist_play, R.string.queues_title, actions.onQueues)
                Shortcut(R.drawable.ic_history, R.string.title_activity_history, actions.onHistory)
            }
        }
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.library_playlists),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        when {
            state.loading -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.playlists.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.library_no_playlists),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
            }

            else -> items(state.playlists, key = { (if (it.isRemote) "r" else "l") + it.id }) {
                PlaylistCard(it, actions)
            }
        }
    }
}

@Composable
private fun Shortcut(icon: Int, label: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .focusRing(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = VideoPrimeColors.Accent,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            stringResource(label),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistCard(playlist: LibraryPlaylist, actions: LibraryActions) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .focusRing(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = { actions.onOpen(playlist) },
                onLongClick = { actions.onMore(playlist) }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Cover(playlist.covers)
            if (playlist.offline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .background(Color(0xB3000000), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_cloud_download),
                        contentDescription = stringResource(R.string.offline_available),
                        tint = Color(0xFFA7F3D0),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Text(
            playlist.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            listOfNotNull(
                playlist.uploader?.takeIf { it.isNotBlank() },
                if (playlist.streamCount >= 0) {
                    pluralStringResource(
                        R.plurals.videos,
                        playlist.streamCount.toInt(),
                        playlist.streamCount.toInt()
                    )
                } else {
                    null
                }
            ).joinToString(" · "),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** One big image, or a mosaic of four when the playlist has enough different videos. */
@Composable
private fun BoxScope.Cover(covers: List<String>) {
    if (covers.size >= 4) {
        Column(Modifier.fillMaxSize()) {
            for (row in 0..1) {
                Row(Modifier.weight(1f)) {
                    for (col in 0..1) {
                        AsyncImage(
                            model = covers[row * 2 + col],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        )
                    }
                }
            }
        }
    } else if (covers.isNotEmpty()) {
        AsyncImage(
            model = covers.first(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.placeholder_thumbnail_playlist),
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Icon(
            painterResource(R.drawable.ic_playlist_play),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.Center)
        )
    }
}
