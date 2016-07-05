package org.grassroot.android.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.grassroot.android.BuildConfig;
import org.grassroot.android.R;
import org.grassroot.android.activities.FAQActivity;
import org.grassroot.android.activities.HomeScreenActivity;
import org.grassroot.android.activities.NotificationCenter;
import org.grassroot.android.activities.ProfileSettingsActivity;
import org.grassroot.android.activities.StartActivity;
import org.grassroot.android.adapters.NavigationDrawerAdapter;
import org.grassroot.android.events.NotificationEvent;
import org.grassroot.android.events.UserLoggedOutEvent;
import org.grassroot.android.fragments.dialogs.ConfirmCancelDialogFragment;
import org.grassroot.android.interfaces.ClickListener;
import org.grassroot.android.interfaces.NavigationConstants;
import org.grassroot.android.interfaces.NotificationConstants;
import org.grassroot.android.models.NavDrawerItem;
import org.grassroot.android.services.ApplicationLoader;
import org.grassroot.android.services.GcmRegistrationService;
import org.grassroot.android.adapters.RecyclerTouchListener;
import org.grassroot.android.utils.Constant;
import org.grassroot.android.utils.PreferenceUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;


public class NavigationDrawerFragment extends Fragment {

    public static final String TAG = NavigationDrawerFragment.class.getCanonicalName();

    private NavigationDrawerCallbacks mCallbacks;

    List<NavDrawerItem> draweritems;
    private NavigationDrawerAdapter drawerAdapter;
    private int currentlySelectedItem = NavigationConstants.HOME_NAV_GROUPS;

    @BindView(R.id.rv_nav_items) RecyclerView mDrawerRecyclerView;
    @BindView(R.id.txt_version) TextView txtVersion;
    @BindView(R.id.displayName) TextView displayName;

    public interface NavigationDrawerCallbacks {
        void onNavigationDrawerItemSelected(int position);
    }

