// SPDX-License-Identifier: GPL-3.0-only
package com.nullgram.client.keyword;

import android.content.Context;
import android.content.Intent;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

/** Local timeline entry. Message bodies and attachments stay in their original chats. */
final class WatchedPersonRecord {
    final String name, chatTitle;
    final long date, dialogId, topicId;
    final int messageId, scope, privateReadCount;

    WatchedPersonRecord(String name, String chatTitle, long date, long dialogId, int messageId, long topicId, int scope) {
        this(name, chatTitle, date, dialogId, messageId, topicId, scope, 0);
    }

    WatchedPersonRecord(String name, String chatTitle, long date, long dialogId, int messageId, long topicId, int scope, int privateReadCount) {
        this.name = limit(name);
        this.chatTitle = limit(chatTitle);
        this.date = date;
        this.dialogId = dialogId;
        this.messageId = messageId;
        this.topicId = topicId;
        this.scope = scope;
        this.privateReadCount = privateReadCount;
    }

    private static String limit(String text) {
        if (text == null) return "";
        return text.length() > 256 ? text.substring(0, 256) : text;
    }

    String description() {
        if (privateReadCount > 0) {
            return privateReadCount == 1 ? LocaleController.getString(R.string.NullgramReminderReadSingle)
                    : LocaleController.formatString(R.string.NullgramReminderReadMultiple, privateReadCount);
        }
        if (messageId == 0) return LocaleController.getString(R.string.NullgramReminderOnlineText);
        if (scope == WatchedPerson.PRIVATE) return LocaleController.getString(R.string.NullgramReminderPrivate);
        int label = scope == WatchedPerson.CHANNEL ? R.string.NullgramReminderChannel : R.string.NullgramReminderGroup;
        String type = LocaleController.getString(label);
        return chatTitle.isEmpty() ? type : type + " · " + chatTitle;
    }

    Intent openIntent(Context context, int account, long owner) {
        return KeywordNotificationRouter.intent(context, account, owner, dialogId, messageId, topicId);
    }
}
