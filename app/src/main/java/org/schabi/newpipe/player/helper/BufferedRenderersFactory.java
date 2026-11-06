package org.schabi.newpipe.player.helper;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioTrackBufferSizeProvider;

/**
 * PulsePlay: the sound keeps 1-2 s ready in the audio output (ExoPlayer's default is 0.25-0.75 s).
 * When another app opens, or Android Auto and the phone are busy, the player can get no processor
 * for a moment; with the short buffer the output ran dry and the sound cut out for an instant
 * (then went on). Pausing, seeking and volume still act at once: they do not wait for the buffer.
 */
public class BufferedRenderersFactory extends DefaultRenderersFactory {
    private static final int MIN_PCM_BUFFER_US = 1_000_000;
    private static final int MAX_PCM_BUFFER_US = 2_000_000;
    private static final int PCM_BUFFER_MULTIPLICATION_FACTOR = 8;

    public BufferedRenderersFactory(final Context context) {
        super(context);
    }

    @NonNull
    @Override
    protected AudioSink buildAudioSink(final Context context,
                                       final boolean enableFloatOutput,
                                       final boolean enableAudioTrackPlaybackParams,
                                       final boolean enableOffload) {
        return new DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setOffloadMode(enableOffload
                        ? DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED
                        : DefaultAudioSink.OFFLOAD_MODE_DISABLED)
                .setAudioTrackBufferSizeProvider(new DefaultAudioTrackBufferSizeProvider.Builder()
                        .setMinPcmBufferDurationUs(MIN_PCM_BUFFER_US)
                        .setMaxPcmBufferDurationUs(MAX_PCM_BUFFER_US)
                        .setPcmBufferMultiplicationFactor(PCM_BUFFER_MULTIPLICATION_FACTOR)
                        .build())
                .build();
    }
}
