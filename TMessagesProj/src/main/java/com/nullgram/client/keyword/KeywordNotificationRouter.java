// SPDX-License-Identifier: GPL-3.0-only
// Nullgram modification, 2026-11-01.
package com.nullgram.client.keyword;

import android.content.Context;
import android.content.Intent;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.LaunchActivity;

/** Uses the same open-chat extras and message positioning mechanism as Telegram notifications. */
public final class KeywordNotificationRouter {
    public static final String USER_ID = "nullgram_keyword_user_id";
    private KeywordNotificationRouter() { }
    public static Intent intent(Context context, int account, long userId, long dialogId, int messageId) {
        return intent(context, account, userId, dialogId, messageId, 0);
    }
    public static Intent intent(Context context, int account, long userId, long dialogId, int messageId, long topicId) {
        Intent intent = new Intent(context, LaunchActivity.class);
        intent.setAction("com.tmessages.openchat.keyword." + userId + "." + dialogId + "." + messageId);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("currentAccount", account);
        intent.putExtra(USER_ID, userId);
        intent.putExtra("messageId", messageId);
        if (topicId > 0) intent.putExtra("topicId", topicId);
        if (DialogObject.isEncryptedDialog(dialogId)) intent.putExtra("encId", DialogObject.getEncryptedChatId(dialogId));
        else if (dialogId < 0) intent.putExtra("chatId", -dialogId);
        else intent.putExtra("userId", dialogId);
        return intent;
    }

    /** Only keyword notifications add exact positioning to the existing secret-chat route. */
    public static void addEncryptedMessagePosition(Intent intent, android.os.Bundle args) {
        if (intent != null && intent.hasExtra(USER_ID) && intent.getIntExtra("encId", 0) != 0) {
            int messageId = intent.getIntExtra("messageId", 0);
            if (messageId != 0) args.putInt("message_id", messageId);
        }
    }

    public static void sanitizeIfStale(Intent intent) {
        if (intent == null || !intent.hasExtra(USER_ID)) return;
        int account = intent.getIntExtra("currentAccount", -1);
        boolean valid = account >= 0 && account < UserConfig.MAX_ACCOUNT_COUNT
                && UserConfig.getInstance(account).isClientActivated()
                && UserConfig.getInstance(account).getClientUserId() == intent.getLongExtra(USER_ID, 0);
        sanitize(intent, valid, UserConfig.selectedAccount);
    }

    public static void sanitize(Intent intent, boolean valid, int fallbackAccount) {
        if (!intent.hasExtra(USER_ID) || valid) return;
        // Old visible notifications survive disabling/logout, but can never open another user's chat.
        intent.setAction(Intent.ACTION_MAIN);
        for (String key : new String[]{"chatId", "userId", "encId", "messageId", "topicId", USER_ID}) intent.removeExtra(key);
        intent.putExtra("currentAccount", Math.max(0, Math.min(UserConfig.MAX_ACCOUNT_COUNT - 1, fallbackAccount)));
    }
}
