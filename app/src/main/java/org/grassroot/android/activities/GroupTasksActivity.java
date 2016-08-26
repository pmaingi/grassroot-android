package org.grassroot.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.NavUtils;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.grassroot.android.R;
import org.grassroot.android.events.TaskCancelledEvent;
import org.grassroot.android.fragments.JoinCodeFragment;
import org.grassroot.android.fragments.NewTaskMenuFragment;
import org.grassroot.android.fragments.TaskListFragment;
import org.grassroot.android.fragments.ViewTaskFragment;
import org.grassroot.android.fragments.dialogs.MultiLineTextDialog;
import org.grassroot.android.interfaces.GroupConstants;
import org.grassroot.android.models.Group;
import org.grassroot.android.services.GroupService;
import org.grassroot.android.utils.Constant;
import org.grassroot.android.utils.ErrorUtils;
import org.grassroot.android.utils.IntentUtils;
import org.grassroot.android.utils.NetworkUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;

public class GroupTasksActivity extends PortraitActivity implements NewTaskMenuFragment.NewTaskMenuListener, JoinCodeFragment.JoinCodeListener, TaskListFragment.TaskListListener {

    private static final String TAG = GroupTasksActivity.class.getCanonicalName();

    private Group groupMembership;
    private boolean canCreateTask;
    private TaskListFragment taskListFragment;
    private NewTaskMenuFragment newTaskMenuFragment;
    private JoinCodeFragment joinCodeFragment;

    private Menu thisMenu;
    private boolean showDescOption;
    private int descOptionText;

