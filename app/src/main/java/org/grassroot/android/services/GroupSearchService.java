package org.grassroot.android.services;

import org.grassroot.android.models.GroupSearchResponse;
import org.grassroot.android.models.PublicGroupModel;
import org.grassroot.android.models.exceptions.ApiCallException;
import org.grassroot.android.utils.ErrorUtils;
import org.grassroot.android.utils.NetworkUtils;
import org.grassroot.android.utils.RealmUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created by luke on 2016/08/11.
 */
public class GroupSearchService {

	public static final String TAG = GroupSearchService.class.getSimpleName();

	private static GroupSearchService instance = null;

	public List<PublicGroupModel> foundByGroupName;
	public List<PublicGroupModel> foundByTaskName;

	protected GroupSearchService() {
	}

	public static GroupSearchService getInstance() {
		GroupSearchService methodInstance = instance;
		if (methodInstance == null) {
			synchronized (GroupSearchService.class) {
				methodInstance = instance;
				if (methodInstance == null) {
					instance = methodInstance = new GroupSearchService();
				}
			}
		}
		return methodInstance;
	}

	public Observable<String> searchForGroups(final String searchTerm, boolean searchNamesAndTerms) {
		return Observable.create(new Observable.OnSubscribe<String>() {
			@Override
			public void call(Subscriber<? super String> subscriber) {
				try {
					final String mobileNumber = RealmUtils.loadPreferencesFromDB().getMobileNumber();
					final String code = RealmUtils.loadPreferencesFromDB().getToken();
					final String trimmedTerm = searchTerm.trim();
					Response<GroupSearchResponse> searchResponse = GrassrootRestService.getInstance()
						.getApi().search(mobileNumber, code, trimmedTerm).execute();
					if (searchResponse.isSuccessful()) {
						foundByGroupName = new ArrayList<>(searchResponse.body().getGroups());
						subscriber.onNext(NetworkUtils.FETCHED_SERVER);
						subscriber.onCompleted();
					} else {
						throw new ApiCallException(NetworkUtils.SERVER_ERROR,
							ErrorUtils.getRestMessage(searchResponse.errorBody()));
					}
				} catch (IOException e) {
					throw new ApiCallException(NetworkUtils.CONNECT_ERROR);
				}
			}
		}).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
	}

}
