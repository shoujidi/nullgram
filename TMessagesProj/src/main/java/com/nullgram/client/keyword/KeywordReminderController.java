// SPDX-License-Identifier: GPL-3.0-only
// Nullgram modification, 2026-11-01.
package com.nullgram.client.keyword;

import top.nkbe.niagram.helpers.LocalNameHelper;

import android.content.Context;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import java.util.List;

/** Owns only its own observers, for exactly one selected account. UI thread only. */
public final class KeywordReminderController implements NotificationCenter.NotificationCenterDelegate {
    public interface Accounts extends KeywordReminderEngine.Accounts {
        long userId(int account);
        String title(int account, long dialogId, MessageObject message);
        default long presence(int account, long userId) { return 0; }
        default String personName(int account, long userId) { return Long.toString(userId); }
        default boolean connected(int account) { return true; }
        default int scope(int account, MessageObject message) {
            if (message.getDialogId() > 0 || org.telegram.messenger.DialogObject.isEncryptedDialog(message.getDialogId())) return WatchedPerson.PRIVATE;
            return message.messageOwner.peer_id instanceof TLRPC.TL_peerChat
                    ? WatchedPerson.GROUP : WatchedPerson.CHANNEL;
        }
        default long topicId(int account, MessageObject message) { return 0; }
    }
    private static KeywordReminderController instance;
    private final KeywordReminderEngine engine;
    private final Accounts accounts;
    private int observedAccount = -1;
    private boolean presenceConnected;

    public static KeywordReminderController get() {
        if (instance == null) {
            Context context = ApplicationLoader.applicationContext;
            instance = new KeywordReminderController(new KeywordReminderStore(context), new NativeAccounts(),
                    new KeywordReminderEngine.Tasks() {
                        public void post(Runnable action, long delayMillis) { AndroidUtilities.runOnUIThread(action, delayMillis); }
                        public void cancel(Runnable action) { AndroidUtilities.cancelRunOnUIThread(action); }
                    }, new KeywordNotificationPublisher(context), KeywordReminderEngine::contains);
            instance.engine.restore();
        }
        return instance;
    }

    /** No observer, worker, polling or extra connection is created when the persisted switch is off. */
    public static void restoreIfEnabled(Context context) {
        if (!context.getSharedPreferences(KeywordReminderStore.PREFERENCES, Context.MODE_PRIVATE).getBoolean("enabled", false)) return;
        AndroidUtilities.runOnUIThread(() -> {
            try { get().engine.restore(); }
            catch (RuntimeException error) { FileLog.e("Nullgram keyword restore failed: " + error.getClass().getSimpleName()); }
        });
    }

    public KeywordReminderController(KeywordReminderEngine.Store store, Accounts accounts,
                                    KeywordReminderEngine.Tasks tasks, KeywordReminderEngine.Publisher publisher,
                                    KeywordReminderEngine.Matcher matcher) {
        this.accounts = accounts;
        engine = new KeywordReminderEngine(store, accounts, new KeywordReminderEngine.Events() {
            public void register(int account) {
                if (observedAccount == account) return;
                if (observedAccount != -1) unregister(observedAccount);
                NotificationCenter center = NotificationCenter.getInstance(account);
                center.addObserver(KeywordReminderController.this, NotificationCenter.didReceiveNewMessages);
                center.addObserver(KeywordReminderController.this, NotificationCenter.appDidLogout);
                center.addObserver(KeywordReminderController.this, NotificationCenter.updateInterfaces);
                center.addObserver(KeywordReminderController.this, NotificationCenter.didUpdateConnectionState);
                center.addObserver(KeywordReminderController.this, NotificationCenter.messageReceivedByServer);
                center.addObserver(KeywordReminderController.this, NotificationCenter.messagesRead);
                observedAccount = account;
                presenceConnected = accounts.connected(account);
            }
            public void unregister(int account) {
                NotificationCenter center = NotificationCenter.getInstance(account);
                center.removeObserver(KeywordReminderController.this, NotificationCenter.didReceiveNewMessages);
                center.removeObserver(KeywordReminderController.this, NotificationCenter.appDidLogout);
                center.removeObserver(KeywordReminderController.this, NotificationCenter.updateInterfaces);
                center.removeObserver(KeywordReminderController.this, NotificationCenter.didUpdateConnectionState);
                center.removeObserver(KeywordReminderController.this, NotificationCenter.messageReceivedByServer);
                center.removeObserver(KeywordReminderController.this, NotificationCenter.messagesRead);
                observedAccount = -1;
            }
        }, tasks, publisher, matcher);
    }

    public KeywordReminderEngine engine() { return engine; }
    public boolean enableForAccount(int account) { return engine.enable(account, accounts.userId(account)); }
    public long accountUserId(int account) { return accounts.userId(account); }

    /** UI-thread entry for dialog snapshots received from Telegram. */
    public static void onServerDialogsLoaded(int account, List<TLRPC.Dialog> dialogs) {
        if (instance != null) instance.acceptServerDialogs(account, dialogs);
    }

    void acceptServerDialogs(int account, List<TLRPC.Dialog> dialogs) {
        if (!engine.isEnabled() || account != engine.account()) return;
        try {
            for (TLRPC.Dialog dialog : dialogs) {
                if (DialogObject.isUserDialog(dialog.id) && dialog.read_outbox_max_id > 0) {
                    engine.onPrivateRead(dialog.id, dialog.read_outbox_max_id);
                }
            }
        } catch (RuntimeException error) {
            disableAfterFailure(error);
        }
    }

