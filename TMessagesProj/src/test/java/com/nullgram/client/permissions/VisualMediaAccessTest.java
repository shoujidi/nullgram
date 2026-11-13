package com.nullgram.client.permissions;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.telegram.messenger.UiTestEnvironment;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = UiTestEnvironment.App.class, sdk = 36)
public class VisualMediaAccessTest {
    private static final class Grants extends ContextWrapper {
        final Set<String> granted = new HashSet<>();

        Grants(Context context) { super(context); }

        @Override public int checkSelfPermission(String permission) {
            return granted.contains(permission) ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
        }
    }

    @Test public void selectedMediaWorksWithoutFullGrantsAndRevocationIsReflected() {
        Grants context = new Grants(RuntimeEnvironment.getApplication());
        context.granted.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
        int access = VisualMediaAccess.current(context);
        assertTrue(VisualMediaAccess.isLimited(access));
        assertTrue(VisualMediaAccess.canRead(access, true, false));
        assertTrue(VisualMediaAccess.canRead(access, false, true));

        context.granted.clear();
        assertFalse(VisualMediaAccess.canRead(VisualMediaAccess.current(context), true, true));
    }

    @Test public void fullPhotoGrantDoesNotImplyVideoAccess() {
        Grants context = new Grants(RuntimeEnvironment.getApplication());
        context.granted.add(Manifest.permission.READ_MEDIA_IMAGES);
        context.granted.add(Manifest.permission.READ_MEDIA_AUDIO);
        int access = VisualMediaAccess.current(context);
        assertTrue(VisualMediaAccess.canRead(access, true, false));
        assertFalse(VisualMediaAccess.canRead(access, false, true));
    }

    @Test public void photoOnlyRequestIncludesSelectedMediaAndExcludesVideo() {
        assertArrayEquals(new String[]{Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},
                VisualMediaAccess.permissionsFor(true, false));
        assertArrayEquals(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},
                VisualMediaAccess.permissionsFor(true, true));
    }

    @Test
    @Config(sdk = 28)
    public void legacyGalleryStillUsesTheLegacyReadGrant() {
        Grants context = new Grants(RuntimeEnvironment.getApplication());
        assertFalse(VisualMediaAccess.canRead(VisualMediaAccess.current(context), true, true));
        context.granted.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        assertTrue(VisualMediaAccess.canRead(VisualMediaAccess.current(context), true, true));
        assertArrayEquals(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                VisualMediaAccess.permissionsFor(true, true));
    }
}
