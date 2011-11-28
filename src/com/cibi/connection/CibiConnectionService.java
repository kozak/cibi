package com.cibi.connection;

import android.app.*;
import android.content.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import android.util.Log;
import com.cibi.R;
import com.cibi.activity.TestConnection;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.cibi.connection.ConnectionAction.*;

public class CibiConnectionService extends Service {
    public static final String TAG = "CibiConnectionService";

    private static final String HOST = "10.0.2.2";
    private static final int PORT = 8888;

    private static final long KEEP_ALIVE_INTERVAL = 1000 * 20 * 28;
    private static final long INITIAL_RETRY_INTERVAL = 1000 * 10;
    private static final long MAXIMUM_RETRY_INTERVAL = 1000 * 60 * 30;

    private static final int NOTIF_CONNECTED = 0;
    private static final String PREF_STARTED = "isStarted";


    private ConnectionLog mLog;

    private ConnectivityManager mConnMan;
    private NotificationManager mNotifMan;

    private boolean mStarted;
    private ConnectionThread mConnection;

    private SharedPreferences mPrefs;

    final Messenger mMessenger = new Messenger(new ConnectionServiceHandler());
    final List<Messenger> mClients = new ArrayList<Messenger>();


    class ConnectionServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case  START:
                    start();
                    break;
                case STOP:
                    stop();
                    stopSelf();
                    break;
                case KEEP_ALIVE:
                    keepAlive();
                    break;
                case RECONNECT:
                    reconnectIfNecessary();
                    break;
                case MSG:
                    sendTextMessage(msg.getData().getString("msg"));
                    break;
                case ADD_LISTENER:
                    mClients.add(msg.replyTo);
                    break;
                case REMOVE_LISTENER:
                    mClients.remove(msg.replyTo);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }





    @Override
    public void onCreate() {
        super.onCreate();

        try {
            mLog = new ConnectionLog();
            Log.i(TAG, "Opened log at " + mLog.getPath());
        } catch (IOException e) {
        }

        mPrefs = getSharedPreferences(TAG, MODE_PRIVATE);

        mConnMan =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        mNotifMan =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        /* If our process was reaped by the system for any reason we need
           * to restore our state with merely a call to onCreate.  We record
           * the last "started" value and restore it here if necessary. */
        handleCrashedService();
    }

    private void handleCrashedService() {
        if (wasStarted()) {
            /* We probably didn't get a chance to clean up gracefully, so do
                * it now. */
            hideNotification();
            stopKeepAlives();

            /* Formally start and attempt connection. */
            start();
        }
    }

    @Override
    public void onDestroy() {
        log("Service destroyed (started=" + mStarted + ")");

        if (mStarted)
            stop();

        try {
            if (mLog != null)
                mLog.close();
        } catch (IOException e) {
        }
    }

    private void log(String message) {
        Log.i(TAG, message);

        if (mLog != null) {
            try {
                mLog.println(message);
            } catch (IOException e) {
            }
        }
    }

    private boolean wasStarted() {
        return mPrefs.getBoolean(PREF_STARTED, false);
    }

    private void setStarted(boolean started) {
        mPrefs.edit().putBoolean(PREF_STARTED, started).commit();
        mStarted = started;
    }


    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    private synchronized void start() {
        if (mStarted) {
            Log.w(TAG, "Attempt to start connection that is already active");
            return;
        }

        setStarted(true);

        registerReceiver(mConnectivityChanged,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        log("Connecting...");

        mConnection = new ConnectionThread(HOST, PORT);
        mConnection.start();
    }

    private synchronized void stop() {
        if (!mStarted) {
            Log.w(TAG, "Attempt to stop connection not active.");
            return;
        }

        setStarted(false);

        unregisterReceiver(mConnectivityChanged);
        cancelReconnect();

        if (mConnection != null) {
            mConnection.abort();
            mConnection = null;
        }
    }

    private synchronized void sendTextMessage(String text) {
        try {
            if (mStarted && mConnection != null) {
                mConnection.sendTextMessage(text);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error when sending text message", e);
        }

    }

    private synchronized void keepAlive() {
        try {
            if (mStarted && mConnection != null)
                mConnection.sendKeepAlive();
        } catch (IOException e) {
        }
    }

    private void startKeepAlives() {
        Intent i = new Intent();
        i.setClass(this, CibiConnectionService.class);
        i.setAction(ConnectionAction.ACTION_KEEP_ALIVE.toString());
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        AlarmManager alarmMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + KEEP_ALIVE_INTERVAL,
                KEEP_ALIVE_INTERVAL, pi);
    }

    private void stopKeepAlives() {
        Intent i = new Intent();
        i.setClass(this, CibiConnectionService.class);
        i.setAction(ConnectionAction.ACTION_KEEP_ALIVE.toString());
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        AlarmManager alarmMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmMgr.cancel(pi);
    }

    public void scheduleReconnect(long startTime) {
        long interval =
                mPrefs.getLong("retryInterval", INITIAL_RETRY_INTERVAL);

        long now = System.currentTimeMillis();
        long elapsed = now - startTime;

        if (elapsed < interval)
            interval = Math.min(interval * 4, MAXIMUM_RETRY_INTERVAL);
        else
            interval = INITIAL_RETRY_INTERVAL;

        log("Rescheduling connection in " + interval + "ms.");

        mPrefs.edit().putLong("retryInterval", interval).commit();

        Intent i = new Intent();
        i.setClass(this, CibiConnectionService.class);
        i.setAction(ConnectionAction.ACTION_RECONNECT.toString());
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        AlarmManager alarmMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmMgr.set(AlarmManager.RTC_WAKEUP, now + interval, pi);
    }

    public void cancelReconnect() {
        Intent i = new Intent();
        i.setClass(this, CibiConnectionService.class);
        i.setAction(ConnectionAction.ACTION_RECONNECT.toString());
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        AlarmManager alarmMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmMgr.cancel(pi);
    }

    private synchronized void reconnectIfNecessary() {
        if (mStarted && mConnection == null) {
            log("Reconnecting...");

            mConnection = new ConnectionThread(HOST, PORT);
            mConnection.start();
        }
    }

    private BroadcastReceiver mConnectivityChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            NetworkInfo info = (NetworkInfo) intent.getParcelableExtra
                    (ConnectivityManager.EXTRA_NETWORK_INFO);

            boolean hasConnectivity = (info != null && info.isConnected());

            log("Connecting changed: connected=" + hasConnectivity);

            if (hasConnectivity)
                reconnectIfNecessary();
        }
    };

    private void showNotification() {
        Notification n = new Notification();

        n.flags = Notification.FLAG_NO_CLEAR |
                Notification.FLAG_ONGOING_EVENT;

        n.icon = R.drawable.connected_notify;
        n.when = System.currentTimeMillis();

        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, TestConnection.class), 0);

        n.setLatestEventInfo(this, "KeepAlive connected",
                "Connected to " + HOST + ":" + PORT, pi);

        mNotifMan.notify(NOTIF_CONNECTED, n);
    }

    private void hideNotification() {
        mNotifMan.cancel(NOTIF_CONNECTED);
    }

    private class ConnectionThread extends Thread {
        private final Socket mSocket;
        private final String mHost;
        private final int mPort;

        private volatile boolean mAbort = false;

        public ConnectionThread(String host, int port) {
            mHost = host;
            mPort = port;
            mSocket = new Socket();
        }

        public boolean isConnected() {
            return mSocket.isConnected();
        }

        private boolean isNetworkAvailable() {
            NetworkInfo info = mConnMan.getActiveNetworkInfo();
            if (info == null)
                return false;

            return info.isConnected();
        }

        public void run() {
            Socket s = mSocket;

            long startTime = System.currentTimeMillis();

            try {
                s.connect(new InetSocketAddress(mHost, mPort), 20000);

                /* This is a special case for our demonstration.  The
                     * keep-alive is sent from the client side but since I'm
                     * testing it with just netcat, no response is sent from the
                     * server.  This means that we might never actually read
                     * any data even though our connection is still alive.  Most
                     * instances of a persistent TCP connection would have some
                     * sort of application-layer acknowledgement from the server
                     * and so should set a read timeout of KEEP_ALIVE_INTERVAL
                     * plus an arbitrary timeout such as 2 minutes. */
                //s.setSoTimeout((int)KEEP_ALIVE_INTERVAL + 120000);

                log("Connection established to " + s.getInetAddress() +
                        ":" + mPort);

                startKeepAlives();
                showNotification();

                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));

                String line = null;
                while ((line = in.readLine()) != null) {
                    log("Got msg: " + line);
                    Message message = Message.obtain(null, ConnectionAction.ACTION_MSG.toInteger());
                    message.getData().putString("msg", line);
                    message.replyTo = mMessenger;
                    broadcastMsg(message);
                }

                if (!mAbort)
                    log("Server closed connection unexpectedly.");
                in.close();
            } catch (IOException e) {
                log("Unexpected I/O error: " + e.toString());
            } finally {
                stopKeepAlives();
                hideNotification();

                if (mAbort)
                    log("Connection aborted, shutting down.");
                else {
                    try {
                        s.close();
                    } catch (IOException e) {
                    }

                    synchronized (CibiConnectionService.this) {
                        mConnection = null;
                    }

                    /* If our local interface is still up then the connection
                          * failure must have been something intermittent.  Try
                          * our connection again later (the wait grows with each
                          * successive failure).  Otherwise we will try to
                          * reconnect when the local interface comes back. */
                    if (isNetworkAvailable())
                        scheduleReconnect(startTime);
                }
            }
        }

        public void sendKeepAlive()
                throws IOException {
            Socket s = mSocket;
            Date d = new Date();
            s.getOutputStream().write((d.toString() + "\n").getBytes());

            log("Keep-alive sent.");
        }

        public void sendTextMessage(String msg) throws IOException {
            Socket s = mSocket;
            s.getOutputStream().write((msg + "\n").getBytes());
        }

        public void broadcastMsg(Message msg) {
            for (Messenger mClient : mClients) {
                try {
                    mClient.send(msg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }


        public void abort() {
            log("Connection aborting.");

            mAbort = true;

            try {
                mSocket.shutdownOutput();
            } catch (IOException e) {
            }

            try {
                mSocket.shutdownInput();
            } catch (IOException e) {
            }

            try {
                mSocket.close();
            } catch (IOException e) {
            }

            while (true) {
                try {
                    join();
                    break;
                } catch (InterruptedException e) {
                }
            }
        }
    }
}
