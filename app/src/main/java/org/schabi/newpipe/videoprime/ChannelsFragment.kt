package org.schabi.newpipe.videoprime

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.twitch.TwitchLiveChannels
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.subscription.SubscriptionFragment
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.local.subscription.SubscriptionsImportExportHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * VideoPrime: the "Subscriptions" section. The subscribed channels by platform, as avatars, with
 * the Twitch channels that are live now on top. NewPipe's channel groups are in the menu.
 */
class ChannelsFragment : Fragment() {
    private val disposables = CompositeDisposable()
    private var state by mutableStateOf(ChannelsState())
    private lateinit var manager: SubscriptionManager
    private lateinit var importExport: SubscriptionsImportExportHelper

    init {
        @Suppress("DEPRECATION")
        setHasOptionsMenu(true)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        manager = SubscriptionManager(context)
        importExport = SubscriptionsImportExportHelper(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val dark = !ThemeHelper.isLightThemeSelected(requireContext())
        val actions = ChannelsActions(
            onFilter = { state = state.copy(filter = it) },
            onQuery = { state = state.copy(query = it) },
            onOpen = { channel ->
                NavigationHelper.openChannelFragment(
                    requireActivity().supportFragmentManager,
                    channel.serviceId,
                    channel.url,
                    channel.name
                )
            },
            onOpenLive = { channel ->
                NavigationHelper.openVideoDetailFragment(
                    requireContext(),
                    requireActivity().supportFragmentManager,
                    channel.serviceId,
                    channel.liveUrl ?: channel.url,
                    channel.liveTitle ?: channel.name,
                    null,
                    false
                )
            },
            onMore = ::showMenu,
            onSearch = {
                NavigationHelper.openSearchFragment(
                    requireActivity().supportFragmentManager,
                    ServiceHelper.getSelectedServiceId(requireContext()),
                    ""
                )
            },
            onImport = ::showImport
        )
        setContent { VideoPrimeTheme(dark = dark) { ChannelsScreen(state, actions) } }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        disposables.add(
            manager.subscriptions()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ show(it) }, { state = state.copy(loading = false) })
        )
    }

    override fun onDestroyView() {
        disposables.clear()
        super.onDestroyView()
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        @Suppress("DEPRECATION")
        super.onCreateOptionsMenu(menu, inflater)
        menu.add(R.string.channels_import).setOnMenuItemClickListener {
            showImport()
            true
        }
        menu.add(R.string.export_to).setOnMenuItemClickListener {
            importExport.onExportSelected()
            true
        }
        menu.add(R.string.channels_groups).setOnMenuItemClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_holder, SubscriptionFragment())
                .addToBackStack(null)
                .commit()
            true
        }
    }

    private fun show(subscriptions: List<SubscriptionEntity>) {
        val channels = subscriptions
            .filter { it.url != null }
            .map { entity ->
                ChannelCard(
                    serviceId = entity.serviceId,
                    url = entity.url!!,
                    name = entity.name ?: "",
                    avatarUrl = ImageStrategy.choosePreferredImage(
                        ImageStrategy.dbUrlToImageList(entity.avatarUrl)
                    ),
                    notificationsOn = entity.notificationMode == NotificationMode.ENABLED
                )
            }
            .sortedBy { it.name.lowercase() }
        val live = state.channels.filter { it.liveUrl != null }.associateBy { it.url }
        state = state.copy(
            loading = false,
            channels = channels.map { channel ->
                live[channel.url]?.let {
                    channel.copy(liveUrl = it.liveUrl, liveTitle = it.liveTitle)
                } ?: channel
            }
        )
        fetchLive(channels)
    }

    /** Asks Twitch which of the subscribed channels are live right now. */
    private fun fetchLive(channels: List<ChannelCard>) {
        val twitch = channels.filter { it.serviceId == ServiceList.Twitch.serviceId }
        val logins = twitch.mapNotNull { login(it.url) }
        if (logins.isEmpty()) return
        disposables.add(
            Single.fromCallable { TwitchLiveChannels.fetch(logins) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { streams -> applyLive(streams) },
                    { /* without the live state the channels still show */ }
                )
        )
    }

    private fun applyLive(streams: List<StreamInfoItem>) {
        val byLogin = streams.associateBy { login(it.uploaderUrl ?: it.url) }
        state = state.copy(
            channels = state.channels.map { channel ->
                val stream = if (channel.serviceId == ServiceList.Twitch.serviceId) {
                    byLogin[login(channel.url)]
                } else {
                    null
                }
                channel.copy(liveUrl = stream?.url, liveTitle = stream?.name)
            }
        )
    }

    private fun login(url: String?): String? = url?.let { LOGIN.find(it)?.groupValues?.get(1)?.lowercase() }

    private fun showMenu(channel: ChannelCard) {
        val context = requireContext()
        AlertDialog.Builder(context)
            .setTitle(channel.name)
            .setItems(
                arrayOf(
                    getString(
                        if (channel.notificationsOn) {
                            R.string.videoprime_channel_notifications_off
                        } else {
                            R.string.videoprime_channel_notifications_on
                        }
                    ),
                    getString(R.string.unsubscribe)
                )
            ) { _, which ->
                when (which) {
                    0 -> disposables.add(
                        manager.updateNotificationMode(
                            channel.serviceId,
                            channel.url,
                            if (channel.notificationsOn) NotificationMode.DISABLED else NotificationMode.ENABLED
                        )
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({
                                Toast.makeText(
                                    context,
                                    if (channel.notificationsOn) {
                                        R.string.videoprime_channel_notifications_off_done
                                    } else {
                                        R.string.videoprime_channel_notifications_on_done
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                            }, { })
                    )

                    1 -> disposables.add(
                        manager.deleteSubscription(channel.serviceId, channel.url).subscribe {
                            Toast.makeText(
                                context,
                                R.string.channel_unsubscribed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
            .show()
    }

    /** Import from a previous VideoPrime/NewPipe export or from YouTube (Google Takeout). */
    private fun showImport() {
        val options = arrayOf(getString(R.string.previous_export), "YouTube")
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.import_from)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> importExport.onImportPreviousSelected()

                    1 -> NavigationHelper.openSubscriptionsImportFragment(
                        requireActivity().supportFragmentManager,
                        ServiceList.YouTube.serviceId
                    )
                }
            }
            .show()
    }

    companion object {
        private val LOGIN = Regex("twitch\\.tv/([^/?#]+)")
    }
}
