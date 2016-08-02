package org.grassroot.android.models;

import android.text.TextUtils;

import org.grassroot.android.utils.NetworkUtils;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class PreferenceObject extends RealmObject {
  @PrimaryKey
  private int id = 0;

  private boolean isLoggedIn;
  private boolean hasRatedUs;
  private boolean hasGroups;
  private boolean hasGcmRegistered;
  private boolean mustRefresh;
  private String userName;
  private String mobileNumber;
  private String token;

  private long lastTimeSyncPerformed;
  private long lastTimeGroupsFetched;
  private long lastTimeUpcomingTasksFetched;

  public long getLastTimeNotificationsFetched() {
    return lastTimeNotificationsFetched;
  }

  public void setLastTimeNotificationsFetched(long lastTimeNotificationsFetched) {
    this.lastTimeNotificationsFetched = lastTimeNotificationsFetched;
  }

  private long lastTimeNotificationsFetched;

  private String onlineStatus;
  private boolean showOnlineOfflinePicker = true;

  private String alert;
  private String languagePreference;
  private int notificationCounter;

  public boolean isLoggedIn() {
    return isLoggedIn;
  }

  public void setLoggedIn(boolean loggedIn) {
    isLoggedIn = loggedIn;
  }

  public boolean isHasRatedUs() {
    return hasRatedUs;
  }

  public void setHasRatedUs(boolean hasRatedUs) {
    this.hasRatedUs = hasRatedUs;
  }

  public boolean isHasGroups() {
    return hasGroups;
  }

  public void setHasGroups(boolean hasGroups) {
    this.hasGroups = hasGroups;
  }

  public boolean isHasGcmRegistered() {
    return hasGcmRegistered;
  }

  public void setHasGcmRegistered(boolean hasGcmRegistered) {
    this.hasGcmRegistered = hasGcmRegistered;
  }

  public boolean isMustRefresh() {
    return mustRefresh;
  }

  public void setMustRefresh(boolean mustRefresh) {
    this.mustRefresh = mustRefresh;
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public String getMobileNumber() {
    return mobileNumber;
  }

  public void setMobileNumber(String mobileNumber) {
    this.mobileNumber = mobileNumber;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getLanguagePreference() {
    return languagePreference;
  }

  public void setLanguagePreference(String languagePreference) {
    this.languagePreference = languagePreference;
  }

  public int getNotificationCounter() {
    return notificationCounter;
  }

  public void setNotificationCounter(int notificationCounter) {
    this.notificationCounter = notificationCounter;
  }

  public void setLastTimeSyncPerformed(long lastTimeSyncPerformed) { this.lastTimeSyncPerformed = lastTimeSyncPerformed; }

  public long getLastTimeSyncPerformed() { return lastTimeSyncPerformed; }

  public void setLastTimeGroupsFetched(long lastTimeGroupsFetched) { this.lastTimeGroupsFetched = lastTimeGroupsFetched; }

  public long getLastTimeGroupsFetched() { return lastTimeGroupsFetched; }

  public void setLastTimeUpcomingTasksFetched(long lastTimeUpcomingTasksFetched) { this.lastTimeUpcomingTasksFetched = lastTimeUpcomingTasksFetched; }

  public long getLastTimeUpcomingTasksFetched() { return lastTimeUpcomingTasksFetched; }

  public String getAlert() {
    return alert;
  }

  public void setAlert(String alert) {
    this.alert = alert;
  }

  public void setOnlineStatus(String onlineStatus) { this.onlineStatus = onlineStatus; }

  public String getOnlineStatus() {
    if (TextUtils.isEmpty(onlineStatus)) {
      onlineStatus = NetworkUtils.ONLINE_DEFAULT;
    }
    return onlineStatus;
  }

  public void setShowOnlineOfflinePicker(boolean showOnlineOfflinePicker) { this.showOnlineOfflinePicker = showOnlineOfflinePicker; }

  public boolean isShowOnlineOfflinePicker() { return showOnlineOfflinePicker; }

}
