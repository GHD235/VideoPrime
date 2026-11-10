package org.schabi.newpipe.videoprime

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.schabi.newpipe.R

/** VideoPrime palette, as in the redesign mockups. */
object VideoPrimeColors {
    val Accent = Color(0xFF7C3AED)
    val YouTube = Color(0xFFFF5A52)
    val YouTubeMusic = Color(0xFFFF8A3D)
    val Twitch = Color(0xFFA970FF)
    val Live = Color(0xFFD92D36)

    /** Videos not seen yet (cyan: purple means "on", red means "live"). */
    val New = Color(0xFF22D3EE)
    val OnNew = Color(0xFF06222A)
    val Scrim = Color(0xB8000000)
    val Selected = Color(0x337C3AED)
    val Danger = Color(0xFFE5484D)
    val DangerFill = Color(0xFFC2333B)

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

/** PulsePlay: the app's own typeface (Plus Jakarta Sans), the same as the classic screens. */
val PulsePlayFont = FontFamily(
    Font(R.font.plus_jakarta_sans_400, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_500, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_600, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_700, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_800, FontWeight.ExtraBold)
)

private val PulsePlayTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = PulsePlayFont),
        displayMedium = base.displayMedium.copy(fontFamily = PulsePlayFont),
        displaySmall = base.displaySmall.copy(fontFamily = PulsePlayFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = PulsePlayFont),
        headlineMedium = base.headlineMedium.copy(fontFamily = PulsePlayFont),
        headlineSmall = base.headlineSmall.copy(fontFamily = PulsePlayFont),
        titleLarge = base.titleLarge.copy(fontFamily = PulsePlayFont),
        titleMedium = base.titleMedium.copy(fontFamily = PulsePlayFont),
        titleSmall = base.titleSmall.copy(fontFamily = PulsePlayFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = PulsePlayFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = PulsePlayFont),
        bodySmall = base.bodySmall.copy(fontFamily = PulsePlayFont),
        labelLarge = base.labelLarge.copy(fontFamily = PulsePlayFont),
        labelMedium = base.labelMedium.copy(fontFamily = PulsePlayFont),
        labelSmall = base.labelSmall.copy(fontFamily = PulsePlayFont)
    )
}

@Composable
fun VideoPrimeTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme, typography = PulsePlayTypography) {
        // text without an explicit colour must follow the theme, not default to black
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
