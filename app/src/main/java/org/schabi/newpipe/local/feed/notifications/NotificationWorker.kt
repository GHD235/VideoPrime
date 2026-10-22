package org.schabi.newpipe.local.feed.notifications

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.rxjava3.RxWorker
import androidx.work.workDataOf
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.service.FeedLoadManager
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.videoprime.BackgroundStatus
import org.schabi.newpipe.videoprime.CheckAlarm
import org.schabi.newpipe.videoprime.InstantPush
import org.schabi.newpipe.videoprime.LiveNotifications
import org.schabi.newpipe.videoprime.UpdateChecker

/*
 * Worker which checks for new streams of subscribed channels
 * in intervals which can be set by the user in the settings.
 */
class NotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : RxWorker(appContext, workerParams) {

    private val notificationHelper by lazy {
        NotificationHelper(appContext)
    }
    private val feedLoadManager = FeedLoadManager(appContext)

    /*
     * VideoPrime: this worker also keeps the feed up to date in the background (all the
     * subscriptions, not only those with notifications), so that the home screen opens with
     * the new videos already there, and tells when a Twitch channel goes live.
     */
    override fun createWork(): Single<Result> {
        val notify = areNotificationsEnabled(applicationContext)
        return Single.fromCallable {
            // Twitch first: one small request, and a live stream is the most urgent news (the
            // alarm's pulse has just asked, then it is not asked again)
            if (notify && !inputData.getBoolean(KEY_SKIP_TWITCH, false)) {
                runCatching { LiveNotifications.check(applicationContext) }
                    .onFailure { Log.w(TAG, "Could not check Twitch live channels", it) }
            }
            true
        }
            .subscribeOn(Schedulers.io())
            .flatMap {
                feedLoadManager.startLoading(
                    groupId = FeedGroupEntity.GROUP_ALL_ID,
                    lightweight = true,
                    // told as soon as each batch is saved (see FeedLoadManager.startLoading)
                    onNewStreams = { batch -> if (notify) tell(batch) }
                )
            }
            .observeOn(Schedulers.io())
            .map {
                BackgroundStatus.markYouTubeCheck(applicationContext)
                // VideoPrime: the instant notifications follow the bells (and the server is
                // reminded once a day which channels to watch)
                InstantPush.syncInBackground(applicationContext)
                runCatching { UpdateChecker.checkIfDue(applicationContext) }
                    .onFailure { Log.w(TAG, "Could not check for a new version", it) }
                Result.success()
            }
            .doOnError { throwable ->
                Log.e(TAG, "Error while updating the feed in the background", throwable)
                // it ran: the background work is not being stopped (a network or YouTube error)
                BackgroundStatus.markYouTubeCheck(applicationContext)
            }
            // the alarm's pulse is not retried (it would block the next pulses for hours):
            // the next pulse, ten minutes later, is the retry
            .onErrorReturnItem(
                if (inputData.getBoolean(KEY_SKIP_TWITCH, false)) Result.success() else Result.retry()
            )
    }

    /**
     * VideoPrime: when the check runs expedited on Android 11 and older it is shown as a small
     * notification while it works (Android asks for one there).
     */
    override fun getForegroundInfo(): Single<ForegroundInfo> = Single.just(
        ForegroundInfo(
            FOREGROUND_NOTIFICATION_ID,
            NotificationCompat.Builder(
                applicationContext,
                applicationContext.getString(R.string.notification_channel_id)
            )
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setContentTitle(applicationContext.getString(R.string.videoprime_checking_new_videos))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .build()
        )
    )

    /** Channels with notifications on and something new from the last days. */
    private fun tell(batch: List<FeedUpdateInfo>) {
        batch.filter { it.notificationMode == NotificationMode.ENABLED }
            .onEach { info ->
                // VideoPrime: a video told by a push is not told again
                info.newStreams = info.newStreams.filter {
                    isRecentVideo(it) && !InstantPush.wasPushed(applicationContext, it.url)
                }
            }
            .filter { it.newStreams.isNotEmpty() }
            .forEach { notificationHelper.displayNewStreamsNotifications(it) }
    }

    /**
     * Live streams are told by [LiveNotifications]; old videos are not news; Twitch past
     * broadcasts neither (their stream was already told when it went live, as the Twitch app).
     */
    private fun isRecentVideo(item: StreamInfoItem): Boolean {
        if (item.streamType == StreamType.LIVE_STREAM || item.streamType == StreamType.AUDIO_LIVE_STREAM) {
            return false
        }
        if (item.serviceId == ServiceList.Twitch.serviceId) return false
        val date = item.uploadDate?.offsetDateTime() ?: return false
        return date.isAfter(OffsetDateTime.now().minusDays(NEWS_MAX_AGE_DAYS))
    }

    companion object {

        private val TAG = NotificationWorker::class.java.simpleName
        private const val KEY_SKIP_TWITCH = "videoprime_skip_twitch"
        private const val EXPEDITED_WORK = "videoprime_expedited_check"
        private const val FOREGROUND_NOTIFICATION_ID = 0x7632
        private const val NEWS_MAX_AGE_DAYS = 2L
        private const val WORK_TAG = App.PACKAGE_NAME + "_streams_notifications"

        private fun areNotificationsEnabled(context: Context) = NotificationHelper.areNewStreamsNotificationsEnabled(context) &&
            NotificationHelper.areNotificationsEnabledOnDevice(context)

        /**
         * Schedules a task for the [NotificationWorker]
         * if the (device and in-app) notifications are enabled,
         * otherwise [cancel]s all scheduled tasks.
         */
        @JvmStatic
        fun initialize(context: Context) {
            // VideoPrime: always, the worker also updates the feed in the background
            schedule(context)
            // and the Twitch live streams are looked at more often, on their own
            LiveNotifications.schedule(context)
            // the pulse that wakes the app even when the phone sleeps
            CheckAlarm.schedule(context)
        }

        /**
         * @param context the context to use
         * @param options configuration options for the scheduler
         * @param force Force the scheduler to use the new options
         * by replacing the previously used worker.
         */
        fun schedule(context: Context, options: ScheduleOptions, force: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (options.isRequireNonMeteredNetwork) {
                        NetworkType.UNMETERED
                    } else {
                        NetworkType.CONNECTED
                    }
                ).build()

            val request = PeriodicWorkRequest.Builder(
                NotificationWorker::class.java,
                options.interval,
                TimeUnit.MILLISECONDS
            ).setConstraints(constraints)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_TAG,
                    if (force) {
                        ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                    } else {
                        ExistingPeriodicWorkPolicy.KEEP
                    },
                    request
                )
        }

        @JvmStatic
        fun schedule(context: Context) = schedule(context, ScheduleOptions.from(context))

        /**
         * VideoPrime: the alarm's pulse: a check of the YouTube channels that are due, run
         * expedited (it skips the waits of Doze and battery saver while the app has quota) and
         * only one at a time.
         */
        @JvmStatic
        fun runExpedited(context: Context) {
            val builder = OneTimeWorkRequestBuilder<NotificationWorker>()
            // on Android 11 and older an expedited work shows a notification each time
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            val request = builder
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setInputData(workDataOf(KEY_SKIP_TWITCH to true))
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(EXPEDITED_WORK, ExistingWorkPolicy.KEEP, request)
        }

        /**
         * Check for new streams immediately
         */
        @JvmStatic
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<NotificationWorker>()
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        /**
         * Cancels all current work related to the [NotificationWorker].
         */
        @JvmStatic
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        }
    }
}
