package org.schabi.newpipe.player.mediasession;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.player.notification.NotificationActionData;

import java.lang.ref.WeakReference;

public class SessionConnectorActionProvider implements MediaSessionConnector.CustomActionProvider {

    private final NotificationActionData data;
    @NonNull
    private final WeakReference<Context> context;
    /** VideoPrime: the player to run the action on directly, without a broadcast. */
    @NonNull
    private final WeakReference<org.schabi.newpipe.player.Player> newPipePlayer;

    public SessionConnectorActionProvider(final NotificationActionData notificationActionData,
                                          @NonNull final Context context) {
        this(notificationActionData, context, null);
    }

    public SessionConnectorActionProvider(final NotificationActionData notificationActionData,
                                          @NonNull final Context context,
                                          @Nullable final org.schabi.newpipe.player.Player
                                                  newPipePlayer) {
        this.data = notificationActionData;
        this.context = new WeakReference<>(context);
        this.newPipePlayer = new WeakReference<>(newPipePlayer);
    }

    @Override
    public void onCustomAction(@NonNull final Player player,
                               @NonNull final String action,
                               @Nullable final Bundle extras) {
        // VideoPrime: straight to the player; a broadcast may be delayed or dropped on recent
        // Android versions (shuffle did nothing from the car and the media controls)
        final org.schabi.newpipe.player.Player target = newPipePlayer.get();
        if (target != null) {
            target.runAction(action);
            return;
        }
        final Context actualContext = context.get();
        if (actualContext != null) {
            actualContext.sendBroadcast(new Intent(action));
        }
    }

    @Nullable
    @Override
    public PlaybackStateCompat.CustomAction getCustomAction(@NonNull final Player player) {
        return new PlaybackStateCompat.CustomAction.Builder(
                data.action(), data.name(), data.icon()
        ).build();
    }
}
