package com.nullgram.client.appearance;

import android.content.SharedPreferences;

/** A theme is a one-time assignment, never a lock on individual settings. */
final class DisguiseThemeConfig {
    static final String LABEL = "nullgramDisguiseLabel";
    static final String CUSTOM_NAME = "nullgramDisguiseCustomName";
    static final String HOME = "nullgramDisguiseListTheme";
    static final String CHAT = "nullgramDisguiseChatTheme";
    private static final String THEME = "nullgramDisguiseTheme";

    static void applyQq(SharedPreferences settings, boolean name, boolean home, boolean chat) {
        SharedPreferences.Editor edit = settings.edit().putString(THEME, "qq");
        if (name) edit.remove("nullgramDisguiseName").remove(CUSTOM_NAME).putString(LABEL, "DISGUISE_QQ");
        if (home) edit.putString(HOME, "qq");
        if (chat) edit.putString(CHAT, "qq");
        edit.apply();
    }

    static boolean completeQq(SharedPreferences settings, boolean qqIcon) {
        return qqIcon && "DISGUISE_QQ".equals(settings.getString(LABEL, ""))
                && settings.getString(CUSTOM_NAME, "").isEmpty()
                && "qq".equals(settings.getString(HOME, "original"))
                && "qq".equals(settings.getString(CHAT, "original"));
    }

    static boolean customizedQq(SharedPreferences settings) { return "qq".equals(settings.getString(THEME, "")); }

    static boolean original(SharedPreferences settings, boolean disguiseIcon) {
        return !disguiseIcon && settings.getString(LABEL, "").isEmpty()
                && settings.getString(CUSTOM_NAME, "").isEmpty()
                && "original".equals(settings.getString(HOME, "original"))
                && "original".equals(settings.getString(CHAT, "original"));
    }

    static void reset(SharedPreferences settings) {
        settings.edit().remove(THEME).remove(LABEL).remove(CUSTOM_NAME).remove("nullgramDisguiseName")
                .putString(HOME, "original").putString(CHAT, "original").apply();
    }
}
