package org.schabi.newpipe.videoprime

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.function.Consumer
import org.schabi.newpipe.util.ThemeHelper

/** VideoPrime: the colours of the sheets from the bottom (menus, playlists, speed...). */
class SheetColors(val dark: Boolean) {
    val surface = if (dark) VideoPrimeColors.DarkSurface else VideoPrimeColors.LightBackground
    val raised = if (dark) VideoPrimeColors.DarkSurfaceHigh else VideoPrimeColors.LightSurface
    val text = if (dark) VideoPrimeColors.DarkText else VideoPrimeColors.LightText
    val muted = if (dark) VideoPrimeColors.DarkTextMuted else VideoPrimeColors.LightTextMuted
    val outline = if (dark) VideoPrimeColors.DarkOutline else VideoPrimeColors.LightOutline

    /** The main button of a sheet: white on dark, dark on light (violet means "on"). */
    val buttonFill = if (dark) Color.White else VideoPrimeColors.DarkBackground
    val buttonText = if (dark) VideoPrimeColors.DarkBackground else Color.White
}

/** VideoPrime: the frame of a sheet from the bottom: rounded top, handle and a title. */
@Composable
fun SheetFrame(
    colors: SheetColors,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(colors.surface)
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 38.dp, height = 4.dp)
                .clip(CircleShape)
                .background(colors.muted.copy(alpha = 0.5f))
        )
        Text(
            title,
            color = colors.text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        content()
    }
}

/** VideoPrime: the big rounded button at the bottom of a sheet. */
@Composable
fun SheetButton(colors: SheetColors, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.buttonFill,
            contentColor = colors.buttonText
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * VideoPrime: a sheet from the bottom that asks for a name (a new playlist...), instead of the
 * old dialog with an underlined field.
 */
object NameSheet {
    /** Makes a sheet dialog with a transparent frame (the content draws its own). */
    @JvmStatic
    fun dialog(context: Context, theme: Int = 0): BottomSheetDialog {
        val dialog = BottomSheetDialog(context, theme)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(AndroidColor.TRANSPARENT)
        }
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        return dialog
    }

    /**
     * @param subtitle a line under the field (e.g. what will be added), if any
     * @param onDone   gets the name typed (never blank)
     */
    @JvmStatic
    fun view(
        context: Context,
        title: String,
        hint: String,
        subtitle: String?,
        button: String,
        dismiss: Runnable,
        onDone: Consumer<String>
    ): View {
        val colors = SheetColors(!ThemeHelper.isLightThemeSelected(context))
        return ComposeView(context).apply {
            setContent {
                VideoPrimeTheme(dark = colors.dark) {
                    Content(colors, title, hint, subtitle, button) { name ->
                        onDone.accept(name)
                        dismiss.run()
                    }
                }
            }
        }
    }

    /** Lets the keyboard push the sheet up. */
    @JvmStatic
    fun adjustForKeyboard(dialog: BottomSheetDialog) {
        @Suppress("DEPRECATION")
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    @Composable
    private fun Content(
        colors: SheetColors,
        title: String,
        hint: String,
        subtitle: String?,
        button: String,
        done: (String) -> Unit
    ) {
        var name by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        SheetFrame(colors, title) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text(hint) },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (name.isNotBlank()) done(name.trim())
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
            if (subtitle != null) {
                Text(subtitle, color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
            }
            SheetButton(colors, button, enabled = name.isNotBlank()) { done(name.trim()) }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}
