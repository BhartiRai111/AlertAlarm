package com.bharti.alertalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Wakes up at a volume schedule's start or end time and sets the right volume. */
public class VolumeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        VolumeManager.reconcile(context);
    }
}
