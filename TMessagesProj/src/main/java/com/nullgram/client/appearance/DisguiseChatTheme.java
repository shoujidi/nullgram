package com.nullgram.client.appearance;

import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.SparseIntArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;

/** Fixed light appearance for ordinary chats; never changes a saved Telegram theme. */
public final class DisguiseChatTheme {
    private static final String KEY = "nullgramDisguiseChatTheme";
    public static final int BACKGROUND = 0xffe8ebf0;
    public static final int INCOMING = 0xffffffff;
    public static final int OUTGOING = 0xff0060bb;
    public static final int TEXT = 0xff1a1a1a;
    public static final int SECONDARY = 0xff535d6b;
    public static final int RADIUS_DP = 12;
    public static final int INPUT_BACKGROUND = 0xfff0f0f2;
    public static final int INPUT_BORDER = 0xffb6bec9;
    public static final int SERVICE_ICON = 0xff334155;
    // Darker than QQ's #0099ff so white message text remains readable.
    public static final int SELECTED_OUTGOING = 0xff0058a8;

    private DisguiseChatTheme() {}

    public static boolean isEnabled() {
        return BuildConfig.NULLGRAM_DISGUISE && isEnabled(MessagesController.getGlobalMainSettings(), true);
    }

    public static boolean isEnabled(SharedPreferences preferences, boolean disguise) {
        return disguise && DisguiseListTheme.QQ.equals(preferences.getString(KEY, DisguiseListTheme.ORIGINAL));
    }

    public static void set(String value) { save(MessagesController.getGlobalMainSettings(), value); }

    public static void save(SharedPreferences preferences, String value) {
        if (!DisguiseListTheme.QQ.equals(value) && !DisguiseListTheme.ORIGINAL.equals(value)) {
            throw new IllegalArgumentException("Unknown chat appearance: " + value);
        }
        preferences.edit().putString(KEY, value).apply();
    }

    public static boolean supports(TLRPC.User user, TLRPC.Chat chat, long selfId,
                                   boolean encrypted, boolean thread, boolean preview, int mode) {
        if (encrypted || thread || preview || mode != 0) return false;
        if (user != null) return !user.bot && !user.deleted && user.id != selfId;
        return chat != null && !chat.forum && !chat.monoforum
                && (!ChatObject.isChannel(chat) || chat.megagroup);
    }

    public static final class Palette implements Theme.ResourcesProvider {
        private final SparseIntArray colors = new SparseIntArray();
        private final HashMap<String, Drawable> drawables = new HashMap<>();
        private final HashMap<String, Paint> paints = new HashMap<>();
        public final ColorDrawable wallpaper = new ColorDrawable(BACKGROUND);

