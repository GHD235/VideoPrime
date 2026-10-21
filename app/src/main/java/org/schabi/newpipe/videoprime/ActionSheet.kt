package org.schabi.newpipe.videoprime

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.schabi.newpipe.util.ThemeHelper

/**
 * One action of an [ActionSheet].
 *
 * @param toggled  for an on/off setting (a switch row that keeps the sheet open), its state;
 *                 `null` for a plain action
 * @param confirm  for a destructive action, the text asking to tap again; `null` to run at once
 */
class SheetAction @JvmOverloads constructor(
    val label: String,
    @DrawableRes val icon: Int,
    val danger: Boolean = false,
    val toggled: Boolean? = null,
    val confirm: String? = null,
    val onClick: Runnable
)

/**
 * VideoPrime: the long-press menus as a sheet from the bottom, with what it is on top (thumbnail
 * or avatar, title, channel). A video's menu shows round buttons in a row with the neon glow of
 * the queue ([Layout.BUTTONS]); a channel's menu shows rows, its notifications as a switch
 * ([Layout.ROWS]). No action is highlighted: violet means "on", never "main".
 */
object ActionSheet {
    enum class Layout { BUTTONS, ROWS }

    private val Cyan = Color(0xFF56C8E8)

    /**
     * @param imageUrl the video's thumbnail or the channel's avatar
     * @param round    whether the image is an avatar (round) or a thumbnail (16:9)
     * @param badge    text over the thumbnail's corner (the duration), if any
     */
    @JvmStatic
    fun show(
        context: Context,
        layout: Layout,
        title: String,
        subtitle: String?,
        imageUrl: String?,
        round: Boolean,
        badge: String?,
        actions: List<SheetAction>
    ) {
        val dialog = BottomSheetDialog(context)
        val dark = !ThemeHelper.isLightThemeSelected(context)
        dialog.setContentView(
            ComposeView(context).apply {
                setContent {
                    VideoPrimeTheme(dark = dark) {
                        Sheet(dark, layout, title, subtitle, imageUrl, round, badge, actions) {
                            dialog.dismiss()
                        }
                    }
                }
            }
        )
        dialog.setOnShowListener {
            // the sheet draws its own rounded background
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(AndroidColor.TRANSPARENT)
        }
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }

    private class Palette(dark: Boolean) {
        val surface = if (dark) VideoPrimeColors.DarkSurface else VideoPrimeColors.LightBackground
        val raised = if (dark) VideoPrimeColors.DarkSurfaceHigh else VideoPrimeColors.LightSurface
        val text = if (dark) VideoPrimeColors.DarkText else VideoPrimeColors.LightText
        val muted = if (dark) VideoPrimeColors.DarkTextMuted else VideoPrimeColors.LightTextMuted
        val glow = if (dark) Cyan else VideoPrimeColors.Accent
    }

    @Composable
    private fun Sheet(
        dark: Boolean,
        layout: Layout,
        title: String,
        subtitle: String?,
        imageUrl: String?,
        round: Boolean,
        badge: String?,
        actions: List<SheetAction>,
        dismiss: () -> Unit
    ) {
        val colors = remember(dark) { Palette(dark) }
        val background = if (layout == Layout.BUTTONS) {
            // the neon glass of the queue: a cyan glow from the top, violet at the bottom
            Brush.verticalGradient(
                listOf(
                    colors.glow.copy(alpha = 0.12f).compositeOver(colors.surface),
                    colors.surface,
                    VideoPrimeColors.Accent.copy(alpha = 0.08f).compositeOver(colors.surface)
                )
            )
        } else {
            Brush.verticalGradient(listOf(colors.surface, colors.surface))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(background)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.muted.copy(alpha = 0.5f))
            )
            Header(colors, title, subtitle, imageUrl, round, badge)
            when (layout) {
                Layout.BUTTONS -> Buttons(colors, actions, dismiss)
                Layout.ROWS -> Rows(colors, actions, dismiss)
            }
        }
    }

    @Composable
    private fun Header(
        colors: Palette,
        title: String,
        subtitle: String?,
        imageUrl: String?,
        round: Boolean,
        badge: String?
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (round) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(colors.raised)
                )
            } else {
                Box(
                    Modifier
                        .width(104.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.raised)
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    if (!badge.isNullOrEmpty()) {
                        Text(
                            badge,
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(5.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xB3000000))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = colors.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        subtitle,
                        color = colors.muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    /** Round buttons, four per row, with the name below. */
    @Composable
    private fun Buttons(colors: Palette, actions: List<SheetAction>, dismiss: () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            actions.chunked(BUTTONS_PER_ROW).forEach { row ->
                Row {
                    row.forEach { action ->
                        val tint = if (action.danger) VideoPrimeColors.Danger else colors.text
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    dismiss()
                                    action.onClick.run()
                                }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(colors.text.copy(alpha = 0.06f))
                                    .border(
                                        1.dp,
                                        (if (action.danger) VideoPrimeColors.Danger else colors.glow)
                                            .copy(alpha = 0.4f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(action.icon),
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                action.label,
                                color = if (action.danger) VideoPrimeColors.Danger else colors.muted,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // keep the buttons of a short last row the same width
                    repeat(BUTTONS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    /** Rows with an icon; a setting gets a switch, a destructive action asks to tap again. */
    @Composable
    private fun Rows(colors: Palette, actions: List<SheetAction>, dismiss: () -> Unit) {
        Column {
            actions.forEach { action ->
                var on by remember { mutableStateOf(action.toggled ?: false) }
                var confirming by remember { mutableStateOf(false) }
                val tint = if (action.danger) VideoPrimeColors.Danger else VideoPrimeColors.Accent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            when {
                                action.toggled != null -> {
                                    on = !on
                                    action.onClick.run()
                                }

                                action.confirm != null && !confirming -> confirming = true

                                else -> {
                                    dismiss()
                                    action.onClick.run()
                                }
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        painterResource(action.icon),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        if (confirming) action.confirm.orEmpty() else action.label,
                        color = if (action.danger) VideoPrimeColors.Danger else colors.text,
                        fontSize = 16.sp,
                        fontWeight = if (confirming) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (action.toggled != null) {
                        Switch(
                            checked = on,
                            onCheckedChange = {
                                on = it
                                action.onClick.run()
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = VideoPrimeColors.Accent,
                                checkedThumbColor = Color.White
                            )
                        )
                    }
                }
            }
        }
    }

    private const val BUTTONS_PER_ROW = 4
}
