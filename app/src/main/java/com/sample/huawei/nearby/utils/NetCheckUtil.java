package com.sample.huawei.nearby.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class NetCheckUtil {
    private static final String TAG = "NetCheckUtil";

    private NetCheckUtil() {
    }

    public static boolean isNetworkAvailable(Context context) {
        boolean mobileConnection = isMobileConnection(context);
        boolean wifiConnection = isWifiConnection(context);
        if (!mobileConnection && !wifiConnection) {
            Log.i(TAG, "No network available");
            return false;
        }
        return true;
    }

    public static boolean isMobileConnection(Context context) {
        Object object = context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (!(object instanceof ConnectivityManager)) {
            return false;
        }
        ConnectivityManager manager = (ConnectivityManager) object;
        NetworkInfo networkInfo = manager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        if (networkInfo != null && networkInfo.isConnected()) {
            return true;
        }
        return false;
    }

    public static boolean isWifiConnection(Context context) {
        Object object = context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (!(object instanceof ConnectivityManager)) {
            return false;
        }
        ConnectivityManager manager = (ConnectivityManager) object;
        NetworkInfo networkInfo = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (networkInfo != null && networkInfo.isConnected()) {
            return true;
        }
        return false;
    }
}
