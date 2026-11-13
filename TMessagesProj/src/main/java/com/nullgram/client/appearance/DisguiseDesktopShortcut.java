package com.nullgram.client.appearance;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.LaunchActivity;

import java.util.Collections;

final class DisguiseDesktopShortcut {
    private static final String ID = "nullgram_disguise_home";

    enum Mode { UPDATE_ONLY, ADD_IF_MISSING, ADD_TO_CURRENT_LAUNCHER }

    private DisguiseDesktopShortcut() {}

    /** Runs on a worker thread. Returns a message resource on failure, or zero. */
    static int apply(Context context, String name, int icon, Mode mode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return unsupported(mode);
        }
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) return unsupported(mode);
        try {
            boolean pinned = false;
            for (ShortcutInfo existing : manager.getPinnedShortcuts()) {
                if (ID.equals(existing.getId())) {
                    pinned = true;
                    break;
                }
            }
            boolean requestPin = mode == Mode.ADD_TO_CURRENT_LAUNCHER
                    || (!pinned && mode == Mode.ADD_IF_MISSING);
            if (!pinned && !requestPin) return 0;

            // Keep the launch target stable when the enabled launcher alias changes.
            Intent intent = new Intent(context, LaunchActivity.class)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ShortcutInfo shortcut = new ShortcutInfo.Builder(context, ID)
                    .setShortLabel(name)
                    .setIcon(Icon.createWithResource(context, icon))
                    .setIntent(intent)
                    .build();
            if (pinned && !manager.updateShortcuts(Collections.singletonList(shortcut))) {
                return R.string.NullgramDisguiseShortcutFailed;
            }
            if (requestPin && (!manager.isRequestPinShortcutSupported()
                    || !manager.requestPinShortcut(shortcut, null))) {
                return R.string.NullgramDisguiseShortcutUnsupported;
            }
            return 0;
        } catch (RuntimeException exception) {
            FileLog.e(exception);
            return R.string.NullgramDisguiseShortcutFailed;
        }
    }

    private static int unsupported(Mode mode) {
        return mode == Mode.UPDATE_ONLY ? 0 : R.string.NullgramDisguiseShortcutUnsupported;
    }
}
