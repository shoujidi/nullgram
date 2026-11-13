// SPDX-License-Identifier: GPL-3.0-only
// Nullgram modification, 2026-11-01. Single UI-thread lifecycle; no background service.
package com.nullgram.client.keyword;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.NavigableSet;
import java.util.TreeSet;

public final class KeywordReminderEngine {
    public static final int MAX_KEYWORDS = 100;
    public static final int MAX_KEYWORD_LENGTH = 128;
    public static final int MAX_SEEN = 512;
    public static final int MAX_WATCHED = 100;
    public static final int ONLINE_COOLDOWN_SECONDS = 60;

    public interface Store {
        boolean enabled();
        default boolean keywordsEnabled() { return true; }
        default void keywordsEnabled(boolean value) { }
        default List<WatchedPerson> watched(long owner) { return Collections.emptyList(); }
        default void watched(long owner, List<WatchedPerson> values) { }
        default Map<Long, Long> onlineAlerts(long owner) { return Collections.emptyMap(); }
        boolean bannerEnabled(long owner);
        void recordWatched(long owner, WatchedPersonRecord record, Set<String> seen, Map<Long, Long> onlineAlerts);
        List<Integer> pendingPrivateReads(long owner, long dialogId);
        void rememberPrivateReads(long owner, long dialogId, List<Integer> pending);
        void recordPrivateRead(long owner, WatchedPersonRecord record, Set<String> seen, List<Integer> remaining);
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
    public interface Tasks {
        default void post(Runnable action) { post(action, 0); }
        void post(Runnable action, long delayMillis);
        void cancel(Runnable action);
    }
    public interface Publisher {
        boolean publish(Message message, int account, long userId);
        default long watchedMessageDelayMillis(Message message, int account, long userId) { return 0; }
        default void publishWatched(Message message, WatchedPersonRecord record, int account, long userId, boolean bannerEnabled) {
            publish(message, account, userId);
        }
    }
    public interface Matcher { boolean matches(String text, List<String> keywords); }

    public static final class Message {
        public final long dialogId, topicId;
        public final int id;
        public final long date;
        public final String title, text;
        public final long senderId;
        public final int scope;
        public final boolean online;
        public final int privateReadCount;
        public Message(long dialogId, int id, long date, String title, String text) {
            this(dialogId, id, date, title, text, 0);
        }
        public Message(long dialogId, int id, long date, String title, String text, long topicId) {
            this(dialogId, id, date, title, text, topicId, 0, 0, false);
        }
        public Message(long dialogId, int id, long date, String title, String text, long topicId,
                       long senderId, int scope, boolean online) {
            this(dialogId, id, date, title, text, topicId, senderId, scope, online, 0);
        }
        private Message(long dialogId, int id, long date, String title, String text, long topicId,
                        long senderId, int scope, boolean online, int privateReadCount) {
            this.dialogId = dialogId; this.id = id; this.date = date;
            this.title = title; this.text = text == null ? "" : text; this.topicId = topicId;
            this.senderId = senderId; this.scope = scope; this.online = online;
            this.privateReadCount = privateReadCount;
        }
        public String key() {
            if (online) return "online:" + senderId + ":" + date;
            return (privateReadCount > 0 ? "read:" : "") + dialogId + ":" + id;
        }
    }

    static final int MAX_TRACKED_PRIVATE_SENDS = 512;

    private static final class PrivateReadProgress {
        final LinkedHashSet<Integer> localIds = new LinkedHashSet<>();
        final NavigableSet<Integer> confirmedIds = new TreeSet<>();
        int readMax;
        long observedAt;
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
    private boolean enabled, keywordsEnabled;
    private List<WatchedPerson> watched = new ArrayList<>();
    private final Map<Long, PrivateReadProgress> privateReads = new HashMap<>();
    private final Set<Long> queuedPrivateReads = new HashSet<>();
    private final Map<Long, Long> statusBaseline = new HashMap<>();
    private Map<Long, Long> lastOnline = new HashMap<>();
    private final Set<Long> pendingOnline = new HashSet<>();
    private final Set<Runnable> presenceTasks = new HashSet<>();
    private long presenceVersion;
    private int account = -1;
    private long userId, since, version;

    public KeywordReminderEngine(Store store, Accounts accounts, Events events, Tasks tasks, Publisher publisher, Matcher matcher) {
        this.store = store; this.accounts = accounts; this.events = events;
        this.tasks = tasks; this.publisher = publisher; this.matcher = matcher;
        keywords = new ArrayList<>(store.keywords());
        keywordsEnabled = store.keywordsEnabled();
    }

