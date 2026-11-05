package com.bharti.alertalarm;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Settings tab: language and voice, sound and vibration, theme, time format, backup, about. */
final class SettingsPage {

    private static final int[] VOICE_EVERY = {10, 20, 30, 60};
    private static final float[] SPEEDS = {0.75f, 0.9f, 1.15f};
    private static final String[] SPEED_NAMES = {"Slow", "Normal", "Fast"};

    private final AppCompatActivity activity;
    private final LinearLayout voice;
    private final LinearLayout sound;
    private final LinearLayout display;
    private final LinearLayout backup;
    private final LinearLayout help;
    private final LinearLayout reminders;
    private final ActivityResultLauncher<String> calendarPermission;
    private final LayoutInflater inflater;
    private TextToSpeech testVoice;

    private final ActivityResultLauncher<Intent> tonePicker;
    private final ActivityResultLauncher<String> backupFile;
    private final ActivityResultLauncher<String[]> restoreFile;

    SettingsPage(AppCompatActivity activity) {
        this.activity = activity;
        inflater = LayoutInflater.from(activity);
        voice = activity.findViewById(R.id.voiceSection);
        sound = activity.findViewById(R.id.soundSection);
        display = activity.findViewById(R.id.displaySection);
        backup = activity.findViewById(R.id.backupSection);
        help = activity.findViewById(R.id.helpSection);
        reminders = activity.findViewById(R.id.remindersSection);
        calendarPermission = activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                granted -> {
                    AppSettings.setCalendarSync(activity, granted);
                    toast(granted ? "Calendar events now show in the Calendar tab"
                            : "Calendar permission is needed to show your events");
                    render();
                });

