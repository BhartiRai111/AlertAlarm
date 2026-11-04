package com.bharti.alertalarm;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Home, Calendar, Tasks and Settings, switched with the bottom bar. */
public class MainActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable periodicRefresh = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 30_000L);
        }
    };

    private List<Reminder> all = new ArrayList<>();
    private int tab = R.id.nav_home;

    private View homePage;
    private View calendarPage;
    private View tasksPage;
    private View settingsPage;
    private View addButton;
    private BottomNavigationView bottomNav;

    // Home
    private TextView dateText;
    private TextView greetingText;
    private View heroCard;
    private TextView heroLabel;
    private TextView heroBadge;
    private TextView heroTime;
    private TextView heroTask;
    private TextView heroMeta;
    private TextView statToday;
    private TextView statRepeating;
    private TextView statDone;
    private LinearLayout todayList;
    private View todayEmpty;
    private View banner;
    private TextView bannerText;
    private TextView bannerButton;

    // Calendar
    private final Calendar shownMonth = Calendar.getInstance();
    private long selectedDay;
    private TextView monthTitle;
    private LinearLayout calendarGrid;
    private TextView dayTitle;
    private TextView dayCount;
    private LinearLayout dayList;
    private View dayEmpty;

    // Tasks
    private String filter = "all";
    private EditText searchInput;
    private TextView chipAll;
    private TextView chipUpcoming;
    private TextView chipRepeating;
    private TextView chipDone;
    private LinearLayout taskList;
    private TextView taskEmpty;

    // Settings
    private LinearLayout settingsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Notifications.ensureChannel(this);

        homePage = findViewById(R.id.homePage);
        calendarPage = findViewById(R.id.calendarPage);
        tasksPage = findViewById(R.id.tasksPage);
        settingsPage = findViewById(R.id.settingsPage);
        addButton = findViewById(R.id.addButton);
        bottomNav = findViewById(R.id.bottomNav);

        setUpHome();
        setUpCalendar();
        setUpTasks();
        settingsList = findViewById(R.id.settingsList);
        ((TextView) findViewById(R.id.aboutText)).setText(
                "AlertAlarm speaks your task out loud every 20 seconds and keeps ringing until the end time."
                        + " The Stop button and the back button stay locked until then.\n\nVersion 2.0");

        bottomNav.setItemActiveIndicatorEnabled(false);
        bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });

        addButton.setOnClickListener(v -> {
            Intent i = new Intent(this, AddReminderActivity.class);
            if (tab == R.id.nav_calendar) {
                i.putExtra(AddReminderActivity.EXTRA_DATE, selectedDay);
            }
            startActivity(i);
        });

        if (savedInstanceState != null) {
            tab = savedInstanceState.getInt("tab", R.id.nav_home);
            selectedDay = savedInstanceState.getLong("day", selectedDay);
            shownMonth.setTimeInMillis(savedInstanceState.getLong("month", shownMonth.getTimeInMillis()));
            filter = savedInstanceState.getString("filter", "all");
        }
        bottomNav.setSelectedItemId(tab);
        showTab(tab);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
        out.putLong("day", selectedDay);
        out.putLong("month", shownMonth.getTimeInMillis());
        out.putString("filter", filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(periodicRefresh);

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

    private void showTab(int id) {
        tab = id;
        homePage.setVisibility(id == R.id.nav_home ? View.VISIBLE : View.GONE);
        calendarPage.setVisibility(id == R.id.nav_calendar ? View.VISIBLE : View.GONE);
        tasksPage.setVisibility(id == R.id.nav_tasks ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(id == R.id.nav_settings ? View.VISIBLE : View.GONE);
        addButton.setVisibility(id == R.id.nav_settings ? View.GONE : View.VISIBLE);
        refresh();
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        ReminderStore.markExpiredAndGetActive(this, now);
        all = ReminderStore.getAll(this);
        List<Check> checks = checks();
        renderBanner(checks);
        if (tab == R.id.nav_home) {
            renderHome(now);
        } else if (tab == R.id.nav_calendar) {
            renderCalendar(now);
        } else if (tab == R.id.nav_tasks) {
            renderTasks(now);
        } else {
            renderSettings(checks);
        }
    }

    private void openDetail(Reminder r) {
        startActivity(new Intent(this, DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, r.id));
    }

    // ================================================================== Home

    private void setUpHome() {
        dateText = findViewById(R.id.dateText);
        greetingText = findViewById(R.id.greetingText);
        heroCard = findViewById(R.id.heroCard);
        heroLabel = findViewById(R.id.heroLabel);
        heroBadge = findViewById(R.id.heroBadge);
        heroTime = findViewById(R.id.heroTime);
        heroTask = findViewById(R.id.heroTask);
        heroMeta = findViewById(R.id.heroMeta);
        statToday = findViewById(R.id.statToday);
        statRepeating = findViewById(R.id.statRepeating);
        statDone = findViewById(R.id.statDone);
        todayList = findViewById(R.id.todayList);
        todayEmpty = findViewById(R.id.todayEmpty);
        banner = findViewById(R.id.banner);
        bannerText = findViewById(R.id.bannerText);
        bannerButton = findViewById(R.id.bannerButton);
        findViewById(R.id.seeAll).setOnClickListener(v -> bottomNav.setSelectedItemId(R.id.nav_tasks));
    }

    private void renderHome(long now) {
        dateText.setText(new SimpleDateFormat("EEEE, d MMM", Locale.ENGLISH).format(new Date(now)));
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greetingText.setText(hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening");

        // Hero: the alarm ringing now, else the next enabled one
        Reminder hero = null;
        boolean ringing = false;
        for (Reminder r : all) {
            if (r.isActive(now)) {
                hero = r;
                ringing = true;
                break;
            }
        }
        if (hero == null) {
            for (Reminder r : all) {
                if (r.enabled && !r.done && r.startMillis > now
                        && (hero == null || r.startMillis < hero.startMillis)) {
                    hero = r;
                }
            }
        }
        if (hero == null) {
            heroLabel.setText("No upcoming alarms");
            heroBadge.setVisibility(View.GONE);
            heroTime.setText("All clear");
            heroTime.setTextSize(34);
            heroTask.setText("Tap + to set your next alarm.");
            heroMeta.setVisibility(View.GONE);
            heroCard.setOnClickListener(null);
            heroCard.setClickable(false);
        } else {
            Reminder h = hero;
            heroLabel.setText(ringing ? "Ringing now" : "Next alarm");
            heroBadge.setVisibility(View.VISIBLE);
            heroBadge.setText(ringing ? "locked" : "in " + TimeText.until(h.startMillis));
            heroTime.setText(Ui.bigTime(h.startMillis));
            heroTime.setTextSize(50);
            heroTask.setText(h.task);
            heroMeta.setVisibility(View.VISIBLE);
            heroMeta.setText((h.isRepeating() ? h.repeatLabel() : TimeText.dayLabel(h.startMillis))
                    + " · until " + TimeText.time(h.endMillis) + " · " + TimeText.duration(h.endMillis - h.startMillis));
            Ui.tintStart(heroMeta, h.isRepeating() ? R.drawable.ic_repeat : R.drawable.ic_clock, R.color.hero_text);
            heroCard.setOnClickListener(v -> openDetail(h));
        }

        // Stats + today's list
        List<Ui.Entry> today = Ui.entriesOn(all, Ui.startOfDay(now));
        int repeating = 0;
        int done = 0;
        for (Reminder r : all) {
            if (r.isRepeating() && r.enabled) {
                repeating++;
            }
            if (r.done) {
                done++;
            }
        }
        statToday.setText(String.valueOf(today.size()));
        statRepeating.setText(String.valueOf(repeating));
        statDone.setText(String.valueOf(done));

        todayList.removeAllViews();
        for (Ui.Entry e : today) {
            Reminder r = e.reminder;
            String meta = r.isActive(now) ? Ui.meta(r, now)
                    : TimeText.time(e.start) + " – " + TimeText.time(e.start + r.durationMs)
                    + " · " + (r.done ? "Done" : r.repeatLabel()) + (r.enabled || r.done ? "" : " · Off");
            View row = Ui.alarmRow(todayList, r, now, meta);
            addSwitch(row, r, now);
            todayList.addView(row);
        }
        todayEmpty.setVisibility(today.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Row click opens details, long press deletes, switch turns the alarm on/off (like the Clock app). */
    private void addSwitch(View row, Reminder r, long now) {
        row.setOnClickListener(v -> openDetail(r));
        row.setOnLongClickListener(v -> {
            confirmDelete(r);
            return true;
        });
        if (r.done) {
            return;
        }
        MaterialSwitch onOff = row.findViewById(R.id.itemSwitch);
        onOff.setVisibility(View.VISIBLE);
        onOff.setChecked(r.enabled);
        onOff.setEnabled(!r.isActive(now));
        onOff.setOnCheckedChangeListener((b, checked) -> toggle(r, checked));
    }

    private void toggle(Reminder r, boolean on) {
        Alarms.toggle(this, r, on);
        refresh();
    }

    private void confirmDelete(Reminder r) {
        Alarms.confirmDelete(this, r, this::refresh);
    }

    // ================================================================== Calendar

    private void setUpCalendar() {
        monthTitle = findViewById(R.id.monthTitle);
        calendarGrid = findViewById(R.id.calendarGrid);
        dayTitle = findViewById(R.id.dayTitle);
        dayCount = findViewById(R.id.dayCount);
        dayList = findViewById(R.id.dayList);
        dayEmpty = findViewById(R.id.dayEmpty);

        selectedDay = Ui.startOfDay(System.currentTimeMillis());
        shownMonth.setTimeInMillis(selectedDay);
        shownMonth.set(Calendar.DAY_OF_MONTH, 1);

        LinearLayout weekdays = findViewById(R.id.weekdayRow);
        for (String name : new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"}) {
            TextView t = new TextView(this);
            t.setText(name);
            t.setGravity(android.view.Gravity.CENTER);
            t.setTextSize(12);
            t.setTextColor(Ui.color(this, R.color.muted));
            weekdays.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }

        findViewById(R.id.prevMonth).setOnClickListener(v -> {
            shownMonth.add(Calendar.MONTH, -1);
            refresh();
        });
        findViewById(R.id.nextMonth).setOnClickListener(v -> {
            shownMonth.add(Calendar.MONTH, 1);
            refresh();
        });
    }

    private void renderCalendar(long now) {
        monthTitle.setText(new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(shownMonth.getTime()));
        calendarGrid.removeAllViews();

        Calendar day = (Calendar) shownMonth.clone();
        day.set(Calendar.DAY_OF_MONTH, 1);
        int firstColumn = day.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        int days = day.getActualMaximum(Calendar.DAY_OF_MONTH);
        int rows = (firstColumn + days + 6) / 7;
        long today = Ui.startOfDay(now);
        LayoutInflater inflater = LayoutInflater.from(this);
        float density = getResources().getDisplayMetrics().density;

        for (int row = 0; row < rows; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (6 * density);
            for (int col = 0; col < 7; col++) {
                int num = row * 7 + col - firstColumn + 1;
                View cell = inflater.inflate(R.layout.item_day, line, false);
                if (num < 1 || num > days) {
                    cell.setVisibility(View.INVISIBLE);
                } else {
                    day.set(Calendar.DAY_OF_MONTH, num);
                    long dayStart = Ui.startOfDay(day.getTimeInMillis());
                    bindDay(cell, num, dayStart, dayStart == selectedDay, dayStart == today,
                            !Ui.entriesOn(all, dayStart).isEmpty());
                }
                line.addView(cell);
            }
            calendarGrid.addView(line, lp);
        }

        // Selected day's alarms
        List<Ui.Entry> entries = Ui.entriesOn(all, selectedDay);
        dayTitle.setText(selectedDay == today ? "Today"
                : new SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(new Date(selectedDay)));
        dayCount.setText(entries.isEmpty() ? "" : entries.size() + (entries.size() == 1 ? " alarm" : " alarms"));
        dayList.removeAllViews();
        for (Ui.Entry e : entries) {
            Reminder r = e.reminder;
            String meta = TimeText.duration(r.durationMs) + " · "
                    + (r.done ? "Done" : r.repeatLabel()) + (r.enabled || r.done ? "" : " · Off");
            View row = Ui.alarmRow(dayList, r, now, meta);
            TextView time = row.findViewById(R.id.itemTime);
            time.setVisibility(View.VISIBLE);
            time.setText(TimeText.time(e.start));
            row.setOnClickListener(v -> openDetail(r));
            dayList.addView(row);
        }
        dayEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void bindDay(View cell, int num, long dayStart, boolean selected, boolean isToday, boolean busy) {
        TextView number = cell.findViewById(R.id.dayNum);
        View dot = cell.findViewById(R.id.dayDot);
        number.setText(String.valueOf(num));
        number.setTextColor(Ui.color(this, selected ? R.color.on_accent : R.color.text));
        number.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        cell.setBackgroundResource(selected ? R.drawable.bg_day_selected : isToday ? R.drawable.bg_day_today : 0);
        dot.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Ui.color(this, selected ? R.color.on_accent : R.color.accent)));
        cell.setContentDescription(num + (busy ? ", has alarms" : ""));
        cell.setOnClickListener(v -> {
            selectedDay = dayStart;
            refresh();
        });
    }

    // ================================================================== Tasks

    private void setUpTasks() {
        searchInput = findViewById(R.id.searchInput);
        chipAll = findViewById(R.id.chipAll);
        chipUpcoming = findViewById(R.id.chipUpcoming);
        chipRepeating = findViewById(R.id.chipRepeating);
        chipDone = findViewById(R.id.chipDone);
        taskList = findViewById(R.id.taskList);
        taskEmpty = findViewById(R.id.taskEmpty);

        chipAll.setOnClickListener(v -> setFilter("all"));
        chipUpcoming.setOnClickListener(v -> setFilter("upcoming"));
        chipRepeating.setOnClickListener(v -> setFilter("repeating"));
        chipDone.setOnClickListener(v -> setFilter("done"));

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (tab == R.id.nav_tasks) {
                    renderTasks(System.currentTimeMillis());
                }
            }
        });
    }

    private void setFilter(String f) {
        filter = f;
        renderTasks(System.currentTimeMillis());
    }

    private void renderTasks(long now) {
        Ui.chip(chipAll, filter.equals("all"));
        Ui.chip(chipUpcoming, filter.equals("upcoming"));
        Ui.chip(chipRepeating, filter.equals("repeating"));
        Ui.chip(chipDone, filter.equals("done"));

        String query = searchInput.getText().toString().trim().toLowerCase(Locale.ENGLISH);
        List<Reminder> open = new ArrayList<>();
        List<Reminder> finished = new ArrayList<>();
        for (Reminder r : all) {
            boolean match;
            switch (filter) {
                case "upcoming":
                    match = !r.done && !r.isRepeating();
                    break;
                case "repeating":
                    match = r.isRepeating();
                    break;
                case "done":
                    match = r.done;
                    break;
                default:
                    match = true;
            }
            if (match && (query.isEmpty() || r.task.toLowerCase(Locale.ENGLISH).contains(query))) {
                (r.done ? finished : open).add(r);
            }
        }
        open.sort(Comparator.comparingLong(r -> r.startMillis));
        finished.sort((a, b) -> Long.compare(b.startMillis, a.startMillis));
        open.addAll(finished);

        taskList.removeAllViews();
        for (Reminder r : open) {
            View row = Ui.alarmRow(taskList, r, now, Ui.meta(r, now));
            row.findViewById(R.id.itemChevron).setVisibility(View.VISIBLE);
            row.setOnClickListener(v -> openDetail(r));
            row.setOnLongClickListener(v -> {
                confirmDelete(r);
                return true;
            });
            taskList.addView(row);
        }
        taskEmpty.setVisibility(open.isEmpty() ? View.VISIBLE : View.GONE);
        taskEmpty.setText(all.isEmpty() ? "No alarms yet. Tap + to add one." : "Nothing here");
    }

    // ================================================================== Settings + permissions

    /** One thing the phone must allow so the alarm rings on time. */
    private static final class Check {
        final String title;
        final String problem;
        final boolean ok;
        final Intent fix;

        Check(String title, String problem, boolean ok, Intent fix) {
            this.title = title;
            this.problem = problem;
            this.ok = ok;
            this.fix = fix;
        }
    }

    private List<Check> checks() {
        String pkg = "package:" + getPackageName();
        List<Check> list = new ArrayList<>();
        list.add(new Check("Notifications",
                "Notifications are off. Turn them on so the alarm can show.",
                NotificationManagerCompat.from(this).areNotificationsEnabled(),
                new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        list.add(new Check("Alarms & reminders",
                "Allow \"Alarms & reminders\", otherwise the alarm may not ring on time.",
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                        || getSystemService(AlarmManager.class).canScheduleExactAlarms(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse(pkg)) : null));
        list.add(new Check("Display over other apps",
                "Allow \"Display over other apps\" so the alarm opens full screen even while you use the phone.",
                Settings.canDrawOverlays(this),
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse(pkg))));
        list.add(new Check("Full-screen alarm",
                "Allow full-screen alarms so the alarm opens on the lock screen.",
                Build.VERSION.SDK_INT < 34
                        || getSystemService(NotificationManager.class).canUseFullScreenIntent(),
                Build.VERSION.SDK_INT >= 34
                        ? new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse(pkg)) : null));
        list.add(new Check("Battery optimization off",
                "Turn off battery optimization so the phone does not stop the alarm.",
                getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName()),
                new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse(pkg))));
        return list;
    }

    private void open(Intent action) {
        try {
            startActivity(action);
        } catch (ActivityNotFoundException | SecurityException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void renderBanner(List<Check> checks) {
        for (Check c : checks) {
            if (!c.ok) {
                banner.setVisibility(View.VISIBLE);
                bannerText.setText(c.problem);
                bannerButton.setOnClickListener(v -> open(c.fix));
                return;
            }
        }
        banner.setVisibility(View.GONE);
    }

    private void renderSettings(List<Check> checks) {
        settingsList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Check c : checks) {
            View row = inflater.inflate(R.layout.item_setting, settingsList, false);
            ((TextView) row.findViewById(R.id.settingTitle)).setText(c.title);
            ((TextView) row.findViewById(R.id.settingDesc)).setText(c.ok ? "Allowed" : c.problem);
            TextView action = row.findViewById(R.id.settingAction);
            if (c.ok) {
                action.setText("On");
                action.setTextColor(Ui.color(this, R.color.accent));
                action.setBackground(null);
            } else {
                action.setText("Fix");
                action.setTextColor(Ui.color(this, R.color.on_accent));
                action.setBackgroundResource(R.drawable.bg_pill_accent);
                action.setOnClickListener(v -> open(c.fix));
            }
            settingsList.addView(row);
        }

        View voice = inflater.inflate(R.layout.item_setting, settingsList, false);
        ((TextView) voice.findViewById(R.id.settingTitle)).setText("Voice");
        ((TextView) voice.findViewById(R.id.settingDesc)).setText("Change the text-to-speech voice or speed");
        TextView openVoice = voice.findViewById(R.id.settingAction);
        openVoice.setText("Open");
        openVoice.setTextColor(Ui.color(this, R.color.text));
        openVoice.setBackgroundResource(R.drawable.bg_chip);
        openVoice.setOnClickListener(v -> {
            try {
                startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "Voice settings are not available on this phone", Toast.LENGTH_SHORT).show();
            }
        });
        settingsList.addView(voice);
    }
}
