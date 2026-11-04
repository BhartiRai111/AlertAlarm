package com.bharti.alertalarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Uses AlarmManager.setAlarmClock: the most reliable alarm on Android. It fires exactly on time,
 * even in Doze (deep sleep), and shows the alarm icon in the status bar.
 */
final class AlarmScheduler {

    private static final String ACTION_FIRE = "com.bharti.alertalarm.FIRE";
    private static final int WAKE_REQUEST_CODE = -7;

    private AlarmScheduler() {
    }

    static void schedule(Context c, Reminder r) {
        long trigger = Math.max(r.startMillis, System.currentTimeMillis() + 1_000L);
        setAlarm(c, trigger, firePendingIntent(c, requestCode(r.id)));
    }

    /** Used to restart ringing quickly if Android kills the service before the end time. */
    static void scheduleWake(Context c, long triggerAt) {
        setAlarm(c, triggerAt, firePendingIntent(c, WAKE_REQUEST_CODE));
    }

    static void cancel(Context c, long id) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.cancel(firePendingIntent(c, requestCode(id)));
    }

    private static void setAlarm(Context c, long triggerAt, PendingIntent fire) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent showApp = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAt, showApp), fire);
                return;
            }
        } catch (SecurityException e) {
            Log.w("AlarmScheduler", "Exact alarm not allowed, using inexact alarm", e);
        }
        // Fallback when the "Alarms & reminders" permission is off: may be a few minutes late.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, fire);
    }

    private static PendingIntent firePendingIntent(Context c, int requestCode) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACTION_FIRE);
        return PendingIntent.getBroadcast(c, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static int requestCode(long id) {
        int rc = (int) (id ^ (id >>> 32));
        return rc == WAKE_REQUEST_CODE ? rc + 1 : rc;
    }
}
