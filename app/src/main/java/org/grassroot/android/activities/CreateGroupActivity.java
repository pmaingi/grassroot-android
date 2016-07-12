package org.grassroot.android.activities;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.grassroot.android.R;
import org.grassroot.android.events.GroupCreatedEvent;
import org.grassroot.android.fragments.ContactSelectionFragment;
import org.grassroot.android.fragments.MemberListFragment;
import org.grassroot.android.interfaces.GroupConstants;
import org.grassroot.android.models.Contact;
import org.grassroot.android.models.Group;
import org.grassroot.android.models.GroupResponse;
import org.grassroot.android.models.Member;
import org.grassroot.android.services.GroupService;
import org.grassroot.android.utils.Constant;
import org.grassroot.android.utils.ErrorUtils;
import org.grassroot.android.utils.PermissionUtils;
import org.grassroot.android.utils.PreferenceUtils;
import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTextChanged;
import retrofit2.Response;

import static butterknife.OnTextChanged.Callback.AFTER_TEXT_CHANGED;

public class CreateGroupActivity extends PortraitActivity
    implements MemberListFragment.MemberListListener, MemberListFragment.MemberClickListener,
    ContactSelectionFragment.ContactSelectionListener {

  private static final String TAG = CreateGroupActivity.class.getSimpleName();

  @BindView(R.id.rl_cg_root) RelativeLayout rlCgRoot;

  @BindView(R.id.cg_add_member_options) FloatingActionButton addMemberOptions;
  @BindView(R.id.ll_add_member_manually) LinearLayout addMemberManually;
  @BindView(R.id.ll_add_member_contacts) LinearLayout addMemberFromContacts;

  @BindView(R.id.tv_counter) TextView tvCounter;
  @BindView(R.id.et_groupname) TextInputEditText et_groupname;
  @BindView(R.id.et_group_description) TextInputEditText et_group_description;

  private List<Member> manuallyAddedMembers;
  private Map<Integer, Member> mapMembersContacts;
  private MemberListFragment memberListFragment;

  private ContactSelectionFragment contactSelectionFragment;
  private boolean onMainScreen;
  private boolean menuOpen;

  private ProgressDialog progressDialog;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_create__group);
    ButterKnife.bind(this);

    progressDialog = new ProgressDialog(this);
    progressDialog.setMessage(getString(R.string.txt_pls_wait));
    progressDialog.setIndeterminate(true);

    init();
    setUpMemberList();
  }

  private void init() {
    memberListFragment = MemberListFragment.newInstance(null, false, false, this, this, null);
    contactSelectionFragment = ContactSelectionFragment.newInstance(null, false);
    mapMembersContacts = new HashMap<>();
    manuallyAddedMembers = new ArrayList<>();
    onMainScreen = true;
  }

  @OnClick(R.id.cg_add_member_options)
  public void toggleAddMenu() {
    addMemberOptions.setImageResource(menuOpen ? R.drawable.ic_add : R.drawable.ic_add_45d);
    addMemberFromContacts.setVisibility(menuOpen ? View.GONE : View.VISIBLE);
    addMemberManually.setVisibility(menuOpen ? View.GONE : View.VISIBLE);
    menuOpen = !menuOpen;
  }

  private void setUpMemberList() {
    getSupportFragmentManager().beginTransaction()
        .add(R.id.cg_new_member_list_container, memberListFragment)
        .commit();
  }

  @OnClick(R.id.cg_iv_crossimage) public void ivCrossimage() {
    if (!onMainScreen) {
      // note : this means we do not save / return the contacts on cross clicked
      getSupportFragmentManager().popBackStack();
      onMainScreen = true;
    } else {
      progressDialog.dismiss();
      finish();
    }
  }

  @OnTextChanged(value = R.id.et_group_description, callback = AFTER_TEXT_CHANGED)
  public void changeLengthCounter(CharSequence s) {
    tvCounter.setText("" + s.length() + "/" + "160");
  }

  @OnClick(R.id.ll_add_member_contacts) public void icon_add_from_contacts() {
    toggleAddMenu();
    if (!PermissionUtils.contactReadPermissionGranted(this)) {
      PermissionUtils.requestReadContactsPermission(this);
    } else {
      launchContactSelectionFragment();
    }
  }

  @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
      int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (PermissionUtils.checkContactsPermissionGranted(requestCode, grantResults)) {
      launchContactSelectionFragment();
    }
  }

  private void launchContactSelectionFragment() {
    onMainScreen = false;
    getSupportFragmentManager().beginTransaction()
        .add(R.id.cg_body_root, contactSelectionFragment)
        .addToBackStack(null)
        .commitAllowingStateLoss(); // todo : clean this up in a less hacky way (known issue w/ support lib and Android 6+, need to do an onResume check or similar)
  }

  private void closeContactSelectionFragment() {
    onMainScreen = true;
    getSupportFragmentManager().beginTransaction().remove(contactSelectionFragment).commit();
  }

  @Override public void onContactSelectionComplete(List<Contact> contactsSelected) {
    progressDialog.show();
    List<Member> selectedMembers = new ArrayList<>(manuallyAddedMembers);
    for (Contact c : contactsSelected) {
      if (mapMembersContacts.containsKey(c.id)) {
        selectedMembers.add(mapMembersContacts.get(c.id));
      } else {
        Member m =
            new Member(c.selectedMsisdn, c.getDisplayName(), GroupConstants.ROLE_ORDINARY_MEMBER,
                c.id, true);
        mapMembersContacts.put(c.id, m);
        selectedMembers.add(m);
      }
    }
    memberListFragment.transitionToMemberList(selectedMembers);
    closeContactSelectionFragment();
    progressDialog.hide();
  }

  @OnClick(R.id.ll_add_member_manually) public void ic_edit_call() {
    toggleAddMenu();
    startActivityForResult(new Intent(CreateGroupActivity.this, AddContactManually.class),
        Constant.activityManualMemberEntry);
  }

  @OnClick(R.id.cg_bt_save) public void save() {
    if (menuOpen) {
      toggleAddMenu();
    }
    validate_allFields();
  }

  private void validate_allFields() {
    if (!(TextUtils.isEmpty(
        et_groupname.getText().toString().trim().replaceAll(Constant.regexAlphaNumeric, "")))) {
      createGroup();
    } else {
      ErrorUtils.showSnackBar(rlCgRoot, R.string.error_group_name_blank, Snackbar.LENGTH_SHORT);
    }
  }

  private void createGroup() {
    String groupName =
        et_groupname.getText().toString().trim().replaceAll(Constant.regexAlphaNumeric, "");
    String groupDescription = et_group_description.getText().toString().trim();
    List<Member> groupMembers = memberListFragment.getSelectedMembers();

    progressDialog.show();
    GroupService.getInstance().createGroup(groupName, groupDescription, groupMembers, new GroupService.GroupCreationListener() {
      @Override
      public void groupCreatedLocally(Group group) {
        progressDialog.dismiss();
        handleSuccessfulGroupCreation(group);
      }

      @Override
      public void groupCreatedOnServer(Group group) {
        progressDialog.dismiss();
        handleSuccessfulGroupCreation(group);
      }

      @Override
      public void groupCreationError(Response<GroupResponse> response) {
        progressDialog.dismiss();
        ErrorUtils.showSnackBar(rlCgRoot, R.string.error_generic, Snackbar.LENGTH_SHORT);
      }
    });
  }

  private void handleSuccessfulGroupCreation(Group group) {
    PreferenceUtils.setUserHasGroups(getApplicationContext(), true);
    EventBus.getDefault().post(new GroupCreatedEvent());
    Intent i = new Intent(CreateGroupActivity.this, ActionCompleteActivity.class);
    String completionMessage;
    if (!group.getIsLocal()) {
      completionMessage = String.format(getString(R.string.ac_body_group_create_server), group.getGroupName(),
              group.getGroupMemberCount());
    } else {
      completionMessage = String.format(getString(R.string.ac_body_group_create_local), group.getGroupName());
    }
    i.putExtra(ActionCompleteActivity.HEADER_FIELD, R.string.ac_header_group_create);
    i.putExtra(ActionCompleteActivity.BODY_FIELD, completionMessage);
    i.putExtra(ActionCompleteActivity.TASK_BUTTONS, true);
    i.putExtra(ActionCompleteActivity.ACTION_INTENT, ActionCompleteActivity.HOME_SCREEN);
    i.putExtra(GroupConstants.OBJECT_FIELD, group);
    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startActivity(i);
    finish();
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == Activity.RESULT_OK && data != null) {
      if (requestCode == Constant.activityManualMemberEntry) {
        Member newMember = new Member(data.getStringExtra("selectedNumber"), data.getStringExtra("name"),
                GroupConstants.ROLE_ORDINARY_MEMBER, -1);
        manuallyAddedMembers.add(newMember);
        memberListFragment.addMembers(Collections.singletonList(newMember));
      }
    }
  }

  @Override public void onMemberListInitiated(MemberListFragment fragment) {
    // todo: use this to handle fragment setting up & observation, instead of create at start...
    // memberListFragment.setShowSelected(true);
    // memberListFragment.setCanDismissItems(true);
  }

  @Override public void onMemberListPopulated(List<Member> memberList) {

  }

  @Override public void onMemberListDone() {

  }

  @Override public void onMemberDismissed(int position, String memberUid) {
    // todo : deal with this (maybe)
  }

  @Override public void onMemberClicked(int position, String memberUid) {
    // todo : deal with this
  }

  private void setResultIntent(Group group) {
    Intent resultIntent = new Intent();
    resultIntent.putExtra(GroupConstants.OBJECT_FIELD, group);
    setResult(RESULT_OK, resultIntent);
  }
}
