package com.bharti.alertalarm;

import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

/** Turn on/off and delete, shared by the dashboard lists and the detail screen. */
final class Alarms {

    private Alarms() {
    }

    /** On/off switch, like the Clock app. A ringing alarm cannot be switched off. */
    static void toggle(Context c, Reminder r, boolean on) {
        if (r.isActive(System.currentTimeMillis())) {
            Toast.makeText(c, "This alarm is locked until " + TimeText.time(r.endMillis), Toast.LENGTH_SHORT).show();
            return;
        }
        ReminderStore.setEnabled(c, r.id, on);
        r.enabled = on;
        if (on) {
            AlarmScheduler.schedule(c, r);
            Toast.makeText(c, "Alarm on: " + TimeText.dayLabel(r.startMillis) + ", "
                    + TimeText.time(r.startMillis), Toast.LENGTH_SHORT).show();
        } else {
            AlarmScheduler.cancel(c, r.id);
            Toast.makeText(c, "Alarm off", Toast.LENGTH_SHORT).show();
        }
    }

    static void confirmDelete(Context c, Reminder r, Runnable after) {
        if (r.isActive(System.currentTimeMillis())) {
            Toast.makeText(c, "This alarm is ringing now. It cannot be deleted before "
                    + TimeText.time(r.endMillis) + ".", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(c)
                .setTitle("Delete this alarm?")
                .setMessage(r.task)
                .setPositiveButton("Delete", (d, w) -> {
                    AlarmScheduler.cancel(c, r.id);
                    ReminderStore.delete(c, r.id);
                    if (r.voicePath != null) {
                        new java.io.File(r.voicePath).delete();
                    }
                    after.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
