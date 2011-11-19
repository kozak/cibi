package com.cibi.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

/**
 * @author morswin
 */
public class ConnectionChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
        NetworkInfo mobNetInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if (activeNetInfo != null) {
            Toast.makeText(context, "Aktywna sieć : " + activeNetInfo.getTypeName(), Toast.LENGTH_SHORT).show();
        }
        if (mobNetInfo != null) {
            Toast.makeText(context, "Mobilna sieć: " + mobNetInfo.getTypeName(), Toast.LENGTH_SHORT).show();
        }

    }
}