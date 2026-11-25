package com.google.android.material.appbar;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import org.schabi.newpipe.R;

import java.lang.reflect.Field;
import java.util.List;

// See https://stackoverflow.com/questions/56849221#57997489
public final class FlingBehavior extends AppBarLayout.Behavior {
    private final Rect focusScrollRect = new Rect();

    public FlingBehavior(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    private boolean allowScroll = true;
    private final Rect globalRect = new Rect();
    private final List<Integer> skipInterceptionOfElements = List.of(
            R.id.itemsListPanel, R.id.playbackSeekBar,
            R.id.playPauseButton, R.id.playPreviousButton, R.id.playNextButton);

    @Override
    public boolean onRequestChildRectangleOnScreen(
            @NonNull final CoordinatorLayout coordinatorLayout, @NonNull final AppBarLayout child,
            @NonNull final Rect rectangle, final boolean immediate) {
        if (isFullscreen(child)) {
            // PulsePlay: the video fills the screen: nothing to bring into view (a control
            // taking the focus moved the whole page under the video)
            return false;
        }
        focusScrollRect.set(rectangle);

        coordinatorLayout.offsetDescendantRectToMyCoords(child, focusScrollRect);

        final int height = coordinatorLayout.getHeight();

        if (focusScrollRect.top <= 0 && focusScrollRect.bottom >= height) {
            // the child is too big to fit inside ourselves completely, ignore request
            return false;
        }

        final int dy;

        if (focusScrollRect.bottom > height) {
            dy =  focusScrollRect.top;
        } else if (focusScrollRect.top < 0) {
            // scrolling up
            dy = -(height - focusScrollRect.bottom);
        } else {
            // nothing to do
            return false;
        }

        final int consumed = scroll(coordinatorLayout, child, dy, getMaxDragOffset(child), 0);

        return consumed == dy;
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull final CoordinatorLayout parent,
                                         @NonNull final AppBarLayout child,
                                         @NonNull final MotionEvent ev) {
        if (isFullscreen(child)) {
            // PulsePlay: in full screen the header never scrolls (a small drag of the finger
            // touching the video moved it); the touch goes to the player
            allowScroll = false;
            return false;
        }
        for (final int element : skipInterceptionOfElements) {
            final View view = child.findViewById(element);
            if (view != null) {
                final boolean visible = view.getGlobalVisibleRect(globalRect);
                if (visible && globalRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    allowScroll = false;
                    return false;
                }
            }
        }
        allowScroll = true;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // remove reference to old nested scrolling child
                resetNestedScrollingChild();
                // Stop fling when your finger touches the screen
                stopAppBarLayoutFling();
                break;
            default:
                break;
        }
        return super.onInterceptTouchEvent(parent, child, ev);
    }

    /**
     * PulsePlay: set by the video page while its video fills the screen.
     *
     * @param child the page's header
     * @return whether the video fills the screen
     */
    private static boolean isFullscreen(@NonNull final AppBarLayout child) {
        return Boolean.TRUE.equals(child.getTag(R.id.pulseplay_fullscreen));
    }

    @Override
    public boolean onStartNestedScroll(@NonNull final CoordinatorLayout parent,
                                       @NonNull final AppBarLayout child,
                                       @NonNull final View directTargetChild,
                                       final View target,
                                       final int nestedScrollAxes,
                                       final int type) {
        return allowScroll && super.onStartNestedScroll(
                parent, child, directTargetChild, target, nestedScrollAxes, type);
    }

    @Override
    public boolean onNestedFling(@NonNull final CoordinatorLayout coordinatorLayout,
                                 @NonNull final AppBarLayout child,
                                 @NonNull final View target, final float velocityX,
                                 final float velocityY, final boolean consumed) {
        return allowScroll && super.onNestedFling(
                coordinatorLayout, child, target, velocityX, velocityY, consumed);
    }

    @Nullable
    private OverScroller getScrollerField() {
        try {
            final Class<?> headerBehaviorType = this.getClass()
                    .getSuperclass().getSuperclass().getSuperclass();
            if (headerBehaviorType != null) {
                final Field field = headerBehaviorType.getDeclaredField("scroller");
                field.setAccessible(true);
                return ((OverScroller) field.get(this));
            }
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            // ?
        }
        return null;
    }

    @Nullable
    private Field getLastNestedScrollingChildRefField() {
        try {
            final Class<?> headerBehaviorType = this.getClass().getSuperclass().getSuperclass();
            if (headerBehaviorType != null) {
                final Field field =
                        headerBehaviorType.getDeclaredField("lastNestedScrollingChildRef");
                field.setAccessible(true);
                return field;
            }
        } catch (final NoSuchFieldException e) {
            // ?
        }
        return null;
    }

    private void resetNestedScrollingChild() {
        final Field field = getLastNestedScrollingChildRefField();
        if (field != null) {
            try {
                final Object value = field.get(this);
                if (value != null) {
                    field.set(this, null);
                }
            } catch (final IllegalAccessException e) {
                // ?
            }
        }
    }

    private void stopAppBarLayoutFling() {
        final OverScroller scroller = getScrollerField();
        if (scroller != null) {
            scroller.forceFinished(true);
        }
    }
}
