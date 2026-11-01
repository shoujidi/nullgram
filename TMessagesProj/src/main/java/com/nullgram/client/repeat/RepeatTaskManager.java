package com.nullgram.client.repeat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 单账号、单线程状态机。生产环境在 UI 线程运行，测试使用虚拟时钟。 */
public final class RepeatTaskManager {
    public interface Scheduler {
        long now();
        void post(Runnable action, long delayMs);
        void cancel(Runnable action);
    }
    public interface Sender {
        /** 每轮重新验证固定的账号、会话、权限和限速；null 表示允许。 */
        String validate();
        /** 使用上游发送接口，返回本轮真实生成的本地消息 ID；不得重试。 */
        List<Integer> send();
        default String submissionError() { return null; }
    }
    public interface Listener { void onChanged(RepeatTask task); }

    private final int account;
    private final Scheduler scheduler;
    private static final class PendingRound {
        int remaining;
        final long deadline;
        PendingRound(int remaining, long deadline) { this.remaining = remaining; this.deadline = deadline; }
    }

    private final Map<Integer, PendingRound> pending = new HashMap<>();
    private final Set<Listener> listeners = new HashSet<>();
    private RepeatTask task;
    private Sender sender;
    private long generation;
    private final Runnable tick = this::submit;
    private final Runnable timeout = () -> fail("CONFIRMATION_TIMEOUT");

    public RepeatTaskManager(int account, Scheduler scheduler) {
        this.account = account;
        this.scheduler = scheduler;
    }

    /** 输入内容快照与目标；返回 false 时调用方必须保留原草稿。 */
    public boolean startTask(long dialogId, RepeatTask.Type type, int count, long intervalMs, Sender sender) {
        if (getTaskState() == RepeatTaskState.RUNNING || dialogId == 0 || sender == null || type == null
                || !RepeatConfig.isValid(count, intervalMs)
                || type == RepeatTask.Type.EXISTING && !RepeatConfig.ENABLE_EXISTING
                || type == RepeatTask.Type.NEW_MESSAGE && !RepeatConfig.ENABLE_NEW) return false;
        String error = sender.validate();
        task = new RepeatTask(account, dialogId, type, count, intervalMs,
                error == null ? RepeatTaskState.RUNNING : RepeatTaskState.FAILED, 0, 0, 0, 0, error);
        this.sender = error == null ? sender : null;
        generation++;
        long activeGeneration = generation;
        pending.clear();
        changed();
        if (error != null) return false;
        if (generation == activeGeneration && getTaskState() == RepeatTaskState.RUNNING) scheduler.post(tick, 0);
        return true;
    }

    public RepeatTaskState getTaskState() { return task == null ? RepeatTaskState.IDLE : task.state; }
    public RepeatTask getProgress() { return task; }
    public void addListener(Listener listener) { listeners.add(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }

    /** 立即移除未执行的调度，已经提交的消息交由上游发送系统处理。 */
    public void cancelTask() {
        if (getTaskState() == RepeatTaskState.RUNNING) finish(RepeatTaskState.CANCELLED, null);
    }

    public void cleanupTask() {
        if (getTaskState() == RepeatTaskState.RUNNING) return;
        scheduler.cancel(tick);
        scheduler.cancel(timeout);
        sender = null;
        pending.clear();
        task = null;
        changed();
    }

    /** 只接受本任务实际创建的消息 ID，普通发送的通知不会推进任务。 */
    public void onConfirmed(int localId) {
        if (getTaskState() != RepeatTaskState.RUNNING) return;
        PendingRound round = pending.remove(localId);
        if (round == null) return;
        long activeGeneration = generation;
        task = copy(task.state, task.submitted, task.confirmed + (--round.remaining == 0 ? 1 : 0),
                task.submittedMessages, task.confirmedMessages + 1, null);
        changed();
        if (generation != activeGeneration || getTaskState() != RepeatTaskState.RUNNING) return;
        if (task.submitted == task.total && pending.isEmpty()) finish(RepeatTaskState.COMPLETED, null);
        else scheduleTimeout();
    }

    public void onSendError(int localId, String reason) {
        if (getTaskState() == RepeatTaskState.RUNNING && pending.containsKey(localId)) {
            fail(reason == null ? "SEND_FAILED" : reason);
        }
    }

    private void submit() {
        if (getTaskState() != RepeatTaskState.RUNNING || task.submitted >= task.total) return;
        long activeGeneration = generation;
        try {
            String error = sender.validate();
            if (error != null) { fail(error); return; }
            long submittedAt = scheduler.now();
            List<Integer> ids = sender.send();
            if (generation != activeGeneration || getTaskState() != RepeatTaskState.RUNNING) return;
            if (ids == null || ids.isEmpty()) { fail("UNSUPPORTED_MESSAGE"); return; }
            Set<Integer> unique = new HashSet<>(ids);
            if (unique.contains(null) || unique.size() != ids.size()) { fail("INVALID_MESSAGE_IDS"); return; }
            for (int id : unique) if (pending.containsKey(id)) { fail("INVALID_MESSAGE_IDS"); return; }
            PendingRound round = new PendingRound(unique.size(), submittedAt + RepeatConfig.CONFIRMATION_TIMEOUT_MS);
            for (int id : unique) pending.put(id, round);
            task = copy(task.state, task.submitted + 1, task.confirmed,
                    task.submittedMessages + unique.size(), task.confirmedMessages, null);
            String errorAfterSend = sender.submissionError();
            if (errorAfterSend != null) { fail(errorAfterSend); return; }
            changed();
            if (generation != activeGeneration || getTaskState() != RepeatTaskState.RUNNING) return;
            scheduleTimeout();
            // 按实际提交时间安排下一轮，回执只更新进度，不触发发送；延迟后不补发积压轮次。
            if (task.submitted < task.total) scheduler.post(tick, Math.max(0, submittedAt + task.intervalMs - scheduler.now()));
        } catch (RuntimeException error) {
            // 不重复提交；日志仅记录异常类别，不写入消息正文。
            if (generation == activeGeneration) fail("SEND_EXCEPTION:" + error.getClass().getSimpleName());
        }
    }

    /** 每条消息沿用所属轮次的原始截止时间，后续发送或部分回执不能延长超时。 */
    private void scheduleTimeout() {
        scheduler.cancel(timeout);
        long deadline = Long.MAX_VALUE;
        for (PendingRound round : pending.values()) deadline = Math.min(deadline, round.deadline);
        if (!pending.isEmpty()) scheduler.post(timeout, Math.max(0, deadline - scheduler.now()));
    }

    private RepeatTask copy(RepeatTaskState state, int submitted, int confirmed, int messages, int confirmedMessages, String error) {
        return new RepeatTask(account, task.dialogId, task.type, task.total, task.intervalMs,
                state, submitted, confirmed, messages, confirmedMessages, error);
    }

    private void fail(String reason) {
        if (getTaskState() == RepeatTaskState.RUNNING) finish(RepeatTaskState.FAILED, reason);
    }

    private void finish(RepeatTaskState state, String reason) {
        scheduler.cancel(tick);
        scheduler.cancel(timeout);
        sender = null;
        pending.clear();
        task = copy(state, task.submitted, task.confirmed, task.submittedMessages, task.confirmedMessages, reason);
        changed();
    }

    private void changed() {
        for (Listener listener : new ArrayList<>(listeners)) listener.onChanged(task);
    }
}
