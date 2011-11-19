package com.cibi;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.*;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.Toast;
import com.cibi.item.GeoItem;
import com.cibi.item.ItemType;
import com.cibi.item.SearchResult;
import com.cibi.overlay.CustomItemizedOverlay;
import com.cibi.service.ItemsChangedListener;
import com.cibi.service.ItemService;
import com.cibi.utils.LocationUtils;
import com.cibi.view.ZoomEventsMapView;
import com.google.android.maps.*;

import java.util.*;

public class OverviewActivity extends MapActivity {
    private static final String TAG = OverviewActivity.class.getName();

    private ZoomEventsMapView mView;
    private Location mLastLocation;

    private Set<ItemType> enabledOverlays = new HashSet<ItemType>(Arrays.asList(ItemType.values()));


    private Map<ItemType, CustomItemizedOverlay> overlayMap = new HashMap<ItemType, CustomItemizedOverlay>(
            ItemType.values().length);

    boolean mBound = false;
    private Vibrator mVibrator;
    private ProgressDialog mProgressDialog;


    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(getApplicationContext(), ItemService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(serviceConnection);
            mBound = false;
        }
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.overview);
        mView = (ZoomEventsMapView) findViewById(R.id.mapview);

        handler = new Handler(); // handler will be bound to the current thread (UI)

        Intent serviceIntent = new Intent(getApplicationContext(), ItemService.class);
        getApplicationContext().startService(serviceIntent);
        if (!getApplicationContext().bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)) {
            Log.e(TAG, "bindService failed");
        }

        for (ItemType value : ItemType.values()) {
            Drawable drawable = this.getResources().getDrawable(value.getIcon());
            overlayMap.put(value, new CustomItemizedOverlay(drawable, this));
        }


        mView.setOnPanListener(new ZoomEventsMapView.OnPanAndZoomListener() {
            public void onPan() {
                updateSearchParams();
            }

            public void onZoom() {
                updateSearchParams();
            }
        });
        mView.getController().setZoom(15);

        handleButton(R.id.add_police, ItemType.POLICE);
        handleButton(R.id.add_parking, ItemType.PARKING_SPOT);
        handleButton(R.id.add_traffic, ItemType.TRAFFIC_JAM);
        handleCheckbox(R.id.police_enabled, ItemType.POLICE);
        handleCheckbox(R.id.parking_enabled, ItemType.PARKING_SPOT);
        handleCheckbox(R.id.traffic_enabled, ItemType.TRAFFIC_JAM);

        mVibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setMessage("Łączę z GPS... Mobilki, mobilki jak mnie słychać?");


        MyLocation myLocation = new MyLocation();

        myLocation.getLocation(this, new MyLocation.LocationResult() {
            public void gotLocation(final Location location) {
                Log.i(TAG, "Got location " + location);
                mLastLocation = location;
                CustomItemizedOverlay overlay = overlayMap.get(ItemType.ME);
                if (mLastLocation != null) {
                    overlay.clear();
                    overlay.addOverlay(new OverlayItem(LocationUtils.toGeoPoint(mLastLocation),
                            ItemType.ME.toString(),
                            ItemType.ME.toString()));
                    mView.postInvalidate();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            mProgressDialog.hide();
                        }
                    });
                }
                updateSearchParams();
            }
        });

        mProgressDialog.show();
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
        ImageButton button = (ImageButton) findViewById(id);
        final Context c = this;
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (mLastLocation != null) {
                    GeoItem.create(getApplicationContext(),
                            LocationUtils.toGeoPoint(mLastLocation),
                            type);
                    mVibrator.vibrate(50L);
                    Toast toast = Toast.makeText(c, R.string.toast_thanks, Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.TOP, 0, 75);
                    toast.show();
                }
            }
        });
    }

    private void updateSearchParams() {
        if (mLastLocation != null && mBound) {
            mView.getController().animateTo(
                    LocationUtils.toGeoPoint(mLastLocation)
            );
            try {
                api.setParams((int) (mLastLocation.getLatitude() * GeoItem.TO_GEOPOINT),
                        (int) (mLastLocation.getLongitude() * GeoItem.TO_GEOPOINT),
                        mView.getLatitudeSpan(),
                        mView.getLongitudeSpan(),
                        ItemType.getEnabled(enabledOverlays));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
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
            api = ((ItemService.LocalBinder) service).getService();
            mBound = true;
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
            mBound = false;
        }
    };

    private ItemService api;

    private Handler handler;

    private ItemsChangedListener collectorListener = new ItemsChangedListener() {
        public void onItemsChange() {
            updateView();
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            api.removeListener(collectorListener);
            if (mBound) {
                unbindService(serviceConnection);
            }
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

                    for (Map.Entry<ItemType, CustomItemizedOverlay> entry: overlayMap.entrySet()){
                        if (!entry.getKey().equals(ItemType.ME)) {
                            entry.getValue().clear();
                        }
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
