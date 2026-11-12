package org.schabi.newpipe.videoprime

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * VideoPrime: an image that keeps showing the picture it had while a new address loads, instead
 * of going blank for a moment (a refresh gives Twitch's live thumbnails a new address every few
 * minutes, and the cards flashed empty).
 *
 * PulsePlay: [identity] says which thing the image is of (a video, a channel): the old picture is
 * only kept while it is the same one, never shown on a card that now holds another video. A failed
 * load (a moment without network) also keeps the old picture rather than the placeholder.
 *
 * The old picture is drawn UNDER the new one (which draws nothing until it has loaded), so a card
 * is not composed again at each step of the load: once when its picture arrives, and a card that
 * is not changing draws a single image.
 */
@Composable
fun SmoothImage(
    model: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    error: Painter? = null,
    identity: Any? = model,
    crossfade: Boolean = true
) {
    val context = LocalPlatformContext.current
    // PulsePlay: a channel's picture shows at once, without the fade
    val request = remember(model, crossfade) {
        if (crossfade) model else ImageRequest.Builder(context).data(model).crossfade(false).build()
    }
    // PulsePlay: the big thumbnail was slow and the small one was drawn meanwhile (a TV, a slow
    // network): when the big one arrives the image is asked again, with the small one kept
    // underneath, instead of staying blurred until the card leaves the screen and comes back
    var version by remember(model) { mutableIntStateOf(0) }
    val best = remember(model) { BestThumbnail.of(model) }
    if (best != null && best != model) {
        LaunchedEffect(best) {
            // once: if even the second try is slow, the small one stays (no endless retries)
            BestThumbnail.upgraded.collect { if (it == best && version == 0) version = 1 }
        }
    }
    // the last picture that loaded, and the address and version it came from
    var previous by remember(identity) { mutableStateOf<Triple<String?, Int, Painter>?>(null) }
    val old = previous?.takeIf { it.first != model || it.second != version }?.third
    Box(modifier) {
        if (old != null) {
            Image(old, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
        key(version) {
            val shown = version
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = contentScale,
                // with an old picture to keep, a failure leaves it in sight
                error = if (old == null) error else null,
                fallback = if (old == null) error else null,
                onSuccess = { previous = Triple(model, shown, it.painter) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
