package com.bharti.alertalarm;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Bedtime reminder: a notice at night with tomorrow's first task. */
final class Bedtime {

    private static final String CHANNEL = "bedtime";
    private static final int NOTIFICATION_ID = 7100;

    private Bedtime() {
    }

    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 77_002, new Intent(c, BedtimeReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (!AppSettings.bedtimeOn(c)) {
            am.cancel(pi);
            return;
        }
        int minutes = AppSettings.bedtimeMinutes(c);
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, minutes / 60);
        next.set(Calendar.MINUTE, minutes % 60);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_MONTH, 1);
        }
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
                return;
            }
        } catch (SecurityException ignored) {
            // falls back below
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
    }

    static void notifyNow(Context c) {
        long now = System.currentTimeMillis();
        ReminderStore.markExpiredAndGetActive(c, now);
        List<Reminder> enabled = new ArrayList<>();
        for (Reminder r : ReminderStore.getAll(c)) {
            if (r.enabled && !r.done) {
                enabled.add(r);
            }
        }
        long tomorrow = Ui.nextDay(Ui.startOfDay(now));
        List<Ui.Entry> list = Ui.entriesOn(enabled, tomorrow);
        String text;
        if (list.isEmpty()) {
            text = "No alarms tomorrow. Sleep well!";
        } else {
            Ui.Entry first = list.get(0);
            text = "Tomorrow starts with \"" + first.reminder.task + "\" at " + TimeText.time(first.start)
                    + (list.size() > 1 ? " (" + list.size() + " alarms)" : "") + ". Sleep now to wake up fresh.";
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Bedtime reminder", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Reminds you to sleep, with tomorrow's first task");
        nm.createNotificationChannel(channel);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("Time to sleep 😴")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        try {
            nm.notify(NOTIFICATION_ID, b.build());
            AlarmLog.add(c, "😴 Bedtime reminder shown");
        } catch (SecurityException e) {
            AlarmLog.error(c, "Could not show the bedtime reminder", e);
        }
    }
}
