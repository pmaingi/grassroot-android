package org.grassroot.android.services;

import android.text.TextUtils;
import android.util.Log;

import org.grassroot.android.events.TaskUpdatedEvent;
import org.grassroot.android.events.TasksRefreshedEvent;
import org.grassroot.android.interfaces.TaskConstants;
import org.grassroot.android.models.Group;
import org.grassroot.android.models.Member;
import org.grassroot.android.models.PreferenceObject;
import org.grassroot.android.models.ResponseTotalsModel;
import org.grassroot.android.models.RsvpListModel;
import org.grassroot.android.models.TaskModel;
import org.grassroot.android.models.exceptions.ApiCallException;
import org.grassroot.android.models.responses.GenericResponse;
import org.grassroot.android.models.responses.MemberListResponse;
import org.grassroot.android.models.responses.TaskChangedResponse;
import org.grassroot.android.models.responses.TaskResponse;
import org.grassroot.android.utils.ErrorUtils;
import org.grassroot.android.utils.NetworkUtils;
import org.grassroot.android.utils.RealmUtils;
import org.grassroot.android.utils.Utilities;
import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import io.realm.RealmList;
import retrofit2.Call;
import retrofit2.Response;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Created by luke on 2016/07/06.
 */
public class TaskService {

  private static final String TAG = TaskService.class.getSimpleName();

  private static TaskService instance;

  protected TaskService() { }

  public static TaskService getInstance() {
    TaskService methodInstance = instance;
    if (methodInstance == null) {
      synchronized (TaskService.class) {
        methodInstance = instance;
        if (methodInstance == null) {
          instance = methodInstance = new TaskService();
        }
      }
    }
    return methodInstance;
  }

  public Observable<String> fetchTasks(final String parentUid, Scheduler observingThread) {
    return TextUtils.isEmpty(parentUid) ? fetchUpcomingTasks(observingThread) :
        fetchGroupTasks(parentUid, observingThread);
  }

  public Observable<String> fetchUpcomingTasks(Scheduler observingThread) {
    long lastTimeUpdated = RealmUtils.loadPreferencesFromDB().getLastTimeUpcomingTasksFetched();
    return (lastTimeUpdated == 0) ? fetchUpcomingTasksForFirstTime(observingThread) :
        fetchUpcomingTasksAndCancelled(lastTimeUpdated, observingThread);
  }

  private Observable<String> fetchUpcomingTasksForFirstTime(Scheduler observingThread) {
    observingThread = (observingThread == null) ? AndroidSchedulers.mainThread() : observingThread;
    return Observable.create(new Observable.OnSubscribe<String>() {
      @Override
      public void call(Subscriber<? super String> subscriber) {
        final String phoneNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
        final String code = RealmUtils.loadPreferencesFromDB().getToken();
        if (!NetworkUtils.isOfflineOrLoggedOut(subscriber, phoneNumber, code)) {
          try {
            Response<TaskResponse> tasks = GrassrootRestService.getInstance().getApi()
                .getUserTasks(phoneNumber, code).execute();
            if (tasks.isSuccessful()) {
              updateTasksFetchedTime(null);
              persistUpcomingTasks(tasks.body().getTasks());
              subscriber.onNext(NetworkUtils.FETCHED_SERVER);
              subscriber.onCompleted();
            } else {
              subscriber.onNext(NetworkUtils.SERVER_ERROR);
              subscriber.onCompleted();
            }
          } catch (IOException e) {
            handleFetchConnectionError(subscriber);
          } catch (Exception e) {
            subscriber.onNext(NetworkUtils.CONNECT_ERROR); // in case get strange, "phone number not null"
            subscriber.onCompleted();
          }
        }
      }
    }).subscribeOn(Schedulers.io()).observeOn(observingThread);
  }

