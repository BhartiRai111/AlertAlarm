package com.bharti.alertalarm;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Add a task: what, which date, start time, and how long it must keep ringing.
 * The length can be set either by picking an end time OR by typing minutes/seconds;
 * both stay in sync.
 */
public class AddReminderActivity extends AppCompatActivity {

    /** Testing mode: a start time in the current minute is allowed and rings right away. */
    private static final long START_GRACE_MS = 60_000L;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH);

    /** Holds the date AND the start time. */
    private final Calendar start = Calendar.getInstance();
    /** How long the alarm rings. The end time is always start + duration. */
    private long durationMs = 30 * 60_000L;
    /** True while the code itself writes into the minute/second boxes (so the watcher ignores it). */
    private boolean updatingFields = false;

    private EditText taskInput;
    private EditText minutesInput;
    private EditText secondsInput;
    private Button dateButton;
    private Button startButton;
    private Button endButton;
    private TextView preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_reminder);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Defaults: start in 5 minutes, ring for 30 minutes
        start.add(Calendar.MINUTE, 5);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        taskInput = findViewById(R.id.taskInput);
        minutesInput = findViewById(R.id.minutesInput);
        secondsInput = findViewById(R.id.secondsInput);
        dateButton = findViewById(R.id.dateButton);
        startButton = findViewById(R.id.startButton);
        endButton = findViewById(R.id.endButton);
        preview = findViewById(R.id.previewText);

        dateButton.setOnClickListener(v -> pickDate());
        startButton.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            start.set(Calendar.HOUR_OF_DAY, h);
            start.set(Calendar.MINUTE, m);
            render();   // duration stays the same, so the end time moves with the start
        }, start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE), false).show());
        endButton.setOnClickListener(v -> pickEndTime());

        TextWatcher durationWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (updatingFields) {
                    return;
                }
                durationMs = readDurationFromFields();
                render();
            }
        };
        minutesInput.addTextChangedListener(durationWatcher);
        secondsInput.addTextChangedListener(durationWatcher);

        findViewById(R.id.saveButton).setOnClickListener(v -> save());

        writeDurationToFields();
        render();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void pickDate() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            start.set(Calendar.YEAR, y);
            start.set(Calendar.MONTH, m);
            start.set(Calendar.DAY_OF_MONTH, d);
            render();
        }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    /** Picking an end time updates the minute/second boxes. An earlier time than the start means the next day. */
    private void pickEndTime() {
        Calendar currentEnd = Calendar.getInstance();
        currentEnd.setTimeInMillis(endMillis());
        new TimePickerDialog(this, (view, h, m) -> {
            Calendar end = (Calendar) start.clone();
            end.set(Calendar.HOUR_OF_DAY, h);
            end.set(Calendar.MINUTE, m);
            end.set(Calendar.SECOND, 0);
            if (end.getTimeInMillis() <= start.getTimeInMillis()) {
                end.add(Calendar.DAY_OF_MONTH, 1);
            }
            durationMs = end.getTimeInMillis() - start.getTimeInMillis();
            writeDurationToFields();
            render();
        }, currentEnd.get(Calendar.HOUR_OF_DAY), currentEnd.get(Calendar.MINUTE), false).show();
    }

    private long readDurationFromFields() {
        long minutes = parse(minutesInput.getText().toString());
        long seconds = parse(secondsInput.getText().toString());
        return (minutes * 60 + seconds) * 1000L;
    }

    private void writeDurationToFields() {
        updatingFields = true;
        long totalSeconds = durationMs / 1000;
        minutesInput.setText(String.valueOf(totalSeconds / 60));
        secondsInput.setText(String.valueOf(totalSeconds % 60));
        updatingFields = false;
    }

    private static long parse(String text) {
        try {
            return text.trim().isEmpty() ? 0 : Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long endMillis() {
        return start.getTimeInMillis() + durationMs;
    }

    private void render() {
        long s = start.getTimeInMillis();
        long e = endMillis();

        dateButton.setText(dateFormat.format(new Date(s)));
        startButton.setText(TimeText.time(s));
        endButton.setText(TimeText.time(e) + (TimeText.sameDay(s, e) ? "" : " (next day)"));

        if (durationMs <= 0) {
            preview.setText("Enter how long the alarm should ring.");
            return;
        }
        preview.setText("The alarm will start at " + TimeText.time(s) + " and keep ringing until "
                + TimeText.time(e) + " (" + TimeText.duration(durationMs) + ").\n"
                + "Nobody can stop it before that.");
    }

    private void save() {
        String task = taskInput.getText().toString().trim();
        long s = start.getTimeInMillis();
        long e = endMillis();

        if (task.isEmpty()) {
            taskInput.setError("Please enter a task");
            return;
        }
        long now = System.currentTimeMillis();
        if (s < now - START_GRACE_MS) {
            Toast.makeText(this, "This start time has already passed", Toast.LENGTH_LONG).show();
            return;
        }
        if (durationMs <= 0) {
            Toast.makeText(this, "Enter how long the alarm should ring", Toast.LENGTH_LONG).show();
            return;
        }
        if (s < now) {
            // Start time is in the current minute: ring now, keep the same end time
            durationMs = Math.max(1_000L, e - now);
            s = now;
            e = s + durationMs;
        }

        Reminder reminder = new Reminder(System.currentTimeMillis(), task, s, e);
        ReminderStore.save(this, reminder);
        AlarmScheduler.schedule(this, reminder);

        Toast.makeText(this, "Alarm set for " + dateFormat.format(new Date(s)) + ", "
                + TimeText.time(s), Toast.LENGTH_LONG).show();

        // Go back to the dashboard (alarm list), like the Clock app
        startActivity(new android.content.Intent(this, MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
}
