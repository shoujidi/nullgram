package com.nullgram.client.appearance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

/** Static QQ 9.2.15 artwork, with a lightweight selection crossfade driven by the existing tab animator. */
public final class QqTabPainter {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable[][] icons = new Drawable[4][2];
    private final Context context;
    private static final int[][] ICONS = {
        {R.drawable.nullgram_qq_tab_message_normal, R.drawable.nullgram_qq_tab_message_selected},
        {R.drawable.nullgram_qq_tab_contacts_normal, R.drawable.nullgram_qq_tab_contacts_selected},
        {R.drawable.nullgram_qq_tab_calls_normal, R.drawable.nullgram_qq_tab_calls_selected},
        {R.drawable.nullgram_qq_tab_me_normal, R.drawable.nullgram_qq_tab_me_selected}
    };
    public QqTabPainter(Context context) {
        this.context = context;
        for (int i = 0; i < icons.length; i++) for (int j = 0; j < 2; j++) {
            icons[i][j] = context.getResources().getDrawable(ICONS[i][j], context.getTheme()).mutate();
            icons[i][j].setTint(j == 0 ? DisguiseListTheme.TEXT : DisguiseListTheme.BLUE);
        }
    }
    private static int slot(int kind) { return kind == 0 ? 0 : kind == 1 ? 1 : kind == 3 ? 2 : 3; }
    public static CharSequence label(int kind) {
        return LocaleController.getString(kind == 0 ? R.string.NullgramQqTabMessages : kind == 1 ? R.string.Contacts
                : kind == 3 ? R.string.Calls : R.string.NullgramQqTabMe);
    }
    public void draw(Canvas canvas, float width, float height, int kind, float selection, String badge) {
        float d = AndroidUtilities.density;
        float selected = Math.max(0, Math.min(1, selection));
        int iconSize = AndroidUtilities.dp(29), top = AndroidUtilities.dp(3);
        int left = Math.round((width - iconSize) / 2);
        for (int state = 0; state < 2; state++) {
            Drawable icon = icons[slot(kind)][state];
            icon.setBounds(left, top, left + iconSize, top + iconSize);
            icon.setAlpha(Math.round(255 * (state == 0 ? 1 - selected : selected)));
            icon.draw(canvas);
        }
        int normal = 0xff8b8d99, active = DisguiseListTheme.BLUE;
        text.setColor(Color.rgb(Math.round(Color.red(normal) + (Color.red(active) - Color.red(normal)) * selected),
                Math.round(Color.green(normal) + (Color.green(active) - Color.green(normal)) * selected),
                Math.round(Color.blue(normal) + (Color.blue(active) - Color.blue(normal)) * selected)));
        text.setTextSize(QqListStyle.sp(context, 11));
        text.setTextAlign(Paint.Align.CENTER);
        CharSequence fitted = TextUtils.ellipsize(label(kind), text, Math.max(1, width - 8 * d), TextUtils.TruncateAt.END);
        float baseline = Math.min(height - 4 * d - text.descent(), 35 * d - text.ascent());
        canvas.drawText(fitted.toString(), width / 2, baseline, text);
        if (!TextUtils.isEmpty(badge)) {
            text.setTextSize(QqListStyle.sp(context, 10));
            String value = badge.length() > 4 ? "99+" : badge;
            float h = Math.max(16 * d, text.descent() - text.ascent() + 2 * d);
            float w = Math.max(h, text.measureText(value) + 8 * d);
            float x = Math.min(width - w - 2 * d, width / 2 + 7 * d), y = 2 * d;
            fill.setColor(DisguiseListTheme.RED);
            canvas.drawRoundRect(x, y, x + w, y + h, h / 2, h / 2, fill);
            text.setColor(DisguiseListTheme.WHITE);
            canvas.drawText(value, x + w / 2, y + h / 2 - (text.ascent() + text.descent()) / 2, text);
        }
    }
}
