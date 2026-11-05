package com.bharti.alertalarm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import java.util.List;

/** "Did you finish it?" after an alarm ends: the answer is saved on the task and shown in the lists. */
final class TaskCheck {

    static final int UNKNOWN = 0;
    static final int COMPLETED = 1;
    static final int NOT_DONE = 2;

    static final String ACTION_ANSWER = "com.bharti.alertalarm.TASK_ANSWER";
    static final String EXTRA_ID = "id";
    static final String EXTRA_START = "start";
    static final String EXTRA_RESULT = "result";
    private static final String CHANNEL = "task_check";

    private TaskCheck() {
    }

    /** One task occurrence that just finished ringing. */
    static final class Rang {
        final long id;
        final long start;
        final String task;

        Rang(long id, long start, String task) {
            this.id = id;
            this.start = start;
            this.task = task;
        }
    }

    static void record(Context c, long id, long start, int result) {
        Reminder r = ReminderStore.setResult(c, id, start, result);
        cancel(c, id);
        if (r != null) {
            AlarmLog.add(c, (result == COMPLETED ? "✅ Completed: " : "❌ Not done: ") + r.task);
        }
    }

    /** Asks with a notification (Yes / No buttons), unless it was already answered on the alarm screen. */
    static void ask(Context c, List<Rang> rang) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Did you finish it?",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Asks after an alarm whether the task was done");
        nm.createNotificationChannel(channel);
        for (Rang t : rang) {
            Reminder r = ReminderStore.find(c, t.id);
            if (r != null && r.resultFor == t.start && r.result != UNKNOWN) {
                continue;   // answered already
            }
            NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_alarm)
                    .setContentTitle("Did you finish it?")
                    .setContentText(t.task)
                    .setAutoCancel(true)
                    .setContentIntent(PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                    .addAction(0, "✅ Yes, done", answer(c, t, COMPLETED))
                    .addAction(0, "❌ No", answer(c, t, NOT_DONE));
            try {
                nm.notify(notificationId(t.id), b.build());
            } catch (SecurityException e) {
                AlarmLog.error(c, "Could not ask \"Did you finish it?\"", e);
            }
        }
    }

    private static PendingIntent answer(Context c, Rang t, int result) {
        Intent i = new Intent(c, TaskCheckReceiver.class).setAction(ACTION_ANSWER)
                .putExtra(EXTRA_ID, t.id).putExtra(EXTRA_START, t.start).putExtra(EXTRA_RESULT, result);
        int code = notificationId(t.id) * 4 + result;
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void cancel(Context c, long id) {
        c.getSystemService(NotificationManager.class).cancel(notificationId(id));
    }

    private static int notificationId(long id) {
        return 6000 + (int) ((id ^ (id >>> 32)) & 0x3FFF);
    }

    /** " · ✅ Completed" / " · ❌ Not done" / "" */
    static String label(Reminder r) {
        if (r.result == COMPLETED) {
            return r.isRepeating() ? " · Last time ✅" : " · ✅ Completed";
        }
        if (r.result == NOT_DONE) {
            return r.isRepeating() ? " · Last time ❌" : " · ❌ Not done";
        }
        return "";
    }
}
