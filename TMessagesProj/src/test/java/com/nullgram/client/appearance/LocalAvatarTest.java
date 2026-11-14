package com.nullgram.client.appearance;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.UiTestEnvironment;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import top.nkbe.niagram.config.NyaConfig;
import top.nkbe.niagram.helpers.LocalNameHelper;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = UiTestEnvironment.App.class, sdk = 28,
        shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class})
public class LocalAvatarTest {
    @Before public void setup() {
        NyaConfig.getPreferences().edit().clear().commit();
        LocalAvatarStore.preferences().edit().clear().commit();
        owner(0, 10001); owner(1, 10002);
    }
    private void owner(int account, long id) {
        TLRPC.TL_user user = new TLRPC.TL_user(); user.id = id;
        ReflectionHelpers.setField(UserConfig.getInstance(account), "currentUser", user);
    }
    private File draft(long owner, long peer) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.MAGENTA);
        try { return LocalAvatarStore.writeDraft(owner, peer, bitmap); }
        finally { bitmap.recycle(); }
    }

    @Test public void accountAndPeerIsolationPersistenceAndStaleOwnerProtection() throws Exception {
        File person = draft(10001, 42), group = draft(10001, -42), other = draft(10002, 42);
        assertTrue(LocalAvatarStore.save(0, 10001, 42, person, "Person"));
        assertTrue(LocalAvatarStore.save(0, 10001, -42, group, "Group"));
        assertTrue(LocalAvatarStore.save(1, 10002, 42, other, "Other account"));
        assertEquals(person, LocalAvatarStore.get(0, 42));
        assertEquals(group, LocalAvatarStore.get(0, -42));
        assertEquals(other, LocalAvatarStore.get(1, 42));
        owner(0, 99999);
        assertNull(LocalAvatarStore.get(0, 42));
        assertFalse(LocalAvatarStore.save(0, 10001, 42, null, ""));
        owner(0, 10001);
        assertEquals(person, LocalAvatarStore.get(0, 42));
        Bitmap decoded = BitmapFactory.decodeFile(person.getAbsolutePath());
        assertEquals(512, decoded.getWidth()); assertEquals(512, decoded.getHeight()); decoded.recycle();
        assertFalse("Avatar mappings must not enter synced settings", NyaConfig.getPreferences().getAll().containsKey("10001_42"));
    }

    @Test public void replacementsUseNewCacheKeysRestoreKeepsNameAndMissingFileFallsBack() throws Exception {
        LocalNameHelper.set(0, 42, "Private alias");
        File first = draft(10001, 42), second = draft(10001, 42);
        assertNotEquals(first, second);
        assertTrue(LocalAvatarStore.save(0, 10001, 42, first, "Original"));
        assertTrue(LocalAvatarStore.save(0, 10001, 42, second, "Original"));
        assertFalse(first.exists());
        assertEquals(second, LocalAvatarStore.get(0, 42));
        assertTrue(second.delete());
        assertNull(LocalAvatarStore.get(0, 42));
        assertTrue(LocalAvatarStore.save(0, 10001, 42, null, ""));
        assertEquals("Private alias", LocalNameHelper.get(0, 42));
        assertTrue(LocalAvatarStore.entries(0).isEmpty());
    }

    @Test public void avatarOnlyEntriesAppearInLocalSearchWithoutDuplicateNames() throws Exception {
        assertTrue(LocalAvatarStore.save(0, 10001, -42, draft(10001, -42), "Anime group"));
        assertEquals(-42, LocalAvatarStore.search(0, "anime").get(0).peer());
        assertTrue(LocalAvatarStore.search(1, "").isEmpty());
        LocalNameHelper.set(0, -42, "Local group");
        assertEquals(1, LocalAvatarStore.search(0, "").size());
        assertEquals("Local group", LocalAvatarStore.search(0, "local").get(0).name());
        LocalNameHelper.set(0, -42, null);
        assertEquals("Anime group", LocalAvatarStore.search(0, "anime").get(0).name());
    }

    private static class Receiver extends ImageReceiver {
        String path;
        Receiver() { super(null); setCurrentAccount(0); }
        @Override public void setImage(ImageLocation location, String filter, Drawable thumb, String ext, Object parent, int cacheType) {
            path = location.path;
        }
    }

    @Test public void sharedAvatarEntryUsesLocalImagesForUserBotGroupAndChannelWithoutMutatingServerModels() throws Exception {
        TLRPC.TL_user person = new TLRPC.TL_user(); person.id = 42;
        TLRPC.TL_user bot = new TLRPC.TL_user(); bot.id = 43; bot.bot = true;
        TLRPC.TL_chat group = new TLRPC.TL_chat(); group.id = 42;
        TLRPC.TL_channel channel = new TLRPC.TL_channel(); channel.id = 43;
        Receiver receiver = new Receiver();
        for (org.telegram.tgnet.TLObject object : new org.telegram.tgnet.TLObject[]{person, bot, group, channel}) {
            long peer = LocalAvatarStore.peer(object);
            File file = draft(10001, peer);
            assertTrue(LocalAvatarStore.save(0, 10001, peer, file, "Original"));
            receiver.setForUserOrChat(object, null);
            assertEquals(file.getAbsolutePath(), receiver.path);
        }
        assertNull(person.photo); assertNull(bot.photo); assertNull(group.photo); assertNull(channel.photo);
        assertTrue(LocalAvatarStore.save(0, 10001, 42, null, ""));
        assertFalse(LocalAvatarStore.apply(receiver, 0, person, null, false));
    }

    @Test public void draftCancelAndInvalidFileCannotReplaceOrDeleteSavedAvatar() throws Exception {
        File saved = draft(10001, 42), cancelled = draft(10001, 42);
        assertTrue(LocalAvatarStore.save(0, 10001, 42, saved, "Original"));
        LocalAvatarStore.discard(cancelled);
        assertEquals(saved, LocalAvatarStore.get(0, 42));
        File outside = new File(RuntimeEnvironment.getApplication().getCacheDir(), "unrelated.png");
        try (FileOutputStream stream = new FileOutputStream(outside)) { stream.write(1); }
        assertFalse(LocalAvatarStore.save(0, 10001, 42, outside, "Invalid"));
        LocalAvatarStore.discard(outside);
        assertTrue(outside.exists());
        assertFalse(LocalAvatarStore.save(0, 10001, 10001, saved, "Self"));
    }

    @Test public void sixStaticPresetsRenderAndSelectedPresetSurvivesRepeatedReads() throws Exception {
        Bitmap sheet = Bitmap.createBitmap(768, 512, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        for (int i = 0; i < LocalAliasEditActivity.PRESETS.length; i++) {
            Drawable icon = RuntimeEnvironment.getApplication().getDrawable(LocalAliasEditActivity.PRESETS[i]);
            icon.setBounds((i % 3) * 256, (i / 3) * 256, (i % 3 + 1) * 256, (i / 3 + 1) * 256);
            icon.draw(canvas);
        }
        File output = new File("build/reports/local-avatars/presets.png");
        output.getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(output)) { assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, stream)); }
        assertTrue(LocalAvatarStore.save(0, 10001, 42, LocalAvatarStore.writeDraft(10001, 42, sheet), "Original"));
        File selected = LocalAvatarStore.get(0, 42);
        for (int i = 0; i < 10; i++) assertEquals(selected, LocalAvatarStore.get(0, 42));
        sheet.recycle();
    }
}
