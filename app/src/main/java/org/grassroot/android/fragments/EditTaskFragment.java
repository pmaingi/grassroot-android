package org.grassroot.android.fragments;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.security.keystore.UserNotAuthenticatedException;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TimePicker;

import org.grassroot.android.R;
import org.grassroot.android.events.TaskUpdatedEvent;
import org.grassroot.android.interfaces.NetworkErrorDialogListener;
import org.grassroot.android.interfaces.TaskConstants;
import org.grassroot.android.models.Member;
import org.grassroot.android.models.MemberList;
import org.grassroot.android.models.TaskModel;
import org.grassroot.android.models.TaskResponse;
import org.grassroot.android.services.ApplicationLoader;
import org.grassroot.android.services.GrassrootRestService;
import org.grassroot.android.utils.Constant;
import org.grassroot.android.utils.ErrorUtils;
import org.grassroot.android.utils.MenuUtils;
import org.grassroot.android.utils.PreferenceUtils;
import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnEditorAction;
import butterknife.OnTextChanged;
import butterknife.OnTouch;
import butterknife.Unbinder;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by paballo on 2016/06/21.
 */
public class EditTaskFragment extends Fragment implements DatePickerDialog.OnDateSetListener, TimePickerDialog.OnTimeSetListener {

    private static final String TAG = EditTaskFragment.class.getCanonicalName();
    private static final int changeColor = ContextCompat.getColor(ApplicationLoader.applicationContext, R.color.red);

    private TaskModel task;
    private String taskType;

    private final Calendar calendar = Calendar.getInstance();

    private ViewGroup vContainer;
    private List<Member> selectedMembers;

    private ProgressDialog progressDialog;
    private Unbinder unbinder;

    @BindView(R.id.etsk_title_ipl)
    TextInputLayout subjectInput;
    @BindView(R.id.etsk_til_location)
    TextInputLayout locationInput;
    @BindView(R.id.etsk_et_title)
    TextInputEditText etTitleInput;
    @BindView(R.id.etsk_et_location)
    TextInputEditText etLocationInput;
    @BindView(R.id.etsk_et_description)
    TextInputEditText etDescriptionInput;

    @BindView(R.id.etsk_deadline_date)
    TextView dateDisplayed;
    @BindView(R.id.etsk_cv_time)
    CardView timeCard;
    @BindView(R.id.etsk_deadline_time)
    TextView timeDisplayed;

    @BindView(R.id.etsk_cv_description)
    CardView descriptionCard;
    @BindView(R.id.etsk_desc_header)
    TextView descriptionHeader;
    @BindView(R.id.etsk_rl_desc_body)
    RelativeLayout descriptionBody;
    @BindView(R.id.etsk_til_desc)
    TextInputLayout descriptionInput;
    @BindView(R.id.etsk_desc_expand)
    ImageView ivDescExpandIcon;

    @BindView(R.id.etsk_tv_assign_label)
    TextView tvInviteeLabel;

    @BindView(R.id.etsk_btn_update_task)
    Button btTaskUpdate;

    public static EditTaskFragment newInstance(TaskModel task) {
        EditTaskFragment fragment = new EditTaskFragment();
        Bundle args = new Bundle();
        args.putParcelable(TaskConstants.TASK_ENTITY_FIELD, task);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle b = getArguments();
        if (b == null) {
            throw new UnsupportedOperationException("Error! Fragment needs to be created with arguments");
        }

        task = b.getParcelable(TaskConstants.TASK_ENTITY_FIELD);
        if (task == null) {
            throw new UnsupportedOperationException("Error! Fragment called without valid task");
        }

        taskType = task.getType();
        calendar.setTime(task.getDeadlineDate());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View viewToReturn = inflater.inflate(R.layout.fragment_edit_task, container, false);
        unbinder = ButterKnife.bind(this, viewToReturn);
        vContainer = container;
        progressDialog = new ProgressDialog(getContext());
        populateFields();
        fetchAssignedMembers();
        return viewToReturn;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        progressDialog.dismiss();
        unbinder.unbind();
    }

