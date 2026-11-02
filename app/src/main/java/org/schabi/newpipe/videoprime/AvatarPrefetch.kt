package org.schabi.newpipe.videoprime

import android.content.Context
import coil3.imageLoader
import coil3.request.ImageRequest
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * PulsePlay: the subscribed channels' pictures are fetched in the background as soon as the app
 * knows the channels (the home screen reads them on opening), so that Subscriptions draws them
 * from memory instead of asking the network for dozens of avatars when it opens.
 *
 * They are asked at the size the screens draw them at ([AVATAR_PX]: BestThumbnail turns it into
 * the same address and cache key a channel tile uses), once per run of the app.
 */
object AvatarPrefetch {
    /** What a channel tile asks for (≤ 360 px wide): the smallest size BestThumbnail requests. */
    private const val AVATAR_PX = 360

    // ~0.5 MB each in memory: enough for the channels on screen without pushing out the covers
    private const val MAX = 80

    private val done = HashSet<String>()

    @JvmStatic
    fun warm(context: Context, subscriptions: List<SubscriptionEntity>) {
        val urls = subscriptions.asSequence()
            .mapNotNull { ImageStrategy.choosePreferredImage(ImageStrategy.dbUrlToImageList(it.avatarUrl)) }
            .distinct()
            .take(MAX)
            .filter { synchronized(done) { done.add(it) } }
            .toList()
        if (urls.isEmpty()) return
        val app = context.applicationContext
        val loader = app.imageLoader
        urls.forEach { url ->
            loader.enqueue(
                ImageRequest.Builder(app)
                    .data(url)
                    .size(AVATAR_PX)
                    // without network it is tried again on the next read of the channels
                    .listener(onError = { _, _ -> synchronized(done) { done.remove(url) } })
                    .build()
            )
        }
    }
}
