// SPDX-License-Identifier: GPL-3.0-only
// Nullgram modification, 2026-11-01.
package com.nullgram.client.keyword;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ContactsActivity;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.MessagesController;

public final class KeywordSettingsActivity extends BaseFragment {
    private KeywordSettingsView settings;
    private long requestedPerson;
    private WatchedPerson pendingPerson;
    public KeywordSettingsActivity() { }
    public KeywordSettingsActivity(long userId) { requestedPerson = userId; }
    @Override public void onResume() {
        super.onResume();
        if (pendingPerson != null && settings != null) {
            WatchedPerson person = pendingPerson; pendingPerson = null;
            settings.editPerson(person);
        }
    }
    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.NullgramKeywordTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, getResourceProvider()));
        settings = new KeywordSettingsView(context, getResourceProvider(), KeywordReminderController.get(), currentAccount);
        settings.setPersonPicker(this::pickPerson);
        if (requestedPerson > 0) {
            org.telegram.tgnet.TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(requestedPerson);
            if (user != null && !user.bot && !user.deleted && !user.self) preparePerson(user);
            requestedPerson = 0;
        }
        scroll.addView(settings);
        fragmentView = scroll;
        return fragmentView;
    }
    private void preparePerson(org.telegram.tgnet.TLRPC.User user) {
        pendingPerson = new WatchedPerson(user.id, UserObject.getUserName(user), 0, false);
        for (WatchedPerson saved : KeywordReminderController.get().engine().watched(
                KeywordReminderController.get().accountUserId(currentAccount))) if (saved.id == user.id) pendingPerson = saved;
    }
    private void pickPerson() {
        Bundle args = new Bundle();
        args.putBoolean("onlyUsers", true);
        args.putBoolean("returnAsResult", true);
        args.putBoolean("allowBots", false);
        args.putBoolean("allowSelf", false);
        args.putBoolean("allowUsernameSearch", true);
        args.putBoolean("needFinishFragment", true);
        ContactsActivity picker = new ContactsActivity(args);
        picker.setCurrentAccount(currentAccount);
        picker.setDelegate((user, param, activity) -> {
            if (user == null || user.bot || user.deleted || user.id == UserConfig.getInstance(currentAccount).getClientUserId()) return;
            MessagesController.getInstance(currentAccount).putUser(user, false);
            preparePerson(user);
        });
        presentFragment(picker);
    }
}
