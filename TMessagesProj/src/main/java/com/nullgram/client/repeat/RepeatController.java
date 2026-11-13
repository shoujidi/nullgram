package com.nullgram.client.repeat;

import android.os.SystemClock;
import android.widget.Toast;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Android 适配层：账号通知、前台生命周期及真实消息 ID 关联。只在 UI 线程访问。 */
public final class RepeatController implements NotificationCenter.NotificationCenterDelegate {
    private static final Map<Integer, RepeatController> INSTANCES = new HashMap<>();
    private static final ThreadLocal<Capture> CAPTURE = new ThreadLocal<>();
    private static final ThreadLocal<DisplayBatch> DISPLAY_BATCH = new ThreadLocal<>();
    private final RepeatTaskManager manager;

    private static final class DisplayBatch {
        int firstDate;
    }

    private RepeatController(int account) {
        manager = new RepeatTaskManager(account, new RepeatTaskManager.Scheduler() {
            public long now() { return SystemClock.elapsedRealtime(); }
            public void post(Runnable action, long delay) { AndroidUtilities.runOnUIThread(action, delay); }
            public void cancel(Runnable action) { AndroidUtilities.cancelRunOnUIThread(action); }
        });
        NotificationCenter center = NotificationCenter.getInstance(account);
        center.addObserver(this, NotificationCenter.messageReceivedByServer);
        center.addObserver(this, NotificationCenter.messageSendError);
        center.addObserver(this, NotificationCenter.appDidLogout);
        manager.addListener(task -> {
            if (task != null && task.state == RepeatTaskState.FAILED) {
                FileLog.e("nullgram repeat failed account=" + task.account + " submitted=" + task.submitted + " reason=" + task.error);
                Toast.makeText(ApplicationLoader.applicationContext,
                        LocaleController.formatString(R.string.NullgramRepeatFailed, errorText(task.error)), Toast.LENGTH_LONG).show();
            }
        });
    }

    public static RepeatTaskManager get(int account) {
        RepeatController controller = INSTANCES.get(account);
        if (controller == null) { controller = new RepeatController(account); INSTANCES.put(account, controller); }
        return controller.manager;
    }

    public static boolean startTask(int account, long dialogId, RepeatTask.Type type, int count, long intervalMs,
                                    int messagesPerRound, RepeatTaskManager.Sender sender) {
        return get(account).startTask(dialogId, type, count, intervalMs,
                withSharedDisplayTime(count, intervalMs, messagesPerRound, sender));
    }

    static RepeatTaskManager.Sender withSharedDisplayTime(int count, long intervalMs, int messagesPerRound,
                                                          RepeatTaskManager.Sender sender) {
        if (sender == null || !RepeatConfig.isValid(count, intervalMs) || intervalMs >= 60 || messagesPerRound <= 0
                || (long) count * messagesPerRound >= 6) return sender;
        DisplayBatch batch = new DisplayBatch();
        return new RepeatTaskManager.Sender() {
            @Override public String validate() { return sender.validate(); }
            @Override public String submissionError() { return sender.submissionError(); }
            @Override public List<Integer> send() {
                DISPLAY_BATCH.set(batch);
                try {
                    return sender.send();
                } finally {
                    DISPLAY_BATCH.remove();
                }
            }
        };
    }

    public static void cancelAll() {
        for (RepeatController controller : INSTANCES.values()) controller.manager.cancelTask();
    }

