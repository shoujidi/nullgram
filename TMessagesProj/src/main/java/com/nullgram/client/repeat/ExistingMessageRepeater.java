package com.nullgram.client.repeat;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.List;

/** 复用上游整组转发/副本接口；长按快速复读保留原文字/贴纸回复语义。 */
public final class ExistingMessageRepeater implements RepeatTaskManager.Sender {
    private final int account;
    private final long dialogId;
    private final ArrayList<MessageObject> messages = new ArrayList<>();
    private final boolean asCopy;
    private final MessageObject top;
    private final NewMessageRepeater textSender;
    private final MessageObject stickerReply;
    private final boolean specialSticker;
    private String sendError;

    public ExistingMessageRepeater(int account, long dialogId, List<MessageObject> selected, boolean asCopy,
                                   MessageObject top, boolean special, String text, MessageObject reply) {
        this.account = account; this.dialogId = dialogId; this.asCopy = asCopy;
        this.top = MessageSnapshot.copy(account, top);
        for (MessageObject message : selected) messages.add(MessageSnapshot.copy(account, message));
        textSender = special && text != null && !text.isEmpty()
                ? new NewMessageRepeater(account, dialogId, text, messages.get(0).messageOwner.entities, reply, top, null, null, false, true) : null;
        specialSticker = special && textSender == null && messages.size() == 1
                && (messages.get(0).isSticker() || messages.get(0).isAnimatedSticker());
        stickerReply = MessageSnapshot.copy(account, reply);
        if (special && textSender == null && !specialSticker) sendError = "UNSUPPORTED_MESSAGE";
    }

    public String validate() {
        if (sendError != null || messages.isEmpty()) return "UNSUPPORTED_MESSAGE";
        MessagesController controller = MessagesController.getInstance(account);
        for (MessageObject message : messages) {
            TLRPC.Chat source = message.getDialogId() < 0 ? controller.getChat(-message.getDialogId()) : null;
            boolean invalidId = message.getId() == 0 || message.getId() < 0 && !DialogObject.isEncryptedDialog(message.getDialogId());
            if (invalidId || message.isSending() || message.isSendError() || message.messageOwner instanceof TLRPC.TL_messageService || message.messageOwner.noforwards
                    || message.isEphemeral() || controller.isChatNoForwards(source)) return "UNSUPPORTED_MESSAGE";
        }
        return RepeatController.validate(account, dialogId, textSender != null);
    }

    public List<Integer> send() {
        if (textSender != null) return textSender.send();
        return RepeatController.capture(account, dialogId, () -> {
            if (specialSticker) {
                SendMessagesHelper.getInstance(account).sendSticker(messages.get(0).getDocument(), null, dialogId,
                        stickerReply, top, null, null, null, true, 0, 0, false, null, null, 0, 0, null);
            } else {
                int result = SendMessagesHelper.getInstance(account).sendMessage(new ArrayList<>(messages), dialogId,
                        asCopy, false, true, 0, top, -1, 0);
                if (result != 0) sendError = "MEDIA_PERMISSION_" + result;
            }
        });
    }

    @Override public String submissionError() { return sendError; }

    public int messagesPerRound() {
        return textSender != null || specialSticker ? 1 : messages.size();
    }
}