    private void refreshPresence(int account) {
        for (WatchedPerson person : engine.activeWatched()) if (person.online)
            engine.onStatus(account, person.id, accounts.presence(account, person.id), accounts.personName(account, person.id));
    }

    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (!engine.isEnabled() || account != engine.account()) return;
        try {
            if (id == NotificationCenter.appDidLogout) { engine.disable(); return; }
            if (id == NotificationCenter.didUpdateConnectionState) {
                boolean connected = accounts.connected(account);
                if (!connected || !presenceConnected) engine.resetPresence();
                presenceConnected = connected;
                return;
            }
            if (id == NotificationCenter.updateInterfaces) {
                if (presenceConnected && args.length > 0 && args[0] instanceof Integer
                        && (((Integer) args[0]) & MessagesController.UPDATE_MASK_STATUS) != 0) refreshPresence(account);
                return;
            }
            if (id == NotificationCenter.messagesRead) {
                // Argument zero contains inbox progress from this account or its other devices.
                if (args.length > 1 && args[1] instanceof LongSparseIntArray outbox) {
                    for (int i = 0; i < outbox.size(); i++) {
                        long dialogId = outbox.keyAt(i);
                        if (DialogObject.isUserDialog(dialogId)) engine.onPrivateRead(dialogId, outbox.valueAt(i));
                    }
                }
                return;
            }
            if (id == NotificationCenter.messageReceivedByServer) {
                if (args.length > 6 && Boolean.FALSE.equals(args[6]) && args[0] instanceof Integer localId
                        && args[1] instanceof Integer serverId && args[3] instanceof Long dialogId
                        && DialogObject.isUserDialog(dialogId)) {
                    engine.onPrivateSendConfirmed(dialogId, localId, serverId);
                }
                return;
            }
            if (id != NotificationCenter.didReceiveNewMessages || args.length < 2 || !(args[1] instanceof List)) return;
            if (args.length > 2 && Boolean.TRUE.equals(args[2])) return;
            if (args.length > 3 && args[3] instanceof Number && ((Number) args[3]).intValue() != 0) return;
            for (Object item : (List<?>) args[1]) {
                if (!(item instanceof MessageObject)) continue;
                MessageObject message = (MessageObject) item;
                TLRPC.Message owner = message.messageOwner;
                if (owner == null || owner instanceof TLRPC.TL_messageService) continue;
                long dialogId = message.getDialogId();
                if (owner.out) {
                    if (owner.peer_id instanceof TLRPC.TL_peerUser && DialogObject.isUserDialog(dialogId)) {
                        engine.onPrivateSend(dialogId, owner.id, owner.date);
                    }
                    continue;
                }
                if (owner.from_id != null && owner.from_id.user_id == engine.userId()) continue;
                // No mute/archive lookup: those are exclusively the original notification system's rules.
                engine.onMessage(account, new KeywordReminderEngine.Message(dialogId, owner.id, owner.date,
                        accounts.title(account, dialogId, message), owner.message, accounts.topicId(account, message),
                        owner.from_id == null ? 0 : owner.from_id.user_id, accounts.scope(account, message), false));
            }
        } catch (RuntimeException error) {
            disableAfterFailure(error);
        }
    }

    private void disableAfterFailure(RuntimeException error) {
        // Isolate the reminder module from Telegram's normal synchronization and observers.
        try { engine.disable(); } catch (RuntimeException ignored) { }
        FileLog.e("Nullgram keyword event failed: " + error.getClass().getSimpleName());
    }

    private static final class NativeAccounts implements Accounts {
        public int scope(int account, MessageObject message) {
            if (message.getDialogId() > 0 || org.telegram.messenger.DialogObject.isEncryptedDialog(message.getDialogId())) return WatchedPerson.PRIVATE;
            return message.messageOwner.peer_id instanceof TLRPC.TL_peerChat || message.isSupergroup() ? WatchedPerson.GROUP : WatchedPerson.CHANNEL;
        }
        public long topicId(int account, MessageObject message) {
            return MessageObject.getTopicId(account, message.messageOwner, MessagesController.getInstance(account).isForum(message));
        }
        public long userId(int account) {
            return account >= 0 && account < UserConfig.MAX_ACCOUNT_COUNT ? UserConfig.getInstance(account).getClientUserId() : 0;
        }
        public boolean valid(int account, long userId) {
            return userId != 0 && account >= 0 && account < UserConfig.MAX_ACCOUNT_COUNT
                    && UserConfig.getInstance(account).isClientActivated() && userId(account) == userId;
        }
        public long presence(int account, long userId) {
            TLRPC.User user = MessagesController.getInstance(account).getUser(userId);
            if (user == null || user.bot || user.deleted || user.status == null) return 0;
            if (user.status instanceof TLRPC.TL_userStatusOnline) return user.status.expires;
            if (user.status instanceof TLRPC.TL_userStatusOffline) return -1;
            return 0; // recently/last week/hidden is unknown, not evidence of going offline.
        }
        public String personName(int account, long userId) {
            TLRPC.User user = MessagesController.getInstance(account).getUser(userId);
            return user == null ? Long.toString(userId) : LocalNameHelper.name(account, user);
        }
        public boolean connected(int account) {
            return ConnectionsManager.getInstance(account).getConnectionState() == ConnectionsManager.ConnectionStateConnected;
        }
        public long nowSeconds(int account) { return ConnectionsManager.getInstance(account).getCurrentTime(); }
        public String title(int account, long dialogId, MessageObject message) {
            MessagesController messages = MessagesController.getInstance(account);
            if (dialogId < 0) {
                TLRPC.Chat chat = messages.getChat(-dialogId);
                if (chat != null && chat.title != null) return LocalNameHelper.name(account, chat);
            } else {
                TLRPC.User user = messages.getUser(dialogId);
                if (user != null) return LocalNameHelper.name(account, user);
            }
            return "Nullgram 路 " + dialogId;
        }
    }
}
