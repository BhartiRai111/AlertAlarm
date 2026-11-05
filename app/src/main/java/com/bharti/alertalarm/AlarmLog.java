package com.bharti.alertalarm;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** A small diary of what the alarm did (set, rang, stopped, errors), shown in Settings > Alarm log. */
final class AlarmLog {

    private static final String PREFS = "alert_alarm_log";
    private static final String KEY = "entries";
    private static final int MAX = 200;

    private AlarmLog() {
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized void add(Context c, String message) {
        try {
            JSONArray old = new JSONArray(p(c).getString(KEY, "[]"));
            JSONArray arr = new JSONArray();
            int from = Math.max(0, old.length() - (MAX - 1));
            for (int i = from; i < old.length(); i++) {
                arr.put(old.get(i));
            }
            JSONObject e = new JSONObject();
            e.put("t", System.currentTimeMillis());
            e.put("m", message);
            arr.put(e);
            p(c).edit().putString(KEY, arr.toString()).commit();
        } catch (Exception ignored) {
            // the log must never break the alarm
        }
    }

    static void error(Context c, String what, Throwable t) {
        add(c, "⚠ " + what + ": " + t.getClass().getSimpleName()
                + (t.getMessage() != null ? " (" + t.getMessage() + ")" : ""));
    }

    /** Newest first, one entry per line. */
    static synchronized String text(Context c) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat f = new SimpleDateFormat(TimeText.use24h ? "dd MMM, HH:mm:ss" : "dd MMM, hh:mm:ss a",
                Locale.ENGLISH);
        try {
            JSONArray arr = new JSONArray(p(c).getString(KEY, "[]"));
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject e = arr.getJSONObject(i);
                sb.append(f.format(new Date(e.getLong("t")))).append("\n   ").append(e.getString("m")).append("\n\n");
            }
        } catch (Exception ignored) {
            // show what we have
        }
        return sb.length() == 0 ? "Nothing in the log yet. Set an alarm and let it ring." : sb.toString().trim();
    }

    static synchronized void clear(Context c) {
        p(c).edit().remove(KEY).commit();
    }
}
