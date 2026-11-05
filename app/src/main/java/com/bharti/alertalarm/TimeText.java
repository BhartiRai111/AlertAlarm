package com.bharti.alertalarm;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Small helpers to show times and durations in English. */
final class TimeText {

    /** 24-hour clock (14:30) instead of 12-hour (02:30 PM), from Settings. */
    static volatile boolean use24h = false;

    private TimeText() {
    }

    /** Minutes after midnight as a clock time, e.g. 870 -> "02:30 PM" or "14:30". */
    static String clock(int minutes) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, (minutes / 60) % 24);
        c.set(Calendar.MINUTE, minutes % 60);
        c.set(Calendar.SECOND, 0);
        return time(c.getTimeInMillis());
    }

    /** "11:30 AM", or "11:30:45 AM" when the time has seconds. */
    static String time(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        boolean seconds = c.get(Calendar.SECOND) != 0;
        String pattern = use24h ? (seconds ? "HH:mm:ss" : "HH:mm") : (seconds ? "hh:mm:ss a" : "hh:mm a");
        return new SimpleDateFormat(pattern, Locale.ENGLISH).format(new Date(millis));
    }

    /** "1 hr 5 min 30 sec", "30 min", "45 sec". */
    static String duration(long millis) {
        long total = millis / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) {
            sb.append(h).append(" hr ");
        }
        if (m > 0) {
            sb.append(m).append(" min ");
        }
        if (s > 0 || sb.length() == 0) {
            sb.append(s).append(" sec");
        }
        return sb.toString().trim();
    }

    /** "Today", "Tomorrow" or "Thu, 06 Nov". */
    static String dayLabel(long millis) {
        long now = System.currentTimeMillis();
        if (sameDay(now, millis)) {
            return "Today";
        }
        if (sameDay(now + 24L * 60 * 60 * 1000, millis)) {
            return "Tomorrow";
        }
        return new SimpleDateFormat("EEE, dd MMM", Locale.ENGLISH).format(new Date(millis));
    }

    /** "2 hr 5 min", "5 min", "less than a minute". */
    static String until(long millis) {
        long minutes = (millis - System.currentTimeMillis()) / 60_000;
        if (minutes < 1) {
            return "less than a minute";
        }
        long d = minutes / (60 * 24);
        long h = (minutes % (60 * 24)) / 60;
        long m = minutes % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append(d == 1 ? " day " : " days ");
        }
        if (h > 0) {
            sb.append(h).append(" hr ");
        }
        if (m > 0 && d == 0) {
            sb.append(m).append(" min");
        }
        return sb.toString().trim();
    }

    static boolean sameDay(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }
}
