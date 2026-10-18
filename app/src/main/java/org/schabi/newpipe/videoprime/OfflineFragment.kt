package org.schabi.newpipe.videoprime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import coil3.compose.AsyncImage
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.image.ImageStrategy

private data class OfflineState(
    val index: OfflineMusic.Index = OfflineMusic.Index(),
    val enabled: Boolean = true,
    val wifiOnly: Boolean = true,
    val limitMb: Int = 2048
)

/**
 * VideoPrime: Library → Offline. What is saved to listen to without connection, how much room
 * it takes, and its settings.
 */
class OfflineFragment : Fragment() {
    private val disposables = CompositeDisposable()
    private var state by mutableStateOf(OfflineState())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val dark = !ThemeHelper.isLightThemeSelected(requireContext())
        setContent { VideoPrimeTheme(dark = dark) { Screen(state) } }
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.offline_title)
        refresh()
    }

    override fun onDestroyView() {
        disposables.clear()
        super.onDestroyView()
    }

    private fun refresh() {
        val context = requireContext().applicationContext
        disposables.add(
            Single.fromCallable { OfflineMusic.load(context) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ index ->
                    state = OfflineState(
                        index = index,
                        enabled = OfflineMusic.isEnabled(context),
                        wifiOnly = OfflineMusic.isWifiOnly(context),
                        limitMb = OfflineMusic.limitMb(context)
                    )
                }, { })
        )
    }

    private fun play(items: List<OfflineItem>, shuffle: Boolean) {
        if (items.isEmpty()) return
        val songs = if (shuffle) items.shuffled() else items
        val queue = SinglePlayQueue(
            songs.map { item ->
                StreamInfoItem(item.serviceId, item.url, item.title, StreamType.AUDIO_STREAM).also {
                    it.uploaderName = item.uploader
                    it.duration = item.duration
                    it.thumbnails = ImageStrategy.dbUrlToImageList(item.thumbnailUrl)
                }
            },
            0
        )
        NavigationHelper.playOnBackgroundPlayer(requireContext(), queue, true)
    }

    private fun chooseLimit() {
        val context = requireContext()
        val labels = OfflineMusic.LIMITS_MB.map { formatSize(it * OfflineMusic.MB) }
        AlertDialog.Builder(context)
            .setTitle(R.string.offline_space)
            .setSingleChoiceItems(
                labels.toTypedArray(),
                OfflineMusic.LIMITS_MB.indexOf(state.limitMb)
            ) { dialog, which ->
                val mb = OfflineMusic.LIMITS_MB[which]
                disposables.add(
                    Single.fromCallable { OfflineMusic.setLimitMb(context, mb) }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ refresh() }, { })
                )
                dialog.dismiss()
            }
            .show()
    }

    private fun confirmClear() {
        val context = requireContext()
        AlertDialog.Builder(context)
            .setTitle(R.string.offline_clear)
            .setMessage(R.string.offline_clear_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                disposables.add(
                    Single.fromCallable { OfflineMusic.clear(context) }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ refresh() }, { })
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun unpin(collection: OfflineCollection) {
        OfflineMusic.setPinned(requireContext(), collection, false)
        refresh()
    }

    @Composable
    private fun Screen(state: OfflineState) {
        val songs = state.index.items.sortedByDescending { it.lastPlayed }
        val limit = state.limitMb * OfflineMusic.MB
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "usage") {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(16.dp)
                ) {
                    Text(
                        stringResource(
                            R.string.offline_usage,
                            formatSize(state.index.usedBytes).ifEmpty { "0 MB" },
                            formatSize(limit)
                        ),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    LinearProgressIndicator(
                        progress = { (state.index.usedBytes.toFloat() / limit).coerceIn(0f, 1f) },
                        color = VideoPrimeColors.Accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    )
                    Text(
                        pluralStringResource(R.plurals.downloads_song_count, songs.size, songs.size) +
                            if (state.index.wanted.isNotEmpty()) {
                                " · " + pluralStringResource(
                                    R.plurals.offline_waiting,
                                    state.index.wanted.size,
                                    state.index.wanted.size
                                )
                            } else {
                                ""
                            },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.index.pinnedPending > 0) {
                        Text(
                            pluralStringResource(
                                if (state.wifiOnly) {
                                    R.plurals.offline_lists_pending_wifi
                                } else {
                                    R.plurals.offline_lists_pending
                                },
                                state.index.pinnedPending,
                                state.index.pinnedPending
                            ),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VideoPrimeColors.Accent,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Text(
                        stringResource(R.string.offline_explanation),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            item(key = "settings") {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    SettingSwitch(
                        R.string.offline_save_listened,
                        R.string.offline_save_listened_summary,
                        state.enabled
                    ) {
                        OfflineMusic.setEnabled(requireContext(), it)
                        refresh()
                    }
                    SettingSwitch(
                        R.string.offline_wifi_only,
                        R.string.offline_wifi_only_summary,
                        state.wifiOnly
                    ) {
                        OfflineMusic.setWifiOnly(requireContext(), it)
                        refresh()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { chooseLimit() }
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        Text(
                            stringResource(R.string.offline_space),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(formatSize(limit), color = VideoPrimeColors.Accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item(key = "lists") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.offline_lists),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (state.index.collections.isEmpty()) {
                        Text(
                            stringResource(R.string.offline_lists_empty),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    state.index.collections.forEach { collection ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(R.drawable.ic_playlist_play),
                                contentDescription = null,
                                tint = VideoPrimeColors.Accent
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                collection.name,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { unpin(collection) }) {
                                Icon(
                                    painterResource(R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.offline_unpin)
                                )
                            }
                        }
                    }
                }
            }
            item(key = "songs-header") {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.offline_songs),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (songs.isNotEmpty()) {
                        TextButton(onClick = { confirmClear() }) {
                            Text(stringResource(R.string.offline_clear), color = VideoPrimeColors.Danger)
                        }
                    }
                }
                if (songs.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { play(songs, false) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VideoPrimeColors.Accent,
                                contentColor = Color.White
                            )
                        ) { Text(stringResource(R.string.downloads_play_all), fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = { play(songs, true) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onSurface,
                                contentColor = MaterialTheme.colorScheme.surface
                            )
                        ) { Text(stringResource(R.string.downloads_shuffle), fontWeight = FontWeight.Bold) }
                    }
                }
            }
            items(songs, key = { it.serviceId.toString() + it.url }) { song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { play(songs.dropWhile { it != song } + songs.takeWhile { it != song }, false) }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        song.thumbnailUrl?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            listOfNotNull(
                                song.uploader.removeSuffix(" - Topic").takeIf { it.isNotEmpty() },
                                formatSize(song.size).takeIf { it.isNotEmpty() },
                                if (song.pinned) stringResource(R.string.offline_in_list) else null
                            ).joinToString(" · "),
                            fontSize = 12.sp,
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
    private fun SettingSwitch(title: Int, summary: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onChange(!checked) }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(summary),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }

    companion object {
        @JvmStatic
        fun open(fragmentManager: FragmentManager) {
            fragmentManager.beginTransaction()
                .replace(R.id.fragment_holder, OfflineFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}
