package com.bharti.alertalarm;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Saves the four volume modes, and the phone's own volume while a mode is changing it. */
final class VolumeStore {

    private static final String PREFS = "alert_alarm_volume";
    private static final String KEY = "profiles";

    private VolumeStore() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Always all four modes, in order (missing ones get their default levels). */
    static synchronized List<VolumeProfile> getAll(Context c) {
        VolumeProfile[] byMode = new VolumeProfile[VolumeProfile.Mode.values().length];
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                try {
                    VolumeProfile p = VolumeProfile.fromJson(arr.getJSONObject(i));
                    byMode[p.mode.ordinal()] = p;
                } catch (IllegalArgumentException | JSONException ignored) {
                    // unknown mode in an old save
                }
            }
        } catch (JSONException e) {
            Log.e("VolumeStore", "Could not read volume modes", e);
        }
        List<VolumeProfile> list = new ArrayList<>();
        for (VolumeProfile.Mode m : VolumeProfile.Mode.values()) {
            list.add(byMode[m.ordinal()] != null ? byMode[m.ordinal()] : VolumeProfile.defaults(m));
        }
        return list;
    }

    static synchronized void saveAll(Context c, List<VolumeProfile> list) {
        JSONArray arr = new JSONArray();
        try {
            for (VolumeProfile p : list) {
                arr.put(p.toJson());
            }
        } catch (JSONException e) {
            return;
        }
        prefs(c).edit().putString(KEY, arr.toString()).commit();
    }

    static synchronized void save(Context c, VolumeProfile profile) {
        List<VolumeProfile> list = getAll(c);
        list.set(profile.mode.ordinal(), profile);
        saveAll(c, list);
    }
}
