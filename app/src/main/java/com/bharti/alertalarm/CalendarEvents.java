package com.bharti.alertalarm;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Events from the phone's calendars (Google Calendar etc.), read-only, for the Calendar tab. */
final class CalendarEvents {

    static final class Event {
        String title;
        long begin;
        long end;
        boolean allDay;
        String calendar;
    }

    private CalendarEvents() {
    }

    static boolean hasPermission(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean enabled(Context c) {
        return AppSettings.calendarSync(c) && hasPermission(c);
    }

    /** Events that start between from and to, oldest first. Empty if not allowed. */
    static List<Event> between(Context c, long from, long to) {
        List<Event> list = new ArrayList<>();
        if (!enabled(c)) {
            return list;
        }
        Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri, from - 24L * 60 * 60 * 1000);   // all-day events are in UTC
        ContentUris.appendId(uri, to + 24L * 60 * 60 * 1000);
        String[] cols = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME};
        try (Cursor cur = c.getContentResolver().query(uri.build(), cols, null, null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            while (cur != null && cur.moveToNext()) {
                Event e = new Event();
                e.title = cur.getString(0) == null || cur.getString(0).trim().isEmpty() ? "(No title)" : cur.getString(0);
                e.begin = cur.getLong(1);
                e.end = cur.getLong(2);
                e.allDay = cur.getInt(3) == 1;
                e.calendar = cur.getString(4);
                if (e.allDay) {
                    e.begin = utcDayToLocal(e.begin);
                    e.end = e.begin + 24L * 60 * 60 * 1000;
                }
                if (e.begin >= from && e.begin < to) {
                    list.add(e);
                }
            }
        } catch (SecurityException | IllegalArgumentException ex) {
            AlarmLog.error(c, "Could not read the calendar", ex);
        }
        return list;
    }

    /** All-day events are stored as UTC midnight: turn that into local midnight of the same date. */
    private static long utcDayToLocal(long utcMillis) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
        return local.getTimeInMillis();
    }

    /** Start / length for an alarm made from an event (all-day: 9 AM for 30 minutes). */
    static long alarmStart(Event e) {
        return e.allDay ? e.begin + 9L * 60 * 60 * 1000 : e.begin;
    }

    static long alarmDuration(Event e) {
        if (e.allDay) {
            return 30 * 60_000L;
        }
        long d = e.end - e.begin;
        return Math.max(5 * 60_000L, Math.min(d, 12L * 60 * 60 * 1000));
    }
}
