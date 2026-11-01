package com.nullgram.client.repeat;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import java.util.HashMap;

/** 深复制 TL 消息数据，弹窗关闭和菜单清空不会改变任务输入。 */
final class MessageSnapshot {
    private MessageSnapshot() {}
    static MessageObject copy(int account, MessageObject original) {
        if (original == null) return null;
        SerializedData output = new SerializedData(original.messageOwner.getObjectSize());
        original.messageOwner.serializeToStream(output);
        SerializedData input = new SerializedData(output.toByteArray());
        try {
            TLRPC.Message owner = TLRPC.Message.TLdeserialize(input, input.readInt32(true), true);
            owner.attachPath = original.messageOwner.attachPath;
            owner.dialog_id = original.getDialogId();
            if (original.messageOwner.params != null) owner.params = new HashMap<>(original.messageOwner.params);
            MessageObject copy = new MessageObject(account, owner, false, true);
            copy.isTopicMainMessage = original.isTopicMainMessage;
            return copy;
        } finally { input.cleanup(); output.cleanup(); }
    }
}
