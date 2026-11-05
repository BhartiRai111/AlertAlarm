package com.bharti.alertalarm;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import java.util.List;

/**
 * Applies the right volume for "now" and wakes up at the next start/end time.
 * Before a schedule changes anything, the phone's own ringer mode and volumes are saved, and they are put
 * back when the schedule ends.
 */
final class VolumeManager {

    private static final String TAG = "VolumeManager";
    static final String ACTION_CHECK = "com.bharti.alertalarm.VOLUME_CHECK";

    private static final String ACTIVE_ID = "active_id";
    private static final String ACTIVE_MODE = "active_mode";
    private static final String ACTIVE_END = "active_end";
    private static final String SAVED_RINGER = "saved_ringer";
    private static final String SAVED_RING = "saved_ring";
    private static final String SAVED_NOTIFICATION = "saved_notification";

    /** Prayer = this share of the maximum ringtone volume. */
    private static final float PRAYER_LEVEL = 0.2f;

    private VolumeManager() {
    }

    static boolean hasDndAccess(Context c) {
        return c.getSystemService(NotificationManager.class).isNotificationPolicyAccessGranted();
    }

    /** Meeting mode is changing the volume right now (AlertAlarm alarms stay quiet then). */
    static boolean isMeetingNow(Context c) {
        SharedPreferences p = VolumeStore.prefs(c);
        return p.getLong(ACTIVE_ID, 0) != 0
                && VolumeSchedule.Mode.MEETING.name().equals(p.getString(ACTIVE_MODE, ""))
                && System.currentTimeMillis() < p.getLong(ACTIVE_END, 0);
    }

    /** The schedule changing the volume now, or null. */
    static VolumeSchedule active(Context c) {
        long id = VolumeStore.prefs(c).getLong(ACTIVE_ID, 0);
        if (id == 0) {
            return null;
        }
        for (VolumeSchedule v : VolumeStore.getAll(c)) {
            if (v.id == id) {
                return v;
            }
        }
        return null;
    }

    static long activeEnd(Context c) {
        return VolumeStore.prefs(c).getLong(ACTIVE_END, 0);
    }

    /** Call after any change, at boot, and whenever the wake-up alarm fires. */
    static synchronized void reconcile(Context c) {
        long now = System.currentTimeMillis();
        List<VolumeSchedule> all = VolumeStore.getAll(c);

        // One-time schedules that are over switch themselves off
        boolean changed = false;
        for (VolumeSchedule v : all) {
            if (v.enabled && v.isOver(now)) {
                v.enabled = false;
                changed = true;
            }
        }
        if (changed) {
            VolumeStore.saveAll(c, all);
        }

        // Which schedule should be active now? The one that started most recently wins.
        VolumeSchedule current = null;
        long currentStart = 0;
        long currentEnd = 0;
        for (VolumeSchedule v : all) {
            long[] w = v.windowAt(now);
            if (w != null && (current == null || w[0] > currentStart)) {
                current = v;
                currentStart = w[0];
                currentEnd = w[1];
            }
        }

        SharedPreferences p = VolumeStore.prefs(c);
        long activeId = p.getLong(ACTIVE_ID, 0);
        String activeMode = p.getString(ACTIVE_MODE, "");
        if (current != null) {
            if (activeId == 0) {
                saveCurrentVolume(c);
            }
            if (activeId != current.id || !activeMode.equals(current.mode.name()) || activeId == 0) {
                apply(c, current.mode);
            }
            p.edit().putLong(ACTIVE_ID, current.id).putString(ACTIVE_MODE, current.mode.name())
                    .putLong(ACTIVE_END, currentEnd).apply();
        } else if (activeId != 0) {
            restore(c);
            p.edit().remove(ACTIVE_ID).remove(ACTIVE_MODE).remove(ACTIVE_END).apply();
        }

        scheduleNextCheck(c, all, now, current != null ? currentEnd : -1);
    }

    // ------------------------------------------------------------------ volume

    private static void saveCurrentVolume(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        VolumeStore.prefs(c).edit()
                .putInt(SAVED_RINGER, am.getRingerMode())
                .putInt(SAVED_RING, am.getStreamVolume(AudioManager.STREAM_RING))
                .putInt(SAVED_NOTIFICATION, am.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
                .apply();
    }

    private static void apply(Context c, VolumeSchedule.Mode mode) {
        AudioManager am = c.getSystemService(AudioManager.class);
        try {
            switch (mode) {
                case MEETING:
                    am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    break;
                case TRAVEL:
                    am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    setVolume(am, AudioManager.STREAM_RING, 1f);
                    setVolume(am, AudioManager.STREAM_NOTIFICATION, 1f);
                    break;
                case PRAYER:
                    am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    setVolume(am, AudioManager.STREAM_RING, PRAYER_LEVEL);
                    setVolume(am, AudioManager.STREAM_NOTIFICATION, PRAYER_LEVEL);
                    break;
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Volume change blocked: Do Not Disturb access is off", e);
        }
    }

    private static void setVolume(AudioManager am, int stream, float share) {
        int max = am.getStreamMaxVolume(stream);
        int level = Math.max(1, Math.round(max * share));
        am.setStreamVolume(stream, Math.min(max, level), 0);
    }

    private static void restore(Context c) {
        SharedPreferences p = VolumeStore.prefs(c);
        AudioManager am = c.getSystemService(AudioManager.class);
        int ringer = p.getInt(SAVED_RINGER, AudioManager.RINGER_MODE_NORMAL);
        try {
            // Volumes can only be set while the phone is in normal mode, so: normal -> volumes -> old mode
            am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            if (p.contains(SAVED_RING)) {
                am.setStreamVolume(AudioManager.STREAM_RING, Math.max(1, p.getInt(SAVED_RING, 1)), 0);
            }
            if (p.contains(SAVED_NOTIFICATION)) {
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, Math.max(1, p.getInt(SAVED_NOTIFICATION, 1)), 0);
            }
            if (ringer != AudioManager.RINGER_MODE_NORMAL) {
                am.setRingerMode(ringer);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Could not restore volume: Do Not Disturb access is off", e);
        }
    }

    // ------------------------------------------------------------------ wake-ups

    private static void scheduleNextCheck(Context c, List<VolumeSchedule> all, long now, long activeEnd) {
        long next = activeEnd > now ? activeEnd : Long.MAX_VALUE;
        for (VolumeSchedule v : all) {
            long s = v.nextStart(now);
            if (s > now && s < next) {
                next = s;
            }
        }
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = checkIntent(c);
        if (next == Long.MAX_VALUE) {
            am.cancel(pi);
            return;
        }
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
                return;
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Exact alarm not allowed", e);
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
    }

    private static PendingIntent checkIntent(Context c) {
        Intent i = new Intent(c, VolumeReceiver.class).setAction(ACTION_CHECK);
        return PendingIntent.getBroadcast(c, 77_001, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
