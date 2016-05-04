package com.techmorphosis.grassroot.ui.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Gravity;

import com.techmorphosis.grassroot.R;
import com.techmorphosis.grassroot.ui.fragments.AlertDialogFragment;
import com.techmorphosis.grassroot.ui.fragments.Group_Homepage;
import com.techmorphosis.grassroot.ui.fragments.NavigationDrawerFragment;
import com.techmorphosis.grassroot.ui.fragments.RateUsFragment;
import com.techmorphosis.grassroot.ui.fragments.WelcomeFragment;
import com.techmorphosis.grassroot.utils.SettingPreference;
import com.techmorphosis.grassroot.utils.UtilClass;
import com.techmorphosis.grassroot.utils.listener.AlertDialogListener;

public class HomeScreen extends PortraitActivity implements NavigationDrawerFragment.NavigationDrawerCallbacks,WelcomeFragment.FragmentCallbacks,Group_Homepage.FragmentCallbacks{

    private DrawerLayout drawer;
   android.support.v4.app.Fragment fragment = null;
    private String openFragment;
    public  String TAG="HomeScreen";
    AlertDialogFragment alertDialogFragment;
    UtilClass utilClass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homescreen);
        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        utilClass = new UtilClass();
     //   hasUserRatedApp();
    }

    private void hasUserRatedApp()
    {

        if (!SettingPreference.getisRateus(getApplicationContext()))
        {
            //if user does not do rate us yet

            int rateuscount = SettingPreference.getIsRateuscounter(getApplicationContext());
            if (rateuscount == -1)
            {
                SettingPreference.setIsRateuscounter(HomeScreen.this, 1);
                Log.e(TAG, "counter is firsttime");

            }
            else
            {
                rateuscount++;
                SettingPreference.setIsRateuscounter(HomeScreen.this, rateuscount);

                if (rateuscount%15==0)
                {
                    //user attempt to open 15 time
                    Log.e(TAG,"IF rateuscount is " + rateuscount);
                   // Toast.makeText(getApplicationContext(),"rateuscount is " + rateuscount,Toast.LENGTH_LONG);
                    RateUsFragment rateUsFragment = new RateUsFragment();
                    rateUsFragment.show(getFragmentManager(),"RateUsFragment");

                }
                else
                {
                  //  Toast.makeText(getApplicationContext(),"rateuscount is " + rateuscount,Toast.LENGTH_LONG);
                    Log.e(TAG,"ELSE rateuscount is " + rateuscount);
                }

            }


        }
    /*    else
        {
            //user has already done rate us
            Toast.makeText(getApplicationContext(),"rate done ",Toast.LENGTH_LONG);
            Log.e(TAG, "RATE DONE " );
        }*/

    }


    @Override
    public void onNavigationDrawerItemSelected(int position) {

        if (drawer != null) drawer.closeDrawer(GravityCompat.START);
        fragment=null;

            switch(position)
            {
                case 0:
                  //Profile

                    if (SettingPreference.getisHasgroup(HomeScreen.this))
                    {
                        fragment = new Group_Homepage();
                        openFragment="Group_Homepage";
                    }
                    else
                    {
                        fragment = new WelcomeFragment();
                        openFragment="WelcomeFragment";

                    }


                break;

                case 1:
                    //Setting
                break;

                case 2:
                    //FAQ
                break;

                case 3:
                    //Logout

                    alertDialogFragment =utilClass.showAlerDialog(getFragmentManager(), getString(R.string.Logout_text), "Yes","No",true, new AlertDialogListener()
                    {
                        @Override
                        public void setRightButton()
                        {//no


                            alertDialogFragment.dismiss();
                        }

                        @Override
                        public void setLeftButton()
                        {
                            //Yes
                            SettingPreference.clearAll(getApplicationContext());
                            Intent open= new Intent(HomeScreen.this,StartActivity.class);
                            startActivity(open);
                            finish();

                            alertDialogFragment.dismiss();

                        }
                    });



                break;

                case 4:
                    //Share

                    //implicit Intent
                    Intent shareapp= new Intent("android.intent.action.SEND");
                    shareapp.setType("text/plain");
                    shareapp.setAction("android.intent.action.SEND");
                    shareapp.putExtra("android.intent.extra.TEXT", getString(R.string.share_app_text));
                    startActivity(Intent.createChooser(shareapp,"Share via.."));

                    break;

                case 5:
                    //Rate App

                    //implicit Intent
                    try {
                        Intent rateapp = new Intent("android.intent.action.VIEW", Uri.parse("market://details?id=com.techmorphosis.grassroot"));
                        startActivity(rateapp);
                    } catch (ActivityNotFoundException activitynotfoundexception) {
                        Intent rateapp2 = new Intent("android.intent.action.VIEW" ,Uri.parse("https://play.google.com/store/apps/details?id=com.techmorphosis.grassroot"));
                        startActivity(rateapp2);
                    }


                    break;



            }

        if (fragment != null)
        {

            android.support.v4.app.FragmentManager fragmentManager1 = getSupportFragmentManager();
            android.support.v4.app.FragmentTransaction ft1 = fragmentManager1.beginTransaction();
            ft1.replace(R.id.fragment_container, fragment);
            ft1.commit();

        } else
        {
            Log.e("Error", "Error in creating fragment");
        }
    }

    @Override
    public void menuClick() { // Getting data from fragment
        if (drawer != null) drawer.openDrawer(GravityCompat.START);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (SettingPreference.getPrefHasSaveClicked(this)){

            SettingPreference.setPrefHasSaveClicked(this, false);

            if (SettingPreference.getisHasgroup(HomeScreen.this))
            {
                Log.e("onResume", "Error in creating fragment");

                fragment = new Group_Homepage();
                openFragment="Group_Homepage";
            }
            else
            {
                fragment = new WelcomeFragment();
                openFragment="WelcomeFragment";

            }
            if (fragment != null)
            {

                android.support.v4.app.FragmentManager fragmentManager1 = getSupportFragmentManager();
                android.support.v4.app.FragmentTransaction ft1 = fragmentManager1.beginTransaction();
                ft1.replace(R.id.fragment_container, fragment);
                ft1.commit();

            } else
            {
                Log.e("Error", "Error in creating fragment");
            }

        }




    }
}
