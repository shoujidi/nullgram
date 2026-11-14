package com.nullgram.client.appearance;

import android.content.Context;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;

public final class LocalAliasEditor {
    private LocalAliasEditor() {}

    public static void show(BaseFragment fragment, int account, long peer) {
        Context context = fragment.getParentActivity();
        if (context == null || !UserConfig.getInstance(account).isClientActivated()
                || peer == 0 || DialogObject.isEncryptedDialog(peer)
                || peer == UserConfig.getInstance(account).getClientUserId()) {
            return;
        }
        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.User user = peer > 0 ? controller.getUser(peer) : null;
        TLRPC.Chat chat = peer < 0 ? controller.getChat(-peer) : null;
        if (user == null && chat == null || user != null && UserObject.isDeleted(user)) {
            return;
        }
        String original = user != null ? UserObject.getUserName(user) : chat.title;
        fragment.presentFragment(new LocalAliasEditActivity(account, peer, original));
    }
}
