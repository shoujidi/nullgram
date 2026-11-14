package com.nullgram.client.appearance;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.PhotoCropActivity;

import java.io.File;
import java.util.concurrent.ThreadLocalRandom;
import top.nkbe.niagram.helpers.LocalNameHelper;

/** Draft edits are local until Save; neither picker nor cropper has an upload delegate. */
final class LocalAliasEditActivity extends BaseFragment {
    static final int[] PRESETS = {R.drawable.nullgram_avatar_sakura, R.drawable.nullgram_avatar_sora,
            R.drawable.nullgram_avatar_momo, R.drawable.nullgram_avatar_mint,
            R.drawable.nullgram_avatar_violet, R.drawable.nullgram_avatar_silver};
    private static final int PICK_IMAGE = 701;
    private final long peer, owner;
    private final String original, initial;
    private EditTextBoldCursor input;
    private BackupImageView preview;
    private File draft;
    private boolean avatarEdited, restoreName, processing, saving;
    private int requestVersion;

    LocalAliasEditActivity(int account, long peer, String original) {
        setCurrentAccount(account);
        this.peer = peer;
        this.owner = LocalAvatarStore.owner(account);
        this.original = original;
        String alias = LocalNameHelper.get(account, peer);
        initial = alias == null ? original : alias;
    }

