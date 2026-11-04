package com.bharti.alertalarm.imports;

import java.util.Calendar;

/** One task read from an imported file, before it becomes an alarm. Plain Java (no Android), so it can be tested. */
public final class ImportItem {

    public enum Repeat { ONCE, DAILY, MONTHLY, YEARLY }

    public String task;
    public Repeat repeat = Repeat.ONCE;
    /** Date from the file; -1 when the file had no date (then today is used). */
    public int year = -1;
    public int month = -1;   // 0-11
    public int day = -1;     // 1-31
    /** Minutes after midnight. */
    public int startMinutes;
    /** Minutes after midnight; -1 when the file had no end time. */
    public int endMinutes = -1;

    /** Ticked in the preview = will be set as an alarm. */
    public boolean checked = true;
    /** Short note shown in the preview (e.g. "Already over"). */
    public String note;

    public boolean hasDate() {
        return year >= 0;
    }

    public void setDate(Calendar c) {
        year = c.get(Calendar.YEAR);
        month = c.get(Calendar.MONTH);
        day = c.get(Calendar.DAY_OF_MONTH);
    }

    /** Start of the alarm on its date (today when there is no date). */
    public long startMillis(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        if (hasDate()) {
            c.set(Calendar.YEAR, year);
            c.set(Calendar.MONTH, month);
            c.set(Calendar.DAY_OF_MONTH, Math.min(day, c.getActualMaximum(Calendar.DAY_OF_MONTH)));
        }
        c.set(Calendar.HOUR_OF_DAY, startMinutes / 60);
        c.set(Calendar.MINUTE, startMinutes % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** How long it rings. No end time = 30 minutes. An end before the start means the next day. */
    public long durationMs() {
        if (endMinutes < 0) {
            return 30 * 60_000L;
        }
        int minutes = endMinutes - startMinutes;
        if (minutes <= 0) {
            minutes += 24 * 60;
        }
        return minutes * 60_000L;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ENGLISH, "%s | %s | %02d:%02d-%s | %s",
                hasDate() ? String.format(java.util.Locale.ENGLISH, "%02d-%02d-%04d", day, month + 1, year) : "(no date)",
                repeat, startMinutes / 60, startMinutes % 60,
                endMinutes < 0 ? "(no end)" : String.format(java.util.Locale.ENGLISH, "%02d:%02d", endMinutes / 60, endMinutes % 60),
                task);
    }
}
