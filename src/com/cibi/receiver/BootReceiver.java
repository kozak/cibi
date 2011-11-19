package com.cibi.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.cibi.service.ItemService;

/**
 * @author morswin
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
	public void onReceive(Context context, Intent intent) {
        Log.i("BootReceiver", "BootReceiver#onReceive");
        Intent serviceIntent = new Intent(ItemService.class.getName());
        context.startService(serviceIntent);
	}
}
