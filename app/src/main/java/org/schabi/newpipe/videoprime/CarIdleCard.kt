package org.schabi.newpipe.videoprime

import android.content.Context
import android.graphics.BitmapFactory
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import org.schabi.newpipe.R

/**
 * PulsePlay: what the car's PulsePlay card shows while nothing plays. Android Auto draws the card
 * itself (its texts, buttons and darkening) over the session's artwork: without a player the
 * session has no metadata and the card stayed grey. Here it gets the app's own background (the
 * violet gradient with the cyan wave of the logo). While something plays, the player's metadata
 * (the song's cover) replaces it.
 */
object CarIdleCard {
    private const val TAG = "PulsePlayCarCard"

    @JvmStatic
    fun show(context: Context, session: MediaSessionCompat) {
        try {
            val background = BitmapFactory.decodeResource(context.resources, R.drawable.pulseplay_car_background)
            val title = context.getString(R.string.app_name)
            val subtitle = context.getString(R.string.pulseplay_car_idle_subtitle)
            session.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, background)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, background)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, background)
                    .build()
            )
        } catch (e: RuntimeException) {
            // only looks: the card stays as Android Auto draws it by default
            Log.w(TAG, "Could not set the car card's background", e)
        }
    }
}
