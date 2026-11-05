package com.bharti.alertalarm;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** The Volume tab: what is active now, the three modes, and the list of schedules. */
final class VolumePage {

    private final AppCompatActivity activity;
    private final View banner;
    private final ImageView nowIcon;
    private final TextView nowTitle;
    private final TextView nowSub;
    private final LinearLayout list;
    private final View empty;

    VolumePage(AppCompatActivity activity) {
        this.activity = activity;
        banner = activity.findViewById(R.id.dndBanner);
        nowIcon = activity.findViewById(R.id.volumeNowIcon);
        nowTitle = activity.findViewById(R.id.volumeNowTitle);
        nowSub = activity.findViewById(R.id.volumeNowSub);
        list = activity.findViewById(R.id.volumeList);
        empty = activity.findViewById(R.id.volumeEmpty);

        activity.findViewById(R.id.dndFix).setOnClickListener(v -> openDndSettings(activity));
        activity.findViewById(R.id.presetMeeting).setOnClickListener(v -> openEditor(null, VolumeSchedule.Mode.MEETING));
        activity.findViewById(R.id.presetTravel).setOnClickListener(v -> openEditor(null, VolumeSchedule.Mode.TRAVEL));
        activity.findViewById(R.id.presetPrayer).setOnClickListener(v -> openEditor(null, VolumeSchedule.Mode.PRAYER));
    }

