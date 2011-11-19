package com.cibi.utils;

import android.location.Location;
import com.google.android.maps.GeoPoint;

/**
 * @author morswin
 */
public class LocationUtils {

    public static GeoPoint toGeoPoint(Location location) {
        return new GeoPoint(
                (int)(location.getLatitude() * 1E6),
                (int)(location.getLongitude()* 1E6)
        );
    }

}
