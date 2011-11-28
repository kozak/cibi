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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.Toast;
import com.cibi.R;
import com.cibi.connection.CibiConnectionService;
import com.cibi.connection.ConnectionAction;
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

import java.io.Serializable;
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

    private Messenger mService;
    private boolean mIsBound;


    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(getApplicationContext(), ItemService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        doBindService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(serviceConnection);
            mBound = false;
        }
        doUnbindService();
    }

    private static final int MENU_CONNECT = Menu.FIRST;
    private static final int MENU_DISCONNECT = Menu.FIRST + 1;
    private static final int MENU_PING = Menu.FIRST + 2;


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_CONNECT, 0, R.string.menu_connect);
        menu.add(0, MENU_DISCONNECT, 0, R.string.menu_disconnect);
        menu.add(0, MENU_PING, 0, R.string.menu_ping);
        return result;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        try {
            switch (item.getItemId()) {
                case MENU_CONNECT:
                    sendMessage(ConnectionAction.ACTION_START, null);
                    return true;
                case MENU_DISCONNECT:
                    sendMessage(ConnectionAction.ACTION_STOP, null);
                    return true;
                case MENU_PING:
                    sendMessage(ConnectionAction.ACTION_KEEP_ALIVE, null);
                    return true;
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return super.onMenuItemSelected(featureId, item);
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
            final ArrayList<String> matches = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            Log.i(TAG, matches.toString());
            if (mText2SpeechReady) {
                if (!matches.isEmpty()) {
                    Map<String, Serializable> msgData = new HashMap<String, Serializable>() {{
                        put("", matches.get(0));
                    }};
                    try {
                        sendMessage(ConnectionAction.ACTION_MSG, msgData);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Can't send message " + matches.toString(), e);
                    }
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
            doUnbindService();
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


    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ConnectionAction.MSG:
                    String message = msg.getData().getString("msg");
                    System.out.println("Received from service: " + message);
                    mTextToSpeech.speak(message, TextToSpeech.QUEUE_ADD, null);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());


    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = new Messenger(service);

            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                sendMessage(ConnectionAction.ACTION_ADD_LISTENER, null);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }

        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
        }
    };

    private void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        bindService(new Intent(getApplicationContext(), CibiConnectionService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    private void sendMessage(ConnectionAction action, Map<String, Serializable> data) throws RemoteException {
        Message message = Message.obtain(null, action.toInteger());
        message.replyTo = mMessenger;
        if (data != null && !data.isEmpty()) {
            Bundle bundle = message.getData();
            for (Map.Entry<String, Serializable> entry : data.entrySet()) {
                bundle.putSerializable(entry.getKey(), entry.getValue());
            }
        }
        mService.send(message);
    }


    private void doUnbindService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            if (mService != null) {
                try {
                    sendMessage(ConnectionAction.ACTION_REMOVE_LISTENER, null);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service
                    // has crashed.
                }
            }

            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }
}
