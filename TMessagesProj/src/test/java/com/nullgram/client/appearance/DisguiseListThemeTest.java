package com.nullgram.client.appearance;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UiTestEnvironment;
import org.telegram.ui.ActionBar.Theme;

@RunWith(RobolectricTestRunner.class)
@Config(application = UiTestEnvironment.App.class, sdk = {28, 36},
        shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class})
public class DisguiseListThemeTest {
    private SharedPreferences settings;

    @Before public void setup() {
        settings = RuntimeEnvironment.getApplication().getSharedPreferences("list-theme-test", Context.MODE_PRIVATE);
        settings.edit().clear().commit();
        AndroidUtilities.density = 1;
    }

    @Test public void defaultAndStandardEditionKeepOriginalAppearance() {
        assertFalse(DisguiseListTheme.isQq(settings, true));
        DisguiseListTheme.save(settings, DisguiseListTheme.QQ);
        assertTrue(DisguiseListTheme.isQq(settings, true));
        assertFalse(DisguiseListTheme.isQq(settings, false));
    }

    @Test public void switchingPersistsWithoutChangingNameIconOrUserTheme() {
        settings.edit().putString("nullgramDisguiseCustomName", "工作")
                .putString("nullgramOriginalIcon", "PHOTO").putString("theme", "dark").commit();
        for (int i = 0; i < 20; i++) {
            DisguiseListTheme.save(settings, DisguiseListTheme.QQ);
            SharedPreferences reopened = RuntimeEnvironment.getApplication().getSharedPreferences("list-theme-test", Context.MODE_PRIVATE);
            assertTrue(DisguiseListTheme.isQq(reopened, true));
            DisguiseListTheme.save(settings, DisguiseListTheme.ORIGINAL);
            assertFalse(DisguiseListTheme.isQq(reopened, true));
        }
        assertEquals("工作", settings.getString("nullgramDisguiseCustomName", null));
        assertEquals("PHOTO", settings.getString("nullgramOriginalIcon", null));
        assertEquals("dark", settings.getString("theme", null));
    }

    @Test public void selectorsArchiveCommunitiesAndPreviewsAreExcluded() {
        assertTrue(DisguiseListTheme.appliesTo(false, 0, 0, true, false));
        assertFalse(DisguiseListTheme.appliesTo(true, 0, 0, true, false));
        assertFalse(DisguiseListTheme.appliesTo(false, 1, 0, true, false));
        assertFalse(DisguiseListTheme.appliesTo(false, 0, 42, true, false));
        assertFalse(DisguiseListTheme.appliesTo(false, 0, 0, false, false));
        assertFalse(DisguiseListTheme.appliesTo(false, 0, 0, true, true));
    }

    @Test public void localPaletteRestoresOriginalProviderAndDoesNotWriteGlobalColors() {
        Theme.ResourcesProvider original = key -> 0xff123456;
        int global = Theme.getColor(Theme.key_chats_name);
        Theme.ResourcesProvider qq = DisguiseListTheme.wrap(original, true);
        assertEquals(DisguiseListTheme.WHITE, qq.getColor(Theme.key_windowBackgroundWhite));
        assertEquals(DisguiseListTheme.RED, qq.getColor(Theme.key_chats_unreadCounter));
        assertFalse(qq.isDark());
        assertEquals(global, Theme.getColor(Theme.key_chats_name));
        assertEquals(Theme.getDefaultColor(Theme.key_chat_outBubble), qq.getColor(Theme.key_chat_outBubble));
        assertSame(original, DisguiseListTheme.wrap(qq, false));
        assertSame(original, DisguiseListTheme.wrap(DisguiseListTheme.wrap(qq, true), false));
        assertNull(DisguiseListTheme.wrap(DisguiseListTheme.wrap(null, true), false));
    }

    @Test public void unknownThemeDoesNotOverwriteSavedChoice() {
        DisguiseListTheme.save(settings, DisguiseListTheme.QQ);
        assertThrows(IllegalArgumentException.class, () -> DisguiseListTheme.save(settings, "unimplemented"));
        assertTrue(DisguiseListTheme.isQq(settings, true));
    }

    @Test public void headerShowsAccountNameAndKeepsItsAccountActionClickable() {
        org.telegram.messenger.MessagesController.getGlobalMainSettings().edit()
                .putString("nullgramDisguiseCustomName", "工作聊天").commit();
        int[] calls = {0};
        QqListHeader header = new QqListHeader(RuntimeEnvironment.getApplication(), () -> calls[0]++);
        org.telegram.tgnet.TLRPC.User user = new org.telegram.tgnet.TLRPC.TL_user();
        user.id = 123; user.first_name = "Alex"; user.last_name = "Chen";
        header.bindIdentity(user);
        header.measure(android.view.View.MeasureSpec.makeMeasureSpec(260, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(56, android.view.View.MeasureSpec.EXACTLY));
        header.layout(0, 0, 260, 56);
        android.widget.TextView title = (android.widget.TextView) header.getChildAt(1);
        assertEquals("Alex Chen", title.getText().toString());
        assertTrue(title.getRight() <= 260);
        assertTrue(header.getChildAt(0).performClick());
        assertTrue(title.performClick());
        assertEquals(2, calls[0]);
    }

    @Test public void staticTabsDrawForNarrowWidthsLongLabelsAndCounters() {
        QqTabPainter painter = new QqTabPainter(RuntimeEnvironment.getApplication());
        Bitmap bitmap = Bitmap.createBitmap(360, 60, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        for (int i = 0; i < 5; i++) {
            painter.draw(canvas, 64, 48, i, i == 0 ? 1f : 0f, "123456");
            painter.draw(canvas, 120, 48, i, 0f, null);
        }
        bitmap.recycle();
    }
}
