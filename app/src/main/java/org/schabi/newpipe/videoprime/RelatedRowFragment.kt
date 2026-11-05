package org.schabi.newpipe.videoprime

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * PulsePlay: the videos after this one as a list beside the video and its details (the TV
 * mock-up "vista pequeña A", also on a tablet or any wide screen in landscape): cards the
 * remote moves along, the autoplay switch in the heading. Choosing one opens it, holding it
 * gives the usual menu.
 */
class RelatedRowFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val autoplay = mutableStateOf(false)
    private val shown = mutableStateOf<List<StreamInfoItem>>(emptyList())
    private val seen = mutableStateOf<Set<String>>(emptySet())
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val items = BundleCompat.getSerializable(requireArguments(), ITEMS, ArrayList::class.java)
            ?.filterIsInstance<StreamInfoItem>()
            .orEmpty()
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        autoplay.value = prefs.getBoolean(getString(R.string.auto_queue_key), false)
        prefs.registerOnSharedPreferenceChangeListener(this)
        val dark = !ThemeHelper.isLightThemeSelected(requireContext())
        shown.value = items
        // videos already watched (in the history) are left out, as YouTube does
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val watched = withContext(Dispatchers.IO) {
                runCatching {
                    val dao = NewPipeDatabase.getInstance(appContext).streamHistoryDAO()
                    items.groupBy { it.serviceId }
                        .flatMap { (service, list) -> dao.getWatchedUrls(service, list.map { it.url }) }
                        .toSet()
                }.getOrDefault(emptySet())
            }
            // what is new first; the watched ones after it, marked
            seen.value = watched
            shown.value = WatchedUrls.leaveOut(items, watched) { it.url }
        }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VideoPrimeTheme(dark = dark) {
                    RelatedRow(shown.value, seen.value, autoplay.value, ::toggleAutoplay, ::open, ::showMenu)
                }
            }
        }
    }

    override fun onDestroyView() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroyView()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == getString(R.string.auto_queue_key)) {
            autoplay.value = prefs.getBoolean(key, false)
        }
    }

    private fun toggleAutoplay() {
        prefs.edit { putBoolean(getString(R.string.auto_queue_key), !autoplay.value) }
    }

    private fun open(item: StreamInfoItem) {
        NavigationHelper.openVideoDetailFragment(
            requireContext(),
            requireActivity().supportFragmentManager,
            item.serviceId,
            item.url,
            item.name,
            null,
            false
        )
    }

    private fun showMenu(item: StreamInfoItem) {
        try {
            InfoItemDialog.Builder(requireActivity(), requireContext(), this, item).create().show()
        } catch (e: IllegalArgumentException) {
            InfoItemDialog.Builder.reportErrorDuringInitialization(e, item)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun RelatedRow(
        items: List<StreamInfoItem>,
        watched: Set<String>,
        autoplayOn: Boolean,
        onToggleAutoplay: () -> Unit,
        onOpen: (StreamInfoItem) -> Unit,
        onMenu: (StreamInfoItem) -> Unit
    ) {
        if (items.isEmpty()) return
        val card = RoundedCornerShape(14.dp)
        val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
        LazyColumn(
            contentPadding = PaddingValues(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.pulseplay_autoplay),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = muted,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Switch(
                        checked = autoplayOn,
                        onCheckedChange = { onToggleAutoplay() },
                        modifier = Modifier.focusRing(RoundedCornerShape(999.dp)),
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = VideoPrimeColors.Accent,
                            checkedThumbColor = Color.White
                        )
                    )
                }
            }
            items(items, key = { it.url }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRing(card)
                        .clip(card)
                        .combinedClickable(
                            onClick = { onOpen(item) },
                            onLongClick = { onMenu(item) }
                        )
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(168.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VideoPrimeColors.DarkSurface)
                    ) {
                        SmoothImage(
                            ImageStrategy.choosePreferredImage(item.thumbnails),
                            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        )
                        if (item.duration > 0) {
                            Text(
                                Localization.getDurationString(item.duration),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xCC000000))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            item.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        item.uploaderName?.let {
                            Text(
                                it,
                                fontSize = 13.sp,
                                color = muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (item.url in watched) {
                            Text(
                                stringResource(R.string.pulseplay_watched),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = muted
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val ITEMS = "items"
        private const val MAX_ITEMS = 30

        @JvmStatic
        fun newInstance(info: StreamInfo): RelatedRowFragment {
            val items = info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .filter { it.url != info.url }
                .distinctBy { it.url }
                .take(MAX_ITEMS)
            return RelatedRowFragment().apply {
                arguments = Bundle().apply { putSerializable(ITEMS, ArrayList(items)) }
            }
        }
    }
}
