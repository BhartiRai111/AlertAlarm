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
 * Applies the volume mode that should be active now and wakes up at the next time-range start/end.
 * Several modes can be on: the one switched on most recently wins. When none is on, the phone's own
 * volumes (saved before the first mode changed anything) are put back. The alarm volume is never touched.
 */
final class VolumeManager {

    private static final String TAG = "VolumeManager";
    static final String ACTION_CHECK = "com.bharti.alertalarm.VOLUME_CHECK";

    private static final String APPLIED = "applied_signature";
    private static final String APPLIED_MODE = "applied_mode";
    private static final String APPLIED_UNTIL = "applied_until";
    private static final String SAVED = "saved";
    private static final String SAVED_RINGER = "saved_ringer";
    private static final String SAVED_RING = "saved_ring";
    private static final String SAVED_NOTIFICATION = "saved_notification";
    private static final String SAVED_CALL = "saved_call";
    private static final String SAVED_MEDIA = "saved_media";
    /** Left over from the first volume version (time-range schedules). */
    private static final String LEGACY_ACTIVE = "active_id";

    private VolumeManager() {
    }

    static boolean hasDndAccess(Context c) {
        return c.getSystemService(NotificationManager.class).isNotificationPolicyAccessGranted();
    }

    /** Meeting mode is active right now (AlertAlarm task alarms stay quiet then). */
    static boolean isMeetingNow(Context c) {
        return VolumeProfile.Mode.MEETING.name().equals(VolumeStore.prefs(c).getString(APPLIED_MODE, ""));
    }

    /** The mode changing the volume now, or null. */
    static VolumeProfile active(Context c) {
        String mode = VolumeStore.prefs(c).getString(APPLIED_MODE, null);
        if (mode == null) {
            return null;
        }
        for (VolumeProfile p : VolumeStore.getAll(c)) {
            if (p.mode.name().equals(mode)) {
                return p;
            }
        }
        return null;
    }

    /** End of the active mode's time range, or 0 when it was switched on by hand. */
    static long activeUntil(Context c) {
        return VolumeStore.prefs(c).getLong(APPLIED_UNTIL, 0);
    }

    /** Is this mode on (by hand or by its time range) right now? */
    static boolean isOn(VolumeProfile p, long now) {
        return p.manualOn || p.windowAt(now) != null;
    }

    /** Call after any change, at boot, and whenever the wake-up alarm fires. */
    static synchronized void reconcile(Context c) {
        long now = System.currentTimeMillis();
        List<VolumeProfile> all = VolumeStore.getAll(c);

        // A one-time range that is over turns its time range off
        boolean changed = false;
        for (VolumeProfile p : all) {
            if (p.timeIsOver(now)) {
                p.timed = false;
                changed = true;
            }
        }
        if (changed) {
            VolumeStore.saveAll(c, all);
        }

        // The mode switched on most recently wins
        VolumeProfile winner = null;
        long winnerSince = -1;
        long winnerUntil = 0;
        for (VolumeProfile p : all) {
            long since = -1;
            long until = 0;
            if (p.manualOn) {
                since = p.onSince;
            }
            long[] w = p.windowAt(now);
            if (w != null && w[0] > since) {
                since = w[0];
                until = p.manualOn ? 0 : w[1];
            }
            if (since >= 0 && since > winnerSince) {
                winner = p;
                winnerSince = since;
                winnerUntil = until;
            }
        }

        SharedPreferences prefs = VolumeStore.prefs(c);
        String applied = prefs.getString(APPLIED, null);
        if (winner != null) {
            if (!prefs.getBoolean(SAVED, false)) {
                saveCurrentVolume(c);
            }
            if (!winner.signature().equals(applied)) {
                apply(c, winner);
            }
            prefs.edit().putString(APPLIED, winner.signature()).putString(APPLIED_MODE, winner.mode.name())
                    .putLong(APPLIED_UNTIL, winnerUntil).remove(LEGACY_ACTIVE).apply();
        } else if (applied != null || prefs.getLong(LEGACY_ACTIVE, 0) != 0) {
            restore(c);
            prefs.edit().remove(APPLIED).remove(APPLIED_MODE).remove(APPLIED_UNTIL).remove(SAVED)
                    .remove(LEGACY_ACTIVE).apply();
        }

        scheduleNextCheck(c, all, now);
    }

