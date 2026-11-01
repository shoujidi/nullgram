package com.nullgram.client.repeat;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/** 输入框上方的统一状态条；附着时订阅，移除时释放，重建后读取同一账号快照。 */
public final class RepeatStatusView extends LinearLayout implements RepeatTaskManager.Listener, org.telegram.messenger.NotificationCenter.NotificationCenterDelegate {
    private final TextView progress, stop;
    private final Theme.ResourcesProvider theme;
    private RepeatTaskManager manager;
    private boolean subscribed;

    public RepeatStatusView(Context context, int account, Theme.ResourcesProvider theme) {
        super(context);
        this.theme = theme;
        setGravity(Gravity.CENTER_VERTICAL);
        setOrientation(HORIZONTAL);
        setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        progress = new TextView(context);
        progress.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        progress.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(progress, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        stop = new TextView(context);
        stop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        stop.setGravity(Gravity.CENTER);
        stop.setMinHeight(AndroidUtilities.dp(48));
        stop.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        addView(stop, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        stop.setOnClickListener(view -> {
            stop.setEnabled(false);
            manager.cancelTask();
        });
        bind(account);
    }

    public void bind(int account) {
        if (subscribed) manager.removeListener(this);
        manager = RepeatController.get(account);
        if (subscribed) manager.addListener(this);
        onChanged(manager.getProgress());
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); subscribed = true; manager.addListener(this); onChanged(manager.getProgress());
        org.telegram.messenger.NotificationCenter.getGlobalInstance().addObserver(this, org.telegram.messenger.NotificationCenter.didSetNewTheme);
    }
    @Override protected void onDetachedFromWindow() {
        org.telegram.messenger.NotificationCenter.getGlobalInstance().removeObserver(this, org.telegram.messenger.NotificationCenter.didSetNewTheme);
        manager.removeListener(this); subscribed = false; super.onDetachedFromWindow();
    }
    @Override public void onChanged(RepeatTask task) {
        boolean running = task != null && task.state == RepeatTaskState.RUNNING;
        setVisibility(running && (RepeatConfig.SHOW_STATUS || RepeatConfig.SHOW_STOP) ? VISIBLE : GONE);
        progress.setVisibility(RepeatConfig.SHOW_STATUS ? VISIBLE : GONE);
        stop.setVisibility(RepeatConfig.SHOW_STOP ? VISIBLE : GONE);
        stop.setEnabled(running);
        stop.setText(LocaleController.getString(R.string.NullgramRepeatStop));
        if (running) progress.setText(LocaleController.formatString(R.string.NullgramRepeatProgress,
                LocaleController.getString(task.type == RepeatTask.Type.EXISTING ? R.string.NullgramRepeatExisting : R.string.NullgramRepeatNew),
                task.submitted, task.total, task.remaining(), task.confirmed, task.confirmedMessages, task.submittedMessages));
        updateColors();
    }

    private void updateColors() {
        setBackgroundColor(Theme.getColor(Theme.key_chat_messagePanelBackground, theme));
        progress.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText, theme));
        stop.setTextColor(Theme.getColor(Theme.key_chat_messagePanelSend, theme));
        stop.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, theme), 7));
    }
    @Override public void didReceivedNotification(int id, int account, Object... args) { updateColors(); }
    @Override protected void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config); onChanged(manager.getProgress());
    }
}
