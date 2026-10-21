package org.schabi.newpipe.videoprime

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.schabi.newpipe.util.ThemeHelper

/** One tile of an [ActionSheet]. */
class SheetAction(
    val label: String,
    @DrawableRes val icon: Int,
    val style: Style = Style.NORMAL,
    val onClick: Runnable
) {
    enum class Style { MAIN, NORMAL, DANGER }
}

/**
 * VideoPrime: the long-press menus (a video, a channel) as a sheet from the bottom with big tiles
 * in two columns ("A · Baldosas" in the mockups): what it is on top (thumbnail or avatar, title,
 * channel), the actions below, the main one in violet.
 */
object ActionSheet {
    private val Cyan = Color(0xFF56C8E8)

    /**
     * @param imageUrl the video's thumbnail or the channel's avatar
     * @param round    whether the image is an avatar (round) or a thumbnail (16:9)
     * @param badge    text over the thumbnail's corner (the duration), if any
     */
    @JvmStatic
    fun show(
        context: Context,
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
                        Sheet(dark, title, subtitle, imageUrl, round, badge, actions) { action ->
                            dialog.dismiss()
                            action.onClick.run()
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

    @Composable
    private fun Sheet(
        dark: Boolean,
        title: String,
        subtitle: String?,
        imageUrl: String?,
        round: Boolean,
        badge: String?,
        actions: List<SheetAction>,
        onAction: (SheetAction) -> Unit
    ) {
        val surface = if (dark) VideoPrimeColors.DarkSurface else VideoPrimeColors.LightBackground
        val tile = if (dark) VideoPrimeColors.DarkSurfaceHigh else VideoPrimeColors.LightSurface
        val text = if (dark) VideoPrimeColors.DarkText else VideoPrimeColors.LightText
        val muted = if (dark) VideoPrimeColors.DarkTextMuted else VideoPrimeColors.LightTextMuted
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(surface)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(muted.copy(alpha = 0.5f))
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (round) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(tile)
                    )
                } else {
                    Box(
                        Modifier
                            .width(104.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(tile)
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
                        color = text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrEmpty()) {
                        Text(
                            subtitle,
                            color = muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { action ->
                            Tile(action, dark, tile, text, Modifier.weight(1f)) { onAction(action) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Tile(
        action: SheetAction,
        dark: Boolean,
        background: Color,
        text: Color,
        modifier: Modifier,
        onClick: () -> Unit
    ) {
        val main = action.style == SheetAction.Style.MAIN
        val danger = action.style == SheetAction.Style.DANGER
        val content = when {
            main -> Color.White
            danger -> VideoPrimeColors.Danger
            else -> text
        }
        val iconColor = when {
            main -> Color.White
            danger -> VideoPrimeColors.Danger
            dark -> Cyan
            else -> VideoPrimeColors.Accent
        }
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (main) VideoPrimeColors.Accent else background)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painterResource(action.icon),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                action.label,
                color = content,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
