package com.nullgram.client.branding;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import org.telegram.messenger.UiTestEnvironment;

import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@Config(application = UiTestEnvironment.App.class, sdk = {28, 36})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NullgramSplashTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private FrameLayout root;

    @Before public void setup() {
        ReflectionHelpers.setStaticField(NullgramSplash.class, "shownInProcess", false);
        controller = Robolectric.buildActivity(Activity.class).setup().visible();
        activity = controller.get();
        root = (FrameLayout) activity.getWindow().getDecorView();
        // Model content which is still loading. Ready-content tests reveal it
        // explicitly; the splash must no longer force ready content to wait.
        activity.findViewById(android.R.id.content).setVisibility(View.INVISIBLE);
    }

    @After public void cleanup() {
        controller.pause().stop().destroy();
    }

    private Intent launcher() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    }

    private NullgramSplash show() {
        assertTrue(NullgramSplash.attach(activity, launcher(), null));
        return (NullgramSplash) root.getChildAt(root.getChildCount() - 1);
    }

    @Test public void realArtworkFramesDecodeAndDrawDifferentBlueFrames() {
        NullgramSplash splash = show();
        Bitmap atlas = ReflectionHelpers.getField(splash, "animation");
        assertNotNull("Actual Android artwork decoder must succeed", atlas);
        assertEquals(1760, atlas.getWidth());
        assertEquals(3520, atlas.getHeight());
        int[] ends = ReflectionHelpers.getField(splash, "frameEnds");
        assertEquals(31, ends.length);
        assertEquals(2000, ends[ends.length - 1]);
        splash.layout(0, 0, 300, 300);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));
        Bitmap first = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        splash.draw(new Canvas(first));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        Bitmap next = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        splash.draw(new Canvas(next));
        assertFalse("Animation must draw changing frames", first.sameAs(next));
        assertTrue((next.getPixel(0, 0) & 255) > 240);
    }

    @Test public void closesAtTwoSecondsAndLeavesExistingContent() {
        int originalChildren = root.getChildCount();
        NullgramSplash splash = show();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1999));
        assertSame(root, splash.getParent());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertNull(splash.getParent());
        assertEquals(originalChildren, root.getChildCount());
        assertNull(ReflectionHelpers.getField(splash, "animation"));
    }

    @Test public void readyContentDismissesBeforeAnimationCompletes() {
        NullgramSplash splash = show();
        View content = activity.findViewById(android.R.id.content);
        content.layout(0, 0, 300, 450);
        content.setVisibility(View.VISIBLE);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(120));
        content.getViewTreeObserver().dispatchOnPreDraw();
        assertNull(splash.getParent());
        assertNull(ReflectionHelpers.getField(splash, "animation"));
        assertNull(ReflectionHelpers.getField(splash, "readyListener"));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertNull(splash.getParent());
    }

    @Test public void contentWithoutLayoutKeepsAnimationUntilReady() {
        NullgramSplash splash = show();
        View content = activity.findViewById(android.R.id.content);
        content.setVisibility(View.VISIBLE);
        content.layout(0, 0, 0, 0);
        content.getViewTreeObserver().dispatchOnPreDraw();
        assertSame(root, splash.getParent());
        content.layout(0, 0, 300, 450);
        content.getViewTreeObserver().dispatchOnPreDraw();
        assertNull(splash.getParent());
    }

    @Test public void notificationShareAndDeepLinkIntentsDoNotShowSplash() {
        assertFalse(NullgramSplash.attach(activity, new Intent("com.tmessages.openchat"), null));
        assertFalse(NullgramSplash.attach(activity, new Intent(Intent.ACTION_SEND), null));
        assertFalse(NullgramSplash.attach(activity, new Intent(Intent.ACTION_VIEW,
                Uri.parse("tg://resolve?domain=test")), null));
        assertFalse(NullgramSplash.attach(activity, launcher().setData(Uri.parse("tg://test")), null));
        assertTrue(NullgramSplash.attach(activity, launcher(), null));
    }

    @Test public void restoredActivityDoesNotReplayAndWarmStartDoesNotDuplicate() {
        assertFalse(NullgramSplash.attach(activity, launcher(), new Bundle()));
        show();
        int count = root.getChildCount();
        assertFalse(NullgramSplash.attach(activity, launcher(), null));
        assertEquals(count, root.getChildCount());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertFalse(NullgramSplash.attach(activity, launcher(), null));
    }

    @Test public void backgroundingReleasesAnimationAndPendingCallback() {
        NullgramSplash splash = show();
        splash.onWindowVisibilityChanged(View.INVISIBLE);
        assertNull(splash.getParent());
        assertNull(ReflectionHelpers.getField(splash, "animation"));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertNull(splash.getParent());
    }

    @Test public void tappingCanDismissWithoutTouchingUnderlyingContent() {
        NullgramSplash splash = show();
        assertTrue(splash.performClick());
        assertNull(splash.getParent());
        assertFalse(NullgramSplash.attach(activity, launcher(), null));
    }

    @Test public void decoderFailureUsesStaticLogoAndStillDismisses() {
        NullgramSplash splash = show();
        ReflectionHelpers.setField(splash, "animation", null);
        splash.layout(0, 0, 300, 300);
        Bitmap rendered = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        splash.draw(new Canvas(rendered));
        assertNotEquals(rendered.getPixel(0, 0), rendered.getPixel(100, 100));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertNull(splash.getParent());
    }
}
