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
 * the same address and cache key a channel tile uses). Subscriptions asks again when it opens:
 * on a TV the memory for images is small and the home covers push the avatars out, and a
 * picture already in memory costs nothing to ask for.
 */
object AvatarPrefetch {
    /** What a channel tile asks for (≤ 360 px wide): the smallest size BestThumbnail requests. */
    private const val AVATAR_PX = 360

    // ~0.5 MB each in memory: enough for the channels on screen without pushing out the covers
    private const val MAX = 80

    /** The ones being fetched now (not asked twice at the same time). */
    private val inFlight = HashSet<String>()

    @JvmStatic
    fun warm(context: Context, subscriptions: List<SubscriptionEntity>) {
        val urls = subscriptions.asSequence()
            .mapNotNull { ImageStrategy.choosePreferredImage(ImageStrategy.dbUrlToImageList(it.avatarUrl)) }
            .distinct()
            .take(MAX)
            .filter { synchronized(inFlight) { inFlight.add(it) } }
            .toList()
        if (urls.isEmpty()) return
        val app = context.applicationContext
        val loader = app.imageLoader
        urls.forEach { url ->
            loader.enqueue(
                ImageRequest.Builder(app)
                    .data(url)
                    .size(AVATAR_PX)
                    .listener(
                        onSuccess = { _, _ -> synchronized(inFlight) { inFlight.remove(url) } },
                        onCancel = { synchronized(inFlight) { inFlight.remove(url) } },
                        onError = { _, _ -> synchronized(inFlight) { inFlight.remove(url) } }
                    )
                    .build()
            )
        }
    }
}
