package com.nullgram.client.repeat;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageCustomParamsHelper;
import org.telegram.messenger.R;
import org.telegram.messenger.UiTestEnvironment;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.EditTextBoldCursor;
import top.nkbe.niagram.config.NyaConfig;
import top.nkbe.niagram.settings.NekoSettingsActivity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exercises real repeat controls and the production Handler/manager adapter, without Telegram sends. */
@RunWith(RobolectricTestRunner.class)
@Config(application = UiTestEnvironment.App.class, sdk = {28, 36},
        shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class RepeatUiTest {
    private Activity activity;
    private AlertDialog dialog;

    @Before public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        AndroidUtilities.density = 1;
        RepeatController.cancelAll();
        RepeatController.get(0).cleanupTask();
        RepeatController.get(1).cleanupTask();
        drainUi();
    }

    @After public void cleanup() {
        if (dialog != null) dialog.dismiss();
        RepeatController.cancelAll();
        RepeatController.get(0).cleanupTask();
        RepeatController.get(1).cleanupTask();
        activity.finish();
    }

    @Test public void cancelAndDismissNeverInvokeStart() {
        int[] calls = {0};
        dialog = RepeatDialog.create(activity, null, "unsent draft", RepeatTask.Type.NEW_MESSAGE, (count, interval) -> {
            calls[0]++;
            return true;
        });
        dialog.show(); drainUi();
        assertTrue(dialog.isShowing());
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        drainUi();
        assertFalse(dialog.isShowing());
        assertEquals(0, calls[0]);
        dialog.show(); drainUi(); dialog.cancel();
        assertEquals(0, calls[0]);
    }

    @Test public void settingsSaveSeparateCountsAndIntervalsAndPrefillSendingDialogs() {
        var preferences = NyaConfig.getPreferences();
        preferences.edit().putInt("NullgramExistingRepeatDefaultCount", 4)
                .remove("NullgramNewRepeatDefaultCount")
                .remove("NullgramExistingRepeatDefaultIntervalMs")
                .remove("NullgramNewRepeatDefaultIntervalMs").apply();
        class Settings extends NekoSettingsActivity {
            @Override public Activity getParentActivity() { return activity; }
            @Override public Dialog showDialog(Dialog value) {
                dialog = (AlertDialog) value;
                dialog.show();
                return dialog;
            }
            void open(String rowField) {
                updateRows();
                listAdapter = createAdapter(activity);
                onItemClick(null, ReflectionHelpers.<Integer>getField(this, rowField), 0f, 0f);
                drainUi();
            }
        }
        Settings settings = new Settings();
        try {
            settings.open("existingRepeatDefaultsRow");
            List<EditTextBoldCursor> fields = inputs(dialog.getWindow().getDecorView());
            EditTextBoldCursor count = fields.get(0);
            EditTextBoldCursor interval = fields.get(1);
            assertEquals("4", count.getText().toString());
            assertEquals("3", interval.getText().toString());
            count.setText("101");
            assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
            count.setText("7");
            interval.setText("0.009");
            assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue(dialog.isShowing());
            assertEquals(4, RepeatDefaults.getCount(RepeatTask.Type.EXISTING));
            assertEquals(3000L, RepeatDefaults.getIntervalMs(RepeatTask.Type.EXISTING));
            interval.setText("1.75");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(7, RepeatDefaults.getCount(RepeatTask.Type.EXISTING));
            assertEquals(1750L, RepeatDefaults.getIntervalMs(RepeatTask.Type.EXISTING));
            assertFalse(dialog.isShowing());

            settings.open("newRepeatDefaultsRow");
            fields = inputs(dialog.getWindow().getDecorView());
            count = fields.get(0);
            interval = fields.get(1);
            assertEquals("5", count.getText().toString());
            assertEquals("3", interval.getText().toString());
            count.setText("11");
            interval.setText("0.01");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(1750L, RepeatDefaults.getIntervalMs(RepeatTask.Type.EXISTING));
            assertEquals(10L, RepeatDefaults.getIntervalMs(RepeatTask.Type.NEW_MESSAGE));

            List<Long> submitted = new ArrayList<>();
            for (RepeatTask.Type type : RepeatTask.Type.values()) {
                dialog = RepeatDialog.create(activity, null, "draft", type, (n, ms) -> {
                    submitted.add((long) n);
                    submitted.add(ms);
                    return true;
                });
                dialog.show(); drainUi();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            }
            assertEquals(java.util.Arrays.asList(7L, 1750L, 11L, 10L), submitted);
        } finally {
            preferences.edit().remove("NullgramExistingRepeatDefaultCount")
                    .remove("NullgramNewRepeatDefaultCount")
                    .remove("NullgramExistingRepeatDefaultIntervalMs")
                    .remove("NullgramNewRepeatDefaultIntervalMs").apply();
        }
    }

    @Test public void invalidValuesDisableStartAndValidValuesPreserveMilliseconds() {
        List<Long> accepted = new ArrayList<>();
        dialog = RepeatDialog.create(activity, null, "draft", RepeatTask.Type.NEW_MESSAGE, (count, interval) -> {
            accepted.add((long) count); accepted.add(interval); return true;
        });
        dialog.show(); drainUi();
        List<EditTextBoldCursor> fields = inputs(dialog.getWindow().getDecorView());
        assertEquals(2, fields.size());
        View start = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        fields.get(0).setText("0"); assertFalse(start.isEnabled());
        fields.get(0).setText("101"); assertFalse(start.isEnabled());
        fields.get(0).setText("5"); fields.get(1).setText("0.009"); assertFalse(start.isEnabled());
        fields.get(1).setText("60.001"); assertFalse(start.isEnabled());
        fields.get(1).setText("1.0001"); assertFalse(start.isEnabled());
        assertTrue(accepted.isEmpty());
        fields.get(1).setText("1.750"); assertTrue(start.isEnabled());
        start.performClick();
        assertEquals(java.util.Arrays.asList(5L, 1750L), accepted);
        assertFalse(dialog.isShowing());
        assertFalse(start.isEnabled());
    }

    @Test public void minimumIntervalIsTenMillisecondsAndHasUnits() {
        List<Long> accepted = new ArrayList<>();
        dialog = RepeatDialog.create(activity, null, "draft", RepeatTask.Type.NEW_MESSAGE, (count, interval) -> {
            accepted.add(interval); return true;
        });
        dialog.show(); drainUi();
        List<EditTextBoldCursor> fields = inputs(dialog.getWindow().getDecorView());
        fields.get(1).setText("0.009");
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        assertTrue(containsText(dialog.getWindow().getDecorView(), "0.01"));
        fields.get(1).setText("0.01");
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        assertTrue(containsText(dialog.getWindow().getDecorView(), LocaleController.getString(R.string.NullgramRepeatCount)));
        assertTrue(containsText(dialog.getWindow().getDecorView(), LocaleController.getString(R.string.NullgramRepeatInterval)));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertEquals(Collections.singletonList(10L), accepted);
    }

    @Test public void handlerSubmitsAtMinimumIntervalWithoutServerNotifications() {
        RepeatTaskManager manager = RepeatController.get(0);
        Sender sender = new Sender();
        assertTrue(manager.startTask(123, RepeatTask.Type.NEW_MESSAGE, 5, 10, sender));
        // Activity setup can leave a frame synchronization barrier before the first Handler post.
        for (int waited = 0; sender.calls == 0 && waited < 32; waited++)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertEquals(1, sender.calls);
        for (int submitted = 2; submitted <= 5; submitted++) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10));
            assertEquals("one submission per 10ms without ACK", submitted, sender.calls);
        }
        assertEquals(0, manager.getProgress().confirmed);
        assertEquals(RepeatTaskState.RUNNING, manager.getTaskState());
        manager.cancelTask();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        assertEquals(5, sender.calls);
        assertEquals(RepeatTaskState.CANCELLED, manager.getTaskState());
    }

    @Test public void shortBatchSharesDisplayTimeAcrossSecondsButLeavesRealDatesAlone() {
        List<TLRPC.Message> messages = new ArrayList<>();
        int[] messagesPerRound = {1};
        RepeatTaskManager.Sender sender = new RepeatTaskManager.Sender() {
            @Override public String validate() { return null; }
            @Override public List<Integer> send() {
                return RepeatController.capture(0, 123, () -> {
                    for (int i = 0; i < messagesPerRound[0]; i++) {
                        TLRPC.Message message = new TLRPC.TL_message();
                        message.id = -1 - messages.size();
                        message.dialog_id = 123;
                        message.date = 1700000000 + messages.size();
                        RepeatController.onMessageCreated(0, message);
                        messages.add(message);
                    }
                });
            }
        };
        RepeatTaskManager.Sender shared = RepeatController.withSharedDisplayTime(5, 59, 1, sender);
        for (int i = 0; i < 5; i++) shared.send();
        assertEquals(5, messages.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(1700000000, messages.get(i).repeatDisplayDate);
            assertEquals(1700000000 + i, messages.get(i).date);
        }

        RepeatController.withSharedDisplayTime(5, 60, 1, sender).send();
        RepeatController.withSharedDisplayTime(6, 59, 1, sender).send();
        sender.send();
        messagesPerRound[0] = 2;
        RepeatController.withSharedDisplayTime(3, 59, 2, sender).send();
        for (int i = 5; i < messages.size(); i++) assertEquals(0, messages.get(i).repeatDisplayDate);
        int groupStart = messages.size();
        RepeatTaskManager.Sender group = RepeatController.withSharedDisplayTime(2, 10, 2, sender);
        group.send();
        group.send();
        assertEquals(groupStart + 4, messages.size());
        for (int i = groupStart; i < messages.size(); i++) {
            assertEquals(messages.get(groupStart).date, messages.get(i).repeatDisplayDate);
        }
    }

    @Test public void sharedDisplayTimeSurvivesLocalParamsRoundTripWithoutReplacingServerDate() throws Exception {
        TLRPC.Message sent = new TLRPC.TL_message();
        sent.repeatDisplayDate = 1700000000;
        sent.date = 1700000001;
        assertFalse(MessageCustomParamsHelper.isEmpty(sent));
        var paramsClass = Class.forName("org.telegram.messenger.MessageCustomParamsHelper$Params_v1")
                .asSubclass(TLObject.class);
        TLObject params = ReflectionHelpers.callConstructor(paramsClass,
                ReflectionHelpers.ClassParameter.from(TLRPC.Message.class, sent));
        SerializedData output = new SerializedData();
        params.serializeToStream(output);

        TLRPC.Message restored = new TLRPC.TL_message();
        restored.date = sent.date;
        MessageCustomParamsHelper.readLocalParams(restored, new SerializedData(output.toByteArray()));
        assertEquals(1700000000, restored.repeatDisplayDate);
        assertEquals(1700000001, restored.date);
    }

    @Test public void rejectedStartKeepsDialogAndAllowsRetry() {
        int[] calls = {0};
        dialog = RepeatDialog.create(activity, null, "draft", RepeatTask.Type.EXISTING, (count, interval) -> ++calls[0] == 2);
        dialog.show(); drainUi();
        View start = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        start.performClick();
        assertTrue(dialog.isShowing()); assertTrue(start.isEnabled());
        start.performClick();
        assertFalse(dialog.isShowing()); assertEquals(2, calls[0]);
    }

    @Test public void stopControlTracksProgressAndCancelsActualHandlerCallbacks() {
        RepeatTaskManager manager = RepeatController.get(0);
        Sender sender = new Sender();
        RepeatStatusView status = attach(0);
        assertEquals(View.GONE, status.getVisibility());
        assertTrue(manager.startTask(123, RepeatTask.Type.NEW_MESSAGE, 5, 1000, sender));
        assertEquals(View.VISIBLE, status.getVisibility());
        drainUi();
        assertEquals(1, sender.calls);
        TextView text = (TextView) status.getChildAt(0);
        assertEquals(LocaleController.formatString(R.string.NullgramRepeatProgress,
                LocaleController.getString(R.string.NullgramRepeatNew), 1, 5, 4, 0, 0, 1), text.getText().toString());
        manager.onConfirmed(sender.lastId);
        View stop = status.getChildAt(1);
        assertTrue(stop.isEnabled()); stop.performClick(); stop.performClick();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        assertEquals(RepeatTaskState.CANCELLED, manager.getTaskState());
        assertEquals(1, sender.calls);
        assertEquals(View.GONE, status.getVisibility()); assertFalse(stop.isEnabled());
    }

    @Test public void completionHidesControlAndReattachmentRestoresCurrentProgress() {
        RepeatTaskManager manager = RepeatController.get(0);
        Sender sender = new Sender();
        RepeatStatusView old = attach(0);
        manager.startTask(123, RepeatTask.Type.EXISTING, 1, 1000, sender);
        drainUi();
        ((ViewGroup) old.getParent()).removeView(old);
        manager.onConfirmed(sender.lastId);
        assertEquals(RepeatTaskState.COMPLETED, manager.getTaskState());
        RepeatStatusView restored = attach(0);
        assertEquals(View.GONE, restored.getVisibility());
        assertFalse(restored.getChildAt(1).isEnabled());
        manager.startTask(123, RepeatTask.Type.EXISTING, 1, 1000, sender);
        assertEquals(View.VISIBLE, restored.getVisibility());
        drainUi(); manager.onConfirmed(sender.lastId);
        assertEquals(View.GONE, restored.getVisibility());
    }

    @Test public void accountRebindStopsOnlyVisibleAccountAndCancelAllStopsRemainder() {
        RepeatTaskManager first = RepeatController.get(0), second = RepeatController.get(1);
        Sender a = new Sender(), b = new Sender();
        RepeatStatusView status = attach(0);
        first.startTask(123, RepeatTask.Type.EXISTING, 5, 1000, a);
        second.startTask(456, RepeatTask.Type.NEW_MESSAGE, 5, 1000, b);
        drainUi();
        status.bind(1); status.getChildAt(1).performClick();
        assertEquals(RepeatTaskState.RUNNING, first.getTaskState());
        assertEquals(RepeatTaskState.CANCELLED, second.getTaskState());
        RepeatController.cancelAll();
        first.onConfirmed(a.lastId); second.onConfirmed(b.lastId);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        assertEquals(RepeatTaskState.CANCELLED, first.getTaskState());
        assertEquals(1, a.calls); assertEquals(1, b.calls);
    }

    private RepeatStatusView attach(int account) {
        RepeatStatusView view = new RepeatStatusView(activity, account, null);
        activity.setContentView(view);
        drainUi();
        return view;
    }

    private static void drainUi() {
        // The app has a permanent frame callback, so run a bounded number of frames.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
    }

    private static List<EditTextBoldCursor> inputs(View view) {
        List<EditTextBoldCursor> result = new ArrayList<>();
        if (view instanceof EditTextBoldCursor) result.add((EditTextBoldCursor) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) result.addAll(inputs(group.getChildAt(i)));
        }
        return result;
    }

    private static boolean containsText(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (containsText(group.getChildAt(i), text)) return true;
        }
        return false;
    }

    private static class Sender implements RepeatTaskManager.Sender {
        int calls, lastId;
        public String validate() { return null; }
        public List<Integer> send() { calls++; return Collections.singletonList(--lastId); }
    }
}
