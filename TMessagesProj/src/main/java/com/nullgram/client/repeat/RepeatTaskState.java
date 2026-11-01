package com.nullgram.client.repeat;

/** COMPLETED 仅表示服务器确认，不能解释为收件人已读。 */
public enum RepeatTaskState { IDLE, RUNNING, COMPLETED, CANCELLED, FAILED }
