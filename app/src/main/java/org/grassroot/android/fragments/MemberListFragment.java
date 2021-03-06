package org.grassroot.android.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import org.grassroot.android.R;
import org.grassroot.android.adapters.MemberListAdapter;
import org.grassroot.android.adapters.RecyclerTouchListener;
import org.grassroot.android.interfaces.ClickListener;
import org.grassroot.android.models.Group;
import org.grassroot.android.models.Member;
import org.grassroot.android.utils.RealmUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.functions.Consumer;

/**
 * Created by luke on 2016/05/08.
 */
public class MemberListFragment extends Fragment {

    private static final String TAG = MemberListFragment.class.getCanonicalName();

    private Group group;

    private boolean canClickItems;
    private boolean showSelected;
    private boolean selectedByDefault;
    private boolean includeThisUser;

    private List<Member> preSelectedMembers;
    private List<Member> filteredMembers;

    private MemberClickListener clickListener;
    private MemberListAdapter memberListAdapter;

    RecyclerView memberListRecyclerView;
    ProgressBar progressBar;

    public interface MemberClickListener {
        void onMemberClicked(int position, String memberUid);
    }

    // note : groupUid can be set null, in which case we are adding members generated locally
    public static MemberListFragment newInstance(String parentUid, boolean clickEnabled, boolean showSelected,
                                                 List<Member> selectedMembers, boolean includeThisUser, MemberClickListener clickListener) {

        MemberListFragment fragment = new MemberListFragment();
        if (parentUid != null) {
            fragment.group = RealmUtils.loadGroupFromDB(parentUid);
        } else {
            fragment.group = null;
        }
        fragment.canClickItems = clickEnabled;
        fragment.showSelected = showSelected;
        fragment.clickListener = clickListener;
        fragment.preSelectedMembers = selectedMembers;
        fragment.includeThisUser = includeThisUser;
        return fragment;
    }

    public static MemberListFragment newInstance(Group group, boolean includeThisUser, boolean showSelected, List<Member> filteredMembers,
                                                 MemberClickListener clickListener) {
        MemberListFragment fragment = new MemberListFragment();
        fragment.group = group;
        fragment.canClickItems = true;
        fragment.showSelected = showSelected;
        fragment.clickListener = clickListener;
        fragment.filteredMembers = filteredMembers;
        fragment.includeThisUser = includeThisUser;
        return fragment;
    }

    public void setSelectedByDefault(boolean selectedByDefault) {
        this.selectedByDefault = selectedByDefault;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (memberListAdapter == null) {
            memberListAdapter = new MemberListAdapter(this.getContext(), includeThisUser);
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View viewToReturn = inflater.inflate(R.layout.fragment_member_list, container, false);
        memberListRecyclerView = (RecyclerView) viewToReturn.findViewById(R.id.mlist_frag_recycler_view);
        progressBar = (ProgressBar) viewToReturn.findViewById(R.id.progressBar);
        setUpRecyclerView();
        return viewToReturn;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        memberListRecyclerView = null;
        progressBar = null;
    }

    public void transitionToMemberList(List<Member> members) {
        if (memberListAdapter != null) {
            memberListAdapter.setMembers(members);
        }
    }

    public void addMembers(List<Member> members) {
        if (memberListAdapter != null) {
            memberListAdapter.addMembers(members);
        }
    }

    public void updateMember(int position, Member revisedMember) {
        if (memberListAdapter != null) {
            memberListAdapter.updateMember(position, revisedMember); // todo : rethink all this pass-through stuff
        }
    }

    public void removeMember(int position) {
        if (memberListAdapter != null) {
            memberListAdapter.removeMembers(new int[] { position });
        }
    }

    public List<Member> getSelectedMembers() {
        if (!showSelected) {
            return memberListAdapter.getMembers();
        } else {
            List<Member> membersToReturn = new ArrayList<>();
            for (Member m : memberListAdapter.getMembers()) {
                if (m.isSelected()) membersToReturn.add(m);
            }
            return membersToReturn;
        }
    }

    public void selectAllMembers() {
        // todo : move this into adapter
        for (Member m : memberListAdapter.getMembers()) {
            m.setSelected(true);
        }
        memberListAdapter.notifyDataSetChanged();
    }

    public void unselectAllMembers() {
        for (Member m : memberListAdapter.getMembers()) {
            m.setSelected(false);
        }
        memberListAdapter.notifyDataSetChanged(); // by definition have to refresh whole dataset
    }

    public void refreshMembersToDb() {
        fetchGroupMembers();
    }

    private void setUpRecyclerView() {
        memberListRecyclerView.setAdapter(memberListAdapter);
        memberListRecyclerView.setLayoutManager(new LinearLayoutManager(this.getContext()));
        memberListAdapter.setShowSelected(showSelected);

        if (group != null)
            fetchGroupMembers();
        if (canClickItems)
            setUpSelectionListener();

        memberListRecyclerView.setVisibility(View.VISIBLE);
    }

    private void setUpSelectionListener() {
        memberListRecyclerView.addOnItemTouchListener(new RecyclerTouchListener(getContext(), memberListRecyclerView,
                new ClickListener() {
                    @Override
                    public void onClick(View view, int position) {
                        memberListAdapter.toggleMemberSelected(position);
                        if (clickListener != null) {
                            Log.e(TAG, "member clicked at position ... " + position);
                            clickListener.onMemberClicked(position, memberListAdapter.getMemberUid(position));
                        }
                    }

                    @Override
                    public void onLongClick(View view, int position) {
                        memberListAdapter.toggleMemberSelected(position);
                        if (clickListener != null) {
                            clickListener.onMemberClicked(position, memberListAdapter.getMemberUid(position));
                        }
                    }
        }));
    }

    private void fetchGroupMembers() {
        Log.d(TAG, "inside MemberListFragment, retrieving group members for uid = " + group.getGroupUid());

        if (group.getGroupMemberCount() > 20) {
            progressBar.setVisibility(View.VISIBLE);
        }

        RealmUtils.loadGroupMembers(group.getGroupUid(), includeThisUser).subscribe(new Consumer<List<Member>>() {
            @Override
            public void accept(List<Member> members) {
                if (filteredMembers != null) {
                    members.removeAll(filteredMembers);
                }

                if (memberListAdapter != null) {
                    memberListAdapter.setMembers(members);
                    if (preSelectedMembers != null && !selectedByDefault) {
                        // todo : consider using list.contains on members when can trust hashing/equals
                        final Map<String, Integer> positionMap = new HashMap<>();
                        final int listSize = preSelectedMembers.size();
                        for (int i = 0; i < listSize; i++) {
                            positionMap.put((members.get(i)).getMemberUid(), i);
                        }
                        for (Member m : preSelectedMembers) {
                            if (positionMap.containsKey(m.getMemberUid())) {
                                memberListAdapter.toggleMemberSelected(positionMap.get(m.getMemberUid()));
                            }
                        }
                    }
                }

                if (progressBar != null) {
                    progressBar.setVisibility(View.INVISIBLE);
                }
            }
        });

    }

}