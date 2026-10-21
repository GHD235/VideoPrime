package org.schabi.newpipe.videoprime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.schabi.newpipe.R

/** One video or broadcast of the home screen. */
data class HomeItem(
    val serviceId: Int,
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val avatarUrl: String?,
    val isLive: Boolean,
    /** "18:42" for videos, "48 mil viendo" for live streams. */
    val badge: String?,
    /** "Tito Kolde · hace 2 horas". */
    val meta: String,
    /** Watched fraction (0..1), or null if never played. */
    val progress: Float?
)

enum class HomeFilter { ALL, YOUTUBE, TWITCH }

data class HomeState(
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val hasSubscriptions: Boolean = true,
    val filter: HomeFilter = HomeFilter.ALL,
    val live: List<HomeItem> = emptyList(),
    val videos: List<HomeItem> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onFilter: (HomeFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (HomeItem) -> Unit,
    onMore: (HomeItem) -> Unit,
    onFindChannels: () -> Unit
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "filters") { Filters(state.filter, onFilter) }

            if (state.loaded && !state.hasSubscriptions) {
                item(key = "empty") {
                    EmptyMessage(
                        text = stringResource(R.string.home_no_subscriptions),
                        action = stringResource(R.string.home_find_channels),
                        onAction = onFindChannels
                    )
                }
                return@LazyColumn
            }

            if (state.live.isNotEmpty()) {
                item(key = "live-title") { SectionTitle(stringResource(R.string.home_live_now)) }
                item(key = "live") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.live, key = { it.url }) { LiveCard(it, onOpen, onMore) }
                    }
                }
            }

            item(key = "videos-title") {
                SectionTitle(stringResource(R.string.home_from_subscriptions))
            }
            if (state.loaded && state.videos.isEmpty()) {
                item(key = "no-videos") {
                    EmptyMessage(
                        text = stringResource(R.string.home_no_videos),
                        action = stringResource(R.string.home_update),
                        onAction = onRefresh
                    )
                }
            }
            items(state.videos, key = { it.url }) { VideoCard(it, onOpen, onMore) }
        }
    }
}

@Composable
private fun Filters(selected: HomeFilter, onFilter: (HomeFilter) -> Unit) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterButton(stringResource(R.string.all), null, selected == HomeFilter.ALL) {
            onFilter(HomeFilter.ALL)
        }
        FilterButton("YouTube", VideoPrimeColors.YouTube, selected == HomeFilter.YOUTUBE) {
            onFilter(HomeFilter.YOUTUBE)
        }
        FilterButton("Twitch", VideoPrimeColors.Twitch, selected == HomeFilter.TWITCH) {
            onFilter(HomeFilter.TWITCH)
        }
    }
}

@Composable
private fun FilterButton(label: String, dot: Color?, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold) },
        leadingIcon = dot?.let { { Dot(it, 8) } },
        shape = RoundedCornerShape(18.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = Modifier.height(40.dp)
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .semantics { heading() }
    )
}

@Composable
private fun LiveCard(item: HomeItem, onOpen: (HomeItem) -> Unit, onMore: (HomeItem) -> Unit) {
    Column(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = { onOpen(item) }, onLongClick = { onMore(item) }),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Thumbnail(item, RoundedCornerShape(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(item.avatarUrl, 34, ring = VideoPrimeColors.forService(item.serviceId))
            Column {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.uploader,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VideoCard(item: HomeItem, onOpen: (HomeItem) -> Unit, onMore: (HomeItem) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onOpen(item) }, onLongClick = { onMore(item) })
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Thumbnail(item, RoundedCornerShape(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(item.avatarUrl, 38, ring = null)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(VideoPrimeColors.forService(item.serviceId), 7)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        item.meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(item: HomeItem, shape: RoundedCornerShape) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        // a broadcast or video without image (or whose image fails) shows the app's placeholder
        SmoothImage(
            model = item.thumbnailUrl,
            error = painterResource(R.drawable.placeholder_thumbnail_video),
            modifier = Modifier.fillMaxSize()
        )
        if (item.isLive) {
            Badge(
                stringResource(R.string.home_live_badge),
                VideoPrimeColors.Live,
                Modifier.align(Alignment.TopStart)
            )
        }
        item.badge?.let {
            Badge(
                it,
                VideoPrimeColors.Scrim,
                Modifier.align(if (item.isLive) Alignment.BottomStart else Alignment.BottomEnd)
            )
        }
        item.progress?.let {
            LinearProgressIndicator(
                progress = { it },
                color = VideoPrimeColors.Accent,
                trackColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomStart)
            )
        }
    }
}

@Composable
private fun Badge(text: String, background: Color, modifier: Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .padding(8.dp)
            .background(background, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@Composable
private fun Avatar(url: String?, sizeDp: Int, ring: Color?) {
    val base = Modifier.size(sizeDp.dp)
    val ringed = if (ring == null) {
        base
    } else {
        base
            .border(2.dp, ring, CircleShape)
            .padding(3.dp)
    }
    SmoothImage(
        model = url,
        modifier = ringed
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outline)
    )
}

@Composable
private fun Dot(color: Color, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .background(color, CircleShape)
    )
}

@Composable
private fun EmptyMessage(text: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onAction) { Text(action) }
    }
}
