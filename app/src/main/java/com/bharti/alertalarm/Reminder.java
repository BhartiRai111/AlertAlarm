package com.bharti.alertalarm;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

/**
 * One alarm. It can ring once, or repeat every day / month / year.
 * startMillis/endMillis always hold the CURRENT (or next) occurrence; for repeating alarms they move
 * forward after each occurrence ends.
 */
public class Reminder {

    /** Alarm may fire a moment before the exact start; treat it as started. */
    static final long START_TOLERANCE_MS = 2_000L;

    public enum Repeat { ONCE, DAILY, MONTHLY, YEARLY }

    public long id;
    public String task;
    public long startMillis;
    public long endMillis;
    public boolean done;
    /** Switched on/off from the dashboard (like the Clock app). */
    public boolean enabled = true;

    public Repeat repeat = Repeat.ONCE;
    public int hour;          // 0-23
    public int minute;        // 0-59
    public int dayOfMonth;    // 1-31, used by MONTHLY and YEARLY
    public int month;         // 0-11, used by YEARLY
    public long durationMs;
    /** Answer to "Did you finish it?" for the occurrence that started at resultFor (TaskCheck constants). */
    public int result;
    public long resultFor;

    public Reminder(long id, String task, long startMillis, long endMillis) {
        this.id = id;
        this.task = task;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.durationMs = endMillis - startMillis;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startMillis);
        this.hour = c.get(Calendar.HOUR_OF_DAY);
        this.minute = c.get(Calendar.MINUTE);
        this.dayOfMonth = c.get(Calendar.DAY_OF_MONTH);
        this.month = c.get(Calendar.MONTH);
    }

    /** True while the alarm must keep ringing (between start and end). */
    public boolean isActive(long now) {
        return enabled && !done && now + START_TOLERANCE_MS >= startMillis && now < endMillis;
    }

    public boolean isRepeating() {
        return repeat != Repeat.ONCE;
    }

    void moveTo(long start) {
        startMillis = start;
        endMillis = start + durationMs;
    }

    /**
     * Earliest start of this alarm at or after notBefore.
     * Monthly on the 31st rings on the last day in shorter months; yearly on 29 Feb rings on 28 Feb.
     */
    long occurrenceOnOrAfter(long notBefore) {
        if (repeat == Repeat.ONCE) {
            return startMillis;
        }
        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(notBefore);
        base.set(Calendar.SECOND, 0);
        base.set(Calendar.MILLISECOND, 0);

        if (repeat == Repeat.DAILY) {
            Calendar c = (Calendar) base.clone();
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            if (c.getTimeInMillis() < notBefore) {
                c.add(Calendar.DAY_OF_MONTH, 1);
            }
            return c.getTimeInMillis();
        }

        for (int i = 0; i < 3; i++) {
            Calendar c = (Calendar) base.clone();
            c.set(Calendar.DAY_OF_MONTH, 1);
            if (repeat == Repeat.MONTHLY) {
                c.add(Calendar.MONTH, i);
            } else {
                c.add(Calendar.YEAR, i);
                c.set(Calendar.MONTH, month);
            }
            c.set(Calendar.DAY_OF_MONTH, Math.min(dayOfMonth, c.getActualMaximum(Calendar.DAY_OF_MONTH)));
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            if (c.getTimeInMillis() >= notBefore) {
                return c.getTimeInMillis();
            }
        }
        return startMillis; // not reached
    }

    /** After an occurrence ends: jump to the next one whose ringing window is still ahead. */
    void advance(long now) {
        long notBefore = Math.max(startMillis + 1_000L, now - durationMs + 1);
        moveTo(occurrenceOnOrAfter(notBefore));
    }

    /** "Once", "Every day", "Every month on the 5th", "Every year on 5 Nov". */
    String repeatLabel() {
        switch (repeat) {
            case DAILY:
                return "Every day";
            case MONTHLY:
                return "Every month on the " + ordinal(dayOfMonth);
            case YEARLY:
                return "Every year on " + dayOfMonth + " "
                        + new DateFormatSymbols(Locale.ENGLISH).getShortMonths()[month];
            default:
                return "Once";
        }
    }

    static String ordinal(int n) {
        if (n % 100 >= 11 && n % 100 <= 13) {
            return n + "th";
        }
        switch (n % 10) {
            case 1:
                return n + "st";
            case 2:
                return n + "nd";
            case 3:
                return n + "rd";
            default:
                return n + "th";
        }
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("task", task);
        o.put("start", startMillis);
        o.put("end", endMillis);
        o.put("done", done);
        o.put("enabled", enabled);
        o.put("repeat", repeat.name());
        o.put("hour", hour);
        o.put("minute", minute);
        o.put("day", dayOfMonth);
        o.put("month", month);
        o.put("duration", durationMs);
        o.put("result", result);
        o.put("result_for", resultFor);
        return o;
    }

    static Reminder fromJson(JSONObject o) throws JSONException {
        Reminder r = new Reminder(o.getLong("id"), o.getString("task"), o.getLong("start"), o.getLong("end"));
        r.done = o.optBoolean("done", false);
        r.enabled = o.optBoolean("enabled", true);
        // Older saved alarms have no repeat fields: the constructor already filled them from start/end
        try {
            r.repeat = Repeat.valueOf(o.optString("repeat", "ONCE"));
        } catch (IllegalArgumentException e) {
            r.repeat = Repeat.ONCE;
        }
        r.hour = o.optInt("hour", r.hour);
        r.minute = o.optInt("minute", r.minute);
        r.dayOfMonth = o.optInt("day", r.dayOfMonth);
        r.month = o.optInt("month", r.month);
        r.durationMs = o.optLong("duration", r.durationMs);
        r.result = o.optInt("result", 0);
        r.resultFor = o.optLong("result_for", 0);
        return r;
    }
}