  private Observable<String> fetchUpcomingTasksAndCancelled(final long changedSince, Scheduler observingThread) {
    observingThread = (observingThread == null) ? AndroidSchedulers.mainThread() : observingThread;
    return Observable.create(new Observable.OnSubscribe<String>() {
      @Override
      public void call(Subscriber<? super String> subscriber) {
        if (!NetworkUtils.isOnline()) {
          subscriber.onNext(NetworkUtils.OFFLINE_SELECTED);
        } else {
          final String phoneNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
          final String code = RealmUtils.loadPreferencesFromDB().getToken();
          try {
            Response<TaskChangedResponse> response = GrassrootRestService.getInstance().getApi().
                getUpcomingTasksAndCancellations(phoneNumber, code, changedSince).execute();
            if (response.isSuccessful()) {
              updateTasksFetchedTime(null);
              RealmUtils.removeObjectsByUid(TaskModel.class, "taskUid", response.body().getRemovedUids());
              persistUpcomingTasks(response.body().getAddedAndUpdated());
              subscriber.onNext(NetworkUtils.FETCHED_SERVER);
              subscriber.onCompleted();
            } else {
              final String restMessage = ErrorUtils.getRestMessage(response.errorBody());
              subscriber.onNext(restMessage);
              subscriber.onCompleted();
            }
          } catch (IOException e) {
            handleFetchConnectionError(subscriber);
          }
        }
      }
    }).subscribeOn(Schedulers.io()).observeOn(observingThread);
  }

  private Observable<String> fetchGroupTasks(final String groupUid, Scheduler observingThread) {
    observingThread = (observingThread == null) ? AndroidSchedulers.mainThread() : observingThread;
    return Observable.create(new Observable.OnSubscribe<String>() {
      @Override
      public void call(Subscriber<? super String> subscriber) {
        if (!NetworkUtils.isOnline()) {
          subscriber.onNext(NetworkUtils.OFFLINE_SELECTED);
        } else {
          final String phoneNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
          final String code = RealmUtils.loadPreferencesFromDB().getToken();
          try {
            Response<TaskChangedResponse> response = GrassrootRestService.getInstance().getApi()
                .getGroupTasks(phoneNumber, code, groupUid).execute();
            if (response.isSuccessful()) {
              updateAndRemoveTasks(response.body(), groupUid);
              subscriber.onNext(NetworkUtils.FETCHED_SERVER);
              subscriber.onCompleted();
            } else {
              final String restMessage = ErrorUtils.getRestMessage(response.errorBody());
              subscriber.onNext(restMessage);
              subscriber.onCompleted();
            }

          } catch (IOException e) {
            handleFetchConnectionError(subscriber);
          }
        }
      }
    }).subscribeOn(Schedulers.io()).observeOn(observingThread);
  }

  private void handleFetchConnectionError(Subscriber<? super String> subscriber) {
    NetworkUtils.setConnectionFailed();
    subscriber.onNext(NetworkUtils.CONNECT_ERROR);
    subscriber.onCompleted();
  }

  @SuppressWarnings("unchecked")
  private void persistUpcomingTasks(RealmList<TaskModel> tasks) {
    for (TaskModel task : tasks) {
      task.calcDeadlineDate(); // triggers processing & store of Date object (maybe move into a JSON converter)
    }
    RealmUtils.saveDataToRealm(tasks, Schedulers.immediate()).subscribe(new Action1() {
      @Override
      public void call(Object o) {
        // do the post in here to make sure count updates etc refresh to correct count
        EventBus.getDefault().post(new TasksRefreshedEvent());
      }
    });
  }

  private void updateAndRemoveTasks(final TaskChangedResponse responseBody, final String groupUid) {
    List<TaskModel> addedUpdated = responseBody.getAddedAndUpdated();
    for (TaskModel task : addedUpdated) {
      task.calcDeadlineDate();
    }
    RealmUtils.saveDataToRealmSync(addedUpdated);
    EventBus.getDefault().post(new TasksRefreshedEvent(groupUid));
  }

