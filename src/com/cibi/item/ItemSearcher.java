package com.cibi.item;

import android.content.Context;
import android.os.AsyncTask;
import com.cibi.R;
import com.cibi.utils.UrlUtils;
import com.google.android.maps.GeoPoint;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.util.Log;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;


/**
 * @author morswin
 */
public final class ItemSearcher {

    private static final String TAG = ItemSearcher.class.getName();

    Context context;


    public ItemSearcher(Context context) {
        this.context = context;
    }


    protected String getItemsUrl(final String url,
                                 final GeoPoint location,
                                 final int latSpan,
                                 final int lngSpan,
                                 final ItemType... types) {

        final StringBuilder b = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            b.append(types[i].ordinal());
            if (i + 1 < types.length) {
                b.append(",");
            }
        }
       Log.d(TAG, String.format("Types are: %s", b.toString()));

        Map<String, String> params = new HashMap<String, String>() {{
            put("lat", String.valueOf(location.getLatitudeE6()));
            put("lng", String.valueOf(location.getLongitudeE6()));
            put("latSpan", String.valueOf(latSpan));
            put("lngSpan", String.valueOf(lngSpan));
            put("type", b.toString());
        }};

        return UrlUtils.getParams(url, params);
    }




    public SearchResult search(GeoPoint location,
                               int latSpan,
                               int lngSpan,
                               ItemType... types) throws ClientProtocolException, IOException {
        HttpClient httpClient = new DefaultHttpClient();
        String url = context.getResources().getText(R.string.get_url).toString();
        String getUrl = getItemsUrl(url, location, latSpan, lngSpan, types);
        Log.i(TAG, getUrl);
        HttpGet getMethod = new HttpGet(getUrl);
        HttpResponse response = httpClient.execute(getMethod);
        try {
            SearchResult result = new SearchResult();
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
            StringBuilder builder = new StringBuilder();
            for (String line = null; (line = reader.readLine()) != null; ) {
                builder.append(line).append("\n");
            }
            String content = builder.toString();
            Log.i(TAG, "Response is: " + content);
            JSONTokener tokener = new JSONTokener(content);
            JSONObject json = new JSONObject(tokener);
            boolean success = json.getBoolean("success");
            if (success) {
                JSONArray array = json.getJSONArray("items");
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
//                    Log.i(TAG, "Item: " + item);
                    ItemType type = ItemType.fromOrdinal(item.getInt("type"));
                    int lat = item.getInt("lat");
                    int lng = item.getInt("lng");
//                    Log.i(TAG, String.format("type=%s lat=%d lng=%d", type, lat, lng));
                    result.addGeoItem(new GeoItem(type, lat, lng));
                }
            }
            reader.close();
            return result;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }




}

