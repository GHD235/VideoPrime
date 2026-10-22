package org.schabi.newpipe.videoprime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList

/** A subscribed channel. */
data class ChannelCard(
    val serviceId: Int,
    val url: String,
    val name: String,
    val avatarUrl: String?,
    /** Whether new videos and live streams of this channel are notified (the bell). */
    val notificationsOn: Boolean = false,
    /** The live stream's URL while the channel is live. */
    val liveUrl: String? = null,
    val liveTitle: String? = null,
    /** Videos published since the last visit to the channel and not watched. */
    val newCount: Int = 0
)

enum class ChannelsFilter { ALL, YOUTUBE, TWITCH }

data class ChannelsState(
    val loading: Boolean = true,
    val filter: ChannelsFilter = ChannelsFilter.ALL,
    val query: String = "",
    val channels: List<ChannelCard> = emptyList()
) {
    val youtube: List<ChannelCard>
        get() = matching(channels.filter { it.serviceId == ServiceList.YouTube.serviceId })
    val twitch: List<ChannelCard>
        get() = matching(channels.filter { it.serviceId == ServiceList.Twitch.serviceId })
    val live: List<ChannelCard> get() = channels.filter { it.liveUrl != null }

    private fun matching(list: List<ChannelCard>): List<ChannelCard> {
        if (query.isBlank()) return list
        return list.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
}

class ChannelsActions(
    val onFilter: (ChannelsFilter) -> Unit,
    val onQuery: (String) -> Unit,
    val onOpen: (ChannelCard) -> Unit,
    val onOpenLive: (ChannelCard) -> Unit,
    val onMore: (ChannelCard) -> Unit,
    val onSearch: () -> Unit,
    val onImport: () -> Unit
)

/**
 * VideoPrime: the subscriptions, split by platform and shown as avatars, with the Twitch
 * channels that are live right now on top.
 */
@Composable
fun ChannelsScreen(state: ChannelsState, actions: ChannelsActions) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.channels.isEmpty() -> Empty(actions)
            else -> Channels(state, actions)
        }
    }
}

@Composable
private fun Channels(state: ChannelsState, actions: ChannelsActions) {
    val showYoutube = state.filter != ChannelsFilter.TWITCH && state.youtube.isNotEmpty()
    val showTwitch = state.filter != ChannelsFilter.YOUTUBE && state.twitch.isNotEmpty()
    val live = state.live.filter {
        state.filter != ChannelsFilter.YOUTUBE || it.serviceId != ServiceList.Twitch.serviceId
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 84.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoPrimeChip(
                    stringResource(R.string.all) + " · " + state.channels.size,
                    state.filter == ChannelsFilter.ALL,
                    null
                ) { actions.onFilter(ChannelsFilter.ALL) }
                VideoPrimeChip(
                    "YouTube" + " · " + state.channels.count { it.serviceId == ServiceList.YouTube.serviceId },
                    state.filter == ChannelsFilter.YOUTUBE,
                    VideoPrimeColors.YouTube
                ) { actions.onFilter(ChannelsFilter.YOUTUBE) }
                VideoPrimeChip(
                    "Twitch" + " · " + state.channels.count { it.serviceId == ServiceList.Twitch.serviceId },
                    state.filter == ChannelsFilter.TWITCH,
                    VideoPrimeColors.Twitch
                ) { actions.onFilter(ChannelsFilter.TWITCH) }
            }
        }
        if (state.channels.size > SEARCH_FROM) {
            item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = actions.onQuery,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.channels_search)) },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }
        if (live.isNotEmpty() && state.query.isBlank()) {
            item(key = "live", span = { GridItemSpan(maxLineSpan) }) {
                LiveRow(live, actions)
            }
        }
        if (showYoutube) {
            item(key = "yt-header", span = { GridItemSpan(maxLineSpan) }) {
                Header("YouTube", VideoPrimeColors.YouTube, state.youtube.size)
            }
            items(state.youtube, key = { "yt:" + it.url }) { channel ->
                Avatar(channel, actions)
            }
        }
        if (showTwitch) {
            item(key = "tw-header", span = { GridItemSpan(maxLineSpan) }) {
                Header("Twitch", VideoPrimeColors.Twitch, state.twitch.size)
            }
            items(state.twitch, key = { "tw:" + it.url }) { channel ->
                Avatar(channel, actions)
            }
        }
    }
}

@Composable
private fun Header(title: String, dot: Color, count: Int) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(dot, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(8.dp))
        Text(
            pluralStringResource(R.plurals.channels_count, count, count),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LiveRow(live: List<ChannelCard>, actions: ChannelsActions) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).background(VideoPrimeColors.Live, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.home_live_now),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(live, key = { "live:" + it.url }) { channel ->
                Column(
                    modifier = Modifier
                        .width(124.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .combinedClickableCompat(
                            onClick = { actions.onOpenLive(channel) },
                            onLongClick = { actions.onMore(channel) }
                        )
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AvatarImage(channel, 64, VideoPrimeColors.Live)
                    Text(
                        stringResource(R.string.home_live_badge),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .background(VideoPrimeColors.Live, RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                    Text(
                        channel.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    channel.liveTitle?.let {
                        Text(
                            it,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** Above this the number on an avatar reads "9+". */
private const val MAX_SHOWN_NEW = 9

@Composable
private fun Avatar(channel: ChannelCard, actions: ChannelsActions) {
    Column(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickableCompat(
                onClick = { actions.onOpen(channel) },
                onLongClick = { actions.onMore(channel) }
            )
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            AvatarImage(channel, 68, if (channel.liveUrl != null) VideoPrimeColors.Live else null)
            if (channel.newCount > 0) {
                Text(
                    if (channel.newCount > MAX_SHOWN_NEW) "$MAX_SHOWN_NEW+" else channel.newCount.toString(),
                    color = VideoPrimeColors.OnNew,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
                        .background(VideoPrimeColors.New, CircleShape)
                        .padding(3.dp)
                        .defaultMinSize(minWidth = 18.dp)
                        .padding(horizontal = 3.dp)
                )
            }
        }
        Text(
            channel.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun AvatarImage(channel: ChannelCard, size: Int, ring: Color?) {
    val ringed = ring?.let {
        Modifier
            .border(2.5.dp, it, CircleShape)
            .padding(4.dp)
    } ?: Modifier
    Box(
        modifier = ringed
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (channel.avatarUrl != null) {
            SmoothImage(model = channel.avatarUrl, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                channel.name.take(1).uppercase(),
                fontSize = (size / 2.6).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun Empty(actions: ChannelsActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.channels_empty),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = actions.onSearch,
            modifier = Modifier.padding(top = 20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VideoPrimeColors.Accent,
                contentColor = Color.White
            )
        ) { Text(stringResource(R.string.home_find_channels), fontWeight = FontWeight.Bold) }
        OutlinedButton(onClick = actions.onImport, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.channels_import))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: () -> Unit) = combinedClickable(onClick = onClick, onLongClick = onLongClick)

private const val SEARCH_FROM = 12