  private void updateTasksFetchedTime(String parentUid) {
    if (!TextUtils.isEmpty(parentUid)) {
      Group group = RealmUtils.loadObjectFromDB(Group.class, "groupUid", parentUid);
      if (group != null) {
        group.setLastTimeTasksFetched(String.valueOf(Utilities.getCurrentTimeInMillisAtUTC()));
        group.setFetchedTasks(true);
        RealmUtils.saveGroupToRealm(group);
      }
    } else {
      PreferenceObject prefs = RealmUtils.loadPreferencesFromDB();
      prefs.setLastTimeUpcomingTasksFetched(Utilities.getCurrentTimeInMillisAtUTC());
      RealmUtils.saveDataToRealm(prefs).subscribe();
    }
  }

  /*
  SECTION: CREATING TASK
   */

  public Observable<TaskModel> sendTaskToServer(final TaskModel task, Scheduler observingThread) {
    observingThread = (observingThread == null) ? AndroidSchedulers.mainThread() : observingThread;
    return Observable.create(new Observable.OnSubscribe<TaskModel>() {
      @Override
      public void call(Subscriber<? super TaskModel> subscriber) {
        if (!NetworkUtils.isOnline()) {
          RealmUtils.saveDataToRealmSync(task);
          subscriber.onNext(task);
          subscriber.onCompleted();
        } else {
          try {
            Response<TaskResponse> response = newTaskApiCall(task).execute();
            if (response.isSuccessful()) {
              final TaskModel taskFromServer = response.body().getTasks().first();
              taskFromServer.calcDeadlineDate();
              taskFromServer.setLocal(false);
              RealmUtils.saveDataToRealmSync(taskFromServer);
              subscriber.onNext(taskFromServer);
              if (task.isLocal() && !task.getTaskUid().equals(taskFromServer.getTaskUid())) {
                RealmUtils.removeObjectFromDatabase(TaskModel.class, "taskUid", task.getTaskUid());
              }
              subscriber.onCompleted();
            } else {
              throw new ApiCallException(NetworkUtils.CONNECT_ERROR, ErrorUtils.getRestMessage(response.errorBody()));
            }
          } catch (IOException e) {
            RealmUtils.saveDataToRealmSync(task);
            NetworkUtils.setConnectionFailed();
            throw new ApiCallException(NetworkUtils.CONNECT_ERROR);
          }
        }
      }
    }).subscribeOn(Schedulers.io()).observeOn(observingThread);
  }

  private Call<TaskResponse> newTaskApiCall(TaskModel task) {
    final String phoneNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
    final String code = RealmUtils.loadPreferencesFromDB().getToken();

    switch (task.getType()) {
      case TaskConstants.MEETING:
        final String location = task.getLocation();
        return GrassrootRestService.getInstance()
            .getApi()
            .createMeeting(phoneNumber, code, task.getParentUid(), task.getTitle(),
                task.getDescription(), task.getDeadlineISO(), task.getMinutes(), location,
                new HashSet<>(RealmUtils.convertListOfRealmStringInListOfString(task.getMemberUIDS())));
      case TaskConstants.VOTE:
        return GrassrootRestService.getInstance()
            .getApi()
            .createVote(phoneNumber, code, task.getParentUid(), task.getTitle(),
                task.getDescription(), task.getDeadlineISO(), task.getMinutes(), new HashSet<>(
                    RealmUtils.convertListOfRealmStringInListOfString(task.getMemberUIDS())),
                false);
      case TaskConstants.TODO:
        return GrassrootRestService.getInstance()
            .getApi()
            .createTodo(phoneNumber, code, task.getParentUid(), task.getTitle(),
                task.getDescription(), task.getDeadlineISO(), task.getMinutes(),
                new HashSet<>(RealmUtils.convertListOfRealmStringInListOfString(task.getMemberUIDS())));
      default:
        throw new UnsupportedOperationException("Error! Missing task type in call");
    }
  }

  /*
  SECTION: TASK EDITS
   */

