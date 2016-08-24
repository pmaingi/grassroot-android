package org.grassroot.android.interfaces;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import org.grassroot.android.fragments.dialogs.NetworkErrorDialogFragment;

/**
 * Created by paballo on 2016/06/02.
 * todo : move this to being an actual interface
 */
public abstract class NetworkErrorDialogListener {

    private static final String TAG = NetworkErrorDialogFragment.class.getCanonicalName();

    public abstract void retryClicked();

    public void offlineClicked() {

    }

    public void checkNetworkSettingsClicked(Activity activity){
        Log.e(TAG,activity.getLocalClassName());
        activity.startActivityForResult(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS),
            NavigationConstants.NETWORK_SETTINGS_DIALOG);
    }

}
