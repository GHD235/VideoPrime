package org.schabi.newpipe.videoprime

import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * PulsePlay (TV): the video's details over the video itself, in a floating panel on the right
 * (like the TV's own quick settings), instead of leaving full screen for the small page: title,
 * channel with Subscribe and the bell, views/likes/date, the description and what comes next.
 * Opened with "down" while the controls are hidden, or with the info button; Back closes it.
 */
class PlayerInfoPanel(private val player: Player, private val root: ViewGroup) {

    private var view: ComposeView? = null
    private var shown = mutableStateOf(false)

    /** The player's controls under the panel, kept out of the remote's reach meanwhile. */
    private val blocked = mutableListOf<Pair<ViewGroup, Int>>()

    val isShowing: Boolean
        get() = view != null

    private var shownUrl: String? = null

    /** Whether the open panel is about the video at [url]. */
    fun isAbout(url: String?): Boolean = view == null || url == shownUrl

    fun show(): Boolean {
        if (view != null) return true
        val info = player.currentStreamInfo.orElse(null) ?: return false
        val queue = player.playQueue
        val inQueue = queue?.streams?.map { it.url }?.toSet().orEmpty()
        val related = info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .filter { it.url != info.url && it.url !in inQueue && !it.isShortFormContent }
            .distinctBy { it.url }
        val next = WatchedUrls.leaveOut(related, WatchedUrls.cached(info)) { it.url }.take(MAX_ITEMS)
        shownUrl = info.url
        val visible = mutableStateOf(false)
        shown = visible
        val compose = ComposeView(root.context).apply {
            layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setContent {
                VideoPrimeTheme(dark = true) {
                    LaunchedEffect(Unit) { visible.value = true }
                    Panel(info, next, visible.value) { play(it) }
                }
            }
        }
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i) as? ViewGroup ?: continue
            blocked += child to child.descendantFocusability
            child.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        root.addView(compose)
        compose.bringToFront()
        view = compose
        return true
    }

    /** Slides the panel out, then takes it away. */
    fun hide() {
        val current = view ?: return
        view = null
        shown.value = false
        blocked.forEach { (group, focusability) -> group.descendantFocusability = focusability }
        blocked.clear()
        current.postDelayed({ root.removeView(current) }, EXIT_MILLIS.toLong())
    }

    /** At once (the player is going away). */
    fun remove() {
        val current = view
        hide()
        current?.let { root.removeView(it) }
    }

    private fun play(item: StreamInfoItem) {
        hide()
        val queue = player.playQueue ?: return
        val queueItem = SinglePlayQueue(item).item ?: return
        queue.append(listOf(queueItem))
        player.selectQueueItem(queueItem)
    }

    @Composable
    private fun Panel(info: StreamInfo, next: List<StreamInfoItem>, visible: Boolean, onPlay: (StreamInfoItem) -> Unit) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            val width = (maxWidth * 0.38f).coerceIn(340.dp, 520.dp)
            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(tween(ENTER_MILLIS)) { it / 3 } + fadeIn(tween(ENTER_MILLIS)),
                exit = slideOutHorizontally(tween(EXIT_MILLIS)) { it / 3 } + fadeOut(tween(EXIT_MILLIS))
            ) {
                Content(info, next, onPlay, Modifier.width(width))
            }
        }
    }

    @Composable
    private fun Content(info: StreamInfo, next: List<StreamInfoItem>, onPlay: (StreamInfoItem) -> Unit, modifier: Modifier) {
        val context = LocalContext.current
        val shape = RoundedCornerShape(28.dp)
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(FOCUS_DELAY_MILLIS)
            runCatching { first.requestFocus() }
        }
        val description = remember(info) { descriptionText(info.description) }
        val stats = remember(info) { stats(context, info) }
        LazyColumn(
            modifier = modifier
                .fillMaxHeight()
                .padding(vertical = 24.dp, horizontal = 24.dp)
                .clip(shape)
                .background(PanelBackground)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), shape),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    info.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 25.sp
                )
            }
            item { Channel(info, first) }
            if (stats.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        stats.forEach { (value, label) ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(StatBackground)
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                                if (label.isNotEmpty()) {
                                    Text(label, fontSize = 11.sp, color = TextMuted, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            // the description in paragraphs that the remote can step through (a Text alone
            // cannot take the focus, and the list would not scroll past it)
            items(description) { paragraph ->
                Text(
                    paragraph,
                    fontSize = 14.sp,
                    color = TextBody,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRing(RoundedCornerShape(10.dp))
                        .clickable { }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            if (next.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.videoprime_up_next).uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = VideoPrimeColors.New,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(next, key = { it.url }) { item -> NextRow(item) { onPlay(item) } }
            }
        }
    }

    @Composable
    private fun Channel(info: StreamInfo, first: FocusRequester) {
        val context = LocalContext.current
        val url = info.uploaderUrl
        var subscription by remember(url) { mutableStateOf<SubscriptionEntity?>(null) }
        var known by remember(url) { mutableStateOf(false) }
        val manager = remember { SubscriptionManager(context) }
        if (!url.isNullOrEmpty()) {
            DisposableEffect(url) {
                val disposable = manager.subscriptionTable().getSubscriptionFlowable(info.serviceId, url)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ list ->
                        subscription = list.firstOrNull()
                        known = true
                    }, { })
                onDispose { disposable.dispose() }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmoothImage(
                    model = ImageStrategy.choosePreferredImage(info.uploaderAvatars),
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                    crossfade = false
                )
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        info.uploaderName.orEmpty(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (info.uploaderSubscriberCount >= 0) {
                        Text(
                            Localization.shortSubscriberCount(context, info.uploaderSubscriberCount),
                            fontSize = 12.sp,
                            color = TextMuted,
                            maxLines = 1
                        )
                    }
                }
            }
            if (!url.isNullOrEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val current = subscription
                    Pill(
                        text = stringResource(
                            if (current != null) R.string.subscribed_button_title else R.string.subscribe_button_title
                        ),
                        filled = current == null,
                        modifier = Modifier.focusRequester(first)
                    ) {
                        if (!known) return@Pill
                        val change = if (current != null) {
                            Completable.fromAction { manager.deleteSubscription(current) }
                        } else {
                            val entity = SubscriptionEntity().apply {
                                serviceId = info.serviceId
                                this.url = url
                                name = info.uploaderName
                                avatarUrl = ImageStrategy.imageListToDbUrl(info.uploaderAvatars)
                                subscriberCount = info.uploaderSubscriberCount.takeIf { it >= 0 }
                            }
                            Completable.fromAction { manager.insertSubscription(entity) }
                        }
                        change.subscribeOn(Schedulers.io()).subscribe({ }, { })
                    }
                    if (current != null) {
                        val on = current.notificationMode == NotificationMode.ENABLED
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .focusRing(CircleShape)
                                .clip(CircleShape)
                                .background(if (on) VideoPrimeColors.Accent else StatBackground)
                                .clickable {
                                    manager.updateNotificationMode(
                                        info.serviceId,
                                        url,
                                        if (on) NotificationMode.DISABLED else NotificationMode.ENABLED
                                    ).subscribeOn(Schedulers.io()).subscribe({ }, { })
                                }
                        ) {
                            Icon(
                                painterResource(if (on) R.drawable.ic_notifications else R.drawable.ic_notifications_off),
                                contentDescription = stringResource(
                                    if (on) R.string.videoprime_channel_bell_on else R.string.videoprime_channel_bell_off
                                ),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Pill(text: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
        val shape = RoundedCornerShape(22.dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .focusRing(shape)
                .clip(shape)
                .background(if (filled) Color.White else VideoPrimeColors.Accent)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 11.dp)
        ) {
            Text(
                text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (filled) Color.Black else Color.White,
                maxLines = 1
            )
        }
    }

    @Composable
    private fun NextRow(item: StreamInfoItem, onClick: () -> Unit) {
        val shape = RoundedCornerShape(14.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .focusRing(shape)
                .clip(shape)
                .clickable(onClick = onClick)
                .padding(4.dp)
        ) {
            Box(modifier = Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
                SmoothImage(
                    model = ImageStrategy.choosePreferredImage(item.thumbnails),
                    modifier = Modifier.fillMaxSize()
                )
                if (item.duration > 0) {
                    Text(
                        Localization.getDurationString(item.duration),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Text(
                    item.uploaderName.orEmpty(),
                    fontSize = 12.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }

    private companion object {
        const val MAX_ITEMS = 20
        const val ENTER_MILLIS = 220
        const val EXIT_MILLIS = 180
        const val FOCUS_DELAY_MILLIS = 120L
        const val MAX_PARAGRAPHS = 60
        val PanelBackground = Color(0xF21B1826)
        val StatBackground = Color(0x33A78BFA)
        val TextBody = Color(0xFFE4E1EC)
        val TextMuted = Color(0xFFA8A3B8)

        fun descriptionText(description: Description?): List<String> {
            val content = description?.content.orEmpty()
            if (content.isBlank()) return emptyList()
            val text = if (description?.type == Description.Type.HTML) {
                HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            } else {
                content
            }
            return text.split(Regex("\n\\s*\n"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(MAX_PARAGRAPHS)
        }

        fun stats(context: android.content.Context, info: StreamInfo): List<Pair<String, String>> {
            val stats = ArrayList<Pair<String, String>>()
            if (info.viewCount >= 0) {
                stats += Localization.shortCount(context, info.viewCount) to context.getString(R.string.pulseplay_stat_views)
            }
            if (info.likeCount >= 0) {
                stats += Localization.shortCount(context, info.likeCount) to context.getString(R.string.pulseplay_stat_likes)
            }
            info.uploadDate?.let { date ->
                runCatching {
                    val day = date.offsetDateTime().toLocalDate()
                    stats += day.format(DateTimeFormatter.ofPattern("d MMM")) to day.year.toString()
                }
            }
            return stats
        }
    }
}
