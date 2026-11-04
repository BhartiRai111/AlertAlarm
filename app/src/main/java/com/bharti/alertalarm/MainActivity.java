package com.bharti.alertalarm;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Dashboard: list of alarms like the Clock app, with on/off switches and the next alarm on top. */
public class MainActivity extends AppCompatActivity {

    private static final int COLOR_DARK = Color.parseColor("#111827");
    private static final int COLOR_MUTED = Color.parseColor("#9CA3AF");
    private static final int COLOR_BRAND = Color.parseColor("#4F46E5");
    private static final int COLOR_RED = Color.parseColor("#B91C1C");

    private final List<Reminder> items = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable periodicRefresh = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 30_000L);
        }
    };

    private ReminderAdapter adapter;
    private TextView nextAlarmText;
    private View banner;
    private TextView bannerText;
    private Button bannerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Notifications.ensureChannel(this);

        nextAlarmText = findViewById(R.id.nextAlarmText);
        ListView list = findViewById(R.id.list);
        list.setEmptyView(findViewById(R.id.emptyText));
        adapter = new ReminderAdapter();
        list.setAdapter(adapter);
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmDelete(items.get(position));
            return true;
        });

        banner = findViewById(R.id.banner);
        bannerText = findViewById(R.id.bannerText);
        bannerButton = findViewById(R.id.bannerButton);

        findViewById(R.id.addButton).setOnClickListener(v ->
                startActivity(new Intent(this, AddReminderActivity.class)));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(periodicRefresh);
        updatePermissionBanner();

        // If an alarm is ringing right now, always show the alarm screen.
        if (!ReminderStore.getActive(this, System.currentTimeMillis()).isEmpty()) {
            AlarmService.ensureRunning(this);
            startActivity(new Intent(this, RingingActivity.class));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(periodicRefresh);
    }

    /** Upcoming and ringing alarms first (soonest on top), finished ones at the bottom (latest first). */
    private void refresh() {
        long now = System.currentTimeMillis();
        ReminderStore.markExpiredAndGetActive(this, now);
        List<Reminder> all = ReminderStore.getAll(this);

        List<Reminder> open = new ArrayList<>();
        List<Reminder> finished = new ArrayList<>();
        for (Reminder r : all) {
            (r.done ? finished : open).add(r);
        }
        open.sort(Comparator.comparingLong(r -> r.startMillis));
        finished.sort((a, b) -> Long.compare(b.startMillis, a.startMillis));

        items.clear();
        items.addAll(open);
        items.addAll(finished);
        adapter.notifyDataSetChanged();
        updateNextAlarm(open, now);
    }

    private void updateNextAlarm(List<Reminder> open, long now) {
        for (Reminder r : open) {
            if (r.enabled && r.isActive(now)) {
                nextAlarmText.setText("🔔 Ringing now: " + r.task);
                return;
            }
        }
        for (Reminder r : open) {
            if (r.enabled && r.startMillis > now) {
                nextAlarmText.setText("Next alarm in " + TimeText.until(r.startMillis)
                        + "\n" + TimeText.dayLabel(r.startMillis) + ", " + TimeText.time(r.startMillis));
                return;
            }
        }
        nextAlarmText.setText("No upcoming alarms");
    }

    private void confirmDelete(Reminder r) {
        if (r.isActive(System.currentTimeMillis())) {
            Toast.makeText(this, "This alarm is ringing now. It cannot be deleted before "
                    + TimeText.time(r.endMillis) + ".", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete this alarm?")
                .setMessage(r.task)
                .setPositiveButton("Delete", (d, w) -> {
                    AlarmScheduler.cancel(this, r.id);
                    ReminderStore.delete(this, r.id);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** On/off switch, like the Clock app. A ringing alarm cannot be switched off. */
    private void toggle(Reminder r, boolean on) {
        long now = System.currentTimeMillis();
        if (r.isActive(now)) {
            Toast.makeText(this, "This alarm is locked until " + TimeText.time(r.endMillis), Toast.LENGTH_SHORT).show();
            refresh();
            return;
        }
        ReminderStore.setEnabled(this, r.id, on);
        r.enabled = on;
        if (on) {
            AlarmScheduler.schedule(this, r);
            Toast.makeText(this, "Alarm on: " + TimeText.dayLabel(r.startMillis) + ", "
                    + TimeText.time(r.startMillis), Toast.LENGTH_SHORT).show();
        } else {
            AlarmScheduler.cancel(this, r.id);
            Toast.makeText(this, "Alarm off", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    // ------------------------------------------------------------------ permissions

    private void updatePermissionBanner() {
        String pkg = "package:" + getPackageName();
        String text = null;
        Intent action = null;

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            text = "Notifications are off. Turn them on so the alarm can show.";
            action = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !getSystemService(AlarmManager.class).canScheduleExactAlarms()) {
            text = "Allow \"Alarms & reminders\", otherwise the alarm may not ring on time.";
            action = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse(pkg));
        } else if (Build.VERSION.SDK_INT >= 34
                && !getSystemService(NotificationManager.class).canUseFullScreenIntent()) {
            text = "Allow full-screen alarms so the alarm opens on the lock screen.";
            action = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse(pkg));
        } else if (!getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName())) {
            text = "Turn off battery optimization so the phone does not stop the alarm.";
            action = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse(pkg));
        }

        if (text == null) {
            banner.setVisibility(View.GONE);
            return;
        }
        banner.setVisibility(View.VISIBLE);
        bannerText.setText(text);
        Intent finalAction = action;
        bannerButton.setOnClickListener(v -> {
            try {
                startActivity(finalAction);
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse(pkg)));
            }
        });
    }

    // ------------------------------------------------------------------ list

    private class ReminderAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(MainActivity.this).inflate(R.layout.item_reminder, parent, false);
            Reminder r = items.get(position);
            long now = System.currentTimeMillis();

            TextView time = view.findViewById(R.id.itemTime);
            TextView until = view.findViewById(R.id.itemUntil);
            TextView task = view.findViewById(R.id.itemTask);
            TextView status = view.findViewById(R.id.itemStatus);
            MaterialSwitch onOff = view.findViewById(R.id.itemSwitch);

            time.setText(TimeText.time(r.startMillis));
            until.setText("until " + TimeText.time(r.endMillis) + "  ·  " + TimeText.duration(r.endMillis - r.startMillis));
            task.setText(r.task);

            boolean active = r.isActive(now);
            boolean dim = r.done || !r.enabled;
            time.setTextColor(dim ? COLOR_MUTED : COLOR_DARK);
            task.setTextColor(dim ? COLOR_MUTED : COLOR_DARK);

            if (r.done) {
                status.setText("✓ Done  ·  " + TimeText.dayLabel(r.startMillis));
                status.setTextColor(COLOR_MUTED);
            } else if (active) {
                status.setText("🔔 Ringing · locked until " + TimeText.time(r.endMillis));
                status.setTextColor(COLOR_RED);
            } else if (!r.enabled) {
                status.setText("Off  ·  " + TimeText.dayLabel(r.startMillis));
                status.setTextColor(COLOR_MUTED);
            } else {
                status.setText(TimeText.dayLabel(r.startMillis) + "  ·  in " + TimeText.until(r.startMillis));
                status.setTextColor(COLOR_BRAND);
            }

            // Set the state first with no listener, so recycling rows does not toggle alarms.
            onOff.setOnCheckedChangeListener(null);
            onOff.setChecked(r.enabled && !r.done);
            onOff.setVisibility(r.done ? View.INVISIBLE : View.VISIBLE);
            onOff.setEnabled(!r.done && !active);
            onOff.setOnCheckedChangeListener((button, checked) -> toggle(r, checked));
            return view;
        }
    }
}
