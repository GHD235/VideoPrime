package org.schabi.newpipe.videoprime

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.schabi.newpipe.R

/**
 * VideoPrime: the update notification's tap. Downloads the new APK inside the app and opens
 * Android's installer on it, so that updating is the notification and one "Update" button (the
 * first time, Android also asks to let VideoPrime install apps).
 */
class UpdateActivity : AppCompatActivity() {
    private lateinit var message: TextView
    private lateinit var progress: ProgressBar
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askedPermission = savedInstanceState?.getBoolean(STATE_ASKED, false) ?: false
        apkUrl = intent.getStringExtra(EXTRA_URL)
        if (apkUrl == null) {
            finish()
            return
        }
        setFinishOnTouchOutside(false)
        val padding = (24 * resources.displayMetrics.density).toInt()
        message = TextView(this).apply { textSize = 16f }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padding, padding, padding, padding)
                addView(message)
                addView(progress)
            }
        )
        setTitle(R.string.videoprime_update_title)
    }

    override fun onResume() {
        super.onResume()
        if (download != null) return
        if (!canInstall()) {
            if (askedPermission) {
                // came back without allowing it
                Toast.makeText(this, R.string.videoprime_update_not_allowed, Toast.LENGTH_LONG).show()
                finish()
            } else {
                askedPermission = true
                message.setText(R.string.videoprime_update_allow)
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri())
                )
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

    private fun startDownload() {
        message.text = getString(R.string.videoprime_update_downloading, 0)
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
                runOnUiThread { if (!isFinishing && !isDestroyed) install(file) }
            } catch (e: InterruptedException) {
                part.delete()
            } catch (e: Exception) {
                part.delete()
                if (cancelled) return@Thread
                Log.w(TAG, "Could not download the update", e)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    // the browser can still do it
                    Toast.makeText(this, R.string.videoprime_update_failed, Toast.LENGTH_LONG).show()
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    }
                    finish()
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
            val total = connection.contentLength.toLong()
            var done = 0L
            var shown = -1
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (Thread.interrupted()) throw InterruptedException()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val percent = if (total > 0) (done * 100 / total).toInt() else -1
                        if (percent != shown) {
                            shown = percent
                            runOnUiThread { showProgress(percent) }
                        }
                    }
                }
            }
            if (total > 0 && done != total) error("Incomplete download: $done of $total bytes")
        } finally {
            connection.disconnect()
        }
    }

    private fun showProgress(percent: Int) {
        if (isFinishing || percent < 0) return
        progress.isIndeterminate = false
        progress.progress = percent
        message.text = getString(R.string.videoprime_update_downloading, percent)
    }

    private fun install(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName$AUTHORITY_SUFFIX", file)
        val installer = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(installer)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the installer", e)
            Toast.makeText(this, R.string.videoprime_update_failed, Toast.LENGTH_LONG).show()
        }
        finish()
    }

    /** Shares the downloaded APK with Android's installer. */
    class Provider : FileProvider(R.xml.videoprime_update_paths)

    companion object {
        private const val TAG = "VideoPrimeUpdate"
        const val EXTRA_URL = "apk_url"
        private const val AUTHORITY_SUFFIX = ".updates"
        private const val STATE_ASKED = "asked_permission"

        /** The APK goes in the cache: Android may clear it, and the next update replaces it. */
        fun apkFile(context: Context) = File(context.cacheDir, "updates/VideoPrime.apk")
    }
}
