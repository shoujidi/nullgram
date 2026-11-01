package org.telegram.ui.Components;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UiTestEnvironment;

import java.time.Duration;

import top.nkbe.niagram.config.NyaConfig;

/** Compares platform editing with the same sequence through the app's input controls. */
@RunWith(RobolectricTestRunner.class)
@Config(application = UiTestEnvironment.App.class, sdk = {28, 36},
        shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class InputCompositionRegressionTest {
    private Activity activity;

    @Before public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        AndroidUtilities.density = 1;
    }

    @After public void cleanup() {
        NyaConfig.INSTANCE.getInputTextAnimations().value = false;
        activity.finish();
    }

    @Test public void platformInputPreservesLayoutAfterCompositionAndStyledDelete() {
        exercise(new EditText(activity, null, 0, org.telegram.messenger.R.style.EditTextNoBackgroundStyle), false);
    }

    @Test public void appInputWithAnimationDisabledPreservesLayout() {
        exercise(new LocalInput(activity), false);
    }

    @Test public void appInputWithAnimationEnabledPreservesLayout() {
        exercise(new LocalInput(activity), true);
    }

    private void exercise(EditText input, boolean animate) {
        NyaConfig.INSTANCE.getInputTextAnimations().value = animate;
        activity.setContentView(input);
        input.requestFocus();
        input.setTransformationMethod(PasswordTransformationMethod.getInstance());
        input.getText().append("secret");
        draw(input);
        input.setTransformationMethod(null);
        Editable text = input.getText();
        TextWatcher[] watchers = text.getSpans(0, text.length(), TextWatcher.class);
        assertTrue("framework watchers are installed", watchers.length > 0);
        InputConnection ime = input.onCreateInputConnection(new EditorInfo());
        assertNotNull(ime);
        input.setSelection(input.length());
        // The static setComposingSpans(liveEditable) would also mark framework watchers.
        assertTrue(ime.setComposingText("候选", 1));
        assertTrue(BaseInputConnection.getComposingSpanStart(text) >= 0);
        draw(input);
        assertTrue(ime.finishComposingText());
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(text));
        for (TextWatcher watcher : watchers) {
            assertTrue("IME must preserve framework watchers", text.getSpanStart(watcher) >= 0);
            assertEquals(0, text.getSpanFlags(watcher) & Spanned.SPAN_COMPOSING);
        }
        text.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.delete(0, 1);
        assertEquals("ecret候选", text.toString());
        draw(input);
        assertEquals(text.length(), input.getLayout().getLineEnd(input.getLayout().getLineCount() - 1));
    }

    private static void draw(EditText input) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
        input.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY));
        input.layout(0, 0, 400, 160);
        Bitmap bitmap = Bitmap.createBitmap(400, 160, Bitmap.Config.ARGB_8888);
        try { input.draw(new Canvas(bitmap)); }
        finally { bitmap.recycle(); }
    }

    private static class LocalInput extends EditTextEffects {
        LocalInput(Context context) { super(context); }
        @Override public void updateAnimatedEmoji(boolean force) {}
        @Override public void invalidateQuotes(boolean force) {}
        @Override public void invalidateEffects() {}
    }
}
