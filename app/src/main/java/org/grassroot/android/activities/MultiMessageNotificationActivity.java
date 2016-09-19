package org.grassroot.android.activities;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;

import org.grassroot.android.R;
import org.grassroot.android.fragments.GroupChatFragment;
import org.grassroot.android.fragments.MultiGroupChatFragment;
import org.grassroot.android.fragments.NotificationCenterFragment;
import org.grassroot.android.interfaces.GroupConstants;
import org.grassroot.android.interfaces.NotificationConstants;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * Created by paballo on 2016/09/06.
 */
public class MultiMessageNotificationActivity extends PortraitActivity {

    private static final String TAG = MultiMessageNotificationActivity.class.getCanonicalName();
    private Unbinder unbinder;
    private String groupUid;
    private String groupName;
    private String clickAction;
    private Fragment fragment;

    @BindView(R.id.vca_toolbar)
    Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_activtity);
        unbinder = ButterKnife.bind(this);

        groupUid = getIntent().getStringExtra(GroupConstants.UID_FIELD);
        groupName = getIntent().getStringExtra(GroupConstants.NAME_FIELD);
        clickAction = getIntent().getStringExtra(NotificationConstants.CLICK_ACTION);

        if (NotificationConstants.CHAT_MESSAGE.equals(clickAction)) {
            fragment = createGroupChatFragment(groupUid, groupName);
        }
        if (NotificationConstants.CHAT_LIST.equals(clickAction)) {
            fragment = createGroupChatListFragment();
        }
        if (NotificationConstants.NOTIFICATION_LIST.equals(clickAction)) {
            fragment = createNotificationCenterFragment();
        }

        setUpToolbar();
        getSupportFragmentManager().beginTransaction().add(R.id.gca_fragment_holder, fragment,TAG)
                .commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_noti_messages, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.mi_group_mute).setVisible(false);
        menu.findItem(R.id.mi_delete_messages).setVisible(false);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbinder.unbind();

    }

    private void setUpToolbar() {
        setSupportActionBar(toolbar);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private Fragment createGroupChatFragment(String groupUid, String groupName) {
        this.setTitle(groupName);
        toolbar.setNavigationIcon(R.drawable.btn_close_white);
        GroupChatFragment groupChatFragment = GroupChatFragment.newInstance(groupUid, groupName);
        return groupChatFragment;
    }

    private Fragment createGroupChatListFragment() {
        toolbar.setNavigationIcon(R.drawable.btn_back_wt);
        MultiGroupChatFragment multiGroupChatFragment = MultiGroupChatFragment.newInstance();
        return multiGroupChatFragment;
    }

    private Fragment createNotificationCenterFragment(){
        this.setTitle(R.string.drawer_notis);
        return  new NotificationCenterFragment();
    }






}
