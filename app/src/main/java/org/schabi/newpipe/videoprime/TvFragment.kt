package org.schabi.newpipe.videoprime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.text.Normalizer
import java.util.Locale
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.tv.TvChannelList
import org.schabi.newpipe.extractor.services.tv.TvChannelList.Format
import org.schabi.newpipe.extractor.services.tv.TvChannelList.TvChannel
import org.schabi.newpipe.extractor.services.tv.TvParsingHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

/**
 * VideoPrime: free-to-air TV channels of Spain, grouped like the TDTChannels list. Channels
 * with an HLS stream play in the app; the others open on YouTube, Twitch or the web.
 */
class TvFragment : Fragment() {
    private val disposables = CompositeDisposable()
    private var state by mutableStateOf(TvState())
    private var channels: List<TvChannel> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val dark = !ThemeHelper.isLightThemeSelected(requireContext())
        setContent {
            VideoPrimeTheme(dark = dark) {
                TvScreen(
                    state = state,
                    onQuery = { state = state.copy(query = it).filtered() },
                    onGroup = { state = state.copy(group = it).filtered() },
                    onOpen = ::open,
                    onRetry = ::load
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        load()
    }

    override fun onDestroyView() {
        disposables.clear()
        super.onDestroyView()
    }

    private fun load() {
        state = state.copy(loading = true, error = false)
        disposables.add(
            Single.fromCallable { TvChannelList.getChannels() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { list ->
                        channels = list
                        state = state.copy(
                            loading = false,
                            groups = list.map { it.group }.distinct()
                        ).filtered()
                    },
                    { state = state.copy(loading = false, error = true) }
                )
        )
    }

    private fun TvState.filtered(): TvState {
        val query = normalize(query)
        return copy(
            channels = channels
                .filter { group == null || it.group == group }
                .filter { query.isEmpty() || normalize(it.name).contains(query) }
                .map { TvItem(it.id, it.name, it.group, it.logo) }
        )
    }

    private fun open(item: TvItem) {
        val channel = channels.firstOrNull { it.id == item.id } ?: return
        val option = channel.bestOption
        when (option.format) {
            Format.M3U8 -> openStream(ServiceList.TV.serviceId, channel.url, channel.name)

            Format.YOUTUBE -> openYoutubeLive(channel, option.url)

            Format.WEB -> {
                val twitchUrl = TvParsingHelper.twitchUrlFromPlayer(option.url)
                if (twitchUrl != null) {
                    openStream(ServiceList.Twitch.serviceId, twitchUrl, channel.name)
                } else {
                    ShareUtils.openUrlInBrowser(requireContext(), channel.web ?: option.url)
                }
            }
        }
    }

    private fun openYoutubeLive(channel: TvChannel, channelLiveUrl: String) {
        disposables.add(
            Maybe.fromCallable<String> { TvParsingHelper.resolveYoutubeLive(channelLiveUrl) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { url -> openStream(ServiceList.YouTube.serviceId, url, channel.name) },
                    { notLive(channel) },
                    { notLive(channel) }
                )
        )
    }

    private fun notLive(channel: TvChannel) {
        val context = context ?: return
        Toast.makeText(
            context,
            getString(R.string.tv_not_live, channel.name),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun openStream(serviceId: Int, url: String, title: String) {
        NavigationHelper.openVideoDetailFragment(
            requireContext(),
            requireActivity().supportFragmentManager,
            serviceId,
            url,
            title,
            null,
            false
        )
    }

    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .lowercase(Locale.ROOT)
        .trim()
}
