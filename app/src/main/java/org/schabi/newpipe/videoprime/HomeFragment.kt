package org.schabi.newpipe.videoprime

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
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
import io.reactivex.rxjava3.disposables.Disposable
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
import org.schabi.newpipe.util.DeviceUtils
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

    /** The one read of the feed in progress: a new one replaces it (an old answer never wins). */
    private var homeLoad: Disposable? = null

    /** What the last read gave: Twitch's answer is drawn with these, never with older ones. */
    private var lastStreams: List<StreamWithState> = emptyList()
    private var lastSubs: List<SubscriptionEntity> = emptyList()

    /**
     * Everything that was loaded, before applying [HomeState.filter]. PulsePlay: it starts with
     * what the Home showed last (coming back from a channel or a search draws it at once).
     */
    private var built: Built = cached
        // who was live a while ago is not shown as live now
        ?.let { if (SystemClock.elapsedRealtime() - cachedAt > LIVE_CACHE_MILLIS) it.withoutLive() else it }
        ?: Built()
    private var state by mutableStateOf(
        if (cached == null) HomeState() else HomeState(loaded = true).filtered(built)
    )

    /** The one build of the cards in progress (off the main thread). */
    private var showJob: Disposable? = null
    private var lastProgressLoad = 0L

    /** The TV's first update is waiting its few seconds (see onViewCreated). */
    private var tvRefreshPending = false

    private val isTv by lazy { DeviceUtils.isTv(requireContext()) }
    private val progressReloadMillis get() = if (isTv) TV_PROGRESS_RELOAD_MILLIS else PROGRESS_RELOAD_MILLIS

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
                            if (now - lastProgressLoad > progressReloadMillis) {
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
        // back from a video (the page went down to the mini player or closed): this screen was
        // there all along and gets no onResume, so it catches up here
        disposables.add(
            PlayerSheetEvents.lowered()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { if (isResumed) catchUp() }
        )
        if (lastSmartRefresh == 0L) {
            // only the subscriptions that are outdated are fetched again, through their RSS
            // feed first (the full load only for those with something new). PulsePlay: on a TV a
            // few seconds later: its weak processor first draws the screen and answers the remote
            // (what is saved shows at once anyway)
            if (isTv) {
                lastSmartRefresh = SystemClock.elapsedRealtime()
                tvRefreshPending = true
                view.postDelayed({
                    if (tvRefreshPending && isAdded && !pulled) refreshFeed(smart = true)
                    tvRefreshPending = false
                }, TV_FIRST_REFRESH_DELAY_MILLIS)
            } else {
                refreshFeed(smart = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        catchUp()
    }

    /**
     * PulsePlay: what was watched shows as watched at once, and if the feed was last checked
     * a while ago the subscriptions are checked again quietly (RSS first; cheap), so that the
     * Home is up to date without pulling down.
     */
    private fun catchUp() {
        // (a full update asked by pulling down may still be running: it is left alone)
        if (!pulled && SystemClock.elapsedRealtime() - lastSmartRefresh > CATCH_UP_MILLIS) {
            refreshFeed(smart = true)
        } else {
            load()
        }
    }

    override fun onDestroyView() {
        // left before the TV's delayed first update ran: it runs on coming back
        if (tvRefreshPending) {
            tvRefreshPending = false
            lastSmartRefresh = 0L
        }
        homeLoad?.dispose()
        homeLoad = null
        showJob?.dispose()
        showJob = null
        disposables.clear()
        StreamPrefetch.cancel()
        super.onDestroyView()
    }

    private fun refreshFeed(smart: Boolean) {
        lastSmartRefresh = SystemClock.elapsedRealtime()
        // the full update asked by pulling down shows the circle; the quiet one on opening does not
        pulled = !smart
        state = state.copy(refreshing = pulled)
        twitchLiveTime = 0
        // Android 12+ refuses services started from the background (a late callback)
        runCatching {
            requireContext().startService(
                Intent(requireContext(), FeedLoadService::class.java)
                    .putExtra(FeedLoadService.EXTRA_GROUP_ID, FeedGroupEntity.GROUP_ALL_ID)
                    .putExtra(FeedLoadService.EXTRA_SMART, smart)
            )
        }
        load()
    }

    private fun load() {
        val context = requireContext().applicationContext
        val feedDatabase = FeedDatabaseManager(context)
        val subscriptions = NewPipeDatabase.getInstance(context).subscriptionDAO()

        // what is saved on the phone shows at once; who is live on Twitch arrives afterwards
        homeLoad?.dispose()
        homeLoad =
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
                // PulsePlay: the cards are built here too, not on the screen's thread (hundreds
                // of videos with their dates, on every reload while the feed updates)
                Loaded(streams, subs, build(context, streams, subs, twitchLive))
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { loaded ->
                        lastStreams = loaded.streams
                        lastSubs = loaded.subs
                        // Subscriptions then draws the channels' pictures from memory
                        AvatarPrefetch.warmFromHome(context, loaded.subs)
                        showJob?.dispose()
                        apply(loaded.built)
                        refreshTwitchLive(loaded.streams, loaded.subs)
                    },
                    { state = state.copy(loaded = true) }
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
                        show(live)
                    },
                    {
                        // tried again in a minute, not on every reload; a list saved long ago is
                        // not shown as live while Twitch cannot be asked
                        if (System.currentTimeMillis() - shownSince > STALE_LIVE_MILLIS) {
                            twitchLive = emptyList()
                            show(twitchLive)
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

    /** Twitch's answer arrived: the cards are built again with the last read of the feed. */
    private fun show(twitchLive: List<StreamInfoItem>) {
        val context = context?.applicationContext ?: return
        val streams = lastStreams
        val subs = lastSubs
        showJob?.dispose()
        showJob = Single.fromCallable { build(context, streams, subs, twitchLive) }
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ apply(it) }, { })
    }

    private fun apply(result: Built) {
        val context = context ?: return
        built = result
        cached = result
        cachedAt = SystemClock.elapsedRealtime()
        // the first unwatched videos open at once when tapped
        StreamPrefetch.prefetch(
            context,
            result.videos.filter { it.progress == null }.map { it.serviceId to it.url }
        )
        state = state.copy(loaded = true, hasSubscriptions = result.hasSubscriptions).filtered(result)
        // the covers further down are ready before the remote or the finger gets there
        CoverPrefetch.warm(context, state.videos)
    }

    private fun setFilter(filter: HomeFilter) {
        state = state.copy(filter = filter).filtered(built)
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
        val infoItem = built.infoItems[item.url] ?: return
        runCatching {
            InfoItemDialog.Builder(requireActivity(), requireContext(), this, infoItem)
                .create()
                .show()
        }
    }

    companion object {
        /** What the Home showed last in this run of the app. */
        private var cached: Built? = null
        private var cachedAt = 0L
        private const val LIVE_CACHE_MILLIS = 5 * 60 * 1000L

        /**
         * When the feed was last checked from here (0 = not in this run of the app): checked
         * on opening and again on coming back if it is older than [CATCH_UP_MILLIS].
         */
        private var lastSmartRefresh = 0L
        private const val CATCH_UP_MILLIS = 10 * 60 * 1000L

        /** Twitch channels that were live the last time they were checked, and when. */
        @Volatile
        private var twitchLive: List<StreamInfoItem> = emptyList()

        @Volatile
        private var twitchLiveTime = 0L
        private const val TWITCH_LIVE_MAX_AGE_MILLIS = 60_000L
        private const val STALE_LIVE_MILLIS = 30 * 60_000L

        private const val PROGRESS_RELOAD_MILLIS = 2_000L

        // PulsePlay: a TV redraws its grid of covers slowly: fewer reloads while updating
        private const val TV_PROGRESS_RELOAD_MILLIS = 6_000L
        private const val TV_FIRST_REFRESH_DELAY_MILLIS = 6_000L
    }
}

/** What a read of the feed gave and the cards made from it. */
private class Loaded(
    val streams: List<StreamWithState>,
    val subs: List<SubscriptionEntity>,
    val built: Built
)

/** The Home's cards before the filter, and the items their menus need. */
private class Built(
    val live: List<HomeItem> = emptyList(),
    val videos: List<HomeItem> = emptyList(),
    val infoItems: Map<String, StreamInfoItem> = emptyMap(),
    val hasSubscriptions: Boolean = true
) {
    fun withoutLive() = Built(emptyList(), videos, infoItems, hasSubscriptions)
}

private fun HomeState.filtered(all: Built): HomeState {
    val keep: (HomeItem) -> Boolean = when (filter) {
        HomeFilter.ALL -> { _ -> true }
        HomeFilter.YOUTUBE -> { item -> item.serviceId == ServiceList.YouTube.serviceId }
        HomeFilter.TWITCH -> { item -> item.serviceId == ServiceList.Twitch.serviceId }
    }
    return copy(live = all.live.filter(keep), videos = all.videos.filter(keep))
}

/** YouTube live streams of the feed older than this are assumed to have ended. */
private const val YOUTUBE_LIVE_MAX_HOURS = 12L

/** Builds the cards (called off the main thread). */
private fun build(
    context: Context,
    streams: List<StreamWithState>,
    subscriptions: List<SubscriptionEntity>,
    twitchLive: List<StreamInfoItem>
): Built {
    val avatars = subscriptions.associate {
        it.url to ImageStrategy.choosePreferredImage(ImageStrategy.dbUrlToImageList(it.avatarUrl))
    }
    val infoItems = HashMap<String, StreamInfoItem>()

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
    return Built(
        live = live.distinctBy { it.url },
        videos = videos.distinctBy { it.url },
        infoItems = infoItems,
        hasSubscriptions = subscriptions.isNotEmpty()
    )
}
