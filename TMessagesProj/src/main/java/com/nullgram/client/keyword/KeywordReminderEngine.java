// SPDX-License-Identifier: GPL-3.0-only
// Nullgram modification, 2026-11-01. Single UI-thread lifecycle; no background service.
package com.nullgram.client.keyword;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class KeywordReminderEngine {
    public static final int MAX_KEYWORDS = 100;
    public static final int MAX_KEYWORD_LENGTH = 128;
    public static final int MAX_SEEN = 512;

    public interface Store {
        boolean enabled();
        int account();
        long userId();
        long since();
        List<String> keywords();
        Set<String> seen(long userId);
        void enabled(boolean value);
        void bind(int account, long userId, long since);
        void keywords(List<String> values);
        void remember(long userId, Set<String> values);
    }
    public interface Accounts {
        boolean valid(int account, long userId);
        long nowSeconds(int account);
    }
    public interface Events { void register(int account); void unregister(int account); }
    public interface Tasks { void post(Runnable action); void cancel(Runnable action); }
    public interface Publisher { boolean publish(Message message, int account, long userId); }
    public interface Matcher { boolean matches(String text, List<String> keywords); }

    public static final class Message {
        public final long dialogId, topicId;
        public final int id;
        public final long date;
        public final String title, text;
        public Message(long dialogId, int id, long date, String title, String text) {
            this(dialogId, id, date, title, text, 0);
        }
        public Message(long dialogId, int id, long date, String title, String text, long topicId) {
            this.dialogId = dialogId; this.id = id; this.date = date;
            this.title = title; this.text = text; this.topicId = topicId;
        }
        public String key() { return dialogId + ":" + id; }
    }

    private final Store store;
    private final Accounts accounts;
    private final Events events;
    private final Tasks tasks;
    private final Publisher publisher;
    private final Matcher matcher;
    private final Set<Runnable> pending = new HashSet<>();
    private final Set<String> pendingKeys = new HashSet<>();
    private LinkedHashSet<String> seen = new LinkedHashSet<>();
    private List<String> keywords;
    private boolean enabled;
    private int account = -1;
    private long userId, since, version;

    public KeywordReminderEngine(Store store, Accounts accounts, Events events, Tasks tasks, Publisher publisher, Matcher matcher) {
        this.store = store; this.accounts = accounts; this.events = events;
        this.tasks = tasks; this.publisher = publisher; this.matcher = matcher;
        keywords = new ArrayList<>(store.keywords());
    }

    public boolean isEnabled() { return enabled; }
    public int account() { return account; }
    public long userId() { return userId; }
    public List<String> keywords() { return new ArrayList<>(keywords); }
    public int pendingCount() { return pending.size(); }

    public void restore() {
        if (!store.enabled()) return;
        if (!accounts.valid(store.account(), store.userId())) { disable(); return; }
        activate(store.account(), store.userId(), store.since());
    }

    /** Explicitly bind one logged-in account; switching the app's visible account does not rebind. */
    public boolean enable(int selectedAccount, long selectedUserId) {
        if (!accounts.valid(selectedAccount, selectedUserId)) return false;
        if (enabled && account == selectedAccount && userId == selectedUserId) return true;
        long start = accounts.nowSeconds(selectedAccount);
        stopRuntime();
        store.bind(selectedAccount, selectedUserId, start);
        store.enabled(true);
        activate(selectedAccount, selectedUserId, start);
        return true;
    }

    private void activate(int selectedAccount, long selectedUserId, long start) {
        if (enabled && account == selectedAccount && userId == selectedUserId) return;
        stopRuntime();
        account = selectedAccount; userId = selectedUserId; since = start;
        seen = new LinkedHashSet<>(store.seen(userId));
        enabled = true;
        events.register(account);
    }

    public void disable() {
        stopRuntime(); // Invalidate callbacks before persistence or any external work.
        store.enabled(false);
    }

    /** Process teardown only. Persisted on/off state is retained for a normal restart. */
    public void shutdown() { stopRuntime(); }

    private void stopRuntime() {
        version++;
        boolean wasEnabled = enabled;
        enabled = false;
        if (wasEnabled) events.unregister(account);
        cancelPending();
        seen.clear();
    }

    private void cancelPending() {
        for (Runnable action : new ArrayList<>(pending)) tasks.cancel(action);
        pending.clear();
        pendingKeys.clear();
    }

    public void setKeywords(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        Set<String> folded = new HashSet<>();
        for (String value : values) {
            String word = value == null ? "" : value.trim();
            if (word.isEmpty() || word.length() > MAX_KEYWORD_LENGTH) throw new IllegalArgumentException("KEYWORD_LENGTH");
            if (folded.add(foldEnglish(word))) normalized.add(word);
        }
        if (normalized.size() > MAX_KEYWORDS) throw new IllegalArgumentException("KEYWORD_COUNT");
        store.keywords(new ArrayList<>(normalized));
        version++;
        cancelPending();
        keywords = new ArrayList<>(normalized);
    }

    /** Disabled gate precedes matching, de-dup access and scheduling. */
    public void onMessage(int sourceAccount, Message message) {
        if (!enabled) return;
        if (sourceAccount != account || message == null || message.id == 0 || message.dialogId == 0) return;
        if (!accounts.valid(account, userId)) { disable(); return; }
        if (message.date < since || keywords.isEmpty() || message.text == null || message.text.isEmpty()) return;
        if (!matcher.matches(message.text, keywords)) return;
        String key = message.key();
        if (seen.contains(key) || !pendingKeys.add(key)) return;
        long token = version;
        Runnable action = new Runnable() {
            @Override public void run() {
                // A cancelled callback may already have been dequeued. Never remove a new epoch's key.
                if (!enabled || token != version) return;
                pending.remove(this);
                pendingKeys.remove(key);
                if (!accounts.valid(account, userId)) { disable(); return; }
                if (seen.contains(key)) return;
                try {
                    if (publisher.publish(message, account, userId) && enabled && token == version) {
                        seen.add(key);
                        while (seen.size() > MAX_SEEN) seen.remove(seen.iterator().next());
                        store.remember(userId, seen);
                    }
                } catch (RuntimeException failure) {
                    stopRuntime();
                    try { store.enabled(false); } catch (RuntimeException ignored) { }
                }
            }
        };
        pending.add(action);
        tasks.post(action);
    }

    public static String foldEnglish(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            result.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return result.toString();
    }

    public static boolean contains(String text, List<String> keywords) {
        String normalized = foldEnglish(text);
        for (String keyword : keywords) if (normalized.contains(foldEnglish(keyword))) return true;
        return false;
    }
}
