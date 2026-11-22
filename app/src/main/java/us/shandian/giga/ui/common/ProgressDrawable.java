package us.shandian.giga.ui.common;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * PulsePlay: the card of a download, in the app's look: a rounded dark card with a thin bar at
 * the bottom (the same as the download bar in Library → Downloads). NewPipe painted the whole
 * card in a red or grey block that filled up as it downloaded. Without a known size (or while it
 * prepares) a short piece of the bar slides along it. Finished downloads have no bar.
 */
public class ProgressDrawable extends Drawable {
    private static final int MARQUEE_INTERVAL = 16;
    private static final long MARQUEE_CYCLE_MILLIS = 1400;
    private static final float CORNER_DP = 16f;
    private static final float BAR_DP = 4f;
    private static final float BAR_MARGIN_DP = 12f;

    private final float density = Resources.getSystem().getDisplayMetrics().density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::invalidateSelf;

    private float mProgress;
    private int mBackgroundColor;
    private int mForegroundColor;
    private boolean mMarquee;

    public void setColors(@ColorInt final int background, @ColorInt final int foreground) {
        mBackgroundColor = background;
        mForegroundColor = foreground;
        invalidateSelf();
    }

    public void setProgress(final double progress) {
        mProgress = (float) progress;
        invalidateSelf();
    }

    public void setMarquee(final boolean marquee) {
        if (marquee == mMarquee) {
            return;
        }
        mMarquee = marquee;
        handler.removeCallbacks(tick);
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull final Canvas canvas) {
        final Rect bounds = getBounds();
        final float corner = CORNER_DP * density;

        paint.setColor(mBackgroundColor);
        rect.set(bounds);
        canvas.drawRoundRect(rect, corner, corner, paint);

        if (!mMarquee && mProgress >= 1f) {
            return; // finished: just the card
        }

        final float margin = BAR_MARGIN_DP * density;
        final float bar = BAR_DP * density;
        final float left = bounds.left + margin;
        final float right = bounds.right - margin;
        final float bottom = bounds.bottom - margin * 0.75f;
        final float top = bottom - bar;
        if (right <= left) {
            return;
        }

        // the track
        paint.setColor(0x33FFFFFF);
        rect.set(left, top, right, bottom);
        canvas.drawRoundRect(rect, bar, bar, paint);

        paint.setColor(mForegroundColor);
        final float width = right - left;
        if (mMarquee) {
            final float phase = (SystemClock.uptimeMillis() % MARQUEE_CYCLE_MILLIS)
                    / (float) MARQUEE_CYCLE_MILLIS;
            final float piece = width * 0.25f;
            final float start = left - piece + (width + piece) * phase;
            rect.set(Math.max(left, start), top, Math.min(right, start + piece), bottom);
            if (rect.width() > 0) {
                canvas.drawRoundRect(rect, bar, bar, paint);
            }
            if (isVisible()) {
                handler.removeCallbacks(tick);
                handler.postDelayed(tick, MARQUEE_INTERVAL);
            }
            return;
        }

        final float progress = Math.max(0f, Math.min(1f, mProgress));
        if (progress > 0f) {
            rect.set(left, top, left + Math.max(bar, width * progress), bottom);
            canvas.drawRoundRect(rect, bar, bar, paint);
        }
    }

    @Override
    public boolean setVisible(final boolean visible, final boolean restart) {
        if (!visible) {
            handler.removeCallbacks(tick);
        }
        return super.setVisible(visible, restart);
    }

    @Override
    public void setAlpha(final int alpha) {
        // Unsupported
    }

    @Override
    public void setColorFilter(final ColorFilter filter) {
        // Unsupported
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