    public boolean isEnabled() { return enabled; }
    public int account() { return account; }
    public long userId() { return userId; }
    public List<String> keywords() { return new ArrayList<>(keywords); }
    public int pendingCount() { return pending.size(); }
    public boolean keywordsEnabled() { return keywordsEnabled; }
    public List<WatchedPerson> watched(long owner) { return new ArrayList<>(store.watched(owner)); }
    public List<WatchedPerson> activeWatched() { return new ArrayList<>(watched); }
    public void setKeywordsEnabled(boolean value) {
        store.keywordsEnabled(value); keywordsEnabled = value; invalidateTasks();
        resumePrivateReads();
    }
    public void setWatched(long owner, List<WatchedPerson> values) {
        if (owner <= 0 || values.size() > MAX_WATCHED) throw new IllegalArgumentException("WATCHED_LIMIT");
        Set<Long> ids = new HashSet<>();
        for (WatchedPerson person : values) if (person == null || person.id == owner || !ids.add(person.id))
            throw new IllegalArgumentException("WATCHED_PERSON");
        store.watched(owner, values);
        if (owner == userId) {
            watched = new ArrayList<>(values); statusBaseline.clear(); invalidateTasks();
            lastOnline.keySet().retainAll(ids);
            if (enabled) {
                refreshPrivateReads();
                resumePrivateReads();
            }
        }
    }
    private void invalidateTasks() { version++; cancelPending(); }
    public void resetPresence() {
        if (!enabled) return;
        statusBaseline.clear(); presenceVersion++;
        for (Runnable action : new ArrayList<>(presenceTasks)) { tasks.cancel(action); pending.remove(action); }
        presenceTasks.clear(); pendingOnline.clear();
        pendingKeys.removeIf(key -> key.startsWith("online:"));
    }

    /** 0 = unavailable, -1 = visible offline, positive = online expiration (server seconds).
     * Initial/reconnect observations are baselines, never notifications. No polling or timers. */
    public void onStatus(int sourceAccount, long personId, long expires, String name) {
        if (!enabled || sourceAccount != account) return;
        if (!accounts.valid(account, userId)) { disable(); return; }
        WatchedPerson person = null;
        for (WatchedPerson candidate : watched) if (candidate.id == personId && candidate.online) { person = candidate; break; }
        if (person == null) return;
        if (expires == 0) { statusBaseline.remove(personId); return; }
        Long previous = statusBaseline.put(personId, expires);
        long now = accounts.nowSeconds(account);
        if (previous == null || previous > now || expires <= now || pendingOnline.contains(personId)) return;
        long last = lastOnline.getOrDefault(personId, 0L);
        if (last != 0 && now - last < ONLINE_COOLDOWN_SECONDS) return;
        pendingOnline.add(personId);
        String displayName = name == null ? person.name : name;
        enqueue(new Message(personId, 0, expires, displayName, "", 0, personId, 0, true), displayName);
    }

    private void refreshPrivateReads() {
        Set<Long> active = new HashSet<>();
        for (WatchedPerson person : watched) {
            if (!person.privateRead) continue;
            active.add(person.id);
            privateReads.computeIfAbsent(person.id, id -> {
                PrivateReadProgress progress = new PrivateReadProgress();
                progress.confirmedIds.addAll(store.pendingPrivateReads(userId, id));
                return progress;
            });
        }
        privateReads.keySet().retainAll(active);
    }

    void onPrivateSend(long dialogId, int localId, long date) {
        PrivateReadProgress progress = privateReads.get(dialogId);
        if (progress == null || localId >= 0 || date < since) return;
        progress.localIds.add(localId);
        if (progress.localIds.size() > MAX_TRACKED_PRIVATE_SENDS) {
            progress.localIds.remove(progress.localIds.iterator().next());
        }
    }

    void onPrivateSendConfirmed(long dialogId, int localId, int serverId) {
        PrivateReadProgress progress = privateReads.get(dialogId);
        if (progress == null || serverId <= 0 || !progress.localIds.remove(localId) || !progress.confirmedIds.add(serverId)) return;
        if (progress.confirmedIds.size() > MAX_TRACKED_PRIVATE_SENDS) progress.confirmedIds.pollFirst();
        store.rememberPrivateReads(userId, dialogId, new ArrayList<>(progress.confirmedIds));
        queuePrivateRead(dialogId, progress);
    }

    void onPrivateRead(long dialogId, int readMax) {
        PrivateReadProgress progress = privateReads.get(dialogId);
        if (progress == null) return;
        if (readMax > progress.readMax) {
            progress.readMax = readMax;
            progress.observedAt = accounts.nowSeconds(account);
        }
        queuePrivateRead(dialogId, progress);
    }

    private void resumePrivateReads() {
        privateReads.forEach(this::queuePrivateRead);
    }

    private void queuePrivateRead(long dialogId, PrivateReadProgress progress) {
        if (!enabled || progress.confirmedIds.isEmpty() || progress.confirmedIds.first() > progress.readMax
                || !queuedPrivateReads.add(dialogId)) return;
        long token = version;
        Runnable action = new Runnable() {
            @Override public void run() {
                if (!enabled || token != version) return;
                pending.remove(this);
                queuedPrivateReads.remove(dialogId);
                if (!accounts.valid(account, userId)) { disable(); return; }
                try {
                    publishPrivateRead(dialogId, progress);
                } catch (RuntimeException failure) {
                    stopRuntime();
                    try { store.enabled(false); } catch (RuntimeException ignored) { }
                }
            }
        };
        pending.add(action);
        tasks.post(action);
    }

