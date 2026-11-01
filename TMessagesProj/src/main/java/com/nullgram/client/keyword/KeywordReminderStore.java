// SPDX-License-Identifier: GPL-3.0-only
// Nullgram modification, 2026-11-01.
package com.nullgram.client.keyword;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class KeywordReminderStore implements KeywordReminderEngine.Store {
    public static final String PREFERENCES = "nullgram_keyword_reminders";
    private final SharedPreferences preferences;
    public KeywordReminderStore(Context context) { preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE); }
    public boolean enabled() { return preferences.getBoolean("enabled", false); }
    public int account() { return preferences.getInt("account", -1); }
    public long userId() { return preferences.getLong("user_id", 0); }
    public long since() { return preferences.getLong("since", 0); }
    public List<String> keywords() { return strings(preferences.getString("keywords", "[]")); }
    public Set<String> seen(long userId) {
        if (preferences.getLong("seen_user_id", 0) != userId) return new LinkedHashSet<>();
        List<String> items = strings(preferences.getString("seen", "[]"));
        return new LinkedHashSet<>(items.subList(Math.max(0, items.size() - KeywordReminderEngine.MAX_SEEN), items.size()));
    }
    public void enabled(boolean value) { save(preferences.edit().putBoolean("enabled", value)); }
    public void bind(int account, long userId, long since) {
        save(preferences.edit().putInt("account", account).putLong("user_id", userId).putLong("since", since));
    }
    public void keywords(List<String> values) { save(preferences.edit().putString("keywords", new JSONArray(values).toString())); }
    public void remember(long userId, Set<String> values) {
        save(preferences.edit().putLong("seen_user_id", userId).putString("seen", new JSONArray(values).toString()));
    }
    private static void save(SharedPreferences.Editor editor) {
        // Synchronous bounded writes: a completed toggle/restart must not race an outstanding apply().
        if (!editor.commit()) throw new IllegalStateException("KEYWORD_PREFERENCES_WRITE_FAILED");
    }
    private static List<String> strings(String json) {
        ArrayList<String> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "");
                if (!value.isEmpty()) result.add(value);
            }
        } catch (Exception ignored) { /* Corrupt cache does not erase unrelated keyword configuration. */ }
        return result;
    }
}
