package com.bharti.alertalarm;

import org.json.JSONException;
import org.json.JSONObject;

/** One task, e.g. "On the 1st, from 11:00 AM to 11:30 AM: study Spring Security". */
public class Reminder {

    /** Alarm may fire a moment before the exact start; treat it as started. */
    static final long START_TOLERANCE_MS = 2_000L;

    public long id;
    public String task;
    public long startMillis;
    public long endMillis;
    public boolean done;
    /** Switched on/off from the dashboard (like the Clock app). */
    public boolean enabled = true;

    public Reminder(long id, String task, long startMillis, long endMillis) {
        this.id = id;
        this.task = task;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
    }

    /** True while the alarm must keep ringing (between start and end). */
    public boolean isActive(long now) {
        return enabled && !done && now + START_TOLERANCE_MS >= startMillis && now < endMillis;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("task", task);
        o.put("start", startMillis);
        o.put("end", endMillis);
        o.put("done", done);
        o.put("enabled", enabled);
        return o;
    }

    static Reminder fromJson(JSONObject o) throws JSONException {
        Reminder r = new Reminder(o.getLong("id"), o.getString("task"), o.getLong("start"), o.getLong("end"));
        r.done = o.optBoolean("done", false);
        r.enabled = o.optBoolean("enabled", true);
        return r;
    }
}