  public Observable<String> sendTaskUpdateToServer(final TaskModel model, final boolean selectedMembersChanged,
                                                   Scheduler observingThread) {
    observingThread = (observingThread == null) ? AndroidSchedulers.mainThread() : observingThread;
    return Observable.create(new Observable.OnSubscribe<String>() {
      @Override
      public void call(Subscriber<? super String> subscriber) {
        if (!NetworkUtils.isOnline()) {
          subscriber.onNext(NetworkUtils.OFFLINE_SELECTED);
          subscriber.onCompleted();
        } else {
          try {
            Response<TaskResponse> editedTask = updateTaskApiCall(model, selectedMembersChanged).execute();
            if (editedTask.isSuccessful()) {
              RealmUtils.saveDataToRealmSync(editedTask.body().getTasks().first());
              subscriber.onNext(NetworkUtils.SAVED_SERVER);
              subscriber.onCompleted();
            } else {
              subscriber.onNext(NetworkUtils.SERVER_ERROR);
              subscriber.onCompleted();
            }
          } catch (IOException e) {
            NetworkUtils.setConnectionFailed();
            subscriber.onNext(NetworkUtils.CONNECT_ERROR);
            subscriber.onCompleted();
          }
        }
      }
    }).subscribeOn(Schedulers.io()).observeOn(observingThread);
  }

  private Call<TaskResponse> updateTaskApiCall(TaskModel model, boolean selectedMembersChanged) {
    List<String> memberUids =
        selectedMembersChanged ? model.getMemberUIDS() : Collections.EMPTY_LIST;
    final String phoneNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
    final String code = RealmUtils.loadPreferencesFromDB().getToken();
    switch (model.getType()) {
      case TaskConstants.MEETING:
        return GrassrootRestService.getInstance()
            .getApi()
            .editMeeting(phoneNumber, code, model.getTaskUid(), model.getTitle(),
                model.getDescription(), model.getLocation(), model.getDeadlineISO(), memberUids);
      case TaskConstants.VOTE:
        return GrassrootRestService.getInstance()
            .getApi()
            .editVote(phoneNumber, code, model.getTaskUid(), model.getTitle(),
                model.getDescription(), model.getDeadlineISO());
      case TaskConstants.TODO:
        return GrassrootRestService.getInstance()
            .getApi()
            .editTodo(phoneNumber, code, model.getTaskUid(), model.getTitle(),
                model.getDescription(), model.getDeadlineISO(), null);
      default:
        throw new UnsupportedOperationException("Error! Missing task type in call");
    }
  }


  /*
  FETCH A TASK AND STORE IT LOCALLY (FOR USE IN BACKGROUND WHEN GET & VIEW NOTIFICATION)
   */

  public Observable<String> fetchAndStoreTask(final String taskUid, final String taskType, Scheduler observingThread) {
    observingThread = (observingThread == null) ? AndroidSchedulers.mainThread() : observingThread;
    return Observable.create(new Observable.OnSubscribe<String>() {
      @Override
      public void call(Subscriber<? super String> subscriber) {
        if (TextUtils.isEmpty(taskUid) || TextUtils.isEmpty(taskType)) {
          Log.e(TAG, "Error! Faulty GCM packet or other cause has led to null taskUid or type, existing");
          subscriber.onNext(NetworkUtils.LOCAL_ERROR);
          subscriber.onCompleted();
        } else if (!NetworkUtils.isOnline()) {
          subscriber.onNext(NetworkUtils.OFFLINE_SELECTED);
          subscriber.onCompleted();
        } else {
          final String phoneNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
          final String code = RealmUtils.loadPreferencesFromDB().getToken();
          try {
            Response<TaskResponse> taskResponse = GrassrootRestService.getInstance().getApi()
                .fetchTaskEntity(phoneNumber, code, taskUid, taskType).execute();
            if (taskResponse.isSuccessful()) {
              RealmUtils.saveDataToRealmSync(taskResponse.body().getTasks().first());
              subscriber.onNext(NetworkUtils.FETCHED_SERVER);
              subscriber.onCompleted();
            } else {
              subscriber.onNext(NetworkUtils.SERVER_ERROR);
              subscriber.onCompleted();
            }
          } catch (IOException e) {
            NetworkUtils.setConnectionFailed();
            subscriber.onNext(NetworkUtils.CONNECT_ERROR);
            subscriber.onCompleted();
          }
        }
      }
    }).subscribeOn(Schedulers.io()).observeOn(observingThread);
  }

