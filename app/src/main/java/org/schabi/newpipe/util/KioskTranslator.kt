/*
 * SPDX-FileCopyrightText: 2017-2025 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.content.Context
import org.schabi.newpipe.R

object KioskTranslator {
    @JvmStatic
    fun getTranslatedKioskName(kioskId: String, context: Context): String {
        return when (kioskId) {
            "Trending" -> context.getString(R.string.trending)
            "live" -> context.getString(R.string.duration_live)
            "trending_gaming" -> context.getString(R.string.trending_gaming)
            "trending_music" -> context.getString(R.string.trending_music)
            "trending_movies_and_shows" -> context.getString(R.string.trending_movies)
            "trending_podcasts_episodes" -> context.getString(R.string.trending_podcasts)
            else -> kioskId
        }
    }

    @JvmStatic
    fun getKioskIcon(kioskId: String): Int {
        return when (kioskId) {
            "Trending" -> R.drawable.ic_whatshot
            "live" -> R.drawable.ic_live_tv
            "trending_gaming" -> R.drawable.ic_videogame_asset
            "trending_music" -> R.drawable.ic_music_note
            "trending_movies_and_shows" -> R.drawable.ic_movie
            "trending_podcasts_episodes" -> R.drawable.ic_podcasts
            else -> 0
        }
    }
}
