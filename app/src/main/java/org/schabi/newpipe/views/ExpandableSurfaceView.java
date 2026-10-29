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
        // VideoPrime: in the picture-in-picture window the video takes the window's current size
        // in the same layout pass, instead of a height set afterwards (that lag made the picture
        // ripple while the window was resized)
        final Rect window = pipWindowBounds();
        if (window != null && window.width() > 0 && window.height() > 0) {
            width = Math.min(width, window.width());
            height = window.height();
        } else if (SystemClock.uptimeMillis() - leftPipAt < LEFT_PIP_MILLIS
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
    }

    /**
     * Scale view only in {@link #onLayout} to make transition for ZOOM mode as smooth as possible.
     */
    @Override
    protected void onLayout(final boolean changed,
                            final int left, final int top, final int right, final int bottom) {
        setScaleX(scaleX);
        setScaleY(scaleY);
        // PulsePlay: in the picture-in-picture window the video is centred on the window itself.
        // Its parents may still have the page's old size for a moment (they follow the window a
        // bit later) and centring on them left a black band on top and cut the bottom
        final Rect window = pipWindowBounds();
        if (window == null) {
            setTranslationX(0);
            setTranslationY(0);
            return;
        }
        // where the view would be without any translation or scale: its parent's place + its own
        if (!(getParent() instanceof View)) {
            return;
        }
        ((View) getParent()).getLocationInWindow(location);
        final float x = location[0] + left;
        final float y = location[1] + top;
        setTranslationX((window.width() - (right - left)) / 2f - x);
        setTranslationY((window.height() - (bottom - top)) / 2f - y);
    }

    /** PulsePlay: the app came back from the picture-in-picture window to the page. */
    public void onLeftPictureInPicture() {
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
     * PulsePlay: the window's bounds, from the very first layout in it. Android animates the
     * opening by scaling the app as it is already laid out at the window's size (see
     * PipUtils.calcEndTransform in AOSP): that layout must already be only the video filling
     * the window. Waiting for the animation to end (as tried before) showed the page's layout
     * shrinking, the video on top and black below, and then a jump.
     *
     * @return the window's bounds, or null if not in the window
     */
    @Nullable
    private Rect pipWindowBounds() {
        return currentWindowBounds();
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