    // ------------------------------------------------------------------ volume

    private static void saveCurrentVolume(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        VolumeStore.prefs(c).edit()
                .putBoolean(SAVED, true)
                .putInt(SAVED_RINGER, am.getRingerMode())
                .putInt(SAVED_RING, am.getStreamVolume(AudioManager.STREAM_RING))
                .putInt(SAVED_NOTIFICATION, am.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
                .putInt(SAVED_CALL, am.getStreamVolume(AudioManager.STREAM_VOICE_CALL))
                .putInt(SAVED_MEDIA, am.getStreamVolume(AudioManager.STREAM_MUSIC))
                .apply();
    }

    private static void apply(Context c, VolumeProfile p) {
        AlarmLog.add(c, "🔈 " + p.mode.title + " mode on (" + p.levelsText() + ")");
        AudioManager am = c.getSystemService(AudioManager.class);
        // Ringtone 0 = vibrate. Volumes can only be set in normal mode, so set them first.
        safe(c, () -> am.setRingerMode(AudioManager.RINGER_MODE_NORMAL));
        if (p.ring > 0) {
            setPercent(c, am, AudioManager.STREAM_RING, p.ring);
            setPercent(c, am, AudioManager.STREAM_NOTIFICATION, p.notification);
        }
        setPercent(c, am, AudioManager.STREAM_VOICE_CALL, Math.max(p.call, 1));
        setPercent(c, am, AudioManager.STREAM_MUSIC, p.media);
        if (p.ring == 0) {
            safe(c, () -> am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE));
        } else if (p.notification == 0) {
            safe(c, () -> am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0));
        }
    }

    private static void setPercent(Context c, AudioManager am, int stream, int percent) {
        int max = am.getStreamMaxVolume(stream);
        int level = Math.round(max * Math.max(0, Math.min(100, percent)) / 100f);
        if (percent > 0) {
            level = Math.max(1, level);
        }
        int finalLevel = Math.min(max, level);
        safe(c, () -> am.setStreamVolume(stream, finalLevel, 0));
    }

    private static void restore(Context c) {
        AlarmLog.add(c, "🔈 Volume modes off: phone volume back to normal");
        SharedPreferences p = VolumeStore.prefs(c);
        AudioManager am = c.getSystemService(AudioManager.class);
        int ringer = p.getInt(SAVED_RINGER, AudioManager.RINGER_MODE_NORMAL);
        safe(c, () -> am.setRingerMode(AudioManager.RINGER_MODE_NORMAL));
        if (p.contains(SAVED_RING)) {
            safe(c, () -> am.setStreamVolume(AudioManager.STREAM_RING, Math.max(1, p.getInt(SAVED_RING, 1)), 0));
        }
        if (p.contains(SAVED_NOTIFICATION)) {
            safe(c, () -> am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, Math.max(1, p.getInt(SAVED_NOTIFICATION, 1)), 0));
        }
        if (p.contains(SAVED_CALL)) {
            safe(c, () -> am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, Math.max(1, p.getInt(SAVED_CALL, 1)), 0));
        }
        if (p.contains(SAVED_MEDIA)) {
            safe(c, () -> am.setStreamVolume(AudioManager.STREAM_MUSIC, p.getInt(SAVED_MEDIA, 0), 0));
        }
        if (ringer != AudioManager.RINGER_MODE_NORMAL) {
            safe(c, () -> am.setRingerMode(ringer));
        }
    }

    /** Without Do Not Disturb access some changes are refused; the rest still happen. */
    private static void safe(Context c, Runnable r) {
        try {
            r.run();
        } catch (SecurityException e) {
            Log.w(TAG, "Volume change blocked (Do Not Disturb access is off?)", e);
            AlarmLog.add(c, "⚠ Volume change blocked: allow Do Not Disturb access");
        }
    }

    // ------------------------------------------------------------------ wake-ups

    private static void scheduleNextCheck(Context c, List<VolumeProfile> all, long now) {
        long next = Long.MAX_VALUE;
        for (VolumeProfile p : all) {
            long[] w = p.windowAt(now);
            if (w != null && w[1] < next) {
                next = w[1];
            }
            long s = p.nextStart(now);
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
