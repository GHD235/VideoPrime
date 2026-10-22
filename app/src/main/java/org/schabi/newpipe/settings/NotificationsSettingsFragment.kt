package org.schabi.newpipe.settings

import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.feed.notifications.NotificationHelper
import org.schabi.newpipe.local.feed.notifications.NotificationWorker
import org.schabi.newpipe.local.feed.notifications.ScheduleOptions
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.videoprime.BackgroundPermission
import org.schabi.newpipe.videoprime.BackgroundStatus
import org.schabi.newpipe.videoprime.CheckAlarm

class NotificationsSettingsFragment : BasePreferenceFragment(), OnSharedPreferenceChangeListener {

    private var streamsNotificationsPreference: SwitchPreference? = null
    private var notificationWarningSnackbar: Snackbar? = null
    private var loader: Disposable? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.notifications_settings)
        streamsNotificationsPreference = requirePreference(R.string.enable_streams_notifications)

        // VideoPrime: what the checks are doing; tapping fixes the first problem it can
        findPreference<Preference>(STATUS_KEY)?.setOnPreferenceClickListener {
            val context = requireContext()
            if (BackgroundStatus.popupsOff(context)) {
                openNewVideosChannelSettings()
            } else if (!BackgroundPermission.isAllowed(context)) {
                BackgroundPermission.request(context)
            } else {
                // look now, and show the result when coming back
                NotificationWorker.runExpedited(context)
                it.summary = BackgroundStatus.summary(context)
            }
            true
        }
        // VideoPrime: what lets the check for new videos run on time
        findPreference<Preference>(BACKGROUND_KEY)?.setOnPreferenceClickListener {
            BackgroundPermission.request(requireContext())
            true
        }
        val xiaomi = BackgroundPermission.isXiaomi()
        findPreference<Preference>(XIAOMI_BATTERY_KEY)?.apply {
            isVisible = xiaomi
            setOnPreferenceClickListener {
                BackgroundPermission.openXiaomiSettings(requireContext(), autostart = false)
                true
            }
        }
        findPreference<Preference>(XIAOMI_AUTOSTART_KEY)?.apply {
            isVisible = xiaomi
            setOnPreferenceClickListener {
                BackgroundPermission.openXiaomiSettings(requireContext(), autostart = true)
                true
            }
        }

        // main check is done in onResume, but also do it here to prevent flickering
        updateEnabledState(NotificationHelper.areNotificationsEnabledOnDevice(requireContext()))
    }

    override fun onStart() {
        super.onStart()
        defaultPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        defaultPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val context = context ?: return
        if (key == getString(R.string.streams_notifications_interval_key) ||
            key == getString(R.string.streams_notifications_network_key)
        ) {
            // apply new configuration
            NotificationWorker.schedule(context, ScheduleOptions.from(context), true)
        } else if (key == getString(R.string.enable_streams_notifications)) {
            // VideoPrime: the worker also keeps the feed up to date in the background and checks
            // for new versions, so it keeps running; it only stops notifying
            NotificationWorker.schedule(context)
            // the pulse that wakes the app only runs while notifications are on
            CheckAlarm.schedule(context)
        }
    }

    override fun onResume() {
        super.onResume()

        // Check whether the notifications are disabled in the device's app settings.
        // If they are disabled, show a snackbar informing the user about that
        // while allowing them to open the device's app settings.
        val enabled = NotificationHelper.areNotificationsEnabledOnDevice(requireContext())
        updateEnabledState(enabled)
        findPreference<Preference>(STATUS_KEY)?.summary =
            BackgroundStatus.summary(requireContext())
        findPreference<Preference>(BACKGROUND_KEY)?.setSummary(
            if (BackgroundPermission.isAllowed(requireContext())) {
                R.string.videoprime_background_pref_allowed
            } else {
                R.string.videoprime_background_pref_restricted
            }
        )
        if (!enabled) {
            if (notificationWarningSnackbar == null) {
                notificationWarningSnackbar = Snackbar.make(
                    listView,
                    R.string.notifications_disabled,
                    Snackbar.LENGTH_INDEFINITE
                ).apply {
                    setAction(R.string.settings) {
                        NotificationHelper.openNewPipeSystemNotificationSettings(it.context)
                    }
                    setActionTextColor(Color.YELLOW)
                    addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            notificationWarningSnackbar = null
                        }
                    })
                    show()
                }
            }
        }

        // (Re-)Create loader
        loader?.dispose()
        loader = SubscriptionManager(requireContext())
            .subscriptions()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::updateSubscriptions, this::onError)
    }

    override fun onPause() {
        loader?.dispose()
        loader = null

        notificationWarningSnackbar?.dismiss()
        notificationWarningSnackbar = null

        super.onPause()
    }

    private fun updateEnabledState(enabled: Boolean) {
        // On Android 13 player notifications are exempt from notification settings
        // so the preferences in app should always be available.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            streamsNotificationsPreference?.isEnabled = enabled
        } else {
            preferenceScreen.isEnabled = enabled
        }
    }

    private fun updateSubscriptions(subscriptions: List<SubscriptionEntity>) {
        val notified = subscriptions.count { it.notificationMode != NotificationMode.DISABLED }
        val preference = requirePreference<Preference>(R.string.streams_notifications_channels_key)
        preference.summary = "$notified/${subscriptions.size}"
    }

    private fun onError(e: Throwable) {
        ErrorUtil.showSnackbar(
            this,
            ErrorInfo(e, UserAction.SUBSCRIPTION_GET, "Get subscriptions list")
        )
    }

    /** VideoPrime: the system screen of the "new videos" channel (to turn its pop-ups on). */
    private fun openNewVideosChannelSettings() {
        val context = requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startActivity(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(
                        Settings.EXTRA_CHANNEL_ID,
                        getString(R.string.streams_notification_channel_id)
                    )
            )
        } else {
            NotificationHelper.openNewPipeSystemNotificationSettings(context)
        }
    }

    companion object {
        private const val BACKGROUND_KEY = "videoprime_background"
        private const val STATUS_KEY = "videoprime_status"
        private const val XIAOMI_BATTERY_KEY = "videoprime_xiaomi_battery"
        private const val XIAOMI_AUTOSTART_KEY = "videoprime_xiaomi_autostart"
    }
}
