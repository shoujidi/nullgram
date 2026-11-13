package com.nullgram.client.branding;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
import org.telegram.messenger.R;
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
        assertTrue(NullgramSplash.attach(activity));
        return (NullgramSplash) root.getChildAt(root.getChildCount() - 1);
    }

    private Bitmap render(NullgramSplash splash) {
        splash.layout(0, 0, 300, 300);
        Bitmap bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        splash.draw(new Canvas(bitmap));
        return bitmap;
    }

    @Test public void singleLogoScalesWithoutForcingSoftwareRendering() {
        NullgramSplash splash = show();
        Drawable logo = ReflectionHelpers.getField(splash, "logo");
        Bitmap artwork = ((BitmapDrawable) logo).getBitmap();
        int expectedWidth = 880;
        int backgroundColor = activity.getColor(R.color.nullgram_splash_background);
        assertEquals(expectedWidth, artwork.getWidth());
        assertTrue(artwork.getAllocationByteCount() <= expectedWidth * expectedWidth * 4);
        assertEquals(View.LAYER_TYPE_NONE, splash.getLayerType());
        Bitmap first = render(splash);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(NullgramSplash.ENTER_MS));
        Bitmap next = render(splash);
        assertFalse("Logo must grow between frames", first.sameAs(next));
        assertEquals(backgroundColor, next.getPixel(0, 0));
        assertNotEquals(next.getPixel(0, 0), next.getPixel(150, 150));
    }

    @Test public void closesAtTwoSecondsAndLeavesExistingContent() {
        int originalChildren = root.getChildCount();
        NullgramSplash splash = show();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1999));
        assertSame(root, splash.getParent());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertNull(splash.getParent());
        assertEquals(originalChildren, root.getChildCount());
        assertNull(ReflectionHelpers.getField(splash, "logo"));
    }

    @Test public void readyContentStillGetsAFirstFrameAndCanBeTouchedDuringFade() {
        NullgramSplash splash = show();
        View content = activity.findViewById(android.R.id.content);
        content.layout(0, 0, 300, 450);
        content.setVisibility(View.VISIBLE);
        content.getViewTreeObserver().dispatchOnPreDraw();
        assertSame(root, splash.getParent());
        assertFalse(splash.isClickable());
        assertNull(ReflectionHelpers.getField(splash, "readyListener"));
        Bitmap first = render(splash);
        assertEquals(255, first.getPixel(0, 0) >>> 24);
        assertNotEquals(first.getPixel(0, 0), first.getPixel(150, 150));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(NullgramSplash.EXIT_MS / 2));
        Bitmap fading = render(splash);
        int alpha = fading.getPixel(0, 0) >>> 24;
        assertTrue(alpha > 0 && alpha < 255);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(NullgramSplash.EXIT_MS / 2));
        assertNull(splash.getParent());
        assertNull(ReflectionHelpers.getField(splash, "logo"));
    }

    @Test public void contentWithoutLayoutKeepsAnimationUntilReady() {
        NullgramSplash splash = show();
        View content = activity.findViewById(android.R.id.content);
        content.setVisibility(View.VISIBLE);
        content.layout(0, 0, 0, 0);
        content.getViewTreeObserver().dispatchOnPreDraw();
        assertSame(root, splash.getParent());
        assertTrue(splash.isClickable());
        content.layout(0, 0, 300, 450);
        content.getViewTreeObserver().dispatchOnPreDraw();
        render(splash);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(NullgramSplash.EXIT_MS));
        assertNull(splash.getParent());
    }

    @Test public void notificationShareAndDeepLinkIntentsDoNotShowSplash() {
        assertFalse(NullgramSplash.isLauncherStart(new Intent("com.tmessages.openchat"), null));
        assertFalse(NullgramSplash.isLauncherStart(new Intent(Intent.ACTION_SEND), null));
        assertFalse(NullgramSplash.isLauncherStart(new Intent(Intent.ACTION_VIEW,
                Uri.parse("tg://resolve?domain=test")), null));
        assertFalse(NullgramSplash.isLauncherStart(launcher().setData(Uri.parse("tg://test")), null));
        assertTrue(NullgramSplash.isLauncherStart(launcher(), null));
    }

    @Test public void restoredActivityDoesNotReplayAndWarmStartDoesNotDuplicate() {
        assertFalse(NullgramSplash.isLauncherStart(launcher(), new Bundle()));
        show();
        int count = root.getChildCount();
        assertFalse(NullgramSplash.attach(activity));
        assertEquals(count, root.getChildCount());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertFalse(NullgramSplash.attach(activity));
    }

    @Test public void backgroundingReleasesAnimationAndPendingCallback() {
        NullgramSplash splash = show();
        render(splash);
        splash.onWindowVisibilityChanged(View.INVISIBLE);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNull(splash.getParent());
        assertNull(ReflectionHelpers.getField(splash, "logo"));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertNull(splash.getParent());
    }

    @Test public void tappingCanDismissWithoutTouchingUnderlyingContent() {
        NullgramSplash splash = show();
        assertTrue(splash.performClick());
        assertNull(splash.getParent());
        assertFalse(NullgramSplash.attach(activity));
    }

    @Test public void launcherDecisionSurvivesTheRoutingCodeClearingTheIntent() {
        Intent intent = launcher();
        boolean showSplash = NullgramSplash.isLauncherStart(intent, null);
        intent.setAction(null);
        assertFalse(NullgramSplash.isLauncherStart(intent, null));
        assertTrue(showSplash && NullgramSplash.attach(activity));
    }
}