    private void populateFields() {

        locationInput.setVisibility(TaskConstants.MEETING.equals(taskType) ? View.VISIBLE : View.GONE);
        locationCharCounter.setVisibility(TaskConstants.MEETING.equals(taskType) ? View.VISIBLE : View.GONE);

        etTitleInput.setText(task.getTitle());
        etDescriptionInput.setText(task.getDescription());
        dateDisplayed.setText(String.format(getString(R.string.etsk_vote_date), TaskConstants.dateDisplayWithoutHours.format(task.getDeadlineDate())));
        timeDisplayed.setText(String.format(getString(R.string.etsk_mtg_time), TaskConstants.timeDisplayWithoutDate.format(task.getDeadlineDate())));

        switch (task.getType()) {
            case TaskConstants.MEETING:
                etTitleInput.setHint(R.string.cmtg_title_hint);
                etLocationInput.setText(task.getLocation());
                etLocationInput.setHint(R.string.cmtg_location_hint);
                tvInviteeLabel.setText(task.getWholeGroupAssigned() ? getString(R.string.etsk_mtg_invite) :
                        String.format(getString(R.string.etsk_mtg_invite_x), task.getAssignedMemberCount()));
                btTaskUpdate.setText(R.string.etsk_bt_mtg_save);
                break;
            case TaskConstants.VOTE:
                etTitleInput.setHint(R.string.cvote_subject);
                descriptionBody.setVisibility(View.VISIBLE);
                ivDescExpandIcon.setImageResource(R.drawable.ic_arrow_up);
                tvInviteeLabel.setText(task.getWholeGroupAssigned() ? getString(R.string.etsk_vote_invite) :
                        String.format(getString(R.string.etsk_vote_invite_x), task.getAssignedMemberCount()));
                btTaskUpdate.setText(R.string.etsk_bt_vote_save);
                break;
            case TaskConstants.TODO:
                etTitleInput.setHint(R.string.ctodo_subject);
                timeCard.setVisibility(View.GONE);
                descriptionBody.setVisibility(View.VISIBLE);
                ivDescExpandIcon.setImageResource(R.drawable.ic_arrow_up);
                tvInviteeLabel.setText(task.getWholeGroupAssigned() ? getString(R.string.etsk_todo_invite) :
                        String.format(getString(R.string.etsk_todo_invite_x), task.getAssignedMemberCount()));
                btTaskUpdate.setText(R.string.etsk_bt_todo_save);
                break;
            default:
                throw new UnsupportedOperationException("Error! Fragment must have valid task type");
        }
    }

    private void fetchAssignedMembers() {
        if (task.getWholeGroupAssigned()) {
            selectedMembers = new ArrayList<>();
        } else {
            GrassrootRestService.getInstance().getApi().fetchAssignedMembers(
                    PreferenceUtils.getPhoneNumber(), PreferenceUtils.getAuthToken(),
                    task.getTaskUid(), taskType).enqueue(new Callback<List<Member>>() {
                @Override
                public void onResponse(Call<List<Member>> call, Response<List<Member>> response) {
                    if (response.isSuccessful()) {
                        selectedMembers = response.body();
                    } else {
                        selectedMembers = new ArrayList<>();
                    }
                }

                @Override
                public void onFailure(Call<List<Member>> call, Throwable t) {
                    selectedMembers = new ArrayList<>();
                }
            });
        }
    }

    @OnEditorAction(R.id.etsk_et_title)
    public boolean onTitleNextOrDone(TextInputEditText view, int actionId, KeyEvent event) {
        if (!etTitleInput.getText().toString().trim().equals(task.getTitle())) {
            etTitleInput.setTextColor(changeColor);
        }

        if (!taskType.equals(TaskConstants.MEETING)) {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                hideKeyboard(view);
                etTitleInput.clearFocus();
                return true;
            }
        }

