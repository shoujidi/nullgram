package com.nullgram.client.keyword;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.telegram.messenger.*;
import org.telegram.utils.localization.Localization;
import com.nullgram.client.repeat.RepeatLocalizationTest;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
@Config(application = UiTestEnvironment.App.class, sdk = {28, 36},
        shadows = {RepeatLocalizationTest.WithoutAccounts.class, UiTestEnvironment.LocalSettings.class})
public class NullgramBrandingTest {
    private LocaleController previous, controller;
    @Before public void setup() {
        previous = ReflectionHelpers.getStaticField(LocaleController.class, "Instance");
        controller = new LocaleController();
        ReflectionHelpers.setField(controller, "currentLocale", Locale.ENGLISH);
        ReflectionHelpers.setField(controller, "localizationExternal", Localization.EMPTY);
        ReflectionHelpers.setField(controller, "localizationInternal", Localization.EMPTY);
        ReflectionHelpers.setStaticField(LocaleController.class, "Instance", controller);
    }
    @After public void cleanup() { ReflectionHelpers.setStaticField(LocaleController.class, "Instance", previous); }

    @Test public void actualStringsResolveBrandingKeywordsAndUpstreamAttribution() throws Exception {
        for (Locale locale : new Locale[] {Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE, Locale.GERMAN}) {
            ReflectionHelpers.setField(controller, "currentLocale", locale);
            assertEquals("Nullgram", LocaleController.getString(R.string.AppName));
            assertEquals("Nullgram", LocaleController.getString(R.string.NiagramX));
            for (java.lang.reflect.Field field : R.string.class.getFields()) {
                if (!field.getName().startsWith("Nullgram")) continue;
                String value = LocaleController.getString(field.getInt(null));
                assertFalse(field.getName() + "=" + value, value.isEmpty() || value.startsWith("LOC_ERR"));
            }
            assertTrue(LocaleController.getString(R.string.NullgramSourceNotice).contains("Niagram"));
            assertTrue(LocaleController.getString(R.string.NullgramSourceNotice).contains("GPL"));
        }
        ReflectionHelpers.setField(controller, "currentLocale", Locale.SIMPLIFIED_CHINESE);
        assertEquals("启用关键词提醒", LocaleController.getString(R.string.NullgramKeywordEnabled));
        assertEquals("Null设置", LocaleController.getString(R.string.NekoSettings));
        assertEquals("Null设置", LocaleController.getString(R.string.N_Config));
    }
    @Test public void settingsHintsWelcomeAndPushStatusAreBrandedInAllBundledLanguages() {
        for (String language : new String[]{"en","ar","de","es","es-ES","fa-IR","fr","id-ID","it-IT","ja-JP","ko","nl","pl-PL","pt-BR","ru-RU","tr-TR","uk-UA","vi-VN","zh-CN","zh-TW"}) {
            ReflectionHelpers.setField(controller, "currentLocale", Locale.forLanguageTag(language));
            for (int id : new int[]{R.string.NekoSettings,R.string.N_Config,R.string.CustomTitleHint,
                    R.string.UnifiedPushNeverReceivedNotifications,R.string.UnifiedPushLastReceivedNotification}) {
                String value = LocaleController.getString(id);
                assertTrue(language + ": " + value, value.contains("Null"));
                assertFalse(value, value.contains("Niagram") || value.contains("Neo"));
            }
            assertEquals("Nullgram", LocaleController.getString(R.string.Page1Title));
            assertTrue(LocaleController.getString(R.string.TelegramVersion).startsWith("Nullgram"));
        }
    }
    @Test public void actualMainHeaderMigratesOldDefaultAndKeepsCustomTitle() {
        top.nkbe.niagram.config.ConfigItem title = top.nkbe.niagram.config.NyaConfig.INSTANCE.getCustomTitle();
        top.nkbe.niagram.config.ConfigItem userName = top.nkbe.niagram.config.NyaConfig.INSTANCE.getCustomTitleUserName();
        Object savedTitle = title.value, savedUserName = userName.value;
        try {
            assertEquals("Nullgram", title.defaultValue);
            userName.changed(false);
            for (String oldDefault : new String[]{"Niagram", "", "Nullgram"}) {
                title.changed(oldDefault);
                assertEquals("Nullgram", top.nkbe.niagram.helpers.TypefaceHelper.getTitleText(0).toString());
            }
            title.changed("My chats");
            assertEquals("My chats", top.nkbe.niagram.helpers.TypefaceHelper.getTitleText(0).toString());
            assertTrue(top.nkbe.niagram.utils.AndroidUtil.getVersionText().startsWith("Nullgram v"));
        } finally { title.changed(savedTitle); userName.changed(savedUserName); }
    }
    @Test public void currentDeviceShowsNullgramAndOtherClientsKeepTheirTrueNames() {
        org.telegram.ui.Cells.SessionCell cell = new org.telegram.ui.Cells.SessionCell(ApplicationLoader.applicationContext, 0);
        org.telegram.tgnet.TLRPC.TL_authorization session = new org.telegram.tgnet.TLRPC.TL_authorization();
        session.app_name = "Niagram"; session.app_version = "13.0.3";
        session.device_model = "Android test"; session.platform = "Android"; session.system_version = "test"; session.country = "";
        session.flags = 1;
        cell.setSession(session, false);
        android.widget.TextView detail = ReflectionHelpers.getField(cell,"detailTextView");
        assertEquals("Nullgram 13.0.3", detail.getText().toString());
        session.flags = 0;
        cell.setSession(session, false);
        assertEquals("Niagram 13.0.3", detail.getText().toString());
    }
    @Test public void packagedLicenseAndIndependentIconsAreReadable() throws Exception {
        try (InputStream input = ApplicationLoader.applicationContext.getAssets().open("nullgram/LICENSE.txt")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] chunk = new byte[4096]; int count;
            while ((count = input.read(chunk)) != -1) bytes.write(chunk, 0, count);
            String text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            assertTrue(text.contains("GNU GENERAL PUBLIC LICENSE")); assertTrue(text.contains("Version 3"));
        }
        assertNotNull(ApplicationLoader.applicationContext.getDrawable(R.mipmap.ic_launcher_nullgram));
        assertNotNull(ApplicationLoader.applicationContext.getDrawable(R.drawable.nullgram_notification));
        assertNotNull(ApplicationLoader.applicationContext.getDrawable(R.mipmap.icon_9_launcher));
        assertNotNull(ApplicationLoader.applicationContext.getDrawable(R.mipmap.icon_9_launcher_round));
        assertNotNull(ApplicationLoader.applicationContext.getDrawable(R.drawable.ic_launcher_dr));
    }
    @Test public void restoredUpstreamSelectionSurvivesAndEveryIconSwitches() {
        android.content.Context context = ApplicationLoader.applicationContext;
        android.content.pm.PackageManager manager = context.getPackageManager();
        android.content.ComponentName legacy = new android.content.ComponentName(context.getPackageName(), "org.telegram.messenger.SpaceIcon");
        for (org.telegram.ui.LauncherIconController.LauncherIcon icon : org.telegram.ui.LauncherIconController.LauncherIcon.values()) {
            manager.setComponentEnabledSetting(icon.getComponentName(context), android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 1);
        }
        manager.setComponentEnabledSetting(legacy, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 1);
        org.telegram.ui.LauncherIconController.tryFixLauncherIconIfNeeded();
        assertEquals(org.telegram.ui.LauncherIconController.LauncherIcon.SPACE, org.telegram.ui.LauncherIconController.getCurrentIcon());
        assertEquals(android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, manager.getComponentEnabledSetting(legacy));
        assertEquals(19, org.telegram.ui.LauncherIconController.LauncherIcon.values().length);
        for (org.telegram.ui.LauncherIconController.LauncherIcon icon : org.telegram.ui.LauncherIconController.LauncherIcon.values()) {
            org.telegram.ui.LauncherIconController.setIcon(icon);
            assertEquals(icon, org.telegram.ui.LauncherIconController.getCurrentIcon());
            assertNotNull(context.getDrawable(icon.iconRes));
            for (org.telegram.ui.LauncherIconController.LauncherIcon other : org.telegram.ui.LauncherIconController.LauncherIcon.values()) {
                assertEquals(other == icon, org.telegram.ui.LauncherIconController.isEnabled(other));
            }
        }
    }
    @Test public void missingLauncherSelectionRepairsToBlueDefault() {
        android.content.Context context = ApplicationLoader.applicationContext;
        android.content.pm.PackageManager manager = context.getPackageManager();
        for (org.telegram.ui.LauncherIconController.LauncherIcon icon : org.telegram.ui.LauncherIconController.LauncherIcon.values()) {
            manager.setComponentEnabledSetting(icon.getComponentName(context), android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 1);
        }
        org.telegram.ui.LauncherIconController.tryFixLauncherIconIfNeeded();
        assertEquals(org.telegram.ui.LauncherIconController.LauncherIcon.NULLGRAM, org.telegram.ui.LauncherIconController.getCurrentIcon());
        assertEquals(R.mipmap.ic_launcher_nullgram, org.telegram.ui.LauncherIconController.LauncherIcon.NULLGRAM.iconRes);
        assertEquals("NullgramIcon", org.telegram.ui.LauncherIconController.LauncherIcon.NULLGRAM.key);
    }

    @Test public void freshInstallHasExactlyOneBlueLauncherAndPreservesExplicitIconChoice() throws Exception {
        android.content.Context context = ApplicationLoader.applicationContext;
        android.content.pm.PackageManager manager = context.getPackageManager();
        for (org.telegram.ui.LauncherIconController.LauncherIcon icon : org.telegram.ui.LauncherIconController.LauncherIcon.values()) {
            manager.setComponentEnabledSetting(icon.getComponentName(context), android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 1);
            boolean enabledInManifest = manager.getActivityInfo(icon.getComponentName(context), android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS).enabled;
            assertEquals(icon == org.telegram.ui.LauncherIconController.LauncherIcon.NULLGRAM, enabledInManifest);
            assertEquals(enabledInManifest, org.telegram.ui.LauncherIconController.isEnabled(icon));
        }
        assertEquals(org.telegram.ui.LauncherIconController.LauncherIcon.NULLGRAM, org.telegram.ui.LauncherIconController.getCurrentIcon());
        assertEquals(R.mipmap.ic_launcher_nullgram, manager.getApplicationInfo(context.getPackageName(), 0).icon);
        org.telegram.ui.LauncherIconController.LauncherIcon explicitChoice = org.telegram.ui.LauncherIconController.LauncherIcon.PHOTO;
        manager.setComponentEnabledSetting(explicitChoice.getComponentName(context), android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 1);
        org.telegram.ui.LauncherIconController.tryFixLauncherIconIfNeeded();
        assertEquals(explicitChoice, org.telegram.ui.LauncherIconController.getCurrentIcon());
        for (org.telegram.ui.LauncherIconController.LauncherIcon icon : org.telegram.ui.LauncherIconController.LauncherIcon.values()) {
            assertEquals(icon == explicitChoice, org.telegram.ui.LauncherIconController.isEnabled(icon));
        }
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void portraitIsOnlySmallLowQualityArtworkWithCopyrightNotice() throws Exception {
        android.content.Context context = ApplicationLoader.applicationContext;
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeResource(context.getResources(), R.drawable.nullgram_avatar_thumbnail, bounds);
        assertEquals(96, bounds.outWidth);
        assertEquals(96, bounds.outHeight);
        assertEquals("image/jpeg", bounds.outMimeType);
        try (InputStream input = context.getResources().openRawResource(R.drawable.nullgram_avatar_thumbnail)) {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int count;
            while ((count = input.read(buffer)) != -1) data.write(buffer, 0, count);
            assertTrue(data.size() < 4096);
        }
        try (InputStream input = context.getAssets().open("nullgram/AVATAR_COPYRIGHT.txt")) {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024]; int count;
            while ((count = input.read(buffer)) != -1) data.write(buffer, 0, count);
            String notice = new String(data.toByteArray(), StandardCharsets.UTF_8);
            assertTrue(notice.contains("版权"));
            assertTrue(notice.contains("96"));
        }
        for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE}) {
            ReflectionHelpers.setField(controller, "currentLocale", locale);
            String sourceNotice = LocaleController.getString(R.string.NullgramSourceNotice);
            assertTrue(sourceNotice, sourceNotice.contains("96"));
            assertFalse(LocaleController.getString(R.string.NullgramIconPhoto).startsWith("LOC_ERR"));
        }
    }
}
