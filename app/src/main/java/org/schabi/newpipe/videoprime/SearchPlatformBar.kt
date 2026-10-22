package org.schabi.newpipe.videoprime

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.ThemeHelper

/** The platforms that can be searched. YouTube Music is YouTube with its music filters. */
enum class SearchPlatform(val serviceId: Int, val isMusic: Boolean, val label: String, val dot: Color) {
    YOUTUBE(ServiceList.YouTube.serviceId, false, "YouTube", VideoPrimeColors.YouTube),
    YOUTUBE_MUSIC(ServiceList.YouTube.serviceId, true, "YouTube Music", VideoPrimeColors.YouTubeMusic),
    TWITCH(ServiceList.Twitch.serviceId, false, "Twitch", VideoPrimeColors.Twitch);

    /** @return the content filters of this platform, the first one being the default */
    fun filters(): List<String> {
        val all = NewPipe.getService(serviceId).searchQHFactory.availableContentFilter.toList()
        return if (serviceId != ServiceList.YouTube.serviceId) {
            all
        } else {
            all.filter { it.startsWith(MUSIC_PREFIX) == isMusic }
        }
    }

    companion object {
        private const val MUSIC_PREFIX = "music_"

        /** @return the platform that a search with these service and filter belongs to */
        @JvmStatic
        fun of(serviceId: Int, contentFilter: Array<String>): SearchPlatform = when {
            serviceId == ServiceList.Twitch.serviceId -> TWITCH
            contentFilter.firstOrNull()?.startsWith(MUSIC_PREFIX) == true -> YOUTUBE_MUSIC
            else -> YOUTUBE
        }

        /** The default filter of YouTube Music: songs. */
        const val MUSIC_DEFAULT = YoutubeSearchQueryHandlerFactory.MUSIC_SONGS

        /** Music videos, shown when YouTube Music finds no songs. */
        const val MUSIC_VIDEOS = YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS
    }
}

/**
 * VideoPrime: the bar above the search results to choose the platform (YouTube, YouTube Music or
 * Twitch, side by side) and what to look for in it. It replaces NewPipe's filter menu.
 */
object SearchPlatformBar {
    interface Listener {
        fun onPlatformSelected(platform: SearchPlatform)

        fun onFilterSelected(filter: String)
    }

    @JvmStatic
    fun show(
        view: ComposeView,
        platform: SearchPlatform,
        selectedFilter: String?,
        listener: Listener
    ) {
        val context = view.context
        val dark = !ThemeHelper.isLightThemeSelected(context)
        val filters = platform.filters()
        val selected = selectedFilter?.takeIf { it in filters } ?: filters.firstOrNull()
        view.setContent {
            VideoPrimeTheme(dark = dark) {
                Bar(context, platform, filters, selected, listener)
            }
        }
    }

    @Composable
    private fun Bar(
        context: Context,
        platform: SearchPlatform,
        filters: List<String>,
        selected: String?,
        listener: Listener
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchPlatform.entries.forEach { option ->
                    VideoPrimeChip(option.label, option == platform, option.dot) {
                        if (option != platform) listener.onPlatformSelected(option)
                    }
                }
            }
            if (filters.size > 1) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filters.forEach { filter ->
                        VideoPrimeChip(
                            ServiceHelper.getTranslatedFilterString(filter, context),
                            filter == selected,
                            null,
                            small = true
                        ) {
                            if (filter != selected) listener.onFilterSelected(filter)
                        }
                    }
                }
            }
        }
    }
}
