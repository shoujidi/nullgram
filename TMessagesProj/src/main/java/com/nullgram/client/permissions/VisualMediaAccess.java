// SPDX-License-Identifier: GPL-3.0-only
package com.nullgram.client.permissions;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;

/** Reads the current grant. Android filters MediaStore results to the selected items. */
public final class VisualMediaAccess {
    private static final int PHOTOS = 1;
    private static final int VIDEOS = 2;
    private static final int SELECTED = 4;
    private static final int FULL = PHOTOS | VIDEOS;

    private VisualMediaAccess() {}

    public static int current(Context context) {
        if (context == null) return 0;
        if (Build.VERSION.SDK_INT < 33) {
            return granted(context, Manifest.permission.READ_EXTERNAL_STORAGE) ? FULL : 0;
        }
        int access = 0;
        if (granted(context, Manifest.permission.READ_MEDIA_IMAGES)) access |= PHOTOS;
        if (granted(context, Manifest.permission.READ_MEDIA_VIDEO)) access |= VIDEOS;
        if (Build.VERSION.SDK_INT >= 34 && granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) {
            access |= SELECTED;
        }
        return access;
    }

    public static boolean canRead(int access, boolean photos, boolean videos) {
        return photos && (access & (PHOTOS | SELECTED)) != 0
                || videos && (access & (VIDEOS | SELECTED)) != 0;
    }

    public static boolean isLimited(int access) {
        return (access & SELECTED) != 0 && (access & FULL) != FULL;
    }

    public static String[] permissionsFor(boolean photos, boolean videos) {
        if (Build.VERSION.SDK_INT < 33) return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        ArrayList<String> permissions = new ArrayList<>(3);
        if (photos) permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
        if (videos) permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
        if (Build.VERSION.SDK_INT >= 34) permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
        return permissions.toArray(new String[0]);
    }

    private static boolean granted(Context context, String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }
}
