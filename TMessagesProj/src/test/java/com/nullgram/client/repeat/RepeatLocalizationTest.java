package com.nullgram.client.repeat;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UiTestEnvironment;
import org.telegram.utils.localization.Localization;

import java.lang.reflect.Field;
import java.util.Locale;

/** Real LocaleController and packaged language assets; no replacement for string lookup. */
@RunWith(RobolectricTestRunner.class)
@Config(application = UiTestEnvironment.App.class, sdk = {28, 36},
        shadows = {RepeatLocalizationTest.WithoutAccounts.class, UiTestEnvironment.LocalSettings.class})
public class RepeatLocalizationTest {
    @Implements(LocaleController.class)
    public static class WithoutAccounts {
        // Skip account/network startup, then initialize only fields used by the real lookup path.
        @Implementation protected void __constructor__() {}
    }

    private LocaleController previous;
    private LocaleController controller;

    @Before public void setup() {
        previous = ReflectionHelpers.getStaticField(LocaleController.class, "Instance");
        controller = new LocaleController();
        ReflectionHelpers.setField(controller, "localizationExternal", Localization.EMPTY);
        ReflectionHelpers.setField(controller, "localizationInternal", Localization.EMPTY);
        ReflectionHelpers.setStaticField(LocaleController.class, "Instance", controller);
    }

    @After public void cleanup() {
        ReflectionHelpers.setStaticField(LocaleController.class, "Instance", previous);
    }

    @Test public void englishMenuAndDialogLabelsResolveWithUnits() {
        language(Locale.ENGLISH);
        assertEquals("Repeat send", LocaleController.getString(R.string.NullgramRepeatTitle));
        assertEquals("Quick repeat", LocaleController.getString(R.string.NullgramRepeatQuick));
        assertEquals("Repetitions (times)", LocaleController.getString(R.string.NullgramRepeatCount));
        assertEquals("Interval (seconds)", LocaleController.getString(R.string.NullgramRepeatInterval));
        assertEquals("Start sending", LocaleController.getString(R.string.NullgramRepeatStart));
        assertEquals("Cancel", LocaleController.getString(R.string.NullgramRepeatCancel));
        assertEquals("Enter 1–100 repetitions and 0.01–60 seconds.",
                LocaleController.formatString(R.string.NullgramRepeatInvalid, 1, 100, "0.01", "60"));
    }

    @Test public void chineseMenuDialogAndProgressResolve() {
        language(Locale.SIMPLIFIED_CHINESE);
        assertEquals("重复发送", LocaleController.getString(R.string.NullgramRepeatTitle));
        assertEquals("快速复读", LocaleController.getString(R.string.NullgramRepeatQuick));
        assertEquals("重复次数（次）", LocaleController.getString(R.string.NullgramRepeatCount));
        assertEquals("发送间隔（秒）", LocaleController.getString(R.string.NullgramRepeatInterval));
        assertEquals("停止重复", LocaleController.getString(R.string.NullgramRepeatStop));
        assertEquals("请输入 1–100 次，间隔 0.01–60 秒。",
                LocaleController.formatString(R.string.NullgramRepeatInvalid, 1, 100, "0.01", "60"));
        String progress = LocaleController.formatString(R.string.NullgramRepeatProgress,
                "新消息重复发送", 1, 5, 4, 0, 0, 1);
        assertTrue(progress, progress.contains("已提交 1/5"));
        assertTrue(progress, progress.contains("剩余 4"));
        assertFalse(progress, progress.contains("LOC_ERR"));
    }

    @Test public void unsupportedLanguageUsesEnglishFallback() {
        language(Locale.GERMAN);
        assertEquals("Repeat send", LocaleController.getString(R.string.NullgramRepeatTitle));
        assertEquals("Stop repeat", LocaleController.getString(R.string.NullgramRepeatStop));
    }

    @Test public void everyRepeatResourceResolvesAndExistingNamespacesRemainAvailable() throws Exception {
        for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE, Locale.GERMAN}) {
            language(locale);
            int checked = 0;
            for (Field field : R.string.class.getFields()) {
                if (!field.getName().startsWith("NullgramRepeat")) continue;
                String value = LocaleController.getString(field.getInt(null));
                assertNotNull(field.getName(), value);
                assertFalse(field.getName() + "=" + value, value.isEmpty() || value.startsWith("LOC_ERR"));
                checked++;
            }
            assertTrue("Repeat resources must actually be checked", checked >= 17);
            assertFalse(LocaleController.getString(R.string.AppName).startsWith("LOC_ERR"));
            assertFalse(LocaleController.getString(R.string.Chat).startsWith("LOC_ERR"));
            assertFalse(LocaleController.getString(R.string.Repeat).startsWith("LOC_ERR"));
        }
    }

    private void language(Locale locale) {
        ReflectionHelpers.setField(controller, "currentLocale", locale);
    }
}
