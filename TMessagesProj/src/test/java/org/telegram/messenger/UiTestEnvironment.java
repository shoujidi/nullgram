package org.telegram.messenger;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowChoreographer;

import java.util.Locale;

/** JVM substitutes for account initialization and ART access, never for UI or repeat behavior. */
public final class UiTestEnvironment {
    private UiTestEnvironment() {}

    public static class App extends Application {
        @Override public void onCreate() {
            super.onCreate();
            ShadowChoreographer.setPaused(true);
            ApplicationLoader.applicationContext = this;
            ApplicationLoader.applicationHandler = new Handler(Looper.getMainLooper());
            ApplicationLoader.applicationLoaderInstance = new ApplicationLoader() {
                @Override protected boolean isAndroidTestEnv() { return true; }
            };
        }
    }

    @Implements(LocaleController.class)
    public static class LocalStrings {
        // TypefaceHelper needs the current locale, without starting Telegram accounts or JNI.
        @Implementation protected void __constructor__() {}
        @Implementation protected Locale getCurrentLocale() { return Locale.US; }
        @Implementation protected static String getString(int id) {
            return RuntimeEnvironment.getApplication().getString(id);
        }
        @Implementation protected static String getString(String key, int id) {
            return getString(id);
        }
        @Implementation protected static String formatString(int id, Object... args) {
            return String.format(Locale.US, getString(id), args);
        }
    }

    @Implements(MessagesController.class)
    public static class LocalSettings {
        @Implementation protected static SharedPreferences getGlobalMainSettings() {
            return RuntimeEnvironment.getApplication().getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
        }
    }

}
