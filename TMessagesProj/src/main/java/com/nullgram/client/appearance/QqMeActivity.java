package com.nullgram.client.appearance;

import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SettingsActivity;
import org.telegram.ui.Components.LayoutHelper;
import top.nkbe.niagram.helpers.MainTabsHelper;

/** Lightweight QQ home tab; full profile features remain in ProfileActivity. */
public final class QqMeActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate, NotificationCenter.NotificationCenterDelegate {
    private ScrollView scroll;
    private QqMeContent content;

    @Override public boolean onFragmentCreate() {
        if (!super.onFragmentCreate()) return false;
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        return true;
    }

    @Override public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        super.onFragmentDestroy();
    }

    @Override public ActionBar createActionBar(Context context) {
        resourceProvider = QqPageStyle.wrap(resourceProvider, true);
        ActionBar bar = super.createActionBar(context);
        QqPageStyle.header(bar);
        bar.setAddToContainer(false);
        return bar;
    }

    @Override public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.NullgramQqTabMe));
        actionBar.setCastShadows(false);
        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(QqPageStyle.BACKGROUND);
        scroll.setPadding(0, 0, 0, AndroidUtilities.dp(MainTabsHelper.getMainTabsHeightWithMargins()) + AndroidUtilities.navigationBarHeight);
        content = new QqMeContent(context, this::open);
        scroll.addView(content);
        fragmentView = new PageLayout(context, scroll, actionBar);
        bind();
        return fragmentView;
    }

    private void open(QqMeContent.Destination destination) {
        BaseFragment target;
        Bundle args = new Bundle();
        switch (destination) {
            case PROFILE:
                args.putLong("user_id", getUserConfig().getClientUserId());
                args.putBoolean("my_profile", true);
                target = new ProfileActivity(args);
                break;
            case SAVED_MESSAGES:
                args.putLong("user_id", getUserConfig().getClientUserId());
                target = new ChatActivity(args);
                break;
            case DISGUISE: target = new DisguiseSettingsActivity(); break;
            default: target = new SettingsActivity(); break;
        }
        target.setCurrentAccount(currentAccount);
        presentFragment(target);
    }

    private void bind() { if (content != null) content.bind(currentAccount, getUserConfig().getCurrentUser()); }
    @Override public void onResume() { super.onResume(); bind(); }
    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces && args.length > 0 && args[0] instanceof Integer) {
            int mask = (Integer) args[0];
            if ((mask & (MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_AVATAR
                    | MessagesController.UPDATE_MASK_EMOJI_STATUS | LocalAvatarStore.CHANGE_MASK)) != 0) bind();
        }
    }
    @Override public boolean canParentTabsSlide(MotionEvent event, boolean forward) { return true; }
    @Override public void onParentScrollToTop() { if (scroll != null) scroll.smoothScrollTo(0, 0); }

    // MainTabs' pager overlays a separately attached ActionBar. Own the layout here so
    // the profile card stays below the measured bar, including status-bar/tablet sizes.
    static final class PageLayout extends FrameLayout {
        private final View body, header;
        PageLayout(Context context, View body, View header) {
            super(context);
            this.body = body; this.header = header;
            setBackgroundColor(QqPageStyle.BACKGROUND);
            addView(body, LayoutHelper.createFrame(-1, -1));
            addView(header, LayoutHelper.createFrame(-1, -2));
        }
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            measureChildWithMargins(header, widthMeasureSpec, 0, heightMeasureSpec, 0);
            ((LayoutParams) body.getLayoutParams()).topMargin = header.getMeasuredHeight();
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
