// SPDX-License-Identifier: GPL-3.0-only
// Nullgram modification, 2026-11-01.
package com.nullgram.client.keyword;

import android.content.Context;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import java.util.List;

/** Owns only its own two observers, for exactly one selected account. UI thread only. */
public final class KeywordReminderController implements NotificationCenter.NotificationCenterDelegate {
    public interface Accounts extends KeywordReminderEngine.Accounts {
        long userId(int account);
        String title(int account, long dialogId, MessageObject message);
        default long topicId(int account, MessageObject message) { return 0; }
    }
    private static KeywordReminderController instance;
    private final KeywordReminderEngine engine;
    private final Accounts accounts;
    private int observedAccount = -1;

    public static KeywordReminderController get() {
        if (instance == null) {
            Context context = ApplicationLoader.applicationContext;
            instance = new KeywordReminderController(new KeywordReminderStore(context), new NativeAccounts(),
                    new KeywordReminderEngine.Tasks() {
                        public void post(Runnable action) { AndroidUtilities.runOnUIThread(action); }
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
                observedAccount = account;
            }
            public void unregister(int account) {
                NotificationCenter center = NotificationCenter.getInstance(account);
                center.removeObserver(KeywordReminderController.this, NotificationCenter.didReceiveNewMessages);
                center.removeObserver(KeywordReminderController.this, NotificationCenter.appDidLogout);
                observedAccount = -1;
            }
        }, tasks, publisher, matcher);
    }

    public KeywordReminderEngine engine() { return engine; }
    public boolean enableForAccount(int account) { return engine.enable(account, accounts.userId(account)); }

    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (!engine.isEnabled() || account != engine.account()) return;
        try {
            if (id == NotificationCenter.appDidLogout) { engine.disable(); return; }
            if (id != NotificationCenter.didReceiveNewMessages || args.length < 2 || !(args[1] instanceof List)) return;
            if (args.length > 2 && Boolean.TRUE.equals(args[2])) return;
            if (args.length > 3 && args[3] instanceof Number && ((Number) args[3]).intValue() != 0) return;
            for (Object item : (List<?>) args[1]) {
                if (!(item instanceof MessageObject)) continue;
                MessageObject message = (MessageObject) item;
                TLRPC.Message owner = message.messageOwner;
                if (owner == null || owner.out || owner instanceof TLRPC.TL_messageService) continue;
                if (owner.from_id != null && owner.from_id.user_id == engine.userId()) continue;
                long dialogId = message.getDialogId();
                // No mute/archive lookup: those are exclusively the original notification system's rules.
                engine.onMessage(account, new KeywordReminderEngine.Message(dialogId, owner.id, owner.date,
                        accounts.title(account, dialogId, message), owner.message, accounts.topicId(account, message)));
            }
        } catch (RuntimeException error) {
            // A keyword-module failure must never interrupt delivery to Telegram's other observers.
            try { engine.disable(); } catch (RuntimeException ignored) { }
            FileLog.e("Nullgram keyword event failed: " + error.getClass().getSimpleName());
        }
    }

    private static final class NativeAccounts implements Accounts {
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
        public long nowSeconds(int account) { return ConnectionsManager.getInstance(account).getCurrentTime(); }
        public String title(int account, long dialogId, MessageObject message) {
            MessagesController messages = MessagesController.getInstance(account);
            if (dialogId < 0) {
                TLRPC.Chat chat = messages.getChat(-dialogId);
                if (chat != null && chat.title != null) return chat.title;
            } else {
                TLRPC.User user = messages.getUser(dialogId);
                if (user != null) return UserObject.getUserName(user);
            }
            return "Nullgram · " + dialogId;
        }
    }
}
