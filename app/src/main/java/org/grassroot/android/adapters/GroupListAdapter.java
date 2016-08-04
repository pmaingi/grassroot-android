package org.grassroot.android.adapters;

import android.content.Context;
import android.os.Build;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.NetworkPolicy;
import com.squareup.picasso.Picasso;

import org.grassroot.android.R;
import org.grassroot.android.fragments.HomeGroupListFragment;
import org.grassroot.android.interfaces.GroupConstants;
import org.grassroot.android.models.Group;
import org.grassroot.android.utils.CircularImageTransformer;
import org.grassroot.android.utils.RealmUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Subscriber;

/**
 * P
 */
public class GroupListAdapter extends RecyclerView.Adapter<GroupListAdapter.GHP_ViewHolder> {

    private String TAG = GroupListAdapter.class.getSimpleName();

    private final Context context;
    private final GroupRowListener listener;

    List<Group> fullGroupList;
    List<Group> displayedGroups;

    private static final SimpleDateFormat outputSDF = new SimpleDateFormat("EEE, d MMM");
    private final float localGroupAlpha = 0.5f;

    public interface GroupRowListener {
        void onGroupRowShortClick(Group group);

        void onGroupRowLongClick(Group group);

        void onGroupRowMemberClick(Group group, int position);

        void onGroupRowAvatarClick(Group group, int position);
    }

    public GroupListAdapter(List<Group> groups, HomeGroupListFragment fragment) {
        this.displayedGroups = groups;
        this.context = fragment.getContext();
        this.listener = fragment;
    }

    public void setGroupList(List<Group> groupList) {
        displayedGroups.clear();
        displayedGroups.addAll(groupList);
        notifyDataSetChanged(); // calling item range inserted causes a strange crash (related to main/background threads, I think)
    }

