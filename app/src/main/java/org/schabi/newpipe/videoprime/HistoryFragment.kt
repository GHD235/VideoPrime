package org.schabi.newpipe.videoprime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import coil3.compose.AsyncImage
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.info_list.dialog.StreamDialogDefaultEntry
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.settings.HistorySettingsFragment
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper

/**
 * VideoPrime: the watch history by day (today, yesterday, this week, this month, earlier), with
 * a search box, what is left of each video and "play all" / "background". A long press offers
 * the usual menu with "delete".
 */
class HistoryFragment : Fragment() {
    private val disposables = CompositeDisposable()
    private var entries by mutableStateOf<List<StreamStatisticsEntry>?>(null)
    private lateinit var recordManager: HistoryRecordManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val colors = SheetColors(!ThemeHelper.isLightThemeSelected(requireContext()))
        setContent {
            VideoPrimeTheme(dark = colors.dark) {
                Screen(colors, entries, ::open, ::showMenu, ::playAll)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recordManager = HistoryRecordManager(requireContext())
        (activity as? AppCompatActivity)?.supportActionBar?.title =
            getString(R.string.title_activity_history)
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menu.add(R.string.clear_views_history_title)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    if (menuItem.title == getString(R.string.clear_views_history_title)) {
                        HistorySettingsFragment.openDeleteWatchHistoryDialog(
                            requireContext(),
                            recordManager,
                            disposables
                        )
                        return true
                    }
                    return false
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
        disposables.add(
            recordManager.streamStatistics
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ entries = it }, { entries = emptyList() })
        )
    }

    override fun onDestroyView() {
        disposables.clear()
        super.onDestroyView()
    }

    private fun open(entry: StreamStatisticsEntry) {
        val stream = entry.streamEntity
        NavigationHelper.openVideoDetailFragment(
            requireContext(),
            parentFragmentManager,
            stream.serviceId,
            stream.url,
            stream.title,
            null,
            false
        )
    }

    private fun playAll(background: Boolean, shown: List<StreamStatisticsEntry>) {
        if (shown.isEmpty()) return
        val queue = SinglePlayQueue(shown.map { it.toStreamInfoItem() }, 0)
        if (background) {
            NavigationHelper.playOnBackgroundPlayer(requireContext(), queue, false)
        } else {
            NavigationHelper.playOnMainPlayer(requireContext(), queue, false)
        }
    }

    private fun showMenu(entry: StreamStatisticsEntry) {
        val item = entry.toStreamInfoItem()
        try {
            InfoItemDialog.Builder(requireActivity(), requireContext(), this, item)
                .addEntry(StreamDialogDefaultEntry.DELETE)
                .setAction(StreamDialogDefaultEntry.DELETE) { _, _ -> delete(entry) }
                .create()
                .show()
        } catch (e: IllegalArgumentException) {
            InfoItemDialog.Builder.reportErrorDuringInitialization(e, item)
        }
    }

    private fun delete(entry: StreamStatisticsEntry) {
        disposables.add(
            recordManager.deleteStreamHistoryAndState(entry.streamId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { Toast.makeText(context, R.string.one_item_deleted, Toast.LENGTH_SHORT).show() },
                    { Toast.makeText(context, R.string.general_error, Toast.LENGTH_SHORT).show() }
                )
        )
    }

    companion object {
        @JvmStatic
        fun open(fragmentManager: FragmentManager) {
            fragmentManager.beginTransaction()
                .replace(R.id.fragment_holder, HistoryFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}

private enum class Period(val title: Int) {
    TODAY(R.string.videoprime_history_today),
    YESTERDAY(R.string.videoprime_history_yesterday),
    WEEK(R.string.videoprime_history_week),
    MONTH(R.string.videoprime_history_month),
    EARLIER(R.string.videoprime_history_earlier)
}

private fun periodOf(entry: StreamStatisticsEntry, today: LocalDate): Period {
    val day = entry.latestAccessDate.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
    return when {
        !day.isBefore(today) -> Period.TODAY
        day == today.minusDays(1) -> Period.YESTERDAY
        day.isAfter(today.minusDays(7)) -> Period.WEEK
        day.isAfter(today.minusDays(30)) -> Period.MONTH
        else -> Period.EARLIER
    }
}

@Composable
private fun Screen(
    colors: SheetColors,
    entries: List<StreamStatisticsEntry>?,
    onOpen: (StreamStatisticsEntry) -> Unit,
    onMenu: (StreamStatisticsEntry) -> Unit,
    onPlayAll: (Boolean, List<StreamStatisticsEntry>) -> Unit
) {
    NoAutoKeyboard()
    if (entries == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var query by remember { mutableStateOf("") }
    val shown = remember(entries, query) {
        val q = query.trim()
        entries.sortedByDescending { it.latestAccessDate }.filter {
            q.isEmpty() || it.streamEntity.title.contains(q, ignoreCase = true) ||
                it.streamEntity.uploader.contains(q, ignoreCase = true)
        }
    }
    val today = remember { LocalDate.now() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.videoprime_history_search)) },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                VideoPrimeChip(stringResource(R.string.play_all), false, null) { onPlayAll(false, shown) }
                VideoPrimeChip(stringResource(R.string.controls_background_title), false, null) {
                    onPlayAll(true, shown)
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.no_videos),
                    color = colors.muted,
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                )
            }
        }
        // the rows under their day's header
        val groups = shown.groupBy { periodOf(it, today) }
        Period.entries.forEach { period ->
            val group = groups[period] ?: return@forEach
            item(key = "h:" + period.name) {
                Text(
                    stringResource(period.title),
                    color = colors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
                )
            }
            items(group, key = { "s:" + it.streamId }) { entry ->
                HistoryRow(entry, colors, onOpen, onMenu)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    entry: StreamStatisticsEntry,
    colors: SheetColors,
    onOpen: (StreamStatisticsEntry) -> Unit,
    onMenu: (StreamStatisticsEntry) -> Unit
) {
    val stream = entry.streamEntity
    val durationMillis = TimeUnit.SECONDS.toMillis(stream.duration)
    val progress = if (durationMillis > 0) (entry.progressMillis.toFloat() / durationMillis).coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = { onOpen(entry) }, onLongClick = { onMenu(entry) })
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(144.dp)
                .height(81.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.raised)
        ) {
            AsyncImage(
                model = stream.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (stream.duration > 0) {
                Text(
                    Localization.getDurationString(stream.duration),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            if (progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(VideoPrimeColors.Live)
                    )
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                stream.title,
                color = colors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                CarText.artist(stream.uploader),
                color = colors.muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val left = durationMillis - entry.progressMillis
            val status = when {
                progress >= WATCHED -> stringResource(R.string.videoprime_history_watched)

                progress > 0f && left > 0 ->
                    stringResource(R.string.videoprime_history_left, Localization.getDurationString(left / 1000))

                else -> null
            }
            if (status != null) {
                Text(status, color = colors.muted, fontSize = 12.sp)
            }
        }
    }
}

private const val WATCHED = 0.95f
