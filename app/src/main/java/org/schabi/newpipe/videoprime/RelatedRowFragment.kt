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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * PulsePlay: the videos after this one as a row under the video and its details (the TV
 * mock-up "vista pequeña B", also on a tablet or any wide screen in landscape): big cards the
 * remote moves along, the autoplay switch in the heading. Choosing one opens it, holding it
 * gives the usual menu.
 */
class RelatedRowFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val autoplay = mutableStateOf(false)
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
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VideoPrimeTheme(dark = dark) {
                    RelatedRow(items, autoplay.value, ::toggleAutoplay, ::open, ::showMenu)
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
        autoplayOn: Boolean,
        onToggleAutoplay: () -> Unit,
        onOpen: (StreamInfoItem) -> Unit,
        onMenu: (StreamInfoItem) -> Unit
    ) {
        if (items.isEmpty()) return
        val pill = RoundedCornerShape(999.dp)
        val card = RoundedCornerShape(14.dp)
        val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
        Column(
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.pulseplay_up_next).uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = VideoPrimeColors.New
                )
                Text(
                    stringResource(
                        if (autoplayOn) {
                            R.string.pulseplay_autoplay_on
                        } else {
                            R.string.pulseplay_autoplay_off
                        }
                    ),
                    fontSize = 13.sp,
                    color = muted,
                    modifier = Modifier
                        .focusRing(pill)
                        .clip(pill)
                        .combinedClickable(onClick = onToggleAutoplay)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(items, key = { it.url }) { item ->
                    Column(
                        modifier = Modifier
                            .width(220.dp)
                            .focusRing(card)
                            .clip(card)
                            .combinedClickable(
                                onClick = { onOpen(item) },
                                onLongClick = { onMenu(item) }
                            ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(card)
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
                        Text(
                            item.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        item.uploaderName?.let {
                            Text(it, fontSize = 12.sp, color = muted, maxLines = 1)
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
