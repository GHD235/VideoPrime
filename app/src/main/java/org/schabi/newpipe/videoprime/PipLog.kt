package org.schabi.newpipe.videoprime

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import java.util.ArrayDeque

/**
 * PulsePlay: what happens around the picture-in-picture window (lifecycle, the video turned
 * off or on, the video surface created/changed/destroyed, sizes, first frames), with times, to
 * find on the user's device why the picture goes black. Only in memory (the last lines), never
 * sent anywhere: the user shares it from the settings.
 */
object PipLog {
    private const val MAX_LINES = 300
    private val lines = ArrayDeque<String>()
    private var startedAt = 0L

    @JvmStatic
    fun log(event: String) {
        val now = SystemClock.uptimeMillis()
        synchronized(lines) {
            if (startedAt == 0L) startedAt = now
            lines.addLast("${now - startedAt} ms  ${Thread.currentThread().name.take(12)}  $event")
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
    }

    @JvmStatic
    fun text(): String = synchronized(lines) {
        "PulsePlay · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT})\n" + lines.joinToString("\n")
    }

    /** Opens Android's share sheet with the log. */
    @JvmStatic
    fun share(context: Context) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "PulsePlay: registro de la ventana flotante")
            .putExtra(Intent.EXTRA_TEXT, text())
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
