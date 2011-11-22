package com.cibi.service;

import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import com.cibi.utils.MyLocation;
import com.cibi.item.ItemSearcher;
import com.cibi.item.ItemType;
import com.cibi.item.SearchResult;
import com.cibi.utils.LocationUtils;
import com.google.android.maps.GeoPoint;

public class ItemService extends Service {

    private static final String TAG = ItemService.class.getSimpleName();

    private GeoPoint mGeoPoint;
    private ItemType[] mTypes;
    private int mLatSpan;
    private int mLngSpan;
    private final IBinder mBinder = new LocalBinder();

    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2);

    public class LocalBinder extends Binder {
        public ItemService getService() {
            // Return this instance of LocalService so clients can call public methods
            return ItemService.this;
        }
    }


    private Runnable updateTask = new Runnable() {
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

    public GeoPoint getLatestGeoPoint() {
        return mGeoPoint;
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

    public void setParams(int latSpan, int lngSpan, String[] types) throws RemoteException {
        Log.i(TAG, String.format("Setting search params: latSpan=%d lngSpan=%d types=%s", latSpan, lngSpan, Arrays.toString(types)));
        mLatSpan = latSpan;
        mLngSpan = lngSpan;
        mTypes = new ItemType[types.length];
        for (int i = 0; i < types.length; i++) {
            mTypes[i] = ItemType.valueOf(types[i]);
        }
        executor.execute(updateTask);
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

        MyLocation myLocation = new MyLocation();

        myLocation.getLocation(this, new MyLocation.LocationResult() {
            public void gotLocation(final Location location) {
                Log.i(TAG, "Got location " + location);
                if (location != null) {
                    mGeoPoint = LocationUtils.toGeoPoint(location);
                    executor.execute(updateTask);
                }
            }
        });


    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Destroying ItemService");
        listeners.clear();
        executor.shutdown();
    }
}