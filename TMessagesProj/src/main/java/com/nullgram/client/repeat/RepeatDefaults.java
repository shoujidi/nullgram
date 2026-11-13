package com.nullgram.client.repeat;

import top.nkbe.niagram.config.NyaConfig;

public final class RepeatDefaults {
    private RepeatDefaults() {}

    public static int getCount(RepeatTask.Type type) {
        int fallback = switch (type) {
            case EXISTING -> RepeatConfig.EXISTING_DEFAULT_COUNT;
            case NEW_MESSAGE -> RepeatConfig.NEW_DEFAULT_COUNT;
        };
        try {
            int count = NyaConfig.getPreferences().getInt(key(type, "Count"), fallback);
            return RepeatConfig.isValidCount(count) ? count : fallback;
        } catch (ClassCastException invalidPreference) {
            return fallback;
        }
    }

    public static long getIntervalMs(RepeatTask.Type type) {
        long fallback = RepeatConfig.DEFAULT_INTERVAL_MS;
        try {
            long intervalMs = NyaConfig.getPreferences().getLong(key(type, "IntervalMs"), fallback);
            return RepeatConfig.isValidInterval(intervalMs) ? intervalMs : fallback;
        } catch (ClassCastException invalidPreference) {
            return fallback;
        }
    }

    public static void set(RepeatTask.Type type, int count, long intervalMs) {
        if (!RepeatConfig.isValid(count, intervalMs)) throw new IllegalArgumentException("Invalid repeat defaults");
        NyaConfig.getPreferences().edit()
                .putInt(key(type, "Count"), count)
                .putLong(key(type, "IntervalMs"), intervalMs)
                .apply();
    }

    private static String key(RepeatTask.Type type, String suffix) {
        String prefix = switch (type) {
            case EXISTING -> "NullgramExistingRepeatDefault";
            case NEW_MESSAGE -> "NullgramNewRepeatDefault";
        };
        return prefix + suffix;
    }
}
