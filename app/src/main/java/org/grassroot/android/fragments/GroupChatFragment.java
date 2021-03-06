package org.grassroot.android.fragments;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttServiceConstants;
import org.eclipse.paho.android.service.Status;
import org.grassroot.android.R;
import org.grassroot.android.activities.GroupTasksActivity;
import org.grassroot.android.activities.MultiMessageNotificationActivity;
import org.grassroot.android.adapters.CommandsAdapter;
import org.grassroot.android.adapters.GroupChatAdapter;
import org.grassroot.android.adapters.RecyclerTouchListener;
import org.grassroot.android.events.GroupChatEvent;
import org.grassroot.android.events.GroupChatMessageReadEvent;
import org.grassroot.android.events.MessageNotSentEvent;
import org.grassroot.android.fragments.dialogs.NetworkErrorDialogFragment;
import org.grassroot.android.interfaces.ClickListener;
import org.grassroot.android.interfaces.GroupConstants;
import org.grassroot.android.interfaces.TaskConstants;
import org.grassroot.android.models.Message;
import org.grassroot.android.models.PreferenceObject;
import org.grassroot.android.models.TaskModel;
import org.grassroot.android.models.exceptions.ApiCallException;
import org.grassroot.android.models.helpers.Command;
import org.grassroot.android.models.helpers.RealmString;
import org.grassroot.android.models.responses.GroupChatSettingResponse;
import org.grassroot.android.services.GcmListenerService;
import org.grassroot.android.services.GroupService;
import org.grassroot.android.services.MqttConnectionManager;
import org.grassroot.android.services.SharingService;
import org.grassroot.android.services.TaskService;
import org.grassroot.android.utils.Constant;
import org.grassroot.android.utils.ErrorUtils;
import org.grassroot.android.utils.RealmUtils;
import org.grassroot.android.utils.Utilities;
import org.grassroot.android.utils.chat.EmojIconMultiAutoCompleteActions;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import hani.momanii.supernova_emoji_library.Helper.EmojiconMultiAutoCompleteTextView;
import io.reactivex.Observer;
import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.realm.RealmList;

import static org.eclipse.paho.android.service.MqttServiceConstants.CALLBACK_TO_ACTIVITY;
import static org.grassroot.android.utils.NetworkUtils.CONNECT_ERROR;
import static org.grassroot.android.utils.NetworkUtils.ONLINE_DEFAULT;
import static org.grassroot.android.utils.NetworkUtils.isNetworkAvailable;

/**
 * Created by paballo on 2016/08/30.
 */
public class GroupChatFragment extends Fragment implements GroupChatAdapter.GroupChatAdapterListener {

    private static final String TAG = GroupChatFragment.class.getSimpleName();

    private String groupUid;
    private String groupName;

    private boolean isMutedSending;
    private boolean isMutedReceiving;
    private List<String> mutedUsersUid;

    private BroadcastReceiver mqttReceiver;
    private Unbinder unbinder;

    @BindView(R.id.root_view) ViewGroup rootView;

    @BindView(R.id.gc_recycler_view) RecyclerView chatMessageView;
    @BindView(R.id.chat_welcome_message) TextView welcomeMsgTitle;
    @BindView(R.id.chat_welcome_message_body) TextView welcomeMsgBody;

    @BindView(R.id.emoji_btn) ImageView openEmojis;
    @BindView(R.id.text_chat) EmojiconMultiAutoCompleteTextView textView;
    @BindView(R.id.btn_send) ImageView sendMessage;

    private static final int SEND_MESSAGE = 200;
    private static final int REFRESH_MSGS = 300;

    private static final String MQTT_ERROR = "ERROR";

    private GroupChatAdapter groupChatAdapter;
    private ArrayAdapter<Command> commandsAdapter;
    private List<Command> commands;

    public static GroupChatFragment newInstance(final String groupUid, final String groupName) {
        GroupChatFragment fragment = new GroupChatFragment();
        Bundle bundle = new Bundle();
        bundle.putString(GroupConstants.UID_FIELD, groupUid);
        bundle.putString(GroupConstants.NAME_FIELD, groupName);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        groupUid = getArguments().getString(GroupConstants.UID_FIELD);
        groupName = getArguments().getString(GroupConstants.NAME_FIELD);
        mutedUsersUid = new ArrayList<>();

        loadGroupSettings();
        registerMqttReceiver();
    }

