package com.nullgram.client.branding;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import org.telegram.messenger.R;

/** Local artwork only; does not delay account initialization or intent routing. */
public final class NullgramSplash extends View {
    static final int DURATION_MS = 2000;
    private static boolean shownInProcess;
    private Bitmap animation;
    private final int[] frameEnds;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect source = new Rect();
    private final RectF target = new RectF();
    private Drawable fallback;
    private long startedAt;
    private boolean dismissing;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable finish = this::dismiss;
    private View content;
    private ViewTreeObserver.OnPreDrawListener readyListener;

    private NullgramSplash(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setClickable(true);
        setOnClickListener(view -> dismiss());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        frameEnds = getResources().getIntArray(R.array.nullgram_splash_frame_ends);
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            animation = BitmapFactory.decodeResource(getResources(), R.drawable.nullgram_splash_frames, options);
        } catch (RuntimeException | OutOfMemoryError ignored) {
            // The exact static blue mark remains available on low-memory devices.
        }
        fallback = context.getDrawable(R.drawable.nullgram_splash_icon);
    }

    public static boolean attach(Activity activity, Intent intent, Bundle savedState) {
        if (shownInProcess || savedState != null || activity.isFinishing()
                || activity.isDestroyed() || intent == null
                || !Intent.ACTION_MAIN.equals(intent.getAction())
                || !intent.hasCategory(Intent.CATEGORY_LAUNCHER)
                || intent.getData() != null) {
            return false;
        }
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof FrameLayout)) {
            return false;
        }
        shownInProcess = true;
        NullgramSplash splash = new NullgramSplash(activity);
        ((FrameLayout) decor).addView(splash,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        splash.start();
        splash.dismissWhenContentIsReady(activity.findViewById(android.R.id.content));
        return true;
    }

    private void dismissWhenContentIsReady(View view) {
        if (view == null || dismissing) return;
        content = view;
        readyListener = () -> {
            // The existing activity has completed initialization and its first
            // usable layout is ready. Never wait for the artwork or network sync.
            if (view.isShown() && view.getWidth() > 0 && view.getHeight() > 0) {
                dismiss();
            }
            return true;
        };
        view.getViewTreeObserver().addOnPreDrawListener(readyListener);
    }

    private void start() {
        if (dismissing) return;
        startedAt = SystemClock.uptimeMillis();
        handler.postDelayed(finish, DURATION_MS);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(getResources().getColor(R.color.nullgram_splash_background, null));
        float density = getResources().getDisplayMetrics().density;
        float size = Math.min(240 * density, Math.min(getWidth(), getHeight()) * .72f);
        float left = (getWidth() - size) / 2f, top = (getHeight() - size) / 2f;
        if (animation != null && !animation.isRecycled() && frameEnds.length > 0) {
            long elapsed = Math.max(0, SystemClock.uptimeMillis() - startedAt);
            int frame = 0;
            while (frame < frameEnds.length - 1 && elapsed >= frameEnds[frame]) frame++;
            int x = (frame % 4) * 440, y = (frame / 4) * 440;
            source.set(x, y, x + 440, y + 440);
            target.set(left, top, left + size, top + size);
            canvas.drawBitmap(animation, source, target, paint);
            if (elapsed < DURATION_MS) {
                postInvalidateOnAnimation();
            }
        } else if (fallback != null) {
            fallback.setBounds(Math.round(left), Math.round(top),
                    Math.round(left + size), Math.round(top + size));
            fallback.draw(canvas);
        }
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE && getParent() != null) {
            dismiss();
        }
    }

    @Override protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }

    private void release() {
        handler.removeCallbacks(finish);
        if (content != null && readyListener != null) {
            ViewTreeObserver observer = content.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnPreDrawListener(readyListener);
        }
        content = null;
        readyListener = null;
        if (animation != null) animation.recycle();
        animation = null;
        fallback = null;
    }

    private void dismiss() {
        if (dismissing) return;
        dismissing = true;
        release();
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
    }
}
