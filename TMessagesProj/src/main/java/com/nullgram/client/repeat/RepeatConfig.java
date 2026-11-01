package com.nullgram.client.repeat;

/** 重复发送的集中配置；人工维护时优先修改这里。 */
public final class RepeatConfig {
    public static final int EXISTING_DEFAULT_COUNT = 1;
    public static final int NEW_DEFAULT_COUNT = 5;
    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 100;
    public static final long DEFAULT_INTERVAL_MS = 3000;
    public static final long MIN_INTERVAL_MS = 10;
    public static final long MAX_INTERVAL_MS = 60000;
    public static final int MAX_TASKS_PER_ACCOUNT = 1;
    public static final long CONFIRMATION_TIMEOUT_MS = 120000;
    public static final boolean ENABLE_EXISTING = true;
    public static final boolean ENABLE_NEW = true;
    public static final boolean SHOW_STATUS = true;
    public static final boolean SHOW_STOP = true;

    private RepeatConfig() {}

    public static boolean isValid(int count, long intervalMs) {
        return count >= MIN_COUNT && count <= MAX_COUNT
                && intervalMs >= MIN_INTERVAL_MS && intervalMs <= MAX_INTERVAL_MS;
    }
}
