package org.schabi.newpipe.videoprime

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.schabi.newpipe.R
import org.schabi.newpipe.player.helper.PlaybackParameterDialog
import org.schabi.newpipe.util.ThemeHelper

/**
 * VideoPrime: the playback speed as a sheet from the bottom: the usual speeds as chips, a finer
 * step with − and +, "skip silences", and the old dialog (pitch...) behind "More options".
 * Every change applies at once.
 */
object SpeedSheet {
    private val SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    private const val STEP = 0.05f
    private const val MIN = 0.1f
    private const val MAX = 3f

    @JvmStatic
    fun show(
        activity: FragmentActivity,
        speed: Float,
        pitch: Float,
        skipSilence: Boolean,
        callback: PlaybackParameterDialog.Callback
    ) {
        val dialog = NameSheet.dialog(activity)
        val colors = SheetColors(!ThemeHelper.isLightThemeSelected(activity))
        dialog.setContentView(
            ComposeView(activity).apply {
                setContent {
                    VideoPrimeTheme(dark = colors.dark) {
                        Content(activity, colors, speed, pitch, skipSilence, callback) {
                            dialog.dismiss()
                            PlaybackParameterDialog.newInstance(
                                it.first.toDouble(),
                                pitch.toDouble(),
                                it.second,
                                callback
                            ).show(activity.supportFragmentManager, null)
                        }
                    }
                }
            }
        )
        dialog.show()
    }

    private fun label(speed: Float): String {
        val rounded = (speed * 100).roundToInt() / 100f
        val text = if (rounded == rounded.toInt().toFloat()) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%s", rounded).trimEnd('0')
        }
        return "$text×"
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Content(
        context: Context,
        colors: SheetColors,
        initialSpeed: Float,
        pitch: Float,
        initialSkip: Boolean,
        callback: PlaybackParameterDialog.Callback,
        more: (Pair<Float, Boolean>) -> Unit
    ) {
        var speed by remember { mutableFloatStateOf(initialSpeed) }
        var skip by remember { mutableStateOf(initialSkip) }
        fun apply(newSpeed: Float, newSkip: Boolean) {
            speed = ((newSpeed.coerceIn(MIN, MAX) * 100).roundToInt()) / 100f
            skip = newSkip
            callback.onPlaybackParameterChanged(speed, pitch, skip)
        }
        SheetFrame(colors, context.getString(R.string.videoprime_speed_title)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RoundButton(colors, "−") { apply(speed - STEP, skip) }
                Text(label(speed), color = colors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                RoundButton(colors, "+") { apply(speed + STEP, skip) }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SPEEDS.forEach { value ->
                    VideoPrimeChip(label(value), abs(speed - value) < 0.001f, null) {
                        apply(value, skip)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.raised)
                    .clickable { apply(speed, !skip) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.skip_silence_checkbox),
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = skip,
                    onCheckedChange = { apply(speed, it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = VideoPrimeColors.Accent,
                        uncheckedTrackColor = colors.surface,
                        uncheckedBorderColor = colors.outline,
                        uncheckedThumbColor = colors.muted
                    )
                )
            }
            Text(
                stringResource(R.string.videoprime_speed_more),
                color = colors.muted,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { more(speed to skip) }
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }
    }

    @Composable
    private fun RoundButton(colors: SheetColors, text: String, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.raised)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}
