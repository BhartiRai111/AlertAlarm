package com.bharti.alertalarm;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

/** A time range in which the phone's ringtone volume is changed (Meeting / Travel / Prayer). */
public class VolumeSchedule {

    public enum Mode {
        MEETING("Meeting", "Vibrate only"),
        TRAVEL("Travel", "Full volume"),
        PRAYER("Prayer", "Low volume");

        final String title;
        final String effect;

        Mode(String title, String effect) {
            this.title = title;
            this.effect = effect;
        }
    }

    public long id;
    public Mode mode = Mode.MEETING;
    public boolean daily;
    /** Date for a one-time schedule. */
    public int year;
    public int month;
    public int day;
    public int startMinutes;
    public int endMinutes;
    public boolean enabled = true;

    /** Start of the occurrence that begins on the given day (midnight millis). */
    long startOn(long dayStart) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayStart);
        c.set(Calendar.HOUR_OF_DAY, startMinutes / 60);
        c.set(Calendar.MINUTE, startMinutes % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    long durationMs() {
        int minutes = endMinutes - startMinutes;
        if (minutes <= 0) {
            minutes += 24 * 60;   // ends after midnight
        }
        return minutes * 60_000L;
    }

    private long onceStart() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, day, startMinutes / 60, startMinutes % 60);
        return c.getTimeInMillis();
    }

    /** {start, end} of the occurrence happening at 'now', or null. */
    long[] windowAt(long now) {
        if (!enabled) {
            return null;
        }
        if (!daily) {
            long s = onceStart();
            return now >= s && now < s + durationMs() ? new long[]{s, s + durationMs()} : null;
        }
        long today = Ui.startOfDay(now);
        Calendar y = Calendar.getInstance();
        y.setTimeInMillis(today);
        y.add(Calendar.DAY_OF_MONTH, -1);
        for (long dayStart : new long[]{today, y.getTimeInMillis()}) {
            long s = startOn(dayStart);
            if (now >= s && now < s + durationMs()) {
                return new long[]{s, s + durationMs()};
            }
        }
        return null;
    }

    /** Next start after 'now', or -1 if it will not start again. */
    long nextStart(long now) {
        if (!enabled) {
            return -1;
        }
        if (!daily) {
            long s = onceStart();
            return s > now ? s : -1;
        }
        long s = startOn(Ui.startOfDay(now));
        return s > now ? s : startOn(Ui.nextDay(Ui.startOfDay(now)));
    }

    /** One-time schedule whose time is fully over. */
    boolean isOver(long now) {
        return !daily && now >= onceStart() + durationMs();
    }

    String timeRange() {
        long base = startOn(Ui.startOfDay(System.currentTimeMillis()));
        return TimeText.time(base) + " – " + TimeText.time(base + durationMs());
    }

    String whenLabel() {
        if (daily) {
            return "Every day";
        }
        return TimeText.dayLabel(onceStart());
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("mode", mode.name());
        o.put("daily", daily);
        o.put("year", year);
        o.put("month", month);
        o.put("day", day);
        o.put("start", startMinutes);
        o.put("end", endMinutes);
        o.put("enabled", enabled);
        return o;
    }

    static VolumeSchedule fromJson(JSONObject o) throws JSONException {
        VolumeSchedule v = new VolumeSchedule();
        v.id = o.getLong("id");
        try {
            v.mode = Mode.valueOf(o.optString("mode", "MEETING"));
        } catch (IllegalArgumentException e) {
            v.mode = Mode.MEETING;
        }
        v.daily = o.optBoolean("daily", false);
        v.year = o.optInt("year");
        v.month = o.optInt("month");
        v.day = o.optInt("day");
        v.startMinutes = o.optInt("start");
        v.endMinutes = o.optInt("end");
        v.enabled = o.optBoolean("enabled", true);
        return v;
    }
}