        public Palette() {
            put(INCOMING, Theme.key_windowBackgroundWhite, Theme.key_actionBarDefault,
                    Theme.key_actionBarActionModeDefault, Theme.key_chat_inBubble,
                    Theme.key_chat_messageTextOut, Theme.key_chat_messageLinkOut,
                    Theme.key_chat_outReplyNameText, Theme.key_chat_outReplyMessageText,
                    Theme.key_chat_outReplyLine, Theme.key_chat_outSiteNameText);
            put(TEXT, Theme.key_chat_messageTextIn, Theme.key_actionBarDefaultTitle,
                    Theme.key_actionBarDefaultIcon, Theme.key_actionBarActionModeDefaultIcon,
                    Theme.key_chat_messagePanelText, Theme.key_windowBackgroundWhiteBlackText);
            put(OUTGOING, Theme.key_chat_outBubble, Theme.key_chat_messagePanelSend,
                    Theme.key_chat_messagePanelCursor, Theme.key_chat_inReplyLine,
                    Theme.key_chat_inReplyNameText, Theme.key_chat_messageLinkIn,
                    Theme.key_chat_inSiteNameText);
            put(SECONDARY, Theme.key_chat_inTimeText, Theme.key_chat_inTimeSelectedText,
                    Theme.key_actionBarDefaultSubtitle, Theme.key_chat_messagePanelHint,
                    Theme.key_chat_serviceText, Theme.key_chat_inReplyMessageText);
            put(0xffffffff, Theme.key_chat_outTimeText, Theme.key_chat_outTimeSelectedText,
                    Theme.key_chat_outSentCheck, Theme.key_chat_outSentCheckSelected,
                    Theme.key_chat_outSentCheckRead, Theme.key_chat_outSentCheckReadSelected,
                    Theme.key_chat_outSentClock, Theme.key_chat_outSentClockSelected);
            put(TEXT, Theme.key_chat_messagePanelIcons, Theme.key_glass_defaultIcon);
            put(INPUT_BACKGROUND, Theme.key_chat_messagePanelBackground);
            put(0xffd9e7f5, Theme.key_chat_inBubbleSelected);
            put(SELECTED_OUTGOING, Theme.key_chat_outBubbleSelected);
            put(0, Theme.key_chat_inBubbleShadow, Theme.key_chat_outBubbleShadow,
                    Theme.key_chat_outBubbleGradient1, Theme.key_chat_outBubbleGradient2,
                    Theme.key_chat_outBubbleGradient3, Theme.key_chat_outBubbleGradientAnimated,
                    Theme.key_chat_messagePanelShadow, Theme.key_chat_outBubbleGradientSelectedOverlay);

            // Explicit pairs for native media, side-action and status controls.
            put(INCOMING,
                    Theme.key_chat_outForwardedNameText, Theme.key_chat_outViaBotNameText, Theme.key_chat_outReplyLine2,
                    Theme.key_chat_outPreviewLine, Theme.key_chat_outInstant, Theme.key_chat_outInstantSelected,
                    Theme.key_chat_outPreviewInstantText, Theme.key_chat_outAudioTitleText, Theme.key_chat_outAudioPerformerText,
                    Theme.key_chat_outAudioPerformerSelectedText, Theme.key_chat_outAudioDurationText, Theme.key_chat_outAudioDurationSelectedText,
                    Theme.key_chat_outFileNameText, Theme.key_chat_outFileInfoText, Theme.key_chat_outFileInfoSelectedText,
                    Theme.key_chat_outContactNameText, Theme.key_chat_outContactPhoneText, Theme.key_chat_outContactPhoneSelectedText,
                    Theme.key_chat_outVenueInfoText, Theme.key_chat_outVenueInfoSelectedText, Theme.key_chat_outReplyMediaMessageText,
                    Theme.key_chat_outReplyMediaMessageSelectedText, Theme.key_chat_outMenu, Theme.key_chat_outMenuSelected,
                    Theme.key_chat_outVoiceSeekbarFill, Theme.key_chat_outAudioSeekbarFill, Theme.key_chat_outLoader,
                    Theme.key_chat_outLoaderSelected, Theme.key_chat_outReactionButtonText, Theme.key_chat_inReactionButtonTextSelected,
                    Theme.key_chat_outReactionButtonBackground);
            put(OUTGOING,
                    Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inAudioProgress,
                    Theme.key_chat_inAudioSelectedProgress, Theme.key_chat_inAudioSeekbarFill, Theme.key_chat_inVoiceSeekbarFill,
                    Theme.key_chat_inInstant, Theme.key_chat_inInstantSelected, Theme.key_chat_inPreviewInstantText,
                    Theme.key_chat_inForwardedNameText, Theme.key_chat_inViaBotNameText, Theme.key_chat_inPreviewLine,
                    Theme.key_chat_outFileProgress, Theme.key_chat_outFileProgressSelected, Theme.key_chat_outMediaIcon,
                    Theme.key_chat_outMediaIconSelected, Theme.key_chat_serviceLink, Theme.key_chat_inReactionButtonText,
                    Theme.key_chat_outReactionButtonTextSelected, Theme.key_chat_inReactionButtonBackground,
                    Theme.key_chat_outAudioProgress, Theme.key_chat_outAudioSelectedProgress);
            put(TEXT,
                    Theme.key_chat_inFileNameText, Theme.key_chat_inAudioTitleText, Theme.key_chat_inContactNameText,
                    Theme.key_chat_inMenu, Theme.key_chat_inMenuSelected, Theme.key_chat_inAudioPerformerText,
                    Theme.key_chat_inAudioPerformerSelectedText);
            put(SECONDARY,
                    Theme.key_chat_inFileInfoText, Theme.key_chat_inFileInfoSelectedText, Theme.key_chat_inAudioDurationText,
                    Theme.key_chat_inAudioDurationSelectedText, Theme.key_chat_inContactPhoneText, Theme.key_chat_inContactPhoneSelectedText,
                    Theme.key_chat_inVenueInfoText, Theme.key_chat_inVenueInfoSelectedText, Theme.key_chat_inReplyMediaMessageText,
                    Theme.key_chat_inReplyMediaMessageSelectedText, Theme.key_chat_messagePanelHint);
            put(0xff94bde4,
                    Theme.key_chat_outVoiceSeekbar, Theme.key_chat_outVoiceSeekbarSelected, Theme.key_chat_outAudioSeekbar,
                    Theme.key_chat_outAudioCacheSeekbar, Theme.key_chat_outAudioSeekbarSelected);
            put(0xffa5b6c8,
                    Theme.key_chat_inVoiceSeekbar, Theme.key_chat_inVoiceSeekbarSelected, Theme.key_chat_inAudioSeekbar,
                    Theme.key_chat_inAudioCacheSeekbar, Theme.key_chat_inAudioSeekbarSelected);
            put(0xffe0eaf4,
                    Theme.key_chat_inFileBackground, Theme.key_chat_inFileBackgroundSelected, Theme.key_chat_inContactBackground);
            put(SELECTED_OUTGOING,
                    Theme.key_chat_outFileBackground, Theme.key_chat_outFileBackgroundSelected, Theme.key_chat_outContactBackground);
            put(SERVICE_ICON,
                    Theme.key_chat_serviceIcon);
            put(0xffffffff,
                    Theme.key_chat_serviceBackground, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected,
                    Theme.key_chat_inFileProgress, Theme.key_chat_inFileProgressSelected);
            put(0xffd3dfed,
                    Theme.key_chat_serviceBackgroundSelected);
            put(0x22334155,
                    Theme.key_chat_serviceBackgroundSelector);
        }

