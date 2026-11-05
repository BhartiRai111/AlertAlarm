package com.bharti.alertalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires at bedtime: shows the reminder and sets tomorrow's. */
public class BedtimeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Bedtime.notifyNow(context);
        Bedtime.schedule(context);
    }
}
