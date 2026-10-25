package org.schabi.newpipe.videoprime

import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * PulsePlay: a white ring around what has the focus of the TV remote (or a keyboard). Put it
 * before the clickable it belongs to. With the fingers nothing ever takes the focus, so on the
 * phone it never shows.
 */
fun Modifier.focusRing(shape: Shape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.hasFocus }
        .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
}
