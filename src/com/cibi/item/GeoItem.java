package com.cibi.item;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import com.cibi.R;
import com.cibi.utils.UrlUtils;
import com.google.android.maps.GeoPoint;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author morswin
 */
public class GeoItem implements Parcelable {
    public static double TO_GEOPOINT = 1E6;
    GeoPoint point;
    ItemType type;

    public static final Creator<GeoItem> CREATOR = new Creator<GeoItem>() {
        public GeoItem createFromParcel(Parcel source) {
            return new GeoItem(source);
        }

        public GeoItem[] newArray(int size) {
            return new GeoItem[size];
        }
    };

    public GeoItem(ItemType type, int lat, int lng) {
        point = new GeoPoint(lat, lng);
        this.type = type;
    }

    public GeoItem(ItemType type, double lat, double lng) {
        point = new GeoPoint((int) (lat * TO_GEOPOINT), (int) (lng * TO_GEOPOINT));
        this.type = type;
    }

    public GeoItem(Parcel source) {
        String name = source.readString();
        if (name != null) {
            type = ItemType.valueOf(name);
        }
        int lat = source.readInt();
        int lng = source.readInt();
        point = new GeoPoint(lat, lng);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(type.toString());
        parcel.writeInt(point.getLatitudeE6());
        parcel.writeInt(point.getLongitudeE6());
    }


    public GeoPoint getPoint() {
        return point;
    }


    public ItemType getType() {
        return type;
    }

    public void setType(ItemType type) {
        this.type = type;
    }


    public static void create(Context context,
                              GeoPoint location,
                              ItemType type) {
        new Thread(new AddItem(context, location, type)).start();
    }

    private static class AddItem implements Runnable {
        String getUrl;

        public AddItem(Context context, GeoPoint location, ItemType type) {
            String url = context.getResources().getText(R.string.add_url).toString();
            getUrl = buildUrl(url, location, type);
        }

        private String buildUrl(String url, final GeoPoint location, final ItemType type) {
            Map<String, String> params = new HashMap<String, String>() {{
                put("lat", String.valueOf(location.getLatitudeE6()));
                put("lng", String.valueOf(location.getLongitudeE6()));
                put("type", String.valueOf(type.ordinal()));
            }};
            return UrlUtils.getParams(url, params);
        }


        public void run() {
            HttpClient httpClient = new DefaultHttpClient();
            Log.i(AddItem.class.getName(), getUrl);
            HttpGet getMethod = new HttpGet(getUrl);
            try {
                httpClient.execute(getMethod);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public String toString() {
        return "GeoItem{" +
                "point=" + point +
                ", type=" + type +
                '}';
    }
}