    private boolean validOwner() { return !isFinished && owner == LocalAvatarStore.owner(currentAccount); }
    private static String text(int id) { return LocaleController.getString(id); }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.NullgramLocalAlias));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20));
        scroll.addView(content);
        preview = new BackupImageView(context);
        preview.setRoundRadius(AndroidUtilities.dp(48));
        preview.getImageReceiver().setCurrentAccount(currentAccount);
        preview.setContentDescription(text(R.string.NullgramAvatarChange));
        preview.setOnClickListener(v -> chooseAvatar());
        content.addView(preview, LayoutHelper.createLinear(96, 96, Gravity.CENTER_HORIZONTAL));
        addButton(content, R.string.NullgramAvatarChange, this::chooseAvatar);
        TextView hint = new TextView(context);
        hint.setText(LocaleController.formatString(R.string.NullgramLocalAliasHint, original));
        hint.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        hint.setTextSize(14);
        content.addView(hint, LayoutHelper.createLinear(-1, -2, 0, 12, 0, 8));
        input = new EditTextBoldCursor(context);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setText(initial);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Math.max(64, initial.length()))});
        input.setSelection(input.length());
        content.addView(input, LayoutHelper.createLinear(-1, 48));
        addButton(content, R.string.NullgramRestoreName, () -> {
            if (saving) return;
            input.setText(original);
            restoreName = true;
        });
        TextView local = new TextView(context);
        local.setText(text(R.string.NullgramAvatarLocalHint));
        local.setTextSize(14);
        local.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        content.addView(local, LayoutHelper.createLinear(-1, -2, 0, 12, 0, 12));
        addButton(content, R.string.Save, this::save);
        fragmentView = scroll;
        updatePreview();
        return scroll;
    }

    private void addButton(LinearLayout layout, int label, Runnable click) {
        TextView row = new TextView(layout.getContext());
        row.setText(text(label));
        row.setTextSize(16);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
        row.setBackground(Theme.getSelectorDrawable(false));
        row.setOnClickListener(v -> click.run());
        layout.addView(row, LayoutHelper.createLinear(-1, 48));
    }

    private void chooseAvatar() {
        if (!validOwner() || saving) return;
        AndroidUtilities.hideKeyboard(input);
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(text(R.string.NullgramAvatarChange))
                .setItems(new CharSequence[]{text(R.string.NullgramAvatarPresets), text(R.string.NullgramAvatarRandom),
                        text(R.string.NullgramAvatarCustom), text(R.string.NullgramAvatarRestore)}, (dialog, which) -> {
                    if (!validOwner() || saving) return;
                    if (which == 0) choosePreset();
                    else if (which == 1) selectPreset(ThreadLocalRandom.current().nextInt(PRESETS.length));
                    else if (which == 2) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
                                    .addCategory(Intent.CATEGORY_OPENABLE);
                            startActivityForResult(intent, PICK_IMAGE);
                        } catch (Exception error) { error(); }
                    } else {
                        requestVersion++;
                        processing = false;
                        LocalAvatarStore.discard(draft);
                        draft = null;
                        avatarEdited = true;
                        updatePreview();
                    }
                }).create());
    }

    private void choosePreset() {
        LinearLayout grid = new LinearLayout(getParentActivity());
        grid.setOrientation(LinearLayout.VERTICAL);
        AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(text(R.string.NullgramAvatarPresets)).setView(grid)
                .setNegativeButton(text(R.string.Cancel), null).create();
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(getParentActivity());
            grid.addView(line, LayoutHelper.createLinear(-1, 88));
            for (int col = 0; col < 3; col++) {
                final int index = row * 3 + col;
                android.widget.ImageView item = new android.widget.ImageView(getParentActivity());
                item.setImageResource(PRESETS[index]);
                item.setPadding(8, 8, 8, 8);
                item.setContentDescription(text(R.string.NullgramAvatarPresets) + " " + (index + 1));
                item.setOnClickListener(v -> { dialog.dismiss(); selectPreset(index); });
                line.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
            }
        }
        showDialog(dialog);
    }

    private void selectPreset(int index) {
        if (!validOwner() || saving) return;
        Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        Drawable drawable = getParentActivity().getDrawable(PRESETS[index]);
        drawable.setBounds(0, 0, 512, 512);
        drawable.draw(new Canvas(bitmap));
        prepare(bitmap);
    }

    @Override public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_IMAGE || resultCode != Activity.RESULT_OK || data == null || data.getData() == null
                || !validOwner()) return;
        Bundle args = new Bundle();
        args.putParcelable("photoUri", data.getData());
        args.putBoolean("freeform", false);
        PhotoCropActivity crop = new PhotoCropActivity(args);
        crop.setCurrentAccount(currentAccount);
        crop.setDelegate(bitmap -> prepare(bitmap == null ? null : bitmap.copy(Bitmap.Config.ARGB_8888, false)));
        if (!presentFragment(crop)) error();
    }

    private void prepare(Bitmap bitmap) {
        if (bitmap == null) { error(); return; }
        if (!validOwner() || saving) { bitmap.recycle(); return; }
        int version = ++requestVersion;
        processing = true;
        Utilities.globalQueue.postRunnable(() -> {
            File result = null;
            try { result = LocalAvatarStore.writeDraft(owner, peer, bitmap); }
            catch (Exception ignored) {}
            finally { bitmap.recycle(); }
            File ready = result;
            AndroidUtilities.runOnUIThread(() -> {
                if (!validOwner() || version != requestVersion) { LocalAvatarStore.discard(ready); return; }
                processing = false;
                if (ready == null) { error(); return; }
                LocalAvatarStore.discard(draft);
                draft = ready;
                avatarEdited = true;
                updatePreview();
            });
        });
    }

    private void updatePreview() {
        if (preview == null || !validOwner()) return;
        File file = avatarEdited ? draft : LocalAvatarStore.get(currentAccount, peer);
        TLObject object = peer > 0 ? getMessagesController().getUser(peer) : getMessagesController().getChat(-peer);
        AvatarDrawable fallback = new AvatarDrawable();
        fallback.setInfo(peer, original, null);
        if (file != null) preview.setImage(ImageLocation.getForPath(file.getAbsolutePath()), "100_100", fallback, object);
        else if (avatarEdited) {
            preview.setImage(ImageLocation.getForUserOrChat(currentAccount, object, ImageLocation.TYPE_SMALL), "100_100", fallback, object);
        } else preview.setForUserOrChat(object, fallback);
    }

    private void save() {
        if (!validOwner() || saving) return;
        if (processing) {
            Toast.makeText(getParentActivity(), text(R.string.Loading), Toast.LENGTH_SHORT).show();
            return;
        }
        saving = true;
        String edited = input.getText().toString();
        File selected = draft;
        draft = null; // The save job now owns the draft, even if this fragment closes.
        Utilities.globalQueue.postRunnable(() -> {
            boolean saved = !avatarEdited || LocalAvatarStore.save(currentAccount, owner, peer, selected, original);
            if (!saved) LocalAvatarStore.discard(selected);
            AndroidUtilities.runOnUIThread(() -> {
                saving = false;
                if (owner != LocalAvatarStore.owner(currentAccount)) return;
                if (!saved) {
                    avatarEdited = false;
                    if (!isFinished) { updatePreview(); error(); }
                    return;
                }
                if (restoreName && edited.trim().equals(original.trim())) LocalNameHelper.set(currentAccount, peer, null);
                else LocalNameHelper.saveEditedName(currentAccount, peer, initial, edited);
                if (avatarEdited) LocalAvatarStore.refresh(currentAccount);
                if (!isFinished) finishFragment();
            });
        });
    }

    private void error() {
        if (getParentActivity() != null) Toast.makeText(getParentActivity(), text(R.string.NullgramAvatarError), Toast.LENGTH_SHORT).show();
    }

    @Override public void onFragmentDestroy() {
        requestVersion++;
        LocalAvatarStore.discard(draft);
        draft = null;
        super.onFragmentDestroy();
    }
}
