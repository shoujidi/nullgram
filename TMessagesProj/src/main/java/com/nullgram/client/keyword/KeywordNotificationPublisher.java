// SPDX-License-Identifier: GPL-3.0-only
// Nullgram modification, 2026-11-01.
package com.nullgram.client.keyword;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;

public final class KeywordNotificationPublisher implements KeywordReminderEngine.Publisher {
    public static final String CHANNEL = "nullgram_keywords_v1";
    public static final String TAG_PREFIX = "nullgram_keyword:";
    private final Context context;
    public KeywordNotificationPublisher(Context context) { this.context = context.getApplicationContext(); }

    @Override public boolean publish(KeywordReminderEngine.Message message, int account, long userId) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
            NotificationManagerCompat manager = NotificationManagerCompat.from(context);
            if (!manager.areNotificationsEnabled()) return false;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager system = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                NotificationChannel channel = system.getNotificationChannel(CHANNEL);
                if (channel == null) system.createNotificationChannel(new NotificationChannel(CHANNEL,
                        LocaleController.getString(R.string.NullgramKeywordTitle), NotificationManager.IMPORTANCE_HIGH));
                else if (channel.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
            }
            boolean locked = SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter;
            String title = locked ? LocaleController.getString(R.string.NullgramKeywordTitle) : message.title;
            String text = locked ? LocaleController.getString(R.string.NullgramKeywordLocked) : message.text;
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
