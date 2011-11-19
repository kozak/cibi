package com.cibi;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ZoomButtonsController;
import com.cibi.item.GeoItem;
import com.cibi.item.ItemType;
import com.cibi.item.SearchResult;
import com.cibi.overlay.CustomItemizedOverlay;
import com.cibi.service.ItemApi;
import com.cibi.service.ItemListener;
import com.cibi.service.ItemService;
import com.cibi.utils.LocationUtils;
import com.cibi.view.ZoomEventsMapView;
import com.google.android.maps.*;

import java.io.IOException;
import java.util.*;

public class OverviewActivity extends MapActivity {
    private static final String TAG = OverviewActivity.class.getName();

    private ZoomEventsMapView mView;
    private Location mLastLocation;

    private Set<ItemType> enabledOverlays = new HashSet<ItemType>(Arrays.asList(ItemType.values()));


    private Map<ItemType, CustomItemizedOverlay> overlayMap = new HashMap<ItemType, CustomItemizedOverlay>(
            ItemType.values().length);


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.overview);
        mView = (ZoomEventsMapView) findViewById(R.id.mapview);
        Drawable mDrawable = this.getResources().getDrawable(R.drawable.androidmarker);
        handler = new Handler(); // handler will be bound to the current thread (UI)
        Intent serviceIntent = new Intent(ItemService.class.getName());
        // start the service explicitly.
        // otherwise it will only run while the IPC connection is up.
        getApplicationContext().startService(serviceIntent);
        if (!getApplicationContext().bindService(serviceIntent, serviceConnection, 0)) {
            Log.e(TAG, "bindService failed");
        }

        for (ItemType value : ItemType.values()) {
            overlayMap.put(value, new CustomItemizedOverlay(mDrawable, this));
        }

        mView.setOnPanListener(new ZoomEventsMapView.OnPanAndZoomListener() {
            public void onPan() {
                updateSearchParams();
            }

            public void onZoom() {
                updateSearchParams();
            }
        });

        handleButton(R.id.add_police, ItemType.POLICE);
        handleButton(R.id.add_parking, ItemType.PARKING_SPOT);
        handleButton(R.id.add_traffic, ItemType.TRAFFIC_JAM);
        handleCheckbox(R.id.police_enabled, ItemType.POLICE);
        handleCheckbox(R.id.parking_enabled, ItemType.PARKING_SPOT);
        handleCheckbox(R.id.traffic_enabled, ItemType.TRAFFIC_JAM);


        MyLocation myLocation = new MyLocation();
        myLocation.getLocation(this, locationResult);
    }

    private void handleCheckbox(final int id, final ItemType type) {
        final CheckBox checkBox = (CheckBox) findViewById(id);
        checkBox.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (checkBox.isChecked()) {
                    enabledOverlays.add(type);
                } else {
                    enabledOverlays.remove(type);
                }
                updateSearchParams();
                updateView();
            }
        });

    }

    private void handleButton(final int id, final ItemType type) {
        Button button = (Button) findViewById(id);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (mLastLocation != null) {
                    GeoItem.create(getApplicationContext(),
                            LocationUtils.toGeoPoint(mLastLocation),
                            type);

                }
            }
        });
    }

    public MyLocation.LocationResult locationResult = new MyLocation.LocationResult() {
        public void gotLocation(final Location location) {
            Log.i(TAG, "Got location " + location);
            mLastLocation = location;
            updateSearchParams();
        }
    };

    private void updateSearchParams() {
        if (mLastLocation != null) {
            try {
                api.setParams((int) (mLastLocation.getLatitude() * GeoItem.TO_GEOPOINT),
                        (int) (mLastLocation.getLongitude() * GeoItem.TO_GEOPOINT),
                        mView.getLatitudeSpan(),
                        mView.getLongitudeSpan(),
                        ItemType.getEnabled(enabledOverlays));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            mView.getController().animateTo(
                    LocationUtils.toGeoPoint(mLastLocation)
            );
        }
    }

    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }


    private ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "Service connection established");

            // that's how we get the client side of the IPC connection
            api = ItemApi.Stub.asInterface(service);
            try {
                api.addListener(collectorListener);
//                type=3&lat=50418990&lng=18922120&latSpan=10&lngSpan=10
                api.setParams(50418990, 18922120, 1000000, 1000000, new String[]{ItemType.PHOTO_RADAR.toString()});
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to add listener", e);
            }

            updateView();
        }

        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "Service connection closed");
        }
    };

    private ItemApi api;

    private Handler handler;

    private ItemListener.Stub collectorListener = new ItemListener.Stub() {

        public void onItemsChange() throws RemoteException {
            updateView();
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            api.removeListener(collectorListener);
            unbindService(serviceConnection);
        } catch (Throwable t) {
            // catch any issues, typical for destroy routines
            // even if we failed to destroy something, we need to continue destroying
            Log.w(TAG, "Failed to unbind from the service", t);
        }

        Log.i(TAG, "Activity destroyed");
    }

    private void updateView() {
        // doing this in a Handler allows to call this method safely from any thread
        // see Handler docs for more info
        final Context context = this;
        mView.postInvalidate();

        handler.post(new Runnable() {
            public void run() {
                try {
                    Log.i(TAG, String.format("Enabled overlays = %s", enabledOverlays.toString()));
                    SearchResult result = api.getLatestSearchResult();
                    //add results
                    mView.setBuiltInZoomControls(true);
                    mView.getOverlays().clear();

                    for (CustomItemizedOverlay overlay : overlayMap.values()) {
                        overlay.clear();
                    }

                    List<GeoItem> items = result.getItems();
//                    Log.i(TAG, "items: " + items);
                    for (GeoItem item : items) {
                        ItemType type = item.getType();
                        CustomItemizedOverlay overlay = overlayMap.get(type);
                        overlay.addOverlay(
                                new OverlayItem(item.getPoint(),
                                        type.toString(),
                                        type.toString()));
                    }
                    for (Map.Entry<ItemType, CustomItemizedOverlay> overlay : overlayMap.entrySet()) {
                        Log.i(TAG, String.format("Overlay type=%s size=%s",
                                overlay.getKey(),
                                overlay.getValue().size()));
                        if (overlay.getValue().size() > 0 && enabledOverlays.contains(overlay.getKey())) {
                            mView.getOverlays().add(overlay.getValue());
                        }
                    }

                } catch (Throwable t) {
                    Log.e(TAG, "Error while updating the UI with tweets", t);
                }
            }
        });
    }
}
