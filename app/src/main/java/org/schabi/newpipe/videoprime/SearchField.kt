package org.schabi.newpipe.videoprime

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.util.DeviceUtils

/**
 * PulsePlay: the rounded search box of the app's lists (Subscriptions, History, Downloads). On
 * the TV, moving the remote's focus over it does not open the keyboard any more: it stays
 * read-only until OK is pressed on it (then the keyboard opens), and goes back to that when the
 * focus leaves. On the phone it behaves like a normal text field.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tv = remember { DeviceUtils.isTv(context) }
    var typing by remember { mutableStateOf(!tv) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(typing) {
        if (tv && typing) keyboard?.show()
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        readOnly = !typing,
        placeholder = { Text(placeholder) },
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .onFocusChanged { if (tv && !it.isFocused) typing = false }
            .onPreviewKeyEvent { event ->
                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter ||
                    event.key == Key.NumPadEnter
                if (tv && !typing && ok) {
                    typing = true
                    true
                } else {
                    false
                }
            }
    )
}
