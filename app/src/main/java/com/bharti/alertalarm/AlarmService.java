package com.bharti.alertalarm;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;

/**
 * Rings from start time until end time and cannot be stopped from the UI in between.
 * - Plays the phone's alarm tone on loop (alarm volume, forced to max every few seconds)
 * - Speaks the task out loud every 20 seconds (in English)
 * - Vibrates
 * - Stops itself only when the end time is reached
 */
public class AlarmService extends Service {

    private static final String TAG = "AlarmService";
    private static final int NOTIFICATION_ID = 4242;
    private static final long TICK_MS = 1_000L;
    private static final long SPEAK_EVERY_MS = 20_000L;
    private static final long VOLUME_EVERY_MS = 5_000L;
    private static final long MAX_WAKE_LOCK_MS = 48 * 60 * 60 * 1000L;

    private static volatile boolean running = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioAttributes alarmAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

    private MediaPlayer player;
    private TextToSpeech tts;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;

    private volatile boolean ttsReady = false;
    private volatile boolean speaking = false;
    private boolean started = false;
    private boolean finished = false;
    private long lastSpokenAt = 0;
    private long lastVolumeAt = 0;
    private String currentText = "";
    private String currentTitle = "Alarm";
    private long lockedUntil = 0;

    /** Colours the notification cycles through while ringing (same as the alarm screen). */
    private static final int[] NOTIFICATION_COLORS = {
            0xFF0F766E, 0xFF1D4ED8, 0xFF7C3AED, 0xFFBE185D, 0xFFC2410C, 0xFF15803D};
    /** Android drops notification updates faster than about 5 per second, so 300 ms is the safe minimum. */
    private static final long COLOR_EVERY_MS = 300L;
    private int colorIndex = 0;

    private final Runnable colorTick = new Runnable() {
        @Override
        public void run() {
            if (finished) {
                return;
            }
            colorIndex = (colorIndex + 1) % NOTIFICATION_COLORS.length;
            updateNotification(currentTitle);
            handler.postDelayed(this, COLOR_EVERY_MS);
        }
    };

    static boolean isRunning() {
        return running;
    }

    /** Starts ringing if a reminder is active but the service is not running (self-healing). */
    static void ensureRunning(Context c) {
        if (running) {
            return;
        }
        try {
            ContextCompat.startForegroundService(c, new Intent(c, AlarmService.class));
        } catch (Exception e) {
            Log.w(TAG, "Could not start service", e);
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (finished) {
                return;
            }
            long now = System.currentTimeMillis();
            List<Reminder> active = ReminderStore.markExpiredAndGetActive(AlarmService.this, now);
            if (active.isEmpty()) {
                finishAlarm();   // end time reached: the ONLY way the alarm stops
                return;
            }

            String text = taskText(active);
            long end = 0;
            for (Reminder r : active) {
                end = Math.max(end, r.endMillis);
            }
            lockedUntil = end;
            if (!text.equals(currentText)) {
                currentText = text;
                currentTitle = notificationTitle(active);
                updateNotification(currentTitle);
            }
            if (now - lastVolumeAt >= VOLUME_EVERY_MS) {
                forceMaxVolume();
                lastVolumeAt = now;
            }
            if (ttsReady && !speaking && now - lastSpokenAt >= SPEAK_EVERY_MS) {
                speak(text);
                lastSpokenAt = now;
            }
            if (!speaking && player != null && !player.isPlaying()) {
                try {
                    player.start();
                } catch (IllegalStateException ignored) {
                    // player in a bad state: TTS and vibration still work
                }
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        Notifications.ensureChannel(this);
        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlertAlarm:ringing");
        wakeLock.acquire(MAX_WAKE_LOCK_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        List<Reminder> active = ReminderStore.markExpiredAndGetActive(this, System.currentTimeMillis());

        // Must be called quickly after startForegroundService(), even if we stop right away.
        startInForeground(active.isEmpty() ? "Alarm" : notificationTitle(active));

        if (active.isEmpty()) {
            finishAlarm();
            return START_NOT_STICKY;
        }
        if (!started) {
            started = true;
            startRinging();
            startVibration();
            openRingingScreen();
            initTextToSpeech();
            handler.post(tick);
            handler.postDelayed(colorTick, COLOR_EVERY_MS);
        }
        return START_STICKY;
    }

    // ------------------------------------------------------------------ sound, voice, vibration

    private void startRinging() {
        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri == null) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        }
        if (uri == null) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(alarmAttributes);
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            Log.e(TAG, "Could not play alarm tone", e);
            if (player != null) {
                player.release();
                player = null;
            }
        }
        forceMaxVolume();
    }

