package org.schabi.newpipe.videoprime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

data class DownloadFolder(val name: String, val path: String, val files: List<LocalFile>)

enum class DownloadsTab { MUSIC, VIDEOS, FOLDERS }

/** A download that has not finished yet. */
data class RunningDownload(
    val name: String,
    /** From 0 to 1. */
    val progress: Float,
    val done: Long,
    val total: Long,
    val failed: Boolean
)

/** What can be done with one file from its menu. */
enum class FileAction { PLAY, AUDIO_ONLY, ADD_TO_QUEUE, OPEN_SOURCE, SHARE, DELETE }

data class DownloadsState(
    val loading: Boolean = true,
    val tab: DownloadsTab = DownloadsTab.MUSIC,
    val query: String = "",
    val searching: Boolean = false,
    /** The folder being browsed, or null for the folder list. */
    val openFolder: DownloadFolder? = null,
    val folders: List<DownloadFolder> = emptyList(),
    val all: List<LocalFile> = emptyList(),
    val running: List<RunningDownload> = emptyList(),
    /** URLs of the selected files; selecting is on while this is not empty. */
    val selected: Set<String> = emptySet(),
    /** The file whose menu is open. */
    val menuFor: LocalFile? = null,
    /** Files waiting for the user to confirm that they are to be deleted. */
    val confirmDelete: List<LocalFile>? = null,
    val freeBytes: Long = 0
) {
    val music: List<LocalFile> get() = all.filter { it.isAudio }
    val videos: List<LocalFile> get() = all.filterNot { it.isAudio }
    val selecting: Boolean get() = selected.isNotEmpty()
    val selectedFiles: List<LocalFile> get() = all.filter { it.url in selected }
}

class DownloadsActions(
    val onTab: (DownloadsTab) -> Unit,
    val onQuery: (String) -> Unit,
    val onToggleSearch: () -> Unit,
    val onOpenFolder: (DownloadFolder) -> Unit,
    val onPlay: (List<LocalFile>, Int) -> Unit,
    val onShuffle: (List<LocalFile>) -> Unit,
    val onMenu: (LocalFile?) -> Unit,
    val onFileAction: (LocalFile, FileAction) -> Unit,
    val onToggleSelected: (LocalFile) -> Unit,
    val onSelectAll: (List<LocalFile>) -> Unit,
    val onClearSelection: () -> Unit,
    val onPlaySelected: () -> Unit,
    val onQueueSelected: () -> Unit,
    val onShareSelected: () -> Unit,
    val onDeleteSelected: () -> Unit,
    val onConfirmDelete: (Boolean) -> Unit,
    val onManager: () -> Unit
)

@Composable
fun DownloadsScreen(state: DownloadsState, actions: DownloadsActions) {
    NoAutoKeyboard()
    val shown = shownFiles(state)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.selecting) {
            SelectionBar(
                count = state.selected.size,
                onClose = actions.onClearSelection,
                onSelectAll = { actions.onSelectAll(shown) }
            )
        } else if (state.openFolder == null) {
            TabBar(state, actions)
        }
        if (!state.selecting && state.searching) {
            OutlinedTextField(
                value = state.query,
                onValueChange = actions.onQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.downloads_search)) },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Box(Modifier.weight(1f)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.all.isEmpty() && state.running.isEmpty() -> Empty(actions.onManager)

                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (!state.selecting && state.openFolder == null) {
                        state.running.firstOrNull()?.let { first ->
                            item(key = "running") {
                                RunningCard(first, state.running.size - 1, actions.onManager)
                            }
                        }
                    }
                    when {
                        state.openFolder != null -> fileList(
                            files = shown,
                            header = state.openFolder.path,
                            state = state,
                            actions = actions,
                            big = false
                        )

                        state.tab == DownloadsTab.FOLDERS -> folderList(
                            state.folders,
                            state.query,
                            actions
                        )

                        else -> fileList(
                            files = shown,
                            header = null,
                            state = state,
                            actions = actions,
                            big = state.tab == DownloadsTab.VIDEOS
                        )
                    }
                }
            }
        }

        if (state.selecting) {
            SelectionActions(actions)
        }
    }

    state.menuFor?.let { FileMenu(it, actions) }
    state.confirmDelete?.let { ConfirmDelete(it, actions.onConfirmDelete) }
}

/** @return the files listed now: the open folder's, or the music or videos, filtered */
private fun shownFiles(state: DownloadsState): List<LocalFile> = filter(
    when {
        state.openFolder != null -> state.openFolder.files
        state.tab == DownloadsTab.VIDEOS -> state.videos
        state.tab == DownloadsTab.MUSIC -> state.music
        else -> emptyList()
    },
    state.query
)

