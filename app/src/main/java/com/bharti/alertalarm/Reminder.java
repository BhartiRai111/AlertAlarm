package com.bharti.alertalarm;

import org.json.JSONException;
import org.json.JSONObject;

/** One task: "date 1 ko 11 AM se 11:30 AM tak yeh kaam yaad dilao". */
public class Reminder {

    /** Alarm may fire a moment before the exact start; treat it as started. */
    static final long START_TOLERANCE_MS = 2_000L;

    public long id;
    public String task;
    public long startMillis;
    public long endMillis;
    public boolean done;

    public Reminder(long id, String task, long startMillis, long endMillis) {
        this.id = id;
        this.task = task;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
    }

    /** True while the alarm must keep ringing (between start and end). */
    public boolean isActive(long now) {
        return !done && now + START_TOLERANCE_MS >= startMillis && now < endMillis;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("task", task);
        o.put("start", startMillis);
        o.put("end", endMillis);
        o.put("done", done);
        return o;
    }

    static Reminder fromJson(JSONObject o) throws JSONException {
        Reminder r = new Reminder(o.getLong("id"), o.getString("task"), o.getLong("start"), o.getLong("end"));
        r.done = o.optBoolean("done", false);
        return r;
    }
}