        return false;
    }

    @OnEditorAction(R.id.etsk_et_location)
    public boolean onLocationNextOrDone(TextInputEditText view, int actionId, KeyEvent event) {
        if (taskType.equals(TaskConstants.MEETING)) {
            if (!etLocationInput.getText().toString().trim().equals(task.getLocation())) {
                etLocationInput.setTextColor(changeColor);
            }

            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                hideKeyboard(view);
                etLocationInput.clearFocus();
                return true;
            }
        }
        return false;
    }

    // note: this assumes the view being passed has focus ...
    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    @OnClick(R.id.etsk_cv_date)
    public void launchDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(getActivity(), R.style.AppTheme, this, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    @Override
    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
        calendar.set(year, monthOfYear, dayOfMonth);
        dateDisplayed.setText(String.format(getString(R.string.etsk_mtg_date_changed), TaskConstants.dateDisplayWithoutHours.format(calendar.getTime())));
        dateDisplayed.setTextColor(changeColor);
    }

    @OnClick(R.id.etsk_cv_time)
    public void launchTimePicker() {
        TimePickerDialog dialog = new TimePickerDialog(getActivity(), R.style.AppTheme, this, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
        dialog.show();
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        timeDisplayed.setText(String.format(getString(R.string.etsk_mtg_time_changed), TaskConstants.timeDisplayWithoutDate.format(calendar.getTime())));
        timeDisplayed.setTextColor(changeColor);
    }

    @OnClick(R.id.etsk_cv_description)
    public void expandDescription() {
        if (descriptionBody.getVisibility() == View.GONE) {
            ivDescExpandIcon.setImageResource(R.drawable.ic_arrow_up);
            descriptionBody.setVisibility(View.VISIBLE);
        } else {
            ivDescExpandIcon.setImageResource(R.drawable.ic_arrow_down);
            descriptionBody.setVisibility(View.GONE);
        }
    }

    @OnEditorAction(R.id.etsk_et_description)
    public boolean onDescriptionDone(TextInputEditText view, int actionId, KeyEvent event) {
        if (event != null) {
            if (!event.isShiftPressed() || !event.isAltPressed()) {
                if ((actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_NULL)
                        && event.getAction() == KeyEvent.ACTION_DOWN) {
                    hideKeyboard(view);
                    view.clearFocus();
                    if (!etDescriptionInput.getText().toString().trim().equals(task.getDescription())) {
                        descriptionHeader.setTextColor(changeColor);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @OnClick(R.id.etsk_cv_notify)
    public void changeMemberSelection() {
        Intent i = MenuUtils.memberSelectionIntent(getActivity(), task.getParentUid(), EditTaskFragment.class.getCanonicalName(),
                new ArrayList<>(selectedMembers));
        startActivityForResult(i, Constant.activitySelectGroupMembers);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && requestCode == Constant.activitySelectGroupMembers) {
            if (data == null) {
                throw new UnsupportedOperationException("Error! Null data from select members activity");
            }

            List<Member> newlySelectedMembers = data.getParcelableArrayListExtra(Constant.SELECTED_MEMBERS_FIELD);
            if (!selectedMembers.equals(newlySelectedMembers)) {
                Log.e(TAG, "changed selected members ..." + newlySelectedMembers);
                selectedMembers = newlySelectedMembers;
                tvInviteeLabel.setText(String.format(getString(R.string.etsk_mtg_invite_x), selectedMembers.size()));
                tvInviteeLabel.setTextColor(changeColor);
            }
        }
    }

    @OnClick(R.id.etsk_btn_update_task)
    public void confirmAndUpdate() {
        if (etTitleInput.getText().toString().trim().equals("")) {
            ErrorUtils.showSnackBar(vContainer, "Please enter a subject", Snackbar.LENGTH_LONG, "", null);
        } else if (TaskConstants.MEETING.equals(taskType) && etLocationInput.getText().toString().trim().equals("")) {
            ErrorUtils.showSnackBar(vContainer, "Please enter a location", Snackbar.LENGTH_LONG, "", null);
        } else {
            updateTask();
        }
    }

    public void updateTask() {
        setUpUpdateApiCall().enqueue(new Callback<TaskResponse>() {
            @Override
            public void onResponse(Call<TaskResponse> call, Response<TaskResponse> response) {
                if (response.isSuccessful()) {
                    Intent i = new Intent();
                    i.putExtra(Constant.SUCCESS_MESSAGE, generateSuccessString());
                    getActivity().setResult(Activity.RESULT_OK, i);
                    EventBus.getDefault().post(new TaskUpdatedEvent(response.body().getTasks().get(0)));
                    getActivity().finish();
                } else {
                    ErrorUtils.showSnackBar(vContainer, "Error! Something went wrong", Snackbar.LENGTH_LONG, "", null);
                }
            }

            @Override
            public void onFailure(Call<TaskResponse> call, Throwable t) {
                ErrorUtils.connectivityError(getActivity(), R.string.error_no_network, new NetworkErrorDialogListener() {
                    @Override
                    public void retryClicked() {
                        updateTask();
                    }

                });
            }
        });
    }

    public Call<TaskResponse> setUpUpdateApiCall() {
        final String uid = task.getTaskUid();
        final String phoneNumber = PreferenceUtils.getUserPhoneNumber(ApplicationLoader.applicationContext);
        final String code = PreferenceUtils.getAuthToken(ApplicationLoader.applicationContext);
        final String title = etTitleInput.getText().toString();
        final String description = etDescriptionInput.getText().toString();

        Date updatedDate = calendar.getTime();
        final String dateTimeISO = Constant.isoDateTimeSDF.format(updatedDate);

        if (selectedMembers == null) {
            selectedMembers = new ArrayList<>();
        }

        switch (taskType) {
            case TaskConstants.MEETING:
                final String location = etLocationInput.getText().toString();
                return GrassrootRestService.getInstance().getApi().editMeeting(phoneNumber, code, uid,
                        title, description, location, dateTimeISO, selectedMembers);
            case TaskConstants.VOTE:
                return GrassrootRestService.getInstance().getApi().editVote(phoneNumber, code, uid, title,
                        description, dateTimeISO);
            case TaskConstants.TODO:
                return GrassrootRestService.getInstance().getApi().editTodo(phoneNumber, code, title,
                        dateTimeISO, null);
            default:
                throw new UnsupportedOperationException("Error! Missing task type in call");
        }
    }

    private String generateSuccessString() {
        switch (taskType) {
            case TaskConstants.MEETING:
                return getActivity().getString(R.string.etsk_meeting_updated_success);
            case TaskConstants.VOTE:
                return getActivity().getString(R.string.etsk_vote_update_success);
            case TaskConstants.TODO:
                return getActivity().getString(R.string.etsk_todo_updated_success);
            default:
                throw new UnsupportedOperationException("Error! Missing task type");
        }
    }

    @BindView(R.id.etsk_subject_count)
    TextView subjectCharCounter;
    @BindView(R.id.etsk_desc_count)
    TextView descriptionCharCounter;
    @BindView(R.id.etsk_location_count)
    TextView locationCharCounter;

    @OnTextChanged(R.id.etsk_et_title)
    public void changeCharCounter(CharSequence s) {
        subjectCharCounter.setText(s.length() + " / 35");
    }

    @OnTextChanged(R.id.etsk_et_location)
    public void changeLocCharCounter(CharSequence s) {
        locationCharCounter.setText(s.length() + " / 35");
    }

    @OnTextChanged(R.id.etsk_et_description)
    public void changeDescCounter(CharSequence s) {
        descriptionCharCounter.setText(s.length() + " / 250");
    }

}
