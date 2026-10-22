package org.schabi.newpipe.videoprime

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.schabi.newpipe.R

/**
 * VideoPrime: the update notification's tap. Downloads the new APK inside the app, in a card with
 * the app's look, and opens Android's installer on it, so that updating is the notification and
 * one "Update" button (the first time, Android also asks to let VideoPrime install apps).
 */
class UpdateActivity : AppCompatActivity() {
    private enum class Step { ALLOW, DOWNLOADING, READY, INSTALLING, FAILED }

    private var step by mutableStateOf(Step.DOWNLOADING)
    private var done by mutableStateOf(0L)
    private var total by mutableStateOf(0L)

    private var apkUrl: String? = null

    @Volatile
    private var download: Thread? = null

    /** Sent to the settings to allow installing: carry on when coming back. */
    private var askedPermission = false

    /** Closing it is the only way to stop a read that is waiting for the network. */
    @Volatile
    private var connection: HttpURLConnection? = null

    @Volatile
    private var cancelled = false

    /** Android only lets the installer open from a window in front: else it waits for the user. */
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askedPermission = savedInstanceState?.getBoolean(STATE_ASKED, false) ?: false
        apkUrl = intent.getStringExtra(EXTRA_URL)
        if (apkUrl == null) {
            finish()
            return
        }
        setFinishOnTouchOutside(false)
        setContentView(
            ComposeView(this).apply {
                setContent { VideoPrimeTheme(dark = true) { UpdateCard() } }
            }
        )
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (step == Step.READY) {
            install(apkFile(this))
            return
        }
        if (download != null || step == Step.FAILED) return
        if (!canInstall()) {
            if (askedPermission) {
                // came back without allowing it: the card explains it and offers it again
                step = Step.ALLOW
            } else {
                askedPermission = true
                step = Step.ALLOW
                openInstallSettings()
            }
            return
        }
        startDownload()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ASKED, askedPermission)
    }

    override fun onDestroy() {
        cancelled = true
        download?.interrupt()
        connection?.disconnect()
        super.onDestroy()
    }

    private fun canInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        packageManager.canRequestPackageInstalls()

    private fun openInstallSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri()))
        }
    }

    private fun openInBrowser() {
        apkUrl?.let { url -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
        finish()
    }

    private fun startDownload() {
        step = Step.DOWNLOADING
        done = 0L
        total = 0L
        val url = apkUrl ?: return
        val file = apkFile(this)
        // each download writes its own file: one left behind by a closed window cannot spoil it
        val part = File(file.parentFile, "VideoPrime-${System.nanoTime()}.apk.part")
        download = Thread {
            try {
                fetch(url, part)
                if (cancelled) throw InterruptedException()
                file.delete()
                if (!part.renameTo(file)) error("Could not move the downloaded update")
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (resumed) install(file) else step = Step.READY
                }
            } catch (e: InterruptedException) {
                part.delete()
            } catch (e: Exception) {
                part.delete()
                if (cancelled) return@Thread
                Log.w(TAG, "Could not download the update", e)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) step = Step.FAILED
                }
            }
        }.apply { start() }
    }

    private fun fetch(url: String, file: File) {
        file.parentFile?.mkdirs()
        val connection = URL(url).openConnection() as HttpURLConnection
        this.connection = connection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val length = connection.contentLength.toLong()
            runOnUiThread { total = length }
            var read = 0L
            var shownAt = 0L
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (Thread.interrupted()) throw InterruptedException()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        read += count
                        // a few updates per second are enough for the bar
                        val now = System.currentTimeMillis()
                        if (now - shownAt > PROGRESS_EVERY_MILLIS) {
                            shownAt = now
                            val current = read
                            runOnUiThread { done = current }
                        }
                    }
                }
            }
            if (length > 0 && read != length) error("Incomplete download: $read of $length bytes")
            runOnUiThread { done = read }
        } finally {
            connection.disconnect()
        }
    }

    private fun install(file: File) {
        step = Step.INSTALLING
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName$AUTHORITY_SUFFIX", file)
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
            finish()
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the installer", e)
            step = Step.FAILED
        }
    }

    @Composable
    private fun UpdateCard() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(VideoPrimeColors.DarkSurface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AppIcon()
                Text(
                    stringResource(R.string.videoprime_update_title),
                    color = VideoPrimeColors.DarkText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                when (step) {
                    Step.ALLOW -> Allow()
                    Step.DOWNLOADING, Step.INSTALLING -> Progress()
                    Step.READY -> Ready()
                    Step.FAILED -> Failed()
                }
            }
        }
    }

    @Composable
    private fun AppIcon() {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
        }
    }

    @Composable
    private fun Progress() {
        val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
        Text(
            stringResource(
                if (step == Step.INSTALLING) R.string.videoprime_update_opening else R.string.videoprime_update_getting
            ),
            color = VideoPrimeColors.DarkTextMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        val barModifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
        if (fraction == null) {
            LinearProgressIndicator(
                modifier = barModifier,
                color = VideoPrimeColors.Accent,
                trackColor = VideoPrimeColors.DarkSurfaceHigh
            )
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = barModifier,
                color = VideoPrimeColors.Accent,
                trackColor = VideoPrimeColors.DarkSurfaceHigh,
                drawStopIndicator = {}
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (total > 0) {
                    "${Formatter.formatShortFileSize(this@UpdateActivity, done)} / " +
                        Formatter.formatShortFileSize(this@UpdateActivity, total)
                } else {
                    Formatter.formatShortFileSize(this@UpdateActivity, done)
                },
                color = VideoPrimeColors.DarkTextMuted,
                fontSize = 13.sp
            )
            fraction?.let {
                Text(
                    "${(it * 100).toInt()} %",
                    color = VideoPrimeColors.DarkText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        TextButton(onClick = { finish() }) {
            Text(stringResource(R.string.cancel), color = VideoPrimeColors.DarkTextMuted)
        }
    }

    @Composable
    private fun Ready() {
        Button(
            onClick = { install(apkFile(this)) },
            colors = ButtonDefaults.buttonColors(containerColor = VideoPrimeColors.Accent, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.videoprime_update_install), fontWeight = FontWeight.Bold)
        }
    }

    @Composable
    private fun Allow() {
        Text(
            stringResource(R.string.videoprime_update_allow),
            color = VideoPrimeColors.DarkTextMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = { openInstallSettings() },
            colors = ButtonDefaults.buttonColors(containerColor = VideoPrimeColors.Accent, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.videoprime_update_allow_button), fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = { finish() }) {
            Text(stringResource(R.string.cancel), color = VideoPrimeColors.DarkTextMuted)
        }
    }

    @Composable
    private fun Failed() {
        Text(
            stringResource(R.string.videoprime_update_failed_text),
            color = VideoPrimeColors.DarkTextMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = {
                askedPermission = true
                startDownload()
            },
            colors = ButtonDefaults.buttonColors(containerColor = VideoPrimeColors.Accent, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.retry), fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = { openInBrowser() }) {
            Text(stringResource(R.string.videoprime_update_browser), color = VideoPrimeColors.DarkTextMuted)
        }
    }

    /** Shares the downloaded APK with Android's installer (paths in the manifest's meta-data). */
    class Provider : FileProvider()

    companion object {
        private const val TAG = "VideoPrimeUpdate"
        const val EXTRA_URL = "apk_url"
        private const val AUTHORITY_SUFFIX = ".updates"
        private const val STATE_ASKED = "asked_permission"
        private const val PROGRESS_EVERY_MILLIS = 150L

        /** The APK goes in the cache: Android may clear it, and the next update replaces it. */
        fun apkFile(context: Context) = File(context.cacheDir, "updates/VideoPrime.apk")
    }
}
