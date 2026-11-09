package org.schabi.newpipe.videoprime

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonWriter
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipOutputStream
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.export.BackupFileLocator
import org.schabi.newpipe.util.ZipHelper

/**
 * PulsePlay: a backup by itself once a week (subscriptions, history, playlists and settings, the
 * same file as Settings → Backup → Export, so it is restored with "Import"). It goes to the
 * downloads folder chosen by the user, which survives uninstalling the app; without one, to the
 * app's own folder. The last [KEEP] are kept.
 */
object AutoBackup {
    private const val TAG = "PulsePlayBackup"
    private const val PREF_LAST = "pulseplay_auto_backup_last"
    private const val PREF_WHERE = "pulseplay_auto_backup_where"
    private const val WEEK_MILLIS = 7 * 24 * 60 * 60 * 1000L
    private const val RETRY_MILLIS = 6 * 60 * 60 * 1000L
    private const val PREFIX = "PulsePlayData-auto-"
    private const val KEEP = 4

    @JvmStatic
    fun isEnabled(context: Context): Boolean = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(context.getString(R.string.pulseplay_auto_backup_key), true)

    /** When the last one was made (0 = never) and the folder it went to. */
    @JvmStatic
    fun last(context: Context): Pair<Long, String?> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getLong(PREF_LAST, 0L) to prefs.getString(PREF_WHERE, null)
    }

    /** Makes the weekly backup if it is due. Called when the app opens, off the main thread. */
    @WorkerThread
    @JvmStatic
    @Synchronized
    fun runIfDue(context: Context) {
        val app = context.applicationContext
        if (!isEnabled(app)) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(PREF_LAST, 0L) < WEEK_MILLIS) return
        try {
            val where = write(app)
            prefs.edit {
                putLong(PREF_LAST, now)
                putString(PREF_WHERE, where)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not make the weekly backup", e)
            // not at every opening of the app: again in a few hours
            prefs.edit { putLong(PREF_LAST, now - WEEK_MILLIS + RETRY_MILLIS) }
        }
    }

    private fun write(app: Context): String {
        val name = "$PREFIX${LocalDate.now()}.zip"
        // everything written so far goes into the database file (if it cannot, no backup now:
        // the file alone would be an old state)
        NewPipeDatabase.getInstance(app)
        NewPipeDatabase.checkpoint()
        val folder = downloadsFolder(app)
        if (folder != null) {
            try {
                folder.findFile(name)?.delete()
                val file = folder.createFile("application/zip", name) ?: error("No file")
                val written = runCatching {
                    (app.contentResolver.openOutputStream(file.uri) ?: error("No stream")).use { zip(app, it) }
                }
                // half a file is worse than none
                if (written.isFailure) {
                    runCatching { file.delete() }
                    throw written.exceptionOrNull()!!
                }
                folder.listFiles()
                    .filter { it.name?.startsWith(PREFIX) == true }
                    .sortedByDescending { it.name }
                    .drop(KEEP)
                    .forEach { runCatching { it.delete() } }
                return folder.name ?: "Downloads"
            } catch (e: Exception) {
                Log.w(TAG, "The downloads folder cannot be written, using the app's own", e)
            }
        }
        val dir = File(app.getExternalFilesDir(null) ?: app.filesDir, "backups").apply { mkdirs() }
        val file = File(dir, name)
        val part = File(dir, "$name.part")
        part.outputStream().use { zip(app, it) }
        if (!part.renameTo(file)) {
            file.delete()
            check(part.renameTo(file)) { "Could not rename the backup" }
        }
        dir.listFiles().orEmpty()
            .filter { it.name.startsWith(PREFIX) && it.name.endsWith(".zip") }
            .sortedByDescending { it.name }
            .drop(KEEP)
            .forEach { it.delete() }
        return dir.path
    }

    /** The folder the user chose for downloads, if the app can still write to it. */
    private fun downloadsFolder(app: Context): DocumentFile? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        for (key in intArrayOf(R.string.download_path_video_key, R.string.download_path_audio_key)) {
            val uri = prefs.getString(app.getString(key), null)?.takeIf { it.startsWith("content://") }
                ?: continue
            val folder = runCatching { DocumentFile.fromTreeUri(app, Uri.parse(uri)) }.getOrNull()
            if (folder != null && folder.isDirectory && folder.canWrite()) return folder
        }
        return null
    }

    private fun zip(app: Context, output: OutputStream) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        ZipOutputStream(output.buffered()).use { zip ->
            // nothing else writes while the file is copied (other writers wait for this
            // transaction, so no checkpoint moves pages into the file under the copy)
            NewPipeDatabase.getInstance(app).runInTransaction {
                ZipHelper.addFileToZip(zip, BackupFileLocator.FILE_NAME_DB, BackupFileLocator(app).db)
            }
            ZipHelper.addFileToZip(zip, BackupFileLocator.FILE_NAME_JSON_PREFS) { bytes ->
                JsonWriter.indent("").on(bytes).`object`(prefs.all).done()
            }
        }
    }
}
