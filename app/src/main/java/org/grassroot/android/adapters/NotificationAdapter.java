package org.grassroot.android.adapters;

import android.graphics.Color;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.grassroot.android.R;
import org.grassroot.android.models.TaskNotification;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by ravi on 12/5/16.
 */
public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private List<TaskNotification> notifications = new ArrayList<>();
    private List<TaskNotification> storedNotifications = new ArrayList<>();
    private static final String TAG = "NotificationAdapter";

    public NotificationAdapter(ArrayList<TaskNotification> dataList) {
        this.notifications = dataList;
    }

    public NotificationAdapter() {

    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_notification, parent, false);
        ViewHolder vh = new ViewHolder(view);
        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        TaskNotification notification = notifications.get(position);
        holder.txtNcMessage.setText(notification.getMessage());
        holder.txtDate.setText(notification.getCreatedDateTime());

        //had to set this so that cards that should not be colored are not
        holder.setIsRecyclable(false);
        if(notification.isRead()){
            Log.d(TAG, "notification was read, default color");
        }else{
            Log.d(TAG, "notification not read, change color");
            holder.mainView.setCardBackgroundColor(Color.LTGRAY);
        }
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.main_view)
        CardView mainView;
        @BindView(R.id.txt_nc_message)
        TextView txtNcMessage;
        @BindView(R.id.txt_date)
        TextView txtDate;

        public ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }

    private void setCardBackground(ViewHolder holder, TaskNotification notification){
        Log.e(TAG, String.valueOf(notification.isRead()));
        if(!notification.isRead()){
            Log.e(TAG, "notification not read, changing color");
            holder.mainView.setCardBackgroundColor(Color.LTGRAY);
        }
    }


    public void addData(List<TaskNotification> notificationList) {
        this.notifications.addAll(notificationList);
        this.notifyDataSetChanged();
    }

    public void updateData(List<TaskNotification> notifications) {
        int size = this.notifications.size() + 1;
        this.notifications.addAll(notifications);
        Log.e(TAG, "size of list" + this.notifications.size());;
        this.notifyDataSetChanged();
    }

    public void filter(String queryText) {
        if (storedNotifications == null || storedNotifications.isEmpty()) {
                storedNotifications = new ArrayList<>(notifications);
        }

        notifications.clear();
        for (TaskNotification n : storedNotifications) {
            // todo : probably want to also filter by group name etc
            boolean add = n.getTitle().toLowerCase().contains(queryText) ||
                    n.getMessage().toLowerCase().contains(queryText);
            if (add) {
                notifications.add(n);
            }
        }
        notifyDataSetChanged();
    }

    public void resetToStored() {
        notifications.clear();
        notifications.addAll(storedNotifications);
        notifyDataSetChanged();
    }


    public List<TaskNotification> getNotifications() {
        return notifications;
    }
}
