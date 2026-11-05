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
import com.google.android.material.slider.Slider;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** The Volume tab: Meeting, Travel, Prayer and Office, each with an on/off switch, volume sliders and an optional time. */
final class VolumePage {

    private final AppCompatActivity activity;
    private final View banner;
    private final ImageView nowIcon;
    private final TextView nowTitle;
    private final TextView nowSub;
    private final LinearLayout modes;

    VolumePage(AppCompatActivity activity) {
        this.activity = activity;
        banner = activity.findViewById(R.id.dndBanner);
        nowIcon = activity.findViewById(R.id.volumeNowIcon);
        nowTitle = activity.findViewById(R.id.volumeNowTitle);
        nowSub = activity.findViewById(R.id.volumeNowSub);
        modes = activity.findViewById(R.id.volumeModes);
        activity.findViewById(R.id.dndFix).setOnClickListener(v -> openDndSettings(activity));
    }

    static void openDndSettings(AppCompatActivity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "Open Settings > Apps > Special app access > Do Not Disturb, and allow AlertAlarm.",
                    Toast.LENGTH_LONG).show();
        }
    }

    static int icon(VolumeProfile.Mode mode) {
        switch (mode) {
            case MEETING:
                return R.drawable.ic_vibrate;
            case PRAYER:
                return R.drawable.ic_volume_low;
            case OFFICE:
                return R.drawable.ic_office;
            default:
                return R.drawable.ic_volume;
        }
    }

    void render() {
        VolumeManager.reconcile(activity);
        long now = System.currentTimeMillis();
        banner.setVisibility(VolumeManager.hasDndAccess(activity) ? View.GONE : View.VISIBLE);

        VolumeProfile active = VolumeManager.active(activity);
        if (active != null) {
            nowIcon.setImageResource(icon(active.mode));
            nowTitle.setText(active.mode.title + " mode");
            long until = VolumeManager.activeUntil(activity);
            nowSub.setText(until > 0 ? "Until " + TimeText.time(until) : "On until you switch it off");
        } else {
            nowIcon.setImageResource(R.drawable.ic_volume);
            nowTitle.setText("Normal");
            nowSub.setText("Your phone's own volume");
        }

        modes.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(activity);
        List<VolumeProfile> all = VolumeStore.getAll(activity);
        for (VolumeProfile p : all) {
            View card = inflater.inflate(R.layout.item_volume_mode, modes, false);
            boolean on = VolumeManager.isOn(p, now);
            boolean winning = active != null && active.mode == p.mode;

            ImageView ic = card.findViewById(R.id.modeIcon);
            ic.setImageResource(icon(p.mode));
            ic.setBackgroundResource(on ? R.drawable.bg_icon_accent : R.drawable.bg_icon_done);
            ic.setImageTintList(ColorStateList.valueOf(Ui.color(activity, on ? R.color.accent : R.color.muted)));
            ((TextView) card.findViewById(R.id.modeTitle)).setText(p.mode.title);

            TextView status = card.findViewById(R.id.modeStatus);
            if (winning) {
                status.setText(p.manualOn ? "On now" : "On now (set time)");
                status.setTextColor(Ui.color(activity, R.color.accent));
            } else if (on) {
                status.setText("On, but " + (active != null ? active.mode.title : "another mode") + " was switched on later");
            } else {
                status.setText("Off");
            }

            TextView levels = card.findViewById(R.id.modeLevels);
            levels.setText(p.levelsText());
            Ui.tintStart(levels, R.drawable.ic_volume, R.color.muted);
            TextView time = card.findViewById(R.id.modeTime);
            if (p.timed) {
                time.setVisibility(View.VISIBLE);
                time.setText(p.timeText());
                Ui.tintStart(time, R.drawable.ic_clock, R.color.muted);
            }

            MaterialSwitch sw = card.findViewById(R.id.modeSwitch);
            sw.setChecked(on);
            sw.setContentDescription(p.mode.title + " mode");
            sw.setOnCheckedChangeListener((b, checked) -> toggle(p, checked));
            card.setOnClickListener(v -> openEditor(p));
            modes.addView(card);
        }
    }

    private void toggle(VolumeProfile p, boolean on) {
        long now = System.currentTimeMillis();
        if (on) {
            p.manualOn = true;
            p.onSince = now;
            p.skipUntil = 0;
        } else {
            p.manualOn = false;
            long[] w = p.windowAt(now);
            if (w != null) {
                p.skipUntil = w[1];   // switched off during its set time: skip the rest of today's time
            }
        }
        VolumeStore.save(activity, p);
        if (on && !VolumeManager.hasDndAccess(activity)) {
            Toast.makeText(activity, "Allow Do Not Disturb access, otherwise the volume may not change.",
                    Toast.LENGTH_LONG).show();
        }
        render();
    }

    // ------------------------------------------------------------------ editor

    private void openEditor(VolumeProfile original) {
        VolumeProfile p = copy(original);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_volume, null);
        Slider ring = view.findViewById(R.id.ringSlider);
        Slider notification = view.findViewById(R.id.notificationSlider);
        Slider call = view.findViewById(R.id.callSlider);
        Slider media = view.findViewById(R.id.mediaSlider);
        TextView ringValue = view.findViewById(R.id.ringValue);
        TextView notificationValue = view.findViewById(R.id.notificationValue);
        TextView callValue = view.findViewById(R.id.callValue);
        TextView mediaValue = view.findViewById(R.id.mediaValue);
        MaterialSwitch timed = view.findViewById(R.id.timedSwitch);
        View timeSection = view.findViewById(R.id.timeSection);
        TextView once = view.findViewById(R.id.volOnce);
        TextView daily = view.findViewById(R.id.volDaily);
        TextView date = view.findViewById(R.id.volDate);
        TextView start = view.findViewById(R.id.volStart);
        TextView end = view.findViewById(R.id.volEnd);

        Runnable refresh = () -> {
            ringValue.setText(p.ring == 0 ? "Vibrate" : p.ring + "%");
            notificationValue.setText(p.notification + "%");
            callValue.setText(p.call + "%");
            mediaValue.setText(p.media + "%");
            timeSection.setVisibility(p.timed ? View.VISIBLE : View.GONE);
            Ui.segment(once, !p.daily);
            Ui.segment(daily, p.daily);
            date.setVisibility(p.daily ? View.GONE : View.VISIBLE);
            Calendar c = Calendar.getInstance();
            c.set(p.year, p.month, p.day);
            date.setText("Date: " + new SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH).format(c.getTime()));
            start.setText("From " + TimeText.clock(p.startMinutes));
            end.setText("To " + TimeText.clock(p.endMinutes));
        };
        Runnable loadSliders = () -> {
            ring.setValue(snap(p.ring));
            notification.setValue(snap(p.notification));
            call.setValue(snap(p.call));
            media.setValue(snap(p.media));
        };
        loadSliders.run();
        ring.addOnChangeListener((s, value, fromUser) -> {
            p.ring = Math.round(value);
            refresh.run();
        });
        notification.addOnChangeListener((s, value, fromUser) -> {
            p.notification = Math.round(value);
            refresh.run();
        });
        call.addOnChangeListener((s, value, fromUser) -> {
            p.call = Math.round(value);
            refresh.run();
        });
        media.addOnChangeListener((s, value, fromUser) -> {
            p.media = Math.round(value);
            refresh.run();
        });

        timed.setChecked(p.timed);
        timed.setOnCheckedChangeListener((b, checked) -> {
            p.timed = checked;
            refresh.run();
        });
        once.setOnClickListener(x -> {
            p.daily = false;
            refresh.run();
        });
        daily.setOnClickListener(x -> {
            p.daily = true;
            refresh.run();
        });
        date.setOnClickListener(x -> {
            DatePickerDialog d = new DatePickerDialog(activity, (picker, y, m, dd) -> {
                p.year = y;
                p.month = m;
                p.day = dd;
                refresh.run();
            }, p.year, p.month, p.day);
            d.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            d.show();
        });
        start.setOnClickListener(x -> new TimePickerDialog(activity, (picker, h, m) -> {
            p.startMinutes = h * 60 + m;
            refresh.run();
        }, p.startMinutes / 60, p.startMinutes % 60, TimeText.use24h).show());
        end.setOnClickListener(x -> new TimePickerDialog(activity, (picker, h, m) -> {
            p.endMinutes = h * 60 + m;
            refresh.run();
        }, p.endMinutes / 60, p.endMinutes % 60, TimeText.use24h).show());
        refresh.run();

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(p.mode.title + " mode")
                .setView(view)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Reset", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x -> {
                p.resetLevels();
                loadSliders.run();
                refresh.run();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
                long now = System.currentTimeMillis();
                if (p.timed && p.startMinutes == p.endMinutes) {
                    Toast.makeText(activity, "Start and end time are the same", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (p.timeIsOver(now)) {
                    Toast.makeText(activity, "This time is already over. Pick a later time or Daily.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (p.timed && (original.startMinutes != p.startMinutes || original.endMinutes != p.endMinutes
                        || original.daily != p.daily || !original.timed)) {
                    p.skipUntil = 0;   // a new time range starts fresh
                }
                VolumeStore.save(activity, p);
                dialog.dismiss();
                Toast.makeText(activity, p.mode.title + " saved", Toast.LENGTH_SHORT).show();
                render();
            });
        });
        dialog.show();
    }

    /** Sliders only take multiples of 5. */
    private static float snap(int percent) {
        return Math.max(0, Math.min(100, Math.round(percent / 5f) * 5));
    }

    private static VolumeProfile copy(VolumeProfile o) {
        VolumeProfile p = VolumeProfile.defaults(o.mode);
        p.ring = o.ring;
        p.notification = o.notification;
        p.call = o.call;
        p.media = o.media;
        p.manualOn = o.manualOn;
        p.onSince = o.onSince;
        p.timed = o.timed;
        p.daily = o.daily;
        p.year = o.year;
        p.month = o.month;
        p.day = o.day;
        p.startMinutes = o.startMinutes;
        p.endMinutes = o.endMinutes;
        p.skipUntil = o.skipUntil;
        return p;
    }
}
