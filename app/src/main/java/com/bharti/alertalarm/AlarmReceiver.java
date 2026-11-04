package com.bharti.alertalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

/** Called by AlarmManager at the start time. Starts the ringing service. */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ReminderStore.markExpiredAndGetActive(context, System.currentTimeMillis()).isEmpty()) {
            return;
        }
        try {
            ContextCompat.startForegroundService(context, new Intent(context, AlarmService.class));
        } catch (Exception e) {
            Log.e("AlarmReceiver", "Could not start alarm service", e);
        }
    }
}