    @BindView(R.id.gta_toolbar) Toolbar toolbar;
    @BindView(R.id.gta_fab) FloatingActionButton actionButton;
    @BindView(R.id.progressBar) ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_tasks);
        ButterKnife.bind(this);
        EventBus.getDefault().register(this);

        final Bundle extras = getIntent().getExtras();

        if (extras == null) {
            Log.e(TAG, "Error! Group tasks activity called without group passed");
            startActivity(ErrorUtils.gracefulExitToHome(this));
            finish();
            return;
        }

        groupMembership = extras.getParcelable(GroupConstants.OBJECT_FIELD);
        if (groupMembership == null) {
            Log.e(TAG, "Error! Group tasks activity called without group passed");
            startActivity(ErrorUtils.gracefulExitToHome(this));
            finish();
            return;
        }

        newTaskMenuFragment = NewTaskMenuFragment.newInstance(groupMembership, true);
        canCreateTask = groupMembership.canCallMeeting() || groupMembership.canCallVote() || groupMembership.canCreateTodo();

        setUpViews();
        setUpFragment();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (newTaskMenuFragment != null) {
            getSupportFragmentManager()
                .beginTransaction()
                .remove(newTaskMenuFragment)
                .commit();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_group_tasks, menu);
        final MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        if (searchView != null) {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    taskListFragment.searchStringChanged(query);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    taskListFragment.searchStringChanged(newText);
                    return true;
                }
            });
        }
        return true;
    }

    private void setUpViews() {
        setTitle(groupMembership.getGroupName());

        // if don't have permission to change, we just display
        showDescOption = groupMembership.canEditGroup() || !TextUtils.isEmpty(groupMembership.getDescription());
        descOptionText = TextUtils.isEmpty(groupMembership.getDescription()) ? R.string.gset_desc_add
            : groupMembership.canEditGroup() ? R.string.gta_menu_change_desc : R.string.gta_menu_view_desc;

        setSupportActionBar(toolbar);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        actionButton.setVisibility(canCreateTask ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.mi_change_desc).setVisible(showDescOption);
        menu.findItem(R.id.mi_change_desc).setTitle(descOptionText);
        menu.findItem(R.id.mi_view_join_code).setVisible(groupMembership.hasJoinCode());
        menu.findItem(R.id.mi_add_members).setVisible(groupMembership.canAddMembers());
        menu.findItem(R.id.mi_remove_members).setVisible(groupMembership.canDeleteMembers());
        menu.findItem(R.id.mi_view_members).setVisible(groupMembership.canViewMembers());
        menu.findItem(R.id.mi_group_settings).setVisible(groupMembership.canEditGroup());
        menu.findItem(R.id.mi_share_default).setVisible(false);
        this.thisMenu = menu;
        return true;
    }

    private void setUpFragment() {
        taskListFragment = TaskListFragment.newInstance(groupMembership.getGroupUid(), null, this, false);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.gta_fragment_holder, taskListFragment)
                .commit();
    }

    @OnClick(R.id.gta_fab)
    public void openNewTaskMenu() {
        newTaskMenuFragment.setShowAddMembers(true);
        getSupportFragmentManager().beginTransaction()
            .setCustomAnimations(R.anim.flyin_fast, R.anim.flyout_fast)
            .replace(R.id.gta_root_layout, newTaskMenuFragment)
            .addToBackStack(null)
            .commit();
    }

    @Override
    public void menuCloseClicked() {
        getSupportFragmentManager().beginTransaction()
            .setCustomAnimations(R.anim.flyin_fast, R.anim.flyout_fast)
            .remove(newTaskMenuFragment)
            .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final String groupUid = groupMembership.getGroupUid();
        final String groupName = groupMembership.getGroupName();
        switch (item.getItemId()) {
            case android.R.id.home:
                handleUpButton();
                return true;
            case R.id.mi_icon_filter:
                taskListFragment.filter();
                return true;
            case R.id.mi_change_desc:
                viewOrChangeDescription();
                return true;
            case R.id.mi_view_join_code:
                setUpJoinCodeFragment();
                return true;
            case R.id.mi_view_members:
                Intent viewMembers = IntentUtils.constructIntent(this, GroupMembersActivity.class, groupUid, groupName);
                viewMembers.putExtra(Constant.PARENT_TAG_FIELD, GroupTasksActivity.class.getCanonicalName());
                startActivity(viewMembers);
                return true;
            case R.id.mi_add_members:
                startActivity(IntentUtils.constructIntent(this, AddMembersActivity.class, groupUid, groupName));
                return true;
            case R.id.mi_remove_members:
                Intent removeMembers = IntentUtils.constructIntent(this, RemoveMembersActivity.class, groupUid, groupName);
                startActivity(removeMembers);
                return true;
            case R.id.mi_group_settings:
                Intent groupSettings = IntentUtils.constructIntent(this, GroupSettingsActivity.class, groupMembership);
                startActivity(groupSettings);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void handleUpButton() {
        if (!closeViewTaskFragment()) {
            NavUtils.navigateUpFromSameTask(this);
        }
    }

    private void viewOrChangeDescription() {
        if (groupMembership.canEditGroup()) {
            changeGroupDescDialog(TextUtils.isEmpty(groupMembership.getDescription()));
        } else if (showDescOption) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.gta_desc_title)
                .setMessage(groupMembership.getDescription())
                .setCancelable(true)
                .show();
        }
    }

    private void changeGroupDescDialog(final boolean isEmptyDesc) {
        final String message = isEmptyDesc ? getString(R.string.gset_no_description) :
            getString(R.string.gset_has_desc_body, groupMembership.getDescription());
        MultiLineTextDialog.showMultiLineDialog(getSupportFragmentManager(), -1, message,
            R.string.gset_desc_dialog_hint, R.string.gset_desc_dialog_done).subscribe(new Action1<String>() {
            @Override
            public void call(String s) {
                progressBar.setVisibility(View.VISIBLE);
                serviceCallChangeDesc(s);
            }
        });
    }

    private void serviceCallChangeDesc(final String newDescription) {
        GroupService.getInstance().changeGroupDescription(groupMembership.getGroupUid(), newDescription,
            AndroidSchedulers.mainThread()).subscribe(new Action1<String>() {
            @Override
            public void call(String s) {
                progressBar.setVisibility(View.GONE);
                if (s.equals(NetworkUtils.SAVED_SERVER)) {
                    Toast.makeText(GroupTasksActivity.this, R.string.gset_desc_change_done, Toast.LENGTH_SHORT).show();
                } else {
                    Snackbar.make(actionButton, R.string.gset_desc_offline, Snackbar.LENGTH_SHORT).show();
                }
                groupMembership.setDescription(newDescription);
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                progressBar.setVisibility(View.GONE);
                Snackbar.make(actionButton, ErrorUtils.serverErrorText(throwable), Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private void setUpJoinCodeFragment(){
        String joinCode = groupMembership.getJoinCode();
        joinCodeFragment = JoinCodeFragment.newInstance(joinCode);
        getSupportFragmentManager().beginTransaction()
            .setCustomAnimations(R.anim.flyin_fast, R.anim.flyout_fast)
            .replace(R.id.gta_root_layout, joinCodeFragment, JoinCodeFragment.class.getCanonicalName())
            .addToBackStack(null)
            .commit();
    }

    @Override
    public void joinCodeClose() {
        getSupportFragmentManager().beginTransaction()
            .setCustomAnimations(R.anim.flyin_fast, R.anim.flyout_fast)
                .remove(joinCodeFragment)
                .commit();
    }

    @Override
    public void onTaskLoaded(String taskName) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.btn_close_white);
        }
        if (actionButton != null) {
            actionButton.setVisibility(View.GONE);
        }
        toggleMenuFilter(false); // todo : may not need this (view task overrides & replaces it) ...
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onTaskCancelled(TaskCancelledEvent e) {
        closeViewTaskFragment();
    }

    private boolean closeViewTaskFragment() {
        Fragment frag = getSupportFragmentManager().findFragmentByTag(ViewTaskFragment.class.getCanonicalName());
        if (frag != null && frag.isVisible()) {
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.push_down_in, R.anim.push_down_out)
                    .remove(frag)
                    .commit();
            // keep null checks in place in case subscriber triggered after view destroyed
            if (actionButton != null) {
                actionButton.setVisibility(canCreateTask ? View.VISIBLE : View.GONE);
            }
            if (getSupportActionBar() != null) {
                getSupportActionBar().setHomeAsUpIndicator(R.drawable.btn_back_wt);
            }
            setTitle(groupMembership.getGroupName());
            toggleMenuFilter(true);
            return true;
        } else {
            return false;
        }
    }

    private void toggleMenuFilter(boolean showFilter) {
        MenuItem filter = thisMenu.findItem(R.id.mi_icon_filter);
        if (filter != null) {
            Log.e(TAG, "found menu item"); // note : for some reason, this is not hiding the filter
            if (showFilter) {
                filter.setVisible(true);
                this.invalidateOptionsMenu();
            } else {
                filter.setVisible(false);
                this.invalidateOptionsMenu();
            }
        }
    }
}
