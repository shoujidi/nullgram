package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;

public class LauncherIconController {
    private static final LauncherIcon DEFAULT_ICON = LauncherIcon.NULLGRAM;

    private static final String[] RETIRED_ALIASES = {"DisguiseMessages", "DisguiseContacts"};

    public static void tryFixLauncherIconIfNeeded() {
        Context context = ApplicationLoader.applicationContext;
        PackageManager manager = context.getPackageManager();
        for (String alias : RETIRED_ALIASES) {
            if (manager.getComponentEnabledSetting(retiredAlias(context, alias))
                    == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                setIcon(getOriginalIcon());
                return;
            }
        }
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (!icon.isAvailable() && manager.getComponentEnabledSetting(icon.getComponentName(context))
                    == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                setIcon(getOriginalIcon());
                return;
            }
        }
        // An explicit choice takes priority
        // over the newly enabled manifest default after an application update.
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (context.getPackageManager().getComponentEnabledSetting(icon.getComponentName(context))
                    == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                for (LauncherIcon other : LauncherIcon.values()) {
                    if (other != icon && isEnabled(other)) {
                        setIcon(icon);
                        break;
                    }
                }
                return;
            }
        }
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(DEFAULT_ICON);
    }

    private static ComponentName retiredAlias(Context context, String key) {
        return new ComponentName(context.getPackageName(), "org.telegram.messenger." + key);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        if (!icon.isAvailable()) return false;
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == DEFAULT_ICON;
    }

    public static LauncherIcon getCurrentIcon() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return icon;
            }
        }
        return DEFAULT_ICON;
    }

    public static LauncherIcon getOriginalIcon() {
        String key = MessagesController.getGlobalMainSettings()
                .getString("nullgramOriginalIcon", DEFAULT_ICON.name());
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (icon.name().equals(key) && !icon.isDisguise() && icon.isAvailable()) {
                return icon;
            }
        }
        return DEFAULT_ICON;
    }

    public static void setIcon(LauncherIcon icon) {
        if (!icon.isAvailable()) return;
        if (!icon.isDisguise()) {
            MessagesController.getGlobalMainSettings().edit()
                    .putString("nullgramOriginalIcon", icon.name()).apply();
        } else {
            LauncherIcon current = getCurrentIcon();
            if (!current.isDisguise()) {
                MessagesController.getGlobalMainSettings().edit()
                        .putString("nullgramOriginalIcon", current.name()).apply();
            }
        }
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        pm.setComponentEnabledSetting(icon.getComponentName(ctx), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        for (LauncherIcon i : LauncherIcon.values()) {
            if (i != icon) {
                pm.setComponentEnabledSetting(i.getComponentName(ctx), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }
        for (String alias : RETIRED_ALIASES) {
            ComponentName component = retiredAlias(ctx, alias);
            if (pm.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }
        if (LaunchActivity.instance != null) {
            LaunchActivity.instance.updateTaskDescription();
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                MediaDataController.getInstance(a).buildShortcuts();
            }
        }
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.mipmap.icon_9_launcher_background, R.mipmap.icon_9_launcher_foreground, R.string.NullgramNiagramIconDefault, R.mipmap.icon_9_launcher),
        NEKO("NekoIcon", R.mipmap.icon_7_launcher_background, R.mipmap.icon_7_launcher_foreground, R.string.AppIconNeko, R.mipmap.icon_7_launcher),
        NEKO_DARK("NekoDarkIcon", R.mipmap.icon_2_background_sa, R.mipmap.icon_8_launcher_foreground, R.string.AppIconNekoDark, R.mipmap.icon_8_launcher),
        MUSHEEN("MusheenIcon", R.color.ic_launcher_background, R.mipmap.icon_10_launcher_foreground, R.string.AppIconMusheen, R.mipmap.icon_10_launcher),
        SPACE("SpaceIcon", R.mipmap.icon_11_launcher_background, R.mipmap.icon_11_launcher_foreground, R.string.AppIconSpace, R.mipmap.icon_11_launcher),
        CLOUD("CloudIcon", R.color.ic_launcher_background, R.mipmap.icon_12_launcher_foreground, R.string.AppIconCloud, R.mipmap.icon_12_launcher),
        NEON("NeonIcon", R.mipmap.icon_13_launcher_background, R.mipmap.icon_13_launcher_foreground, R.string.AppIconNeon, R.mipmap.icon_13_launcher),
        MATERIAL("MaterialIcon", R.mipmap.icon_14_launcher_background, R.mipmap.icon_14_launcher_foreground, R.string.AppIconMaterial, R.mipmap.icon_14_launcher),
        FORKGRAM("ForkGramIcon", R.mipmap.ic_launcher_fork, 0, R.string.AppIconForkGram, R.mipmap.ic_launcher_fork),
        TELEGRAM("TelegramIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconTelegramOriginal, R.mipmap.ic_launcher_dr),
        VINTAGE("VintageIcon", R.drawable.icon_6_background_sa, R.mipmap.icon_6_foreground_sa, R.string.AppIconVintage, R.mipmap.icon_6_launcher),
        AQUA("AquaIcon", R.drawable.icon_4_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconAqua, R.mipmap.icon_4_launcher),
        PREMIUM("PremiumIcon", R.drawable.icon_3_background_sa, R.mipmap.icon_3_foreground_sa, R.string.AppIconPremium, true, R.mipmap.icon_3_launcher),
        TURBO("TurboIcon", R.drawable.icon_5_background_sa, R.mipmap.icon_5_foreground_sa, R.string.AppIconTurbo, true, R.mipmap.icon_5_launcher),
        NOX("NoxIcon", R.mipmap.icon_2_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconNox, true, R.mipmap.icon_2_launcher),
        NULLGRAM("NullgramIcon", R.drawable.nullgram_icon_background, R.drawable.nullgram_icon_foreground, R.string.NullgramIconDefault, R.mipmap.ic_launcher_nullgram),
        LIGHT("NullgramLightIcon", R.drawable.nullgram_icon_light_background, R.drawable.nullgram_icon_light_foreground, R.string.NullgramIconLight, R.mipmap.ic_launcher_nullgram_light),
        DARK("NullgramDarkIcon", R.drawable.nullgram_icon_dark_background, R.drawable.nullgram_icon_dark_foreground, R.string.NullgramIconDark, R.mipmap.ic_launcher_nullgram_dark),
        PHOTO("NullgramPhotoIcon", R.drawable.nullgram_photo_background, R.drawable.nullgram_photo_foreground, R.string.NullgramIconPhoto, R.mipmap.ic_launcher_nullgram_photo),
        PEACH("NullgramPeachIcon", R.drawable.nullgram_peach_background, R.drawable.nullgram_peach_foreground, R.string.NullgramIconPeach, R.mipmap.ic_launcher_nullgram_peach),
        DISGUISE_QQ("DisguiseQQ", R.drawable.nullgram_disguise_qq, 0, R.string.NullgramDisguiseQQLabel, R.drawable.nullgram_disguise_qq),
        DISGUISE_WECHAT("DisguiseWechat", R.drawable.nullgram_disguise_wechat, 0, R.string.NullgramDisguiseWechatLabel, R.drawable.nullgram_disguise_wechat),
        DISGUISE_SHANDONGTONG("DisguiseShandongtong", R.drawable.nullgram_disguise_shandongtong, 0, R.string.NullgramDisguiseShandongtongLabel, R.drawable.nullgram_disguise_shandongtong);

        public boolean isDisguise() {
            return this == DISGUISE_QQ || this == DISGUISE_WECHAT || this == DISGUISE_SHANDONGTONG;
        }

        public boolean isAvailable() {
            return (!isDisguise() || BuildConfig.NULLGRAM_DISGUISE)
                    && ((this != PHOTO && this != PEACH) || BuildConfig.NULLGRAM_LOCAL_PORTRAIT);
        }

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;
        public final int iconRes;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title, int iconRes) {
            this(key, background, foreground, title, false, iconRes);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium, int iconRes) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
            this.iconRes = iconRes;
        }


    }
}
