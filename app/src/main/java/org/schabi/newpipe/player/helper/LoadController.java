package org.schabi.newpipe.player.helper;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.upstream.DefaultAllocator;

public class LoadController extends DefaultLoadControl {

    public static final String TAG = "LoadController";

    // VideoPrime: start sooner (1.5 s buffered instead of 2.5 s; a Twitch segment is 2 s) and keep
    // more ahead (up to 90 s instead of 50 s) so that a short dip of the connection does not
    // freeze the picture
    private static final int MIN_BUFFER_MS = 30_000;
    private static final int MAX_BUFFER_MS = 90_000;
    private static final int BUFFER_FOR_PLAYBACK_MS = 1_500;
    private static final int BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 4_000;

    private boolean preloadingEnabled = true;

    public LoadController() {
        super(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                DEFAULT_TARGET_BUFFER_BYTES,
                DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS,
                DEFAULT_BACK_BUFFER_DURATION_MS,
                DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME);
    }

    @Override
    public void onPrepared() {
        preloadingEnabled = true;
        super.onPrepared();
    }

    @Override
    public void onStopped() {
        preloadingEnabled = true;
        super.onStopped();
    }

    @Override
    public void onReleased() {
        preloadingEnabled = true;
        super.onReleased();
    }

    @Override
    public boolean shouldContinueLoading(final long playbackPositionUs,
                                         final long bufferedDurationUs,
                                         final float playbackSpeed) {
        if (!preloadingEnabled) {
            return false;
        }
        return super.shouldContinueLoading(
                playbackPositionUs, bufferedDurationUs, playbackSpeed);
    }

    public void disablePreloadingOfCurrentTrack() {
        preloadingEnabled = false;
    }
}
