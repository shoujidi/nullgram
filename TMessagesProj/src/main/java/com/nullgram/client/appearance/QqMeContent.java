package com.nullgram.client.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

/** QQ-style home surface. All actions lead to the existing Telegram screens. */
public final class QqMeContent extends LinearLayout {
    public enum Destination { PROFILE, SAVED_MESSAGES, DISGUISE, SETTINGS }
    public interface Navigation { void open(Destination destination); }

    final BackupImageView avatar;
    final SimpleTextView name;
    final TextView subtitle;
    final FrameLayout accountCard;
    private final AvatarDrawable placeholder = new AvatarDrawable();
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji;

    public QqMeContent(Context context, Navigation navigation) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(QqPageStyle.BACKGROUND);
        setPadding(dp(12), dp(12), dp(12), dp(20));
        boolean rtl = LocaleController.isRTL;
        float scale = QqListStyle.fontScale(context);

        accountCard = new FrameLayout(context);
        accountCard.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(12), QqPageStyle.WHITE, QqPageStyle.LINE));
        accountCard.setOnClickListener(v -> navigation.open(Destination.PROFILE));
        accountCard.setContentDescription(getString(R.string.MyProfile));
        addView(accountCard, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(120 + Math.round(36 * Math.max(0, scale - 1)))));

        avatar = new BackupImageView(context);
        avatar.setRoundRadius(dp(32));
        accountCard.addView(avatar, LayoutHelper.createFrame(64, 64, (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 16, 0, 16, 0));

        LinearLayout identity = new LinearLayout(context);
        identity.setOrientation(VERTICAL);
        accountCard.addView(identity, LayoutHelper.createFrame(-1, -2, Gravity.CENTER_VERTICAL, rtl ? 40 : 96, 12, rtl ? 96 : 40, 12));
        name = new SimpleTextView(context);
        name.setTextSize(Math.round(20 * scale));
        name.setTextColor(QqPageStyle.TEXT);
        name.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        identity.addView(name, new LinearLayout.LayoutParams(-1, dp(30 * scale)));
        emoji = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(name, dp(22), AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS);
        emoji.setColor(QqPageStyle.BLUE);

        subtitle = new TextView(context);
        subtitle.setTextColor(QqPageStyle.SECONDARY);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, QqListStyle.sp(context, 13));
        subtitle.setMaxLines(1);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        identity.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        addArrow(accountCard);

        LinearLayout menu = new LinearLayout(context);
        menu.setOrientation(VERTICAL);
        menu.setBackground(Theme.createRoundRectDrawable(dp(12), QqPageStyle.WHITE));
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(-1, -2);
        menuParams.topMargin = dp(12);
        addView(menu, menuParams);
        addRow(menu, R.drawable.msg_contacts, R.string.MyProfile, Destination.PROFILE, navigation, true);
        addRow(menu, R.drawable.msg_saved, R.string.SavedMessages, Destination.SAVED_MESSAGES, navigation, true);
        addRow(menu, R.drawable.msg_theme, R.string.NullgramDisguise, Destination.DISGUISE, navigation, true);
        addRow(menu, R.drawable.msg_settings, R.string.Settings, Destination.SETTINGS, navigation, false);
    }

    private void addArrow(FrameLayout row) {
        ImageView arrow = new ImageView(getContext());
        arrow.setImageResource(R.drawable.msg_arrowright);
        arrow.setScaleType(ImageView.ScaleType.CENTER);
        arrow.setColorFilter(QqPageStyle.SECONDARY, PorterDuff.Mode.SRC_IN);
        if (LocaleController.isRTL) arrow.setScaleX(-1);
        row.addView(arrow, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 12, 0, 12, 0));
    }

    private void addRow(LinearLayout menu, int icon, int label, Destination destination, Navigation navigation, boolean divider) {
        boolean rtl = LocaleController.isRTL;
        FrameLayout row = new FrameLayout(getContext());
        row.setTag(destination);
        row.setBackground(Theme.createSelectorDrawable(QqPageStyle.LINE, 2));
        row.setOnClickListener(v -> navigation.open(destination));
        row.setContentDescription(getString(label));
        menu.addView(row, new LinearLayout.LayoutParams(-1, dp(56 + Math.round(24 * Math.max(0, QqListStyle.fontScale(getContext()) - 1)))));
        ImageView image = new ImageView(getContext());
        image.setImageResource(icon);
        image.setScaleType(ImageView.ScaleType.CENTER);
        image.setColorFilter(QqPageStyle.BLUE, PorterDuff.Mode.SRC_IN);
        row.addView(image, LayoutHelper.createFrame(28, 28, (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
        TextView title = new TextView(getContext());
        title.setText(getString(label));
        title.setTextColor(QqPageStyle.TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, QqListStyle.sp(getContext(), 16));
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        row.addView(title, LayoutHelper.createFrame(-1, -1, Gravity.TOP, rtl ? 44 : 58, 0, rtl ? 58 : 44, 0));
        addArrow(row);
        if (divider) {
            View line = new View(getContext()); line.setBackgroundColor(QqPageStyle.LINE);
            row.addView(line, LayoutHelper.createFrame(-1, 1, Gravity.BOTTOM, rtl ? 16 : 58, 0, rtl ? 58 : 16, 0));
        }
    }

    public void bind(int account, TLRPC.User user) {
        avatar.getImageReceiver().setCurrentAccount(account);
        placeholder.setInfo(account, user);
        avatar.setForUserOrChat(user, placeholder);
        name.setText(UserObject.getUserName(user));
        String username = user == null ? null : UserObject.getPublicUsername(user);
        subtitle.setText(TextUtils.isEmpty(username) ? getString(R.string.MyProfile) : "@" + username);
        emoji.setCurrentAccount(account);
        Long status = UserObject.getEmojiStatusDocumentId(user);
        if (status != null) emoji.set(status, false);
        else if (user != null && user.premium) emoji.set(getResources().getDrawable(R.drawable.msg_premium_liststar).mutate(), false);
        else emoji.set((Drawable) null, false);
        name.setRightDrawable(emoji.isEmpty() ? null : emoji);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); emoji.attach(); }
    @Override protected void onDetachedFromWindow() { emoji.detach(); super.onDetachedFromWindow(); }
}
