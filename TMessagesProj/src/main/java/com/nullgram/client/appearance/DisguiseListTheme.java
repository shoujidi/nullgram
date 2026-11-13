package com.nullgram.client.appearance;

import android.content.SharedPreferences;
import android.util.SparseIntArray;

import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.Theme;

/** A page-local appearance. Never writes to Telegram's global theme. */
public final class DisguiseListTheme {
    public static final String ORIGINAL = "original";
    public static final String QQ = "qq";
    private static final String KEY = "nullgramDisguiseListTheme";
    public static final int WHITE = 0xffffffff;
    public static final int TEXT = 0xff1a1a1a;
    public static final int GRAY = 0xff999999;
    public static final int BLUE = 0xff0099ff;
    public static final int RED = 0xfff74c31;
    public static final int SEARCH = 0xfff2f3f5;
    public static final int DIVIDER = 0xffeeeeee;

    private DisguiseListTheme() {}

    public static boolean isQq() {
        return BuildConfig.NULLGRAM_DISGUISE && isQq(MessagesController.getGlobalMainSettings(), true);
    }

    public static boolean isQq(SharedPreferences settings, boolean disguiseEdition) {
        return disguiseEdition && QQ.equals(settings.getString(KEY, ORIGINAL));
    }

    public static void set(String theme) {
        save(MessagesController.getGlobalMainSettings(), theme);
    }

    public static void save(SharedPreferences settings, String theme) {
        if (!ORIGINAL.equals(theme) && !QQ.equals(theme)) {
            throw new IllegalArgumentException("Unknown list theme: " + theme);
        }
        settings.edit().putString(KEY, theme).apply();
    }

    public static boolean appliesTo(boolean onlySelect, int folderId, long communityId,
                                    boolean mainList, boolean preview) {
        return !onlySelect && folderId == 0 && communityId == 0 && mainList && !preview;
    }

    public static Theme.ResourcesProvider wrap(Theme.ResourcesProvider original, boolean enabled) {
        if (original instanceof Palette) original = ((Palette) original).original;
        return enabled ? new Palette(original) : original;
    }

    public static boolean isPalette(Theme.ResourcesProvider provider) {
        return provider instanceof Palette;
    }

    private static final class Palette implements Theme.ResourcesProvider {
        private final Theme.ResourcesProvider original;
        private final SparseIntArray colors = new SparseIntArray();

        Palette(Theme.ResourcesProvider original) {
            this.original = original;
            put(WHITE, Theme.key_windowBackgroundWhite, Theme.key_actionBarDefault,
                    Theme.key_actionBarActionModeDefault, Theme.key_actionBarDefaultArchived,
                    Theme.key_chats_unreadCounterText, Theme.key_actionBarDefaultSubmenuBackground);
            put(TEXT, Theme.key_chats_name, Theme.key_chats_nameArchived, Theme.key_chats_nameMessageArchived, Theme.key_chats_nameMessageArchived_threeLines, Theme.key_chats_nameMessage,
                    Theme.key_chats_nameMessage_threeLines, Theme.key_windowBackgroundWhiteBlackText,
                    Theme.key_actionBarDefaultTitle, Theme.key_actionBarDefaultIcon,
                    Theme.key_actionBarActionModeDefaultIcon, Theme.key_actionBarDefaultSearch,
                    Theme.key_actionBarDefaultSubmenuItem, Theme.key_actionBarDefaultSubmenuItemIcon,
                    Theme.key_telegram_color_dialogsLogo);
            put(GRAY, Theme.key_chats_message, Theme.key_chats_messageArchived, Theme.key_chats_message_threeLines,
                    Theme.key_chats_date, Theme.key_chats_muteIcon, Theme.key_chats_pinnedIcon,
                    Theme.key_actionBarDefaultSearchPlaceholder, Theme.key_actionBarDefaultSubtitle,
                    Theme.key_windowBackgroundWhiteGrayText, Theme.key_windowBackgroundWhiteGrayText2);
            put(BLUE, Theme.key_chats_actionBackground, Theme.key_chats_secretName,
                    Theme.key_chats_draft, Theme.key_chats_sentCheck, Theme.key_chats_sentReadCheck,
                    Theme.key_actionBarTabActiveText, Theme.key_actionBarTabLine,
                    Theme.key_windowBackgroundWhiteBlueText, Theme.key_telegram_color);
            put(RED, Theme.key_chats_unreadCounter);
            put(0xffcccccc, Theme.key_chats_unreadCounterMuted);
            put(0xfff7f7f7, Theme.key_chats_pinnedOverlay);
            put(SEARCH, Theme.key_windowBackgroundGray, Theme.key_actionBarDefaultSelector,
                    Theme.key_actionBarActionModeDefaultSelector, Theme.key_listSelector);
            put(DIVIDER, Theme.key_divider);
        }

        private void put(int color, int... keys) {
            for (int key : keys) colors.put(key, color);
        }

        @Override public int getColor(int key) {
            int index = colors.indexOfKey(key);
            return index >= 0 ? colors.valueAt(index) : Theme.getDefaultColor(key);
        }

        @Override public boolean isDark() { return false; }
    }
}