    private void startVibration() {
        vibrator = getSystemService(Vibrator.class);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 800, 800}, 0), alarmAttributes);
        }
    }

    private void initTextToSpeech() {
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "Text-to-speech not available");
                return;
            }
            tts.setAudioAttributes(alarmAttributes);
            // Indian English voice if installed, otherwise the default English voice
            int result = tts.setLanguage(new Locale("en", "IN"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.ENGLISH);
            }
            tts.setSpeechRate(0.9f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                }

                @Override
                public void onDone(String utteranceId) {
                    handler.post(() -> speaking = false);
                }

                @Override
                public void onError(String utteranceId) {
                    handler.post(() -> speaking = false);
                }
            });
            ttsReady = true;
        });
    }

    private void speak(String tasks) {
        String message = "Attention please. It is time for your task: " + tasks;
        speaking = true;
        if (player != null && player.isPlaying()) {
            player.pause();   // tone pauses so the voice is clear; tick() resumes it
        }
        Bundle params = new Bundle();
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM);
        if (tts.speak(message, TextToSpeech.QUEUE_FLUSH, params, "task") != TextToSpeech.SUCCESS) {
            speaking = false;
        }
    }

    /** "Lock": if someone turns the alarm volume down, it goes back up within 5 seconds. */
    private void forceMaxVolume() {
        AudioManager am = getSystemService(AudioManager.class);
        try {
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        } catch (SecurityException e) {
            Log.w(TAG, "Could not change volume (Do Not Disturb?)", e);
        }
    }

    // ------------------------------------------------------------------ notification

    private void startInForeground(String title) {
        Notification notification = buildNotification(title);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type);
    }

    private void updateNotification(String title) {
        try {
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification(title));
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission missing", e);
        }
    }

    /**
     * Full-screen notifications only open the alarm screen when the phone is locked; while the phone is
     * in use Android shows just a banner. With "Display over other apps" allowed, we can open it ourselves.
     */
    private void openRingingScreen() {
        try {
            startActivity(new Intent(this, RingingActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION));
        } catch (Exception e) {
            Log.w(TAG, "Could not open the alarm screen directly; the notification will open it", e);
        }
    }

    private Notification buildNotification(String title) {
        Intent open = new Intent(this, RingingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, Notifications.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("⏰ Time for your task!")
                .setContentText(lockedUntil > 0
                        ? title + " · Locked until " + TimeText.time(lockedUntil) : title)
                // Colourful notification: the background keeps changing colour while it rings
                .setColor(NOTIFICATION_COLORS[colorIndex])
                .setColorized(true)
                .setOnlyAlertOnce(true)   // colour updates must not replay sound or pop up again
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(openPi, true)   // opens the alarm screen, even on the lock screen
                .setContentIntent(openPi)
                .build();
    }

    private static String taskText(List<Reminder> active) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < active.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(active.get(i).task);
        }
        return sb.toString();
    }

    private static String notificationTitle(List<Reminder> active) {
        String first = active.get(0).task;
        return active.size() > 1 ? first + " (+" + (active.size() - 1) + " more)" : first;
    }

    // ------------------------------------------------------------------ stopping

    private void finishAlarm() {
        if (finished) {
            return;
        }
        finished = true;
        handler.removeCallbacksAndMessages(null);
        releaseAll();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releaseAll() {
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
            }
            player.release();
            player = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (!finished) {
            // Killed by Android (low memory, battery saver) before the end time: come back in 3 seconds.
            handler.removeCallbacksAndMessages(null);
            releaseAll();
            long now = System.currentTimeMillis();
            if (!ReminderStore.getActive(this, now).isEmpty()) {
                AlarmScheduler.scheduleWake(this, now + 3_000L);
            }
        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Swiping the app away from recent apps does NOT stop the alarm.
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
