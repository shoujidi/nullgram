package com.nullgram.client.keyword;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.NotificationManager;
import android.content.Context;
import androidx.collection.LongSparseArray;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import org.telegram.messenger.*;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

/** Runs the original native notification renderer, with account/network startup substituted. */
@RunWith(RobolectricTestRunner.class)
@Config(application = UiTestEnvironment.App.class, sdk = {28, 36},
        shadows = {NativeNotificationCompatibilityTest.Strings.class,
                NativeNotificationCompatibilityTest.Messages.class, NativeNotificationCompatibilityTest.Network.class,
                NativeNotificationCompatibilityTest.Storage.class})
public class NativeNotificationCompatibilityTest {
    @Implements(LocaleController.class)
    public static class Strings extends UiTestEnvironment.LocalStrings {
        @Implementation protected static String formatPluralString(String key, int count, Object... args) { return count + " " + key; }
    }
    @Implements(MessagesController.class)
    public static class Messages extends UiTestEnvironment.LocalSettings {
        static boolean missingPeer;
        @Implementation protected void __constructor__(int account) { }
        @Implementation protected static android.content.SharedPreferences getNotificationsSettings(int account) {
            return RuntimeEnvironment.getApplication().getSharedPreferences("Notifications" + account, Context.MODE_PRIVATE);
        }
        @Implementation protected TLRPC.User getUser(Long id) {
            if (missingPeer) return null;
            TLRPC.TL_user user = new TLRPC.TL_user(); user.id = id; user.first_name = "Sender"; return user;
        }
        @Implementation protected TLRPC.Chat getChat(Long id) { return null; }
        @Implementation protected boolean isForum(long id) { return false; }
        @Implementation protected boolean isForum(MessageObject message) { return false; }
        @Implementation protected boolean isCommunity(long id) { return false; }
        @Implementation protected long getSendPaidMessagesStars(long id) { return 0; }
    }
    @Implements(ConnectionsManager.class)
    public static class Network {
        @Implementation protected void __constructor__(int account) { }
        @Implementation protected int getCurrentTime() { return 1700000000; }
        @Implementation protected long getCurrentTimeMillis() { return 1700000000000L; }
        @Implementation protected void resumeNetworkMaybe() { }
    }

    @Implements(MessagesStorage.class)
    public static class Storage {
        @Implementation protected void __constructor__(int account) {}
        @Implementation protected TLRPC.User getUserSync(long id) { return null; }
        @Implementation protected TLRPC.Chat getChatSync(long id) { return null; }
    }

