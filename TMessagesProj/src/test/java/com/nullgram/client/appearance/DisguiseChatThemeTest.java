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
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UiTestEnvironment;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

@RunWith(RobolectricTestRunner.class)
@Config(application = UiTestEnvironment.App.class, sdk = {28, 36},
        shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class})
public class DisguiseChatThemeTest {
    private SharedPreferences settings;
    private Context context;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        settings = context.getSharedPreferences("chat-theme-test", Context.MODE_PRIVATE);
        settings.edit().clear().commit();
        AndroidUtilities.density = 1;
    }

    @Test public void defaultStandardEditionAndRestoreKeepOriginal() {
        assertFalse(DisguiseChatTheme.isEnabled(settings, true));
        DisguiseChatTheme.save(settings, DisguiseListTheme.QQ);
        assertTrue(DisguiseChatTheme.isEnabled(settings, true));
        assertFalse(DisguiseChatTheme.isEnabled(settings, false));
        DisguiseChatTheme.save(settings, DisguiseListTheme.ORIGINAL);
        assertFalse(DisguiseChatTheme.isEnabled(settings, true));
    }

    @Test public void switchPersistsIndependentlyOfListIconNameAndTelegramTheme() {
        settings.edit().putString("nullgramDisguiseCustomName", "工作")
                .putString("nullgramOriginalIcon", "PHOTO").putString("theme", "dark").commit();
        DisguiseListTheme.save(settings, DisguiseListTheme.QQ);
        for (int i = 0; i < 20; i++) {
            DisguiseChatTheme.save(settings, DisguiseListTheme.QQ);
            assertTrue(DisguiseChatTheme.isEnabled(context.getSharedPreferences("chat-theme-test", 0), true));
            DisguiseChatTheme.save(settings, DisguiseListTheme.ORIGINAL);
            assertFalse(DisguiseChatTheme.isEnabled(context.getSharedPreferences("chat-theme-test", 0), true));
        }
        assertTrue(DisguiseListTheme.isQq(settings, true));
        assertEquals("工作", settings.getString("nullgramDisguiseCustomName", null));
        assertEquals("PHOTO", settings.getString("nullgramOriginalIcon", null));
        assertEquals("dark", settings.getString("theme", null));
    }

    @Test public void onlyOrdinaryPrivateAndGroupChatsQualify() {
        TLRPC.User user = new TLRPC.TL_user(); user.id = 42;
        assertTrue(supports(user, null));
        user.bot = true; assertFalse(supports(user, null));
        user.bot = false; user.deleted = true; assertFalse(supports(user, null));
        user.deleted = false; user.id = 1; assertFalse(supports(user, null));
        assertFalse(supports(null, null));
        assertTrue(supports(null, new TLRPC.TL_chat()));
        TLRPC.Chat channel = new TLRPC.TL_channel();
        assertFalse(supports(null, channel));
        channel.megagroup = true; assertTrue(supports(null, channel));
        channel.forum = true; assertFalse(supports(null, channel));
        channel.forum = false; channel.monoforum = true; assertFalse(supports(null, channel));
    }

    private boolean supports(TLRPC.User user, TLRPC.Chat chat) {
        return DisguiseChatTheme.supports(user, chat, 1, false, false, false, 0);
    }

    @Test public void secretThreadPreviewAndSpecialModesKeepTheirOwnUi() {
        TLRPC.User user = new TLRPC.TL_user(); user.id = 42;
        assertFalse(DisguiseChatTheme.supports(user, null, 1, true, false, false, 0));
        assertFalse(DisguiseChatTheme.supports(user, null, 1, false, true, false, 0));
        assertFalse(DisguiseChatTheme.supports(user, null, 1, false, false, true, 0));
        assertFalse(DisguiseChatTheme.supports(user, null, 1, false, false, false, 1));
    }

    @Test public void invalidChoiceDoesNotOverwriteSavedValue() {
        DisguiseChatTheme.save(settings, DisguiseListTheme.QQ);
        assertThrows(IllegalArgumentException.class, () -> DisguiseChatTheme.save(settings, "invalid"));
        assertTrue(DisguiseChatTheme.isEnabled(settings, true));
    }

    @Test public void paletteIsLocalAndMessageDrawablesAreNotSharedAcrossChats() {
        int global = Theme.getColor(Theme.key_chat_outBubble);
        DisguiseChatTheme.Palette a = new DisguiseChatTheme.Palette(), b = new DisguiseChatTheme.Palette();
        assertEquals(0xff0060bb, a.getColor(Theme.key_chat_outBubble));
        assertEquals(0xffffffff, a.getColor(Theme.key_chat_messageTextOut));
        assertEquals(0xff1a1a1a, a.getColor(Theme.key_chat_messageTextIn));
        assertEquals(0, a.getColor(Theme.key_chat_outBubbleGradient1));
        assertFalse(a.isDark()); assertFalse(a.hasGradientService());
        assertSame(a.getDrawable(Theme.key_drawable_msgOut), a.getDrawable(Theme.key_drawable_msgOut));
        assertNotSame(a.getDrawable(Theme.key_drawable_msgOut), b.getDrawable(Theme.key_drawable_msgOut));
        assertNotSame(a.getDrawable(Theme.key_drawable_msgOut), a.getDrawable(Theme.key_drawable_msgOutMedia));
        assertEquals(global, Theme.getColor(Theme.key_chat_outBubble));
    }

    @Test public void textAndNativeControlsRemainReadableOnAllMessageSurfaces() {
        DisguiseChatTheme.Palette palette = new DisguiseChatTheme.Palette();
        int[][] pairs = {
            {Theme.key_chat_messageTextOut, Theme.key_chat_outBubble},
            {Theme.key_chat_messageTextOut, Theme.key_chat_outBubbleSelected},
            {Theme.key_chat_messageTextIn, Theme.key_chat_inBubble},
            {Theme.key_chat_inTimeText, Theme.key_chat_inBubble},
            {Theme.key_chat_outTimeText, Theme.key_chat_outBubble},
            {Theme.key_chat_outMenu, Theme.key_chat_outBubble},
            {Theme.key_chat_inMenu, Theme.key_chat_inBubble},
            {Theme.key_chat_outMediaIcon, Theme.key_chat_outLoader},
            {Theme.key_chat_inMediaIcon, Theme.key_chat_inLoader},
            {Theme.key_chat_outFileInfoText, Theme.key_chat_outBubble},
            {Theme.key_chat_outAudioTitleText, Theme.key_chat_outBubble},
            {Theme.key_chat_outVoiceSeekbarFill, Theme.key_chat_outBubble},
            {Theme.key_chat_outReactionButtonTextSelected, Theme.key_chat_outReactionButtonBackground},
            {Theme.key_chat_inReactionButtonTextSelected, Theme.key_chat_inReactionButtonBackground},
            {Theme.key_chat_outAudioProgress, Theme.key_chat_outLoader},
            {Theme.key_chat_serviceIcon, Theme.key_chat_serviceBackground},
            {Theme.key_chat_serviceIcon, Theme.key_chat_serviceBackgroundSelected},
            {Theme.key_chat_serviceText, Theme.key_chat_serviceBackground},
            {Theme.key_chat_messagePanelHint, Theme.key_windowBackgroundWhite}
        };
        for (int[] pair : pairs) {
            double ratio = androidx.core.graphics.ColorUtils.calculateContrast(palette.getColor(pair[0]), palette.getColor(pair[1]));
            assertTrue("foreground=" + pair[0] + " background=" + pair[1] + " ratio=" + ratio, ratio >= 4.5);
        }
        // Unselected reactions actually blend their background at 15.6% over the bubble.
        int[][] reactions = {
            {Theme.key_chat_outReactionButtonText, Theme.key_chat_outReactionButtonBackground, Theme.key_chat_outBubble},
            {Theme.key_chat_inReactionButtonText, Theme.key_chat_inReactionButtonBackground, Theme.key_chat_inBubble}
        };
        for (int[] reaction : reactions) {
            int overlay = androidx.core.graphics.ColorUtils.setAlphaComponent(palette.getColor(reaction[1]), (int) (255 * .156f));
            int background = androidx.core.graphics.ColorUtils.compositeColors(overlay, palette.getColor(reaction[2]));
            assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(palette.getColor(reaction[0]), background) >= 4.5);
        }
        assertEquals(255, android.graphics.Color.alpha(palette.getColor(Theme.key_chat_serviceBackground)));
    }

    @Test public void wallpaperPriorityHonorsChatThenCustomGlobalAndRestoresDefault() {
        android.graphics.drawable.Drawable chat = new android.graphics.drawable.ColorDrawable(0xff123456);
        android.graphics.drawable.Drawable global = new android.graphics.drawable.ColorDrawable(0xffabcdef);
        android.graphics.drawable.Drawable fallback = new DisguiseChatTheme.Palette().wallpaper;
        assertSame(chat, DisguiseChatTheme.selectWallpaper(chat, global, true, fallback));
        assertSame(chat, DisguiseChatTheme.selectWallpaper(chat, global, false, fallback));
        assertSame(global, DisguiseChatTheme.selectWallpaper(null, global, true, fallback));
        assertSame(fallback, DisguiseChatTheme.selectWallpaper(null, global, false, fallback));
        assertSame(fallback, DisguiseChatTheme.selectWallpaper(null, null, true, fallback));
        assertEquals(0xffabcdef, ((android.graphics.drawable.ColorDrawable) global).getColor());
    }

    @Test public void sharedDividerIsClonedWithoutGlobalColorOrShaderMutation() {
        android.graphics.Paint saved = Theme.dividerPaint;
        try {
            Theme.dividerPaint = new android.graphics.Paint();
            Theme.dividerPaint.setColor(0xff123456);
            android.graphics.Shader shader = new android.graphics.LinearGradient(0, 0, 0, 10,
                    0xff112233, 0xff445566, android.graphics.Shader.TileMode.CLAMP);
            Theme.dividerPaint.setShader(shader);
            android.graphics.Paint local = new DisguiseChatTheme.Palette().getPaint(Theme.key_paint_divider);
            assertNotSame(Theme.dividerPaint, local);
            assertEquals(Theme.getDefaultColor(Theme.key_divider), local.getColor());
            assertNull(local.getShader());
            assertEquals(0xff123456, Theme.dividerPaint.getColor());
            assertSame(shader, Theme.dividerPaint.getShader());
        } finally { Theme.dividerPaint = saved; }
    }
}
