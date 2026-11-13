package com.nullgram.client.appearance;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import org.telegram.messenger.AndroidUtilities;

/** Paints the QQ surface behind Telegram's composer controls; owns no views or events. */
public final class QqComposerBackground {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF field = new RectF();

    public void draw(Canvas canvas, int width, int height, int reservedTop, Rect textBounds) {
        int top = Math.max(0, Math.min(height, reservedTop));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(DisguiseChatTheme.INPUT_BACKGROUND);
        canvas.drawRect(0, top, width, height, paint);
        if (textBounds == null) return;
        float inset = AndroidUtilities.dp(1);
        field.set(Math.max(inset, textBounds.left), Math.max(top + inset, textBounds.top + AndroidUtilities.dp(2)),
                Math.min(width - inset, textBounds.right), Math.min(height - inset, textBounds.bottom - AndroidUtilities.dp(2)));
        if (field.width() <= 0 || field.height() <= 0) return;
        float radius = Math.min(AndroidUtilities.dp(12), field.height() / 2);
        paint.setColor(DisguiseChatTheme.INCOMING);
        canvas.drawRoundRect(field, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.density);
        paint.setColor(DisguiseChatTheme.INPUT_BORDER);
        canvas.drawRoundRect(field, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
