/*
 * Copyright (C) Eltex ltd 2019 <eltex@eltex-co.ru>
 * FocusAwareDrawerLayout.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.schabi.newpipe.views;

import android.animation.TimeAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.ViewTreeObserver;
import android.widget.SeekBar;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;

import org.schabi.newpipe.util.DeviceUtils;

/**
 * SeekBar, adapted for directional navigation. It emulates touch-related callbacks
 * (onStartTrackingTouch/onStopTrackingTouch), so existing code does not need to be changed to
 * work with it.
  */
public class FocusAwareSeekBar extends AppCompatSeekBar {
    private NestedListener listener;

    private ViewTreeObserver treeObserver;

    public FocusAwareSeekBar(final Context context) {
        super(context);
    }

    public FocusAwareSeekBar(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public FocusAwareSeekBar(final Context context, final AttributeSet attrs,
                             final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setOnSeekBarChangeListener(final OnSeekBarChangeListener l) {
        this.listener = l == null ? null : new NestedListener(l);

        super.setOnSeekBarChangeListener(listener);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (!isInTouchMode() && DeviceUtils.isConfirmKey(keyCode)) {
            stopGlide();
            releaseTrack();
        }

        // PulsePlay: holding left/right on the remote. Android repeated the key about twenty
        // times a second and each repeat was one whole step (10 s): the bar moved in big,
        // uneven jumps. A short press is still one step; held, the bar glides frame by frame,
        // slowly at first and faster the longer it is held (as the big video apps do on a TV).
        if (!isInTouchMode() && isEnabled() && listener != null
                && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if (event.getRepeatCount() == 0) {
                stopGlide();
                return super.onKeyDown(keyCode, event);
            }
            final boolean right = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
            final boolean forward =
                    right != (getLayoutDirection() == LAYOUT_DIRECTION_RTL);
            final boolean atEnd = forward ? getProgress() >= getMax() : getProgress() <= 0;
            if ((glide == null && !atEnd) || (glide != null && glideForward != forward)) {
                startGlide(forward);
            }
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            stopGlide();
        }
        return super.onKeyUp(keyCode, event);
    }

    /** Video seconds per second when the glide starts. */
    private static final float GLIDE_START_SPEED = 20f;
    /** The glide reaches its top speed after this long. */
    private static final float GLIDE_RAMP_MILLIS = 3000f;
    /** At top speed the whole bar is crossed in this many seconds (never slower than 1 min/s). */
    private static final float GLIDE_FULL_BAR_SECONDS = 15f;
    private static final float GLIDE_MIN_TOP_SPEED = 60f;
    /** A late frame counts as this at most, so a busy moment is not a jump. */
    private static final long GLIDE_MAX_FRAME_MILLIS = 50;

    @Nullable
    private TimeAnimator glide;
    private boolean glideForward;
    private float glidePosition;
    private float glideHeldMillis;

    private void startGlide(final boolean forward) {
        stopGlide();
        glideForward = forward;
        glidePosition = getProgress();
        glideHeldMillis = 0;
        final TimeAnimator animator = new TimeAnimator();
        animator.setTimeListener((animation, totalTime, deltaTime) -> {
            if (listener == null) {
                stopGlide();
                return;
            }
            final float delta = Math.min(deltaTime, GLIDE_MAX_FRAME_MILLIS);
            glideHeldMillis += delta;
            // the bar counts milliseconds of video
            final float top = Math.max(GLIDE_MIN_TOP_SPEED,
                    getMax() / 1000f / GLIDE_FULL_BAR_SECONDS);
            final float ramp = Math.min(glideHeldMillis / GLIDE_RAMP_MILLIS, 1f);
            final float speed = GLIDE_START_SPEED
                    + Math.max(0f, top - GLIDE_START_SPEED) * ramp * ramp;
            glidePosition += (glideForward ? 1 : -1) * speed * delta;
            final int target = Math.max(0, Math.min(getMax(), Math.round(glidePosition)));
            if (target != getProgress() && listener != null) {
                setProgress(target);
                // setProgress() reports "not from the user": said again as the user's move
                listener.onProgressChanged(this, target, true);
            }
            if (target <= 0 || target >= getMax()) {
                stopGlide();
            }
        });
        glide = animator;
        animator.start();
    }

    @Override
    public void onWindowFocusChanged(final boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            // the key's release would go to whatever took the focus
            stopGlide();
        }
    }

    private void stopGlide() {
        if (glide != null) {
            glide.cancel();
            glide = null;
        }
    }

    @Override
    protected void onFocusChanged(final boolean gainFocus, final int direction,
                                  final Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);

        if (!gainFocus) {
            stopGlide();
        }
        if (!isInTouchMode() && !gainFocus) {
            releaseTrack();
        }
    }

    private final ViewTreeObserver.OnTouchModeChangeListener touchModeListener = isInTouchMode -> {
        if (isInTouchMode) {
            releaseTrack();
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        treeObserver = getViewTreeObserver();
        treeObserver.addOnTouchModeChangeListener(touchModeListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (treeObserver == null || !treeObserver.isAlive()) {
            treeObserver = getViewTreeObserver();
        }

        stopGlide();
        treeObserver.removeOnTouchModeChangeListener(touchModeListener);
        treeObserver = null;

        super.onDetachedFromWindow();
    }

    private void releaseTrack() {
        if (listener != null && listener.isSeeking) {
            listener.onStopTrackingTouch(this);
        }
    }

    private static final class NestedListener implements OnSeekBarChangeListener {
        private final OnSeekBarChangeListener delegate;

        boolean isSeeking;

        private NestedListener(final OnSeekBarChangeListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onProgressChanged(final SeekBar seekBar, final int progress,
                                      final boolean fromUser) {
            if (!seekBar.isInTouchMode() && !isSeeking && fromUser) {
                isSeeking = true;

                onStartTrackingTouch(seekBar);
            }

            delegate.onProgressChanged(seekBar, progress, fromUser);
        }

        @Override
        public void onStartTrackingTouch(final SeekBar seekBar) {
            isSeeking = true;

            delegate.onStartTrackingTouch(seekBar);
        }

        @Override
        public void onStopTrackingTouch(final SeekBar seekBar) {
            isSeeking = false;

            delegate.onStopTrackingTouch(seekBar);
        }
    }
}
