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
        if (nm.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Kaam alarms",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Kaam ka time hone par alarm");
        channel.setSound(null, null);          // the service plays the alarm sound itself
        channel.enableVibration(false);         // the service vibrates itself
        channel.setBypassDnd(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(channel);
    }
}
