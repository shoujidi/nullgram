package com.nullgram.client.appearance;

import org.telegram.messenger.BotInlineKeyboard;
import androidx.core.graphics.ColorUtils;

/** Local, opaque surfaces keep inline actions readable over custom wallpapers. */
public final class QqInlineButtonStyle {
    private QqInlineButtonStyle() {}
    public static final int BORDER = 0xffb6c9de;
    public static int background(BotInlineKeyboard.BackgroundColor kind, boolean pressed) {
        int color;
        switch (kind) {
            case DANGER: color = 0xffffefee; break;
            case SUCCESS: color = 0xffe9f6ee; break;
            case PRIMARY: color = 0xffe8f2ff; break;
            default: color = 0xffffffff;
        }
        return pressed ? ColorUtils.blendARGB(color, 0xffd3dfed, .45f) : color;
    }
    public static int text(BotInlineKeyboard.BackgroundColor kind) {
        switch (kind) {
            case DANGER: return 0xffa12c28;
            case SUCCESS: return 0xff17613a;
            default: return 0xff0058a8;
        }
    }
}