    public NavigationDrawerFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mCallbacks = (NavigationDrawerCallbacks) context;
        } catch (ClassCastException e) {
            throw new UnsupportedOperationException("Error! Activity must implement listener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_navigation_drawer, container, false);
        ButterKnife.bind(this, view);

        displayName.setText(PreferenceUtils.getUserName(ApplicationLoader.applicationContext));
        txtVersion.setText("v " + BuildConfig.VERSION_NAME);

        drawerAdapter = new NavigationDrawerAdapter(getActivity(), getData());

        mDrawerRecyclerView.setHasFixedSize(true);
        mDrawerRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mDrawerRecyclerView.setAdapter(drawerAdapter);
        mDrawerRecyclerView.setItemAnimator(new DefaultItemAnimator());

        mDrawerRecyclerView.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), mDrawerRecyclerView, new ClickListener() {
            @Override
            public void onClick(View view, int position) {
                selectItem(position);
            }

            @Override
            public void onLongClick(View view, int position) {
            }
        }));

        return view ;
    }

    public void resetToDefault() {
        switchSelectedState(NavigationConstants.HOME_NAV_GROUPS);
    }

    public List<NavDrawerItem> getData() {
        draweritems = new ArrayList<>();
        draweritems.add(new NavDrawerItem(getString(R.string.drawer_group_list), R.drawable.ic_home, R.drawable.ic_home_green, true));
        draweritems.add(new NavDrawerItem(getString(R.string.drawer_open_tasks), R.drawable.ic_star_gray, R.drawable.ic_star_green, false)); // todo: fix icon
        draweritems.add(new NavDrawerItem(getString(R.string.Notifications),R.drawable.ic_notification,R.drawable.ic_notification_green,false));
        draweritems.add(new NavDrawerItem(getString(R.string.Share), R.drawable.ic_share, R.drawable.ic_share_green, false));
        draweritems.add(new NavDrawerItem(getString(R.string.Profile),R.drawable.ic_profile,R.drawable.ic_profile_green,false));
        draweritems.add(new NavDrawerItem(getString(R.string.FAQs),R.drawable.ic_faq,R.drawable.ic_faq_green,false));
        draweritems.add(new NavDrawerItem(getString(R.string.Logout),R.drawable.ic_logout,R.drawable.ic_logout_green,false));
        return draweritems;
    }

    private void selectItem(int position) {
        // handle common & reusable things here, pass back more complex or context-dependent to activity
        int itemToSetSelected = position;
        boolean changeItemSelected = true;
        switch (position) {
            case NavigationConstants.HOME_NAV_GROUPS:
                startActivity(new Intent(getActivity(), HomeScreenActivity.class));
                break;
            case NavigationConstants.HOME_NAV_TASKS:
                Log.e(TAG, "upcoming tasks clicked");
                // startActivity(new Intent(getActivity(), N));
                break;
            case NavigationConstants.HOME_NAV_NOTIFICATIONS:
                startActivity(new Intent(getActivity(), NotificationCenter.class));
                break;
            case NavigationConstants.HOME_NAV_SHARE:
                changeItemSelected = false;
                shareApp();
                break;
            case NavigationConstants.HOME_NAV_PROFILE:
                changeItemSelected = false; // until switch that to fragmet w/nav bar instead of back
                startActivity(new Intent(getActivity(), ProfileSettingsActivity.class));
                break;
            case NavigationConstants.HOME_NAV_FAQ:
                changeItemSelected = false; // as above
                startActivity(new Intent(getActivity(), FAQActivity.class));
                break;
            case NavigationConstants.HOME_NAV_LOGOUT:
                itemToSetSelected = NavigationConstants.HOME_NAV_GROUPS; // in case activity restored, on homegrouplist
                logout();
                break;
            default:
                // todo : put in handling non-standard items
        }

        if (changeItemSelected) {
            switchSelectedState(itemToSetSelected);
        }
        mCallbacks.onNavigationDrawerItemSelected(position);
    }

    private void switchSelectedState(final int selectedItem) {
        draweritems.get(currentlySelectedItem).setIsChecked(false);
        draweritems.get(selectedItem).setIsChecked(true);
        currentlySelectedItem = selectedItem;
        drawerAdapter.notifyDataSetChanged();
    }

    private void shareApp() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_text));
        startActivity(shareIntent);
    }

    private void logout() {
        ConfirmCancelDialogFragment confirmDialog = ConfirmCancelDialogFragment.newInstance(R.string.logout_message, new ConfirmCancelDialogFragment.ConfirmDialogListener() {
                    @Override
                    public void doConfirmClicked() {
                        unregisterGcm();
                        PreferenceUtils.clearAll(getActivity().getApplicationContext());
                        EventBus.getDefault().post(new UserLoggedOutEvent());
                        Intent open = new Intent(getActivity(), StartActivity.class);
                        startActivity(open);
                    }
                });

        confirmDialog.show(getFragmentManager(), "logout");
    }

    // todo : move this onto a background thread?
    private void unregisterGcm() {
        Log.e(TAG, "unregistering from GCM ...");
        final Context context = getActivity().getApplicationContext();
        Intent gcmUnregister = new Intent(getActivity(), GcmRegistrationService.class);
        gcmUnregister.putExtra(NotificationConstants.ACTION, NotificationConstants.GCM_UNREGISTER);
        gcmUnregister.putExtra(NotificationConstants.PHONE_NUMBER, PreferenceUtils.getUserPhoneNumber(context));
        gcmUnregister.putExtra(Constant.USER_TOKEN, PreferenceUtils.getAuthToken(context));
        getActivity().startService(gcmUnregister);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe
    public void onNewNotificationEvent(NotificationEvent event) {
        Log.e(TAG, "redraw navigation drawer");
        int notificationCount = event.getNotificationCount();
        Log.e(TAG, "notification count" + notificationCount);
        drawerAdapter.notifyDataSetChanged();
    }

}