package com.bharti.alertalarm;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Saves volume schedules, and the phone's own volume while a schedule is changing it. */
final class VolumeStore {

    private static final String PREFS = "alert_alarm_volume";
    private static final String KEY = "schedules";

    private VolumeStore() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized List<VolumeSchedule> getAll(Context c) {
        List<VolumeSchedule> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                list.add(VolumeSchedule.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.e("VolumeStore", "Could not read schedules", e);
        }
        list.sort((a, b) -> Integer.compare(a.startMinutes, b.startMinutes));
        return list;
    }

    static synchronized void saveAll(Context c, List<VolumeSchedule> list) {
        JSONArray arr = new JSONArray();
        try {
            for (VolumeSchedule v : list) {
                arr.put(v.toJson());
            }
        } catch (JSONException e) {
            return;
        }
        prefs(c).edit().putString(KEY, arr.toString()).commit();
    }

    static synchronized void save(Context c, VolumeSchedule schedule) {
        List<VolumeSchedule> list = getAll(c);
        list.removeIf(v -> v.id == schedule.id);
        list.add(schedule);
        saveAll(c, list);
    }

    static synchronized void delete(Context c, long id) {
        List<VolumeSchedule> list = getAll(c);
        list.removeIf(v -> v.id == id);
        saveAll(c, list);
    }
}