    @Test public void T02_F13_originalNotificationRendererPublishesWhileKeywordsOffAndSurvivesToggle() {
        Context context = ApplicationLoader.applicationContext;
        new org.telegram.ui.Components.ForegroundDetector(RuntimeEnvironment.getApplication());
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        context.getSharedPreferences(KeywordReminderStore.PREFERENCES, 0).edit().clear().commit();
        KeywordReminderController keywords = new KeywordReminderController(new KeywordReminderStore(context),
                new KeywordReminderTest.Sessions(), new KeywordReminderTest.CapturedTasks(),
                new KeywordNotificationPublisher(context), KeywordReminderEngine::contains);
        TLRPC.TL_user self = new TLRPC.TL_user(); self.id = 500; self.first_name = "Test";
        UserConfig.selectedAccount = 0; UserConfig.getInstance(0).setCurrentUser(self);
        ApplicationLoader.mainInterfacePaused = true;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancelAll();
        NotificationsController nativeNotifications = new NotificationsController(0);
        TLRPC.TL_message owner = new TLRPC.TL_message();
        owner.id = 91; owner.date = 1700000000; owner.dialog_id = 42; owner.message = "Ordinary message";
        owner.peer_id = new TLRPC.TL_peerUser(); owner.peer_id.user_id = 42;
        owner.from_id = new TLRPC.TL_peerUser(); owner.from_id.user_id = 42;
        MessageObject message = new MessageObject(0, owner, "Sender: Ordinary message", "Sender", "Sender", false, false, false, false);
        ArrayList<MessageObject> pushes = ReflectionHelpers.getField(nativeNotifications, "pushMessages"); pushes.add(message);
        LongSparseArray<Integer> dialogs = ReflectionHelpers.getField(nativeNotifications, "pushDialogs"); dialogs.put(42, 1);
        ReflectionHelpers.setField(nativeNotifications, "total_unread_count", 1);
        render(nativeNotifications);
        assertFalse(keywords.engine().isEnabled());
        assertTrue("Original NotificationsController must actually publish", manager.getActiveNotifications().length > 0);
        int count = manager.getActiveNotifications().length;
        assertTrue(keywords.enableForAccount(0)); keywords.engine().disable();
        assertEquals("Turning keywords off must not clear native notices", count, manager.getActiveNotifications().length);
        render(nativeNotifications);
        assertTrue(manager.getActiveNotifications().length > 0);
        for (android.service.notification.StatusBarNotification notice : manager.getActiveNotifications()) {
            assertNotEquals(KeywordNotificationPublisher.CHANNEL, notice.getNotification().getChannelId());
        }
        keywords.engine().shutdown();
    }
    @Test public void coldFcmNotificationsUseAccountAliasesWithoutPeerModels() {
        Context context = ApplicationLoader.applicationContext;
        new org.telegram.ui.Components.ForegroundDetector(RuntimeEnvironment.getApplication());
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        TLRPC.TL_user self = new TLRPC.TL_user(); self.id = 500; self.first_name = "Test";
        UserConfig.selectedAccount = 0;
        UserConfig.getInstance(0).setCurrentUser(self);
        ApplicationLoader.mainInterfacePaused = true;
        top.nkbe.niagram.config.NyaConfig.getPreferences().edit()
                .putString("userNameOverride_500_42", "Local contact")
                .putString("chatNameOverride_500_43", "Local group")
                .putString("chatNameOverride_500_44", "Local channel")
                .putString("userNameOverride_600_45", "Other account private").commit();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        Messages.missingPeer = true;
        try {
            long[] peers = {42, -43, -44, 45};
            String[] names = {"Local contact", "Local group", "Local channel", "Remote name"};
            for (int i = 0; i < peers.length; i++) {
                manager.cancelAll();
                NotificationsController controller = new NotificationsController(0);
                TLRPC.TL_message owner = new TLRPC.TL_message();
                owner.id = 91 + i; owner.date = 1700000000; owner.dialog_id = peers[i]; owner.message = "Message body";
                if (peers[i] > 0) {
                    owner.peer_id = new TLRPC.TL_peerUser(); owner.peer_id.user_id = peers[i];
                } else if (i == 1) {
                    owner.peer_id = new TLRPC.TL_peerChat(); owner.peer_id.chat_id = -peers[i];
                } else {
                    owner.peer_id = new TLRPC.TL_peerChannel(); owner.peer_id.channel_id = -peers[i];
                }
                owner.from_id = new TLRPC.TL_peerUser(); owner.from_id.user_id = 42;
                MessageObject message = new MessageObject(0, owner, "Remote name: Message body", "Remote name", "Remote sender", false, i == 2, false, false);
                ArrayList<MessageObject> pushes = ReflectionHelpers.getField(controller, "pushMessages"); pushes.add(message);
                LongSparseArray<Integer> dialogs = ReflectionHelpers.getField(controller, "pushDialogs"); dialogs.put(peers[i], 1);
                ReflectionHelpers.setField(controller, "total_unread_count", 1);
                render(controller);
                boolean found = false;
                for (var notice : manager.getActiveNotifications()) {
                    CharSequence title = notice.getNotification().extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
                    if (title != null && title.toString().contains(names[i])) found = true;
                }
                assertTrue("Expected published notification title " + names[i], found);
                String[] sender = {null};
                controller.getShortStringForMessage(message, sender, new boolean[1]);
                if (i == 1) assertEquals("Local contact", sender[0]);
                assertEquals("Remote name", message.localName);
                assertEquals("Message body", owner.message);
            }
        } finally { Messages.missingPeer = false; manager.cancelAll(); }
    }

    private void render(NotificationsController controller) {
        ReflectionHelpers.callInstanceMethod(controller, "showOrUpdateNotification", ClassParameter.from(boolean.class, false));
    }
}
