package com.nullgram.client.appearance;

import android.content.Context;
import android.graphics.Paint;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;

/** Scoped styling for the three QQ home tabs, never Telegram's global theme. */
public final class QqPageStyle {
    public static final int TEXT = 0xff20242b, SECONDARY = 0xff697380, BLUE = 0xff008cf0;
    public static final int BACKGROUND = 0xfff4f5f7, WHITE = 0xffffffff, LINE = 0xffeceef1;
    private QqPageStyle() {}

    public static boolean enabled(boolean homeTab) { return homeTab && DisguiseListTheme.isQq(); }
    public static boolean isPalette(Theme.ResourcesProvider provider) { return provider instanceof Palette; }
    public static Theme.ResourcesProvider wrap(Theme.ResourcesProvider original, boolean enabled) {
        if (original instanceof Palette palette) original = palette.original;
        return enabled ? new Palette(original) : original;
    }
    public static int rowHeight(Context context) { return 72 + Math.round(36 * Math.max(0, QqListStyle.fontScale(context) - 1)); }
    public static void header(ActionBar bar) {
        bar.setBackgroundColor(WHITE);
        bar.setTitleColor(TEXT);
        bar.setItemsColor(TEXT, false);
        bar.setItemsBackgroundColor(LINE, false);
    }

    private static final class Palette implements Theme.ResourcesProvider {
        private final Theme.ResourcesProvider original;
        private final Paint divider = new Paint();
        private final Theme.ResourcesProvider list = DisguiseListTheme.wrap(null, true);
        Palette(Theme.ResourcesProvider original) { this.original = original; divider.setColor(LINE); }
        @Override public int getColor(int key) {
            if (key == Theme.key_windowBackgroundGray) return BACKGROUND;
            if (key == Theme.key_windowBackgroundWhiteBlackText || key == Theme.key_chats_name
                    || key == Theme.key_actionBarDefaultTitle || key == Theme.key_actionBarDefaultIcon) return TEXT;
            if (key == Theme.key_windowBackgroundWhiteGrayText || key == Theme.key_windowBackgroundWhiteGrayText2
                    || key == Theme.key_windowBackgroundWhiteGrayText3 || key == Theme.key_windowBackgroundWhiteGrayText4
                    || key == Theme.key_windowBackgroundWhiteGrayIcon || key == Theme.key_emptyListPlaceholder) return SECONDARY;
            if (key == Theme.key_telegram_color || key == Theme.key_telegram_color_text
                    || key == Theme.key_windowBackgroundWhiteBlueText || key == Theme.key_windowBackgroundWhiteBlueText3
                    || key == Theme.key_windowBackgroundWhiteBlueHeader || key == Theme.key_chats_verifiedBackground) return BLUE;
            if (key == Theme.key_divider) return LINE;
            return list.getColor(key);
        }
        @Override public Paint getPaint(String key) { return Theme.key_paint_divider.equals(key) ? divider : null; }
        @Override public boolean isDark() { return false; }
    }
}