    public void refreshGroupsToDB() {
        RealmUtils.loadGroupsSorted().subscribe(new Subscriber<List<Group>>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(List<Group> groups) {
                displayedGroups.clear();
                displayedGroups.addAll(groups);
                notifyDataSetChanged();
                System.out.println("loaded groups " + groups.size());
            }
        });
    }

    public void refreshSingleGroup(final String groupUid) {
        Group group = RealmUtils.loadGroupFromDB(groupUid);
        for (int i = 0; i < displayedGroups.size(); i++) {
            if (displayedGroups.get(i).getGroupUid().equals(groupUid)) {
                displayedGroups.remove(i);
                displayedGroups.add(0, group);
                notifyItemRangeChanged(0, i + 1);
                Log.d(TAG,"Changed group " + groupUid);
            }
        }
    }

    public void removeSingleGroup(final String groupUid) {
        for (int i = 0; i < displayedGroups.size(); i++) {
            if (displayedGroups.get(i).getGroupUid().equals(groupUid)) {
                displayedGroups.remove(i);
                notifyDataSetChanged();
            }
        }

    }

    public void sortByChangedTime() {
        Collections.sort(displayedGroups, Collections.reverseOrder());
        notifyDataSetChanged();
    }

    // todo : make sure interactions of this and refresh from DB are okay
    public void sortByDate() {
        Collections.sort(displayedGroups, Collections.reverseOrder(
                Group.GroupTaskDateComparator)); // since Date entity sorts earliest to latest
        notifyDataSetChanged();
    }

    public void sortByRole() {
        Collections.sort(displayedGroups,
                Collections.reverseOrder(Group.GroupRoleComparator)); // as above
        notifyDataSetChanged();
    }

    // todo : maybe just use Realm query to do this
    public void simpleSearchByName(String searchText) {
        if (fullGroupList == null) {
            fullGroupList = new ArrayList<>(displayedGroups);
        }

        final List<Group> filteredGroups = new ArrayList<>();
        for (Group group : fullGroupList) {
            if (group.getGroupName().trim().toLowerCase(Locale.getDefault()).contains(searchText)) {
                filteredGroups.add(group);
            }
        }
        setGroupList(filteredGroups);
    }

    @Override
    public GHP_ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_group_homepage, parent, false);
        ButterKnife.bind(this, view);
        GHP_ViewHolder holder = new GHP_ViewHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(final GHP_ViewHolder holder, final int position) {

        final Group group = displayedGroups.get(position);

        setUpTextDescriptions(holder, group);
        setUpMemberCount(holder, group);
        setUpListeners(holder, group);
        setAvatarImage(holder, group);

        setAlphaForLocal(holder, group);


    }

    private void setAlphaForLocal(GHP_ViewHolder holder, final Group group) {
        if (Build.VERSION.SDK_INT < 11) {
            final AlphaAnimation animation = new AlphaAnimation(!group.getIsLocal() ? 1f : localGroupAlpha,
                    !group.getIsLocal() ? 1f : localGroupAlpha);
            animation.setDuration(50);
            animation.setFillAfter(true);
            holder.itemView.startAnimation(animation);
        } else {
            holder.itemView.setAlpha(!group.getIsLocal() ? 1f : localGroupAlpha);
        }
    }

    private void setUpTextDescriptions(GHP_ViewHolder holder, final Group group) {
        final String groupOrganizerDescription =
                String.format(context.getString(R.string.group_organizer_prefix), group.getGroupCreator());
        holder.txtGroupname.setText(group.getGroupName());
        holder.txtGroupownername.setText(groupOrganizerDescription);

        if (!group.hasJoinCode()) {
            final String groupDescription = group.getDescription();
            final int visibility = (TextUtils.isEmpty(groupDescription)) ? View.GONE : View.VISIBLE;
            holder.txtGroupdesc.setText(
                    String.format(context.getString(R.string.desc_body_pattern), getChangePrefix(group),
                            groupDescription));
            holder.txtGroupdesc.setVisibility(visibility);
        } else {
            final String tokenCode =
                    context.getString(R.string.join_code_prefix) + group.getJoinCode() + "#";
            holder.txtGroupdesc.setText(tokenCode);
        }

        holder.datetime.setText(
                String.format(context.getString(R.string.date_time_pattern), getChangePrefix(group),
                        outputSDF.format(group.getDate())));
    }

    private void setUpMemberCount(GHP_ViewHolder holder, final Group group) {
        // todo : check later if there's a more efficient way to do this?
        final int height = holder.profileV1.getDrawable().getIntrinsicWidth();
        final int width = holder.profileV1.getDrawable().getIntrinsicHeight();

        RelativeLayout.LayoutParams params =
                (RelativeLayout.LayoutParams) holder.profileV2.getLayoutParams();
        params.height = height;
        params.width = width;

        holder.profileV2.setLayoutParams(params);
        holder.profileV2.setText("+" + String.valueOf(group.getGroupMemberCount()));
    }

    private void setUpListeners(GHP_ViewHolder holder, final Group group) {
        final int position = holder.getAdapterPosition();
        holder.itemView.setLongClickable(true);

        holder.cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onGroupRowShortClick(group);
            }
        });

        holder.cardView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                listener.onGroupRowLongClick(group);
                return true;
            }
        });

        holder.memberIcons.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onGroupRowMemberClick(group, position);
            }
        });

        holder.avatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onGroupRowAvatarClick(group, position);
            }
        });
    }

    private void setAvatarImage(GHP_ViewHolder holder, final Group group) {
        final String imageUrl = group.getImageUrl();
        if (imageUrl != null) {
            setAvatar(holder.avatar, imageUrl, group.getDefaultImageRes());
        } else {
            holder.avatar.setImageResource(group.getDefaultImageRes());
        }
    }

    private String getChangePrefix(Group group) {
        switch (group.getLastChangeType()) {
            case GroupConstants.MEETING_CALLED:
                return (group.getDate().after(new Date()) ? context.getString(
                        R.string.future_meeting_prefix) : context.getString(R.string.past_meeting_prefix));
            case GroupConstants.VOTE_CALLED:
                return (group.getDate().after(new Date())) ? context.getString(R.string.future_vote_prefix)
                        : context.getString(R.string.past_vote_prefix);
            case GroupConstants.GROUP_CREATED:
                return context.getString(R.string.group_created_prefix);
            case GroupConstants.MEMBER_ADDED:
                return context.getString(R.string.member_added_prefix);
            case GroupConstants.GROUP_MOD_OTHER:
                return context.getString(R.string.group_other_prefix);
            default:
                throw new UnsupportedOperationException(
                        "Error! Should only be one of standard change types");
        }
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return displayedGroups.size();
    }

    public void updateGroup(int position, final String groupUid) {
        displayedGroups.set(position, RealmUtils.loadGroupFromDB(groupUid));
        notifyItemChanged(position);
        notifyDataSetChanged();
    }

    public class GHP_ViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.task_card_view_root)
        CardView cardView;
        @BindView(R.id.txt_groupname)
        TextView txtGroupname;
        @BindView(R.id.txt_groupownername)
        TextView txtGroupownername;
        @BindView(R.id.txt_groupdesc)
        TextView txtGroupdesc;
        @BindView(R.id.profile_v1)
        ImageView profileV1;
        @BindView(R.id.profile_v2)
        TextView profileV2;
        @BindView(R.id.datetime)
        TextView datetime;
        @BindView(R.id.member_icons)
        RelativeLayout memberIcons;
        @BindView(R.id.iv_gp_avatar)
        ImageView avatar;

        public GHP_ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }

    private void setAvatar(final ImageView view, final String imageUrl, final int fallBackRes) {
        Picasso.with(context).load(imageUrl)
                .error(fallBackRes)
                .placeholder(fallBackRes)
                .networkPolicy(NetworkPolicy.OFFLINE)
                .transform(new CircularImageTransformer())
                .into(view, new Callback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError() {
                        Picasso.with(context).load(imageUrl)
                                .placeholder(fallBackRes)
                                .transform(new CircularImageTransformer())
                                .error(fallBackRes)
                                .into(view);
                    }
                });

    }
}