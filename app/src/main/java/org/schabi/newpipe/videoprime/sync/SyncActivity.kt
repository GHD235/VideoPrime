package org.schabi.newpipe.videoprime.sync

import android.os.Bundle
import android.text.format.DateUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.R
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.videoprime.VideoPrimeColors
import org.schabi.newpipe.videoprime.VideoPrimeTheme

/**
 * PulsePlay: Ajustes → Sincronización. The first device starts the group; each other one types
 * the code shown on a device already in it (no account, no password: easy with a TV remote).
 */
class SyncActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.setTheme(this, ServiceHelper.getSelectedServiceId(this))
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(this).apply {
                setContent { VideoPrimeTheme(dark = true) { SyncScreen(onBack = ::finish) } }
            }
        )
    }

    @Composable
    private fun SyncScreen(onBack: () -> Unit) {
        val context = this
        val scope = rememberCoroutineScope()
        var linked by remember { mutableStateOf(SyncManager.isLinked(context)) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var code by remember { mutableStateOf<Pair<String, Long>?>(null) }
        var typed by remember { mutableStateOf("") }
        var devices by remember { mutableStateOf<List<SyncApi.Device>>(emptyList()) }
        var refresh by remember { mutableLongStateOf(0L) }

        fun run(action: suspend () -> Unit) {
            if (busy) return
            busy = true
            error = null
            scope.launch {
                try {
                    action()
                } catch (e: SyncApi.HttpException) {
                    error = getString(if (e.code == 404 || e.code == 400) R.string.pulseplay_sync_bad_code else R.string.pulseplay_sync_failed)
                } catch (e: Exception) {
                    error = getString(R.string.pulseplay_sync_failed)
                } finally {
                    busy = false
                    linked = SyncManager.isLinked(context)
                    refresh = System.currentTimeMillis()
                }
            }
        }

        LaunchedEffect(linked, refresh) {
            if (linked) {
                devices = runCatching { withContext(Dispatchers.IO) { SyncManager.devices(context) } }
                    .getOrDefault(devices)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(VideoPrimeColors.DarkBackground)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("←", fontSize = 22.sp, color = VideoPrimeColors.DarkText) }
                Text(
                    stringResource(R.string.pulseplay_sync_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = VideoPrimeColors.DarkText
                )
            }
            Text(stringResource(R.string.pulseplay_sync_explanation), color = VideoPrimeColors.DarkTextMuted, fontSize = 15.sp)

            if (!SyncManager.isAvailable()) {
                Text(stringResource(R.string.pulseplay_sync_unavailable), color = VideoPrimeColors.Danger)
                return@Column
            }

            if (!linked) {
                Card {
                    Text(stringResource(R.string.pulseplay_sync_first_title), fontWeight = FontWeight.Bold, color = VideoPrimeColors.DarkText)
                    Text(stringResource(R.string.pulseplay_sync_first_text), color = VideoPrimeColors.DarkTextMuted)
                    Button(
                        onClick = { run { withContext(Dispatchers.IO) { SyncManager.createGroup(context) } } },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = VideoPrimeColors.Accent)
                    ) { Text(stringResource(R.string.pulseplay_sync_start_here)) }
                }
                Card {
                    Text(stringResource(R.string.pulseplay_sync_join_title), fontWeight = FontWeight.Bold, color = VideoPrimeColors.DarkText)
                    Text(stringResource(R.string.pulseplay_sync_join_text), color = VideoPrimeColors.DarkTextMuted)
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 26.sp, fontFamily = FontFamily.Monospace, letterSpacing = 6.sp),
                        placeholder = { Text("ABC123", fontFamily = FontFamily.Monospace) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (typed.length == 6) run { withContext(Dispatchers.IO) { SyncManager.joinGroup(context, typed) } }
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { run { withContext(Dispatchers.IO) { SyncManager.joinGroup(context, typed) } } },
                        enabled = !busy && typed.length == 6,
                        colors = ButtonDefaults.buttonColors(containerColor = VideoPrimeColors.Accent)
                    ) { Text(stringResource(R.string.pulseplay_sync_join)) }
                }
            } else {
                Card {
                    Text(stringResource(R.string.pulseplay_sync_on), fontWeight = FontWeight.Bold, color = VideoPrimeColors.DarkText)
                    val last = SyncManager.lastSync(context)
                    Text(
                        if (last == 0L) {
                            stringResource(R.string.pulseplay_sync_never)
                        } else {
                            stringResource(R.string.pulseplay_sync_last, DateUtils.getRelativeTimeSpanString(last).toString())
                        },
                        color = VideoPrimeColors.DarkTextMuted
                    )
                    SyncManager.lastError(context)?.let {
                        Text(stringResource(R.string.pulseplay_sync_last_error, it), color = VideoPrimeColors.Danger, fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = {
                        SyncManager.syncNow(context)
                        scope.launch {
                            delay(3000)
                            refresh = System.currentTimeMillis()
                        }
                    }) { Text(stringResource(R.string.pulseplay_sync_now)) }
                }
                Card {
                    Text(stringResource(R.string.pulseplay_sync_add_title), fontWeight = FontWeight.Bold, color = VideoPrimeColors.DarkText)
                    val shown = code
                    if (shown != null && shown.second > System.currentTimeMillis()) {
                        Text(stringResource(R.string.pulseplay_sync_add_code_text), color = VideoPrimeColors.DarkTextMuted)
                        Text(
                            shown.first.chunked(3).joinToString(" "),
                            fontSize = 44.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = VideoPrimeColors.New,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(stringResource(R.string.pulseplay_sync_add_text), color = VideoPrimeColors.DarkTextMuted)
                    }
                    Button(
                        onClick = { run { code = withContext(Dispatchers.IO) { SyncManager.newCode(context) } } },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = VideoPrimeColors.Accent)
                    ) { Text(stringResource(R.string.pulseplay_sync_show_code)) }
                }
                if (devices.isNotEmpty()) {
                    Card {
                        Text(stringResource(R.string.pulseplay_sync_devices), fontWeight = FontWeight.Bold, color = VideoPrimeColors.DarkText)
                        for (device in devices) {
                            Text(
                                device.name.ifEmpty { "?" } + if (device.me) " · " + stringResource(R.string.pulseplay_sync_this_device) else "",
                                color = VideoPrimeColors.DarkText
                            )
                        }
                    }
                }
                TextButton(onClick = { run { withContext(Dispatchers.IO) { SyncManager.leave(context) } } }, enabled = !busy) {
                    Text(stringResource(R.string.pulseplay_sync_leave), color = VideoPrimeColors.Danger)
                }
            }

            if (busy) CircularProgressIndicator(color = VideoPrimeColors.Accent)
            error?.let { Text(it, color = VideoPrimeColors.Danger) }
            Spacer(Modifier.height(24.dp))
        }
    }

    @Composable
    private fun Card(content: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VideoPrimeColors.DarkSurface, RoundedCornerShape(20.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) { content() }
    }
}
