package com.cibi.activity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.*;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import com.cibi.R;
import android.util.Log;
import com.cibi.connection.CibiConnectionService;
import com.cibi.connection.ConnectionAction;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


public class TestConnection extends Activity {
    public static final String TAG = "TestConnection";


    Messenger mService;
    boolean mIsBound;
    EditText mText;


    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ConnectionAction.MSG:
                    System.out.println("Received from service: " + msg.getData().getString("msg"));
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


    private final OnClickListener mClicked = new OnClickListener() {
        public void onClick(View v) {
            if (mService != null && mIsBound) {
                try {
                    switch (v.getId()) {
                        case R.id.start:
                            sendMessage(ConnectionAction.ACTION_START, null);
                            break;
                        case R.id.stop:
                            sendMessage(ConnectionAction.ACTION_STOP, null);
                            break;
                        case R.id.ping:
                            sendMessage(ConnectionAction.ACTION_KEEP_ALIVE, null);
                            break;
                        case R.id.send:
                            Map<String, Serializable> data = new HashMap<String, Serializable>(){{
                                put("msg", mText.getText().toString());
                            }};
                            mText.getText().clear();
                            sendMessage(ConnectionAction.ACTION_MSG, data);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.connection);
        mText = (EditText) findViewById(R.id.msg_text);
        findViewById(R.id.start).setOnClickListener(mClicked);
        findViewById(R.id.stop).setOnClickListener(mClicked);
        findViewById(R.id.ping).setOnClickListener(mClicked);
        findViewById(R.id.send).setOnClickListener(mClicked);
        doBindService();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.i(TAG, "onServiceConnected!!!!");
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

    void doBindService() {
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


    void doUnbindService() {
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
