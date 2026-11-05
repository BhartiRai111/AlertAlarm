package com.bharti.alertalarm;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.Locale;

/** Everything the user can change on the Settings tab. */
final class AppSettings {

    static final String THEME_DARK = "dark";
    static final String THEME_LIGHT = "light";
    static final String THEME_SYSTEM = "system";

    private AppSettings() {
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("alert_alarm_settings", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ voice

    /** "en" or "hi". */
    static String voiceLanguage(Context c) {
        return p(c).getString("voice_language", "en");
    }

    static void setVoiceLanguage(Context c, String lang) {
        p(c).edit().putString("voice_language", lang).apply();
    }

    static boolean voiceOn(Context c) {
        return p(c).getBoolean("voice_on", true);
    }

    static void setVoiceOn(Context c, boolean on) {
        p(c).edit().putBoolean("voice_on", on).apply();
    }

    /** Seconds between announcements. */
    static int voiceEvery(Context c) {
        return p(c).getInt("voice_every", 20);
    }

    static void setVoiceEvery(Context c, int seconds) {
        p(c).edit().putInt("voice_every", seconds).apply();
    }

    static float voiceSpeed(Context c) {
        return p(c).getFloat("voice_speed", 0.9f);
    }

    static void setVoiceSpeed(Context c, float speed) {
        p(c).edit().putFloat("voice_speed", speed).apply();
    }

    static Locale voiceLocale(Context c) {
        return "hi".equals(voiceLanguage(c)) ? new Locale("hi", "IN") : new Locale("en", "IN");
    }

    /** What the alarm says. Hindi only if a Hindi voice is installed. */
    static String announcement(Context c, String tasks, boolean hindiVoice) {
        if (hindiVoice && "hi".equals(voiceLanguage(c))) {
            return "कृपया ध्यान दें। आपके काम का समय हो गया है: " + tasks;
        }
        return "Attention please. It is time for your task: " + tasks;
    }

    // ------------------------------------------------------------------ sound

    /** Chosen alarm tone, or null for the phone's default alarm tone. */
    static String alarmTone(Context c) {
        return p(c).getString("alarm_tone", null);
    }

    static void setAlarmTone(Context c, String uri) {
        p(c).edit().putString("alarm_tone", uri).apply();
    }

    static boolean vibration(Context c) {
        return p(c).getBoolean("vibration", true);
    }

    static void setVibration(Context c, boolean on) {
        p(c).edit().putBoolean("vibration", on).apply();
    }

    /** Alarm volume goes back to max if someone lowers it. */
    static boolean volumeLock(Context c) {
        return p(c).getBoolean("volume_lock", true);
    }

    static void setVolumeLock(Context c, boolean on) {
        p(c).edit().putBoolean("volume_lock", on).apply();
    }

    // ------------------------------------------------------------------ display

    static String theme(Context c) {
        return p(c).getString("theme", THEME_DARK);
    }

    static void setTheme(Context c, String theme) {
        p(c).edit().putString("theme", theme).apply();
        applyTheme(theme);
    }

    static void applyTheme(String theme) {
        int mode = THEME_LIGHT.equals(theme) ? AppCompatDelegate.MODE_NIGHT_NO
                : THEME_SYSTEM.equals(theme) ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                : AppCompatDelegate.MODE_NIGHT_YES;
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode);
        }
    }

    static boolean use24h(Context c) {
        return p(c).getBoolean("use_24h", false);
    }

    static void setUse24h(Context c, boolean on) {
        p(c).edit().putBoolean("use_24h", on).apply();
        TimeText.use24h = on;
    }

    // ------------------------------------------------------------------ backup

    static void exportTo(Context c, org.json.JSONObject o) throws org.json.JSONException {
        o.put("voice_language", voiceLanguage(c));
        o.put("voice_on", voiceOn(c));
        o.put("voice_every", voiceEvery(c));
        o.put("voice_speed", (double) voiceSpeed(c));
        o.put("vibration", vibration(c));
        o.put("volume_lock", volumeLock(c));
        o.put("theme", theme(c));
        o.put("use_24h", use24h(c));
    }

    static void importFrom(Context c, org.json.JSONObject o) {
        SharedPreferences.Editor e = p(c).edit();
        if (o.has("voice_language")) e.putString("voice_language", o.optString("voice_language", "en"));
        if (o.has("voice_on")) e.putBoolean("voice_on", o.optBoolean("voice_on", true));
        if (o.has("voice_every")) e.putInt("voice_every", o.optInt("voice_every", 20));
        if (o.has("voice_speed")) e.putFloat("voice_speed", (float) o.optDouble("voice_speed", 0.9));
        if (o.has("vibration")) e.putBoolean("vibration", o.optBoolean("vibration", true));
        if (o.has("volume_lock")) e.putBoolean("volume_lock", o.optBoolean("volume_lock", true));
        if (o.has("theme")) e.putString("theme", o.optString("theme", THEME_DARK));
        if (o.has("use_24h")) e.putBoolean("use_24h", o.optBoolean("use_24h", false));
        e.apply();
        TimeText.use24h = o.optBoolean("use_24h", use24h(c));
    }
}
