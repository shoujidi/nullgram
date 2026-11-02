// SPDX-License-Identifier: GPL-3.0-only
// Nullgram modification, 2026-11-01.
package com.nullgram.client.keyword;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import java.util.List;
import java.util.ArrayList;

/** The first row is always the independent, default-off global switch. */
public final class KeywordSettingsView extends LinearLayout {
    private final KeywordReminderController controller;
    private final int account;
    private Runnable selectPerson;
    private final Theme.ResourcesProvider theme;
    public KeywordSettingsView(Context context, Theme.ResourcesProvider theme, KeywordReminderController controller, int account) {
        super(context);
        this.controller = controller; this.account = account; this.theme = theme;
        setOrientation(VERTICAL);
        refresh();
    }

    public void setPersonPicker(Runnable picker) { selectPerson = picker; }

    public void refresh() {
        removeAllViews();
        KeywordReminderEngine engine = controller.engine();
        TextCheckCell toggle = new TextCheckCell(getContext(), theme);
        toggle.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, theme));
        toggle.setTextAndCheck(LocaleController.getString(R.string.NullgramKeywordEnabled), engine.isEnabled(), false);
        addView(toggle);
        toggle.setOnClickListener(view -> {
            try {
                if (engine.isEnabled()) engine.disable();
                else if (!controller.enableForAccount(account)) showError(R.string.NullgramKeywordLogin);
            } catch (RuntimeException error) { showError(R.string.NullgramKeywordSaveFailed); }
            refresh();
        });
        TextInfoPrivacyCell info = new TextInfoPrivacyCell(getContext());
        String binding = engine.userId() == 0 ? LocaleController.getString(R.string.NullgramKeywordNoAccount)
                : LocaleController.formatString(R.string.NullgramKeywordAccount, engine.userId());
        info.setText(binding + "\n" + LocaleController.getString(R.string.NullgramKeywordInfo));
        addView(info);
        TextCheckCell keywordToggle = new TextCheckCell(getContext(), theme);
        keywordToggle.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, theme));
        keywordToggle.setTextAndCheck(LocaleController.getString(R.string.NullgramReminderKeywords), engine.keywordsEnabled(), true);
        addView(keywordToggle);
        keywordToggle.setOnClickListener(view -> {
            try { engine.setKeywordsEnabled(!engine.keywordsEnabled()); }
            catch (RuntimeException error) { showError(R.string.NullgramKeywordSaveFailed); }
            refresh();
        });
        TextSettingsCell add = row(LocaleController.getString(R.string.NullgramKeywordAdd));
        addView(add);
        add.setOnClickListener(view -> addKeyword());
        for (String keyword : engine.keywords()) {
            TextSettingsCell cell = row(keyword);
            cell.setTextAndValue(keyword, LocaleController.getString(R.string.Delete), true);
            addView(cell);
            cell.setOnClickListener(view -> new AlertDialog.Builder(getContext(), theme)
                    .setTitle(LocaleController.getString(R.string.NullgramKeywordDelete))
                    .setMessage(keyword).setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
                        List<String> values = engine.keywords();
                        values.remove(keyword);
                        save(values);
                    }).show());
        }
        TextSettingsCell addPerson = row(LocaleController.getString(R.string.NullgramReminderAddPerson));
        addView(addPerson);
        addPerson.setOnClickListener(view -> { if (selectPerson != null) selectPerson.run(); });
        long owner = controller.accountUserId(account);
        for (WatchedPerson person : engine.watched(owner)) {
            TextSettingsCell cell = row(person.name);
            StringBuilder detail = new StringBuilder();
            int[] bits = {WatchedPerson.PRIVATE, WatchedPerson.GROUP, WatchedPerson.CHANNEL};
            int[] labels = {R.string.NullgramReminderPrivate, R.string.NullgramReminderGroup, R.string.NullgramReminderChannel};
            for (int i = 0; i < bits.length; i++) if ((person.scopes & bits[i]) != 0) {
                if (detail.length() > 0) detail.append(" · ");
                detail.append(LocaleController.getString(labels[i]));
            }
            if (person.online) {
                if (detail.length() > 0) detail.append(" · ");
                detail.append(LocaleController.getString(R.string.NullgramReminderOnline));
            }
            cell.setTextAndValue(person.name, detail.toString(), true);
            addView(cell);
            cell.setOnClickListener(view -> editPerson(person));
        }
        TextInfoPrivacyCell watchInfo = new TextInfoPrivacyCell(getContext());
        watchInfo.setText(LocaleController.getString(R.string.NullgramReminderWatchInfo));
        addView(watchInfo);
    }

    public void editPerson(WatchedPerson person) {
        boolean[] options = {(person.scopes & WatchedPerson.PRIVATE) != 0,
                (person.scopes & WatchedPerson.GROUP) != 0, (person.scopes & WatchedPerson.CHANNEL) != 0, person.online};
        int[] labels = {R.string.NullgramReminderPrivate, R.string.NullgramReminderGroup,
                R.string.NullgramReminderChannel, R.string.NullgramReminderOnline};
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(VERTICAL);
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextCheckCell check = new TextCheckCell(getContext(), theme);
            check.setTextAndCheck(LocaleController.getString(labels[i]), options[i], i < options.length - 1);
            check.setOnClickListener(view -> { options[index] = !options[index]; check.setChecked(options[index]); });
            content.addView(check);
        }
        AlertDialog dialog = new AlertDialog.Builder(getContext(), theme).setTitle(person.name + " · " + person.id)
                .setView(content).setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setNeutralButton(LocaleController.getString(R.string.Delete), (d, which) -> savePerson(person, null))
                .setPositiveButton(LocaleController.getString(R.string.Save), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            int scopes = (options[0] ? WatchedPerson.PRIVATE : 0) | (options[1] ? WatchedPerson.GROUP : 0)
                    | (options[2] ? WatchedPerson.CHANNEL : 0);
            if (savePerson(person, new WatchedPerson(person.id, person.name, scopes, options[3]))) dialog.dismiss();
        }));
        dialog.show();
    }

    private boolean savePerson(WatchedPerson previous, WatchedPerson replacement) {
        try {
            long owner = controller.accountUserId(account);
            List<WatchedPerson> values = new ArrayList<>(controller.engine().watched(owner));
            values.removeIf(person -> person.id == previous.id);
            if (replacement != null) values.add(replacement);
            controller.engine().setWatched(owner, values);
            refresh(); return true;
        } catch (IllegalArgumentException error) { showError(R.string.NullgramReminderPersonLimit); }
        catch (RuntimeException error) { showError(R.string.NullgramKeywordSaveFailed); }
        return false;
    }

    private void addKeyword() {
        EditTextBoldCursor input = new EditTextBoldCursor(getContext());
        input.setSingleLine(true);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, theme));
        input.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(24), AndroidUtilities.dp(12));
        AlertDialog dialog = new AlertDialog.Builder(getContext(), theme)
                .setTitle(LocaleController.getString(R.string.NullgramKeywordAdd)).setView(input)
                .setMessage(LocaleController.getString(R.string.NullgramKeywordLimits))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(R.string.Add), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            List<String> values = controller.engine().keywords();
            values.add(input.getText().toString());
            if (save(values)) dialog.dismiss();
        }));
        dialog.show();
    }

    private boolean save(List<String> values) {
        try { controller.engine().setKeywords(values); refresh(); return true; }
        catch (IllegalArgumentException error) { showError(R.string.NullgramKeywordLimits); }
        catch (RuntimeException error) { showError(R.string.NullgramKeywordSaveFailed); }
        return false;
    }
    private TextSettingsCell row(String text) {
        TextSettingsCell cell = new TextSettingsCell(getContext());
        cell.setText(text, true);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, theme));
        return cell;
    }
    private void showError(int resource) { Toast.makeText(getContext(), LocaleController.getString(resource), Toast.LENGTH_LONG).show(); }
}
