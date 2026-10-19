package org.schabi.newpipe.local.feed.notifications

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.rxjava3.RxWorker
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.feed.service.FeedLoadManager
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
        return feedLoadManager.startLoading(groupId = FeedGroupEntity.GROUP_ALL_ID)
            .map { feed ->
                if (!notify) return@map emptyList()
                // channels with notifications on and something new from the last days
                feed.mapNotNull { notification ->
                    notification.value?.takeIf { it.notificationMode == NotificationMode.ENABLED }
                        ?.also { info -> info.newStreams = info.newStreams.filter(::isRecentVideo) }
                        ?.takeIf { it.newStreams.isNotEmpty() }
                }
            }
            .observeOn(AndroidSchedulers.mainThread()) // Picasso requires calls from main thread
            .map { feedUpdateInfoList ->
                // display notifications for each feedUpdateInfo (i.e. channel)
                feedUpdateInfoList.forEach { feedUpdateInfo ->
                    notificationHelper.displayNewStreamsNotifications(feedUpdateInfo)
                }
            }
            .observeOn(Schedulers.io())
            .map {
                if (notify) {
                    runCatching { LiveNotifications.check(applicationContext) }
                        .onFailure { Log.w(TAG, "Could not check Twitch live channels", it) }
                }
                runCatching { UpdateChecker.checkIfDue(applicationContext) }
                    .onFailure { Log.w(TAG, "Could not check for a new version", it) }
                Result.success()
            }
            .doOnError { throwable ->
                Log.e(TAG, "Error while updating the feed in the background", throwable)
            }
            .onErrorReturnItem(Result.retry())
    }

    /** Live streams are told by [LiveNotifications]; old videos are not news. */
    private fun isRecentVideo(item: StreamInfoItem): Boolean {
        if (item.streamType == StreamType.LIVE_STREAM || item.streamType == StreamType.AUDIO_LIVE_STREAM) {
            return false
        }
        val date = item.uploadDate?.offsetDateTime() ?: return false
        return date.isAfter(OffsetDateTime.now().minusDays(NEWS_MAX_AGE_DAYS))
    }

    companion object {

        private val TAG = NotificationWorker::class.java.simpleName
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
