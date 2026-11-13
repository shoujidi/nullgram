package com.nullgram.client.appearance;

import android.content.Context;
import org.telegram.messenger.AndroidUtilities;

/** QQ 9.2.15 list geometry. Keep screen-specific presentation out of account/navigation code. */
public final class QqListStyle {
    private QqListStyle() {}
    public static final int ROW_HEIGHT = 72;
    public static final int AVATAR_SIZE = 58;
    public static final int AVATAR_START = 12;
    public static final int NAV_HEIGHT = 54;
    public static final int TITLE_SP = 17;
    public static final int SUMMARY_SP = 14;
    public static final int HEADER_HEIGHT = 50;

    public static float fontScale(Context context) {
        return context.getResources().getConfiguration().fontScale;
    }
    public static int rowHeight(Context context) {
        return ROW_HEIGHT + Math.round(34 * Math.max(0, fontScale(context) - 1));
    }
    public static int headerHeight(Context context) {
        return HEADER_HEIGHT + Math.round(30 * Math.max(0, fontScale(context) - 1));
    }
    public static int navigationHeight(Context context) {
        return NAV_HEIGHT + Math.round(12 * Math.max(0, fontScale(context) - 1));
    }
    public static float sp(Context context, float value) {
        return AndroidUtilities.density * value * fontScale(context);
    }
}
