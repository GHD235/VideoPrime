package org.schabi.newpipe.videoprime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.schabi.newpipe.R

data class DownloadFolder(val name: String, val path: String, val files: List<LocalFile>)

enum class DownloadsTab { FOLDERS, ALL }

data class DownloadsState(
    val loading: Boolean = true,
    val tab: DownloadsTab = DownloadsTab.FOLDERS,
    val query: String = "",
    /** The folder being browsed, or null for the folder list. */
    val openFolder: DownloadFolder? = null,
    val folders: List<DownloadFolder> = emptyList(),
    val all: List<LocalFile> = emptyList()
)

class DownloadsActions(
    val onTab: (DownloadsTab) -> Unit,
    val onQuery: (String) -> Unit,
    val onOpenFolder: (DownloadFolder) -> Unit,
    val onPlay: (List<LocalFile>, Int) -> Unit,
    val onShuffle: (List<LocalFile>) -> Unit,
    val onMore: (LocalFile) -> Unit,
    val onManager: () -> Unit
)

@Composable
fun DownloadsScreen(state: DownloadsState, actions: DownloadsActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.openFolder == null) {
            SecondaryTabRow(
                selectedTabIndex = state.tab.ordinal,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = state.tab == DownloadsTab.FOLDERS,
                    onClick = { actions.onTab(DownloadsTab.FOLDERS) },
                    text = { Text(stringResource(R.string.downloads_folders)) }
                )
                Tab(
                    selected = state.tab == DownloadsTab.ALL,
                    onClick = { actions.onTab(DownloadsTab.ALL) },
                    text = { Text(stringResource(R.string.all)) }
                )
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = actions.onQuery,
            singleLine = true,
            label = { Text(stringResource(R.string.downloads_search)) },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.all.isEmpty() && state.query.isBlank() -> Empty(actions.onManager)

            state.openFolder != null -> FileList(
                files = filter(state.openFolder.files, state.query),
                header = state.openFolder.path,
                actions = actions
            )

            state.tab == DownloadsTab.ALL -> FileList(
                files = filter(state.all, state.query),
                header = null,
                actions = actions
            )

            else -> FolderList(state.folders, state.query, actions)
        }
    }
}

private fun filter(files: List<LocalFile>, query: String): List<LocalFile> {
    if (query.isBlank()) return files
    val q = query.trim().lowercase()
    return files.filter { it.title.lowercase().contains(q) || it.uploader.lowercase().contains(q) }
}

@Composable
private fun Empty(onManager: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.downloads_empty),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onManager, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.downloads_manager))
        }
    }
}

@Composable
private fun FolderList(folders: List<DownloadFolder>, query: String, actions: DownloadsActions) {
    val shown = if (query.isBlank()) {
        folders
    } else {
        folders.filter { filter(it.files, query).isNotEmpty() }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            OutlinedButton(
                onClick = actions.onManager,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.downloads_manager))
            }
        }
        items(shown, key = { it.path }) { folder ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { actions.onOpenFolder(folder) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        painterResource(R.drawable.ic_folder),
                        contentDescription = null,
                        tint = VideoPrimeColors.Accent
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(folder.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        "${folder.files.size} · ${folder.path}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FileList(files: List<LocalFile>, header: String?, actions: DownloadsActions) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                header?.let {
                    Text(
                        it,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { actions.onPlay(files, 0) }, enabled = files.isNotEmpty()) {
                        Text(stringResource(R.string.downloads_play_all))
                    }
                    FilledTonalButton(
                        onClick = { actions.onShuffle(files) },
                        enabled = files.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.downloads_shuffle))
                    }
                }
            }
        }
        itemsIndexed(files, actions)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    files: List<LocalFile>,
    actions: DownloadsActions
) {
    items(files.size, key = { files[it].url }) { index ->
        FileRow(files[index], onClick = { actions.onPlay(files, index) }, onMore = actions.onMore)
    }
}

@Composable
private fun FileRow(file: LocalFile, onClick: () -> Unit, onMore: (LocalFile) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (file.thumbnailUrl != null) {
                AsyncImage(
                    model = file.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                androidx.compose.material3.Icon(
                    painterResource(
                        if (file.isAudio) R.drawable.ic_headset else R.drawable.ic_movie
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (file.isAudio) {
                Text(
                    stringResource(R.string.downloads_audio),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(VideoPrimeColors.Accent, RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    file.uploader.takeIf { it.isNotEmpty() },
                    file.duration.takeIf { it > 0 }?.let { formatDuration(it) },
                    formatSize(file.size)
                ).joinToString(" · "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        androidx.compose.material3.IconButton(onClick = { onMore(file) }) {
            androidx.compose.material3.Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.more_options)
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1e6)
    bytes > 0 -> "%.0f kB".format(bytes / 1e3)
    else -> ""
}
