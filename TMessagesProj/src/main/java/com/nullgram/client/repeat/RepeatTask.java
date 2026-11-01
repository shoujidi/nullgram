package com.nullgram.client.repeat;

/** 不持有界面或消息对象的任务快照。多选消息的 count 表示整组重复轮数。 */
public final class RepeatTask {
    public enum Type { EXISTING, NEW_MESSAGE }
    public final int account;
    public final long dialogId;
    public final Type type;
    public final int total;
    public final long intervalMs;
    public final RepeatTaskState state;
    public final int submitted;
    public final int confirmed;
    public final int submittedMessages;
    public final int confirmedMessages;
    public final String error;

    RepeatTask(int account, long dialogId, Type type, int total, long intervalMs,
               RepeatTaskState state, int submitted, int confirmed,
               int submittedMessages, int confirmedMessages, String error) {
        this.account = account;
        this.dialogId = dialogId;
        this.type = type;
        this.total = total;
        this.intervalMs = intervalMs;
        this.state = state;
        this.submitted = submitted;
        this.confirmed = confirmed;
        this.submittedMessages = submittedMessages;
        this.confirmedMessages = confirmedMessages;
        this.error = error;
    }

    public int remaining() { return Math.max(0, total - submitted); }
}
