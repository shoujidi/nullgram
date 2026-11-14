package com.nullgram.client.appearance;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.TextView;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UiTestEnvironment;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.MainTabsLayout;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.glass.GlassTabView;
import org.telegram.ui.Cells.DialogCell;
import top.nkbe.niagram.helpers.MainTabsHelper;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = UiTestEnvironment.App.class, sdk = {28, 36},
    shadows = {UiTestEnvironment.LocalStrings.class, UiTestEnvironment.LocalSettings.class})
public class QqListLayoutTest {
    @Before public void setup() { AndroidUtilities.density = 1; LocaleController.isRTL = false; }
    @After public void cleanup() {
        LocaleController.isRTL = false;
        DisguiseListTheme.set(DisguiseListTheme.ORIGINAL);
    }
    static Context context(float fontScale) {
        Context base = RuntimeEnvironment.getApplication();
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.fontScale = fontScale;
        config.densityDpi = 160;
        return base.createConfigurationContext(config);
    }
    static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }
    static QqListHeader header(Context context) {
        QqListHeader header = new QqListHeader(context, () -> {});
        ((TextView) header.getChildAt(1)).setText("Alex Chen / a deliberately very long account nickname");
        AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable status = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(null, 22);
        status.set(new ColorDrawable(0xff0099ff), false);
        header.setStatusDrawable(status, () -> {});
        header.setConnectionState(ConnectionsManager.ConnectionStateConnected);
        return header;
    }
    @Test public void nicknameLeavesSpaceForBadgeAtAllWidthsFontScalesAndDirections() {
        for (boolean rtl : new boolean[]{false, true}) for (float scale : new float[]{1, 1.3f, 2}) {
            LocaleController.isRTL = rtl;
            Context ctx = context(scale);
            for (int width : new int[]{220, 280, 360, 600}) {
                QqListHeader header = header(ctx);
                layout(header, width, QqListStyle.headerHeight(ctx));
                View name = header.getChildAt(1), badge = header.getChildAt(2), status = header.getChildAt(3);
                assertEquals(View.VISIBLE, badge.getVisibility());
                assertTrue(badge.getLeft() >= 0 && badge.getRight() <= width);
                assertTrue(rtl ? badge.getRight() <= name.getLeft() : badge.getLeft() >= name.getRight());
                assertTrue(status.getTop() >= name.getBottom());
                assertTrue("scale=" + scale + " statusBottom=" + status.getBottom() + " header=" + header.getHeight(), status.getBottom() <= header.getHeight());
            }
        }
    }
    @Test public void disconnectedStatusDoesNotClaimOnlineAndDoesNotRemoveNickname() {
        QqListHeader header = header(context(1));
        TextView status = (TextView) header.getChildAt(3);
        String connected = status.getText().toString();
        for (int state : new int[]{1, 2, 4, 5}) {
            header.setConnectionState(state);
            assertNotEquals(connected, status.getText().toString());
        }
        assertTrue(((TextView) header.getChildAt(1)).getText().length() > 0);
    }
    @Test public void changingBadgeKeepsActionAndReleasesReservedWidthWhenRemoved() {
        QqListHeader header = header(context(1));
        layout(header, 260, 56);
        int withBadge = header.getChildAt(1).getWidth();
        assertTrue(header.getChildAt(2).performClick());
        header.setStatusDrawable(null, null);
        layout(header, 260, 56);
        assertEquals(View.GONE, header.getChildAt(2).getVisibility());
        assertTrue(header.getChildAt(1).getWidth() > withBadge);
    }
    static MainTabsLayout tabs(Context context, int selected) {
        MainTabsLayout tabs = new MainTabsLayout(context, null);
        tabs.setQqAppearance(true);
        for (int i = 0; i < 5; i++) {
            GlassTabView tab = new GlassTabView(context);
            tab.setQqAppearance(true, i);
            tabs.addView(tab);
            tabs.setViewVisible(tab, i != 2, false);
            tab.setSelected(i == selected, false);
        }
        return tabs;
    }
    @Test public void fourTabWidthsAreEqualAndCoverAvailableWidthOnAllPages() {
        for (int selected : new int[]{0, 1, 3, 4}) for (int width : new int[]{280, 361, 600, 900}) {
            MainTabsLayout tabs = tabs(context(1), selected);
            layout(tabs, width, 54);
            int total = 0;
            for (int i : new int[]{0, 1, 3, 4}) {
                View child = tabs.getChildAt(i);
                assertTrue(Math.abs(child.getMeasuredWidth() - width / 4f) <= 1);
                total += child.getMeasuredWidth();
                assertTrue(((GlassTabView) child).isTabSelected() == (i == selected));
            }
            assertEquals(width, total);
            assertEquals(0, tabs.getChildAt(2).getMeasuredWidth());
        }
    }
    @Test public void themeSwitchRestoresOriginalNavigationPreferences() {
        android.content.SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        java.util.Map<String, ?> before = new java.util.HashMap<>(prefs.getAll());
        int oldCount = MainTabsHelper.getFragmentsCount();
        boolean oldHidden = MainTabsHelper.isBottomNavigationHidden();
        int oldMargin = MainTabsHelper.getMainTabsMargin();
        boolean oldContactsHidden = MainTabsHelper.isContactsTabHidden();
        DisguiseListTheme.set(DisguiseListTheme.QQ);
        if (org.telegram.messenger.BuildConfig.NULLGRAM_DISGUISE) {
            assertEquals(4, MainTabsHelper.getFragmentsCount());
            assertFalse(MainTabsHelper.isBottomNavigationHidden());
            assertFalse(MainTabsHelper.isContactsTabHidden());
            assertEquals(0, MainTabsHelper.getMainTabsMargin());
        } else {
            assertFalse(DisguiseListTheme.isQq());
            assertEquals(oldCount, MainTabsHelper.getFragmentsCount());
            assertEquals(oldHidden, MainTabsHelper.isBottomNavigationHidden());
            assertEquals(oldContactsHidden, MainTabsHelper.isContactsTabHidden());
            assertEquals(oldMargin, MainTabsHelper.getMainTabsMargin());
        }
        DisguiseListTheme.set(DisguiseListTheme.ORIGINAL);
        assertEquals(oldCount, MainTabsHelper.getFragmentsCount());
        assertEquals(oldHidden, MainTabsHelper.isBottomNavigationHidden());
        assertEquals(oldMargin, MainTabsHelper.getMainTabsMargin());
        for (String key : before.keySet()) if (!key.equals("nullgramDisguiseListTheme")) assertEquals(before.get(key), prefs.getAll().get(key));
    }
    @Test public void tabCentersStayInTheirFourSlotsAfterResizeAndRtl() {
        for (boolean rtl : new boolean[]{false, true}) {
            LocaleController.isRTL = rtl;
            MainTabsLayout tabs = tabs(context(1), 0);
            for (int width : new int[]{360, 600, 280, 361}) {
                layout(tabs, width, QqListStyle.NAV_HEIGHT);
                int slot = 0;
                for (int index : new int[]{0, 1, 3, 4}) {
                    View child = tabs.getChildAt(index);
                    float expected = width * (slot++ + .5f) / 4;
                    if (rtl) expected = width - expected;
                    assertEquals(expected, child.getX() + child.getWidth() / 2f, 1f);
                    assertTrue(child.getX() >= 0 && child.getX() + child.getWidth() <= width);
                    assertEquals(1f, child.getScaleX(), 0);
                }
            }
        }
    }

    @Test public void qqStatusMapsConnectionStatesWithoutClaimingUnknownIsOnline() {
        QqListHeader header = header(context(1));
        TextView status = (TextView) header.getChildAt(3);
        int[][] states = {
            {3, org.telegram.messenger.R.string.NullgramQqConnected},
            {2, org.telegram.messenger.R.string.NullgramQqOffline},
            {1, org.telegram.messenger.R.string.Connecting},
            {4, org.telegram.messenger.R.string.Connecting},
            {5, org.telegram.messenger.R.string.NullgramQqSyncing},
            {0, org.telegram.messenger.R.string.NullgramQqOffline}
        };
        for (int[] state : states) {
            header.setConnectionState(state[0]);
            assertEquals(LocaleController.getString(state[1]), status.getText().toString());
        }
        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(status.getCurrentTextColor(), android.graphics.Color.WHITE) >= 4.5);
        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(QqTabPainter.LABEL_NORMAL, 0xfffafafa) >= 4.5);
        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(QqTabPainter.LABEL_ACTIVE, 0xfffafafa) >= 4.5);
    }

    @Test public void qqRowMeasuresAsTwoLinesWithoutChangingNativeThreeLinePreference() {
        boolean previous = org.telegram.messenger.SharedConfig.useThreeLinesLayout;
        try {
            org.telegram.messenger.SharedConfig.useThreeLinesLayout = true;
            DialogCell qq = new DialogCell(null, context(1), false, true, 0, DisguiseListTheme.wrap(null, true));
            qq.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            assertTrue(qq.getMeasuredHeight() >= 72 && qq.getMeasuredHeight() <= 73);
            assertTrue(org.telegram.messenger.SharedConfig.useThreeLinesLayout);
            DialogCell normal = new DialogCell(null, context(1), false, true, 0, null);
            normal.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            assertTrue(normal.getMeasuredHeight() > qq.getMeasuredHeight());
        } finally { org.telegram.messenger.SharedConfig.useThreeLinesLayout = previous; }
    }
}
