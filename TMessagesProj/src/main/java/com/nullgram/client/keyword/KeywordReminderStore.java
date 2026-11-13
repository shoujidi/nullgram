// SPDX-License-Identifier: GPL-3.0-only
// Nullgram modification, 2026-11-01.
package com.nullgram.client.keyword;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class KeywordReminderStore implements KeywordReminderEngine.Store {
    public static final String PREFERENCES = "nullgram_keyword_reminders";
    static final int HISTORY_LIMIT = 200;
    private static final String PRIVATE_PENDING_PREFIX = "private_pending_";
    private final SharedPreferences preferences;
    public KeywordReminderStore(Context context) { preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE); }
    public boolean enabled() { return preferences.getBoolean("enabled", false); }
    public boolean keywordsEnabled() { return preferences.getBoolean("keywords_enabled", true); }
    public void keywordsEnabled(boolean value) { save(preferences.edit().putBoolean("keywords_enabled", value)); }
    public List<WatchedPerson> watched(long owner) {
        List<WatchedPerson> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString("watched_" + owner, "[]"));
            Set<Long> ids = new LinkedHashSet<>();
            for (int i = 0; i < array.length() && result.size() < KeywordReminderEngine.MAX_WATCHED; i++) {
                try {
                    JSONObject item = array.getJSONObject(i);
                    WatchedPerson person = new WatchedPerson(item.getLong("id"), item.getString("name"),
                            item.getInt("scopes"), item.getBoolean("online"), item.optBoolean("private_read"));
                    if (person.id != owner && ids.add(person.id)) result.add(person);
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        return result;
    }
    public void watched(long owner, List<WatchedPerson> values) {
        JSONArray array = new JSONArray();
        try {
            for (WatchedPerson person : values) array.put(new JSONObject().put("id", person.id).put("name", person.name)
                    .put("scopes", person.scopes).put("online", person.online).put("private_read", person.privateRead));
        } catch (Exception error) { throw new IllegalArgumentException(error); }
        SharedPreferences.Editor editor = preferences.edit().putString("watched_" + owner, array.toString());
        Set<Long> activeReadPeers = new LinkedHashSet<>();
        for (WatchedPerson person : values) if (person.privateRead) activeReadPeers.add(person.id);
        for (WatchedPerson person : watched(owner)) {
            if (person.privateRead && !activeReadPeers.contains(person.id)) editor.remove(privatePendingKey(owner, person.id));
        }
        save(editor);
    }
    public Map<Long, Long> onlineAlerts(long owner) {
        Map<Long, Long> result = new HashMap<>();
        try {
            JSONObject json = new JSONObject(preferences.getString("online_" + owner, "{}"));
            for (WatchedPerson person : watched(owner)) {
                long time = json.optLong(Long.toString(person.id), 0);
                if (time > 0) result.put(person.id, time);
            }
        } catch (Exception ignored) { }
        return result;
    }
    private static JSONObject onlineAlertValues(Map<Long, Long> values) {
        JSONObject json = new JSONObject();
        try {
            for (Map.Entry<Long, Long> entry : values.entrySet()) json.put(entry.getKey().toString(), entry.getValue());
        } catch (Exception error) { throw new IllegalArgumentException(error); }
        return json;
    }

    public boolean bannerEnabled(long owner) {
        return preferences.getBoolean("watched_banner_" + owner, false);
    }

    void bannerEnabled(long owner, boolean value) {
        save(preferences.edit().putBoolean("watched_banner_" + owner, value));
    }

    static String historyKey(long owner) { return "watched_history_" + owner; }

    List<WatchedPersonRecord> history(long owner) {
        List<WatchedPersonRecord> result = new ArrayList<>();
        if (owner <= 0) return result;
        try {
            JSONArray array = new JSONArray(preferences.getString(historyKey(owner), "[]"));
            for (int i = 0; i < array.length() && result.size() < HISTORY_LIMIT; i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                long dialog = item.optLong("dialog");
                long date = item.optLong("date");
                if (dialog == 0 || date <= 0) continue;
                result.add(new WatchedPersonRecord(item.optString("name"), item.optString("chat"), date,
                        dialog, item.optInt("message"), item.optLong("topic"), item.optInt("scope"), item.optInt("private_read_count")));
            }
        } catch (org.json.JSONException ignored) {
            // A malformed history should not hide the rest of the reminder settings.
        }
        return result;
    }

    void clearHistory(long owner) {
        if (owner > 0) save(preferences.edit().remove(historyKey(owner)));
    }

    @Override public void recordWatched(long owner, WatchedPersonRecord record, Set<String> seen, Map<Long, Long> onlineAlerts) {
        SharedPreferences.Editor editor = recordEditor(owner, record, seen);
        if (onlineAlerts != null) editor.putString("online_" + owner, onlineAlertValues(onlineAlerts).toString());
        save(editor);
    }

    @Override public List<Integer> pendingPrivateReads(long owner, long dialogId) {
        List<Integer> ids = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(privatePendingKey(owner, dialogId), "[]"));
            for (int i = Math.max(0, array.length() - KeywordReminderEngine.MAX_TRACKED_PRIVATE_SENDS); i < array.length(); i++) {
                int id = array.optInt(i);
                if (id > 0) ids.add(id);
            }
        } catch (org.json.JSONException ignored) { }
        return ids;
    }

    @Override public void rememberPrivateReads(long owner, long dialogId, List<Integer> pending) {
        // Send confirmations can arrive in bursts. Keep disk I/O off the sending UI path.
        writePrivatePending(preferences.edit(), owner, dialogId, pending).apply();
    }

    @Override public void recordPrivateRead(long owner, WatchedPersonRecord record, Set<String> seen, List<Integer> remaining) {
        save(writePrivatePending(recordEditor(owner, record, seen), owner, record.dialogId, remaining));
    }

    private static String privatePendingKey(long owner, long dialogId) {
        return PRIVATE_PENDING_PREFIX + owner + "_" + dialogId;
    }

    private static SharedPreferences.Editor writePrivatePending(SharedPreferences.Editor editor, long owner, long dialogId,
                                                               List<Integer> pending) {
        String key = privatePendingKey(owner, dialogId);
        return pending.isEmpty() ? editor.remove(key) : editor.putString(key, new JSONArray(pending).toString());
    }

    private void clearPrivatePending(SharedPreferences.Editor editor) {
        for (String key : preferences.getAll().keySet()) if (key.startsWith(PRIVATE_PENDING_PREFIX)) editor.remove(key);
    }

    private SharedPreferences.Editor recordEditor(long owner, WatchedPersonRecord record, Set<String> seen) {
        JSONArray history = new JSONArray();
        try {
            history.put(historyItem(record));
            for (WatchedPersonRecord previous : history(owner)) {
                if (history.length() == HISTORY_LIMIT) break;
                history.put(historyItem(previous));
            }
        } catch (org.json.JSONException error) {
            throw new IllegalArgumentException(error);
        }
        return seenEditor(owner, seen).putString(historyKey(owner), history.toString());
    }

    private static JSONObject historyItem(WatchedPersonRecord record) throws org.json.JSONException {
        return new JSONObject().put("name", record.name).put("chat", record.chatTitle).put("date", record.date)
                .put("dialog", record.dialogId).put("message", record.messageId)
                .put("topic", record.topicId).put("scope", record.scope).put("private_read_count", record.privateReadCount);
    }
    public int account() { return preferences.getInt("account", -1); }
    public long userId() { return preferences.getLong("user_id", 0); }
    public long since() { return preferences.getLong("since", 0); }
    public List<String> keywords() { return strings(preferences.getString("keywords", "[]")); }
    public Set<String> seen(long userId) {
        if (preferences.getLong("seen_user_id", 0) != userId) return new LinkedHashSet<>();
        List<String> items = strings(preferences.getString("seen", "[]"));
        return new LinkedHashSet<>(items.subList(Math.max(0, items.size() - KeywordReminderEngine.MAX_SEEN), items.size()));
    }
    public void enabled(boolean value) {
        SharedPreferences.Editor editor = preferences.edit().putBoolean("enabled", value);
        if (!value) clearPrivatePending(editor);
        save(editor);
    }
    public void bind(int account, long userId, long since) {
        SharedPreferences.Editor editor = preferences.edit().putInt("account", account).putLong("user_id", userId).putLong("since", since);
        clearPrivatePending(editor);
        save(editor);
    }
    public void keywords(List<String> values) { save(preferences.edit().putString("keywords", new JSONArray(values).toString())); }
    public void remember(long userId, Set<String> values) {
        save(seenEditor(userId, values));
    }
    private SharedPreferences.Editor seenEditor(long userId, Set<String> values) {
        return preferences.edit().putLong("seen_user_id", userId).putString("seen", new JSONArray(values).toString());
    }
    private static void save(SharedPreferences.Editor editor) {
        // Commit toggles and delivered records together with their pending state.
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
