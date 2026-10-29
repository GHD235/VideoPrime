package org.schabi.newpipe.videoprime

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter

/**
 * VideoPrime: an image that keeps showing the picture it had while a new address loads, instead
 * of going blank for a moment (a refresh gives Twitch's live thumbnails a new address every few
 * minutes, and the cards flashed empty).
 *
 * PulsePlay: [identity] says which thing the image is of (a video, a channel): the old picture is
 * only kept while it is the same one, never shown on a card that now holds another video. A failed
 * load (a moment without network) also keeps the old picture rather than the placeholder.
 */
@Composable
fun SmoothImage(
    model: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    error: Painter? = null,
    identity: Any? = model
) {
    val painter = rememberAsyncImagePainter(model = model, error = error, fallback = error)
    val state by painter.state.collectAsState()
    // the last picture that loaded, shown on top until the new one is ready
    var previous by remember(identity) { mutableStateOf<Painter?>(null) }
    LaunchedEffect(state) {
        val success = state as? AsyncImagePainter.State.Success
        if (success != null) previous = success.painter
    }
    Box(modifier) {
        // always drawn, so that it learns its size and loads
        Image(painter, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        val old = previous
        if (old != null && state !is AsyncImagePainter.State.Success) {
            Image(old, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
    }
}
