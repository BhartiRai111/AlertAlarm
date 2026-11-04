package com.bharti.alertalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Android deletes all alarms when the phone restarts. This puts them back.
 * If a reminder is in its ringing window right now, the alarm fires in 1 second and ringing restarts.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long now = System.currentTimeMillis();
        for (Reminder r : ReminderStore.getAll(context)) {
            if (r.done) {
                continue;
            }
            if (r.endMillis <= now) {
                ReminderStore.markDone(context, r.id);
            } else {
                AlarmScheduler.schedule(context, r);
            }
        }
    }
}
