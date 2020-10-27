package com.sample.huawei.nearby.utils;

import android.content.Context;
import android.location.LocationManager;

/**
 * Location Check Util
 *
 * @since 2020-06-04
 */
public final class LocationCheckUtil {
    private LocationCheckUtil() {
    }

    /**
     * Is Gps Enabled
     *
     * @param context Context
     * @return true:Gps is enabled
     */
    public static boolean isLocationEnabled(Context context) {
        Object object = context.getSystemService(Context.LOCATION_SERVICE);
        if (!(object instanceof LocationManager)) {
            return false;
        }
        LocationManager locationManager = (LocationManager) object;
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }
}
