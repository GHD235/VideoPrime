package org.schabi.newpipe.videoprime

import android.content.Context
import android.content.Intent
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
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.twitch.TwitchLiveChannels
import org.schabi.newpipe.extractor.services.twitch.linkHandler.TwitchChannelLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.info_list.dialog.InfoItemDialog
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.feed.service.FeedEventManager
import org.schabi.newpipe.local.feed.service.FeedLoadService
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * VideoPrime: the "Home" section. New videos of the subscribed YouTube and Twitch channels,
 * with the subscribed channels that are live right now on top.
 */
class HomeFragment : Fragment() {
    private val disposables = CompositeDisposable()
    private val tv = TvController(this, disposables)
    private var state by mutableStateOf(HomeState())

    /** Everything that was loaded, before applying [HomeState.filter]. */
    private var allLive: List<HomeItem> = emptyList()
    private var allVideos: List<HomeItem> = emptyList()
    private val infoItems = HashMap<String, StreamInfoItem>()
    private var lastProgressLoad = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val dark = !ThemeHelper.isLightThemeSelected(requireContext())
        setContent {
            VideoPrimeTheme(dark = dark) {
                HomeScreen(
                    state = state,
                    onFilter = ::setFilter,
                    // pulling down loads every channel in full
                    onRefresh = { refreshFeed(smart = false) },
                    onOpen = ::open,
                    onMore = ::showMenu,
                    onFindChannels = {
                        NavigationHelper.openSearchFragment(
                            requireActivity().supportFragmentManager,
                            ServiceList.YouTube.serviceId,
                            ""
                        )
                    },
                    tvContent = {
                        TvScreen(
                            state = tv.state,
                            onQuery = tv::setQuery,
                            onGroup = tv::setGroup,
                            onOpen = tv::open,
                            onRetry = tv::load
                        )
                    }
                )
            }
        }
    }

    /** The user pulled down to update: the circle shows until it ends. */
    private var pulled = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        disposables.add(
            FeedEventManager.events()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { event ->
                    when (event) {
                        is FeedEventManager.Event.ProgressEvent -> {
                            // VideoPrime: the update on opening runs quietly; only pulling down
                            // shows the circle
                            state = state.copy(refreshing = pulled)
                            // new videos show up as channels are updated, not only at the end
                            val now = System.currentTimeMillis()
                            if (now - lastProgressLoad > PROGRESS_RELOAD_MILLIS) {
                                lastProgressLoad = now
                                load()
                            }
                        }

                        is FeedEventManager.Event.SuccessResultEvent -> {
                            pulled = false
                            state = state.copy(refreshing = false, failed = false)
                            load()
                        }

                        is FeedEventManager.Event.ErrorResultEvent -> {
                            // the videos already there stay; asked by pulling down, it says so
                            if (pulled) {
                                context?.let {
                                    Toast.makeText(it, R.string.videoprime_home_update_failed, Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                            pulled = false
                            state = state.copy(refreshing = false, failed = true)
                        }

                        else -> {
                            pulled = false
                            state = state.copy(refreshing = false)
                        }
                    }
                }
        )
        if (!refreshedThisSession) {
            refreshedThisSession = true
            // only the subscriptions that are outdated are fetched again, through their RSS
            // feed first (the full load only for those with something new)
            refreshFeed(smart = true)
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onDestroyView() {
        disposables.clear()
        tv.onViewDestroyed()
        super.onDestroyView()
    }

    private fun refreshFeed(smart: Boolean) {
        // the full update asked by pulling down shows the circle; the quiet one on opening does not
        pulled = !smart
        state = state.copy(refreshing = pulled)
        twitchLiveTime = 0
        requireContext().startService(
            Intent(requireContext(), FeedLoadService::class.java)
                .putExtra(FeedLoadService.EXTRA_GROUP_ID, FeedGroupEntity.GROUP_ALL_ID)
                .putExtra(FeedLoadService.EXTRA_SMART, smart)
        )
        load()
    }

    private fun load() {
        val context = requireContext().applicationContext
        val feedDatabase = FeedDatabaseManager(context)
        val subscriptions = NewPipeDatabase.getInstance(context).subscriptionDAO()

        // what is saved on the phone shows at once; who is live on Twitch arrives afterwards
        disposables.add(
            Single.zip(
                feedDatabase.getStreams(FeedGroupEntity.GROUP_ALL_ID, true, true, false)
                    .defaultIfEmpty(emptyList()),
                subscriptions.getAll().firstOrError()
            ) { streams, subs ->
                // who was live at the last check (here or in the background) shows at once;
                // Twitch is asked again in a moment if it is more than a minute old
                HomeLiveCache.read(context, twitchLiveTime)?.let { (live, savedAt) ->
                    twitchLive = live
                    twitchLiveTime = savedAt
                }
                streams to subs
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { (streams, subs) ->
                        show(streams, subs, twitchLive)
                        refreshTwitchLive(streams, subs)
                    },
                    { state = state.copy(loaded = true) }
                )
        )
    }

    /** Asks Twitch who is live, at most once every [TWITCH_LIVE_MAX_AGE_MILLIS]. */
    private fun refreshTwitchLive(streams: List<StreamWithState>, subs: List<SubscriptionEntity>) {
        if (System.currentTimeMillis() - twitchLiveTime < TWITCH_LIVE_MAX_AGE_MILLIS) return
        val shownSince = twitchLiveTime
        twitchLiveTime = System.currentTimeMillis()
        val appContext = requireContext().applicationContext
        disposables.add(
            Single.fromCallable { fetchTwitchLive(subs).also { HomeLiveCache.write(appContext, it) } }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { live ->
                        twitchLive = live
                        show(streams, subs, live)
                    },
                    {
                        // tried again in a minute, not on every reload; a list saved long ago is
                        // not shown as live while Twitch cannot be asked
                        if (System.currentTimeMillis() - shownSince > STALE_LIVE_MILLIS) {
                            twitchLive = emptyList()
                            show(streams, subs, twitchLive)
                        }
                    }
                )
        )
    }

    private fun fetchTwitchLive(subscriptions: List<SubscriptionEntity>): List<StreamInfoItem> {
        val logins = subscriptions
            .filter { it.serviceId == ServiceList.Twitch.serviceId }
            .mapNotNull { runCatching { TwitchChannelLinkHandlerFactory.getInstance().getId(it.url) }.getOrNull() }
        return if (logins.isEmpty()) emptyList() else TwitchLiveChannels.fetch(logins)
    }

    private fun show(
        streams: List<StreamWithState>,
        subscriptions: List<SubscriptionEntity>,
        twitchLive: List<StreamInfoItem>
    ) {
        val context = context ?: return
        val avatars = subscriptions.associate {
            it.url to ImageStrategy.choosePreferredImage(ImageStrategy.dbUrlToImageList(it.avatarUrl))
        }
        infoItems.clear()

        val recentLimit = OffsetDateTime.now().minusHours(YOUTUBE_LIVE_MAX_HOURS)
        val live = ArrayList<HomeItem>()
        twitchLive.forEach { item ->
            infoItems[item.url] = item
            val avatar = ImageStrategy.choosePreferredImage(item.uploaderAvatars)
            live.add(
                HomeItem(
                    serviceId = item.serviceId,
                    url = item.url,
                    title = item.name,
                    uploader = item.uploaderName,
                    thumbnailUrl = ImageStrategy.choosePreferredImage(item.thumbnails),
                    avatarUrl = avatar,
                    isLive = true,
                    badge = Localization.shortWatchingCount(context, item.viewCount),
                    meta = item.uploaderName,
                    progress = null
                )
            )
        }

        val videos = ArrayList<HomeItem>()
        for (streamWithState in streams) {
            val stream = streamWithState.stream
            val isLive = stream.streamType == StreamType.LIVE_STREAM ||
                stream.streamType == StreamType.AUDIO_LIVE_STREAM
            if (isLive && stream.serviceId == ServiceList.Twitch.serviceId) {
                continue // the live state of Twitch channels comes from twitchLive
            }
            if (isLive && (stream.uploadDate == null || stream.uploadDate!!.isBefore(recentLimit))) {
                continue // probably ended long ago
            }

            val infoItem = stream.toStreamInfoItem()
            infoItems[stream.url] = infoItem
            val avatar = avatars[stream.uploaderUrl]
            val date = stream.uploadDate?.let { Localization.relativeTime(it) }
                ?: stream.textualUploadDate
            val homeItem = HomeItem(
                serviceId = stream.serviceId,
                url = stream.url,
                title = stream.title,
                uploader = stream.uploader,
                thumbnailUrl = ImageStrategy.choosePreferredImage(
                    ImageStrategy.dbUrlToImageList(stream.thumbnailUrl)
                ),
                avatarUrl = avatar,
                isLive = isLive,
                badge = when {
                    isLive -> stream.viewCount?.let { Localization.shortWatchingCount(context, it) }
                    stream.duration > 0 -> Localization.getDurationString(stream.duration)
                    else -> null
                },
                meta = listOfNotNull(stream.uploader, date).joinToString(" · "),
                progress = streamWithState.stateProgressMillis
                    ?.takeIf { it > 0 && stream.duration > 0 }
                    ?.let { (it / 1000f / stream.duration).coerceIn(0f, 1f) }
            )
            if (isLive) live.add(homeItem) else videos.add(homeItem)
        }

        // one entry per video: a collaboration can be in the feed of two channels, and the lists
        // use the address as their key
        allLive = live.distinctBy { it.url }
        allVideos = videos.distinctBy { it.url }
        state = state.copy(loaded = true, hasSubscriptions = subscriptions.isNotEmpty())
        applyFilter()
    }

    private fun setFilter(filter: HomeFilter) {
        state = state.copy(filter = filter)
        if (filter == HomeFilter.TV) {
            tv.loadIfNeeded()
        } else {
            applyFilter()
        }
    }

    private fun applyFilter() {
        val keep: (HomeItem) -> Boolean = when (state.filter) {
            HomeFilter.ALL -> { _ -> true }
            HomeFilter.YOUTUBE -> { item -> item.serviceId == ServiceList.YouTube.serviceId }
            HomeFilter.TWITCH -> { item -> item.serviceId == ServiceList.Twitch.serviceId }
            HomeFilter.TV -> return
        }
        state = state.copy(live = allLive.filter(keep), videos = allVideos.filter(keep))
    }

    private fun open(item: HomeItem) {
        NavigationHelper.openVideoDetailFragment(
            requireContext(),
            requireActivity().supportFragmentManager,
            item.serviceId,
            item.url,
            item.title,
            null,
            false
        )
    }

    private fun showMenu(item: HomeItem) {
        val infoItem = infoItems[item.url] ?: return
        runCatching {
            InfoItemDialog.Builder(requireActivity(), requireContext(), this, infoItem)
                .create()
                .show()
        }
    }

    companion object {
        /** YouTube live streams of the feed older than this are assumed to have ended. */
        private const val YOUTUBE_LIVE_MAX_HOURS = 12L

        /** The feed is refreshed automatically once per app start. */
        private var refreshedThisSession = false

        /** Twitch channels that were live the last time they were checked, and when. */
        @Volatile
        private var twitchLive: List<StreamInfoItem> = emptyList()

        @Volatile
        private var twitchLiveTime = 0L
        private const val TWITCH_LIVE_MAX_AGE_MILLIS = 60_000L
        private const val STALE_LIVE_MILLIS = 30 * 60_000L

        private const val PROGRESS_RELOAD_MILLIS = 2_000L
    }
}
