package org.schabi.newpipe.videoprime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * VideoPrime: when the screen comes back (a menu or dialog closes, back from another screen),
 * Android gives the focus to the first field it finds, a search box, and the keyboard opens by
 * itself. Put this in a screen with a search box: the focus is dropped at those moments, so the
 * keyboard only opens when the box is tapped.
 */
@Composable
fun NoAutoKeyboard() {
    val window = LocalWindowInfo.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(window) {
        var wasFocused = false
        snapshotFlow { window.isWindowFocused }.collect { focused ->
            if (focused && !wasFocused) {
                // after Android has handed the focus out
                withFrameNanos { }
                withFrameNanos { }
                focusManager.clearFocus()
            }
            wasFocused = focused
        }
    }
}
