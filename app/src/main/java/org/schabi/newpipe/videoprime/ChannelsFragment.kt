package org.schabi.newpipe.videoprime

import android.content.Context
import android.os.Bundle
import android.util.Log
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
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.RecentUpload
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
        val feed = NewPipeDatabase.getInstance(requireContext()).feedDAO()
        disposables.add(
            Flowable.combineLatest(
                manager.subscriptions(),
                // what each channel has new is counted from the unwatched videos of the last weeks
                // (a premiere scheduled for later is not new yet)
                feed.getRecentUnwatched(OffsetDateTime.now().minusDays(RECENT_DAYS), OffsetDateTime.now())
            ) { subscriptions, uploads -> subscriptions to uploads }
                // the feed is written all the time while it updates: the first result shows at
                // once and then at most one redraw every UPDATE_DEBOUNCE_MILLIS (debounce waited
                // for a quiet moment, and on opening the app there was none)
                .throttleLatest(UPDATE_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS, true)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ (subscriptions, uploads) ->
                    latestSubscriptions = subscriptions
                    latestUploads = uploads
                    show(subscriptions)
                }, { state = state.copy(loading = false) })
        )
    }

    override fun onResume() {
        super.onResume()
        // back from a channel: its number goes away
        latestSubscriptions?.let { show(it) }
    }

    private var latestSubscriptions: List<SubscriptionEntity>? = null
    private var latestUploads: List<RecentUpload> = emptyList()

    /** How many videos each channel published since its last visit. */
    private fun newCounts(subscriptions: List<SubscriptionEntity>): Map<Long, Int> {
        val context = context ?: return emptyMap()
        val since = subscriptions.associate { entity ->
            entity.uid to ChannelVisits.lastVisit(context, entity.serviceId, entity.url.orEmpty())
        }
        return latestUploads
            .filter { upload ->
                val published = upload.uploadDate?.toInstant()?.toEpochMilli() ?: return@filter false
                published > (since[upload.subscriptionId] ?: Long.MAX_VALUE)
            }
            .groupingBy { it.subscriptionId }
            .eachCount()
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
        val counts = newCounts(subscriptions)
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
                    notificationsOn = entity.notificationMode == NotificationMode.ENABLED,
                    newCount = counts[entity.uid] ?: 0
                )
            }
            // the channels with something new first, then by name
            .sortedWith(compareBy({ it.newCount == 0 }, { it.name.lowercase() }))
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

    /** The channel's menu: the bell (tap to turn it on or off) and unsubscribe. */
    private fun showMenu(channel: ChannelCard) {
        val context = requireContext()
        // the switch can be flipped more than once while the sheet is open
        var notificationsOn = channel.notificationsOn
        val platform = if (channel.serviceId == ServiceList.Twitch.serviceId) "Twitch" else "YouTube"
        ActionSheet.show(
            context,
            ActionSheet.Layout.ROWS,
            channel.name,
            platform,
            channel.avatarUrl,
            true,
            null,
            listOf(
                SheetAction(
                    getString(R.string.videoprime_channel_notifications),
                    R.drawable.ic_notifications,
                    toggled = channel.notificationsOn
                ) {
                    notificationsOn = !notificationsOn
                    setNotifications(channel, notificationsOn)
                },
                SheetAction(
                    getString(R.string.unsubscribe),
                    R.drawable.ic_person_remove,
                    danger = true,
                    confirm = getString(R.string.videoprime_channel_unsubscribe_confirm)
                ) { unsubscribe(channel) }
            )
        )
    }

    private fun setNotifications(channel: ChannelCard, enabled: Boolean) {
        val app = requireContext().applicationContext
        disposables.add(
            manager.updateNotificationMode(
                channel.serviceId,
                channel.url,
                if (enabled) NotificationMode.ENABLED else NotificationMode.DISABLED
            )
                // one after the other: two quick flips must end in the last one
                .subscribeOn(Schedulers.single())
                // the instant notifications follow the bell right away
                .subscribe(
                    { InstantPush.syncInBackground(app) },
                    { Log.w(TAG, "Could not change the notifications", it) }
                )
        )
    }

    private fun unsubscribe(channel: ChannelCard) {
        val context = requireContext().applicationContext
        disposables.add(
            manager.deleteSubscription(channel.serviceId, channel.url).subscribe(
                { Toast.makeText(context, R.string.channel_unsubscribed, Toast.LENGTH_SHORT).show() },
                { Log.w(TAG, "Could not unsubscribe", it) }
            )
        )
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
        private const val RECENT_DAYS = 30L
        private const val UPDATE_DEBOUNCE_MILLIS = 400L
        private val LOGIN = Regex("twitch\\.tv/([^/?#]+)")
        private const val TAG = "ChannelsFragment"
    }
}
