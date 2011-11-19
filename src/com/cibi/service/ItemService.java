package com.cibi.service;

import java.util.*;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import com.cibi.item.ItemSearcher;
import com.cibi.item.ItemType;
import com.cibi.item.SearchResult;
import com.google.android.maps.GeoPoint;

public class ItemService extends Service {

    private static final String TAG = ItemService.class.getSimpleName();

    private Timer timer;

    private GeoPoint mGeoPoint;
    private ItemType[] mTypes;
    private int mLatSpan;
    private int mLngSpan;
    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public ItemService getService() {
            // Return this instance of LocalService so clients can call public methods
            return ItemService.this;
        }
    }


    private TimerTask updateTask = new TimerTask() {
        @Override
        public void run() {
            Log.i(TAG, "Timer task doing work");
            try {
                ItemSearcher searcher = new ItemSearcher(getApplicationContext());
                SearchResult newSearchResult = searcher.search(mGeoPoint, mLatSpan, mLngSpan, mTypes);
                if (newSearchResult != null && newSearchResult.getItems() != null) {
                    Log.i(TAG, "Retrieved " + newSearchResult.getItems());

                    synchronized (latestSearchResultLock) {
                        latestSearchResult = newSearchResult;
                    }

                    synchronized (listeners) {
                        for (ItemsChangedListener listener : listeners) {
                            try {
                                listener.onItemsChange();
                            } catch (Exception e) {
                                listeners.remove(listener);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to retrieve items", t);
            }
        }
    };

    private final Object latestSearchResultLock = new Object();

    private SearchResult latestSearchResult = new SearchResult();

    private final List<ItemsChangedListener> listeners = new ArrayList<ItemsChangedListener>();

    public SearchResult getLatestSearchResult() throws RemoteException {
        synchronized (latestSearchResultLock) {
            return latestSearchResult;
        }
    }

    public void addListener(ItemsChangedListener listener)
            throws RemoteException {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(ItemsChangedListener listener)
            throws RemoteException {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public void setParams(int lat, int lng, int latSpan, int lngSpan, String[] types) throws RemoteException {
        Log.i(TAG, String.format("Setting search params: lat=%d lng= %d latSpan=%d lngSpan=%d types=%s",
                lat, lng, latSpan, lngSpan, Arrays.toString(types)));
        mGeoPoint = new GeoPoint(lat, lng);
        mLatSpan = latSpan;
        mLngSpan = lngSpan;
        mTypes = new ItemType[types.length];
        for (int i = 0; i < types.length; i++) {
            mTypes[i] = ItemType.valueOf(types[i]);
        }
        if (timer == null) {
            timer = new Timer("Item collector timer");
            timer.schedule(updateTask, 0L, 10 * 1000L);
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Bound by intent " + intent);
        return mBinder;

    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Creating ItemService");


    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Destroying ItemService");
        listeners.clear();
        timer.cancel();
        timer = null;
    }
}