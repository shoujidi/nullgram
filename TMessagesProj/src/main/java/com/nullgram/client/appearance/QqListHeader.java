package com.nullgram.client.appearance;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;

/** QQ account header; reuses the native account action and Premium status drawable. */
public final class QqListHeader extends FrameLayout {
    private final BackupImageView avatar;
    private final TextView title;
    private final ImageView badge;
    private final TextView connection;
    private final View dot;
    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable statusDrawable;

    public QqListHeader(Context context, Runnable openAccount) {
        super(context);
        setBackgroundColor(DisguiseListTheme.WHITE);
        avatar = new BackupImageView(context);
        avatar.setRoundRadius(AndroidUtilities.dp(18));
        avatar.setContentDescription(LocaleController.getString(R.string.Settings));
        avatar.setOnClickListener(v -> openAccount.run());
        addView(avatar);
        title = label(context, 18, DisguiseListTheme.TEXT);
        title.setOnClickListener(v -> openAccount.run());
        addView(title);
        badge = new ImageView(context);
        badge.setScaleType(ImageView.ScaleType.FIT_CENTER);
        badge.setVisibility(GONE);
        addView(badge);
        connection = label(context, 12, DisguiseListTheme.GRAY);
        addView(connection);
        dot = new View(context);
        dot.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(dot);
    }

    private static TextView label(Context context, int size, int color) {
        TextView view = new TextView(context);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    public void bind(int account) {
        TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
        bindIdentity(self);
        AvatarDrawable placeholder = new AvatarDrawable();
        if (self != null) placeholder.setInfo(account, self);
        avatar.getImageReceiver().setCurrentAccount(account);
        avatar.setForUserOrChat(self, placeholder);
    }

    void bindIdentity(TLRPC.User self) {
        title.setText(self != null ? UserObject.getUserName(self) : "");
        requestLayout();
    }

    public void setStatusDrawable(AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable drawable, Runnable action) {
        if (statusDrawable != null && statusDrawable != drawable) statusDrawable.setSecondParent(null);
        statusDrawable = drawable;
        badge.setImageDrawable(drawable);
        badge.setOnClickListener(action == null ? null : v -> action.run());
        badge.setContentDescription(LocaleController.getString(R.string.PremiumPreviewEmojiStatus));
        if (drawable != null && isAttachedToWindow()) drawable.setSecondParent(badge);
        requestLayout();
    }

    public void setConnectionState(int state) {
        int label = R.string.Connecting;
        if (state == ConnectionsManager.ConnectionStateConnected) label = R.string.NullgramQqConnected;
        else if (state == ConnectionsManager.ConnectionStateWaitingForNetwork) label = R.string.WaitingForNetwork;
        else if (state == ConnectionsManager.ConnectionStateConnectingToProxy) label = R.string.ConnectingToProxy;
        else if (state == ConnectionsManager.ConnectionStateUpdating) label = R.string.Updating;
        connection.setText(LocaleController.getString(label));
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(state == ConnectionsManager.ConnectionStateConnected ? 0xff25c86e : 0xffb0b0b0);
        dot.setBackground(shape);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (statusDrawable != null) statusDrawable.setSecondParent(badge);
    }
    @Override protected void onDetachedFromWindow() {
        if (statusDrawable != null) statusDrawable.setSecondParent(null);
        super.onDetachedFromWindow();
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        boolean hasBadge = statusDrawable != null && statusDrawable.getDrawable() != null;
        badge.setVisibility(hasBadge ? VISIBLE : GONE);
        measureExact(avatar, AndroidUtilities.dp(36), AndroidUtilities.dp(36));
        measureExact(badge, AndroidUtilities.dp(22), AndroidUtilities.dp(22));
        int available = Math.max(0, width - AndroidUtilities.dp(68));
        int titleWidth = Math.min(Math.max(0, available - (hasBadge ? AndroidUtilities.dp(26) : 0)),
                (int) Math.ceil(title.getPaint().measureText(title.getText().toString())));
        title.measure(MeasureSpec.makeMeasureSpec(titleWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        connection.measure(MeasureSpec.makeMeasureSpec(Math.max(0, available - AndroidUtilities.dp(12)), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        measureExact(dot, AndroidUtilities.dp(6), AndroidUtilities.dp(6));
    }
    private static void measureExact(View view, int width, int height) {
        view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int height = b - t;
        place(avatar, AndroidUtilities.dp(16), (height - avatar.getMeasuredHeight()) / 2);
        int top = Math.max(0, (height - title.getMeasuredHeight() - connection.getMeasuredHeight() - AndroidUtilities.dp(5)) / 2);
        place(title, AndroidUtilities.dp(62), top);
        place(badge, AndroidUtilities.dp(66) + title.getMeasuredWidth(), top + (title.getMeasuredHeight() - badge.getMeasuredHeight()) / 2);
        int statusTop = top + title.getMeasuredHeight() + AndroidUtilities.dp(5);
        place(connection, AndroidUtilities.dp(74), statusTop);
        place(dot, AndroidUtilities.dp(62), statusTop + (connection.getMeasuredHeight() - dot.getMeasuredHeight()) / 2);
    }
    private void place(View view, int start, int top) {
        int left = LocaleController.isRTL ? getMeasuredWidth() - start - view.getMeasuredWidth() : start;
        view.layout(left, top, left + view.getMeasuredWidth(), top + view.getMeasuredHeight());
    }
}
