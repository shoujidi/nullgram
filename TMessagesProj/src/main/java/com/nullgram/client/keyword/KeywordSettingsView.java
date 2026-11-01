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

/** The first row is always the independent, default-off global switch. */
public final class KeywordSettingsView extends LinearLayout {
    private final KeywordReminderController controller;
    private final int account;
    private final Theme.ResourcesProvider theme;
    public KeywordSettingsView(Context context, Theme.ResourcesProvider theme, KeywordReminderController controller, int account) {
        super(context);
        this.controller = controller; this.account = account; this.theme = theme;
        setOrientation(VERTICAL);
        refresh();
    }

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