    static void openDndSettings(AppCompatActivity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "Open Settings > Apps > Special app access > Do Not Disturb, and allow AlertAlarm.",
                    Toast.LENGTH_LONG).show();
        }
    }

    void render() {
        VolumeManager.reconcile(activity);
        List<VolumeSchedule> all = VolumeStore.getAll(activity);
        banner.setVisibility(VolumeManager.hasDndAccess(activity) ? View.GONE : View.VISIBLE);

        VolumeSchedule active = VolumeManager.active(activity);
        if (active != null) {
            nowIcon.setImageResource(icon(active.mode));
            nowTitle.setText(active.mode.title + " mode");
            nowSub.setText(active.mode.effect + " until " + TimeText.time(VolumeManager.activeEnd(activity)));
        } else {
            nowIcon.setImageResource(R.drawable.ic_volume);
            nowTitle.setText("Normal");
            nowSub.setText("Your phone's own volume");
        }

        list.removeAllViews();
        long now = System.currentTimeMillis();
        LayoutInflater inflater = LayoutInflater.from(activity);
        for (VolumeSchedule v : all) {
            View row = inflater.inflate(R.layout.item_alarm, list, false);
            ImageView ic = row.findViewById(R.id.itemIcon);
            TextView title = row.findViewById(R.id.itemTitle);
            TextView meta = row.findViewById(R.id.itemMeta);
            boolean on = v.enabled;
            boolean isActive = active != null && active.id == v.id;
            ic.setImageResource(icon(v.mode));
            ic.setBackgroundResource(on ? R.drawable.bg_icon_accent : R.drawable.bg_icon_done);
            ic.setImageTintList(ColorStateList.valueOf(Ui.color(activity, on ? R.color.accent : R.color.muted)));
            title.setText(v.mode.title + " · " + v.mode.effect);
            title.setTextColor(Ui.color(activity, on ? R.color.text : R.color.muted));
            String m = v.timeRange() + " · " + v.whenLabel();
            if (isActive) {
                m = "On now · " + m;
                meta.setTextColor(Ui.color(activity, R.color.accent));
            } else if (!on) {
                m = m + (v.isOver(now) ? " · Done" : " · Off");
            }
            meta.setText(m);

            MaterialSwitch sw = row.findViewById(R.id.itemSwitch);
            sw.setVisibility(View.VISIBLE);
            sw.setChecked(on);
            sw.setOnCheckedChangeListener((b, checked) -> {
                if (checked && v.isOver(System.currentTimeMillis())) {
                    Toast.makeText(activity, "This time is over. Tap it to pick a new date.", Toast.LENGTH_SHORT).show();
                    b.setChecked(false);
                    return;
                }
                v.enabled = checked;
                VolumeStore.save(activity, v);
                render();
            });
            row.setOnClickListener(x -> openEditor(v, v.mode));
            row.setOnLongClickListener(x -> {
                new AlertDialog.Builder(activity)
                        .setTitle("Delete this schedule?")
                        .setMessage(v.mode.title + " · " + v.timeRange())
                        .setPositiveButton("Delete", (d, w) -> {
                            VolumeStore.delete(activity, v.id);
                            render();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });
            list.addView(row);
        }
        empty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
    }

    static int icon(VolumeSchedule.Mode mode) {
        switch (mode) {
            case MEETING:
                return R.drawable.ic_vibrate;
            case PRAYER:
                return R.drawable.ic_volume_low;
            default:
                return R.drawable.ic_volume;
        }
    }

    // ------------------------------------------------------------------ add / edit

    /** existing = null for a new schedule. */
    void openEditor(VolumeSchedule existing, VolumeSchedule.Mode mode) {
        VolumeSchedule v = new VolumeSchedule();
        if (existing != null) {
            v.id = existing.id;
            v.mode = existing.mode;
            v.daily = existing.daily;
            v.year = existing.year;
            v.month = existing.month;
            v.day = existing.day;
            v.startMinutes = existing.startMinutes;
            v.endMinutes = existing.endMinutes;
        } else {
            Calendar c = Calendar.getInstance();
            v.id = System.currentTimeMillis();
            v.mode = mode;
            v.year = c.get(Calendar.YEAR);
            v.month = c.get(Calendar.MONTH);
            v.day = c.get(Calendar.DAY_OF_MONTH);
            int next = (c.get(Calendar.HOUR_OF_DAY) + 1) * 60;   // next full hour, for one hour
            v.startMinutes = next % (24 * 60);
            v.endMinutes = (next + 60) % (24 * 60);
        }

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_volume, null);
        TextView[] modes = {view.findViewById(R.id.modeMeeting), view.findViewById(R.id.modeTravel),
                view.findViewById(R.id.modePrayer)};
        TextView hint = view.findViewById(R.id.modeHint);
        TextView once = view.findViewById(R.id.volOnce);
        TextView daily = view.findViewById(R.id.volDaily);
        TextView date = view.findViewById(R.id.volDate);
        TextView start = view.findViewById(R.id.volStart);
        TextView end = view.findViewById(R.id.volEnd);

        Runnable refresh = () -> {
            for (int i = 0; i < modes.length; i++) {
                Ui.segment(modes[i], VolumeSchedule.Mode.values()[i] == v.mode);
            }
            hint.setText(hintFor(v.mode));
            Ui.segment(once, !v.daily);
            Ui.segment(daily, v.daily);
            date.setVisibility(v.daily ? View.GONE : View.VISIBLE);
            Calendar c = Calendar.getInstance();
            c.set(v.year, v.month, v.day);
            date.setText("Date: " + new SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH).format(c.getTime()));
            start.setText("From " + clock(v.startMinutes));
            end.setText("To " + clock(v.endMinutes));
        };
        for (int i = 0; i < modes.length; i++) {
            VolumeSchedule.Mode value = VolumeSchedule.Mode.values()[i];
            modes[i].setOnClickListener(x -> {
                v.mode = value;
                refresh.run();
            });
        }
        once.setOnClickListener(x -> {
            v.daily = false;
            refresh.run();
        });
        daily.setOnClickListener(x -> {
            v.daily = true;
            refresh.run();
        });
        date.setOnClickListener(x -> {
            DatePickerDialog d = new DatePickerDialog(activity, (p, y, m, dd) -> {
                v.year = y;
                v.month = m;
                v.day = dd;
                refresh.run();
            }, v.year, v.month, v.day);
            d.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            d.show();
        });
        start.setOnClickListener(x -> new TimePickerDialog(activity, (p, h, m) -> {
            v.startMinutes = h * 60 + m;
            refresh.run();
        }, v.startMinutes / 60, v.startMinutes % 60, false).show());
        end.setOnClickListener(x -> new TimePickerDialog(activity, (p, h, m) -> {
            v.endMinutes = h * 60 + m;
            refresh.run();
        }, v.endMinutes / 60, v.endMinutes % 60, false).show());
        refresh.run();

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(existing == null ? "New volume schedule" : "Change schedule")
                .setView(view)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            if (v.startMinutes == v.endMinutes) {
                Toast.makeText(activity, "Start and end time are the same", Toast.LENGTH_SHORT).show();
                return;
            }
            if (v.isOver(System.currentTimeMillis())) {
                Toast.makeText(activity, "This time is already over. Pick a later time or Daily.", Toast.LENGTH_LONG).show();
                return;
            }
            v.enabled = true;
            VolumeStore.save(activity, v);
            dialog.dismiss();
            if (!VolumeManager.hasDndAccess(activity)) {
                Toast.makeText(activity, "Saved. Now allow Do Not Disturb access so the volume can change.",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(activity, v.mode.title + " set: " + v.timeRange() + ", " + v.whenLabel(),
                        Toast.LENGTH_SHORT).show();
            }
            render();
        }));
        dialog.show();
    }

    private static String hintFor(VolumeSchedule.Mode mode) {
        switch (mode) {
            case MEETING:
                return "Ringtone off, phone on vibrate. Task alarms stay silent too (vibration only).";
            case TRAVEL:
                return "Ringtone and notifications at full volume.";
            default:
                return "Ringtone and notifications at low volume.";
        }
    }

    private static String clock(int minutes) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, (minutes / 60) % 24);
        c.set(Calendar.MINUTE, minutes % 60);
        return new SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(c.getTime());
    }
}
