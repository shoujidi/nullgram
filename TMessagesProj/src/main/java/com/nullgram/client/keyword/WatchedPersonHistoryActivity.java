// SPDX-License-Identifier: GPL-3.0-only
package com.nullgram.client.keyword;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.Collections;
import java.util.List;

final class WatchedPersonHistoryActivity extends BaseFragment {
    private static final int CLEAR = 1;
    private SharedPreferences preferences;
    private KeywordReminderStore store;
    private ActionBarMenuItem clearItem;
    private RecyclerListView.SelectionAdapter adapter;
    private long owner;
    private List<WatchedPersonRecord> records = Collections.emptyList();
    private final SharedPreferences.OnSharedPreferenceChangeListener changes = (prefs, key) -> {
        if (KeywordReminderStore.historyKey(owner).equals(key)) refresh();
    };

    @Override public View createView(Context context) {
        store = new KeywordReminderStore(context);
        preferences = context.getSharedPreferences(KeywordReminderStore.PREFERENCES, Context.MODE_PRIVATE);
        owner = getUserConfig().getClientUserId();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.NullgramWatchedHistory));
        clearItem = actionBar.createMenu().addItem(CLEAR, R.drawable.msg_delete);
        clearItem.setContentDescription(LocaleController.getString(R.string.NullgramWatchedClearHistory));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                if (id == CLEAR) confirmClear();
            }
        });

        FrameLayout content = new FrameLayout(context);
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, getResourceProvider()));
        TextView empty = new TextView(context);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        empty.setTextSize(16);
        empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, getResourceProvider()));
        empty.setText(LocaleController.getString(R.string.NullgramWatchedHistoryEmpty));
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));

        RecyclerListView list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setEmptyView(empty);
        adapter = new RecyclerListView.SelectionAdapter() {
            @Override public int getItemCount() { return records.size(); }
            @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                TextDetailSettingsCell cell = new TextDetailSettingsCell(parent.getContext());
                cell.getValueTextView().setMaxLines(3);
                return new RecyclerListView.Holder(cell);
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                WatchedPersonRecord record = records.get(position);
                ((TextDetailSettingsCell) holder.itemView).setTextAndValue(record.name,
                        LocaleController.formatDateTime(record.date, true) + "\n" + record.description(),
                        position < records.size() - 1);
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < records.size() && isCurrentOwner() && getParentActivity() != null) {
                getParentActivity().startActivity(records.get(position).openIntent(context, currentAccount, owner));
            }
        });
        content.addView(list, new FrameLayout.LayoutParams(-1, -1));
        fragmentView = content;
        refresh();
        return fragmentView;
    }

    @Override public void onResume() {
        super.onResume();
        if (preferences == null) return;
        preferences.registerOnSharedPreferenceChangeListener(changes);
        refresh();
    }

    @Override public void onPause() {
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(changes);
        super.onPause();
    }

    private boolean isCurrentOwner() {
        return owner > 0 && owner == getUserConfig().getClientUserId() && getUserConfig().isClientActivated();
    }

    private void refresh() {
        records = isCurrentOwner() ? store.history(owner) : Collections.emptyList();
        adapter.notifyDataSetChanged();
        clearItem.setVisibility(records.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void confirmClear() {
        if (records.isEmpty() || getParentActivity() == null) return;
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.NullgramWatchedClearHistory))
                .setMessage(LocaleController.getString(R.string.NullgramWatchedClearHistoryConfirm))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.Clear), (dialog, which) -> {
                    if (!isCurrentOwner()) return;
                    try {
                        store.clearHistory(owner);
                        refresh();
                    } catch (IllegalStateException error) {
                        Toast.makeText(getParentActivity(), LocaleController.getString(R.string.NullgramKeywordSaveFailed),
                                Toast.LENGTH_SHORT).show();
                    }
                }).create());
    }
}
