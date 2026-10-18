package org.schabi.newpipe.videoprime

import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import org.schabi.newpipe.util.external_communication.ShareUtils

/**
 * VideoPrime: state and actions of the free-to-air TV channels of Spain, shown by [TvScreen].
 * Channels with an HLS stream play in the app; the others open on YouTube, Twitch or the web.
 */
class TvController(
    private val fragment: Fragment,
    private val disposables: CompositeDisposable
) {
    var state by mutableStateOf(TvState())
        private set

    private var allChannels: List<TvChannel> = emptyList()
    private var requested = false

    /** Load the channel list the first time the TV is shown. */
    fun loadIfNeeded() {
        if (!requested) {
            load()
        }
    }

    fun load() {
        requested = true
        state = state.copy(loading = true, error = false)
        disposables.add(
            Single.fromCallable { TvChannelList.getChannels() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { list ->
                        allChannels = list
                        state = filter(
                            state.copy(loading = false, groups = list.map { it.group }.distinct())
                        )
                    },
                    { state = state.copy(loading = false, error = true) }
                )
        )
    }

    fun setQuery(query: String) {
        state = filter(state.copy(query = query))
    }

    fun setGroup(group: String?) {
        state = filter(state.copy(group = group))
    }

    private fun filter(newState: TvState): TvState {
        val query = normalize(newState.query)
        return newState.copy(
            channels = allChannels
                .filter { newState.group == null || it.group == newState.group }
                .filter { query.isEmpty() || normalize(it.name).contains(query) }
                .map { TvItem(it.id, it.name, it.group, it.logo) }
        )
    }

    fun open(item: TvItem) {
        val channel = allChannels.firstOrNull { it.id == item.id } ?: return
        val option = channel.bestOption
        when (option.format) {
            Format.M3U8 -> openStream(ServiceList.TV.serviceId, channel.url, channel.name)
            Format.YOUTUBE -> openYoutubeLive(channel, option.url)
            Format.WEB -> {
                val twitchUrl = TvParsingHelper.twitchUrlFromPlayer(option.url)
                if (twitchUrl != null) {
                    openStream(ServiceList.Twitch.serviceId, twitchUrl, channel.name)
                } else {
                    ShareUtils.openUrlInBrowser(fragment.requireContext(), channel.web ?: option.url)
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
        val context = fragment.context ?: return
        Toast.makeText(
            context,
            context.getString(R.string.tv_not_live, channel.name),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun openStream(serviceId: Int, url: String, title: String) {
        if (!fragment.isAdded) return
        NavigationHelper.openVideoDetailFragment(
            fragment.requireContext(),
            fragment.requireActivity().supportFragmentManager,
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
