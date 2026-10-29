package org.schabi.newpipe.views;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;


import static com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT;
import static com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM;

public class ExpandableSurfaceView extends SurfaceView {
    private int resizeMode = RESIZE_MODE_FIT;
    private int baseHeight = 0;
    private int maxHeight = 0;
    private float videoAspectRatio = 0.0f;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private final int[] location = new int[2];
    private long leftPipAt = 0;
    private static final long LEFT_PIP_MILLIS = 1000;
    /**
     * PulsePlay: in the picture-in-picture window the view keeps the size it had on the page and
     * is scaled to the window. Resizing a SurfaceView throws its picture away (black until the
     * next frame: the whole opening animation, or a black square at its end); scaling it does not.
     */
    private int pipWidth = 0;
    private int pipHeight = 0;
    /** The width last measured on the page (the window's first layout may already be small). */
    private int pageWidth = 0;
    private final ViewTreeObserver.OnGlobalLayoutListener pipLayoutListener =
            this::updatePipTransform;

    public ExpandableSurfaceView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (videoAspectRatio == 0.0f) {
            return;
        }

        final Rect window = currentWindowBounds();
        if (window != null && window.width() > 0 && window.height() > 0) {
            if (pipWidth == 0) {
                // the size it had on the page (or the window's, if it had none yet)
                pipWidth = pageWidth > 0 ? pageWidth : window.width();
                getViewTreeObserver().addOnGlobalLayoutListener(pipLayoutListener);
            }
            // the shape of the video (it may be another video by now)
            pipHeight = Math.max(1, Math.round(pipWidth / videoAspectRatio));
            super.onMeasure(MeasureSpec.makeMeasureSpec(pipWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(pipHeight, MeasureSpec.EXACTLY));
            return;
        }
        stopPip();

        int width = MeasureSpec.getSize(widthMeasureSpec);
        final boolean verticalVideo = videoAspectRatio < 1;
        // Use maxHeight only on non-fit resize mode and in vertical videos
        int height = maxHeight != 0
                && resizeMode != RESIZE_MODE_FIT
                && verticalVideo ? maxHeight : baseHeight;
        if (SystemClock.uptimeMillis() - leftPipAt < LEFT_PIP_MILLIS
                && (resizeMode == RESIZE_MODE_FIT || !verticalVideo)
                && getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT) {
            // PulsePlay: back from the window to the page, the height of the page's player is
            // set a moment later: until then use it already (16:9 of the width, as the page does)
            // instead of the window's height, which made the picture ripple on the way back
            height = (int) (width / (16.0f / 9.0f));
        }

        if (width == 0 || height == 0) {
            return;
        }

        final float viewAspectRatio = width / ((float) height);
        final float aspectDeformation = (videoAspectRatio / viewAspectRatio) - 1;
        scaleX = 1.0f;
        scaleY = 1.0f;

        if (resizeMode == RESIZE_MODE_FIT) {
            if (aspectDeformation > 0) {
                height = (int) (width / videoAspectRatio);
            } else {
                width = (int) (height * videoAspectRatio);
            }
        } else if (resizeMode == RESIZE_MODE_ZOOM) {
            if (aspectDeformation < 0) {
                scaleY = viewAspectRatio / videoAspectRatio;
            } else {
                scaleX = videoAspectRatio / viewAspectRatio;
            }
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        pageWidth = width;
    }

    /**
     * Scale view only in {@link #onLayout} to make transition for ZOOM mode as smooth as possible.
     */
    @Override
    protected void onLayout(final boolean changed,
                            final int left, final int top, final int right, final int bottom) {
        if (pipWidth != 0) {
            updatePipTransform();
            return;
        }
        setScaleX(scaleX);
        setScaleY(scaleY);
        setTranslationX(0);
        setTranslationY(0);
    }

    /**
     * PulsePlay: in the picture-in-picture window, the video scaled to fill it and centred on it
     * (also while the window is being resized: called after every layout of the window). Its
     * parents may keep the page's size for a moment; the position comes from the window itself.
     */
    private void updatePipTransform() {
        if (pipWidth == 0 || pipHeight == 0) {
            return;
        }
        final Rect window = currentWindowBounds();
        if (window == null) {
            // the window was left (also when nothing measures this view again, e.g. hidden)
            stopPip();
            return;
        }
        if (window.width() <= 0 || window.height() <= 0 || getWidth() <= 0
                || !(getParent() instanceof View)) {
            return;
        }
        final float scale = Math.min(window.width() / (float) pipWidth,
                window.height() / (float) pipHeight);
        setScaleX(scale);
        setScaleY(scale);
        // where the view is without translation (its parent's place + its own); scaling keeps
        // its centre (the default pivot), which is moved to the window's centre
        ((View) getParent()).getLocationInWindow(location);
        final float centerX = location[0] + getLeft() + getWidth() / 2f;
        final float centerY = location[1] + getTop() + getHeight() / 2f;
        setTranslationX(window.width() / 2f - centerX);
        setTranslationY(window.height() / 2f - centerY);
    }

    /** PulsePlay: back to normal size and place, out of the picture-in-picture window. */
    private void stopPip() {
        if (pipWidth == 0) {
            return;
        }
        pipWidth = 0;
        pipHeight = 0;
        getViewTreeObserver().removeOnGlobalLayoutListener(pipLayoutListener);
        setScaleX(scaleX);
        setScaleY(scaleY);
        setTranslationX(0);
        setTranslationY(0);
        requestLayout();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // a new window (the activity recreated): the listener goes to its observer
        if (pipWidth != 0) {
            getViewTreeObserver().removeOnGlobalLayoutListener(pipLayoutListener);
            getViewTreeObserver().addOnGlobalLayoutListener(pipLayoutListener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalLayoutListener(pipLayoutListener);
        super.onDetachedFromWindow();
    }

    /** PulsePlay: the app came back from the picture-in-picture window to the page. */
    public void onLeftPictureInPicture() {
        stopPip();
        leftPipAt = SystemClock.uptimeMillis();
        requestLayout();
        // and once more when that second is over, with the page's own height
        postDelayed(this::requestLayout, LEFT_PIP_MILLIS + 50);
    }

    /**
     * @param base The height that will be used in every resize mode as a minimum height
     * @param max  The max height for vertical videos in non-FIT resize modes
     */
    public void setHeights(final int base, final int max) {
        if (baseHeight == base && maxHeight == max) {
            return;
        }
        baseHeight = base;
        maxHeight = max;
        requestLayout();
    }

    /**
     * @return the bounds of the window this view is in, as they are now, or null before
     * Android 11
     */
    private Rect currentWindowBounds() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null;
        }
        // the view is made by the player service: the window is the activity it is shown in
        // asked to the activity each time: a remembered flag could outlive a closed window
        final Activity activity = findActivity();
        return activity == null || !activity.isInPictureInPictureMode()
                ? null
                : activity.getWindowManager().getCurrentWindowMetrics().getBounds();
    }

    @Nullable
    private Activity findActivity() {
        ViewParent parent = getParent();
        while (parent instanceof View) {
            Context context = ((View) parent).getContext();
            while (context instanceof ContextWrapper) {
                if (context instanceof Activity) {
                    return (Activity) context;
                }
                context = ((ContextWrapper) context).getBaseContext();
            }
            parent = parent.getParent();
        }
        return null;
    }

    public void setResizeMode(@AspectRatioFrameLayout.ResizeMode final int newResizeMode) {
        if (resizeMode == newResizeMode) {
            return;
        }

        resizeMode = newResizeMode;
        requestLayout();
    }

    @AspectRatioFrameLayout.ResizeMode
    public int getResizeMode() {
        return resizeMode;
    }

    public void setAspectRatio(final float aspectRatio) {
        if (videoAspectRatio == aspectRatio || aspectRatio == 0 || !Float.isFinite(aspectRatio)) {
            return;
        }

        videoAspectRatio = aspectRatio;
        requestLayout();
    }
}