    /** 返回固定目标的实时拒绝原因；付费/特殊会话不做未经授权的重复付款。 */
    public static String validate(int account, long dialogId, boolean plainText) {
        if (!UserConfig.getInstance(account).isClientActivated() || UserConfig.selectedAccount != account) return "ACCOUNT_CHANGED";
        if (ApplicationLoader.mainInterfacePaused) return "APP_BACKGROUND";
        if (ConnectionsManager.getInstance(account).getConnectionState() != ConnectionsManager.ConnectionStateConnected) return "OFFLINE";
        MessagesController controller = MessagesController.getInstance(account);
        if (controller.getSendPaidMessagesStars(dialogId) > 0) return "PAID_MESSAGES_UNSUPPORTED";
        if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = controller.getChat(-dialogId);
            if (chat == null || ChatObject.isNotInChat(chat) || !ChatObject.canSendMessages(chat)
                    || plainText && !ChatObject.canSendPlain(chat)) return "CHAT_WRITE_FORBIDDEN";
            TLRPC.ChatFull full = controller.getChatFull(-dialogId);
            if (chat.slowmode_enabled && !ChatObject.hasAdminRights(chat)) {
                if (full == null) return "SLOWMODE_INFO_UNAVAILABLE";
                if (full.slowmode_next_send_date > ConnectionsManager.getInstance(account).getCurrentTime()) return "SLOWMODE_WAIT";
            }
        }
        return null;
    }

    private static final class Capture {
        final int account; final long dialogId; final LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        Capture(int account, long dialogId) { this.account = account; this.dialogId = dialogId; }
    }

    /** 发送接口同步创建的本地消息 ID；不通过相邻 UI 通知猜测所属任务。 */
    public static List<Integer> capture(int account, long dialogId, Runnable send) {
        if (CAPTURE.get() != null) throw new IllegalStateException("Nested repeat submission");
        Capture capture = new Capture(account, dialogId);
        CAPTURE.set(capture);
        try { send.run(); return new ArrayList<>(capture.ids); }
        finally { CAPTURE.remove(); }
    }

    public static void onMessageCreated(int account, TLRPC.Message message) {
        Capture capture = CAPTURE.get();
        if (capture != null && account == capture.account && message.id < 0
                && org.telegram.messenger.MessageObject.getDialogId(message) == capture.dialogId) {
            capture.ids.add(message.id);
            DisplayBatch batch = DISPLAY_BATCH.get();
            if (batch != null && message.date > 0) {
                if (batch.firstDate == 0) batch.firstDate = message.date;
                message.repeatDisplayDate = batch.firstDate;
            }
        }
    }

    /** 原始 RPC 限速原因在通知前交给管理器；不改变上游发送或重试逻辑。 */
    public static void onRpcError(int account, int localId, TLRPC.TL_error error) {
        if (error == null) return;
        String text = error.text != null && error.text.matches("[A-Z0-9_]+") ? error.text : "SEND_FAILED";
        Runnable report = () -> {
            RepeatController controller = INSTANCES.get(account);
            if (controller != null) controller.manager.onSendError(localId, text);
        };
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) report.run();
        else AndroidUtilities.runOnUIThread(report);
    }

    public static String errorText(String reason) {
        if (reason != null && (reason.startsWith("FLOOD_WAIT") || reason.startsWith("SLOWMODE_WAIT") || reason.startsWith("FLOOD_PREMIUM_WAIT"))) {
            return LocaleController.formatString(R.string.NullgramRepeatRateLimit, reason);
        }
        if ("CONFIRMATION_TIMEOUT".equals(reason)) return LocaleController.getString(R.string.NullgramRepeatTimeout);
        if ("UNSUPPORTED_MESSAGE".equals(reason) || "PAID_MESSAGES_UNSUPPORTED".equals(reason)) return LocaleController.getString(R.string.NullgramRepeatUnsupported);
        return LocaleController.getString(R.string.NullgramRepeatDenied) + " (" + reason + ")";
    }

    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messageReceivedByServer) manager.onConfirmed((Integer) args[0]);
        else if (id == NotificationCenter.messageSendError) manager.onSendError((Integer) args[0], null);
        else if (id == NotificationCenter.appDidLogout) {
            manager.cancelTask();
            manager.cleanupTask();
            NotificationCenter center = NotificationCenter.getInstance(account);
            center.removeObserver(this, NotificationCenter.messageReceivedByServer);
            center.removeObserver(this, NotificationCenter.messageSendError);
            center.removeObserver(this, NotificationCenter.appDidLogout);
            INSTANCES.remove(account);
        }
    }
}
