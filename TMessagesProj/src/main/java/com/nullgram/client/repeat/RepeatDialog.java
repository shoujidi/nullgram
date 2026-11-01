package com.nullgram.client.repeat;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import java.math.BigDecimal;

/** 两种发送入口共用的参数窗口；取消不调用发送回调。 */
public final class RepeatDialog {
    public interface Start { boolean run(int count, long intervalMs); }
    private RepeatDialog() {}

    public static AlertDialog create(Context context, Theme.ResourcesProvider theme, CharSequence preview,
                                   int defaultCount, Start start) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = AndroidUtilities.dp(24);
        content.setPadding(padding, 0, padding, padding);
        TextView message = label(context, theme, preview == null ? "" : preview);
        message.setMaxLines(3);
        message.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(message);
        content.addView(label(context, theme, LocaleController.getString(R.string.NullgramRepeatCount)));
        EditTextBoldCursor count = input(context, theme, Integer.toString(defaultCount));
        count.setInputType(InputType.TYPE_CLASS_NUMBER);
        content.addView(count);
        content.addView(label(context, theme, LocaleController.getString(R.string.NullgramRepeatInterval)));
        EditTextBoldCursor interval = input(context, theme, BigDecimal.valueOf(RepeatConfig.DEFAULT_INTERVAL_MS, 3).stripTrailingZeros().toPlainString());
        interval.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        content.addView(interval);
        TextView error = label(context, theme, LocaleController.formatString(R.string.NullgramRepeatInvalid,
                RepeatConfig.MIN_COUNT, RepeatConfig.MAX_COUNT,
                BigDecimal.valueOf(RepeatConfig.MIN_INTERVAL_MS, 3).stripTrailingZeros().toPlainString(),
                BigDecimal.valueOf(RepeatConfig.MAX_INTERVAL_MS, 3).stripTrailingZeros().toPlainString()));
        error.setTextColor(Theme.getColor(Theme.key_text_RedRegular, theme));
        content.addView(error);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(context, theme)
                .setTitle(LocaleController.getString(R.string.NullgramRepeatTitle)).setView(scroll)
                .setNegativeButton(LocaleController.getString(R.string.NullgramRepeatCancel), null)
                .setPositiveButton(LocaleController.getString(R.string.NullgramRepeatStart), null).create();
        dialog.setOnShowListener(ignored -> {
            Runnable validate = () -> {
                boolean valid = values(count, interval) != null;
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(valid);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setAlpha(valid ? 1f : .5f);
                error.setVisibility(valid ? View.GONE : View.VISIBLE);
            };
            TextWatcher watcher = new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int from, int n, int after) {}
                public void onTextChanged(CharSequence s, int from, int before, int n) { validate.run(); }
                public void afterTextChanged(Editable editable) {}
            };
            count.addTextChangedListener(watcher);
            interval.addTextChangedListener(watcher);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                long[] values = values(count, interval);
                if (values == null) return;
                view.setEnabled(false);
                if (start.run((int) values[0], values[1])) dialog.dismiss();
                else validate.run();
            });
            validate.run();
        });
        return dialog;
    }

    private static long[] values(EditTextBoldCursor count, EditTextBoldCursor interval) {
        try {
            int n = Integer.parseInt(count.getText().toString().trim());
            long ms = new BigDecimal(interval.getText().toString().trim()).multiply(BigDecimal.valueOf(1000)).longValueExact();
            return RepeatConfig.isValid(n, ms) ? new long[]{n, ms} : null;
        } catch (RuntimeException error) { return null; }
    }

    private static EditTextBoldCursor input(Context context, Theme.ResourcesProvider theme, String value) {
        EditTextBoldCursor view = new EditTextBoldCursor(context);
        view.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, theme));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        view.setBackground(Theme.createEditTextDrawable(context, true));
        view.setSingleLine(true);
        view.setText(value);
        view.setSelectAllOnFocus(true);
        view.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        return view;
    }

    private static TextView label(Context context, Theme.ResourcesProvider theme, CharSequence text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, theme));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(4));
        return view;
    }
}
