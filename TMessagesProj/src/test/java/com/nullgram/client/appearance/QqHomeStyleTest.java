package com.nullgram.client.appearance;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UiTestEnvironment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import java.io.File;
import java.io.FileOutputStream;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = UiTestEnvironment.App.class, sdk = 28,
        shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class})
public class QqHomeStyleTest {
    @After public void cleanup() { LocaleController.isRTL = false; }

    static UserCell contact(float scale, boolean section) {
        AndroidUtilities.density = 1;
        UserCell cell = new UserCell(QqListLayoutTest.context(scale), 58, 1, false, false,
                QqPageStyle.wrap(null, true), true);
        cell.setCallCellStyle(58);
        cell.setAvatarPadding(section ? 58 : 7, 1);
        SimpleTextView name = ReflectionHelpers.getField(cell, "nameTextView");
        SimpleTextView status = ReflectionHelpers.getField(cell, "statusTextView");
        name.setText("Alex Chen"); status.setText("last seen recently");
        cell.setStatusColors(QqPageStyle.SECONDARY, QqPageStyle.BLUE);
        status.setTextColor(QqPageStyle.SECONDARY);
        BackupImageView avatar = ReflectionHelpers.getField(cell, "avatarImageView");
        AvatarDrawable placeholder = new AvatarDrawable(); placeholder.setInfo(0, "Alex", "Chen");
        avatar.setImageDrawable(placeholder);
        return cell;
    }

    @Test public void contactGeometryHandlesSectionsLargeFontsAndRtlWithoutLosingClick() {
        for (boolean rtl : new boolean[]{false, true}) for (float scale : new float[]{1, 1.6f}) {
            LocaleController.isRTL = rtl;
            UserCell cell = contact(scale, true);
            QqListLayoutTest.layout(cell, 320, 300);
            assertEquals(AndroidUtilities.dp(QqPageStyle.rowHeight(cell.getContext())), cell.getMeasuredHeight());
            View avatar = ReflectionHelpers.getField(cell, "avatarImageView");
            View name = ReflectionHelpers.getField(cell, "nameTextView");
            View status = ReflectionHelpers.getField(cell, "statusTextView");
            assertTrue(status.getBottom() <= cell.getHeight());
            assertTrue(status.getTop() >= name.getBottom());
            assertTrue(rtl ? name.getRight() <= avatar.getLeft() : name.getLeft() >= avatar.getRight());
            int[] clicks = {0}; cell.setOnClickListener(v -> clicks[0]++);
            assertTrue(cell.performClick()); assertEquals(1, clicks[0]);
        }
    }

    @Test public void unstyledCellsAndOriginalProviderStayUnchanged() {
        AndroidUtilities.density = 1;
        Theme.ResourcesProvider original = new Theme.ResourcesProvider() { @Override public int getColor(int key) { return 0xff123456; } };
        assertSame(original, QqPageStyle.wrap(original, false));
        assertSame(original, QqPageStyle.wrap(QqPageStyle.wrap(original, true), false));
        UserCell nativeCell = new UserCell(QqListLayoutTest.context(1), 0, 1, false);
        QqListLayoutTest.layout(nativeCell, 320, 300);
        assertEquals(58, nativeCell.getMeasuredHeight());
        assertFalse(QqPageStyle.isPalette(original));
        assertEquals(QqPageStyle.WHITE, QqPageStyle.wrap(original, true).getColor(Theme.key_windowBackgroundWhite));
    }

    @Test public void renderActualContactRows() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(360, 144, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        for (int i = 0; i < 2; i++) {
            UserCell cell = contact(1, i == 1);
            QqListLayoutTest.layout(cell, 360, 72);
            canvas.save(); canvas.translate(0, i * 72); cell.draw(canvas); canvas.restore();
        }
        File output = new File("build/reports/qq-home/contacts.png"); output.getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(output)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)); }
        bitmap.recycle();
    }
}
