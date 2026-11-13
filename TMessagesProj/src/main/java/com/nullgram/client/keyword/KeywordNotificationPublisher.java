// SPDX-License-Identifier: GPL-3.0-only
// Nullgram modification, 2026-11-01.
package com.nullgram.client.keyword;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ContentPreviewViewer;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.SecretMediaViewer;

public final class KeywordNotificationPublisher implements KeywordReminderEngine.Publisher {
    public static final String CHANNEL = "nullgram_keywords_v1";
    public static final String TAG_PREFIX = "nullgram_keyword:";
    private static final long CHAT_LAYOUT_DELAY_MS = 320;
    private final Context context;
    public KeywordNotificationPublisher(Context context) { this.context = context.getApplicationContext(); }

    @Override public long watchedMessageDelayMillis(KeywordReminderEngine.Message message, int account, long userId) {
        // Give the incoming row time to lay out and finish its normal entrance animation.
        return isIncomingPrivateMessage(message) && focusedChat(account, userId, message.dialogId) != null
                ? CHAT_LAYOUT_DELAY_MS : 0;
    }

    @Override public void publishWatched(KeywordReminderEngine.Message message, WatchedPersonRecord record,
                                        int account, long userId, boolean bannerEnabled) {
        if (isIncomingPrivateMessage(message)) {
            ChatActivity chat = focusedChat(account, userId, message.dialogId);
            try {
                if (chat != null && chat.isNewMessageVisibleForReminder(message.id)) return;
            } catch (RuntimeException unavailable) {
                // Keep the reminder if the chat is changing or no longer usable.
            }
        }
        if (bannerEnabled && showBanner(record, account, userId)) return;
        publish(message, account, userId, record.privateReadCount > 0 ? record.description() : null);
    }

    private static boolean isIncomingPrivateMessage(KeywordReminderEngine.Message message) {
        return message.scope == WatchedPerson.PRIVATE && message.id != 0
                && !message.online && message.privateReadCount == 0;
    }

    private static LaunchActivity focusedActivity(int account) {
        LaunchActivity activity = LaunchActivity.instance;
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus()
                || !LaunchActivity.isActive || ApplicationLoader.mainInterfacePaused || account != UserConfig.selectedAccount
                || SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter) return null;
        return activity;
    }

    private static ChatActivity focusedChat(int account, long userId, long dialogId) {
        try {
            LaunchActivity activity = focusedActivity(account);
            if (activity == null || !ApplicationLoader.isScreenOn
                    || UserConfig.getInstance(account).getClientUserId() != userId) return null;
            KeyguardManager keyguard = activity.getSystemService(KeyguardManager.class);
            if (keyguard == null || keyguard.isKeyguardLocked()) return null;
            if ((PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible())
                    || (SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible())
                    || (ArticleViewer.hasInstance() && ArticleViewer.getInstance().isVisible())
                    || (ContentPreviewViewer.hasInstance() && ContentPreviewViewer.getInstance().isVisible())) return null;

            BaseFragment fragment = LaunchActivity.getSafeLastFragment();
            if (!(fragment instanceof ChatActivity chat) || chat.getParentActivity() != activity
                    || chat.getCurrentAccount() != account || chat.getDialogId() != dialogId) return null;
            INavigationLayout layers = activity.getLayersActionBarLayout();
            if (layers != null && layers.getView().isShown() && layers.getLastFragment() != null
                    && layers.getLastFragment() != chat) return null;
            return chat;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private boolean showBanner(WatchedPersonRecord record, int account, long userId) {
        LaunchActivity activity = focusedActivity(account);
        if (activity == null) return false;
        try {
            BaseFragment fragment = LaunchActivity.getSafeLastFragment();
            if (!BulletinFactory.canShowBulletin(fragment) || fragment.getParentActivity() != activity
                    || fragment.getCurrentAccount() != account || !fragment.getLayoutContainer().isShown()
                    || Bulletin.getVisibleBulletin() != null) return false;
            return BulletinFactory.of(fragment).createSimpleBulletin(record.name, record.description(),
                    LocaleController.getString(R.string.Open),
                    () -> activity.startActivity(record.openIntent(activity, account, userId))).show(true).isShowing();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    @Override public boolean publish(KeywordReminderEngine.Message message, int account, long userId) {
        return publish(message, account, userId, null);
    }

    private boolean publish(KeywordReminderEngine.Message message, int account, long userId, String description) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
            NotificationManagerCompat manager = NotificationManagerCompat.from(context);
            if (!manager.areNotificationsEnabled()) return false;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager system = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                NotificationChannel channel = system.getNotificationChannel(CHANNEL);
                if (channel == null) system.createNotificationChannel(new NotificationChannel(CHANNEL,
                        LocaleController.getString(R.string.NullgramKeywordTitle), NotificationManager.IMPORTANCE_HIGH));
                else {
                    // Update only the visible name; retain user-controlled sound, importance and ID.
                    channel.setName(LocaleController.getString(R.string.NullgramKeywordTitle));
                    system.createNotificationChannel(channel);
                    if (channel.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
                }
            }
            boolean locked = SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter;
            String title = locked ? LocaleController.getString(R.string.NullgramKeywordTitle) : message.title;
            String text = locked ? LocaleController.getString(R.string.NullgramKeywordLocked)
                    : description != null ? description
                    : message.online ? LocaleController.getString(R.string.NullgramReminderOnlineText)
                    : message.text.isEmpty() ? LocaleController.getString(R.string.NullgramReminderMedia) : message.text;
            if (text.length() > 500) text = text.substring(0, 500);
            PendingIntent click = PendingIntent.getActivity(context, 0,
                    KeywordNotificationRouter.intent(context, account, userId, message.dialogId, message.id, message.topicId),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            android.app.Notification notification = new NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.nullgram_notification).setContentTitle(title).setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(click)
                    .setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE).setPriority(NotificationCompat.PRIORITY_HIGH).build();
            manager.notify(TAG_PREFIX + userId + ":" + message.key(), 1, notification);
            return true;
        } catch (RuntimeException denied) { return false; }
    }
}