    private void loadGroupSettings() {
        GroupService.getInstance().fetchGroupChatSetting(groupUid, AndroidSchedulers.mainThread(), null)
            .subscribe(new Consumer<GroupChatSettingResponse>() {
                @Override
                public void accept(GroupChatSettingResponse groupChatSettingResponse) {
                    isMutedSending = groupChatSettingResponse.isCanSend();
                    isMutedReceiving = groupChatSettingResponse.isCanReceive();
                    mutedUsersUid = groupChatSettingResponse.getMutedUsersUids();
                }
            }, new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) {
                    Log.e(TAG, "Error fetching group settings!");
                }
            });
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (getActivity() instanceof GroupTasksActivity) {
            GroupTaskMasterFragment masterFragment = (GroupTaskMasterFragment) this.getParentFragment();
            masterFragment.getRequestPager().addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                }
                @Override
                public void onPageSelected(int position) {
                    if (position==1) {
                        final boolean isShowCased = RealmUtils.loadPreferencesFromDB().isGroupChatFragmentShowCased();
                        if (!isShowCased) {
                            Log.e(TAG, "this is where we will insert our own view pager with some text");
                            PreferenceObject preferenceObject = RealmUtils.loadPreferencesFromDB();
                            preferenceObject.setGroupChatFragmentShowCased(true);
                            RealmUtils.saveDataToRealmSync(preferenceObject);
                        }
                        notifyGroupMessagesAsRead(groupUid);
                    }
                }
                @Override
                public void onPageScrollStateChanged(int state) {

                }
            });
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_group_chat, container, false);
        unbinder = ButterKnife.bind(this, view);
        EventBus.getDefault().register(this);

        String[] commandArray = getActivity().getResources().getStringArray(R.array.commands);
        String[] hintArray = getActivity().getResources().getStringArray(R.array.command_hints);
        String[] descriptionArrays = getActivity().getResources().getStringArray(R.array.command_descriptions);

        if (commands == null) {
            commands = new ArrayList<>();
            for (int i = 0; i < commandArray.length; i++) {
                commands.add(new Command(commandArray[i], hintArray[i], descriptionArrays[i]));
            }
        }
        commandsAdapter = new CommandsAdapter(getActivity(), commands);

        setHasOptionsMenu(true);
        setUpViews();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        GcmListenerService.clearGroupsChatNotifications(groupUid);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mqttReceiver);
    }

    private void setUpViews() {
        if (getActivity() instanceof MultiMessageNotificationActivity) {
            getActivity().setTitle(groupName);
        }

        loadMessagesAndInitAdapter();

        textView.setInputType(textView.getInputType() & (~EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE));
        textView.setAdapter(commandsAdapter);
        textView.setThreshold(1); //setting it in xml does not seem to be working

        textView.requestFocus();
        textView.setEnabled(!isMutedSending);
        sendMessage.setEnabled(!isMutedSending);
        openEmojis.setEnabled(!isMutedSending);

        EmojIconMultiAutoCompleteActions emojIconAction = new EmojIconMultiAutoCompleteActions(getActivity(), rootView, textView, openEmojis);
        emojIconAction.ShowEmojIcon();
        RealmUtils.markMessagesAsSeen(groupUid);

        if (isActiveTab(groupUid) || (getActivity() instanceof MultiMessageNotificationActivity)) {
            notifyGroupMessagesAsRead(groupUid);
        }
    }

    public void loadMessagesAndInitAdapter() {
        RealmUtils.loadMessagesFromDb(groupUid).subscribe(new Consumer<List<Message>>() {
            @Override
            public void accept(List<Message> msgs) {
                if (groupChatAdapter == null) {
                    setUpListAndAdapter(msgs);
                } else {
                    groupChatAdapter.reloadFromdb(groupUid);
                }

                if (msgs.isEmpty()) {
                    switchOnIntroText();
                } else {
                    switchOffIntroText();
                    chatMessageView.smoothScrollToPosition(groupChatAdapter.getItemCount());
                }
            }
        });
    }

    private void setUpListAndAdapter(List<Message> messages) {
        groupChatAdapter = new GroupChatAdapter(messages, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        layoutManager.setAutoMeasureEnabled(true);
        layoutManager.setStackFromEnd(true);

        if (chatMessageView != null) {
            chatMessageView.setAdapter(groupChatAdapter);
            chatMessageView.setLayoutManager(layoutManager);
            chatMessageView.setItemViewCacheSize(20);
            chatMessageView.setDrawingCacheEnabled(true);
            chatMessageView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

            chatMessageView.addOnItemTouchListener(new RecyclerTouchListener(getActivity(), chatMessageView, new ClickListener() {
                @Override
                public void onClick(View view, int position) {
                    int viewType = groupChatAdapter.getItemViewType(position);
                    // slight selection hint to indicate that the item can be chosen
                    if (viewType != GroupChatAdapter.SERVER) {
                        groupChatAdapter.selectPosition(position);
                    }
                }
                @Override
                public void onLongClick(View view, int position) {
                    int viewType = groupChatAdapter.getItemViewType(position);
                    longClickOptions(groupChatAdapter.getMessages().get(position), viewType);
                }
            }));
        }
    }

    private void switchOnIntroText() {
        if (chatMessageView != null) {
            chatMessageView.setVisibility(View.GONE);
            welcomeMsgBody.setVisibility(View.VISIBLE);
            welcomeMsgTitle.setVisibility(View.VISIBLE);
        }
    }

    private void switchOffIntroText() {
        if (chatMessageView != null && chatMessageView.getVisibility() != View.VISIBLE) {
            welcomeMsgBody.setVisibility(View.GONE);
            welcomeMsgTitle.setVisibility(View.GONE);
            chatMessageView.setVisibility(View.VISIBLE);
        }
    }

    private void registerMqttReceiver() {
        IntentFilter intentFilter = new IntentFilter(CALLBACK_TO_ACTIVITY);
        mqttReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle b = intent.getExtras();
                Log.e(TAG, "mqtt broadcast: " + b.toString());
                handleMessageResult(b);
            }
        };

        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mqttReceiver, intentFilter);
    }

    // this is a bit convoluted but needed to handle temperamental local MQTT client
    private void handleMessageResult(Bundle resultBundle) {
        final String notConnected = "not connected";
        if (resultBundle != null) {
            final String callbackAction = resultBundle.getString(MqttServiceConstants.CALLBACK_ACTION);
            if (MqttServiceConstants.SEND_ACTION.equals(callbackAction)) {
                if (Status.ERROR.equals(resultBundle.get(MqttServiceConstants.CALLBACK_STATUS))) {
                    final String errorMessage = resultBundle.getString(MqttServiceConstants.CALLBACK_ERROR_MESSAGE);
                    if (notConnected.equals(errorMessage)) {
                        showErrorMessage(getString(R.string.chat_error_connect), true);
                    }
                }
            } else {
                if (resultBundle.containsKey(MqttServiceConstants.CALLBACK_EXCEPTION)) {
                    Exception exception = (Exception) resultBundle.getSerializable(MqttServiceConstants.CALLBACK_EXCEPTION);
                    Log.e(TAG, "have an error exception: " + exception);
                    if (exception != null && !isRoutineMessage(exception.toString())) {
                        showErrorMessage(exception.toString(), false);
                    }
                }
            }
        }
    }

    // since the above is showing spurious errors just when conntect is in progress or already connected
    private boolean isRoutineMessage(String string) {
        return string.contains("32110") || string.contains("32100");
    }

    private void showErrorMessage(String message, boolean showButtons) {
        // Log.e(TAG, "showing an error message: " + message);
        Message errorMsg = new Message(groupUid, UUID.randomUUID().toString(), message, Constant.MSG_ERROR);
        errorMsg.setDelivered(false);
        errorMsg.setSent(false);
        if (showButtons) {
            errorMsg.setHasCommands(true);
        }
        if (groupChatAdapter != null) {
            groupChatAdapter.addMessage(errorMsg);
            chatMessageView.smoothScrollToPosition(groupChatAdapter.getItemCount());
        }
    }

    private void showNetworkDialog(final int originatingAction, final String auxText, final String msgUid) {
        new NetworkErrorDialogFragment.NetworkDialogBuilder(R.string.chat_connect_error)
            .progressBar(null) // since we use the 'sending' tag on the subtitle, progress bar is overkill
            .syncOnConnect(false)
            .action(new SingleObserver<String>() {
                @Override
                public void onSubscribe(Disposable d) {}

                @Override
                public void onError(Throwable e) { }

                @Override
                public void onSuccess(String s) {
                    if (s.equals(CONNECT_ERROR)) {
                        Snackbar.make(rootView, R.string.connect_error_failed_retry, Snackbar.LENGTH_SHORT).show();
                    } else if (s.equals(ONLINE_DEFAULT)) {
                        if (originatingAction == SEND_MESSAGE) {
                            Log.e(TAG, "okay, reconnected, sending message ...");
                            sendMessageInBackground(auxText, msgUid);
                        } else if (originatingAction == REFRESH_MSGS) {
                            // todo : fetch messages again from the server
                            loadMessagesAndInitAdapter();
                        }
                    }
                }
            }).build().show(getFragmentManager(), "network");
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if (menu.findItem(R.id.mi_group_mute) != null) {
            menu.findItem(R.id.mi_group_mute).setVisible(true);
            menu.findItem(R.id.mi_group_mute).setTitle(!isMutedReceiving ? R.string.gp_mute : R.string.gp_un_mute);
        }

        if (menu.findItem(R.id.mi_delete_messages) != null)
            menu.findItem(R.id.mi_delete_messages).setVisible(true);

        if (menu.findItem(R.id.mi_refresh_screen) != null)
            menu.findItem(R.id.mi_refresh_screen).setVisible(true);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.mi_group_mute)
            mute(null, groupUid, true, isMutedReceiving);
        if (item.getItemId() == R.id.mi_delete_messages)
            deleteAllMessages(groupUid);
        if (item.getItemId() == R.id.mi_refresh_screen)
            loadMessagesAndInitAdapter();

        return super.onOptionsItemSelected(item);
    }

    @OnClick(R.id.btn_send)
    public void sendMessage() {
        try {
            if (!TextUtils.isEmpty(textView.getText()) && isNetworkAvailable(getContext())) {
                sendMessageInBackground(textView.getText().toString(), null);
                textView.setText(""); //clear text
                textView.requestFocus();
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMessageInBackground(final String msgText, final String messageUid) {
        final String phoneNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
        final String userName = RealmUtils.loadPreferencesFromDB().getUserName();
        final Message message = messageUid != null ? groupChatAdapter.findMessage(messageUid) :
            new Message(phoneNumber, groupUid, userName, new Date(), msgText, groupName);

        if (message != null) {
            switchOffIntroText();
            message.setSending(true);
            RealmUtils.saveDataToRealmSync(message);

            if (groupChatAdapter != null) {
                groupChatAdapter.addOrUpdateMessage(message);
                chatMessageView.smoothScrollToPosition(groupChatAdapter.getItemCount());
            }

            try {
                MqttConnectionManager.getInstance()
                        .sendMessageInBackground(message)
                        .subscribe(new Consumer<String>() {
                            @Override
                            public void accept(String s) {
                                Log.e(TAG, "message sent succesfully via MQTT");
                                groupChatAdapter.updateMessage(RealmUtils.loadMessage(message.getUid()));
                            }
                        }, new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) {
                                message.setSending(false);
                                groupChatAdapter.updateMessage(message);
                                if (getUserVisibleHint()) {
                                    handleMessageSendingError(throwable, msgText, message.getUid());
                                }
                            }
                });
            } catch (Exception e) {
                Log.e(TAG, "Paho NPE strikes again");
                e.printStackTrace();
            }
        }
    }

    private void handleMessageSendingError(Throwable e, String msgText, String msgUid) {
        if (e instanceof ApiCallException) {
            if (e.getMessage().equals(CONNECT_ERROR)) {
                showNetworkDialog(SEND_MESSAGE, msgText, msgUid);
            } else {
                Snackbar.make(rootView, ErrorUtils.serverErrorText(e), Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(GroupChatEvent groupChatEvent) {
        Log.d(TAG, "group chat event triggered");
        String groupUidInMessage = groupChatEvent.getGroupUid();
        if (this.isVisible() && !groupUidInMessage.equals(groupUid)) {
            GcmListenerService.showNotification(groupChatEvent.getBundle()).subscribe();
        } else if (this.isVisible() && groupUidInMessage.equals(groupUid)) {
            if (!isActiveTab(groupUidInMessage)) {
                GcmListenerService.showNotification(groupChatEvent.getBundle()).subscribe();
            } else {
                RealmUtils.markMessagesAsSeen(groupUid);
                notifyGroupMessagesAsRead(groupUid);
            }
            updateRecyclerView(groupChatEvent);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(GroupChatMessageReadEvent groupChatMessageReadEvent){
        String groupUidInMessage = groupChatMessageReadEvent.getMessage().getGroupUid();
        Log.e(TAG,"messsage read "+groupChatMessageReadEvent.getMessage().isRead());
        if((this.isVisible() && groupUidInMessage.equals(groupUid))){
            updateRecyclerView(groupChatMessageReadEvent);
            notifyGroupMessagesAsRead(groupUid);
        }
    }

    private void updateRecyclerView(GroupChatEvent groupChatEvent) {
        Log.d(TAG, "updating recycler view because of chat event");
        switchOffIntroText();
        Message message = groupChatEvent.getMessage();
        if (groupChatAdapter.getMessages().contains(message)) {
            groupChatAdapter.updateMessage(message);
        } else {
            groupChatAdapter.addMessage(message);
        }
        chatMessageView.smoothScrollToPosition(groupChatAdapter.getItemCount());
    }

    private void updateRecyclerView(GroupChatMessageReadEvent groupChatMessageReadEvent) {
        switchOffIntroText();
        Message message = groupChatMessageReadEvent.getMessage();
        if (groupChatAdapter.getMessages().contains(message)) {
            groupChatAdapter.updateMessage(message);
        }
        chatMessageView.smoothScrollToPosition(groupChatAdapter.getItemCount());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(MessageNotSentEvent messageNotSentEvent) {
        switchOffIntroText();
        Message message = messageNotSentEvent.getMessage();
        if (groupChatAdapter.getMessages().contains(message)) {
            groupChatAdapter.updateMessage(message);
        }
    }

    @Override
    public void createTaskFromMessage(final Message message) {
        String title = message.getTokens().get(0).getString();
        String location = TaskConstants.MEETING.equals(message.getTaskType()) ? message.getTokens().get(2).getString() : null;
        TaskModel taskModel = generateTaskObject(message.getGroupUid(), title, message.getDeadlineISO(), location, message.getTaskType());
        TaskService.getInstance().sendTaskToServer(taskModel, AndroidSchedulers.mainThread()).subscribe(new Observer<TaskModel>() {
            @Override
            public void onSubscribe(Disposable d) {}

            @Override
            public void onComplete() {}

            @Override
            public void onError(Throwable e) {
                Snackbar.make(rootView, ErrorUtils.serverErrorText(e), Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onNext(TaskModel taskModel) {
                if (isVisible() && groupChatAdapter != null) {
                    final String text = TaskConstants.MEETING.equals(taskModel.getType()) ? getString(R.string.chat_calling_meeting) :
                        TaskConstants.VOTE.equals(taskModel.getType()) ? getString(R.string.chat_calling_vote) :
                            TaskConstants.TODO.equals(taskModel.getType()) ? getString(R.string.chat_recording_action)
                                : getString(R.string.chat_calling_task);
                    Message placeHolder = new Message(groupUid, message.getUid(), text, null);
                    placeHolder.setToKeep(false);
                    RealmUtils.saveDataToRealmSync(placeHolder);
                    groupChatAdapter.updateMessage(placeHolder);
                } else {
                    RealmUtils.deleteMessageFromDb(message.getUid());
                }
            }
        });

    }

    /**
     * @param userUid       user whose activity status is to be changed. null if own
     * @param groupUid      uid of the group
     * @param userInitiated true if initiated by self to stop receiving messages from the group
     * @param active        if set to false and user is muting  another user, the latter will continue to receive messages from the group
     *                      but will not be able to send messages, however if self initiated, user will stop receiving messages from group
     */

    private void mute(@Nullable final String userUid, String groupUid, boolean userInitiated, final boolean active) {
        Log.d(TAG, "Grassroot: calling mute user of user");
        GroupService.getInstance().updateMemberChatSetting(groupUid, userUid, userInitiated, active,
            AndroidSchedulers.mainThread()).subscribe(new Consumer<String>() {
            @Override
            public void accept(String s) {
                if (TextUtils.isEmpty(userUid)) {
                    updateEntryView(active);
                    getActivity().supportInvalidateOptionsMenu();
                } else {
                    if (active) {
                        mutedUsersUid.remove(userUid);
                    } else {
                        mutedUsersUid.add(userUid);
                    }
                }
            }
        }, new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) { }}
        );
    }

    private void updateEntryView(boolean active) {
        isMutedSending = !active;
        textView.setEnabled(active);
        sendMessage.setEnabled(active);
        openEmojis.setEnabled(active);
    }

    private void longClickOptions(final Message message, int messageType) {
        switch (messageType) {
            case GroupChatAdapter.SELF:
                handleLongClickSelf(message);
                break;
            case GroupChatAdapter.OTHER:
                handleLongClickOther(message);
                break;
            case GroupChatAdapter.SERVER:
                handleLongClickServer(message);
                break;
            default:
                handleLongClickSelf(message); // has least options, hence safest default
                break;
        }
    }

    private void handleLongClickSelf(final Message message) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        final int selfOptions = message.isSent() ? R.array.self_options : R.array.self_options_resend;
        builder.setItems(selfOptions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                int optionChosen = message.isSent() ? i + 1 : i;
                switch (optionChosen) {
                    case 0:
                        message.setSending(true);
                        groupChatAdapter.updateMessage(message);
                        sendMessageInBackground(message.getText(), message.getUid());
                        break;
                    case 1:
                        Utilities.copyTextToClipboard(getString(R.string.chat_clipboard_label), message.getText());
                        break;
                    case 2:
                        deleteMessage(message.getUid());
                        break;
                }
            }
        });
        builder.setCancelable(true).create().show();
    }

    private void handleLongClickOther(final Message message) {
        //0 - Delete Message // 1 = Mute or Unmute user
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        int otherOptions = (mutedUsersUid != null && mutedUsersUid.contains(message.getUid())) ?
            R.array.chat_msg_already_muted : R.array.chat_msg_mute_available;

        builder.setItems(otherOptions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                switch (i) {
                    case 0:
                        deleteMessage(message.getUid());
                        break;
                    case 1:
                        mute(message.getUserUid(), groupUid, false, !mutedUsersUid.contains(message.getUid()));
                        break;
                }
            }
        });

        builder.setCancelable(true).create().show();
    }

    private void handleLongClickServer(final Message message) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setItems(message.isToKeep() ? R.array.chat_msg_server_keep : R.array.chat_msg_server_not_keep,
            new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                switch (i) {
                    case 0: // share or delete
                        if (!message.isToKeep()) {
                            deleteMessage(message.getUid());
                        } else {
                            try {
                                startActivity(SharingService.simpleTextShare(message.getText()));
                            } catch (ActivityNotFoundException e) {
                                Toast.makeText(getContext(), R.string.chat_share_error, Toast.LENGTH_SHORT).show();
                            }
                        }
                        break;
                    case 1: // delete
                        deleteMessage(message.getUid());
                        break;
                    case 2:
                        Utilities.copyTextToClipboard(getString(R.string.chat_clipboard_label), message.getText());
                        break;
                }
            }
        });

        builder.setCancelable(true).create().show();
    }

    private void deleteAllMessages(final String groupUid) {
        RealmUtils.deleteAllGroupMessagesFromDb(groupUid).subscribe(new Consumer<String>() {
            @Override
            public void accept(String s) {
                groupChatAdapter.deleteAll();
            }
        });

    }

    private void deleteMessage(final String messageId) {
        RealmUtils.deleteMessageFromDb(messageId).subscribe(new Consumer<String>() {
            @Override
            public void accept(String s) {
                groupChatAdapter.deleteOne(messageId);
            }
        });

    }

    private void notifyGroupMessagesAsRead(String groupUid) {
        GroupService.getInstance().markMessagesAsRead(groupUid, AndroidSchedulers.mainThread()).subscribe();

    }
    private boolean isActiveTab(String groupUid) {
        if (getActivity() instanceof GroupTasksActivity) {
            GroupTaskMasterFragment masterFragment = (GroupTaskMasterFragment) this.getParentFragment();
            return masterFragment.getRequestPager().getCurrentItem() == 1;
        }
        return (getActivity() instanceof MultiMessageNotificationActivity &&  this.groupUid.equals(groupUid));
    }

    private TaskModel generateTaskObject(String groupUid, String title, String time, @Nullable String venue, String type) {

        TaskModel model = new TaskModel();
        model.setTitle(title);
        model.setDescription(title);
        model.setCreatedByUserName(RealmUtils.loadPreferencesFromDB().getUserName());
        model.setDeadlineISO(time);
        model.setLocation(venue);
        model.setParentUid(groupUid);
        model.setTaskUid(UUID.randomUUID().toString());
        model.setType(type);
        model.setParentLocal(false);
        model.setLocal(true);
        model.setMinutes(0);
        model.setCanEdit(true);
        model.setCanAction(true);
        model.setReply(TaskConstants.TODO_PENDING);
        model.setHasResponded(false);
        model.setWholeGroupAssigned(true);
        model.setMemberUIDS(new RealmList<RealmString>());

        return model;
    }



}