    private void publishPrivateRead(long dialogId, PrivateReadProgress progress) {
        NavigableSet<Integer> covered = progress.confirmedIds.headSet(progress.readMax, true);
        if (covered.isEmpty()) return;
        for (WatchedPerson person : watched) {
            if (person.id != dialogId) continue;
            int lastId = covered.last();
            int count = covered.size();
            List<Integer> remaining = new ArrayList<>(progress.confirmedIds.tailSet(lastId, false));
            Message message = new Message(dialogId, lastId, progress.observedAt, person.name, "", 0,
                    dialogId, WatchedPerson.PRIVATE, false, count);
            if (seen.contains(message.key())) {
                store.rememberPrivateReads(userId, dialogId, remaining);
                covered.clear();
                return;
            }
            WatchedPersonRecord record = new WatchedPersonRecord(person.name, person.name, message.date,
                    dialogId, lastId, 0, WatchedPerson.PRIVATE, count);
            addSeen(message.key());
            // Consume the tracked IDs in the same commit as the history entry.
            store.recordPrivateRead(userId, record, seen, remaining);
            covered.clear();
            publisher.publishWatched(message, record, account, userId, store.bannerEnabled(userId));
            return;
        }
    }

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
        watched = new ArrayList<>(store.watched(userId));
        refreshPrivateReads();
        lastOnline = new HashMap<>(store.onlineAlerts(userId));
        Set<Long> ids = new HashSet<>();
        for (WatchedPerson person : watched) ids.add(person.id);
        lastOnline.keySet().retainAll(ids);
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
        statusBaseline.clear();
        watched.clear();
        privateReads.clear();
        lastOnline.clear();
    }

    private void cancelPending() {
        for (Runnable action : new ArrayList<>(pending)) tasks.cancel(action);
        pending.clear();
        pendingKeys.clear();
        queuedPrivateReads.clear();
        pendingOnline.clear();
        presenceTasks.clear();
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
        resumePrivateReads();
    }

    /** Disabled gate precedes matching, de-dup access and scheduling. */
    public void onMessage(int sourceAccount, Message message) {
        if (!enabled) return;
        if (sourceAccount != account || message == null || message.id == 0 || message.dialogId == 0) return;
        if (!accounts.valid(account, userId)) { disable(); return; }
        if (message.online || message.senderId == userId || message.date < since) return;
        String watchedName = null;
        for (WatchedPerson person : watched) {
            if (person.id == message.senderId && (person.scopes & message.scope) != 0) {
                watchedName = person.name;
                break;
            }
        }
        if (watchedName == null && (!keywordsEnabled || keywords.isEmpty() || message.text.isEmpty()
                || !matcher.matches(message.text, keywords))) return;
        enqueue(message, watchedName);
    }

    private void enqueue(Message message, String watchedName) {
        String key = message.key();
        if (seen.contains(key) || !pendingKeys.add(key)) {
            if (message.online) pendingOnline.remove(message.senderId);
            return;
        }
        long token = version;
        long presenceToken = presenceVersion;
        Runnable action = new Runnable() {
            @Override public void run() {
                // A cancelled callback may already have been dequeued. Never remove a new epoch's key.
                if (!enabled || token != version || (message.online && presenceToken != presenceVersion)) return;
                pending.remove(this);
                presenceTasks.remove(this);
                pendingKeys.remove(key);
                if (message.online) pendingOnline.remove(message.senderId);
                if (!accounts.valid(account, userId)) { disable(); return; }
                if (seen.contains(key)) return;
                try {
                    if (watchedName != null) {
                        long date = message.online ? accounts.nowSeconds(account) : message.date;
                        WatchedPersonRecord record = new WatchedPersonRecord(watchedName, message.title, date,
                                message.dialogId, message.id, message.topicId, message.scope, message.privateReadCount);
                        addSeen(key);
                        if (message.online) lastOnline.put(message.senderId, date);
                        // Commit the record and its de-duplication state together, even when alerts are muted.
                        store.recordWatched(userId, record, seen, message.online ? lastOnline : null);
                        if (enabled && token == version) {
                            publisher.publishWatched(message, record, account, userId, store.bannerEnabled(userId));
                        }
                    } else if (publisher.publish(message, account, userId) && enabled && token == version) {
                        addSeen(key);
                        store.remember(userId, seen);
                    }
                } catch (RuntimeException failure) {
                    stopRuntime();
                    try { store.enabled(false); } catch (RuntimeException ignored) { }
                }
            }
        };
        pending.add(action);
        if (message.online) presenceTasks.add(action);
        long delay = watchedName == null ? 0 : publisher.watchedMessageDelayMillis(message, account, userId);
        tasks.post(action, delay);
    }

    private void addSeen(String key) {
        seen.add(key);
        while (seen.size() > MAX_SEEN) seen.remove(seen.iterator().next());
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
