// SPDX-License-Identifier: GPL-3.0-only
// Nullgram modification, 2026-11-01.
package com.nullgram.client;

import android.content.Context;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import java.nio.charset.StandardCharsets;

public final class NullgramAbout {
    private NullgramAbout() { }
    public static void show(Context context, Theme.ResourcesProvider theme) {
        new AlertDialog.Builder(context, theme).setTitle("Nullgram · GPLv3")
                .setMessage(LocaleController.getString(R.string.NullgramSourceNotice))
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .setNegativeButton(LocaleController.getString(R.string.NullgramUpstreamSource),
                        (dialog, which) -> Browser.openUrl(context, "https://github.com/HSSkyBoy/Niagram"))
                .setNeutralButton(LocaleController.getString(R.string.NullgramLicense), (dialog, which) -> {
                    try (java.io.InputStream input = context.getAssets().open("nullgram/LICENSE.txt")) {
                        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                        byte[] chunk = new byte[4096];
                        int count;
                        while ((count = input.read(chunk)) != -1) buffer.write(chunk, 0, count);
                        String text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                        new AlertDialog.Builder(context, theme).setTitle("GNU GPL v3").setMessage(text)
                                .setPositiveButton(LocaleController.getString(R.string.OK), null).show();
                    } catch (java.io.IOException error) {
                        Browser.openUrl(context, "https://www.gnu.org/licenses/gpl-3.0.html");
                    }
                }).show();
    }
}
