package com.techmorphosis.grassroot.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by user on 09-Oct-15.
 */
public class SettingPreffrence {

    // Shared Preferences
    SharedPreferences pref;


    // Shared pref file name

    private static final String PREF_NAME = "Grassroot";


    // All Shared Preferences Keys
    private static final String IS_LOGIN = "IsLoggedIn";
    private static final String IS_RATEUS = "IsRateus";
    private static final String IS_RATEUSCOUNTER = "IsRateuscounter";

    private static final String PREF_USER = "Pref_user_id";
    private static final String PREF_USER_name = "Pref_user_name";
    private static final String PREF_USER_mobile_number = "Pref_user_mobile_number";
    private static final String PREF_USER_token = "Pref_user_token";
    private static final String PREF_TypeID = "Pref_type_id";
    private static final String PREF_Type = "Pref_type";


    private static final String PREF_OTP = "Pref_otp";


    public static SharedPreferences getPref(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Create login session
     */

    public static boolean clearAll(Context context)
    {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().commit();
       /* context.clear();
        context.apply();*/
    }


    public static void setisLoggedIn(Context context, Boolean value) {
        getPref(context).edit().putBoolean(IS_LOGIN, value).apply();

    }

    public static void setisRateus(Context context, Boolean value) {
        getPref(context).edit().putBoolean(IS_RATEUS, value).apply();

    }

    public static boolean getisLoggedIn(Context context) {
        return getPref(context).getBoolean(IS_LOGIN, false);

    }


    public static boolean getisRateus(Context context) {
        return getPref(context).getBoolean(IS_RATEUS, false);

    }

    public static void setuserid(Context context, String value) {

        getPref(context).edit().putString(PREF_USER, value).apply();
    }


    public static void setIsRateuscounter(Context context, Integer value) {

        getPref(context).edit().putInt(IS_RATEUSCOUNTER, value).apply();
    }

    public static int getIsRateuscounter(Context context) {
        return getPref(context).getInt(IS_RATEUSCOUNTER, -1);
    }


    public static String getuserid(Context context) {
        return getPref(context).getString(PREF_USER, "");
    }


    public static void setuser_name(Context context, String value) {
        getPref(context).edit().putString(PREF_USER_name, value).apply();
    }

    public static String getuser_name(Context context) {
        return getPref(context).getString(PREF_USER_name, "");
    }

    public static void setuser_mobilenumber(Context context, String value) {
        getPref(context).edit().putString(PREF_USER_mobile_number, value).apply();
    }

    public static String getuser_mobilenumber(Context context) {
        return getPref(context).getString(PREF_USER_mobile_number, "");
    }

    public static void setuser_token(Context context, String value) {
        getPref(context).edit().putString(PREF_USER_token, value).apply();
    }

    public static String getuser_token(Context context) {
        return getPref(context).getString(PREF_USER_token, "");
    }

    public static void setPREF_TypeID(Context context, String value) {
        getPref(context).edit().putString(PREF_TypeID, value).apply();
    }

    public static String getPREF_TypeID(Context context) {
        return getPref(context).getString(PREF_TypeID, "");
    }

    public static void setPREF_Type(Context context, String value) {
        getPref(context).edit().putString(PREF_Type, value).apply();
    }

    public static String getPREF_Type(Context context) {
        return getPref(context).getString(PREF_Type, "");
    }


}