  public Observable<RsvpListModel> fetchMeetingRsvps(final String taskUid) {
    return Observable.create(new Observable.OnSubscribe<RsvpListModel>() {
      @Override
      public void call(Subscriber<? super RsvpListModel> subscriber) {
        if (!NetworkUtils.isOnline()) {
          throw new ApiCallException(NetworkUtils.CONNECT_ERROR); // switch to offline_selected
        } else {
          final String phoneNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
          final String code = RealmUtils.loadPreferencesFromDB().getToken();
          try {
            Response<RsvpListModel> response = GrassrootRestService.getInstance().getApi()
                .fetchMeetingRsvps(phoneNumber, code, taskUid).execute();
            if (response.isSuccessful()) {
              subscriber.onNext(response.body());
              subscriber.onCompleted();
            } else {
              throw new ApiCallException(NetworkUtils.SERVER_ERROR,
                  ErrorUtils.getRestMessage(response.errorBody()));
            }
          } catch (IOException e) {
            throw new ApiCallException(NetworkUtils.CONNECT_ERROR);
          }
        }
      }
    }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
  }

  public Observable<ResponseTotalsModel> fetchVoteTotals(final String taskUid) {
    return Observable.create(new Observable.OnSubscribe<ResponseTotalsModel>() {
      @Override
      public void call(Subscriber<? super ResponseTotalsModel> subscriber) {
        if (!NetworkUtils.isOnline()) {
          throw new ApiCallException(NetworkUtils.CONNECT_ERROR);
        } else {
          final String phoneNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
          final String code = RealmUtils.loadPreferencesFromDB().getToken();
          try {
            Response<ResponseTotalsModel> response = GrassrootRestService.getInstance().getApi()
                .fetchVoteTotals(phoneNumber, code, taskUid).execute();
            if (response.isSuccessful()) {
              subscriber.onNext(response.body());
              subscriber.onCompleted();
            } else {
              throw new ApiCallException(NetworkUtils.SERVER_ERROR,
                  ErrorUtils.getRestMessage(response.errorBody()));
            }
          } catch (IOException e) {
            throw new ApiCallException(NetworkUtils.CONNECT_ERROR);
          }
        }
      }
    }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
  }

  public Observable<List<Member>> fetchAssignedMembers(final String taskUid, final String taskType) {
    return Observable.create(new Observable.OnSubscribe<List<Member>>() {
      @Override
      public void call(Subscriber<? super List<Member>> subscriber) {
        if (NetworkUtils.isOnline()) {
          final String phoneNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
          final String code = RealmUtils.loadPreferencesFromDB().getToken();
          try {
            Response<MemberListResponse> response = GrassrootRestService.getInstance().getApi()
                .fetchAssignedMembers(phoneNumber, code, taskUid, taskType).execute();
            if (response.isSuccessful()) {
              subscriber.onNext(response.body().getMembers());
              subscriber.onCompleted();
            } else {
              throw new ApiCallException(NetworkUtils.SERVER_ERROR,
                  ErrorUtils.getRestMessage(response.errorBody()));
            }
          } catch (IOException e) {
            NetworkUtils.setConnectionFailed();
            throw new ApiCallException(NetworkUtils.CONNECT_ERROR);
          }
        } else {
          throw new ApiCallException(NetworkUtils.CONNECT_ERROR);
        }
      }
    }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
  }

  /*
  RESPONDING TO A TASK, CANCELLING ETC (and storing it)
   */

  public Observable<String> respondToTask(final String taskUid, final String response, Scheduler observingThread) {
    observingThread = (observingThread == null) ? AndroidSchedulers.mainThread() : observingThread;
    return Observable.create(new Observable.OnSubscribe<String>() {
      @Override
      public void call(Subscriber<? super String> subscriber) {
        TaskModel task = RealmUtils.loadObjectFromDB(TaskModel.class, "taskUid", taskUid);
        if (!NetworkUtils.isOnline()) {
          storeTaskResponseOffline(task, response);
          EventBus.getDefault().post(new TaskUpdatedEvent(task));
          subscriber.onNext(NetworkUtils.SAVED_OFFLINE_MODE);
          subscriber.onCompleted();
        } else {
          try {
            final String phoneNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
            final String code = RealmUtils.loadPreferencesFromDB().getToken();
            Call<TaskResponse> call =
                task.getType().equals(TaskConstants.VOTE) ? voteCall(taskUid, phoneNumber,code,response)
                    : task.getType().equals(TaskConstants.MEETING) ? meetingCall(taskUid,phoneNumber,code,response)
                    : competeTodo(taskUid, phoneNumber, code, response);
            Response<TaskResponse> apiCall = call.execute();
            if (apiCall.isSuccessful()) {
              RealmUtils.saveDataToRealmSync(apiCall.body().getTasks().get(0));
              subscriber.onNext(NetworkUtils.SAVED_SERVER);
              EventBus.getDefault().post(new TaskUpdatedEvent(apiCall.body().getTasks().get(0)));
              subscriber.onCompleted();
            } else {
              throw new ApiCallException(NetworkUtils.SERVER_ERROR, ErrorUtils.getRestMessage(apiCall.errorBody()));
            }
          } catch (IOException e) {
            storeTaskResponseOffline(task, response);
            NetworkUtils.setConnectionFailed();
            throw new ApiCallException(NetworkUtils.CONNECT_ERROR);
          }
        }
      }
    }).subscribeOn(Schedulers.io()).observeOn(observingThread);
  }

  public Observable<String> cancelTask(final String taskUid, final String taskType, Scheduler observingThread) {
    observingThread = (observingThread == null) ? AndroidSchedulers.mainThread() : observingThread;
    return Observable.create(new Observable.OnSubscribe<String>() {
      @Override
      public void call(Subscriber<? super String> subscriber) {
        try {
          Response<GenericResponse> response = setUpCancelApiCall(taskType, taskUid).execute();
          if (response.isSuccessful()) {
            RealmUtils.removeObjectFromDatabase(TaskModel.class, "taskUid", taskUid);
            // subscriber must issue event, to trigger view removal, etc
            subscriber.onNext(NetworkUtils.SAVED_SERVER);
            subscriber.onCompleted();
          } else {
            throw new ApiCallException(NetworkUtils.SERVER_ERROR,
                ErrorUtils.getRestMessage(response.errorBody()));
          }
        } catch (IOException e) {
          NetworkUtils.setConnectionFailed();
          throw new ApiCallException(NetworkUtils.CONNECT_ERROR);
        }
      }
    }).subscribeOn(Schedulers.io()).observeOn(observingThread);
  }

  public Observable<String> editTask(final TaskModel updatedTask, final List<Member> selectedMembers,
                                     Scheduler observingThread) {
    observingThread = (observingThread == null) ? AndroidSchedulers.mainThread() : observingThread;
    return Observable.create(new Observable.OnSubscribe<String>() {
      @Override
      public void call(Subscriber<? super String> subscriber) {
        if (!NetworkUtils.isOnline()) {
          throw new ApiCallException(NetworkUtils.OFFLINE_SELECTED);
        } else {
          try {
            Response<TaskResponse> response = setUpUpdateApiCall(updatedTask, selectedMembers).execute();
            if (response.isSuccessful()) {
              // guarding against an index out of bounds on task list, just in case
              TaskModel revisedTask = !response.body().getTasks().isEmpty() ? response.body().getTasks().first() : updatedTask;
              RealmUtils.saveDataToRealmSync(revisedTask);
              EventBus.getDefault().post(new TaskUpdatedEvent(revisedTask));
              subscriber.onNext(NetworkUtils.SAVED_SERVER);
            } else {
              throw new ApiCallException(NetworkUtils.SERVER_ERROR, ErrorUtils.getRestMessage(response.errorBody()));
            }
          } catch (IOException e) {
            throw new ApiCallException(NetworkUtils.SERVER_ERROR);
          }
        }
      }
    }).subscribeOn(Schedulers.io()).observeOn(observingThread);
  }

  @SuppressWarnings("unchecked") // todo : finally clean these unchecked warnings up
  public Call<TaskResponse> setUpUpdateApiCall(final TaskModel model, List<Member> selectedMembers) {
    List<String> memberUids = (selectedMembers == null) ? Collections.EMPTY_LIST :
        new ArrayList<>(Utilities.convertMemberListToUids(selectedMembers));
    final String phoneNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
    final String code = RealmUtils.loadPreferencesFromDB().getToken();
    switch (model.getType()) {
      case TaskConstants.MEETING:
        return GrassrootRestService.getInstance().getApi().editMeeting(phoneNumber, code, model.getTaskUid(),
            model.getTitle(), model.getDescription(), model.getLocation(), model.getDeadlineISO(), memberUids);
      case TaskConstants.VOTE:
        return GrassrootRestService.getInstance().getApi().editVote(phoneNumber, code, model.getTaskUid(), model.getTitle(),
            model.getDescription(), model.getDeadlineISO());
      case TaskConstants.TODO:
        return GrassrootRestService.getInstance().getApi().editTodo(phoneNumber, code, model.getTaskUid(),
            model.getTitle(), model.getDescription(), model.getDeadlineISO(), null);
      default:
        throw new UnsupportedOperationException("Error! Missing task type in call");
    }
  }

  /*
  HELPER METHODS FOR SELECTING THE RIGHT API ENDPOINT TO CALL
   */

  private Call<GenericResponse> setUpCancelApiCall(final String taskType, final String taskUid) {
    final String phoneNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
    final String code = RealmUtils.loadPreferencesFromDB().getToken();
    switch (taskType) {
      case TaskConstants.MEETING:
        return GrassrootRestService.getInstance().getApi().cancelMeeting(phoneNumber, code, taskUid);
      case TaskConstants.VOTE:
        return GrassrootRestService.getInstance().getApi().cancelVote(phoneNumber, code, taskUid);
      case TaskConstants.TODO:
        return GrassrootRestService.getInstance().getApi().cancelTodo(phoneNumber, code, taskUid);
      default:
        throw new UnsupportedOperationException("Error! Missing task type in call");
    }
  }


  private Call<TaskResponse> voteCall(String taskUid, String phoneNumber, String code,String response) {
    return GrassrootRestService.getInstance()
            .getApi()
            .castVote(taskUid, phoneNumber, code, response);
  }

  private Call<TaskResponse> meetingCall(String taskUid, String phoneNumber, String code,String response) {
    return GrassrootRestService.getInstance().getApi().rsvp(taskUid, phoneNumber, code, response);
  }

  private Call<TaskResponse> competeTodo(String taskUid, String phoneNumber, String code,String response) {
    return GrassrootRestService.getInstance().getApi().completeTodo(phoneNumber, code,taskUid);
  }

  private void storeTaskResponseOffline(TaskModel task, String response) {
    switch (response){
      case TaskConstants.RESPONSE_NO:
        task.setHasResponded(true);
        task.setReply(response);
        task.setActionLocal(true);
        RealmUtils.saveDataToRealmSync(task);
        break;
      case TaskConstants.RESPONSE_YES:
        task.setHasResponded(true);
        task.setReply(response);
        task.setActionLocal(true);
        RealmUtils.saveDataToRealmSync(task);
        break;
      case TaskConstants.TODO_DONE:
        task.setHasResponded(true);
        task.setReply(response);
        task.setActionLocal(true);
        RealmUtils.saveDataToRealmSync(task);
        break;
    }
  }

}
