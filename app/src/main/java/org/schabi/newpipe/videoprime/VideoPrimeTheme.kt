package org.schabi.newpipe.videoprime

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** VideoPrime palette, as in the redesign mockups. */
object VideoPrimeColors {
    val Accent = Color(0xFF7C3AED)
    val YouTube = Color(0xFFFF5A52)
    val Twitch = Color(0xFFA970FF)
    val Live = Color(0xFFD92D36)
    val Scrim = Color(0xB8000000)

    val DarkBackground = Color(0xFF0F0E13)
    val DarkSurface = Color(0xFF1A1822)
    val DarkSurfaceHigh = Color(0xFF24212E)
    val DarkOutline = Color(0xFF34303F)
    val DarkText = Color(0xFFF4F2F8)
    val DarkTextMuted = Color(0xFFA9A3B8)

    val LightBackground = Color(0xFFFAF9FC)
    val LightSurface = Color(0xFFEFECF4)
    val LightSurfaceHigh = Color(0xFFE4E0EC)
    val LightOutline = Color(0xFFCBC5D6)
    val LightText = Color(0xFF16141D)
    val LightTextMuted = Color(0xFF5E5870)

    /** Colour of the dot that tells which platform an item comes from. */
    fun forService(serviceId: Int): Color = if (serviceId == TWITCH_SERVICE_ID) Twitch else YouTube

    private const val TWITCH_SERVICE_ID = 5
}

private val DarkScheme = darkColorScheme(
    primary = VideoPrimeColors.Accent,
    onPrimary = Color.White,
    background = VideoPrimeColors.DarkBackground,
    onBackground = VideoPrimeColors.DarkText,
    surface = VideoPrimeColors.DarkBackground,
    onSurface = VideoPrimeColors.DarkText,
    surfaceVariant = VideoPrimeColors.DarkSurface,
    surfaceContainer = VideoPrimeColors.DarkSurface,
    surfaceContainerHigh = VideoPrimeColors.DarkSurfaceHigh,
    onSurfaceVariant = VideoPrimeColors.DarkTextMuted,
    outline = VideoPrimeColors.DarkOutline,
    outlineVariant = VideoPrimeColors.DarkOutline,
    secondaryContainer = VideoPrimeColors.DarkText,
    onSecondaryContainer = VideoPrimeColors.DarkBackground
)

private val LightScheme = lightColorScheme(
    primary = VideoPrimeColors.Accent,
    onPrimary = Color.White,
    background = VideoPrimeColors.LightBackground,
    onBackground = VideoPrimeColors.LightText,
    surface = VideoPrimeColors.LightBackground,
    onSurface = VideoPrimeColors.LightText,
    surfaceVariant = VideoPrimeColors.LightSurface,
    surfaceContainer = VideoPrimeColors.LightSurface,
    surfaceContainerHigh = VideoPrimeColors.LightSurfaceHigh,
    onSurfaceVariant = VideoPrimeColors.LightTextMuted,
    outline = VideoPrimeColors.LightOutline,
    outlineVariant = VideoPrimeColors.LightOutline,
    secondaryContainer = VideoPrimeColors.LightText,
    onSecondaryContainer = VideoPrimeColors.LightBackground
)

@Composable
fun VideoPrimeTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
