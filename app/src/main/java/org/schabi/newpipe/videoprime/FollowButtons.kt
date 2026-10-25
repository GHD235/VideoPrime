package org.schabi.newpipe.videoprime

import android.view.View
import android.widget.Button
import android.widget.ImageButton
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * PulsePlay: "Subscribe" and the bell next to the channel on the video page (tablet layout), the
 * same as in the channel's header: the bell only shows once subscribed, and new channels come
 * with it on.
 */
class FollowButtons(
    private val container: View,
    private val subscribe: Button,
    private val bell: ImageButton
) {
    private val manager = SubscriptionManager(container.context)
    private val disposables = CompositeDisposable()
    private var subscription: SubscriptionEntity? = null

    /** Shows the buttons for the channel of the video that opened (null url: hidden). */
    fun bind(serviceId: Int, url: String?, name: String?, avatars: List<Image>, subscribers: Long) {
        disposables.clear()
        subscription = null
        if (url.isNullOrEmpty()) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        subscribe.setOnClickListener {
            subscribe.isEnabled = false
            val current = subscription
            val change = if (current != null) {
                Completable.fromAction { manager.deleteSubscription(current) }
            } else {
                val entity = SubscriptionEntity().apply {
                    this.serviceId = serviceId
                    this.url = url
                    this.name = name
                    avatarUrl = ImageStrategy.imageListToDbUrl(avatars)
                    subscriberCount = subscribers.takeIf { it >= 0 }
                }
                Completable.fromAction { manager.insertSubscription(entity) }
            }
            disposables.add(
                change.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ }, { subscribe.isEnabled = true })
            )
        }
        bell.setOnClickListener {
            val current = subscription ?: return@setOnClickListener
            bell.isEnabled = false
            val on = current.notificationMode == NotificationMode.ENABLED
            disposables.add(
                manager.updateNotificationMode(
                    serviceId,
                    url,
                    if (on) NotificationMode.DISABLED else NotificationMode.ENABLED
                )
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ }, { bell.isEnabled = true })
            )
        }
        disposables.add(
            manager.subscriptionTable().getSubscriptionFlowable(serviceId, url)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ list -> show(list.firstOrNull()) }, { container.visibility = View.GONE })
        )
    }

    private fun show(entity: SubscriptionEntity?) {
        subscription = entity
        subscribe.isEnabled = true
        bell.isEnabled = true
        if (entity == null) {
            subscribe.setText(R.string.subscribe_button_title)
            subscribe.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            bell.visibility = View.GONE
        } else {
            subscribe.setText(R.string.subscribed_button_title)
            subscribe.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.videoprime_follow_check, 0, 0, 0)
            val on = entity.notificationMode == NotificationMode.ENABLED
            bell.setImageResource(if (on) R.drawable.ic_notifications else R.drawable.ic_notifications_off)
            bell.contentDescription = container.context.getString(
                if (on) R.string.videoprime_channel_bell_on else R.string.videoprime_channel_bell_off
            )
            bell.visibility = View.VISIBLE
        }
    }

    fun dispose() {
        disposables.clear()
    }
}
