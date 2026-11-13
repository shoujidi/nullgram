package com.nullgram.client.appearance;

import static org.junit.Assert.*;
import android.app.Activity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import org.robolectric.util.ReflectionHelpers;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UiTestEnvironment;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.EditTextBoldCursor;
import androidx.recyclerview.widget.RecyclerView;
import top.nkbe.niagram.config.NyaConfig;

@RunWith(RobolectricTestRunner.class)
@Config(application = UiTestEnvironment.App.class, sdk = 28,
        shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class,
                LocalAliasSearchTest.NoNetwork.class})
public class LocalAliasSearchTest {
    @Implements(ConnectionsManager.class)
    public static class NoNetwork {
        @Implementation protected void __constructor__(int account) {
            throw new AssertionError("Local alias searches must not initialize Telegram networking");
        }
    }

    @Test public void realSearchFieldFiltersFullLocalIndexWithoutStartingNetworkOrStorage() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        class Page extends LocalAliasSearchActivity {
            @Override public Activity getParentActivity() { return activity; }
            @Override public MessagesController getMessagesController() {
                throw new AssertionError("Typing aliases must not query Telegram");
            }
            @Override public MessagesStorage getMessagesStorage() {
                throw new AssertionError("Typing aliases must not depend on the recent dialog database");
            }
            void build() { actionBar = createActionBar(activity); createView(activity); }
        }
        TLRPC.TL_user owner = new TLRPC.TL_user(); owner.id = 10001;
        ReflectionHelpers.setField(UserConfig.getInstance(0), "currentUser", owner);
        var prefs = NyaConfig.getPreferences().edit().clear();
        for (int i = 1; i <= 801; i++) prefs.putString("chatNameOverride_10001_" + i, "Alias " + i);
        prefs.putString("userNameOverride_10002_42", "Alias other account").commit();
        Page page = new Page();
        page.setCurrentAccount(0);
        try {
            page.build();
            EditTextBoldCursor input = ReflectionHelpers.getField(page, "search");
            RecyclerView.Adapter<?> adapter = ReflectionHelpers.getField(page, "adapter");
            assertEquals(801, adapter.getItemCount());
            input.setText("aLiAs 801");
            assertEquals(1, adapter.getItemCount());
            input.setText("Alias other account");
            assertEquals(0, adapter.getItemCount());
            input.setText("");
            assertEquals(801, adapter.getItemCount());
            TLRPC.TL_user replacement = new TLRPC.TL_user(); replacement.id = 10002;
            ReflectionHelpers.setField(UserConfig.getInstance(0), "currentUser", replacement);
            input.setText("Alias");
            assertEquals(0, adapter.getItemCount());
        } finally {
            activity.finish();
        }
    }
}
