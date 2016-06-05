package org.grassroot.android.ui.fragments;


import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;

import org.grassroot.android.R;
import org.grassroot.android.ui.activities.AddMembersActivity;
import org.grassroot.android.ui.activities.GroupMembersActivity;
import org.grassroot.android.ui.activities.HomeScreenActivity;
import org.grassroot.android.ui.activities.RemoveMembersActivity;
import org.grassroot.android.utils.Constant;
import org.grassroot.android.utils.MenuUtils;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;


public class GroupQuickMemberModalFragment extends android.support.v4.app.DialogFragment {

    private static final String TAG = GroupQuickMemberModalFragment.class.getSimpleName();

    @BindView(R.id.ic_home_add_member_active)
    ImageView icAddMemberIcon;
    @BindView(R.id.ic_home_view_members_active)
    ImageView icViewMemberIcon;
    @BindView(R.id.ic_remove_members_active)
    ImageView icRemoveMembersIcon;

    private String groupUid;
    private String groupName;
    private int groupPosition;

    private boolean addMemberPermitted, viewMembersPermitted, editSettingsPermitted, removeMembersPermitted;

    // would rather use good practice and not have empty constructor, but Android is Android
    public GroupQuickMemberModalFragment() { }

    @Override
    public void onStart() {
        super.onStart();
        getDialog().getWindow().setWindowAnimations(R.style.animation_fast_flyinout);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.modal_group_members_quick, container, false);
        ButterKnife.bind(this, view);
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(0));
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Bundle b = getArguments();
        if (b == null) { throw new UnsupportedOperationException("Error! Null arguments passed to modal"); }

        Log.d(TAG, "inside quickGroupMemberBundle, passed bundle = " + b.toString());

        this.groupUid = b.getString(Constant.GROUPUID_FIELD);
        this.groupName = b.getString(Constant.GROUPNAME_FIELD);
        this.groupPosition = b.getInt(Constant.INDEX_FIELD);

        addMemberPermitted = b.getBoolean("addMember");
        viewMembersPermitted = b.getBoolean("viewMembers");
        removeMembersPermitted = b.getBoolean("removeMembers");
        editSettingsPermitted = b.getBoolean("editSettings");

        int addIcon = addMemberPermitted ? R.drawable.ic_add_circle_active_24dp : R.drawable.ic_add_circle_inactive_24dp;
        int viewIcon = viewMembersPermitted ? R.drawable.ic_list_icon_active_24dp : R.drawable.ic_list_icon_inactive_24dp;
        int removeIcon = removeMembersPermitted ? R.drawable.ic_remove_circle_active_24dp : R.drawable.ic_remove_circle_inactive_24dp;

        icAddMemberIcon.setImageResource(addIcon);
        icViewMemberIcon.setImageResource(viewIcon);
        icRemoveMembersIcon.setImageResource(removeIcon);
    }

    @OnClick(R.id.ic_home_add_member_active)
    public void gmAddMemberIconListener() {
        if (addMemberPermitted) {
            Intent addMember = MenuUtils.constructIntent(getActivity(), AddMembersActivity.class, groupUid, groupName);
            addMember.putExtra(Constant.INDEX_FIELD, groupPosition);
            // note: inefficiency here in routing back via activity, but getParentFragment is throwing a null error...
            getActivity().startActivityForResult(addMember, Constant.activityAddMembersToGroup);
            getDialog().dismiss();
        } else {
            getDialog().dismiss();
        }
    }

    @OnClick(R.id.ic_home_view_members_active)
    public  void gmViewMembersIconListener() {
        if (viewMembersPermitted) {
            Intent viewMembers = MenuUtils.constructIntent(getActivity(), GroupMembersActivity.class, groupUid, groupName);
            viewMembers.putExtra(Constant.PARENT_TAG_FIELD, HomeScreenActivity.class.getCanonicalName());
            startActivity(viewMembers);
            getDialog().dismiss();
        } else {
            getDialog().dismiss();
        }
    }

    @OnClick(R.id.ic_remove_members_active)
    public void gmRemoveMembersIconListener() {
        if (removeMembersPermitted) {
            Intent removeMembers = MenuUtils.constructIntent(getActivity(), RemoveMembersActivity.class, groupUid, groupName);
            removeMembers.putExtra(Constant.INDEX_FIELD, groupPosition);
            getActivity().startActivityForResult(removeMembers, Constant.activityRemoveMembers);
            getDialog().dismiss();
        } else {
            getDialog().dismiss();


        }
    }

}