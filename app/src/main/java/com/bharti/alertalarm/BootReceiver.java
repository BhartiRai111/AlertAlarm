package com.bharti.alertalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Android deletes all alarms when the phone restarts. This puts them back.
 * Repeating alarms whose time passed while the phone was off move to their next occurrence.
 * If an alarm is in its ringing window right now, it fires in 1 second and ringing restarts.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        ReminderStore.markExpiredAndGetActive(context, System.currentTimeMillis());
        for (Reminder r : ReminderStore.getAll(context)) {
            if (!r.done && r.enabled) {
                AlarmScheduler.schedule(context, r);
            }
        }
    }
}
