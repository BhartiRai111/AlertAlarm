package com.bharti.alertalarm;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * One volume mode (Meeting, Travel, Prayer, Office): the levels it sets for ringtone, notification,
 * call and media (never the alarm), switched on by hand and/or by an optional time range.
 */
public class VolumeProfile {

    public enum Mode {
        MEETING("Meeting", 0, 0, 30, 0),
        TRAVEL("Travel", 100, 100, 100, 100),
        PRAYER("Prayer", 20, 20, 50, 0),
        OFFICE("Office", 40, 40, 70, 30);

        final String title;
        final int ring;
        final int notification;
        final int call;
        final int media;

        Mode(String title, int ring, int notification, int call, int media) {
            this.title = title;
            this.ring = ring;
            this.notification = notification;
            this.call = call;
            this.media = media;
        }
    }

    public Mode mode;
    /** Levels in percent (0-100). Ringtone 0 = vibrate. */
    public int ring;
    public int notification;
    public int call;
    public int media;

    /** Switched on by hand, and when (the latest switched-on mode wins). */
    public boolean manualOn;
    public long onSince;

    /** Optional time range that switches the mode on by itself. */
    public boolean timed;
    public boolean daily = true;
    public int year;
    public int month;
    public int day;
    public int startMinutes = 9 * 60;
    public int endMinutes = 10 * 60;
    /** Switched off by hand during its time range: that occurrence is skipped until this time. */
    public long skipUntil;

    static VolumeProfile defaults(Mode mode) {
        VolumeProfile p = new VolumeProfile();
        p.mode = mode;
        p.resetLevels();
        Calendar c = Calendar.getInstance();
        p.year = c.get(Calendar.YEAR);
        p.month = c.get(Calendar.MONTH);
        p.day = c.get(Calendar.DAY_OF_MONTH);
        return p;
    }

    void resetLevels() {
        ring = mode.ring;
        notification = mode.notification;
        call = mode.call;
        media = mode.media;
    }

    long id() {
        return mode.ordinal() + 1;
    }

    /** "Ring 0 · Notif 0 · Call 30 · Media 0" */
    String levelsText() {
        return (ring == 0 ? "Vibrate" : "Ring " + ring + "%") + " · Notif " + notification + "% · Call "
                + call + "% · Media " + media + "%";
    }

    /** Changes when the levels change, so an active mode is applied again after editing. */
    String signature() {
        return mode.name() + ":" + ring + ":" + notification + ":" + call + ":" + media;
    }

    // ------------------------------------------------------------------ time range

    private long startOn(long dayStart) {
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
            minutes += 24 * 60;
        }
        return minutes * 60_000L;
    }

    private long onceStart() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, day, startMinutes / 60, startMinutes % 60);
        return c.getTimeInMillis();
    }

    /** {start, end} of the time range happening now, or null. */
    long[] windowAt(long now) {
        if (!timed) {
            return null;
        }
        if (!daily) {
            long s = onceStart();
            return now >= s && now < s + durationMs() && s + durationMs() > skipUntil
                    ? new long[]{s, s + durationMs()} : null;
        }
        long today = Ui.startOfDay(now);
        Calendar y = Calendar.getInstance();
        y.setTimeInMillis(today);
        y.add(Calendar.DAY_OF_MONTH, -1);
        for (long dayStart : new long[]{today, y.getTimeInMillis()}) {
            long s = startOn(dayStart);
            if (now >= s && now < s + durationMs() && s + durationMs() > skipUntil) {
                return new long[]{s, s + durationMs()};
            }
        }
        return null;
    }

    long nextStart(long now) {
        if (!timed) {
            return -1;
        }
        if (!daily) {
            long s = onceStart();
            return s > now ? s : -1;
        }
        long s = startOn(Ui.startOfDay(now));
        return s > now ? s : startOn(Ui.nextDay(Ui.startOfDay(now)));
    }

    boolean timeIsOver(long now) {
        return timed && !daily && now >= onceStart() + durationMs();
    }

    String timeText() {
        long base = startOn(Ui.startOfDay(System.currentTimeMillis()));
        return TimeText.time(base) + " – " + TimeText.time(base + durationMs()) + " · "
                + (daily ? "Every day" : TimeText.dayLabel(onceStart()));
    }

    // ------------------------------------------------------------------ JSON

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("mode", mode.name());
        o.put("ring", ring);
        o.put("notification", notification);
        o.put("call", call);
        o.put("media", media);
        o.put("manual_on", manualOn);
        o.put("on_since", onSince);
        o.put("timed", timed);
        o.put("daily", daily);
        o.put("year", year);
        o.put("month", month);
        o.put("day", day);
        o.put("start", startMinutes);
        o.put("end", endMinutes);
        o.put("skip_until", skipUntil);
        return o;
    }

    static VolumeProfile fromJson(JSONObject o) throws JSONException {
        Mode mode = Mode.valueOf(o.getString("mode"));
        VolumeProfile p = defaults(mode);
        p.ring = o.optInt("ring", mode.ring);
        p.notification = o.optInt("notification", mode.notification);
        p.call = o.optInt("call", mode.call);
        p.media = o.optInt("media", mode.media);
        p.manualOn = o.optBoolean("manual_on", false);
        p.onSince = o.optLong("on_since", 0);
        p.timed = o.optBoolean("timed", false);
        p.daily = o.optBoolean("daily", true);
        p.year = o.optInt("year", p.year);
        p.month = o.optInt("month", p.month);
        p.day = o.optInt("day", p.day);
        p.startMinutes = o.optInt("start", p.startMinutes);
        p.endMinutes = o.optInt("end", p.endMinutes);
        p.skipUntil = o.optLong("skip_until", 0);
        return p;
    }
}