        tonePicker = activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                this::onTonePicked);
        backupFile = activity.registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
                uri -> {
                    if (uri != null) {
                        writeBackup(uri);
                    }
                });
        restoreFile = activity.registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                readBackup(uri);
            }
        });

        String version = "";
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            version = " " + info.versionName;
        } catch (Exception ignored) {
            // version stays empty
        }
        ((TextView) activity.findViewById(R.id.aboutTitle)).setText("AlertAlarm" + version);
        ((TextView) activity.findViewById(R.id.aboutText)).setText(
                "A strict task alarm. It speaks your task out loud and keeps ringing until the end time. "
                        + "The Stop button and the back button stay locked until then.\n\n"
                        + "Also: repeat alarms, calendar, import tasks from PDF / Excel / notes, "
                        + "and volume modes for meetings, travel, prayer and office.");
        ((TextView) activity.findViewById(R.id.aboutNext)).setText(
                "•  All screens in Hindi\n"
                        + "•  Import tasks from scanned PDFs and photos\n"
                        + "•  AlertAlarm on the Google Play Store");
    }

    void destroy() {
        if (testVoice != null) {
            testVoice.shutdown();
            testVoice = null;
        }
    }

    void render() {
        renderVoice();
        renderSound();
        renderDisplay();
        renderReminders();
        renderHelp();
        renderBackup();
    }

    // ================================================================== bedtime + calendar

    private void renderReminders() {
        reminders.removeAllViews();
        switchRow(reminders, "Bedtime reminder", "A notice at night with tomorrow's first task",
                AppSettings.bedtimeOn(activity), (b, on) -> {
                    AppSettings.setBedtimeOn(activity, on);
                    Bedtime.schedule(activity);
                    if (on) {
                        toast("Bedtime reminder on: every night at " + TimeText.clock(AppSettings.bedtimeMinutes(activity)));
                    }
                });
        int minutes = AppSettings.bedtimeMinutes(activity);
        valueRow(reminders, "Bedtime", "When the bedtime reminder comes", TimeText.clock(minutes), () ->
                new android.app.TimePickerDialog(activity, (p, h, m) -> {
                    AppSettings.setBedtimeMinutes(activity, h * 60 + m);
                    Bedtime.schedule(activity);
                    render();
                }, minutes / 60, minutes % 60, TimeText.use24h).show());
        switchRow(reminders, "Google Calendar events", "Show your phone's calendar events in the Calendar tab",
                CalendarEvents.enabled(activity), (b, on) -> {
                    if (!on) {
                        AppSettings.setCalendarSync(activity, false);
                    } else if (CalendarEvents.hasPermission(activity)) {
                        AppSettings.setCalendarSync(activity, true);
                    } else {
                        calendarPermission.launch(android.Manifest.permission.READ_CALENDAR);
                    }
                });
    }

    // ================================================================== troubleshooting

    private void renderHelp() {
        help.removeAllViews();
        valueRow(help, "Alarm log", "When alarms were set and rang, and any errors", "Open", this::showLog);
    }

    private void showLog() {
        TextView text = new TextView(activity);
        int pad = (int) (22 * activity.getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad / 2, pad, 0);
        text.setTextSize(13);
        text.setTextIsSelectable(true);
        text.setTextColor(Ui.color(activity, R.color.soft_text));
        text.setText(AlarmLog.text(activity));
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.addView(text);
        new AlertDialog.Builder(activity)
                .setTitle("Alarm log")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .setNeutralButton("Clear", (d, w) -> {
                    AlarmLog.clear(activity);
                    toast("Log cleared");
                })
                .setNegativeButton("Share", (d, w) -> {
                    Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "AlertAlarm log")
                            .putExtra(Intent.EXTRA_TEXT, AlarmLog.text(activity));
                    activity.startActivity(Intent.createChooser(share, "Share the alarm log"));
                })
                .show();
    }

    // ================================================================== voice

    private void renderVoice() {
        voice.removeAllViews();
        boolean hindi = "hi".equals(AppSettings.voiceLanguage(activity));
        valueRow(voice, "Voice language", hindi ? "Needs a Hindi voice in text-to-speech" : "What the alarm speaks",
                hindi ? "Hindi" : "English", () -> choose("Voice language", new String[]{"English", "Hindi (हिंदी)"},
                        hindi ? 1 : 0, i -> AppSettings.setVoiceLanguage(activity, i == 1 ? "hi" : "en")));
        switchRow(voice, "Voice announcements", "Speak the task out loud while the alarm rings",
                AppSettings.voiceOn(activity), (b, on) -> AppSettings.setVoiceOn(activity, on));

        int every = AppSettings.voiceEvery(activity);
        String[] everyNames = new String[VOICE_EVERY.length];
        int selected = 1;
        for (int i = 0; i < VOICE_EVERY.length; i++) {
            everyNames[i] = "Every " + VOICE_EVERY[i] + " seconds";
            if (VOICE_EVERY[i] == every) {
                selected = i;
            }
        }
        valueRow(voice, "Repeat voice", "How often the task is spoken", every + " sec",
                () -> choose("Repeat voice", everyNames, indexOf(VOICE_EVERY, every, 1),
                        i -> AppSettings.setVoiceEvery(activity, VOICE_EVERY[i])));

        float speed = AppSettings.voiceSpeed(activity);
        int speedIndex = 1;
        for (int i = 0; i < SPEEDS.length; i++) {
            if (Math.abs(SPEEDS[i] - speed) < 0.01f) {
                speedIndex = i;
            }
        }
        int si = speedIndex;
        valueRow(voice, "Voice speed", "How fast it speaks", SPEED_NAMES[si],
                () -> choose("Voice speed", SPEED_NAMES, si, i -> AppSettings.setVoiceSpeed(activity, SPEEDS[i])));
        valueRow(voice, "Test voice", "Hear how the alarm will sound", "Play", this::testVoice);
        valueRow(voice, "Text-to-speech engine", "Install voices (e.g. Hindi) or change the engine", "Open", () -> {
            try {
                activity.startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
            } catch (ActivityNotFoundException e) {
                toast("Text-to-speech settings are not available on this phone");
            }
        });
    }

    private void testVoice() {
        if (testVoice != null) {
            speakTest();
            return;
        }
        toast("Loading voice…");
        testVoice = new TextToSpeech(activity, status -> {
            if (status != TextToSpeech.SUCCESS) {
                activity.runOnUiThread(() -> toast("Text-to-speech is not available on this phone"));
                testVoice = null;
                return;
            }
            activity.runOnUiThread(this::speakTest);
        });
    }

    private void speakTest() {
        boolean hindi = "hi".equals(AppSettings.voiceLanguage(activity));
        int result = testVoice.setLanguage(AppSettings.voiceLocale(activity));
        boolean missing = result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED;
        if (missing) {
            testVoice.setLanguage(Locale.ENGLISH);
            if (hindi) {
                toast("Hindi voice is not installed. Open \"Text-to-speech engine\" and install Hindi voice data.");
            }
        }
        testVoice.setSpeechRate(AppSettings.voiceSpeed(activity));
        testVoice.speak(AppSettings.announcement(activity, "Test task", hindi && !missing),
                TextToSpeech.QUEUE_FLUSH, null, "test");
    }

    // ================================================================== sound

    private void renderSound() {
        sound.removeAllViews();
        valueRow(sound, "Alarm tone", "Plays while the alarm rings", toneName(), this::pickTone);
        switchRow(sound, "Vibration", "Vibrate while the alarm rings",
                AppSettings.vibration(activity), (b, on) -> AppSettings.setVibration(activity, on));
        switchRow(sound, "Volume lock", "Alarm volume goes back to max if someone lowers it",
                AppSettings.volumeLock(activity), (b, on) -> AppSettings.setVolumeLock(activity, on));
    }

    private String toneName() {
        String chosen = AppSettings.alarmTone(activity);
        if (chosen == null) {
            return "Default";
        }
        try {
            Ringtone r = RingtoneManager.getRingtone(activity, Uri.parse(chosen));
            String title = r != null ? r.getTitle(activity) : null;
            if (title != null) {
                return title.length() > 18 ? title.substring(0, 17) + "…" : title;
            }
        } catch (Exception ignored) {
            // title not readable
        }
        return "Custom";
    }

    private void pickTone() {
        String chosen = AppSettings.alarmTone(activity);
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm tone")
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        chosen != null ? Uri.parse(chosen) : Settings.System.DEFAULT_ALARM_ALERT_URI);
        try {
            tonePicker.launch(i);
        } catch (ActivityNotFoundException e) {
            toast("No tone picker on this phone");
        }
    }

    private void onTonePicked(ActivityResult result) {
        if (result.getResultCode() != AppCompatActivity.RESULT_OK || result.getData() == null) {
            return;
        }
        Uri uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        AppSettings.setAlarmTone(activity,
                uri == null || uri.equals(Settings.System.DEFAULT_ALARM_ALERT_URI) ? null : uri.toString());
        renderSound();
    }

    // ================================================================== display

    private void renderDisplay() {
        display.removeAllViews();
        String theme = AppSettings.theme(activity);
        String[] themeNames = {"Dark", "Light", "Same as phone"};
        String[] themeKeys = {AppSettings.THEME_DARK, AppSettings.THEME_LIGHT, AppSettings.THEME_SYSTEM};
        int ti = AppSettings.THEME_LIGHT.equals(theme) ? 1 : AppSettings.THEME_SYSTEM.equals(theme) ? 2 : 0;
        valueRow(display, "Theme", "Colours of the app", themeNames[ti],
                () -> choose("Theme", themeNames, ti, i -> AppSettings.setTheme(activity, themeKeys[i])));
        boolean h24 = AppSettings.use24h(activity);
        valueRow(display, "Time format", h24 ? "Example: 14:30" : "Example: 02:30 PM", h24 ? "24-hour" : "12-hour",
                () -> choose("Time format", new String[]{"12-hour (02:30 PM)", "24-hour (14:30)"}, h24 ? 1 : 0,
                        i -> AppSettings.setUse24h(activity, i == 1)));
    }

    // ================================================================== backup

    private void renderBackup() {
        backup.removeAllViews();
        valueRow(backup, "Back up to a file", "Saves all alarms, volume modes and settings", "Save", () -> {
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new Date());
            try {
                backupFile.launch("AlertAlarm-backup-" + date + ".json");
            } catch (ActivityNotFoundException e) {
                toast("No file app found on this phone");
            }
        });
        valueRow(backup, "Restore from a file", "Adds alarms from a backup (e.g. on a new phone)", "Open", () -> {
            try {
                restoreFile.launch(new String[]{"application/json", "text/plain", "application/octet-stream"});
            } catch (ActivityNotFoundException e) {
                toast("No file app found on this phone");
            }
        });
    }

    private void writeBackup(Uri uri) {
        try {
            JSONObject root = new JSONObject();
            root.put("app", "AlertAlarm");
            root.put("backup_version", 1);
            JSONArray reminders = new JSONArray();
            for (Reminder r : ReminderStore.getAll(activity)) {
                reminders.put(r.toJson());
            }
            root.put("reminders", reminders);
            JSONArray volumes = new JSONArray();
            for (VolumeProfile v : VolumeStore.getAll(activity)) {
                volumes.put(v.toJson());
            }
            root.put("volume", volumes);
            JSONObject settings = new JSONObject();
            AppSettings.exportTo(activity, settings);
            root.put("settings", settings);
            try (OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
                if (out == null) {
                    throw new java.io.IOException("Could not open the file");
                }
                out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            toast("Backup saved: " + reminders.length() + " alarms, " + volumes.length() + " volume modes");
        } catch (Exception e) {
            toast("Could not save the backup: " + e.getMessage());
        }
    }

    private void readBackup(Uri uri) {
        JSONObject root;
        try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new java.io.IOException("Could not open the file");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bytes.write(buf, 0, n);
                if (bytes.size() > 5 * 1024 * 1024) {
                    throw new java.io.IOException("File is too big");
                }
            }
            root = new JSONObject(bytes.toString("UTF-8"));
            if (!"AlertAlarm".equals(root.optString("app"))) {
                throw new IllegalArgumentException("This is not an AlertAlarm backup file");
            }
        } catch (Exception e) {
            toast("Could not read the backup: " + e.getMessage());
            return;
        }

        List<Reminder> reminders = new ArrayList<>();
        List<VolumeProfile> volumes = new ArrayList<>();
        try {
            JSONArray r = root.optJSONArray("reminders");
            for (int i = 0; r != null && i < r.length(); i++) {
                reminders.add(Reminder.fromJson(r.getJSONObject(i)));
            }
            JSONArray v = root.optJSONArray("volume");
            for (int i = 0; v != null && i < v.length(); i++) {
                volumes.add(VolumeProfile.fromJson(v.getJSONObject(i)));
            }
        } catch (Exception e) {
            toast("This backup file is damaged");
            return;
        }
        JSONObject settings = root.optJSONObject("settings");
        new AlertDialog.Builder(activity)
                .setTitle("Restore backup?")
                .setMessage("Add " + reminders.size() + " alarms from this backup and restore " + volumes.size()
                        + " volume modes?" + (settings != null ? " Settings will be restored too." : "")
                        + "\n\nAlarms already on this phone are kept.")
                .setPositiveButton("Restore", (d, w) -> restore(reminders, volumes, settings))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restore(List<Reminder> reminders, List<VolumeProfile> volumes, JSONObject settings) {
        List<Long> existing = new ArrayList<>();
        for (Reminder r : ReminderStore.getAll(activity)) {
            existing.add(r.id);
        }
        int added = 0;
        for (Reminder r : reminders) {
            if (!existing.contains(r.id)) {
                ReminderStore.save(activity, r);
                added++;
            }
        }
        // Repeating alarms move to their next time; one-time alarms that are over become done
        ReminderStore.markExpiredAndGetActive(activity, System.currentTimeMillis());
        for (Reminder r : ReminderStore.getAll(activity)) {
            if (!r.done && r.enabled) {
                AlarmScheduler.schedule(activity, r);
            }
        }
        // Volume modes: the backup's levels and times replace these (on/off state is not restored)
        for (VolumeProfile v : volumes) {
            v.manualOn = false;
            v.skipUntil = 0;
            VolumeStore.save(activity, v);
        }
        VolumeManager.reconcile(activity);
        if (settings != null) {
            AppSettings.importFrom(activity, settings);
            AppSettings.applyTheme(AppSettings.theme(activity));
        }
        toast("Restored " + added + " alarms");
        render();
    }

    // ================================================================== rows

    private interface Action {
        void run();
    }

    private interface Pick {
        void picked(int index);
    }

    private void valueRow(LinearLayout parent, String title, String desc, String value, Action onClick) {
        View row = inflater.inflate(R.layout.item_setting, parent, false);
        ((TextView) row.findViewById(R.id.settingTitle)).setText(title);
        ((TextView) row.findViewById(R.id.settingDesc)).setText(desc);
        TextView action = row.findViewById(R.id.settingAction);
        action.setText(value);
        action.setTextColor(Ui.color(activity, R.color.accent));
        action.setBackgroundResource(R.drawable.bg_chip);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> onClick.run());
        action.setOnClickListener(v -> onClick.run());
        parent.addView(row);
    }

    private void switchRow(LinearLayout parent, String title, String desc, boolean checked,
                           CompoundButton.OnCheckedChangeListener listener) {
        View row = inflater.inflate(R.layout.item_setting_switch, parent, false);
        ((TextView) row.findViewById(R.id.settingTitle)).setText(title);
        ((TextView) row.findViewById(R.id.settingDesc)).setText(desc);
        MaterialSwitch sw = row.findViewById(R.id.settingSwitch);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(listener);
        row.setOnClickListener(v -> sw.toggle());
        parent.addView(row);
    }

    private void choose(String title, String[] options, int selected, Pick pick) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setSingleChoiceItems(options, selected, (d, which) -> {
                    d.dismiss();
                    pick.picked(which);
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static int indexOf(int[] values, int value, int fallback) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return fallback;
    }

    private void toast(String text) {
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
    }
}
