// SPDX-License-Identifier: GPL-3.0-only
package com.nullgram.client.keyword;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.util.ReflectionHelpers;
import org.telegram.messenger.*;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Production engine, preferences, account event bus, notification publishing and settings UI.
 * Only Telegram network/account cache access is replaced, not reminder behavior. */
@RunWith(RobolectricTestRunner.class)
@Config(application = UiTestEnvironment.App.class, sdk = {28, 36},
        shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class WatchedPersonReminderTest {
    private Context context;
    private SharedPreferences prefs;
    private NotificationManager notifications;
    private KeywordReminderController controller;
    private final Sessions accounts = new Sessions();
    private final Jobs jobs = new Jobs();
    private int matches, writes;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    static final long OWNER = 500, PERSON = 42;
    static final class Sessions implements KeywordReminderController.Accounts {
        long now = 1700000000;
        boolean connected = true;
        final Map<Long, Long> status = new HashMap<>();
        public long userId(int a) { return a == 0 ? OWNER : a == 1 ? 600 : 0; }
        public boolean valid(int a, long id) { return id > 0 && id == userId(a); }
        public long nowSeconds(int a) { return now; }
        public String title(int a, long d, MessageObject m) { return "Chat " + d; }
        public long presence(int a, long id) { return status.getOrDefault(id, 0L); }
        public String personName(int a, long id) { return "Person " + id; }
        public boolean connected(int a) { return connected; }
    }
    static final class Jobs implements KeywordReminderEngine.Tasks {
        final Handler handler = new Handler(Looper.getMainLooper());
        final List<Runnable> submitted = new ArrayList<>();
        public void post(Runnable r, long delayMillis) { submitted.add(r); handler.postDelayed(r, delayMillis); }
        public void cancel(Runnable r) { handler.removeCallbacks(r); }
    }
    private KeywordReminderController create() {
        return new KeywordReminderController(new KeywordReminderStore(context), accounts, jobs,
                new KeywordNotificationPublisher(context), (text, keywords) -> {
                    matches++; return KeywordReminderEngine.contains(text, keywords);
                });
    }
    @Before public void setup() {
        context = ApplicationLoader.applicationContext;
        shadowOf((android.app.Application)context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        prefs = context.getSharedPreferences(KeywordReminderStore.PREFERENCES, 0);
        assertTrue(prefs.edit().clear().commit());
        notifications = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        notifications.cancelAll();
        controller = create();
        listener = (p, key) -> { if (key.equals("seen") || key.startsWith("online_")) writes++; };
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }
    @After public void finish() {
        controller.engine().shutdown(); jobs.handler.removeCallbacksAndMessages(null);
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }
    private void watch(int scopes, boolean online) {
        controller.engine().setWatched(OWNER, Collections.singletonList(new WatchedPerson(PERSON, "Alice", scopes, online)));
    }
    private void enable() { assertTrue(controller.enableForAccount(0)); }
    private void drain() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50)); }
    private int count() { return notifications.getActiveNotifications().length; }
    private MessageObject message(long dialog, int id, int scope, long sender, String text) {
        TLRPC.TL_message m = new TLRPC.TL_message();
        m.id = id; m.date = (int)accounts.now; m.dialog_id = dialog; m.message = text;
        if (sender > 0) { m.from_id = new TLRPC.TL_peerUser(); m.from_id.user_id = sender; }
        else { m.from_id = new TLRPC.TL_peerChannel(); m.from_id.channel_id = 333; }
        if (scope == WatchedPerson.PRIVATE) { m.peer_id = new TLRPC.TL_peerUser(); m.peer_id.user_id = dialog; }
        else if (scope == WatchedPerson.GROUP) { m.peer_id = new TLRPC.TL_peerChat(); m.peer_id.chat_id = -dialog; }
        else { m.peer_id = new TLRPC.TL_peerChannel(); m.peer_id.channel_id = -dialog; }
        return new MessageObject(0, m, text, "Alice", "alice", false, scope == WatchedPerson.CHANNEL, false, false);
    }
    private void deliver(int account, MessageObject m) {
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.didReceiveNewMessages,
                m.getDialogId(), new ArrayList<>(Collections.singletonList(m)), false, 0);
    }
    private void send(long dialog, int id, int scope, long sender, String text) { deliver(0, message(dialog,id,scope,sender,text)); }
    private void watchPrivateReads(boolean enabled) {
        controller.engine().setWatched(OWNER, Collections.singletonList(new WatchedPerson(PERSON, "Alice", 0, false, enabled)));
    }
    private void sendOutgoing(int account, long dialog, int localId, int scope) {
        MessageObject outgoing = message(dialog, localId, scope, OWNER, "message body");
        outgoing.messageOwner.out = true;
        deliver(account, outgoing);
    }
    private void confirm(int account, long dialog, int localId, int serverId) {
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.messageReceivedByServer,
                localId, serverId, null, dialog, 0L, 0, false);
    }
    private void read(int account, long dialog, int maxId, boolean outbox) {
        LongSparseIntArray progress = new LongSparseIntArray();
        progress.put(dialog, maxId);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.messagesRead,
                outbox ? null : progress, outbox ? progress : null);
    }
    private void serverSnapshot(int account, long dialog, int readMax) {
        TLRPC.TL_dialog snapshot = new TLRPC.TL_dialog();
        snapshot.id = dialog;
        snapshot.read_outbox_max_id = readMax;
        controller.acceptServerDialogs(account, Collections.singletonList(snapshot));
    }
    private void status(long value) {
        accounts.status.put(PERSON, value);
        NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_STATUS);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
    }
    private void queueStatus(long value) { controller.engine().onStatus(0,PERSON,value,"Person 42"); }
    @Test public void defaultsAndLegacyMigrationPreserveKeywordBehavior() {
        assertFalse(controller.engine().isEnabled());
        assertTrue(controller.engine().keywordsEnabled());
        assertTrue(controller.engine().watched(OWNER).isEmpty());
        assertEquals("Reminders", context.getString(R.string.NullgramKeywordTitle));
        controller.engine().setKeywords(Collections.singletonList("hello"));
        enable(); send(PERSON,1,1,PERSON,"Hello"); drain(); assertEquals(1,count());
    }
    @Test public void historyRecordsWatchedEventsEvenWhenSystemNotificationsAreBlocked() {
        notifications.createNotificationChannel(new NotificationChannel(KeywordNotificationPublisher.CHANNEL,
                "Reminders", NotificationManager.IMPORTANCE_NONE));
        controller.engine().setKeywords(Collections.singletonList("match"));
        watch(WatchedPerson.ALL, true);
        enable();
        send(-20, 3, WatchedPerson.GROUP, PERSON, "match private body");
        send(-20, 3, WatchedPerson.GROUP, PERSON, "match private body");
        send(-20, 4, WatchedPerson.GROUP, 99, "match keyword only");
        drain();
        status(-1);
        status(accounts.now + 60);

        List<WatchedPersonRecord> records = new KeywordReminderStore(context).history(OWNER);
        assertEquals(2, records.size());
        assertEquals(0, count());
        assertEquals(0, records.get(0).messageId);
        assertEquals(accounts.now, records.get(0).date);
        assertEquals("Person 42", records.get(0).name);
        WatchedPersonRecord group = records.get(1);
        assertEquals("Alice", group.name);
        assertEquals("Chat -20", group.chatTitle);
        assertEquals(WatchedPerson.GROUP, group.scope);
        Intent destination = group.openIntent(context, 0, OWNER);
        assertEquals(20, destination.getLongExtra("chatId", 0));
        assertEquals(3, destination.getIntExtra("messageId", 0));
        assertFalse(prefs.getString(KeywordReminderStore.historyKey(OWNER), "").contains("private body"));
    }

    @Test public void clearingHistoryPreservesSettingsAndDuplicateProtectionAfterRestart() {
        watch(WatchedPerson.PRIVATE, false);
        enable();
        send(PERSON, 5, WatchedPerson.PRIVATE, PERSON, "hello");
        drain();
        controller.engine().shutdown();
        controller = create();
        controller.engine().restore();
        KeywordReminderStore store = new KeywordReminderStore(context);
        assertEquals(1, store.history(OWNER).size());
        store.clearHistory(OWNER);
        assertTrue(store.history(OWNER).isEmpty());
        assertEquals(1, store.watched(OWNER).size());
        send(PERSON, 5, WatchedPerson.PRIVATE, PERSON, "same event");
        drain();
        assertTrue(store.history(OWNER).isEmpty());
        send(PERSON, 6, WatchedPerson.PRIVATE, PERSON, "new event");
        drain();
        assertEquals(6, store.history(OWNER).get(0).messageId);
    }

    @Test public void historyIsBoundedNewestFirstAndScopedToTheAccount() {
        KeywordReminderStore store = new KeywordReminderStore(context);
        for (int id = 1; id <= KeywordReminderStore.HISTORY_LIMIT + 1; id++) {
            store.recordWatched(OWNER, new WatchedPersonRecord("Alice", "", accounts.now,
                    PERSON, id, 0, WatchedPerson.PRIVATE), Collections.emptySet(), null);
        }
        store.recordWatched(600, new WatchedPersonRecord("Bob", "", accounts.now,
                43, 1, 0, WatchedPerson.PRIVATE), Collections.emptySet(), null);
        List<WatchedPersonRecord> history = store.history(OWNER);
        assertEquals(KeywordReminderStore.HISTORY_LIMIT, history.size());
        assertEquals(KeywordReminderStore.HISTORY_LIMIT + 1, history.get(0).messageId);
        assertEquals(2, history.get(history.size() - 1).messageId);
        assertEquals("Bob", store.history(600).get(0).name);
        store.clearHistory(600);
        assertTrue(store.history(600).isEmpty());
        assertEquals(KeywordReminderStore.HISTORY_LIMIT, store.history(OWNER).size());
    }

    @Test public void enabledBannerFallsBackToSystemNotificationOutsideTheApp() {
        KeywordReminderStore store = new KeywordReminderStore(context);
        assertFalse(store.bannerEnabled(OWNER));
        store.bannerEnabled(OWNER, true);
        assertTrue(new KeywordReminderStore(context).bannerEnabled(OWNER));
        assertFalse(store.bannerEnabled(600));
        watch(WatchedPerson.PRIVATE, false);
        enable();
        send(PERSON, 7, WatchedPerson.PRIVATE, PERSON, "hello");
        drain();
        assertEquals(1, count());
        assertEquals(1, store.history(OWNER).size());
    }

    @Implements(LaunchActivity.class)
    public static class CurrentChat extends ShadowActivity {
        static BaseFragment top;
        @Implementation protected static BaseFragment getSafeLastFragment() { return top; }
    }

    @Implements(ChatActivity.class)
    public static class ChatWithoutAccountInitialization {
        @Implementation protected void __constructor__(Bundle args) { }
    }

    public static class FocusedWindow extends LaunchActivity {
        boolean focused = true;
        @Override public boolean hasWindowFocus() { return focused; }
    }

    @Test
    @Config(shadows = {CurrentChat.class, ChatWithoutAccountInitialization.class})
    public void watchedPrivateMessageChecksVisibilityAtPublicationAndStillRecordsOnce() {
        UserConfig userConfig = UserConfig.getInstance(0);
        TLRPC.User previousUser = userConfig.getCurrentUser();
        TLRPC.User owner = new TLRPC.TL_user();
        owner.id = OWNER;
        int previousAccount = UserConfig.selectedAccount;
        boolean previousActive = LaunchActivity.isActive;
        boolean previousPaused = ApplicationLoader.mainInterfacePaused;
        boolean previousScreenOn = ApplicationLoader.isScreenOn;
        LaunchActivity previousActivity = LaunchActivity.instance;
        FocusedWindow window = Robolectric.buildActivity(FocusedWindow.class).get();
        KeywordReminderStore store = new KeywordReminderStore(context);
        int[] visibleMessage = {0};
        try {
            // Supply the local identity without starting the Telegram account or its native network.
            ReflectionHelpers.setField(userConfig, "currentUser", owner);
            UserConfig.selectedAccount = 0;
            LaunchActivity.instance = window;
            LaunchActivity.isActive = true;
            ApplicationLoader.mainInterfacePaused = false;
            ApplicationLoader.isScreenOn = true;
            CurrentChat.top = new ChatActivity(new Bundle()) {
                @Override public Activity getParentActivity() { return window; }
                @Override public long getDialogId() { return PERSON; }
                @Override public boolean isNewMessageVisibleForReminder(int id) { return visibleMessage[0] == id; }
            };
            controller.engine().setKeywords(Collections.singletonList("keyword"));
            watch(WatchedPerson.PRIVATE, false);
            enable();

            send(PERSON, 1, WatchedPerson.PRIVATE, PERSON, "keyword");
            drain();
            assertEquals("Allow the chat to render before publishing", 0, store.history(OWNER).size());
            visibleMessage[0] = 1;
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
            assertEquals(0, count());
            assertEquals(1, store.history(OWNER).size());

            visibleMessage[0] = 0;
            send(PERSON, 2, WatchedPerson.PRIVATE, PERSON, "outside the viewport");
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
            assertEquals(1, count());

            visibleMessage[0] = 3;
            send(PERSON, 3, WatchedPerson.PRIVATE, PERSON, "visible before leaving");
            window.focused = false;
            send(PERSON, 1, WatchedPerson.PRIVATE, PERSON, "duplicate after leaving");
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
            assertEquals(2, count());
            assertEquals(3, store.history(OWNER).size());

            window.focused = true;
            visibleMessage[0] = 4;
            controller.engine().setWatched(OWNER, Collections.emptyList());
            send(PERSON, 4, WatchedPerson.PRIVATE, PERSON, "keyword only");
            drain();
            assertEquals(3, count());
            assertEquals(3, store.history(OWNER).size());
        } finally {
            CurrentChat.top = null;
            LaunchActivity.instance = previousActivity;
            LaunchActivity.isActive = previousActive;
            ApplicationLoader.mainInterfacePaused = previousPaused;
            ApplicationLoader.isScreenOn = previousScreenOn;
            UserConfig.selectedAccount = previousAccount;
            ReflectionHelpers.setField(userConfig, "currentUser", previousUser);
        }
    }

    @Test public void privateGroupChannelAndCommentsWithoutKeyword() {
        watch(WatchedPerson.ALL,false); enable();
        send(PERSON,1,1,PERSON,"private"); send(-20,1,2,PERSON,"muted group");
        send(-30,1,4,PERSON,"channel personal sender"); send(-40,1,2,PERSON,"channel comment");
        drain(); assertEquals(4,count()); assertEquals(0,matches);
    }
    @Test public void scopesAreIndependentAndCanAllBeOff() {
        watch(2,false); enable();
        send(PERSON,1,1,PERSON,"private"); send(-20,1,2,PERSON,"group"); send(-30,1,4,PERSON,"channel");
        drain(); assertEquals(1,count());
        watch(0,false); send(-20,2,2,PERSON,"off"); drain(); assertEquals(1,count());
    }
    @Test public void nonTextMessagesNotifyAndOpenExactOriginal() {
        watch(2,false); enable(); send(-20,91,2,PERSON,""); drain(); assertEquals(1,count());
        Notification n = notifications.getActiveNotifications()[0].getNotification();
        assertEquals(context.getString(R.string.NullgramReminderMedia), n.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        Intent intent = shadowOf(n.contentIntent).getSavedIntent();
        assertEquals(20,intent.getLongExtra("chatId",0)); assertEquals(91,intent.getIntExtra("messageId",0));
    }
    @Test public void anonymousSignatureAndForwardedOriginDoNotImpersonatePerson() {
        watch(7,false); enable();
        MessageObject anonymous = message(-20,1,2,0,"Alice"); anonymous.messageOwner.post_author="Alice";
        deliver(0,anonymous);
        MessageObject forwarded = message(-20,2,2,99,"forwarded Alice");
        forwarded.messageOwner.fwd_from = new TLRPC.TL_messageFwdHeader();
        forwarded.messageOwner.fwd_from.from_id = new TLRPC.TL_peerUser();
        forwarded.messageOwner.fwd_from.from_id.user_id = PERSON;
        deliver(0,forwarded); drain(); assertEquals(0,count());
    }
    @Test public void mutedArchivedChatsDoNotChangeNativeRules() {
        SharedPreferences nativePrefs=context.getSharedPreferences("Notifications",0);
        assertTrue(nativePrefs.edit().putInt("notify2_-20",2).commit());
        watch(2,false); enable(); MessageObject m=message(-20,1,2,PERSON,"muted archived"); m.messageOwner.silent=true;
        TLRPC.TL_dialog dialog=new TLRPC.TL_dialog(); dialog.folder_id=1; dialog.id=-20;
        deliver(0,m); drain(); assertEquals(1,count()); assertEquals(2,nativePrefs.getInt("notify2_-20",0)); assertEquals(1,dialog.folder_id);
    }
    @Test public void keywordAndPersonAndDuplicateEventsProduceOneNotification() {
        controller.engine().setKeywords(Arrays.asList("hello","Hello")); watch(7,false); enable();
        send(PERSON,1,1,PERSON,"hello"); send(PERSON,1,1,PERSON,"hello"); drain();
        assertEquals(1,count()); assertEquals(1,jobs.submitted.size());
        send(PERSON,2,1,PERSON,"hello"); drain(); assertEquals(2,count());
    }
    @Test public void keywordSubSwitchDoesNotDisableWatchedPeople() {
        controller.engine().setKeywords(Collections.singletonList("hello")); watch(7,false); enable();
        controller.engine().setKeywordsEnabled(false);
        send(99,1,1,99,"hello"); send(PERSON,2,1,PERSON,"hello"); drain();
        assertEquals(1,count()); assertEquals(0,matches);
    }
    @Test public void otherAccountsOwnMessagesAndHistoryAreExcluded() {
        watch(7,false); enable();
        deliver(1,message(PERSON,1,1,PERSON,"other account"));
        MessageObject own=message(PERSON,2,1,PERSON,"own"); own.messageOwner.out=true; deliver(0,own);
        MessageObject old=message(PERSON,3,1,PERSON,"old"); old.messageOwner.date--; deliver(0,old);
        send(PERSON,4,1,OWNER,"self identity"); drain(); assertEquals(0,count());
    }
    @Test public void watchedConfigRestartDedupAndAccountIsolation() {
        watch(7,true); controller.engine().setKeywordsEnabled(false); enable(); send(PERSON,1,1,PERSON,"same"); drain();
        controller.engine().shutdown(); controller=create(); controller.engine().restore();
        assertFalse(controller.engine().keywordsEnabled()); assertEquals(1,controller.engine().activeWatched().size());
        send(PERSON,1,1,PERSON,"same"); drain(); assertEquals(1,count());
        assertTrue(controller.enableForAccount(1)); assertTrue(controller.engine().activeWatched().isEmpty());
        assertEquals(1,controller.engine().watched(OWNER).size());
    }
    @Test public void masterOffCancelsEvenAlreadyDequeuedJobsAndDoesNoWork() {
        watch(7,true); enable(); send(PERSON,1,1,PERSON,"pending"); queueStatus(-1); queueStatus(accounts.now+100);
        List<Runnable> old=new ArrayList<>(jobs.submitted); controller.engine().disable();
        for (Runnable r:old) r.run(); send(PERSON,2,1,PERSON,"off"); status(-1); status(accounts.now+200); drain();
        assertEquals(0,count()); assertEquals(0,matches); assertEquals(0,writes);
        List<WatchedPerson> restored = new KeywordReminderStore(context).watched(OWNER);
        assertEquals(1,restored.size());
        assertFalse(restored.get(0).privateRead);
    }
    @Test public void removePersonInvalidatesPendingMessageAndPresenceTasks() {
        watch(7,true); enable(); send(PERSON,1,1,PERSON,"queued"); queueStatus(-1); queueStatus(accounts.now+100);
        List<Runnable> stale=new ArrayList<>(jobs.submitted); controller.engine().setWatched(OWNER,Collections.emptyList());
        stale.forEach(Runnable::run); drain(); assertEquals(0,count());
    }
    @Test public void rapidSwitchingDoesNotDuplicateObserversOrNotifyOldJobs() {
        watch(7,true);
        for(int i=0;i<30;i++){ enable(); send(PERSON,i+1,1,PERSON,"queued"); controller.engine().disable(); }
        enable(); send(PERSON,100,1,PERSON,"new"); drain(); assertEquals(1,count());
        jobs.submitted.forEach(Runnable::run); assertEquals(1,count());
    }
    @Test public void presenceInitialOnlineIsBaselineAndRealTransitionNotifiesOnce() {
        watch(0,true); enable(); status(accounts.now+100); drain(); assertEquals(0,count());
        status(accounts.now+120); drain(); assertEquals(0,count());
        status(-1); status(accounts.now+150); status(accounts.now+170); drain(); assertEquals(1,count());
        Notification n=notifications.getActiveNotifications()[0].getNotification();
        assertEquals("Person 42",n.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        Intent click=shadowOf(n.contentIntent).getSavedIntent();
        assertEquals(PERSON,click.getLongExtra("userId",0)); assertEquals(0,click.getIntExtra("messageId",-1));
    }
    @Test public void presenceUnknownHiddenStatusIsNotOfflineAndNoOtherUserAlert() {
        watch(0,true); enable(); status(0); status(accounts.now+100); drain(); assertEquals(0,count());
        status(0); status(accounts.now+150); drain(); assertEquals(0,count());
        controller.engine().onStatus(1,PERSON,-1,"Alice"); controller.engine().onStatus(1,PERSON,accounts.now+180,"Alice");
        controller.engine().onStatus(0,99,-1,"Other"); controller.engine().onStatus(0,99,accounts.now+180,"Other");
        drain(); assertEquals(0,count());
    }
    @Test public void presenceCooldownPersistsAndDoesNotBlockSpeech() {
        watch(7,true); enable(); status(-1); status(accounts.now+100); drain(); assertEquals(1,count());
        accounts.now+=10; status(-1); status(accounts.now+100); send(PERSON,1,1,PERSON,"speak"); drain(); assertEquals(2,count());
        controller.engine().shutdown(); controller=create(); controller.engine().restore();
        status(-1); status(accounts.now+150); drain(); assertEquals(2,count());
        accounts.now+=61; status(-1); status(accounts.now+100); drain(); assertEquals(3,count());
    }
    @Test public void reconnectCancelsOnlyPresenceAndResetsBaseline() {
        watch(7,true); enable(); queueStatus(-1); queueStatus(accounts.now+100); send(PERSON,1,1,PERSON,"speech");
        Runnable oldPresence=jobs.submitted.get(0);
        accounts.connected=false; NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.didUpdateConnectionState);
        oldPresence.run(); drain(); assertEquals(1,count());
        accounts.connected=true; NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.didUpdateConnectionState);
        status(accounts.now+120); drain(); assertEquals(1,count());
        status(-1); status(accounts.now+150); drain(); assertEquals(2,count());
    }
    @Test public void nativeStatusObserverSurvivesOurDisableAndWrongMaskDoesNotTrigger() {
        int[] hits={0}; NotificationCenter.NotificationCenterDelegate observer=(id,a,args)->hits[0]++;
        NotificationCenter.getInstance(0).addObserver(observer,NotificationCenter.updateInterfaces);
        try {
            watch(0,true); enable(); status(-1); accounts.status.put(PERSON,accounts.now+100);
            NotificationCenter.getInstance(0).postNotificationName(NotificationCenter.updateInterfaces,MessagesController.UPDATE_MASK_NAME);
            drain(); assertEquals(0,count()); controller.engine().disable(); status(accounts.now+200); assertEquals(3,hits[0]);
        } finally { NotificationCenter.getInstance(0).removeObserver(observer,NotificationCenter.updateInterfaces); }
    }
    @Test public void renamedChannelKeepsUserImportanceAndDisabledNotificationsStayDisabled() {
        NotificationChannel channel=new NotificationChannel(KeywordNotificationPublisher.CHANNEL,"Keyword reminders",NotificationManager.IMPORTANCE_LOW);
        notifications.createNotificationChannel(channel); watch(1,false); enable(); send(PERSON,1,1,PERSON,"speech"); drain();
        assertEquals(1,count()); channel=notifications.getNotificationChannel(KeywordNotificationPublisher.CHANNEL);
        assertEquals("Reminders",channel.getName().toString()); assertEquals(NotificationManager.IMPORTANCE_LOW,channel.getImportance());
    }
    @Test public void invalidAndDuplicatePeopleAreRejectedAndCorruptEntryDoesNotEraseOthers() {
        try { controller.engine().setWatched(OWNER,Arrays.asList(new WatchedPerson(PERSON,"A",7,false),new WatchedPerson(PERSON,"B",7,false))); fail(); }
        catch(IllegalArgumentException expected) { }
        try { controller.engine().setWatched(OWNER,Collections.singletonList(new WatchedPerson(OWNER,"self",7,false))); fail(); }
        catch(IllegalArgumentException expected) { }
        List<WatchedPerson> tooMany=new ArrayList<>(); for(int i=1;i<=101;i++) tooMany.add(new WatchedPerson(i,"person",0,false));
        try { controller.engine().setWatched(OWNER,tooMany); fail(); } catch(IllegalArgumentException expected) { }
        assertTrue(prefs.edit().putString("watched_500","[{\"id\":42,\"name\":\"Alice\",\"scopes\":7,\"online\":true},{\"id\":-1}]").commit());
        assertEquals(1,new KeywordReminderStore(context).watched(OWNER).size());
    }
    @Test public void settingsEditPersonSaveAndRemoveActuallyExecute() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        KeywordSettingsView view=new KeywordSettingsView(activity,null,controller,0); activity.setContentView(view);
        assertFalse(((TextCheckCell)view.getChildAt(0)).isChecked());
        WatchedPerson person=new WatchedPerson(PERSON,"Alice",0,false); view.editPerson(person); drain();
        AlertDialog dialog=(AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        List<TextCheckCell> checks=new ArrayList<>(); collect(dialog.getWindow().getDecorView(),checks);
        assertEquals(5,checks.size()); assertFalse(checks.get(4).isChecked());
        checks.get(0).performClick(); checks.get(3).performClick(); checks.get(4).performClick();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        WatchedPerson saved=controller.engine().watched(OWNER).get(0); assertEquals(1,saved.scopes); assertTrue(saved.online);
        assertTrue(saved.privateRead);
        view.editPerson(saved); drain(); dialog=(AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick(); assertTrue(controller.engine().watched(OWNER).isEmpty());
        activity.finish();
    }
    @Test public void privateOutboxReceiptBatchesConfirmedSendsAndRoutesToLastMessage() {
        watchPrivateReads(true);
        enable();
        sendOutgoing(0, PERSON, -1, WatchedPerson.PRIVATE);
        sendOutgoing(0, PERSON, -2, WatchedPerson.PRIVATE);
        confirm(0, PERSON, -1, 101);
        confirm(0, PERSON, -2, 104);
        read(0, PERSON, 104, false);
        drain();
        assertEquals(0, count());

        accounts.now += 60;
        read(0, PERSON, 104, true);
        read(0, PERSON, 104, true);
        read(0, PERSON, 101, true);
        drain();
        assertEquals(1, count());
        KeywordReminderStore store = new KeywordReminderStore(context);
        WatchedPersonRecord record = store.history(OWNER).get(0);
        assertEquals(1, store.history(OWNER).size());
        assertEquals(2, record.privateReadCount);
        assertEquals(104, record.messageId);
        assertEquals(accounts.now, record.date);
        Notification notice = notifications.getActiveNotifications()[0].getNotification();
        assertEquals("Read 2 messages you sent", notice.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        Intent route = shadowOf(notice.contentIntent).getSavedIntent();
        assertEquals(PERSON, route.getLongExtra("userId", 0));
        assertEquals(104, route.getIntExtra("messageId", 0));
        assertFalse(prefs.getString(KeywordReminderStore.historyKey(OWNER), "").contains("message body"));

        controller.engine().shutdown();
        controller = create();
        controller.engine().restore();
        assertTrue(controller.engine().activeWatched().get(0).privateRead);
        read(0, PERSON, 104, true);
        drain();
        assertEquals(1, store.history(OWNER).size());
    }

    @Test public void privateReadWaitsForSendConfirmationAndIgnoresUntrackedMessages() {
        enable();
        sendOutgoing(0, PERSON, -1, WatchedPerson.PRIVATE);
        watchPrivateReads(true);
        confirm(0, PERSON, -1, 101);
        sendOutgoing(1, PERSON, -2, WatchedPerson.PRIVATE);
        confirm(1, PERSON, -2, 102);
        sendOutgoing(0, -20, -3, WatchedPerson.GROUP);
        confirm(0, -20, -3, 103);
        read(0, -20, 103, true);
        read(0, PERSON, 102, true);
        sendOutgoing(0, PERSON, -4, WatchedPerson.PRIVATE);
        read(0, PERSON, 104, true);
        long observedAt = accounts.now;
        drain();
        assertEquals(0, count());

        accounts.now += 3;
        confirm(0, PERSON, -4, 104);
        confirm(0, PERSON, -4, 104);
        drain();
        assertEquals(1, count());
        WatchedPersonRecord record = new KeywordReminderStore(context).history(OWNER).get(0);
        assertEquals(1, record.privateReadCount);
        assertEquals(104, record.messageId);
        assertEquals(observedAt, record.date);
    }

    @Test public void disablingReadAlertsCancelsQueuedNoticeAndDiscardsTracking() {
        watchPrivateReads(true);
        enable();
        sendOutgoing(0, PERSON, -1, WatchedPerson.PRIVATE);
        confirm(0, PERSON, -1, 100);
        read(0, PERSON, 100, true);
        List<Runnable> stale = new ArrayList<>(jobs.submitted);
        assertEquals(1, stale.size());
        sendOutgoing(0, PERSON, -2, WatchedPerson.PRIVATE);
        confirm(0, PERSON, -2, 101);
        watchPrivateReads(false);
        watchPrivateReads(true);
        stale.forEach(Runnable::run);
        read(0, PERSON, 101, true);
        drain();
        assertEquals(0, count());
        assertTrue(new KeywordReminderStore(context).history(OWNER).isEmpty());
        assertTrue(new KeywordReminderStore(context).pendingPrivateReads(OWNER, PERSON).isEmpty());

        sendOutgoing(0, PERSON, -3, WatchedPerson.PRIVATE);
        confirm(0, PERSON, -3, 102);
        read(0, PERSON, 102, true);
        controller.engine().disable();
        enable();
        read(0, PERSON, 102, true);
        drain();
        assertEquals(0, count());
        assertTrue(new KeywordReminderStore(context).history(OWNER).isEmpty());
        assertTrue(new KeywordReminderStore(context).pendingPrivateReads(OWNER, PERSON).isEmpty());
    }

    @Test public void serverSnapshotReconcilesOnlyPersistedTrackedIdsAfterRestart() {
        watchPrivateReads(true);
        enable();
        serverSnapshot(0, PERSON, 100);
        sendOutgoing(0, PERSON, -1, WatchedPerson.PRIVATE);
        sendOutgoing(0, PERSON, -2, WatchedPerson.PRIVATE);
        sendOutgoing(0, PERSON, -3, WatchedPerson.PRIVATE);
        confirm(0, PERSON, -1, 101);
        confirm(0, PERSON, -2, 104);
        confirm(0, PERSON, -3, 106);
        KeywordReminderStore store = new KeywordReminderStore(context);
        assertEquals(Arrays.asList(101, 104, 106), store.pendingPrivateReads(OWNER, PERSON));
        controller.engine().shutdown();
        controller = create();
        controller.engine().restore();
        serverSnapshot(1, PERSON, 106);
        serverSnapshot(0, PERSON, 100);
        drain();
        assertEquals(0, count());

        accounts.now += 120;
        serverSnapshot(0, PERSON, 105);
        read(0, PERSON, 105, true);
        drain();
        assertEquals(1, count());
        assertEquals(Collections.singletonList(106), store.pendingPrivateReads(OWNER, PERSON));
        WatchedPersonRecord record = store.history(OWNER).get(0);
        assertEquals(2, record.privateReadCount);
        assertEquals(104, record.messageId);
        assertEquals(accounts.now, record.date);

        controller.engine().shutdown();
        controller = create();
        controller.engine().restore();
        serverSnapshot(0, PERSON, 105);
        drain();
        assertEquals(1, store.history(OWNER).size());
        serverSnapshot(0, PERSON, 106);
        drain();
        assertEquals(2, store.history(OWNER).size());
        assertEquals(106, store.history(OWNER).get(0).messageId);
        assertTrue(store.pendingPrivateReads(OWNER, PERSON).isEmpty());
    }
    private void collect(android.view.View view,List<TextCheckCell> checks) {
        if(view instanceof TextCheckCell) checks.add((TextCheckCell)view);
        if(view instanceof android.view.ViewGroup) for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++) collect(((android.view.ViewGroup)view).getChildAt(i),checks);
    }
}
