package com.nullgram.client.appearance;

import static org.junit.Assert.*;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UiTestEnvironment;
import org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = UiTestEnvironment.App.class, sdk = {28, 36},
        shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class})
public class QqStatusRenderingTest {
    private static final int INK = 0xffdf00df;
    private SwapAnimatedEmojiDrawable status;
    private QqListHeader header;
    private int clicks;

    @Before public void setup() {
        AndroidUtilities.density = 1;
        LocaleController.isRTL = false;
        header = new QqListHeader(QqListLayoutTest.context(1), () -> {});
        ((TextView) header.getChildAt(1)).setText("Alex");
        status = new SwapAnimatedEmojiDrawable(null, 26);
        status.center = true;
        status.set(new ColorDrawable(INK), false);
        header.setStatusDrawable(status, () -> clicks++);
        QqListLayoutTest.layout(header, 360, 56);
    }

    @After public void cleanup() {
        LocaleController.isRTL = false;
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);
    }

    private int pixels() {
        Bitmap bitmap = Bitmap.createBitmap(360, 56, Bitmap.Config.ARGB_8888);
        header.draw(new Canvas(bitmap));
        int count = 0;
        for (int y = 0; y < 56; y++) for (int x = 0; x < 360; x++) {
            if (bitmap.getPixel(x, y) == INK) count++;
        }
        bitmap.recycle();
        return count;
    }

    @Test public void sharedNativeOrPopupBoundsCannotHideBadgeOrMoveClickTarget() {
        int before = pixels();
        assertTrue(before > 0);
        View badge = header.getChildAt(2);
        Rect target = new Rect(badge.getLeft(), badge.getTop(), badge.getRight(), badge.getBottom());
        for (int i = 0; i < 30; i++) {
            // Previously these externally assigned bounds made all 484 pixels disappear.
            Rect external = new Rect(100 + i, 200 + i, 126 + i, 226 + i);
            status.setBounds(external);
            assertEquals(before, pixels());
            assertEquals("Rendering must not mutate another host's bounds", external, status.getBounds());
            assertEquals(target, new Rect(badge.getLeft(), badge.getTop(), badge.getRight(), badge.getBottom()));
            assertTrue(badge.performClick());
        }
        assertEquals(30, clicks);
    }

    @Test public void repeatedContentReplacementKeepsVisiblePixelsAndLayout() {
        int before = pixels();
        int left = header.getChildAt(2).getLeft();
        for (int i = 0; i < 30; i++) {
            status.set(new ColorDrawable(INK), false);
            header.setStatusDrawable(status, () -> clicks++);
            QqListLayoutTest.layout(header, 360, 56);
            assertEquals(before, pixels());
            assertEquals(left, header.getChildAt(2).getLeft());
        }
        status.set((android.graphics.drawable.Drawable) null, false);
        header.setStatusDrawable(status, null);
        QqListLayoutTest.layout(header, 360, 56);
        assertEquals(View.GONE, header.getChildAt(2).getVisibility());
        assertFalse(header.getStatusBounds(header, new Rect()));
    }

    @Test public void popupAnchorTracksVisibleBadgeAcrossRtlAndTranslatedHosts() {
        // UiTestEnvironment pauses frame callbacks for bitmap tests; a real window
        // needs its initial traversal before getLocationInWindow is meaningful.
        org.robolectric.shadows.ShadowChoreographer.setPaused(false);
        try (org.robolectric.android.controller.ActivityController<Activity> controller =
                     Robolectric.buildActivity(Activity.class).setup()) {
            FrameLayout root = new FrameLayout(controller.get());
            FrameLayout bar = new FrameLayout(controller.get());
            root.addView(bar);
            bar.addView(header);
            controller.get().setContentView(root);
            controller.visible();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                    .idleFor(java.time.Duration.ofMillis(100));
            assertTrue("Coordinate checks require an attached Android window", header.isAttachedToWindow());
            QqListLayoutTest.layout(root, 400, 500);
            bar.layout(10, 30, 370, 110);
            for (boolean rtl : new boolean[]{false, true}) {
                LocaleController.isRTL = rtl;
                QqListLayoutTest.layout(header, 360, 56);
                header.layout(0, 24, 360, 80);
                header.setTranslationX(5);
                bar.setTranslationY(-12);
                View badge = header.getChildAt(2);
                Rect actual = new Rect();
                assertTrue(header.getStatusBounds(bar, actual));
                assertEquals(new Rect(badge.getLeft() + 5, badge.getTop() + 24,
                        badge.getRight() + 5, badge.getBottom() + 24), actual);
                assertTrue(header.getStatusBounds(root, actual));
                assertEquals(new Rect(badge.getLeft() + 15, badge.getTop() + 42,
                        badge.getRight() + 15, badge.getBottom() + 42), actual);
            }
        }
    }
}
