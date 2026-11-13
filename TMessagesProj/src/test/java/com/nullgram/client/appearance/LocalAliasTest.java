package com.nullgram.client.appearance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.telegram.messenger.UiTestEnvironment;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import top.nkbe.niagram.config.NyaConfig;
import top.nkbe.niagram.helpers.LocalNameHelper;

@RunWith(RobolectricTestRunner.class)
@Config(application = UiTestEnvironment.App.class, sdk = 28)
public class LocalAliasTest {
    @Before
    public void setUp() {
        NyaConfig.getPreferences().edit().clear().commit();
        setOwner(0, 10001);
        setOwner(1, 10002);
    }

    @Test
    public void differentAccountsAndPeerTypesKeepSeparateNames() {
        LocalNameHelper.set(0, 42, "Alice from work");
        LocalNameHelper.set(1, 42, "Alice");
        LocalNameHelper.set(0, -42, "Work group");

        assertEquals("Alice from work", LocalNameHelper.get(0, 42));
        assertEquals("Alice", LocalNameHelper.get(1, 42));
        assertEquals("Work group", LocalNameHelper.get(0, -42));
        assertNull(LocalNameHelper.get(1, -42));
        setOwner(0, 10003);
        assertNull(LocalNameHelper.get(0, 42));
        setOwner(0, 10001);
        assertEquals("Alice from work", LocalNameHelper.get(0, 42));
    }

    @Test
    public void restoreUsesLatestOriginalNameWithoutMutatingPeer() {
        TLRPC.TL_chat chat = new TLRPC.TL_chat();
        chat.id = 42;
        chat.title = "Server title";
        LocalNameHelper.set(0, -42, "  Local name  ");
        assertEquals("Local name", LocalNameHelper.name(0, chat));
        assertEquals("Server title", chat.title);
        chat.title = "New server title";
        assertEquals("Local name", LocalNameHelper.name(0, chat));
        LocalNameHelper.set(0, -42, null);
        assertEquals("New server title", LocalNameHelper.name(0, chat));
    }

    @Test
    public void legacyImportKeepsCurrentAliasesAndAssignsUnownedRecordsOnce() {
        NyaConfig.getPreferences().edit()
                .putString("userNameOverride_42", "Old contact")
                .putString("chatNameOverride_42", "Old group").apply();
        LocalNameHelper.set(0, 42, "New contact");
        assertTrue(LocalNameHelper.hasLegacyNames());
        LocalNameHelper.importLegacyNames(0);
        assertEquals("New contact", LocalNameHelper.get(0, 42));
        assertEquals("Old group", LocalNameHelper.get(0, -42));
        assertNull(LocalNameHelper.get(1, -42));
        assertFalse(LocalNameHelper.hasLegacyNames());
    }

    private static void setOwner(int account, long id) {
        TLRPC.TL_user owner = new TLRPC.TL_user();
        owner.id = id;
        ReflectionHelpers.setField(UserConfig.getInstance(account), "currentUser", owner);
    }

    @Test public void unchangedLegacyEditorDoesNotCreateOrOverwriteAnAlias() {
        LocalNameHelper.saveEditedName(0, -42, "Server title", "Server title");
        LocalNameHelper.saveEditedName(0, 42, "Bot", " Bot ");
        assertNull(LocalNameHelper.get(0, -42));
        assertNull(LocalNameHelper.get(0, 42));
        LocalNameHelper.set(0, -42, "Alias changed in another view");
        LocalNameHelper.saveEditedName(0, -42, "Initial alias", "Initial alias");
        assertEquals("Alias changed in another view", LocalNameHelper.get(0, -42));
        LocalNameHelper.saveEditedName(0, -42, "Initial alias", "Edited alias");
        assertEquals("Edited alias", LocalNameHelper.get(0, -42));
        LocalNameHelper.saveEditedName(0, -42, "Edited alias", "");
        assertNull(LocalNameHelper.get(0, -42));
    }

    @Test public void searchesAllAliasesBeyond600WithoutDialogDatabaseAndKeepsAccountIsolation() {
        var edit = NyaConfig.getPreferences().edit();
        for (int i = 1; i <= 1205; i++) edit.putString("chatNameOverride_10001_" + i, "Group " + i);
        edit.putString("chatNameOverride_10001_1205", "Archived Private ALIAS");
        edit.putString("userNameOverride_10001_42", "Private ALIAS person");
        edit.putString("chatNameOverride_10002_42", "Private ALIAS other account");
        edit.putString("chatNameOverride_99", "Private ALIAS legacy");
        edit.putInt("chatNameOverride_10001_8888", 1);
        edit.putString("chatNameOverride_10001_broken", "Private ALIAS invalid");
        edit.commit();
        assertEquals(1206, LocalNameHelper.search(0, "").size());
        var found = LocalNameHelper.search(0, "  private alias  ");
        assertEquals(2, found.size());
        assertEquals(-1205, found.get(0).peer());
        assertEquals(42, found.get(1).peer());
        assertEquals(1, LocalNameHelper.search(1, "private alias").size());
        setOwner(0, 0);
        assertTrue(LocalNameHelper.search(0, "").isEmpty());
    }

    @Test public void coldNameFallbackUsesStablePeerAndOwnerWithoutCachedModels() {
        LocalNameHelper.set(0, 42, "Contact alias");
        LocalNameHelper.set(0, -42, "Group alias");
        assertEquals("Contact alias", LocalNameHelper.name(0, 42, "Remote person"));
        assertEquals("Group alias", LocalNameHelper.name(0, -42, "Remote group"));
        assertEquals("Remote person", LocalNameHelper.name(1, 42, "Remote person"));
        assertEquals("Unknown", LocalNameHelper.name(0, 99, "Unknown"));
    }
}
