package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.view.WindowManager;

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
    /**
     * VideoPrime: in the picture-in-picture window the video takes the window's current size in
     * the same layout pass, instead of a height set afterwards (that lag made the picture ripple
     * while the window was resized).
     */
    private boolean fillWindow = false;

    public ExpandableSurfaceView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (videoAspectRatio == 0.0f) {
            return;
        }

        int width = MeasureSpec.getSize(widthMeasureSpec);
        final boolean verticalVideo = videoAspectRatio < 1;
        // Use maxHeight only on non-fit resize mode and in vertical videos
        int height = maxHeight != 0
                && resizeMode != RESIZE_MODE_FIT
                && verticalVideo ? maxHeight : baseHeight;
        final Rect window = fillWindow ? currentWindowBounds() : null;
        if (window != null && window.width() > 0 && window.height() > 0) {
            width = Math.min(width, window.width());
            height = window.height();
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
    }

    /**
     * Scale view only in {@link #onLayout} to make transition for ZOOM mode as smooth as possible.
     */
    @Override
    protected void onLayout(final boolean changed,
                            final int left, final int top, final int right, final int bottom) {
        setScaleX(scaleX);
        setScaleY(scaleY);
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
     * VideoPrime: follow the window's size (picture-in-picture), see {@link #fillWindow}.
     *
     * @param fill whether the video takes the window's current size
     */
    public void setFillWindow(final boolean fill) {
        if (fillWindow == fill) {
            return;
        }
        fillWindow = fill;
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
        final WindowManager wm = getContext().getSystemService(WindowManager.class);
        return wm == null ? null : wm.getCurrentWindowMetrics().getBounds();
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