private fun filter(files: List<LocalFile>, query: String): List<LocalFile> {
    if (query.isBlank()) return files
    val q = query.trim().lowercase()
    return files.filter { it.title.lowercase().contains(q) || it.uploader.lowercase().contains(q) }
}

@Composable
private fun TabBar(state: DownloadsState, actions: DownloadsActions) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp)
        ) {
            listOf(
                DownloadsTab.MUSIC to R.string.downloads_music,
                DownloadsTab.VIDEOS to R.string.downloads_videos,
                DownloadsTab.FOLDERS to R.string.downloads_folders
            ).forEach { (tab, label) ->
                val selected = state.tab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(if (selected) VideoPrimeColors.Accent else Color.Transparent)
                        .clickable { actions.onTab(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(label),
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (selected) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        IconButton(onClick = actions.onToggleSearch) {
            Icon(
                painterResource(R.drawable.ic_search),
                contentDescription = stringResource(R.string.downloads_search)
            )
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.cancel)
            )
        }
        Text(
            pluralStringResource(R.plurals.downloads_selected, count, count),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onSelectAll) { Text(stringResource(R.string.downloads_select_all)) }
    }
}

@Composable
private fun SelectionActions(actions: DownloadsActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 6.dp)
    ) {
        BarAction(R.drawable.ic_play_arrow, R.string.downloads_play, actions.onPlaySelected)
        BarAction(R.drawable.ic_playlist_add, R.string.downloads_to_queue, actions.onQueueSelected)
        BarAction(R.drawable.ic_share, R.string.share, actions.onShareSelected)
        BarAction(
            R.drawable.ic_delete,
            R.string.delete,
            actions.onDeleteSelected,
            color = VideoPrimeColors.Danger
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarAction(
    icon: Int,
    label: Int,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color)
        Text(stringResource(label), fontSize = 12.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RunningCard(download: RunningDownload, others: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.ic_file_download),
            contentDescription = null,
            tint = VideoPrimeColors.Accent,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    if (download.failed) R.string.downloads_failed else R.string.downloads_running,
                    download.name
                ),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            LinearProgressIndicator(
                progress = { download.progress },
                color = VideoPrimeColors.Accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )
            Text(
                listOfNotNull(
                    "${(download.progress * 100).toInt()} %",
                    formatSize(download.total).takeIf { it.isNotEmpty() },
                    if (others > 0) stringResource(R.string.downloads_running_more, others) else null
                ).joinToString(" · "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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

private fun LazyListScope.folderList(
    folders: List<DownloadFolder>,
    query: String,
    actions: DownloadsActions
) {
    val shown = if (query.isBlank()) {
        folders
    } else {
        folders.filter { filter(it.files, query).isNotEmpty() }
    }
    items(shown, key = { "folder:" + it.path }) { folder ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { actions.onOpenFolder(folder) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    tint = VideoPrimeColors.Accent
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    pluralStringResource(
                        R.plurals.downloads_files,
                        folder.files.size,
                        folder.files.size
                    ) + " · " + formatSize(folder.files.sumOf { it.size }),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    item(key = "manager") {
        TextButton(
            onClick = actions.onManager,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(stringResource(R.string.downloads_manager))
        }
    }
}

private fun LazyListScope.fileList(
    files: List<LocalFile>,
    header: String?,
    state: DownloadsState,
    actions: DownloadsActions,
    big: Boolean
) {
    if (!state.selecting) {
        item(key = "header") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                header?.let {
                    Text(
                        it,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { actions.onPlay(files, 0) },
                        enabled = files.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VideoPrimeColors.Accent,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.downloads_play_all), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { actions.onShuffle(files) },
                        enabled = files.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.downloads_shuffle), fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    summary(files, state),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
    items(files.size, key = { files[it].url }) { index ->
        val file = files[index]
        val selected = file.url in state.selected
        val onClick = {
            if (state.selecting) actions.onToggleSelected(file) else actions.onPlay(files, index)
        }
        if (big) {
            VideoCard(file, selected, state.selecting, onClick, actions)
        } else {
            FileRow(file, selected, state.selecting, onClick, actions)
        }
    }
}

@Composable
private fun summary(files: List<LocalFile>, state: DownloadsState): String {
    val count = when {
        state.openFolder != null ->
            pluralStringResource(R.plurals.downloads_files, files.size, files.size)

        state.tab == DownloadsTab.VIDEOS ->
            pluralStringResource(R.plurals.downloads_video_count, files.size, files.size)

        else -> pluralStringResource(R.plurals.downloads_song_count, files.size, files.size)
    }
    val free = if (state.freeBytes > 0) {
        " · " + stringResource(R.string.downloads_free_space, formatSize(state.freeBytes))
    } else {
        ""
    }
    return count + " · " + formatSize(files.sumOf { it.size }) + free
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: LocalFile,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    actions: DownloadsActions
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) VideoPrimeColors.Selected else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = { actions.onToggleSelected(file) })
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { actions.onToggleSelected(file) })
        }
        Thumbnail(
            file,
            Modifier
                .size(width = if (file.isAudio) 52.dp else 92.dp, height = 52.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    file.artist.takeIf { it.isNotEmpty() },
                    file.duration.takeIf { it > 0 }?.let { formatDuration(it) }
                ).joinToString(" · "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!selecting) {
            IconButton(onClick = { actions.onMenu(file) }) {
                Icon(
                    painterResource(R.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.more_options)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoCard(
    file: LocalFile,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    actions: DownloadsActions
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) VideoPrimeColors.Selected else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = { actions.onToggleSelected(file) })
            .padding(8.dp)
    ) {
        Box {
            Thumbnail(
                file,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
            )
            file.duration.takeIf { it > 0 }?.let {
                Text(
                    formatDuration(it),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color(0xBF000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (selecting) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { actions.onToggleSelected(file) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color(0x99000000), CircleShape)
                )
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    file.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        file.uploader.takeIf { it.isNotEmpty() },
                        formatSize(file.size).takeIf { it.isNotEmpty() }
                    ).joinToString(" · "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!selecting) {
                IconButton(onClick = { actions.onMenu(file) }) {
                    Icon(
                        painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.more_options)
                    )
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(file: LocalFile, modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
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
            Icon(
                painterResource(if (file.isAudio) R.drawable.ic_headset else R.drawable.ic_movie),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileMenu(file: LocalFile, actions: DownloadsActions) {
    ModalBottomSheet(
        onDismissRequest = { actions.onMenu(null) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Thumbnail(
                file,
                Modifier
                    .size(width = if (file.isAudio) 54.dp else 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(file.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
                Text(
                    listOfNotNull(
                        file.artist.takeIf { it.isNotEmpty() },
                        file.duration.takeIf { it > 0 }?.let { formatDuration(it) },
                        formatSize(file.size).takeIf { it.isNotEmpty() }
                    ).joinToString(" · "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val options = buildList {
            add(
                Triple(
                    FileAction.PLAY,
                    R.drawable.ic_play_arrow,
                    if (file.isAudio) R.string.downloads_play else R.string.downloads_watch
                )
            )
            if (!file.isAudio) {
                add(Triple(FileAction.AUDIO_ONLY, R.drawable.ic_headset, R.string.downloads_audio_only))
            }
            add(Triple(FileAction.ADD_TO_QUEUE, R.drawable.ic_playlist_add, R.string.downloads_to_queue))
            if (file.sourceUrl != null && file.sourceServiceId >= 0) {
                add(Triple(FileAction.OPEN_SOURCE, R.drawable.ic_open_in_new, R.string.downloads_source))
            }
            add(Triple(FileAction.SHARE, R.drawable.ic_share, R.string.downloads_share_file))
            add(Triple(FileAction.DELETE, R.drawable.ic_delete, R.string.downloads_delete))
        }
        options.forEach { (action, icon, label) ->
            val color = if (action == FileAction.DELETE) {
                VideoPrimeColors.Danger
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { actions.onFileAction(file, action) }
                    .padding(horizontal = 24.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = color)
                Spacer(Modifier.width(18.dp))
                Text(
                    stringResource(label),
                    color = color,
                    fontSize = 15.sp,
                    fontWeight = if (action == FileAction.DELETE) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

@Composable
private fun ConfirmDelete(files: List<LocalFile>, onConfirm: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onConfirm(false) },
        title = {
            Text(
                if (files.size == 1) {
                    stringResource(R.string.downloads_delete_one, files.first().title)
                } else {
                    pluralStringResource(R.plurals.downloads_delete_many, files.size, files.size)
                }
            )
        },
        text = {
            Text(stringResource(R.string.downloads_delete_message, formatSize(files.sumOf { it.size })))
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(true) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = VideoPrimeColors.DangerFill,
                    contentColor = Color.White
                )
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(false) }) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Sizes as phones show them (1 GB = 1024 MB), without a useless ",0": "2 GB", "2,5 GB". */
internal fun formatSize(bytes: Long): String {
    val kb = 1024.0
    return when {
        bytes >= kb * kb * kb -> {
            val gb = bytes / (kb * kb * kb)
            if (gb % 1.0 < 0.05 || gb % 1.0 > 0.95) "%.0f GB".format(gb) else "%.1f GB".format(gb)
        }

        bytes >= kb * kb -> "%.0f MB".format(bytes / (kb * kb))

        bytes > 0 -> "%.0f kB".format(bytes / kb)

        else -> ""
    }
}
