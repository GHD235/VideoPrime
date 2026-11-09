package org.schabi.newpipe.videoprime

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    // the last picture that loaded and the address it came from
    var previous by remember(identity) { mutableStateOf<Pair<String?, Painter>?>(null) }
    val old = previous?.takeIf { it.first != model }?.second
    Box(modifier) {
        if (old != null) {
            Image(old, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = contentScale,
            // with an old picture to keep, a failure leaves it in sight
            error = if (old == null) error else null,
            fallback = if (old == null) error else null,
            onSuccess = { previous = model to it.painter },
            modifier = Modifier.fillMaxSize()
        )
    }
}
