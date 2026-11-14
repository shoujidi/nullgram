package org.telegram.ui.Cells;

import static org.junit.Assert.*;
import android.graphics.*;
import android.text.TextPaint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Text;
import com.nullgram.client.appearance.*;
import java.io.*;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = UiTestEnvironment.App.class, sdk = 28,
        shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class})
public class QqInlineButtonTest {
    @Test public void allActionKindsRenderReadableTextOnLightDarkAndCustomWallpaper() throws Exception {
        AndroidUtilities.density = 1;
        Theme.createChatResources(RuntimeEnvironment.getApplication(), false);
        DisguiseChatTheme.Palette palette = new DisguiseChatTheme.Palette();
        TextPaint shared = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        shared.setTextSize(15); shared.setColor(Color.WHITE);
        Bitmap preview = Bitmap.createBitmap(420, 280, Bitmap.Config.ARGB_8888);
        preview.eraseColor(0xff23405b);
        Canvas output = new Canvas(preview);
        int row = 0;
        for (BotInlineKeyboard.BackgroundColor kind : BotInlineKeyboard.BackgroundColor.values()) {
            int column = 0;
            for (boolean pressed : new boolean[]{false, true}) {
                BotButton button = new BotButton(() -> {});
                button.buttonImpl = new BotInlineKeyboard.Button() {
                    @Override public String getText() { return kind.name(); }
                    @Override public BotInlineKeyboard.BackgroundColor getColor() { return kind; }
                };
                button.title = new Text(kind.name(), shared);
                button.pressed = pressed;
                button.pressT = pressed ? 1 : 0;
                int bg = QqInlineButtonStyle.background(kind, pressed), fg = QqInlineButtonStyle.text(kind);
                assertTrue(kind.name(), androidx.core.graphics.ColorUtils.calculateContrast(fg, bg) >= 4.5);
                for (int wallpaper : new int[]{Color.WHITE, Color.BLACK, 0xffff70c0}) {
                    Bitmap tile = Bitmap.createBitmap(180, 52, Bitmap.Config.ARGB_8888);
                    tile.eraseColor(wallpaper);
                    button.draw(new Canvas(tile), new RectF(4, 4, 176, 48), false, false, palette);
                    assertEquals(bg, tile.getPixel(16, 26));
                    int textPixels = 0;
                    for (int y = 12; y < 40; y++) for (int x = 32; x < 150; x++) if (tile.getPixel(x, y) == fg) textPixels++;
                    assertTrue(kind + " text pixels=" + textPixels, textPixels > 8);
                    assertEquals(Color.WHITE, shared.getColor());
                    assertEquals(wallpaper, tile.getPixel(0, 0));
                    tile.recycle();
                }
                output.save(); output.translate(12 + column++ * 204, 12 + row * 64);
                button.draw(output, new RectF(0, 0, 192, 48), false, false, palette); output.restore();
                button.isLocked = true;
                assertTrue(button.draw(new Canvas(Bitmap.createBitmap(180, 52, Bitmap.Config.ARGB_8888)),
                        new RectF(4, 4, 176, 48), true, false, palette));
                assertEquals(Color.WHITE, shared.getColor());
            }
            row++;
        }
        File file = new File("build/reports/qq-followup/inline-buttons.png");
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        try (FileOutputStream stream = new FileOutputStream(file)) { assertTrue(preview.compress(Bitmap.CompressFormat.PNG, 100, stream)); }
        preview.recycle();
    }

    @Test public void appearanceMarkerDoesNotEnableStylingForOrdinaryResources() {
        Theme.ResourcesProvider normal = key -> Theme.getDefaultColor(key);
        assertFalse(DisguiseChatTheme.usesQqAppearance(normal));
        assertFalse(DisguiseChatTheme.usesQqAppearance(null));
        assertTrue(DisguiseChatTheme.usesQqAppearance(new DisguiseChatTheme.Palette()));
        assertFalse(DisguiseChatTheme.usesQqAppearance(new DisguiseChatTheme.StyledResources() {
            @Override public boolean usesQqAppearance() { return false; }
            @Override public int getColor(int key) { return Theme.getDefaultColor(key); }
        }));
    }
}
