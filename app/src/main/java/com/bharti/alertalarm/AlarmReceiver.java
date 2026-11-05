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
        int due = ReminderStore.markExpiredAndGetActive(context, System.currentTimeMillis()).size();
        if (due == 0) {
            AlarmLog.add(context, "Phone woke AlertAlarm, but no task is due right now");
            return;
        }
        AlarmLog.add(context, "⏰ Phone woke AlertAlarm: " + due + (due == 1 ? " task" : " tasks") + " due");
        try {
            ContextCompat.startForegroundService(context, new Intent(context, AlarmService.class));
        } catch (Exception e) {
            Log.e("AlarmReceiver", "Could not start alarm service", e);
            AlarmLog.error(context, "Could not start the alarm (phone blocked it?)", e);
        }
    }
}
