package com.nullgram.client.appearance;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.text.StaticLayout;
import android.view.View;
import android.widget.LinearLayout;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.UiTestEnvironment;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Stories.StoriesUtilities;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = UiTestEnvironment.App.class, sdk = 28,
        shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class, QqOtherPagesTest.OfflineAvatars.class})
public class QqOtherPagesTest {
    // A signed-out JVM has no native story service. Render the real ImageReceiver placeholder;
    // this component test does not claim to exercise live stories or account/network behavior.
    @Implements(StoriesUtilities.class)
    public static class OfflineAvatars {
        @Implementation protected static void drawAvatarWithStory(long id, Canvas canvas, ImageReceiver receiver, StoriesUtilities.AvatarStoryParams params) {
            receiver.setImageCoords(params.originalAvatarRect);
            receiver.draw(canvas);
        }
    }
    @After public void cleanup() { LocaleController.isRTL = false; }

    private static void measure(View view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static ProfileSearchCell call(float scale, int width) {
        AndroidUtilities.density = 1;
        ProfileSearchCell cell = new ProfileSearchCell(QqListLayoutTest.context(scale), QqPageStyle.wrap(null, true));
        cell.setCallCellStyle();
        cell.setPadding(LocaleController.isRTL ? 44 : 0, 0, LocaleController.isRTL ? 0 : 44, 0);
        TLRPC.Chat chat = new TLRPC.TL_chat(); chat.title = "Alex Chen";
        ReflectionHelpers.setField(cell, "chat", chat);
        ReflectionHelpers.setField(cell, "currentName", "Alex Chen");
        ReflectionHelpers.setField(cell, "subLabel", "Today, 10:30");
        AvatarDrawable avatar = new AvatarDrawable(); avatar.setInfo(0, "Alex", "Chen");
        cell.avatarImage.setImageBitmap(avatar);
        measure(cell, width);
        return cell;
    }

    @Test public void callRowsKeepBothLinesInsideAndLeaveRoomForTheCallButton() {
        for (boolean rtl : new boolean[]{false, true}) for (float scale : new float[]{1, 1.6f}) {
            LocaleController.isRTL = rtl;
            ProfileSearchCell cell = call(scale, 320);
            StaticLayout name = ReflectionHelpers.getField(cell, "nameLayout");
            StaticLayout status = ReflectionHelpers.getField(cell, "statusLayout");
            int nameTop = ReflectionHelpers.getField(cell, "nameTop");
            int statusTop = ReflectionHelpers.getField(cell, "qqStatusTop");
            int nameLeft = ReflectionHelpers.getField(cell, "nameLeft");
            assertTrue(statusTop >= nameTop + name.getHeight());
            assertTrue(statusTop + status.getHeight() <= cell.getHeight());
            assertTrue(rtl ? nameLeft >= 56 : nameLeft + name.getWidth() <= 320 - 56);
        }
        ProfileSearchCell nativeCell = new ProfileSearchCell(QqListLayoutTest.context(1));
        nativeCell.setCallCellStyle(); measure(nativeCell, 320);
        assertEquals(56, nativeCell.getMeasuredHeight());
    }

    @Test public void meCardFitsNarrowLargeFontAndRtlAndEveryEntryRemainsClickable() {
        AndroidUtilities.density = 1;
        for (boolean rtl : new boolean[]{false, true}) for (float scale : new float[]{1, 1.6f}) {
            LocaleController.isRTL = rtl;
            ArrayList<QqMeContent.Destination> clicks = new ArrayList<>();
            QqMeContent content = new QqMeContent(QqListLayoutTest.context(scale), clicks::add);
            content.name.setText("Alex Chen / long account name");
            content.subtitle.setText("@alex_chen");
            measure(content, 320);
            View identity = (View) content.name.getParent();
            assertTrue(rtl ? identity.getRight() <= content.avatar.getLeft() : identity.getLeft() >= content.avatar.getRight());
            assertTrue(identity.getBottom() <= content.accountCard.getHeight());
            assertTrue(content.subtitle.getTop() >= content.name.getBottom());
            assertTrue(content.accountCard.performClick());
            LinearLayout menu = (LinearLayout) content.getChildAt(1);
            for (int i = 0; i < menu.getChildCount(); i++) assertTrue(menu.getChildAt(i).performClick());
            assertEquals(Arrays.asList(QqMeContent.Destination.PROFILE, QqMeContent.Destination.PROFILE,
                    QqMeContent.Destination.SAVED_MESSAGES, QqMeContent.Destination.DISGUISE, QqMeContent.Destination.SETTINGS), clicks);
        }
    }

    @Test public void renderRealComponents() throws Exception {
        AndroidUtilities.density = 1;
        save(call(1, 360), "calls.png");
        QqMeContent content = new QqMeContent(QqListLayoutTest.context(1), destination -> {});
        content.name.setText("Alex Chen"); content.subtitle.setText("@alex_chen");
        AvatarDrawable avatar = new AvatarDrawable(); avatar.setInfo(0, "Alex", "Chen");
        content.avatar.setImageDrawable(avatar);
        measure(content, 360); save(content, "me.png");
    }

    @Test public void mePageReservesMeasuredHeaderInTheOverlayPager() {
        AndroidUtilities.density = 1;
        android.content.Context context = QqListLayoutTest.context(1);
        View body = new View(context);
        View header = new View(context) {
            @Override protected void onMeasure(int width, int height) {
                setMeasuredDimension(MeasureSpec.getSize(width), getMinimumHeight());
            }
        };
        QqMeActivity.PageLayout page = new QqMeActivity.PageLayout(context, body, header);
        for (int headerHeight : new int[]{56, 80, 96}) {
            header.setMinimumHeight(headerHeight);
            QqListLayoutTest.layout(page, 360, 640);
            assertEquals(headerHeight, header.getMeasuredHeight());
            assertEquals(header.getBottom(), body.getTop());
            assertEquals(640, body.getBottom());
        }
    }

    private static void save(View view, String name) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        File output = new File("build/reports/qq-home/" + name); output.getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(output)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)); }
        bitmap.recycle();
    }
}
