package com.nullgram.client.repeat;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
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
                                     RepeatTask.Type type, Start start) {
        return create(context, theme, preview, type, LocaleController.getString(R.string.NullgramRepeatTitle),
                R.string.NullgramRepeatStart, start);
    }

    public static AlertDialog createDefaultsEditor(Context context, Theme.ResourcesProvider theme, RepeatTask.Type type,
                                                    String title, Runnable onSaved) {
        return create(context, theme, null, type, title, R.string.Save, (count, intervalMs) -> {
            RepeatDefaults.set(type, count, intervalMs);
            onSaved.run();
            return true;
        });
    }

    private static AlertDialog create(Context context, Theme.ResourcesProvider theme, CharSequence preview,
                                      RepeatTask.Type type, String title, int positiveButton, Start start) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = AndroidUtilities.dp(24);
        content.setPadding(padding, 0, padding, AndroidUtilities.dp(6));
        if (!TextUtils.isEmpty(preview)) {
            TextView message = label(context, theme, preview);
            message.setSingleLine(true);
            message.setEllipsize(TextUtils.TruncateAt.END);
            content.addView(message);
        }

        EditTextBoldCursor count = input(context, theme, Integer.toString(RepeatDefaults.getCount(type)));
        count.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditTextBoldCursor interval = input(context, theme, RepeatConfig.formatSeconds(RepeatDefaults.getIntervalMs(type)));
        interval.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        count.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        interval.setImeOptions(EditorInfo.IME_ACTION_DONE);
        count.setNextFocusForwardId(interval.getId());

        LinearLayout fields = new LinearLayout(context);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        fields.setBaselineAligned(false);
        addField(fields, theme, R.string.NullgramRepeatCount, count, AndroidUtilities.dp(12));
        addField(fields, theme, R.string.NullgramRepeatInterval, interval, 0);
        content.addView(fields);
        TextView error = label(context, theme, LocaleController.formatString(R.string.NullgramRepeatInvalid,
                RepeatConfig.MIN_COUNT, RepeatConfig.MAX_COUNT,
                RepeatConfig.formatSeconds(RepeatConfig.MIN_INTERVAL_MS), RepeatConfig.formatSeconds(RepeatConfig.MAX_INTERVAL_MS)));
        error.setTextColor(Theme.getColor(Theme.key_text_RedRegular, theme));
        error.setVisibility(View.GONE);
        content.addView(error);
        AlertDialog dialog = new AlertDialog.Builder(context, theme)
                .setTitle(title).setView(content)
                .setNegativeButton(LocaleController.getString(R.string.NullgramRepeatCancel), null)
                .setPositiveButton(LocaleController.getString(positiveButton), null).create();
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
        view.setId(View.generateViewId());
        view.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, theme));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        view.setBackground(Theme.createEditTextDrawable(context, true));
        view.setSingleLine(true);
        view.setText(value);
        view.setSelectAllOnFocus(true);
        view.setMinHeight(AndroidUtilities.dp(48));
        view.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        return view;
    }

    private static void addField(LinearLayout row, Theme.ResourcesProvider theme, int labelId,
                                 EditTextBoldCursor input, int endMargin) {
        LinearLayout column = new LinearLayout(row.getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        TextView caption = label(row.getContext(), theme, LocaleController.getString(labelId));
        caption.setLabelFor(input.getId());
        column.addView(caption);
        column.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginEnd(endMargin);
        row.addView(column, params);
    }

    private static TextView label(Context context, Theme.ResourcesProvider theme, CharSequence text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, theme));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        return view;
    }
}
