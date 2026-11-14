package com.nullgram.client.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LauncherIconController;
import org.telegram.ui.LauncherIconController.LauncherIcon;

import java.util.Arrays;
import java.util.function.Consumer;

import top.nkbe.niagram.helpers.LocalNameHelper;
import top.nkbe.niagram.helpers.TypefaceHelper;

public final class DisguiseSettingsActivity extends BaseFragment {
    private static final String LABEL_PREFERENCE = "nullgramDisguiseLabel";
    private static final String CUSTOM_NAME_PREFERENCE = "nullgramDisguiseCustomName";
    private static final LauncherIcon[] CHOICES = {
            LauncherIcon.DISGUISE_QQ, LauncherIcon.DISGUISE_WECHAT, LauncherIcon.DISGUISE_SHANDONGTONG
    };

    private TextSettingsCell presetRow;
    private TextSettingsCell iconRow;
    private TextSettingsCell nameRow;
    private TextSettingsCell themeRow;
    private TextSettingsCell chatThemeRow;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getString(R.string.NullgramDisguise));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scroll = new ScrollView(context);
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        scroll.addView(rows);

        presetRow = addRow(rows, R.string.NullgramDisguisePreset, this::choosePreset);
        addHint(rows, R.string.NullgramDisguiseThemeHint);
        addSection(rows, R.string.NullgramDisguiseIndividual);
        iconRow = addRow(rows, R.string.NullgramDisguiseIcons, () ->
                choose(R.string.NullgramDisguiseIcons, true, icon -> {
                    applyIcon(icon);
                    updateRows();
                    updateDesktopShortcut(DisguiseDesktopShortcut.Mode.UPDATE_ONLY);
                }));
        nameRow = addRow(rows, R.string.NullgramDisguiseName, () ->
                choose(R.string.NullgramDisguiseName, false, icon -> {
                    applyName(icon, null);
                    updateDesktopShortcut(DisguiseDesktopShortcut.Mode.UPDATE_ONLY);
                }));
        themeRow = addRow(rows, R.string.NullgramDisguiseListTheme, this::chooseListTheme);
        updateThemeRow();
        chatThemeRow = addRow(rows, R.string.NullgramDisguiseChatTheme, this::chooseChatTheme);
        updateChatThemeRow();
        addHint(rows, R.string.NullgramDisguiseScopeHint);
        addRow(rows, R.string.NullgramDisguiseAddToHome, () ->
                updateDesktopShortcut(DisguiseDesktopShortcut.Mode.ADD_TO_CURRENT_LAUNCHER));
        addHint(rows, R.string.NullgramDisguiseDesktopHint);
        addSection(rows, R.string.NullgramLocalAlias);
        addRow(rows, R.string.NullgramAliasManage, () -> {
            LocalAliasSearchActivity search = new LocalAliasSearchActivity(true);
            search.setCurrentAccount(currentAccount);
            presentFragment(search);
        });
        addHint(rows, R.string.NullgramDisguiseAliasScope);
        addSection(rows, R.string.Reset);
        addRow(rows, R.string.NullgramDisguiseReset, this::resetAppearance);
        updateRows();
        fragmentView = scroll;
        return scroll;
    }

    private TextSettingsCell addRow(LinearLayout rows, int label, Runnable onClick) {
        TextSettingsCell cell = new TextSettingsCell(rows.getContext(), getResourceProvider());
        cell.setText(getString(label), true);
        cell.setBackground(Theme.getSelectorDrawable(false));
        cell.setOnClickListener(view -> onClick.run());
        rows.addView(cell);
        return cell;
    }

    private void addSection(LinearLayout rows, int title) {
        HeaderCell header = new HeaderCell(rows.getContext(), getResourceProvider());
        header.setText(getString(title));
        rows.addView(header);
    }

    private void addHint(LinearLayout rows, int text) {
        TextView hint = new TextView(rows.getContext());
        hint.setText(getString(text));
        hint.setTextSize(13);
        hint.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setPadding(dp(20), dp(8), dp(20), dp(12));
        rows.addView(hint);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
    }

    private void choosePreset() {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(getString(R.string.NullgramDisguisePreset))
                .setItems(new CharSequence[]{"QQ"}, (dialog, index) -> chooseQqParts())
                .setNegativeButton(getString(R.string.Cancel), null).create());
    }

    private void chooseQqParts() {
        LinearLayout parts = new LinearLayout(getParentActivity());
        parts.setOrientation(LinearLayout.VERTICAL);
        int[] labels = {R.string.NullgramDisguiseIcons, R.string.NullgramDisguiseName,
                R.string.NullgramDisguiseListTheme, R.string.NullgramDisguiseChatTheme};
        boolean[] selected = {true, true, true, true};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            CheckBoxCell cell = new CheckBoxCell(getParentActivity(), 1, getResourceProvider());
            cell.setText(getString(labels[i]), "", true, i < labels.length - 1);
            cell.setOnClickListener(view -> { selected[index] = !selected[index]; cell.setChecked(selected[index], true); });
            parts.addView(cell);
        }
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider()).setTitle("QQ")
                .setView(parts).setPositiveButton(getString(R.string.ApplyTheme), (dialog, which) -> {
                    if (!selected[0] && !selected[1] && !selected[2] && !selected[3]) return;
                    if (selected[0]) applyIcon(LauncherIcon.DISGUISE_QQ);
                    DisguiseThemeConfig.applyQq(MessagesController.getGlobalMainSettings(), selected[1], selected[2], selected[3]);
                    updateRows();
                    if (LaunchActivity.instance != null) LaunchActivity.instance.updateTaskDescription();
                    if (selected[0] || selected[1]) updateDesktopShortcut(DisguiseDesktopShortcut.Mode.UPDATE_ONLY);
                    if ((selected[2] || selected[3]) && getParentLayout() != null) getParentLayout().rebuildFragments(0);
                }).setNegativeButton(getString(R.string.Cancel), null).create());
    }

    private void updateRows() {
        updateNameRow(); updateThemeRow(); updateChatThemeRow();
        LauncherIcon icon = LauncherIconController.getCurrentIcon();
        if (iconRow != null) iconRow.setTextAndValue(getString(R.string.NullgramDisguiseIcons), getString(icon.title), true);
        if (presetRow != null) {
            var settings = MessagesController.getGlobalMainSettings();
            String value = DisguiseThemeConfig.completeQq(settings, icon == LauncherIcon.DISGUISE_QQ) ? "QQ"
                    : DisguiseThemeConfig.original(settings, icon.isDisguise()) ? getString(R.string.NullgramDisguiseListOriginal)
                    : DisguiseThemeConfig.customizedQq(settings) ? getString(R.string.NullgramDisguiseQqCustom)
                    : getString(R.string.NullgramDisguiseCustom);
            presetRow.setTextAndValue(getString(R.string.NullgramDisguisePreset), value, true);
        }
    }

    private void chooseListTheme() {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(getString(R.string.NullgramDisguiseListTheme))
                .setItems(new CharSequence[]{getString(R.string.NullgramDisguiseListOriginal), "QQ"},
                        (dialog, index) -> applyListTheme(index == 1 ? DisguiseListTheme.QQ : DisguiseListTheme.ORIGINAL))
                .setNegativeButton(getString(R.string.Cancel), null).create());
    }

    private void chooseChatTheme() {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(getString(R.string.NullgramDisguiseChatTheme))
                .setItems(new CharSequence[]{getString(R.string.NullgramDisguiseListOriginal), "QQ 9.2.15"},
                        (dialog, index) -> {
                            DisguiseChatTheme.set(index == 1 ? DisguiseListTheme.QQ : DisguiseListTheme.ORIGINAL);
                            updateChatThemeRow();
                            updateRows();
                            if (getParentLayout() != null) getParentLayout().rebuildFragments(0);
                        })
                .setNegativeButton(getString(R.string.Cancel), null).create());
    }

    private void updateChatThemeRow() {
        if (chatThemeRow != null) chatThemeRow.setTextAndValue(getString(R.string.NullgramDisguiseChatTheme),
                DisguiseChatTheme.isEnabled() ? "QQ 9.2.15" : getString(R.string.NullgramDisguiseListOriginal), true);
    }

    private void applyListTheme(String theme) {
        DisguiseListTheme.set(theme);
        updateThemeRow();
        updateRows();
        // Recreate underlying pages on return; keep this settings page and user data.
        if (getParentLayout() != null) getParentLayout().rebuildFragments(0);
    }

    private void updateThemeRow() {
        if (themeRow != null) themeRow.setTextAndValue(getString(R.string.NullgramDisguiseListTheme),
                DisguiseListTheme.isQq() ? "QQ" : getString(R.string.NullgramDisguiseListOriginal), true);
    }

    private void choose(int title, boolean showIcons, Consumer<LauncherIcon> onSelect) {
        CharSequence[] names = new CharSequence[CHOICES.length + 1];
        names[0] = getString(showIcons ? R.string.NullgramDisguiseRestoreIcon : R.string.NullgramDisguiseRestoreName);
        for (int i = 0; i < CHOICES.length; i++) {
            names[i + 1] = getString(CHOICES[i].title);
        }
        Context context = getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context, getResourceProvider())
                .setTitle(getString(title))
                .setNegativeButton(getString(R.string.Cancel), null);
        if (!showIcons) {
            CharSequence[] nameOptions = Arrays.copyOf(names, names.length + 1);
            nameOptions[names.length] = getString(R.string.NullgramDisguiseCustomName);
            showDialog(builder.setItems(nameOptions, (dialog, index) -> {
                if (index == names.length) {
                    editCustomName();
                } else {
                    onSelect.accept(index == 0 ? null : CHOICES[index - 1]);
                }
            }).create());
            return;
        }

        LinearLayout options = new LinearLayout(context);
        options.setOrientation(LinearLayout.VERTICAL);
        AlertDialog dialog = builder.setView(options).create();
        for (int i = 0; i < names.length; i++) {
            LauncherIcon choice = i == 0 ? null : CHOICES[i - 1];
            LauncherIcon preview = choice;
            if (preview == null) {
                LauncherIcon current = LauncherIconController.getCurrentIcon();
                preview = current.isDisguise() ? LauncherIconController.getOriginalIcon() : current;
            }
            TextView row = new TextView(context);
            row.setText(names[i]);
            row.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            row.setTextSize(16);
            row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            row.setMinHeight(dp(48));
            row.setPaddingRelative(dp(24), dp(6), dp(24), dp(6));
            row.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_dialogButtonSelector), 2));
            Drawable image = context.getDrawable(preview.iconRes);
            if (image != null) {
                image.setBounds(0, 0, dp(32), dp(32));
                row.setCompoundDrawablesRelative(image, null, null, null);
                row.setCompoundDrawablePadding(dp(16));
            }
            row.setOnClickListener(view -> {
                dialog.dismiss();
                onSelect.accept(choice);
            });
            options.addView(row);
        }
        showDialog(dialog);
    }

    private void editCustomName() {
        Context context = getParentActivity();
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(16);
        input.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        input.setBackground(Theme.createEditTextDrawable(context, true));
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64)});
        input.setText(TypefaceHelper.getTitleText(currentAccount).toString());
        input.setSelection(input.length());

        FrameLayout field = new FrameLayout(context);
        field.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, 0, 22, 0, 22, 0));
        AlertDialog dialog = new AlertDialog.Builder(context, getResourceProvider())
                .setTitle(getString(R.string.NullgramDisguiseCustomName))
                .setView(field)
                .setPositiveButton(getString(R.string.Save), (d, which) -> {
                    String name = input.getText().toString().trim();
                    applyName(null, name);
                    updateDesktopShortcut(name.isEmpty()
                            ? DisguiseDesktopShortcut.Mode.UPDATE_ONLY
                            : DisguiseDesktopShortcut.Mode.ADD_IF_MISSING);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .create();
        dialog.setOnShowListener(ignored -> {
            input.requestFocus();
            AndroidUtilities.showKeyboard(input);
        });
        showDialog(dialog);
    }

    private void applyName(LauncherIcon icon, String customName) {
        var editor = MessagesController.getGlobalMainSettings().edit()
                .remove("nullgramDisguiseName")
                .remove(LABEL_PREFERENCE)
                .remove(CUSTOM_NAME_PREFERENCE);
        if (icon != null) {
            editor.putString(LABEL_PREFERENCE, icon.name());
        } else if (customName != null && !customName.isEmpty()) {
            editor.putString(CUSTOM_NAME_PREFERENCE, customName);
        }
        editor.apply();
        updateRows();
        if (LaunchActivity.instance != null) {
            LaunchActivity.instance.updateTaskDescription();
        }
    }

    private void updateNameRow() {
        if (nameRow != null) {
            nameRow.setTextAndValue(getString(R.string.NullgramDisguiseName), getShortcutName(), true);
        }
    }

    private String getShortcutName() {
        // Keep the existing custom title/nickname fallback when disguise naming is cleared.
        return TypefaceHelper.getTitleText(currentAccount).toString();
    }

    private void updateDesktopShortcut(DisguiseDesktopShortcut.Mode mode) {
        Context activity = getParentActivity();
        if (activity == null) return;
        Context context = activity.getApplicationContext();
        String name = getShortcutName();
        int icon = LauncherIconController.getCurrentIcon().iconRes;
        Utilities.globalQueue.postRunnable(() -> {
            int error = DisguiseDesktopShortcut.apply(context, name, icon, mode);
            if (error != 0) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (!isFinished && !isPaused()) {
                        Toast.makeText(context, getString(error), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    public static String getDisplayName() {
        if (!BuildConfig.NULLGRAM_DISGUISE) return null;
        var settings = MessagesController.getGlobalMainSettings();
        String customName = settings.getString(CUSTOM_NAME_PREFERENCE, null);
        if (customName != null && !customName.isEmpty()) {
            return customName;
        }
        String name = settings.getString(LABEL_PREFERENCE, null);
        for (LauncherIcon icon : CHOICES) {
            if (icon.name().equals(name)) {
                return getString(icon.title);
            }
        }
        return null;
    }

    private void applyIcon(LauncherIcon icon) {
        LauncherIcon current = LauncherIconController.getCurrentIcon();
        if (icon == null) {
            if (!current.isDisguise()) {
                return;
            }
            icon = LauncherIconController.getOriginalIcon();
        }
        if (current != icon) {
            LauncherIconController.setIcon(icon);
        }
    }

    private void resetAppearance() {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(getString(R.string.NullgramDisguiseReset))
                .setMessage(getString(R.string.NullgramDisguiseResetHint))
                .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                    applyIcon(null);
                    DisguiseThemeConfig.reset(MessagesController.getGlobalMainSettings());
                    updateRows();
                    if (LaunchActivity.instance != null) LaunchActivity.instance.updateTaskDescription();
                    updateDesktopShortcut(DisguiseDesktopShortcut.Mode.UPDATE_ONLY);
                    if (getParentLayout() != null) getParentLayout().rebuildFragments(0);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .create());
    }
}
