package com.cibi.activity;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.*;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.Toast;
import com.cibi.R;
import com.cibi.item.GeoItem;
import com.cibi.item.ItemType;
import com.cibi.item.SearchResult;
import com.cibi.overlay.CustomItemizedOverlay;
import com.cibi.service.ItemsChangedListener;
import com.cibi.service.ItemService;
import com.cibi.utils.Communication;
import com.cibi.utils.LocationUtils;
import com.cibi.view.ZoomEventsMapView;
import com.google.android.maps.*;

import java.util.*;

public class OverviewActivity extends MapActivity implements TextToSpeech.OnInitListener {
    private static final String TAG = OverviewActivity.class.getName();

    private ZoomEventsMapView mView;

    private Set<ItemType> enabledOverlays = new HashSet<ItemType>(Arrays.asList(ItemType.values()));


    private Map<ItemType, CustomItemizedOverlay> overlayMap = new HashMap<ItemType, CustomItemizedOverlay>(
            ItemType.values().length);

    boolean mBound = false;
    private Vibrator mVibrator;
    private ProgressDialog mProgressDialog;
    private MyLocationOverlay mMyLocationOverlay;
    private TextToSpeech mTextToSpeech;
    private boolean mText2SpeechReady;


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

        mMyLocationOverlay = new MyLocationOverlay(this, mView);
        mMyLocationOverlay.enableCompass();
        mMyLocationOverlay.enableMyLocation();


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


        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, Communication.T2S_CHECK_CODE);

//        mProgressDialog = new ProgressDialog(this);
//        mProgressDialog.setCancelable(false);
//        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
//        mProgressDialog.setMessage("Łączę z GPS... Mobilki, mobilki jak mnie słychać?");
//        mProgressDialog.show();
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
            }
        });

    }

    private void handleButton(final int id, final ItemType type) {
        ImageButton button = (ImageButton) findViewById(id);
        final Context c = this;
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                GeoPoint point = api.getLatestGeoPoint();
                if (point != null) {
                    GeoItem.create(getApplicationContext(), point, type);
                    updateSearchParams();
                    mVibrator.vibrate(50L);
                    Toast toast = Toast.makeText(c, R.string.toast_thanks, Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.TOP, 0, 75);
                    toast.show();
                }
            }
        });
    }

    public void onSpeakButtonClick(View target) {
        Communication.inputVoiceCommand(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Communication.VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            ArrayList<String> matches = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            Log.i(TAG, matches.toString());
            if (mText2SpeechReady) {
                for (String match : matches) {
                    mTextToSpeech.speak(match, TextToSpeech.QUEUE_ADD, null);
                }
            }
        } else if (requestCode == Communication.T2S_CHECK_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // success, create the TTS instance
                mTextToSpeech = new TextToSpeech(this, this);
            } else {
                // missing data, install it
                Intent installIntent = new Intent();
                installIntent.setAction(
                        TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    private void updateSearchParams() {
        if (mBound) {
            try {
                api.setParams(mView.getLatitudeSpan(), mView.getLongitudeSpan(), ItemType.getEnabled(enabledOverlays));
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
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to add listener", e);
            }
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
        if (mProgressDialog.isShowing()) {
            mProgressDialog.hide();
        }
        mView.postInvalidate();

        handler.post(new Runnable() {
            public void run() {
                try {
                    Log.i(TAG, String.format("Enabled overlays = %s", enabledOverlays.toString()));
                    SearchResult result = api.getLatestSearchResult();
                    //add results
                    mView.setBuiltInZoomControls(true);
                    List<Overlay> overlays = mView.getOverlays();
                    overlays.clear();
                    overlays.add(mMyLocationOverlay);

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
                            overlays.add(overlay.getValue());
                        }
                    }
                    mView.getController().animateTo(api.getLatestGeoPoint());

                } catch (Throwable t) {
                    Log.e(TAG, "Error while updating the UI with tweets", t);
                }
            }
        });
    }

    public void onInit(int i) {
        mText2SpeechReady = true;
    }
}
