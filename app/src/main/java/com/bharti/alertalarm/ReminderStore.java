package com.bharti.alertalarm;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Saves reminders on the phone (SharedPreferences as JSON). Small and simple, no database needed. */
final class ReminderStore {

    private static final String PREFS = "alert_alarm";
    private static final String KEY = "reminders";

    private ReminderStore() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized List<Reminder> getAll(Context c) {
        List<Reminder> list = new ArrayList<>();
        String json = prefs(c).getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(Reminder.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.e("ReminderStore", "Could not read reminders", e);
        }
        list.sort(Comparator.comparingLong(r -> r.startMillis));
        return list;
    }

    private static synchronized void saveAll(Context c, List<Reminder> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Reminder r : list) {
                arr.put(r.toJson());
            }
        } catch (JSONException e) {
            Log.e("ReminderStore", "Could not save reminders", e);
            return;
        }
        prefs(c).edit().putString(KEY, arr.toString()).commit();
    }

    static synchronized void save(Context c, Reminder reminder) {
        List<Reminder> list = getAll(c);
        list.removeIf(r -> r.id == reminder.id);
        list.add(reminder);
        saveAll(c, list);
    }

    static synchronized void delete(Context c, long id) {
        List<Reminder> list = getAll(c);
        list.removeIf(r -> r.id == id);
        saveAll(c, list);
    }

    static synchronized void markDone(Context c, long id) {
        List<Reminder> list = getAll(c);
        for (Reminder r : list) {
            if (r.id == id) {
                r.done = true;
            }
        }
        saveAll(c, list);
    }

    static synchronized void setEnabled(Context c, long id, boolean enabled) {
        List<Reminder> list = getAll(c);
        for (Reminder r : list) {
            if (r.id == id) {
                r.enabled = enabled;
            }
        }
        saveAll(c, list);
    }

    static synchronized Reminder find(Context c, long id) {
        for (Reminder r : getAll(c)) {
            if (r.id == id) {
                return r;
            }
        }
        return null;
    }

    /** Saves the answer to "Did you finish it?". Returns the task, or null if it was deleted. */
    static synchronized Reminder setResult(Context c, long id, long occurrenceStart, int result) {
        List<Reminder> list = getAll(c);
        for (Reminder r : list) {
            if (r.id == id) {
                r.result = result;
                r.resultFor = occurrenceStart;
                saveAll(c, list);
                return r;
            }
        }
        return null;
    }

    /** Emergency stop: the ringing tasks end now (one-time ones become done, repeating ones move on). */
    static synchronized List<Reminder> stopNow(Context c, long now) {
        List<Reminder> list = getAll(c);
        List<Reminder> stopped = new ArrayList<>();
        for (Reminder r : list) {
            if (r.isActive(now)) {
                r.endMillis = now;
                stopped.add(r);
            }
        }
        if (!stopped.isEmpty()) {
            saveAll(c, list);
        }
        return stopped;
    }

    static synchronized List<Reminder> getActive(Context c, long now) {
        List<Reminder> active = new ArrayList<>();
        for (Reminder r : getAll(c)) {
            if (r.isActive(now)) {
                active.add(r);
            }
        }
        return active;
    }

    /**
     * When an alarm's end time has passed: a one-time alarm becomes done, a repeating alarm moves to
     * its next occurrence (and is scheduled again). Returns the alarms that must ring now.
     */
    static synchronized List<Reminder> markExpiredAndGetActive(Context c, long now) {
        List<Reminder> all = getAll(c);
        List<Reminder> active = new ArrayList<>();
        List<Reminder> toSchedule = new ArrayList<>();
        boolean changed = false;
        for (Reminder r : all) {
            if (!r.done && now >= r.endMillis) {
                changed = true;
                if (r.isRepeating()) {
                    r.advance(now);
                    if (r.enabled) {
                        toSchedule.add(r);
                    }
                } else {
                    r.done = true;
                    continue;
                }
            }
            if (r.isActive(now)) {
                active.add(r);
            }
        }
        if (changed) {
            saveAll(c, all);
        }
        for (Reminder r : toSchedule) {
            AlarmScheduler.schedule(c, r);
        }
        return active;
    }
}
