package top.nkbe.niagram.helpers;

import android.content.SharedPreferences;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import top.nkbe.niagram.config.NyaConfig;

public final class LocalNameHelper {
    public static final String chatNameOverridePrefix = "chatNameOverride_";
    public static final String userNameOverridePrefix = "userNameOverride_";

    private LocalNameHelper() {}

    private static SharedPreferences preferences() {
        return NyaConfig.getPreferences();
    }

    private static String prefix(long peer) {
        return peer > 0 ? userNameOverridePrefix : chatNameOverridePrefix;
    }

    private static String key(long owner, long peer) {
        return prefix(peer) + owner + "_" + Math.abs(peer);
    }

    public static String get(int account, long peer) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        return owner == 0 || peer == 0 ? null : preferences().getString(key(owner, peer), null);
    }

    public static boolean isAliasKey(String key) {
        return key != null && (key.startsWith(chatNameOverridePrefix) || key.startsWith(userNameOverridePrefix));
    }

    /** Works without a cached Telegram user/chat, including cold-start push notifications. */
    public static String name(int account, long peer, String fallback) {
        String alias = get(account, peer);
        return alias != null ? alias : fallback;
    }

    public static void saveEditedName(int account, long peer, String initialName, String editedName) {
        String initial = initialName == null ? "" : initialName.trim();
        String edited = editedName == null ? "" : editedName.trim();
        if (!initial.equals(edited)) set(account, peer, edited);
    }

    public static void set(int account, long peer, String value) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        if (owner == 0 || peer == 0 || peer == owner) {
            return;
        }
        SharedPreferences.Editor editor = preferences().edit();
        if (value == null || value.trim().isEmpty()) {
            editor.remove(key(owner, peer));
        } else {
            editor.putString(key(owner, peer), value.trim());
        }
        editor.apply();
        refresh(account);
    }

    public static String name(int account, TLRPC.User user) {
        String alias = user == null ? null : get(account, user.id);
        return alias != null ? alias : UserObject.getUserName(user);
    }

    public static String name(int account, TLRPC.Chat chat) {
        if (chat == null) {
            return "";
        }
        String alias = get(account, -chat.id);
        return alias != null ? alias : chat.title;
    }

    public static boolean matches(String alias, String query) {
        return alias != null && alias.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    public record Alias(long peer, String name) {}

    /** Entire account-local alias index; independent of dialog recency, folder and network. */
    public static List<Alias> search(int account, String query) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        List<Alias> result = new ArrayList<>();
        if (owner == 0) return result;
        String search = query == null ? "" : query.trim();
        String users = userNameOverridePrefix + owner + "_";
        String chats = chatNameOverridePrefix + owner + "_";
        for (Map.Entry<String, ?> entry : preferences().getAll().entrySet()) {
            String key = entry.getKey();
            boolean user = key.startsWith(users);
            if (!user && !key.startsWith(chats)) continue;
            if (!(entry.getValue() instanceof String alias) || alias.trim().isEmpty() || !matches(alias, search)) continue;
            try {
                long id = Long.parseLong(key.substring(user ? users.length() : chats.length()));
                if (id <= 0 || user && id == owner) continue;
                long peer = user ? id : -id;
                if (org.telegram.messenger.DialogObject.isEncryptedDialog(peer)) continue;
                result.add(new Alias(peer, alias));
            } catch (NumberFormatException invalidKey) {
                // Ignore corrupt or unrelated preferences without dropping valid entries.
            }
        }
        result.sort(Comparator.comparing(Alias::name, String.CASE_INSENSITIVE_ORDER).thenComparingLong(Alias::peer));
        return result;
    }

    private static boolean isLegacyKey(String key) {
        for (String prefix : new String[]{userNameOverridePrefix, chatNameOverridePrefix}) {
            if (key.startsWith(prefix) && key.substring(prefix.length()).matches("[1-9][0-9]*")) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasLegacyNames() {
        for (String key : preferences().getAll().keySet()) {
            if (isLegacyKey(key)) {
                return true;
            }
        }
        return false;
    }

    public static void importLegacyNames(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        if (owner == 0) {
            return;
        }
        SharedPreferences preferences = preferences();
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String oldKey = entry.getKey();
            if (!isLegacyKey(oldKey) || !(entry.getValue() instanceof String)) {
                continue;
            }
            String prefix = oldKey.startsWith(userNameOverridePrefix) ? userNameOverridePrefix : chatNameOverridePrefix;
            String newKey = prefix + owner + "_" + oldKey.substring(prefix.length());
            if (!preferences.contains(newKey)) {
                editor.putString(newKey, (String) entry.getValue());
            }
            editor.remove(oldKey);
        }
        editor.apply();
        refresh(account);
    }

    public static void clearAllLocalNameOverrides() {
        SharedPreferences preferences = preferences();
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(chatNameOverridePrefix) || key.startsWith(userNameOverridePrefix)) {
                editor.remove(key);
            }
        }
        editor.apply();
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated()) {
                refresh(account);
            }
        }
    }

    private static void refresh(int account) {
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces,
                MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_CHAT_NAME);
    }
}
