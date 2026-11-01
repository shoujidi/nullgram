package com.nullgram.client.repeat;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import java.util.ArrayList;
import java.util.List;

/** 文本发送快照；每轮新建参数，永远不读取后续输入框或当前聊天。 */
public final class NewMessageRepeater implements RepeatTaskManager.Sender {
    private final int account;
    private final long dialogId;
    private final String text;
    private final ArrayList<TLRPC.MessageEntity> entities;
    private final MessageObject reply, top;
    private final ChatActivity.ReplyQuote quote;
    private final TLRPC.WebPage webPage;
    private final boolean searchLinks;
    private final boolean allowGames;
    private Runnable firstSubmission;
    private boolean preservePreview, previewAbove, previewSmall;
    private long effectId;

    public NewMessageRepeater(int account, long dialogId, String text, ArrayList<TLRPC.MessageEntity> entities,
                              MessageObject reply, MessageObject top, ChatActivity.ReplyQuote quote,
                              TLRPC.WebPage webPage, boolean searchLinks) {
        this(account, dialogId, text, entities, reply, top, quote, webPage, searchLinks, false);
    }

    NewMessageRepeater(int account, long dialogId, String text, ArrayList<TLRPC.MessageEntity> entities,
                       MessageObject reply, MessageObject top, ChatActivity.ReplyQuote quote,
                       TLRPC.WebPage webPage, boolean searchLinks, boolean allowGames) {
        this.account = account; this.dialogId = dialogId; this.text = text;
        this.entities = entities == null ? null : new ArrayList<>(entities);
        this.reply = MessageSnapshot.copy(account, reply); this.top = MessageSnapshot.copy(account, top);
        this.quote = quote == null ? null : ChatActivity.ReplyQuote.from(this.reply, quote.text, quote.offset);
        this.webPage = webPage; this.searchLinks = searchLinks;
        this.allowGames = allowGames;
    }

    public String validate() {
        if (text == null || text.trim().isEmpty()) return "UNSUPPORTED_MESSAGE";
        return RepeatController.validate(account, dialogId, true);
    }

    /** 仅在第一条真实进入发送队列后消费原草稿；回调不得强引用界面。 */
    public void setFirstSubmission(Runnable callback) { firstSubmission = callback; }

    public void setPreviewOptions(boolean above, boolean small, long effectId) {
        preservePreview = true;
        previewAbove = above;
        previewSmall = small;
        this.effectId = effectId;
    }

    public List<Integer> send() {
        List<Integer> ids = RepeatController.capture(account, dialogId, () -> {
            SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(text, dialogId,
                    reply, top, webPage, searchLinks, entities == null ? null : new ArrayList<>(entities),
                    null, null, true, 0, 0, null, false);
            params.replyQuote = quote;
            params.canSendGames = allowGames; // 新输入作为文字；已有消息快捷复读沿用原 Emoji 语义。
            params.effect_id = effectId;
            if (preservePreview) {
                params.invert_media = previewAbove;
                if (webPage != null && !(webPage instanceof TLRPC.TL_webPagePending)) {
                    params.mediaWebPage = new TLRPC.TL_messageMediaWebPage();
                    params.mediaWebPage.webpage = webPage;
                    params.mediaWebPage.force_small_media = previewSmall;
                    params.mediaWebPage.force_large_media = !previewSmall;
                }
            }
            SendMessagesHelper.getInstance(account).sendMessage(params);
        });
        if (!ids.isEmpty() && firstSubmission != null) {
            Runnable callback = firstSubmission;
            firstSubmission = null;
            callback.run();
        }
        return ids;
    }
}
