package com.bharti.alertalarm;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Add a task: what, which date, start time, and until when it must keep ringing. */
public class AddReminderActivity extends AppCompatActivity {

    private static final long MAX_DURATION_MS = 12 * 60 * 60 * 1000L;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.ENGLISH);

    /** Holds the date AND the start time. */
    private final Calendar start = Calendar.getInstance();
    private int endHour;
    private int endMinute;

    private EditText taskInput;
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
        Calendar defaultEnd = (Calendar) start.clone();
        defaultEnd.add(Calendar.MINUTE, 30);
        endHour = defaultEnd.get(Calendar.HOUR_OF_DAY);
        endMinute = defaultEnd.get(Calendar.MINUTE);

        taskInput = findViewById(R.id.taskInput);
        dateButton = findViewById(R.id.dateButton);
        startButton = findViewById(R.id.startButton);
        endButton = findViewById(R.id.endButton);
        preview = findViewById(R.id.previewText);

        dateButton.setOnClickListener(v -> pickDate());
        startButton.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            start.set(Calendar.HOUR_OF_DAY, h);
            start.set(Calendar.MINUTE, m);
            render();
        }, start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE), false).show());
        endButton.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            endHour = h;
            endMinute = m;
            render();
        }, endHour, endMinute, false).show());
        findViewById(R.id.saveButton).setOnClickListener(v -> save());

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

    /** End is on the same date; if the end time is earlier than the start time, it means the next day. */
    private long endMillis() {
        Calendar end = (Calendar) start.clone();
        end.set(Calendar.HOUR_OF_DAY, endHour);
        end.set(Calendar.MINUTE, endMinute);
        if (end.getTimeInMillis() <= start.getTimeInMillis()) {
            end.add(Calendar.DAY_OF_MONTH, 1);
        }
        return end.getTimeInMillis();
    }

    private void render() {
        long s = start.getTimeInMillis();
        long e = endMillis();
        boolean nextDay = !sameDay(s, e);

        dateButton.setText(dateFormat.format(new Date(s)));
        startButton.setText(timeFormat.format(new Date(s)));
        endButton.setText(timeFormat.format(new Date(e)) + (nextDay ? " (agle din)" : ""));

        long minutes = (e - s) / 60_000;
        String duration = minutes >= 60
                ? (minutes / 60) + " ghanta " + (minutes % 60) + " min"
                : minutes + " min";
        preview.setText("Alarm " + timeFormat.format(new Date(s)) + " par bajega aur "
                + timeFormat.format(new Date(e)) + " tak (" + duration + ") bajta rahega.\n"
                + "Is beech koi bhi ise band nahi kar payega.");
    }

    private void save() {
        String task = taskInput.getText().toString().trim();
        long s = start.getTimeInMillis();
        long e = endMillis();

        if (task.isEmpty()) {
            taskInput.setError("Kaam likho");
            return;
        }
        if (s <= System.currentTimeMillis()) {
            Toast.makeText(this, "Start time aage ka hona chahiye", Toast.LENGTH_LONG).show();
            return;
        }
        if (e - s > MAX_DURATION_MS) {
            Toast.makeText(this, "Alarm 12 ghante se zyada nahi baj sakta", Toast.LENGTH_LONG).show();
            return;
        }

        Reminder reminder = new Reminder(System.currentTimeMillis(), task, s, e);
        ReminderStore.save(this, reminder);
        AlarmScheduler.schedule(this, reminder);

        Toast.makeText(this, "Alarm set: " + dateFormat.format(new Date(s)) + ", "
                + timeFormat.format(new Date(s)), Toast.LENGTH_LONG).show();
        finish();
    }

    private static boolean sameDay(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }
}
