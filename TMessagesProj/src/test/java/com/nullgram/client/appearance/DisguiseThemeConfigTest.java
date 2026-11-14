package com.nullgram.client.appearance;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.telegram.messenger.UiTestEnvironment;

@RunWith(RobolectricTestRunner.class)
@Config(application = UiTestEnvironment.App.class, sdk = {28, 36},
        shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class})
public class DisguiseThemeConfigTest {
    private SharedPreferences preferences;
    @Before public void setup() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("theme-config", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }
    @Test public void originalAllowsIndependentChangesWithoutATheme() {
        assertTrue(DisguiseThemeConfig.original(preferences, false));
        DisguiseListTheme.save(preferences, "qq");
        assertEquals("original", preferences.getString(DisguiseThemeConfig.CHAT, "original"));
        assertFalse(DisguiseThemeConfig.customizedQq(preferences));
        assertFalse(DisguiseThemeConfig.original(preferences, false));
    }
    @Test public void qqOnlyChangesSelectedPartsAndKeepsCustomTitle() {
        preferences.edit().putString(DisguiseThemeConfig.CUSTOM_NAME, "工作").putString("wallpaper", "personal").commit();
        DisguiseThemeConfig.applyQq(preferences, false, true, false);
        assertEquals("工作", preferences.getString(DisguiseThemeConfig.CUSTOM_NAME, ""));
        assertEquals("qq", preferences.getString(DisguiseThemeConfig.HOME, ""));
        assertFalse(preferences.contains(DisguiseThemeConfig.CHAT));
        assertEquals("personal", preferences.getString("wallpaper", ""));
    }
    @Test public void completeThemeRemainsIndividuallyEditableAndPersists() {
        DisguiseThemeConfig.applyQq(preferences, true, true, true);
        assertTrue(DisguiseThemeConfig.completeQq(preferences, true));
        assertFalse(DisguiseThemeConfig.completeQq(preferences, false));
        DisguiseChatTheme.save(preferences, "original");
        assertFalse(DisguiseThemeConfig.completeQq(preferences, true));
        SharedPreferences reopened = RuntimeEnvironment.getApplication().getSharedPreferences("theme-config", Context.MODE_PRIVATE);
        assertEquals("original", reopened.getString(DisguiseThemeConfig.CHAT, ""));
        assertTrue(DisguiseThemeConfig.customizedQq(reopened));
    }
    @Test public void restoreDoesNotClearAliasesAvatarsOrOtherSettings() {
        preferences.edit().putString("alias", "名字").putString("avatar", "local.png")
                .putString("customTitle", "自有标题").putInt("repeatCount", 5).commit();
        DisguiseThemeConfig.applyQq(preferences, true, true, true);
        DisguiseThemeConfig.reset(preferences);
        assertTrue(DisguiseThemeConfig.original(preferences, false));
        assertFalse(DisguiseThemeConfig.customizedQq(preferences));
        assertEquals("名字", preferences.getString("alias", ""));
        assertEquals("local.png", preferences.getString("avatar", ""));
        assertEquals("自有标题", preferences.getString("customTitle", ""));
        assertEquals(5, preferences.getInt("repeatCount", 0));
    }
}
