package com.nullgram.client.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.InputFilter;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import top.nkbe.niagram.helpers.LocalNameHelper;

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
        String alias = LocalNameHelper.get(account, peer);
        String initial = alias == null ? original : alias;
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(16);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, fragment.getResourceProvider()));
        input.setSingleLine(true);
        input.setHint(getString(R.string.NullgramLocalAlias));
        input.setText(initial);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Math.max(64, input.length()))});
        input.setSelection(input.length());

        FrameLayout field = new FrameLayout(context);
        field.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, 0, 22, 0, 22, 0));
        field.setPadding(0, dp(4), 0, 0);

        AlertDialog.Builder builder = new AlertDialog.Builder(context, fragment.getResourceProvider())
                .setTitle(getString(R.string.NullgramLocalAlias))
                .setMessage(LocaleController.formatString(R.string.NullgramLocalAliasHint, original))
                .setView(field)
                .setPositiveButton(getString(R.string.Save), (dialog, which) ->
                        LocalNameHelper.saveEditedName(account, peer, initial, input.getText().toString()))
                .setNegativeButton(getString(R.string.Cancel), null);
        if (alias != null) {
            builder.setNeutralButton(getString(R.string.NullgramRestoreName), (dialog, which) ->
                    LocalNameHelper.set(account, peer, null));
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            input.requestFocus();
            AndroidUtilities.showKeyboard(input);
        });
        fragment.showDialog(dialog);
    }
}
