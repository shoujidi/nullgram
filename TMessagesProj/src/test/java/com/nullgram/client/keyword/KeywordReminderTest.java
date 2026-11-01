package com.nullgram.client.keyword;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UiTestEnvironment;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.TextCheckCell;
import com.nullgram.client.repeat.RepeatTask;
import com.nullgram.client.repeat.RepeatTaskManager;
import com.nullgram.client.repeat.RepeatTaskState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Real engine, local preferences, NotificationCenter, Handler and Android notification publishing. */
@RunWith(RobolectricTestRunner.class)
@Config(application = UiTestEnvironment.App.class, sdk = {28, 36},
        shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class KeywordReminderTest {
    private Context context;
    private SharedPreferences preferences;
    private NotificationManager notifications;
    private KeywordReminderController controller;
    private final Sessions sessions = new Sessions();
    private final CapturedTasks tasks = new CapturedTasks();
    private int matches, seenWrites;
    private SharedPreferences.OnSharedPreferenceChangeListener writes;
    static final int NOW = 1700000000;

    static final class Sessions implements KeywordReminderController.Accounts {
        long primary = 500;
        long topic;
        public long topicId(int account, MessageObject message) { return topic; }
        public long userId(int account) { return account == 0 ? primary : account == 1 ? 600 : 0; }
        public boolean valid(int account, long id) { return id != 0 && userId(account) == id; }
        public long nowSeconds(int account) { return NOW; }
        public String title(int account, long dialog, MessageObject message) { return "Chat " + dialog; }
    }
    static final class CapturedTasks implements KeywordReminderEngine.Tasks {
        final Handler handler = new Handler(Looper.getMainLooper());
        final List<Runnable> submitted = new ArrayList<>();
        int cancelled;
        public void post(Runnable action) { submitted.add(action); handler.post(action); }
        public void cancel(Runnable action) { cancelled++; handler.removeCallbacks(action); }
    }
    private KeywordReminderController create() {
        return new KeywordReminderController(new KeywordReminderStore(context), sessions, tasks,
                new KeywordNotificationPublisher(context), (text, words) -> { matches++; return KeywordReminderEngine.contains(text, words); });
    }
    @Before public void setup() {
        context = ApplicationLoader.applicationContext;
        shadowOf((android.app.Application) context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        preferences = context.getSharedPreferences(KeywordReminderStore.PREFERENCES, Context.MODE_PRIVATE);
        assertTrue(preferences.edit().clear().commit());
        notifications = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notifications.cancelAll();
        controller = create();
        controller.engine().setKeywords(Arrays.asList("Hello", "中奖"));
        writes = (prefs, key) -> { if ("seen".equals(key)) seenWrites++; };
        preferences.registerOnSharedPreferenceChangeListener(writes);
    }
    @After public void cleanup() {
        controller.engine().shutdown();
        preferences.unregisterOnSharedPreferenceChangeListener(writes);
        tasks.handler.removeCallbacksAndMessages(null);
    }
    private void enable() { assertTrue(controller.enableForAccount(0)); }
    private void drain() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50)); }
    private MessageObject message(long dialog, int id, String text) {
        TLRPC.TL_message owner = new TLRPC.TL_message();
        owner.id = id; owner.date = NOW; owner.dialog_id = dialog; owner.message = text;
        owner.from_id = new TLRPC.TL_peerUser(); owner.from_id.user_id = 42;
        if (dialog < 0) { owner.peer_id = new TLRPC.TL_peerChannel(); owner.peer_id.channel_id = -dialog; }
        else { owner.peer_id = new TLRPC.TL_peerUser(); owner.peer_id.user_id = dialog; }
        return new MessageObject(0, owner, text, "Sender", "sender", false, dialog < 0, false, false);
    }
    private void deliver(int account, MessageObject message) {
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.didReceiveNewMessages,
                message.getDialogId(), new ArrayList<>(Collections.singletonList(message)), false, 0);
    }
    private void send(long dialog, int id, String text) { deliver(0, message(dialog, id, text)); }
    private int count() { return notifications.getActiveNotifications().length; }

    @Test public void T01_defaultOffSkipsAllWork() {
        assertFalse(controller.engine().isEnabled()); assertFalse(preferences.getBoolean("enabled", false));
        send(42, 1, "Hello");
        controller.engine().onMessage(0, new KeywordReminderEngine.Message(42, 2, NOW, "Sender", "中奖"));
        drain(); assertEquals(0, count()); assertEquals(0, matches); assertEquals(0, seenWrites);
        assertTrue(tasks.submitted.isEmpty());
    }
    @Test public void T03_F14_repeatManagerAndStopRemainOperationalWhileOff() {
        Handler handler = new Handler(Looper.getMainLooper());
        RepeatTaskManager repeat = new RepeatTaskManager(0, new RepeatTaskManager.Scheduler() {
            public long now() { return android.os.SystemClock.elapsedRealtime(); }
            public void post(Runnable action, long delay) { handler.postDelayed(action, delay); }
            public void cancel(Runnable action) { handler.removeCallbacks(action); }
        });
        int[] sends = {0};
        assertTrue(repeat.startTask(42, RepeatTask.Type.NEW_MESSAGE, 5, 10, new RepeatTaskManager.Sender() {
            public String validate() { return null; }
            public List<Integer> send() { sends[0]++; return Collections.singletonList(-sends[0]); }
        }));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));
        assertTrue(sends[0] > 0);
        repeat.cancelTask(); int stopped = sends[0]; drain();
        assertEquals(stopped, sends[0]); assertEquals(RepeatTaskState.CANCELLED, repeat.getTaskState());
        assertEquals(0, matches); assertEquals(0, count());
    }
    @Test public void T04_F01_privateCaseInsensitiveReminder() {
        enable(); send(42, 1, "say hELLo world"); drain();
        assertEquals(1, count());
        Notification notice = notifications.getActiveNotifications()[0].getNotification();
        assertEquals("Chat 42", notice.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals("say hELLo world", notice.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        assertEquals(KeywordNotificationPublisher.CHANNEL, notice.getChannelId());
    }
    @Test public void F02_groupReminder() {
        enable(); MessageObject message = message(-200, 10, "中奖消息");
        message.messageOwner.peer_id = new TLRPC.TL_peerChat(); message.messageOwner.peer_id.chat_id = 200;
        deliver(0, message); drain(); assertEquals(1, count());
    }
    @Test public void F03_channelReminder() { enable(); send(-300, 10, "Hello channel"); drain(); assertEquals(1, count()); }
    @Test public void F04_mutedChatStillNotifiesWithoutChangingNativePreferences() {
        SharedPreferences nativePrefs = context.getSharedPreferences("Notifications", Context.MODE_PRIVATE);
        assertTrue(nativePrefs.edit().putInt("notify2_-200", 2).commit());
        enable(); MessageObject message = message(-200, 1, "Hello muted"); message.messageOwner.silent = true;
        deliver(0, message); drain(); assertEquals(1, count()); assertEquals(2, nativePrefs.getInt("notify2_-200", 0));
    }
    @Test public void F05_archivedDialogStillReceivesRealtimeEvents() {
        TLRPC.TL_dialog archived = new TLRPC.TL_dialog(); archived.id = -200; archived.folder_id = 1;
        enable(); send(archived.id, 1, "Hello archived"); drain(); assertEquals(1, count()); assertEquals(1, archived.folder_id);
    }
    @Test public void F06_clickIntentUsesOriginalAccountChatAndExactMessage() {
        enable(); send(-300, 91, "Hello channel"); drain();
        PendingIntent pending = notifications.getActiveNotifications()[0].getNotification().contentIntent;
        Intent route = shadowOf(pending).getSavedIntent();
        assertEquals("org.telegram.ui.LaunchActivity", route.getComponent().getClassName());
        assertEquals(0, route.getIntExtra("currentAccount", -1));
        assertEquals(300, route.getLongExtra("chatId", 0)); assertEquals(91, route.getIntExtra("messageId", 0));
        assertEquals(500, route.getLongExtra(KeywordNotificationRouter.USER_ID, 0));
        assertTrue(route.getAction().startsWith("com.tmessages.openchat"));
        KeywordNotificationRouter.sanitize(route, false, 1);
        assertEquals(Intent.ACTION_MAIN, route.getAction()); assertFalse(route.hasExtra("chatId")); assertFalse(route.hasExtra("messageId"));
        assertEquals(1, route.getIntExtra("currentAccount", -1));
    }
    @Test public void F07_duplicateEventsDoNotRepublish() {
        enable(); send(42, 1, "Hello"); send(42, 1, "Hello"); drain();
        assertEquals(1, tasks.submitted.size()); assertEquals(1, count()); assertEquals(1, seenWrites);
        send(42, 1, "Hello edited"); drain(); assertEquals(1, tasks.submitted.size()); assertEquals(1, seenWrites);
    }
    @Test public void F06_forumTopicAndSecretChatKeepExactPositionWithoutChangingOrdinaryRoutes() {
        sessions.topic = 77; enable(); send(-300, 92, "Hello topic"); drain();
        Intent topic = shadowOf(notifications.getActiveNotifications()[0].getNotification().contentIntent).getSavedIntent();
        assertEquals(77, topic.getLongExtra("topicId", 0)); assertEquals(92, topic.getIntExtra("messageId", 0));
        Intent secret = KeywordNotificationRouter.intent(context, 0, 500,
                org.telegram.messenger.DialogObject.makeEncryptedDialogId(9), -19);
        assertEquals(9, secret.getIntExtra("encId", 0));
        android.os.Bundle args = new android.os.Bundle();
        KeywordNotificationRouter.addEncryptedMessagePosition(secret, args);
        assertEquals(-19, args.getInt("message_id"));
        Intent ordinary = new Intent().putExtra("encId", 9).putExtra("messageId", -19);
        android.os.Bundle nativeArgs = new android.os.Bundle();
        KeywordNotificationRouter.addEncryptedMessagePosition(ordinary, nativeArgs);
        assertFalse(nativeArgs.containsKey("message_id"));
        KeywordNotificationRouter.sanitize(topic, false, 0);
        assertFalse(topic.hasExtra("topicId")); assertFalse(topic.hasExtra("messageId"));
    }
    @Test public void F08_multipleKeywordsProduceOneNotification() {
        enable(); send(42, 1, "Hello 中奖了"); drain(); assertEquals(1, count()); assertEquals(1, seenWrites);
    }
    @Test public void F09_identicalTextDifferentMessagesAreDistinct() {
        enable(); send(42, 1, "Hello"); send(42, 2, "Hello"); drain(); assertEquals(2, count()); assertEquals(2, seenWrites);
    }
    @Test public void T05_F10_disableCancelsUnpublishedJobsAndLeavesVisibleNotifications() {
        enable(); send(42, 1, "Hello"); drain(); assertEquals(1, count());
        send(42, 2, "Hello"); assertEquals(1, controller.engine().pendingCount());
        controller.engine().disable(); send(42, 3, "Hello"); drain();
        assertEquals(1, count()); assertEquals(1, seenWrites); assertEquals(0, controller.engine().pendingCount());
        assertTrue(tasks.cancelled > 0);
    }
    @Test public void T06_reenableDoesNotDuplicateListenersOrRemoveSharedObservers() {
        int[] originalEvents = {0};
        NotificationCenter.NotificationCenterDelegate original = (id, account, args) -> originalEvents[0]++;
        NotificationCenter center = NotificationCenter.getInstance(0);
        center.addObserver(original, NotificationCenter.didReceiveNewMessages);
        try {
            enable(); enable(); controller.engine().disable(); enable();
            send(42, 1, "Hello"); drain(); assertEquals(1, count()); assertEquals(1, originalEvents[0]);
            controller.engine().disable(); send(42, 2, "Hello"); drain(); assertEquals(2, originalEvents[0]); assertEquals(1, count());
        } finally { center.removeObserver(original, NotificationCenter.didReceiveNewMessages); }
    }
    @Test public void T07_rapidSwitchesDoNotCrashOrNotifyFromOldEpochs() {
        for (int i = 0; i < 200; i++) { enable(); send(42, i + 1, "Hello"); controller.engine().disable(); }
        drain(); assertEquals(0, count()); assertEquals(0, seenWrites);
        enable(); send(42, 999, "Hello"); drain(); assertEquals(1, count());
    }
    @Test public void T08_disabledRestartRemainsDisabled() {
        enable(); controller.engine().disable(); controller.engine().shutdown();
        controller = create(); controller.engine().restore(); send(42, 1, "Hello"); drain();
        assertFalse(controller.engine().isEnabled()); assertEquals(0, count());
    }
    @Test public void T09_F11_enabledRestartRestoresBindingAndKeywords() {
        enable(); controller.engine().shutdown(); controller = create(); controller.engine().restore();
        assertTrue(controller.engine().isEnabled()); assertEquals(500, controller.engine().userId());
        assertEquals(Arrays.asList("Hello", "中奖"), controller.engine().keywords());
        send(42, 1, "Hello"); drain(); assertEquals(1, count());
    }
    @Test public void T10_disablingRetainsSavedKeywords() {
        enable(); controller.engine().disable(); assertEquals(Arrays.asList("Hello", "中奖"), new KeywordReminderStore(context).keywords());
    }
    @Test public void T11_disabledDirectAndNativeEntrypointsDoNotMatchOrWriteDedup() {
        enable(); controller.engine().disable(); int before = matches;
        for (int i = 0; i < 10; i++) {
            send(42, i + 1, "Hello");
            controller.engine().onMessage(0, new KeywordReminderEngine.Message(42, i + 1, NOW, "Sender", "Hello"));
        }
        drain(); assertEquals(before, matches); assertEquals(0, seenWrites); assertTrue(tasks.submitted.isEmpty());
    }
    @Test public void T12_alreadyDequeuedOldJobCannotPublishAfterDisableAndReenable() {
        enable(); send(42, 1, "Hello"); Runnable stale = tasks.submitted.get(0);
        controller.engine().disable(); stale.run(); assertEquals(0, count());
        enable(); send(42, 1, "Hello"); stale.run(); assertEquals(0, count());
        assertEquals(1, controller.engine().pendingCount()); drain(); assertEquals(1, count()); assertEquals(1, seenWrites);
    }
    @Test public void F12_restartDedupPersistsAcrossNewStoreAndController() {
        enable(); send(42, 1, "Hello"); drain(); assertEquals(1, seenWrites);
        controller.engine().shutdown(); notifications.cancelAll(); controller = create(); controller.engine().restore();
        send(42, 1, "Hello"); drain(); assertEquals(0, count()); assertEquals(1, seenWrites);
    }
    @Test public void onlyOneBoundAccountAndNoOwnMessagesOrHistory() {
        enable(); deliver(1, message(42, 1, "Hello"));
        MessageObject own = message(42, 2, "Hello"); own.messageOwner.out = true; deliver(0, own);
        own = message(42, 3, "Hello"); own.messageOwner.from_id.user_id = 500; deliver(0, own);
        MessageObject old = message(42, 4, "Hello"); old.messageOwner.date = NOW - 1; deliver(0, old);
        MessageObject scheduled = message(42, 5, "Hello");
        NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.didReceiveNewMessages, 42L, new ArrayList<>(Collections.singletonList(scheduled)), true, 0);
        NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.messagesDidLoad, 42L, new ArrayList<>(Collections.singletonList(message(42, 6, "Hello"))));
        drain(); assertEquals(0, count()); assertEquals(0, matches); assertEquals(0, seenWrites);
    }
    @Test public void logoutDisarmsAndSlotReuseDoesNotRestoreOldUserMonitoring() {
        enable(); send(42, 1, "Hello");
        NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.appDidLogout);
        sessions.primary = 700; drain(); assertFalse(controller.engine().isEnabled()); assertFalse(preferences.getBoolean("enabled", true)); assertEquals(0, count());
        controller.engine().restore(); assertFalse(controller.engine().isEnabled());
    }
    @Test public void explicitRebindStopsOldAccount() {
        enable(); send(42, 1, "Hello"); assertTrue(controller.enableForAccount(1));
        send(42, 2, "Hello"); deliver(1, message(42, 3, "Hello")); drain(); assertEquals(1, count());
        assertTrue(notifications.getActiveNotifications()[0].getTag().startsWith(KeywordNotificationPublisher.TAG_PREFIX + "600:"));
    }
    @Test public void topSwitchIsIndependentAndDefaultsOff() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        KeywordSettingsView view = new KeywordSettingsView(activity, null, controller, 0);
        activity.setContentView(view);
        assertTrue(view.getChildAt(0) instanceof TextCheckCell);
        TextCheckCell toggle = (TextCheckCell) view.getChildAt(0); assertFalse(toggle.isChecked());
        toggle.performClick(); assertTrue(controller.engine().isEnabled());
        ((TextCheckCell) view.getChildAt(0)).performClick(); assertFalse(controller.engine().isEnabled());
        assertEquals(Arrays.asList("Hello", "中奖"), controller.engine().keywords());
        activity.finish();
    }
    @Test public void deletingKeywordInvalidatesAlreadyQueuedMatch() {
        enable(); send(42, 1, "Hello"); controller.engine().setKeywords(Collections.singletonList("中奖")); drain();
        assertEquals(0, count()); send(42, 2, "Hello"); send(42, 3, "中奖"); drain(); assertEquals(1, count());
    }
    @Test public void deniedNotificationPermissionOrBlockedChannelDoesNotWriteDedup() {
        enable(); notifications.createNotificationChannel(new android.app.NotificationChannel(KeywordNotificationPublisher.CHANNEL, "Keywords", NotificationManager.IMPORTANCE_NONE));
        send(42, 1, "Hello"); drain(); assertEquals(0, count()); assertEquals(0, seenWrites);
    }
    @Test public void keywordValidationUsesLiteralMatchingAndRejectsEmptyOrTooLong() {
        try { controller.engine().setKeywords(Collections.singletonList("  ")); fail("blank"); } catch (IllegalArgumentException expected) { }
        try { controller.engine().setKeywords(Collections.singletonList(String.join("", Collections.nCopies(129, "x")))); fail("too long"); } catch (IllegalArgumentException expected) { }
        controller.engine().setKeywords(Arrays.asList(" Hello ", "hello", ".*"));
        assertEquals(Arrays.asList("Hello", ".*"), controller.engine().keywords());
        enable(); send(42, 1, "not any text"); send(42, 2, "literal .* example"); drain(); assertEquals(1, count());
    }
    @Test public void dedupStorageRemainsBounded() {
        KeywordReminderStore store = new KeywordReminderStore(context);
        List<Runnable> queue = new ArrayList<>();
        KeywordReminderEngine bounded = new KeywordReminderEngine(store, sessions, new KeywordReminderEngine.Events() {
            public void register(int account) { }
            public void unregister(int account) { }
        }, new KeywordReminderEngine.Tasks() {
            public void post(Runnable action) { queue.add(action); }
            public void cancel(Runnable action) { queue.remove(action); }
        }, (message, account, user) -> true, KeywordReminderEngine::contains);
        assertTrue(bounded.enable(0, 500));
        for (int i = 1; i <= KeywordReminderEngine.MAX_SEEN + 5; i++) {
            bounded.onMessage(0, new KeywordReminderEngine.Message(42, i, NOW, "Sender", "Hello"));
            queue.remove(0).run();
        }
        assertEquals(KeywordReminderEngine.MAX_SEEN, new KeywordReminderStore(context).seen(500).size());
        bounded.shutdown();
    }
}
