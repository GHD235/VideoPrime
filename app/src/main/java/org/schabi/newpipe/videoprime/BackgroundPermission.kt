package org.schabi.newpipe.videoprime

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

/**
 * VideoPrime: the check for new videos runs in the background every 15 minutes, but battery
 * savers (above all Xiaomi's) delay it or stop it. This asks once to let the app work in the
 * background, and the notification settings keep the buttons to do it later.
 */
object BackgroundPermission {
    private const val ASKED_KEY = "videoprime_background_asked"
    private const val STALE_ASKED_KEY = "videoprime_background_stale_asked"
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    /** Whether Android lets the app run in the background without battery restrictions. */
    @JvmStatic
    fun isAllowed(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return true
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    @JvmStatic
    fun isXiaomi(): Boolean = listOf("xiaomi", "redmi", "poco").any {
        Build.MANUFACTURER.equals(it, ignoreCase = true) || Build.BRAND.equals(it, ignoreCase = true)
    }

    /** The system dialog that removes the battery restrictions (or its settings screen). */
    @SuppressLint("BatteryLife") // the app is not on Play: checking for videos is its main job
    @JvmStatic
    fun request(context: Context) {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:" + context.packageName)
        )
        if (!start(context, direct)) {
            start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /**
     * Xiaomi's own screens: the app's battery saver ("No restrictions") and the autostart list;
     * the app's settings if neither exists on this phone.
     */
    @JvmStatic
    fun openXiaomiSettings(context: Context, autostart: Boolean) {
        val intents = if (autostart) {
            listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                )
            )
        } else {
            listOf(
                Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
                    .putExtra("package_name", context.packageName)
                    .putExtra("package_label", context.getString(R.string.app_name))
            )
        }
        if (intents.none { start(context, it) }) {
            start(
                context,
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + context.packageName))
            )
        }
    }

    /**
     * Asks when the app opens: once if the background work is restricted, and again (at most
     * once a day) when the checks have stopped for hours, which on Xiaomi happens even with the
     * battery restriction removed (autostart, the app not locked in recent apps).
     */
    @JvmStatic
    fun askOnce(activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val now = System.currentTimeMillis()
        val stale = BackgroundStatus.isStale(activity) &&
            now - prefs.getLong(STALE_ASKED_KEY, 0L) > DAY_MILLIS
        val firstTime = !prefs.getBoolean(ASKED_KEY, false) && !isAllowed(activity)
        if (!stale && !firstTime) return
        val dialog = AlertDialog.Builder(activity)
            // asked once it was seen and answered (a turned screen shows it again)
            .setOnDismissListener {
                prefs.edit {
                    putBoolean(ASKED_KEY, true)
                    if (stale) putLong(STALE_ASKED_KEY, now)
                }
            }
            .setTitle(if (stale) R.string.videoprime_background_stale_title else R.string.videoprime_background_title)
            .setMessage(
                when {
                    stale && isXiaomi() -> R.string.videoprime_background_stale_message_xiaomi
                    stale -> R.string.videoprime_background_stale_message
                    isXiaomi() -> R.string.videoprime_background_message_xiaomi
                    else -> R.string.videoprime_background_message
                }
            )
            .setPositiveButton(R.string.videoprime_background_allow) { _, _ ->
                // already allowed but still stopped: Xiaomi's own battery saver is the next suspect
                if (isAllowed(activity) && isXiaomi()) {
                    openXiaomiSettings(activity, autostart = false)
                } else {
                    request(activity)
                }
            }
            .setNegativeButton(R.string.videoprime_background_later, null)
        if (isXiaomi()) {
            dialog.setNeutralButton(R.string.videoprime_background_autostart) { _, _ ->
                openXiaomiSettings(activity, autostart = true)
            }
        }
        dialog.show()
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}
