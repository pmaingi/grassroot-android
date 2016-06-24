package org.grassroot.android.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.grassroot.android.R;
import org.grassroot.android.activities.AddMembersActivity;
import org.grassroot.android.activities.CreateMeetingActivity;
import org.grassroot.android.activities.CreateTodoActivity;
import org.grassroot.android.activities.CreateVoteActivity;
import org.grassroot.android.events.TaskAddedEvent;
import org.grassroot.android.interfaces.GroupConstants;
import org.grassroot.android.models.Group;
import org.grassroot.android.utils.Constant;
import org.grassroot.android.utils.MenuUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;


public class NewTaskMenuFragment extends Fragment {

    private static final String TAG = NewTaskMenuFragment.class.getCanonicalName();

    public interface NewTaskMenuListener {
        void menuCloseClicked();
    }

    @BindView(R.id.iv_back)
    ImageView ivBack;
    @BindView(R.id.bt_vote)
    Button bt_vote;
    @BindView(R.id.bt_meeting)
    Button bt_meeting;
    @BindView(R.id.bt_todo)
    Button bt_todo;
    @BindView(R.id.bt_newmember)
    Button bt_addmember;
    @BindView(R.id.nt_tv)
    TextView et_message;
    @BindView(R.id.nt_rl)
    RelativeLayout buttonHolder;

    private NewTaskMenuListener listener;

    private Group groupMembership;
    private String groupUid;
    private String groupName;

    private boolean showAddMembers;
    private boolean showJoinCode; // putting this here as may need it after user feedback

    public static NewTaskMenuFragment newInstance(Group groupMembership, boolean showAddMembers, boolean showJoinCode) {
        NewTaskMenuFragment fragment = new NewTaskMenuFragment();
        fragment.showAddMembers = showAddMembers;
        fragment.showJoinCode = showJoinCode;
        Bundle b = new Bundle();
        b.putParcelable(GroupConstants.OBJECT_FIELD, groupMembership);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            this.listener = (NewTaskMenuListener) context;
        } catch (ClassCastException e) {
            throw new UnsupportedOperationException("Error! New task menu needs a listener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle b = getArguments();
        EventBus.getDefault().register(this);

        if (b == null) {
            throw new UnsupportedOperationException("Error! Null arguments passed to modal");
        }

        this.groupMembership = b.getParcelable(GroupConstants.OBJECT_FIELD);
        if (groupMembership == null) {
            throw new UnsupportedOperationException("Error! New task called without valid group");
        }

        groupUid = groupMembership.getGroupUid();
        groupName = groupMembership.getGroupName();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstance) {
        View viewToReturn = inflater.inflate(R.layout.fragment_new_task_menu, container, false);
        ButterKnife.bind(this, viewToReturn);
        setVisibility(groupMembership);
        return viewToReturn;
    }

    private void setVisibility(Group groupMembership) {
        // todo : handle situation where it contains none of the permissions
        bt_meeting.setVisibility(groupMembership.canCallMeeting() ? View.VISIBLE : View.GONE);
        bt_vote.setVisibility(groupMembership.canCallVote() ? View.VISIBLE : View.GONE);
        bt_todo.setVisibility(groupMembership.canCreateTodo() ? View.VISIBLE : View.GONE);
        bt_addmember.setVisibility(groupMembership.canAddMembers() && showAddMembers ? View.VISIBLE : View.GONE);
    }

    @OnClick(R.id.iv_back)
    public void onBackClick() {
        listener.menuCloseClicked();
    }

    @OnClick(R.id.bt_todo)
    public void onTodoButtonClick() {
        Intent todo = MenuUtils.constructIntent(getActivity(), CreateTodoActivity.class, groupUid, groupName);
        startActivity(todo);
    }

    @OnClick(R.id.bt_meeting)
    public void onMeetingButtonClick() {
        Intent createMeeting = MenuUtils.constructIntent(getActivity(), CreateMeetingActivity.class, groupUid, groupName);
        startActivity(createMeeting);
    }

    @OnClick(R.id.bt_vote)
    public void onVoteButtonClick() {
        Intent createVote = MenuUtils.constructIntent(getActivity(), CreateVoteActivity.class, groupUid, groupName);
        createVote.putExtra("title", "Vote");
        startActivity(createVote);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe
    public void onTaskCreatedEvent(TaskAddedEvent taskAddedEvent){
        et_message.setText(taskAddedEvent.getMessage());
        et_message.setVisibility(View.VISIBLE);
        buttonHolder.setVisibility(View.GONE);

    }



    @OnClick(R.id.bt_newmember)
    public void onNewMemberButtonClick() {
        // todo: if called from here, on finishing go back to group page (manage stack)
        Intent addMembers = new Intent(getActivity(), AddMembersActivity.class);
        addMembers.putExtra(Constant.GROUPUID_FIELD, groupUid);
        addMembers.putExtra(Constant.GROUPNAME_FIELD, groupName);
        startActivity(addMembers);
    }

}
