package com.nullgram.client.repeat;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Looper;
import android.util.SparseArray;
import android.view.View;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessageSuggestionParams;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UiTestEnvironment;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatActivityEnterView;
import top.nkbe.niagram.config.NyaConfig;
import top.nkbe.niagram.helpers.MessageHelper;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;

/** Executes ChatActivity's real repeat and legacy forwarding code; only network
 * endpoints and account/view constructors are substituted. Never sends to Telegram. */
@RunWith(RobolectricTestRunner.class)
@Config(application = UiTestEnvironment.App.class, sdk = {28, 36},
        shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class QuickRepeatTest {
    private Activity activity;
    private Probe chat;
    private Object confirmation;

    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        chat = allocate(Probe.class);
        chat.activity = activity;
        chat.helper = allocate(CopyEndpoint.class);
        chat.sender = allocate(SendEndpoint.class);
        chat.input(allocate(InputEndpoint.class));
        chat.selectedMessagesIds = new SparseArray[]{new SparseArray<>(), new SparseArray<>()};
        ReflectionHelpers.setField(chat, "dialog_id", 123L);
        confirmation = NyaConfig.repeatConfirm.value;
        NyaConfig.repeatConfirm.value = true;
    }
    @After public void cleanup() {
        NyaConfig.repeatConfirm.value = confirmation;
        if (chat.lastDialog != null) chat.lastDialog.dismiss();
        activity.finish();
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> allocatorClass = Class.forName("sun.misc.Unsafe");
        Field field = allocatorClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(allocatorClass.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }
    private MessageObject message(int id) throws Exception {
        MessageObject result = allocate(MessageObject.class);
        result.messageOwner = new TLRPC.TL_message();
        result.messageOwner.id = id;
        result.messageText = "repeat test";
        return result;
    }
    private void selected(MessageObject message) { ReflectionHelpers.setField(chat,"selectedObject",message); }
    private void quick(boolean longClick) {
        ReflectionHelpers.callInstanceMethod(chat,"repeatMessage",
                ClassParameter.from(boolean.class,longClick), ClassParameter.from(boolean.class,true),
                ClassParameter.from(boolean.class,true));
    }
    @Test public void quickUsesOriginalNoAttributionPathOnceWithoutDialogsOrDelayedRepeats() throws Exception {
        MessageObject message = message(10); selected(message);
        quick(false);
        assertEquals(1,chat.sender.calls);
        assertTrue(chat.sender.withoutAttribution);
        assertEquals(123L,chat.sender.target);
        assertSame(message,chat.sender.messages.get(0));
        assertEquals(0,chat.dialogs);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        assertEquals(1,chat.sender.calls);
    }
    @Test public void selectedGroupIsSubmittedOnlyOnceAsTheOriginalGroup() throws Exception {
        MessageObject first=message(11),second=message(12);
        chat.selectedMessagesIds[0].put(11,first);chat.selectedMessagesIds[0].put(12,second);
        quick(false);
        assertEquals(1,chat.sender.calls);
        assertEquals(2,chat.sender.messages.size());
        assertSame(first,chat.sender.messages.get(0));assertSame(second,chat.sender.messages.get(1));
        assertTrue(chat.sender.withoutAttribution);assertEquals(0,chat.dialogs);
    }
    @Test public void slowModeStillBlocksQuickRepeat() throws Exception {
        selected(message(10));chat.blocked=true;quick(false);
        assertEquals(0,chat.sender.calls);assertEquals(0,chat.helper.calls);assertEquals(0,chat.dialogs);
    }
    @Test public void emptySelectionDoesNotSendAnything() {
        quick(false);assertEquals(0,chat.sender.calls);assertEquals(0,chat.helper.calls);
    }
    @Test public void originalReplyContextAndCopyEndpointArePreserved() throws Exception {
        MessageObject message=message(10),reply=message(9);message.replyMessageObject=reply;selected(message);
        quick(true);
        assertEquals(1,chat.helper.calls);assertEquals(0,chat.sender.calls);
        assertSame(reply,chat.helper.reply);assertEquals(0,chat.dialogs);
    }
    @Test public void originalConfigurableActionStillUsesItsConfirmationDialog() throws Exception {
        selected(message(10));
        ReflectionHelpers.callInstanceMethod(chat,"repeatMessage",ClassParameter.from(boolean.class,false),ClassParameter.from(boolean.class,true));
        assertEquals(1,chat.dialogs);assertEquals(0,chat.sender.calls);assertEquals(0,chat.helper.calls);
    }

    public static class Probe extends ChatActivity {
        Activity activity; CopyEndpoint helper; SendEndpoint sender; boolean blocked; int dialogs; Dialog lastDialog;
        public Probe(){super(new Bundle());}
        void input(ChatActivityEnterView view){chatActivityEnterView=view;}
        @Override public boolean checkSlowMode(View view){return blocked;}
        @Override public boolean checkSlowModeAlert(){return !blocked;}
        @Override public MessageHelper getMessageHelper(){return helper;}
        @Override public SendMessagesHelper getSendMessagesHelper(){return sender;}
        @Override public Activity getParentActivity(){return activity;}
        @Override public Dialog showDialog(Dialog dialog){dialogs++;lastDialog=dialog;return dialog;}
    }
    public static class InputEndpoint extends ChatActivityEnterView {
        public InputEndpoint(){super(null,null,null,false);}
        @Override public View getSendButton(){return null;}
    }
    public static class CopyEndpoint extends MessageHelper {
        int calls;MessageObject reply;
        public CopyEndpoint(){super(0);}
        @Override public boolean shouldRepeatMessagesAsCopy(ArrayList<MessageObject> messages,TLRPC.Chat currentChat){return false;}
        @Override public boolean sendMessageAsCopy(MessageObject message,MessageObject.GroupedMessages group,long target,MessageObject replyTo,MessageObject top,ChatActivity.ReplyQuote quote,boolean notify,int schedule,int mode,String shortcut,int shortcutId,long stars,long forum,MessageSuggestionParams suggestion){calls++;reply=replyTo;return true;}
    }
    public static class SendEndpoint extends SendMessagesHelper {
        int calls;boolean withoutAttribution;long target;ArrayList<MessageObject> messages;
        public SendEndpoint(){super(0);}
        @Override public int sendMessage(ArrayList<MessageObject> selected,long peer,boolean fromMyName,boolean hideCaption,boolean notify,int schedule,int repeatPeriod,MessageObject thread,int videoTimestamp,long stars,long forum,MessageSuggestionParams suggestion){calls++;target=peer;withoutAttribution=fromMyName;messages=new ArrayList<>(selected);return 0;}
    }
}
