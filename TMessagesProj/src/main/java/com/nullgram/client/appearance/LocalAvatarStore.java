package com.nullgram.client.appearance;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local display only. Separate preferences never enter Telegram/cloud settings sync. */
public final class LocalAvatarStore {
    public static final int CHANGE_MASK = 1 << 29;
    private LocalAvatarStore() {}

    public record Entry(long peer, String label, File file) {}

    public static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nullgram_local_avatars", Context.MODE_PRIVATE);
    }

    public static long owner(int account) { return UserConfig.getInstance(account).getClientUserId(); }
    private static String key(long owner, long peer) { return owner + "_" + peer; }
    private static File directory() { return new File(ApplicationLoader.applicationContext.getFilesDir(), "local_avatars"); }
    public static boolean validPeer(long owner, long peer) {
        return owner > 0 && peer != 0 && peer != owner && !DialogObject.isEncryptedDialog(peer);
    }

    private static Entry read(long owner, long peer) {
        if (!validPeer(owner, peer)) return null;
        try {
            String value = preferences().getString(key(owner, peer), null);
            if (value == null) return null;
            JSONObject json = new JSONObject(value);
            String name = json.getString("file");
            if (!name.matches(owner + "_" + peer + "_[a-f0-9-]+\\.png")) return null;
            return new Entry(peer, json.optString("label", Long.toString(peer)), new File(directory(), name));
        } catch (Exception invalid) { return null; }
    }

    public static File get(int account, long peer) {
        if (ApplicationLoader.applicationContext == null) return null;
        Entry entry = read(owner(account), peer);
        return entry != null && entry.file().isFile() ? entry.file() : null;
    }

    public static long peer(TLObject object) {
        if (object instanceof TLRPC.User user) return user.id;
        if (object instanceof TLRPC.Chat chat) return -chat.id;
        return 0;
    }

    public static boolean apply(ImageReceiver receiver, int account, TLObject object, Drawable placeholder, boolean big) {
        File file = get(account, peer(object));
        if (file == null) return false;
        // A new immutable filename on every save prevents reuse of the old image cache.
        // setImage cancels the receiver's preceding server image request.
        receiver.setImage(ImageLocation.getForPath(file.getAbsolutePath()), big ? "100_100" : "50_50",
                placeholder, null, object, 0);
        return true;
    }

    /** Call on a worker. Input ownership stays with the caller. */
    public static File writeDraft(long owner, long peer, Bitmap input) throws IOException {
        if (!validPeer(owner, peer) || input == null || input.isRecycled()) throw new IOException("Invalid avatar");
        File dir = directory();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create avatar directory");
        File file = new File(dir, key(owner, peer) + "_" + UUID.randomUUID() + ".png");
        Bitmap scaled = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        try {
            int side = Math.min(input.getWidth(), input.getHeight());
            int x = (input.getWidth() - side) / 2, y = (input.getHeight() - side) / 2;
            new Canvas(scaled).drawBitmap(input, new Rect(x, y, x + side, y + side), new Rect(0, 0, 512, 512),
                    new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            try (FileOutputStream stream = new FileOutputStream(file)) {
                if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IOException("Cannot encode avatar");
                stream.getFD().sync();
            }
            return file;
        } catch (IOException | RuntimeException error) {
            file.delete();
            throw error;
        } finally { scaled.recycle(); }
    }

    /** Publish a complete file, or restore the server avatar when file is null. */
    public static boolean save(int account, long expectedOwner, long peer, File file, String label) {
        if (owner(account) != expectedOwner || !validPeer(expectedOwner, peer)) return false;
        Entry previous = read(expectedOwner, peer);
        try {
            SharedPreferences.Editor editor = preferences().edit();
            if (file == null) editor.remove(key(expectedOwner, peer));
            else {
                if (!file.isFile() || !file.getCanonicalFile().getParentFile().equals(directory().getCanonicalFile())
                        || !file.getName().matches(expectedOwner + "_" + peer + "_[a-f0-9-]+\\.png")) return false;
                JSONObject value = new JSONObject().put("file", file.getName()).put("label", label);
                editor.putString(key(expectedOwner, peer), value.toString());
            }
            if (!editor.commit()) return false;
            if (previous != null && !previous.file().equals(file)) discard(previous.file());
            return true;
        } catch (Exception error) { return false; }
    }

    public static void refresh(int account) {
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces,
                CHANGE_MASK | MessagesController.UPDATE_MASK_AVATAR | MessagesController.UPDATE_MASK_CHAT_AVATAR);
    }

    public static List<Entry> entries(int account) {
        long owner = owner(account);
        List<Entry> entries = new ArrayList<>();
        if (owner == 0) return entries;
        String prefix = owner + "_";
        for (String key : preferences().getAll().keySet()) {
            if (!key.startsWith(prefix)) continue;
            try {
                Entry entry = read(owner, Long.parseLong(key.substring(prefix.length())));
                if (entry != null) entries.add(entry);
            } catch (NumberFormatException ignored) {}
        }
        return entries;
    }

    public static List<top.nkbe.niagram.helpers.LocalNameHelper.Alias> search(int account, String query) {
        String term = query == null ? "" : query.trim();
        var result = new ArrayList<>(top.nkbe.niagram.helpers.LocalNameHelper.search(account, term));
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (var alias : result) seen.add(alias.peer());
        for (Entry entry : entries(account)) {
            String name = top.nkbe.niagram.helpers.LocalNameHelper.name(account, entry.peer(), entry.label());
            if (seen.add(entry.peer()) && (term.isEmpty()
                    || top.nkbe.niagram.helpers.LocalNameHelper.matches(name, term))) {
                result.add(new top.nkbe.niagram.helpers.LocalNameHelper.Alias(entry.peer(), name));
            }
        }
        result.sort(java.util.Comparator.comparing(top.nkbe.niagram.helpers.LocalNameHelper.Alias::name,
                String.CASE_INSENSITIVE_ORDER).thenComparingLong(top.nkbe.niagram.helpers.LocalNameHelper.Alias::peer));
        return result;
    }

    public static void discard(File draft) {
        if (draft == null) return;
        try {
            if (draft.getCanonicalFile().getParentFile().equals(directory().getCanonicalFile())) draft.delete();
        } catch (IOException ignored) {}
    }
}
