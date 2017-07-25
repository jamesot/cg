
package com.cadre.ocr;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;

import java.util.HashMap;


/**
 * Created by stephineosoro on 07/06/16.
 */
public class MyShortcuts {

    public static String baseURL(){
        return "http://41.204.186.107:8000/";
    }

    public static void set(String username, String password, Context context) {
        setDefaults("username", username, context);
        setDefaults("password", password, context);
    }


    public static HashMap<String, String> AunthenticationHeaders(Context context) {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        String creds = String.format("%s:%s", "web@oneshoppoint.com", "spr0iPpQAiwS8u");
        String auth = "Basic " + Base64.encodeToString(creds.getBytes(), Base64.DEFAULT);
        headers.put("Authorization", auth);
        return headers;
    }
    public static HashMap<String, String> AunthenticationHeadersAdmin(Context context) {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        String creds = String.format("%s:%s", getDefaults("username", context), getDefaults("password", context));
        String auth = "Basic " + Base64.encodeToString(creds.getBytes(), Base64.DEFAULT);
        headers.put("Authorization", auth);
        return headers;
    }


    public static void setDefaults(String key, String value, Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, value);
        editor.commit();
    }

    public static String getDefaults(String key, Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getString(key, null);
    }


    public static void showToast(String text, Context context) {

        /*Toast toast = Toast.makeText(context, text, Toast.LENGTH_SHORT);
        toast.show();*/

        Toast ToastMessage = Toast.makeText(context,text, Toast.LENGTH_SHORT);
        View toastView = ToastMessage.getView();
//        toastView.setBackgroundResource(R.drawable.toast_background_color);
        ToastMessage.show();
    }

    public static boolean hasInternetConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnectedOrConnecting()) {
                return true;
            }
        }
        return false;
    }

    public static Boolean checkDefaults(String key, Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.contains(key);
    }

    public static void Delete(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.commit();
    }

}
