package com.nullgram.client.appearance;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UiTestEnvironment;
import org.telegram.ui.MainTabsLayout;
import org.telegram.ui.Components.FragmentSearchField;
import java.io.File;
import java.io.FileOutputStream;

/** Production header, search and navigation rendered by Android. Not a device screenshot. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = UiTestEnvironment.App.class, sdk = 28, qualifiers = "zh-rCN",
    shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class})
public class QqListRenderingTest {
    private QqListHeader previewHeader(Context context) {
        QqListHeader header = QqListLayoutTest.header(context);
        org.telegram.ui.Components.AvatarDrawable avatar = new org.telegram.ui.Components.AvatarDrawable();
        avatar.setInfo(0, "Alex", "Chen");
        ((org.telegram.ui.Components.BackupImageView) header.getChildAt(0)).setImageDrawable(avatar);
        org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable status =
                new org.telegram.ui.Components.AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(null, 22);
        status.set(context.getDrawable(org.telegram.messenger.R.drawable.msg_premium_liststar).mutate(), false);
        status.setColor(DisguiseListTheme.BLUE);
        header.setStatusDrawable(status, null);
        return header;
    }

    @Test public void renderChromeAndFourSelectionStates() throws Exception {
        AndroidUtilities.density = 1;
        Context context = QqListLayoutTest.context(1);
        Bitmap bitmap = Bitmap.createBitmap(720, 480, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.WHITE);
        QqListHeader header = previewHeader(context);
        QqListLayoutTest.layout(header, 304, 56); header.draw(canvas);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(DisguiseListTheme.TEXT); paint.setStrokeWidth(2);
        canvas.drawLine(322, 28, 342, 28, paint); canvas.drawLine(332, 18, 332, 38, paint);
        FragmentSearchField search = new FragmentSearchField(context, DisguiseListTheme.wrap(null, true));
        search.setQqAppearance(true); QqListLayoutTest.layout(search, 340, 48);
        canvas.save(); canvas.translate(10, 58); search.draw(canvas); canvas.restore();
        int row = 0;
        for (int selected : new int[]{0, 1, 3, 4}) {
            MainTabsLayout tabs = QqListLayoutTest.tabs(context, selected);
            QqListLayoutTest.layout(tabs, 360, 54);
            canvas.save(); canvas.translate(0, 160 + row++ * 72); tabs.draw(canvas); canvas.restore();
        }
        Context large = QqListLayoutTest.context(1.6f);
        QqListHeader largeHeader = previewHeader(large);
        QqListLayoutTest.layout(largeHeader, 300, QqListStyle.headerHeight(large));
        canvas.save(); canvas.translate(380, 0); largeHeader.draw(canvas); canvas.restore();
        MainTabsLayout largeTabs = QqListLayoutTest.tabs(large, 0);
        QqListLayoutTest.layout(largeTabs, 300, QqListStyle.navigationHeight(large));
        canvas.save(); canvas.translate(380, 160); largeTabs.draw(canvas); canvas.restore();
        paint.setTextSize(12); paint.setColor(Color.DKGRAY);
        canvas.drawText("1.0x / four page selection states", 12, 140, paint);
        canvas.drawText("1.6x / narrow viewport", 385, 140, paint);
        canvas.drawText("PRODUCTION COMPONENT PREVIEW / NOT A DEVICE CAPTURE", 12, 465, paint);
        File output = new File("build/reports/qq9215/chrome-preview.png");
        assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
        try (FileOutputStream stream = new FileOutputStream(output)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)); }
        assertEquals(Color.WHITE, bitmap.getPixel(359, 120));
        bitmap.recycle();
    }
}
