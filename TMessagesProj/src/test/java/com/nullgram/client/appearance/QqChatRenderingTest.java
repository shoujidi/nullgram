package com.nullgram.client.appearance;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UiTestEnvironment;
import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatActivityEnterView;
import java.io.File;
import java.io.FileOutputStream;

/** Native raster of production components, not a logged-in device screenshot. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = UiTestEnvironment.App.class, sdk = 28,
        shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class})
public class QqChatRenderingTest {
    @Test public void composerPreservesRepeatStripAndDoesNotPaintFieldInRecordingMode() {
        AndroidUtilities.density = 1;
        for (int width : new int[]{280, 360, 600}) {
            Bitmap image = Bitmap.createBitmap(width, 100, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(image);
            image.eraseColor(Color.MAGENTA);
            QqComposerBackground background = new QqComposerBackground();
            background.draw(canvas, width, 100, 32, new Rect(48, 40, width - 60, 92));
            assertEquals(Color.MAGENTA, image.getPixel(width / 2, 16));
            assertEquals(Color.WHITE, image.getPixel(width / 2, 66));
            assertEquals(DisguiseChatTheme.INPUT_BACKGROUND, image.getPixel(10, 66));
            background.draw(canvas, width, 100, 32, null);
            assertEquals(DisguiseChatTheme.INPUT_BACKGROUND, image.getPixel(width / 2, 66));
            assertEquals(Color.MAGENTA, image.getPixel(width / 2, 16));
            image.recycle();
        }
    }

    @Test public void nativeBubblesSideActionsAndSendButtonRender() throws Exception {
        AndroidUtilities.density = 1;
        DisguiseChatTheme.Palette palette = new DisguiseChatTheme.Palette();
        Bitmap bitmap = Bitmap.createBitmap(360, 260, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(DisguiseChatTheme.BACKGROUND);
        Canvas canvas = new Canvas(bitmap);
        for (boolean out : new boolean[]{false, true}) {
            MessageDrawable bubble = palette.createBubble(out, false, false);
            int y = out ? 90 : 10;
            bubble.setTop(y, 360, 260, false, false);
            bubble.setBounds(out ? 90 : 10, y, out ? 350 : 270, y + 60);
            bubble.draw(canvas);
        }
        assertEquals(Color.WHITE, bitmap.getPixel(100, 35));
        assertEquals(DisguiseChatTheme.OUTGOING, bitmap.getPixel(200, 115));
        Theme.createChatResources(RuntimeEnvironment.getApplication(), false);
        int x = 10;
        for (String key : new String[]{Theme.key_drawable_replyIcon, Theme.key_drawable_shareIcon, Theme.key_drawable_moreIcon}) {
            Drawable icon = palette.getDrawable(key);
            assertNotNull(key, icon);
            Bitmap tile = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
            tile.eraseColor(palette.getColor(Theme.key_chat_serviceBackground));
            icon.setBounds(8, 8, 32, 32);
            icon.draw(new Canvas(tile));
            assertTrue(key + " must remain visible", changedPixels(tile, Color.WHITE) > 8);
            canvas.drawBitmap(tile, x, 160, null);
            tile.recycle();
            x += 50;
        }
        canvas.save();
        canvas.translate(0, 210);
        new QqComposerBackground().draw(canvas, 360, 50, 0, new Rect(45, 0, 245, 50));
        canvas.restore();
        ChatActivityEnterView.SendButton send = new ChatActivityEnterView.SendButton(
                RuntimeEnvironment.getApplication(), R.drawable.send_plane_24, palette, true);
        final boolean[] clicked = {false}, longClicked = {false};
        send.setOnClickListener(v -> clicked[0] = true);
        send.setOnLongClickListener(v -> { longClicked[0] = true; return true; });
        send.performClick(); send.performLongClick();
        assertTrue(clicked[0]); assertTrue(longClicked[0]);
        send.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(50, View.MeasureSpec.EXACTLY));
        send.layout(0, 0, 100, 50);
        Bitmap sendImage = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888);
        send.draw(new Canvas(sendImage));
        assertTrue("Native send icon must be rendered", changedPixels(sendImage, Color.TRANSPARENT) > 30);
        canvas.drawBitmap(sendImage, 260, 210, null);
        sendImage.recycle();
        File output = new File("build/reports/qq9215-chat/native-components.png");
        assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
        try (FileOutputStream stream = new FileOutputStream(output)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        bitmap.recycle();
    }

    private int changedPixels(Bitmap bitmap, int background) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (bitmap.getPixel(x, y) != background) count++;
            }
        }
        return count;
    }
}
