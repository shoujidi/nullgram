package com.nullgram.client.appearance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowShortcutManager;
import org.telegram.messenger.R;
import org.telegram.ui.LaunchActivity;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = {28, 36})
public class DisguiseDesktopShortcutTest {
    @Test
    public void renamingUpdatesThePinnedEntryWithTheSameLaunchTarget() {
        Context context = RuntimeEnvironment.getApplication();
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        ShadowShortcutManager shadow = Shadow.extract(manager);
        shadow.setIsRequestPinShortcutSupported(true);

        assertEquals(0, DisguiseDesktopShortcut.apply(context, "日常", R.mipmap.ic_launcher_nullgram,
                DisguiseDesktopShortcut.Mode.ADD_IF_MISSING));
        assertEquals(1, manager.getPinnedShortcuts().size());
        String id = manager.getPinnedShortcuts().get(0).getId();
        assertEquals("日常", manager.getPinnedShortcuts().get(0).getShortLabel().toString());

        assertEquals(0, DisguiseDesktopShortcut.apply(context, "备忘录", R.drawable.nullgram_disguise_qq,
                DisguiseDesktopShortcut.Mode.ADD_IF_MISSING));
        assertEquals(1, manager.getPinnedShortcuts().size());
        var renamed = manager.getPinnedShortcuts().get(0);
        assertEquals(id, renamed.getId());
        assertEquals("备忘录", renamed.getShortLabel().toString());
        assertEquals(Intent.ACTION_MAIN, renamed.getIntent().getAction());
        assertEquals(new ComponentName(context, LaunchActivity.class), renamed.getIntent().getComponent());
    }

    @Test
    public void unsupportedLauncherLeavesHomeScreenUntouched() {
        Context context = RuntimeEnvironment.getApplication();
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        ShadowShortcutManager shadow = Shadow.extract(manager);
        shadow.setIsRequestPinShortcutSupported(false);

        assertEquals(R.string.NullgramDisguiseShortcutUnsupported,
                DisguiseDesktopShortcut.apply(context, "日常", R.mipmap.ic_launcher_nullgram,
                        DisguiseDesktopShortcut.Mode.ADD_TO_CURRENT_LAUNCHER));
        assertTrue(manager.getPinnedShortcuts().isEmpty());
    }
}
