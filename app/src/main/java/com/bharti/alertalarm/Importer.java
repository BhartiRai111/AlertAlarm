package com.bharti.alertalarm;

import com.bharti.alertalarm.imports.ImportItem;

import java.util.Calendar;
import java.util.List;

/** Checks imported tasks (notes for the preview) and turns them into real alarms. */
final class Importer {

    private Importer() {
    }

    /** Fills missing dates with today and writes a short note on anything the user should know. */
    static void prepare(List<ImportItem> items, long now) {
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(now);
        for (ImportItem item : items) {
            check(item, today, now, true);
        }
    }

    /** Re-check one task after it was edited. autoTick=false keeps the user's tick. */
    static void check(ImportItem item, Calendar today, long now, boolean autoTick) {
        if (!item.hasDate()) {
            item.setDate(today);
        }
        StringBuilder note = new StringBuilder();
        if (item.endMinutes < 0) {
            note.append("No end time: rings for 30 min. ");
        }
        if (item.repeat == ImportItem.Repeat.ONCE) {
            long start = item.startMillis(now);
            long end = start + item.durationMs();
            if (end <= now) {
                note.append("This time is already over. ");
                if (autoTick) {
                    item.checked = false;
                }
            } else if (start <= now) {
                note.append("Starts now: it will ring right away. ");
            }
        }
        item.note = note.length() == 0 ? null : note.toString().trim();
    }

    /** "Thu, 05 Nov · 11:00 AM – 12:00 PM · Once" */
    static String describe(ImportItem item, long now) {
        long start = item.startMillis(now);
        String when = item.repeat == ImportItem.Repeat.ONCE ? TimeText.dayLabel(start)
                : toReminder(item, 0, now).repeatLabel();
        String end = item.endMinutes < 0 ? "30 min" : TimeText.time(start + item.durationMs());
        return when + " · " + TimeText.time(start) + " – " + end;
    }

    static Reminder toReminder(ImportItem item, long id, long now) {
        long start = item.startMillis(now);
        long duration = item.durationMs();
        Reminder r = new Reminder(id, item.task, start, start + duration);
        r.repeat = Reminder.Repeat.valueOf(item.repeat.name());
        r.durationMs = duration;
        if (r.isRepeating()) {
            r.moveTo(r.occurrenceOnOrAfter(now - 60_000L));
        }
        return r;
    }
}
