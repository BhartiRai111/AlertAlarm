package com.bharti.alertalarm;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Shared look (colors, rows, big times) and calendar math for all screens. */
final class Ui {

    private Ui() {
    }

    static int color(Context c, int res) {
        return ContextCompat.getColor(c, res);
    }

    /** "07:00 PM" with a smaller "PM", like the design. */
    static CharSequence bigTime(long millis) {
        String t = TimeText.time(millis);
        SpannableString s = new SpannableString(t);
        int space = t.lastIndexOf(' ');
        if (space > 0) {
            s.setSpan(new RelativeSizeSpan(0.42f), space, t.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    /** Segment button (Once/Daily/...) and filter chips: turquoise when selected. */
    static void segment(TextView v, boolean selected) {
        Context c = v.getContext();
        v.setBackgroundResource(selected ? R.drawable.bg_segment_selected : 0);
        v.setTextColor(color(c, selected ? R.color.on_accent : R.color.soft_text));
        v.setSelected(selected);
    }

    static void chip(TextView v, boolean selected) {
        Context c = v.getContext();
        v.setBackgroundResource(selected ? R.drawable.bg_pill_accent : R.drawable.bg_chip);
        v.setTextColor(color(c, selected ? R.color.on_accent : R.color.soft_text));
        v.setSelected(selected);
    }

    static void tintStart(TextView v, int drawableRes, int colorRes) {
        android.graphics.drawable.Drawable d = ContextCompat.getDrawable(v.getContext(), drawableRes);
        if (d != null) {
            int size = (int) (14 * v.getResources().getDisplayMetrics().density);
            d = d.mutate();
            d.setBounds(0, 0, size, size);
            d.setTint(color(v.getContext(), colorRes));
        }
        v.setCompoundDrawablesRelative(d, null, null, null);
    }

    // ------------------------------------------------------------------ alarm row

    /** Inflates one alarm card (icon, title, meta). Callers turn on the time / switch / chevron they need. */
    static View alarmRow(ViewGroup parent, Reminder r, long now, String meta) {
        Context c = parent.getContext();
        View row = LayoutInflater.from(c).inflate(R.layout.item_alarm, parent, false);
        ImageView icon = row.findViewById(R.id.itemIcon);
        TextView title = row.findViewById(R.id.itemTitle);
        TextView metaView = row.findViewById(R.id.itemMeta);

        boolean active = r.isActive(now);
        boolean dim = r.done || !r.enabled;
        int iconRes = r.done ? R.drawable.ic_check
                : active ? R.drawable.ic_bell
                : r.isRepeating() ? R.drawable.ic_repeat : R.drawable.ic_clock;
        icon.setImageResource(iconRes);
        icon.setBackgroundResource(dim ? R.drawable.bg_icon_done : R.drawable.bg_icon_accent);
        icon.setImageTintList(ColorStateList.valueOf(color(c, dim ? R.color.muted : R.color.accent)));

        title.setText(r.task);
        title.setTextColor(color(c, dim ? R.color.muted : R.color.text));
        if (r.done) {
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        metaView.setText(meta);
        if (active) {
            metaView.setTextColor(color(c, R.color.accent));
        }
        return row;
    }

    /** "Every day · 07:00 PM · 20 min", "Today · 09:00 PM · 15 min", "Done · Today, 12:00 PM". */
    static String meta(Reminder r, long now) {
        if (r.done) {
            return "Done · " + TimeText.dayLabel(r.startMillis) + ", " + TimeText.time(r.startMillis);
        }
        if (r.isActive(now)) {
            return "Ringing now · locked until " + TimeText.time(r.endMillis);
        }
        String when = r.isRepeating() ? r.repeatLabel() : TimeText.dayLabel(r.startMillis);
        String s = when + " · " + TimeText.time(r.startMillis) + " · " + TimeText.duration(r.durationMs);
        return r.enabled ? s : s + " · Off";
    }

    // ------------------------------------------------------------------ calendar math

    static long startOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    static long nextDay(long dayStart) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayStart);
        c.add(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    /** Start time of this alarm on the given day, or -1 if it does not ring that day. */
    static long occursOn(Reminder r, long dayStart) {
        long dayEnd = nextDay(dayStart);
        if (!r.isRepeating()) {
            return r.startMillis >= dayStart && r.startMillis < dayEnd ? r.startMillis : -1;
        }
        // Repeating: only from the day of its current (next) occurrence onwards
        if (dayStart < startOfDay(r.startMillis)) {
            return -1;
        }
        if (dayStart == startOfDay(r.startMillis)) {
            return r.startMillis;
        }
        long t = r.occurrenceOnOrAfter(dayStart);
        return t >= dayStart && t < dayEnd ? t : -1;
    }

    /** One alarm on one day. */
    static final class Entry {
        final Reminder reminder;
        final long start;

        Entry(Reminder reminder, long start) {
            this.reminder = reminder;
            this.start = start;
        }
    }

    static List<Entry> entriesOn(List<Reminder> all, long dayStart) {
        List<Entry> list = new ArrayList<>();
        for (Reminder r : all) {
            long t = occursOn(r, dayStart);
            if (t >= 0) {
                list.add(new Entry(r, t));
            }
        }
        list.sort((a, b) -> Long.compare(a.start, b.start));
        return list;
    }

    /** The next few start times of an alarm (just one for a one-time alarm). */
    static List<Long> nextStarts(Reminder r, int count) {
        List<Long> list = new ArrayList<>();
        long t = r.startMillis;
        list.add(t);
        if (!r.isRepeating()) {
            return list;
        }
        for (int i = 1; i < count; i++) {
            t = r.occurrenceOnOrAfter(t + 60_000L);
            list.add(t);
        }
        return list;
    }
}
