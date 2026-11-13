package com.nullgram.client.appearance;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

import top.nkbe.niagram.helpers.LocalNameHelper;

final class LocalAliasPickerActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private record Peer(long id, String name, String detail) {}

    private final ArrayList<Peer> results = new ArrayList<>();
    private long owner;
    private String query = "";
    private RecyclerListView.SelectionAdapter adapter;

    @Override
    public boolean onFragmentCreate() {
        if (!super.onFragmentCreate()) return false;
        owner = getUserConfig().getClientUserId();
        getNotificationCenter().addObserver(this, NotificationCenter.dialogsNeedReload);
        getNotificationCenter().addObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getContactsController().readContacts();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogsNeedReload);
        getNotificationCenter().removeObserver(this, NotificationCenter.contactsDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(getString(R.string.NullgramDisguiseAlias));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        actionBar.createMenu().addItem(1, R.drawable.ic_ab_search_solar).setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override public void onTextChanged(EditText input) {
                        query = input.getText().toString().trim().toLowerCase(Locale.ROOT);
                        refresh();
                    }

                    @Override public void onSearchCollapse() {
                        query = "";
                        refresh();
                        loadMoreDialogs();
                    }
                }).setSearchFieldHint(getString(R.string.Search));

        FrameLayout content = new FrameLayout(context);
        content.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        TextView empty = new TextView(context);
        empty.setText(getString(R.string.NoResult));
        empty.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        empty.setGravity(Gravity.CENTER);
        content.addView(empty, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        RecyclerListView list = new RecyclerListView(context);
        LinearLayoutManager layout = new LinearLayoutManager(context);
        list.setLayoutManager(layout);
        list.setEmptyView(empty);
        adapter = new RecyclerListView.SelectionAdapter() {
            @Override public int getItemCount() { return results.size(); }
            @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }

            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                TextDetailSettingsCell cell = new TextDetailSettingsCell(parent.getContext());
                cell.setMultilineDetail(false);
                return new RecyclerListView.Holder(cell);
            }

            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                Peer peer = results.get(position);
                ((TextDetailSettingsCell) holder.itemView).setTextAndValue(
                        peer.name(), peer.detail(), position + 1 < results.size());
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((view, position) -> {
            var activity = getParentActivity();
            if (activity != null && validOwner() && position >= 0 && position < results.size()) {
                View focused = activity.getCurrentFocus();
                if (focused != null) AndroidUtilities.hideKeyboard(focused);
                LocalAliasEditor.show(this, currentAccount, results.get(position).id());
            }
        });
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && layout.findLastVisibleItemPosition() >= results.size() - 10) {
                    recyclerView.post(LocalAliasPickerActivity.this::loadMoreDialogs);
                }
            }
        });
        content.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = content;
        refresh();
        return content;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
        loadMoreDialogs();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.updateInterfaces
                || ((int) args[0] & (MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_CHAT_NAME)) != 0) {
            refresh();
        }
    }

    private boolean validOwner() {
        return owner != 0 && owner == getUserConfig().getClientUserId();
    }

    private void loadMoreDialogs() {
        if (isFinished || isPaused() || !validOwner() || !query.isEmpty()) return;
        MessagesController controller = getMessagesController();
        for (int folder = 0; folder <= 1; folder++) {
            boolean fromCache = !controller.isDialogsEndReached(folder);
            if (!controller.isLoadingDialogs(folder) && (fromCache || !controller.isServerDialogsEndReached(folder))) {
                controller.loadDialogs(folder, -1, 100, fromCache);
            }
        }
    }

    private void refresh() {
        if (adapter == null) return;
        results.clear();
        if (validOwner()) {
            MessagesController controller = getMessagesController();
            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            for (TLRPC.Dialog dialog : controller.getAllDialogs()) {
                if (DialogObject.isUserDialog(dialog.id) || DialogObject.isChatDialog(dialog.id)) ids.add(dialog.id);
            }
            for (TLRPC.TL_contact contact : getContactsController().contacts) ids.add(contact.user_id);
            ids.remove(owner);
            for (long id : ids) {
                String original;
                String username;
                if (id > 0) {
                    TLRPC.User user = controller.getUser(id);
                    if (user == null || UserObject.isDeleted(user)) continue;
                    original = UserObject.getUserName(user);
                    username = UserObject.getPublicUsername(user);
                } else {
                    TLRPC.Chat chat = controller.getChat(-id);
                    if (chat == null) continue;
                    original = chat.title;
                    username = ChatObject.getPublicUsername(chat);
                }
                String alias = LocalNameHelper.get(currentAccount, id);
                String handle = username == null || username.isEmpty() ? "" : "@" + username;
                if (query.isEmpty() || LocalNameHelper.matches(original, query)
                        || LocalNameHelper.matches(alias, query) || LocalNameHelper.matches(handle, query)) {
                    results.add(new Peer(id, alias == null ? original : alias,
                            alias != null ? original : handle));
                }
            }
        }
        adapter.notifyDataSetChanged();
    }
}
