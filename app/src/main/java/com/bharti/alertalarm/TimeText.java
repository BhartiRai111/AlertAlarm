package com.bharti.alertalarm;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Small helpers to show times and durations in English. */
final class TimeText {

    private TimeText() {
    }

    /** "11:30 AM", or "11:30:45 AM" when the time has seconds. */
    static String time(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        String pattern = c.get(Calendar.SECOND) == 0 ? "hh:mm a" : "hh:mm:ss a";
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

    static boolean sameDay(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }
}
