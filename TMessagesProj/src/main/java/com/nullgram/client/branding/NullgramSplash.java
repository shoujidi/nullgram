package com.nullgram.client.branding;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import org.telegram.messenger.R;

/** Local artwork only; does not delay account initialization or intent routing. */
public final class NullgramSplash extends View {
    static final int MAX_DURATION_MS = 2000;
    static final int ENTER_MS = 320;
    static final int EXIT_MS = 180;
    private static boolean shownInProcess;
    private final int background;
    private Drawable logo;
    private long firstDrawAt = -1;
    private long exitAt = -1;
    private boolean contentReady;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable finish = this::dismiss;
    private View content;
    private ViewTreeObserver.OnPreDrawListener readyListener;

    private NullgramSplash(Context context) {
        super(context);
        setClickable(true);
        setOnClickListener(view -> dismiss());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        background = context.getColor(R.color.nullgram_splash_background);
        logo = context.getDrawable(R.drawable.nullgram_splash_icon).mutate();
    }

    /** Read before LaunchActivity consumes and clears the intent action. */
    public static boolean isLauncherStart(Intent intent, Bundle savedState) {
        return savedState == null && intent != null
                && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
                && intent.getData() == null;
    }

    public static boolean attach(Activity activity) {
        if (shownInProcess || activity.isFinishing() || activity.isDestroyed()) return false;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof FrameLayout)) {
            return false;
        }
        shownInProcess = true;
        NullgramSplash splash = new NullgramSplash(activity);
        ((FrameLayout) decor).addView(splash,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        splash.handler.postDelayed(splash.finish, MAX_DURATION_MS);
        splash.watchContent(activity.findViewById(android.R.id.content));
        return true;
    }

    private void watchContent(View view) {
        if (view == null) return;
        content = view;
        readyListener = () -> {
            if (view.isShown() && view.getWidth() > 0 && view.getHeight() > 0) {
                contentReady = true;
                setClickable(false);
                clearReadyListener();
                invalidate();
            }
            return true;
        };
        view.getViewTreeObserver().addOnPreDrawListener(readyListener);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        if (firstDrawAt < 0) firstDrawAt = now;
        float enter = Math.min(1f, (now - firstDrawAt) / (float) ENTER_MS);
        float remaining = 1f - enter;
        float scale = 1f - .08f * remaining * remaining;
        float opacity = exitAt < 0 ? 1f : Math.max(0f, 1f - (now - exitAt) / (float) EXIT_MS);
        int alpha = Math.round(255 * opacity);
        canvas.drawColor((background & 0x00FFFFFF) | (alpha << 24));
        float density = getResources().getDisplayMetrics().density;
        float preferredSize = 240 * density;
        float size = Math.min(preferredSize, Math.min(getWidth(), getHeight()) * .72f) * scale;
        float left = (getWidth() - size) / 2f, top = (getHeight() - size) / 2f;
        if (logo != null) {
            logo.setAlpha(alpha);
            logo.setBounds(Math.round(left), Math.round(top),
                    Math.round(left + size), Math.round(top + size));
            logo.draw(canvas);
        }
        // A ready layout can be reported before this view has drawn anything.
        // Start fading only after drawing the logo and keep the ready UI tappable.
        if (contentReady && exitAt < 0) {
            exitAt = now;
            handler.postDelayed(finish, EXIT_MS);
        }
        if (enter < 1f || (exitAt >= 0 && opacity > 0f)) postInvalidateOnAnimation();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE && firstDrawAt >= 0 && getParent() != null) {
            // Release on the next loop turn even if a pending frame has installed a sync barrier.
            Message cleanup = Message.obtain(handler, finish);
            cleanup.setAsynchronous(true);
            cleanup.sendToTarget();
        }
    }

    @Override protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }

    private void clearReadyListener() {
        if (content != null && readyListener != null) {
            ViewTreeObserver observer = content.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnPreDrawListener(readyListener);
        }
        content = null;
        readyListener = null;
    }

    private void release() {
        handler.removeCallbacks(finish);
        clearReadyListener();
        logo = null;
    }

    private void dismiss() {
        if (getParent() == null) return;
        release();
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
    }
}
