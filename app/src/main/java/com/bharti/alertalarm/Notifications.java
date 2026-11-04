package com.bharti.alertalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

final class Notifications {

    static final String CHANNEL_ID = "alert_alarm_channel";

    private Notifications() {
    }

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Task alarms",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Rings when it is time for a task");
        channel.setSound(null, null);          // the service plays the alarm sound itself
        channel.enableVibration(false);         // the service vibrates itself
        channel.setBypassDnd(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(channel);
    }
}
