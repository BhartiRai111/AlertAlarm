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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** List of all tasks + permission checks. */
public class MainActivity extends AppCompatActivity {

    private final List<Reminder> items = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM", Locale.ENGLISH);

    private ReminderAdapter adapter;
    private View banner;
    private TextView bannerText;
    private Button bannerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Notifications.ensureChannel(this);

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
        refresh();
        updatePermissionBanner();

        // If an alarm is ringing right now, always show the alarm screen.
        if (!ReminderStore.getActive(this, System.currentTimeMillis()).isEmpty()) {
            AlarmService.ensureRunning(this);
            startActivity(new Intent(this, RingingActivity.class));
        }
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        ReminderStore.markExpiredAndGetActive(this, now);
        items.clear();
        items.addAll(ReminderStore.getAll(this));
        adapter.notifyDataSetChanged();
    }

    private void confirmDelete(Reminder r) {
        if (r.isActive(System.currentTimeMillis())) {
            Toast.makeText(this, "This alarm is ringing now. It cannot be deleted before "
                    + TimeText.time(r.endMillis) + ".", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete this task?")
                .setMessage(r.task)
                .setPositiveButton("Delete", (d, w) -> {
                    AlarmScheduler.cancel(this, r.id);
                    ReminderStore.delete(this, r.id);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
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

            TextView task = view.findViewById(R.id.itemTask);
            TextView time = view.findViewById(R.id.itemTime);
            TextView status = view.findViewById(R.id.itemStatus);

            task.setText(r.task);
            time.setText(dateFormat.format(new Date(r.startMillis)) + "  •  "
                    + TimeText.time(r.startMillis) + " – "
                    + TimeText.time(r.endMillis));

            long now = System.currentTimeMillis();
            if (r.done) {
                status.setText("✓ Done");
                status.setTextColor(Color.parseColor("#6B7280"));
            } else if (r.isActive(now)) {
                status.setText("🔔 Ringing (locked)");
                status.setTextColor(Color.parseColor("#B91C1C"));
            } else {
                status.setText("⏰ Upcoming");
                status.setTextColor(Color.parseColor("#4F46E5"));
            }
            return view;
        }
    }
}
