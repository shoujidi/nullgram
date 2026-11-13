package com.nullgram.client.appearance;

import static org.junit.Assert.*;
import android.os.Handler;
import com.google.gson.JsonParser;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.UiTestEnvironment;
import top.nkbe.niagram.config.NyaConfig;
import top.nkbe.niagram.helpers.CloudSettingsHelper;
import top.nkbe.niagram.helpers.SettingsBackupHelper;

@RunWith(RobolectricTestRunner.class)
@Config(application = UiTestEnvironment.App.class, sdk = 36,
        shadows = LocalAliasBackupTest.NoPush.class)
public class LocalAliasBackupTest {
    @Implements(PushListenerController.class)
    public static class NoPush {
        @Implementation protected static void reconcilePushRegistration() {}
    }

    @Before public void prepare() {
        ReflectionHelpers.setStaticField(NyaConfig.class, "preferences", null);
        NyaConfig.getPreferences().edit().clear()
                .putString("userNameOverride_10001_42", "Private person")
                .putString("chatNameOverride_10002_99", "Private other account")
                .putString("chatNameOverride_88", "Legacy private")
                .putString("CustomTitle", "Public custom title").commit();
    }

    @Test public void cloudExportExcludesAllAccountsAndLegacyAliasesButLocalBackupRetainsThem() throws Exception {
        JSONObject cloud = new JSONObject(SettingsBackupHelper.backupSettingsJson(true, 0)).getJSONObject("nkmrcfg");
        JSONObject local = new JSONObject(SettingsBackupHelper.backupSettingsJson(false, 0)).getJSONObject("nkmrcfg");
        for (String key : new String[]{"userNameOverride_10001_42", "chatNameOverride_10002_99", "chatNameOverride_88"}) {
            assertFalse(key, cloud.has(key));
            assertTrue(key, local.has(key));
        }
        assertEquals("Public custom title", cloud.getString("CustomTitle"));
    }

    @Test public void cloudRestoreCannotOverwriteOrReintroduceAliasesAndLocalRestoreStillWorks() throws Exception {
        var config = JsonParser.parseString("{\"nkmrcfg\":{\"userNameOverride_10001_42\":\"Remote replacement\","
                + "\"chatNameOverride_10002_77\":\"Remote new\",\"userNameOverride_99\":\"Remote legacy\","
                + "\"CustomTitle\":\"Restored title\"}}").getAsJsonObject();
        SettingsBackupHelper.importSettings(config, true);
        var prefs = NyaConfig.getPreferences();
        assertEquals("Private person", prefs.getString("userNameOverride_10001_42", null));
        assertFalse(prefs.contains("chatNameOverride_10002_77"));
        assertFalse(prefs.contains("userNameOverride_99"));
        assertEquals("Restored title", prefs.getString("CustomTitle", null));
        SettingsBackupHelper.importSettings(config);
        assertEquals("Remote replacement", prefs.getString("userNameOverride_10001_42", null));
        assertEquals("Remote new", prefs.getString("chatNameOverride_10002_77", null));
    }

    @Test public void aliasChangesDoNotScheduleAutoSyncAndOrdinarySettingsStillDo() {
        CloudSettingsHelper helper = CloudSettingsHelper.getInstance();
        boolean previous = ReflectionHelpers.getField(helper, "autoSync");
        Handler handler = ReflectionHelpers.getField(helper, "handler");
        Runnable job = ReflectionHelpers.getField(helper, "cloudSyncRunnable");
        handler.removeCallbacks(job);
        ReflectionHelpers.setField(helper, "autoSync", true);
        try {
            for (String key : new String[]{"userNameOverride_10001_42", "chatNameOverride_10002_99", "chatNameOverride_88"}) {
                CloudSettingsHelper.listener.onSharedPreferenceChanged(NyaConfig.getPreferences(), key);
                assertFalse(handler.hasCallbacks(job));
            }
            CloudSettingsHelper.listener.onSharedPreferenceChanged(NyaConfig.getPreferences(), "CustomTitle");
            assertTrue(handler.hasCallbacks(job));
        } finally {
            handler.removeCallbacks(job);
            ReflectionHelpers.setField(helper, "autoSync", previous);
        }
    }
}
