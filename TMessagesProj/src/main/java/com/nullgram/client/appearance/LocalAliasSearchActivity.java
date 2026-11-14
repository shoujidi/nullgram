package com.nullgram.client.appearance;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.RecyclerListView;

import java.util.Collections;
import java.util.List;
import top.nkbe.niagram.config.NyaConfig;
import top.nkbe.niagram.helpers.LocalNameHelper;

/** Queries only local preferences. Opening a chosen chat uses the existing chat navigation. */
public class LocalAliasSearchActivity extends BaseFragment {
    private final boolean management;
    public LocalAliasSearchActivity() { this(false); }
    public LocalAliasSearchActivity(boolean management) { this.management = management; }
    private long owner;
    private boolean opening;
    private EditTextBoldCursor search;
    private RecyclerListView.SelectionAdapter adapter;
    private List<LocalNameHelper.Alias> results = Collections.emptyList();
    private final SharedPreferences.OnSharedPreferenceChangeListener changes = (prefs, key) -> {
        if (key == null || LocalNameHelper.isAliasKey(key)) refresh();
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener avatarChanges = (prefs, key) -> refresh();

    @Override public View createView(Context context) {
        owner = getUserConfig().getClientUserId();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(management ? R.string.NullgramAliasManage : R.string.NullgramAliasSearch));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == 1) {
                    LocalAliasPickerActivity picker = new LocalAliasPickerActivity();
                    picker.setCurrentAccount(currentAccount);
                    presentFragment(picker);
                } else if (id == 2) {
                    showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                            .setTitle(LocaleController.getString(R.string.NullgramDisguiseImport))
                            .setMessage(LocaleController.getString(R.string.NullgramDisguiseImportConfirm))
                            .setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
                                if (!validOwner()) return;
                                LocalNameHelper.importLegacyNames(currentAccount);
                                actionBar.createMenu().getItem(2).setVisibility(View.GONE);
                                refresh();
                            }).setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
                }
            }
        });
        if (management) {
            actionBar.createMenu().addItem(1, R.drawable.msg_add).setContentDescription(LocaleController.getString(R.string.NullgramDisguiseAlias));
            actionBar.createMenu().addItem(2, R.drawable.msg_download).setContentDescription(LocaleController.getString(R.string.NullgramDisguiseImport));
        }
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        TextView hint = new TextView(context);
        hint.setText(LocaleController.getString(management ? R.string.NullgramAliasManageHint : R.string.NullgramAliasSearchHint));
        hint.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        hint.setTextSize(14);
        hint.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        content.addView(hint);
        search = new EditTextBoldCursor(context);
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        search.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        search.setHint(LocaleController.getString(R.string.NullgramAliasSearch));
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        search.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        content.addView(search, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(48)));

        FrameLayout rows = new FrameLayout(context);
        TextView empty = new TextView(context);
        empty.setText(LocaleController.getString(R.string.NoResult));
        empty.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        empty.setGravity(Gravity.CENTER);
        rows.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        RecyclerListView list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setEmptyView(empty);
        adapter = new RecyclerListView.SelectionAdapter() {
            @Override public int getItemCount() { return results.size(); }
            @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                return new RecyclerListView.Holder(new TextDetailSettingsCell(parent.getContext()));
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                LocalNameHelper.Alias alias = results.get(position);
                ((TextDetailSettingsCell) holder.itemView).setTextAndValue(alias.name(),
                        LocaleController.getString(R.string.NullgramLocalAlias)
                                + " · " + Math.abs(alias.peer()), position + 1 < results.size());
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < results.size()) open(results.get(position).peer(), management);
        });
        list.setOnItemLongClickListener((view, position) -> {
            if (position < 0 || position >= results.size() || !validOwner()) return false;
            LocalNameHelper.Alias alias = results.get(position);
            showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                    .setTitle(alias.name())
                    .setItems(new CharSequence[]{LocaleController.getString(R.string.Edit),
                            LocaleController.getString(R.string.NullgramRestoreName),
                            LocaleController.getString(R.string.NullgramAvatarRestore),
                            LocaleController.getString(R.string.NullgramAliasOpenChat)}, (dialog, which) -> {
                        if (!validOwner()) return;
                        if (which == 0) open(alias.peer(), true);
                        else if (which == 1) { LocalNameHelper.set(currentAccount, alias.peer(), null); refresh(); }
                        else if (which == 3) open(alias.peer());
                        else {
                            org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
                                boolean saved = LocalAvatarStore.save(currentAccount, owner, alias.peer(), null, "");
                                AndroidUtilities.runOnUIThread(() -> {
                                    if (saved && validOwner()) { LocalAvatarStore.refresh(currentAccount); refresh(); }
                                });
                            });
                        }
                    }).create());
            return true;
        });
        rows.addView(list, new FrameLayout.LayoutParams(-1, -1));
        content.addView(rows, new LinearLayout.LayoutParams(-1, 0, 1f));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        fragmentView = content;
        refresh();
        return content;
    }

    private boolean validOwner() {
        return !isFinished && owner != 0 && getUserConfig().getClientUserId() == owner
                && getUserConfig().isClientActivated();
    }

    private void refresh() {
        if (adapter == null) return;
        results = validOwner() ? LocalAvatarStore.search(currentAccount, search.getText().toString()) : Collections.emptyList();
        adapter.notifyDataSetChanged();
    }

    @Override public void onResume() {
        super.onResume();
        if (management && actionBar != null) actionBar.createMenu().getItem(2).setVisibility(
                validOwner() && LocalNameHelper.hasLegacyNames() ? View.VISIBLE : View.GONE);
        NyaConfig.getPreferences().registerOnSharedPreferenceChangeListener(changes);
        LocalAvatarStore.preferences().registerOnSharedPreferenceChangeListener(avatarChanges);
        refresh();
    }

    @Override public void onPause() {
        NyaConfig.getPreferences().unregisterOnSharedPreferenceChangeListener(changes);
        LocalAvatarStore.preferences().unregisterOnSharedPreferenceChangeListener(avatarChanges);
        super.onPause();
    }

    @Override public void onFragmentDestroy() {
        NyaConfig.getPreferences().unregisterOnSharedPreferenceChangeListener(changes);
        LocalAvatarStore.preferences().unregisterOnSharedPreferenceChangeListener(avatarChanges);
        super.onFragmentDestroy();
    }

    private void open(long peer) {
        open(peer, false);
    }

    private void open(long peer, boolean edit) {
        if (!validOwner() || opening) return;
        opening = true;
        AndroidUtilities.hideKeyboard(search);
        var controller = getMessagesController();
        var storage = getMessagesStorage();
        TLRPC.User cachedUser = peer > 0 ? controller.getUser(peer) : null;
        TLRPC.Chat cachedChat = peer < 0 ? controller.getChat(-peer) : null;
        storage.getStorageQueue().postRunnable(() -> {
            TLRPC.User user = peer > 0 && cachedUser == null ? storage.getUser(peer) : cachedUser;
            TLRPC.Chat chat = peer < 0 && cachedChat == null ? storage.getChat(-peer) : cachedChat;
            AndroidUtilities.runOnUIThread(() -> {
                opening = false;
                if (!validOwner() || isPaused() || getParentActivity() == null) return;
                if (user == null && chat == null || user != null && UserObject.isDeleted(user)) {
                    Toast.makeText(getParentActivity(), LocaleController.getString(R.string.NullgramAliasUnavailable), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (user != null) controller.putUser(user, true);
                if (chat != null) controller.putChat(chat, true);
                if (edit) { LocalAliasEditor.show(this, currentAccount, peer); return; }
                Bundle args = new Bundle();
                args.putLong(peer > 0 ? "user_id" : "chat_id", Math.abs(peer));
                if (controller.checkCanOpenChat(args, this)) {
                    ChatActivity destination = new ChatActivity(args);
                    destination.setCurrentAccount(currentAccount);
                    presentFragment(destination);
                }
            });
        });
    }
}