        private void put(int color, int... keys) { for (int key : keys) colors.put(key, color); }

        @Override public int getColor(int key) {
            int index = colors.indexOfKey(key);
            return index < 0 ? Theme.getDefaultColor(key) : colors.valueAt(index);
        }

        @Override public boolean isDark() { return false; }
        @Override public boolean hasGradientService() { return false; }

        public MessageDrawable createBubble(boolean outgoing, boolean selected, boolean media) {
            MessageDrawable bubble = new MessageDrawable(media ? MessageDrawable.TYPE_MEDIA : MessageDrawable.TYPE_TEXT,
                    outgoing, selected, this);
            // Preserve complex media geometry; only standard text bubbles get fixed corners.
            if (!media) bubble.setRoundRadius(AndroidUtilities.dp(RADIUS_DP));
            return bubble;
        }

        @Override public Drawable getDrawable(String key) {
            if (drawables.containsKey(key)) return drawables.get(key);
            Drawable drawable;
            switch (key) {
                case Theme.key_drawable_msgIn: drawable = createBubble(false, false, false); break;
                case Theme.key_drawable_msgInSelected: drawable = createBubble(false, true, false); break;
                case Theme.key_drawable_msgOut: drawable = createBubble(true, false, false); break;
                case Theme.key_drawable_msgOutSelected: drawable = createBubble(true, true, false); break;
                case Theme.key_drawable_msgInMedia: drawable = createBubble(false, false, true); break;
                case Theme.key_drawable_msgInMediaSelected: drawable = createBubble(false, true, true); break;
                case Theme.key_drawable_msgOutMedia: drawable = createBubble(true, false, true); break;
                case Theme.key_drawable_msgOutMediaSelected: drawable = createBubble(true, true, true); break;
                default:
                    Drawable original = Theme.getThemeDrawablesMap().get(key);
                    if (original == null || original.getConstantState() == null) return null;
                    drawable = original.getConstantState().newDrawable().mutate();
                    int colorKey = Theme.getThemeDrawableColorKey(key);
                    if (colorKey >= 0) Theme.setDrawableColor(drawable, getColor(colorKey));
            }
            drawables.put(key, drawable);
            return drawable;
        }

        @Override public Paint getPaint(String key) {
            if (paints.containsKey(key)) return paints.get(key);
            Paint original = Theme.getThemePaint(key);
            if (original == null) return null;
            Paint paint = original instanceof TextPaint ? new TextPaint(original) : new Paint(original);
            paint.setShader(null);
            paint.setColorFilter(null);
            // Divider is exposed by getThemePaint(), but is not in the chat paint/color map.
            int colorKey = Theme.key_paint_divider.equals(key) ? Theme.key_divider : Theme.getThemePaintColorKey(key);
            if (colorKey >= 0) paint.setColor(getColor(colorKey));
            if (paint instanceof TextPaint && Theme.key_paint_chatActionText.equals(key)) {
                ((TextPaint) paint).linkColor = getColor(Theme.key_chat_serviceLink);
            }
            paints.put(key, paint);
            return paint;
        }
    }
}
