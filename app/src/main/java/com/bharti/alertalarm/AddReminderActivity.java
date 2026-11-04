package com.bharti.alertalarm;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Add an alarm, in this order:
 * 1. Repeat: Once / Daily / Monthly / Yearly
 * 2. Once -> date, Monthly -> day of month, Yearly -> month + day, Daily -> nothing
 * 3. Start time, and how long it rings (end time OR minutes/seconds, kept in sync)
 */
public class AddReminderActivity extends AppCompatActivity {

    /** Edit an existing alarm (its id). */
    static final String EXTRA_ID = "id";
    /** New alarm on this day (start of day, from the calendar). */
    static final String EXTRA_DATE = "date";

    /** Testing mode: a start time in the current minute is allowed and rings right away. */
    private static final long START_GRACE_MS = 60_000L;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH);

    private Reminder.Repeat repeat = Reminder.Repeat.ONCE;
    /** Date (for Once) and start time (for all). */
    private final Calendar start = Calendar.getInstance();
    private int dayOfMonth;
    private int month;
    private long durationMs = 30 * 60_000L;
    private boolean updatingFields = false;
    private long editId = 0;

    private EditText taskInput;
    private EditText minutesInput;
    private EditText secondsInput;
    private TextView dateButton;
    private TextView startButton;
    private TextView endButton;
    private TextView dayLabel;
    private TextView[] repeatButtons;
    private View dateRow;
    private View spinnerRow;
    private View monthRow;
    private View dayRow;
    private Spinner monthSpinner;
    private Spinner daySpinner;
    private TextView preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_reminder);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // Defaults: start in 5 minutes, ring for 30 minutes
        start.add(Calendar.MINUTE, 5);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        long pickedDay = getIntent().getLongExtra(EXTRA_DATE, 0);
        if (pickedDay > 0) {
            Calendar day = Calendar.getInstance();
            day.setTimeInMillis(pickedDay);
            start.set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH));
        }
        dayOfMonth = start.get(Calendar.DAY_OF_MONTH);
        month = start.get(Calendar.MONTH);

        taskInput = findViewById(R.id.taskInput);
        minutesInput = findViewById(R.id.minutesInput);
        secondsInput = findViewById(R.id.secondsInput);
        dateButton = findViewById(R.id.dateButton);
        startButton = findViewById(R.id.startText);
        endButton = findViewById(R.id.endText);
        dayLabel = findViewById(R.id.dayLabel);
        dateRow = findViewById(R.id.dateRow);
        spinnerRow = findViewById(R.id.spinnerRow);
        monthRow = findViewById(R.id.monthRow);
        dayRow = findViewById(R.id.dayRow);
        monthSpinner = findViewById(R.id.monthSpinner);
        daySpinner = findViewById(R.id.daySpinner);
        preview = findViewById(R.id.previewText);
        Ui.tintStart(dateButton, R.drawable.ic_calendar, R.color.muted);

        editId = getIntent().getLongExtra(EXTRA_ID, 0);
        if (editId != 0) {
            loadForEdit(editId);
        }

        setUpRepeatButtons();
        setUpSpinners();

        dateButton.setOnClickListener(v -> pickDate());
        findViewById(R.id.startCard).setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            start.set(Calendar.HOUR_OF_DAY, h);
            start.set(Calendar.MINUTE, m);
            render();
        }, start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE), false).show());
        findViewById(R.id.endCard).setOnClickListener(v -> pickEndTime());

        TextWatcher durationWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int before, int count) {
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
        showRowsForRepeat();
        render();
    }

    /** Fill the form from a saved alarm. A finished one-time alarm gets the next possible day. */
    private void loadForEdit(long id) {
        Reminder found = null;
        for (Reminder r : ReminderStore.getAll(this)) {
            if (r.id == id) {
                found = r;
            }
        }
        if (found == null) {
            editId = 0;
            return;
        }
        ((TextView) findViewById(R.id.screenTitle)).setText("Edit alarm");
        ((TextView) findViewById(R.id.saveButton)).setText("Save changes");
        taskInput.setText(found.task);
        repeat = found.repeat;
        start.setTimeInMillis(found.startMillis);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        if (found.done) {
            Calendar today = Calendar.getInstance();
            start.set(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH));
            if (start.getTimeInMillis() < System.currentTimeMillis()) {
                start.add(Calendar.DAY_OF_MONTH, 1);
            }
        }
        dayOfMonth = found.dayOfMonth;
        month = found.month;
        durationMs = found.durationMs > 0 ? found.durationMs : found.endMillis - found.startMillis;
    }

    // ------------------------------------------------------------------ repeat + date fields

    private void setUpRepeatButtons() {
        repeatButtons = new TextView[]{
                findViewById(R.id.repeatOnce), findViewById(R.id.repeatDaily),
                findViewById(R.id.repeatMonthly), findViewById(R.id.repeatYearly)};
        Reminder.Repeat[] values = Reminder.Repeat.values();
        for (int i = 0; i < repeatButtons.length; i++) {
            Reminder.Repeat value = values[i];
            repeatButtons[i].setOnClickListener(v -> {
                repeat = value;
                showRowsForRepeat();
                render();
            });
        }
    }

    private void showRowsForRepeat() {
        for (int i = 0; i < repeatButtons.length; i++) {
            Ui.segment(repeatButtons[i], Reminder.Repeat.values()[i] == repeat);
        }
        dateRow.setVisibility(repeat == Reminder.Repeat.ONCE ? View.VISIBLE : View.GONE);
        spinnerRow.setVisibility(repeat == Reminder.Repeat.MONTHLY || repeat == Reminder.Repeat.YEARLY
                ? View.VISIBLE : View.GONE);
        dayLabel.setText(repeat == Reminder.Repeat.YEARLY ? "Day" : "Day of the month");
        monthRow.setVisibility(repeat == Reminder.Repeat.YEARLY ? View.VISIBLE : View.GONE);
        dayRow.setVisibility(repeat == Reminder.Repeat.MONTHLY || repeat == Reminder.Repeat.YEARLY
                ? View.VISIBLE : View.GONE);
    }

    private void setUpSpinners() {
        ArrayAdapter<String> months = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new DateFormatSymbols(Locale.ENGLISH).getMonths());
        // getMonths() has a 13th empty entry on some JDKs; keep the first 12
        if (months.getCount() > 12) {
            List<String> twelve = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                twelve.add(months.getItem(i));
            }
            months = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, twelve);
        }
        monthSpinner.setAdapter(months);
        monthSpinner.setSelection(month);
        monthSpinner.setOnItemSelectedListener(new SimpleSelect(pos -> {
            month = pos;
            render();
        }));

        List<String> days = new ArrayList<>();
        for (int d = 1; d <= 31; d++) {
            days.add(Reminder.ordinal(d));
        }
        daySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, days));
        daySpinner.setSelection(dayOfMonth - 1);
        daySpinner.setOnItemSelectedListener(new SimpleSelect(pos -> {
            dayOfMonth = pos + 1;
            render();
        }));
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

    // ------------------------------------------------------------------ duration (end time <-> min/sec)

    private void pickEndTime() {
        Calendar currentEnd = Calendar.getInstance();
        currentEnd.setTimeInMillis(start.getTimeInMillis() + durationMs);
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

    // ------------------------------------------------------------------ preview + save

    /** Builds the alarm from the form; its start/end are the first time it will ring. */
    private Reminder buildReminder(String task) {
        Reminder r = new Reminder(editId != 0 ? editId : System.currentTimeMillis(), task, start.getTimeInMillis(),
                start.getTimeInMillis() + Math.max(durationMs, 1));
        r.repeat = repeat;
        r.hour = start.get(Calendar.HOUR_OF_DAY);
        r.minute = start.get(Calendar.MINUTE);
        r.dayOfMonth = dayOfMonth;
        r.month = month;
        r.durationMs = Math.max(durationMs, 1);
        if (r.isRepeating()) {
            r.moveTo(r.occurrenceOnOrAfter(System.currentTimeMillis() - START_GRACE_MS));
        }
        return r;
    }

    private void render() {
        Reminder r = buildReminder("");
        long s = r.startMillis;
        long e = r.startMillis + durationMs;

        dateButton.setText(dateFormat.format(new Date(start.getTimeInMillis())));
        startButton.setText(Ui.bigTime(s));
        endButton.setText(TimeText.sameDay(s, e) ? Ui.bigTime(e)
                : android.text.TextUtils.concat(Ui.bigTime(e), " +1 day"));

        if (durationMs <= 0) {
            preview.setText("Enter how long the alarm should ring.");
            return;
        }
        String when = r.isRepeating()
                ? r.repeatLabel() + ", from " + TimeText.time(s) + " to " + TimeText.time(e)
                : dateFormat.format(new Date(s)) + ", from " + TimeText.time(s) + " to " + TimeText.time(e);
        String first = r.isRepeating()
                ? "\nFirst alarm: " + TimeText.dayLabel(s) + ", " + TimeText.time(s) + "."
                : "";
        preview.setText(when + " (" + TimeText.duration(durationMs) + ")." + first
                + "\nNobody can stop it before the end time.");
    }

    private void save() {
        String task = taskInput.getText().toString().trim();
        if (task.isEmpty()) {
            taskInput.setError("Please enter a task");
            return;
        }
        if (durationMs <= 0) {
            Toast.makeText(this, "Enter how long the alarm should ring", Toast.LENGTH_LONG).show();
            return;
        }

        long now = System.currentTimeMillis();
        if (editId != 0) {
            for (Reminder old : ReminderStore.getActive(this, now)) {
                if (old.id == editId) {
                    Toast.makeText(this, "This alarm is ringing now. Edit it after "
                            + TimeText.time(old.endMillis) + ".", Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
        Reminder r = buildReminder(task);
        if (r.startMillis < now - START_GRACE_MS) {
            Toast.makeText(this, "This start time has already passed", Toast.LENGTH_LONG).show();
            return;
        }
        if (r.startMillis < now) {
            // Start time is in the current minute: ring now, keep the same end time
            r.startMillis = now;
            r.endMillis = Math.max(r.endMillis, now + 1_000L);
        }

        if (editId != 0) {
            AlarmScheduler.cancel(this, editId);
        }
        ReminderStore.save(this, r);
        AlarmScheduler.schedule(this, r);

        Toast.makeText(this, (r.isRepeating() ? r.repeatLabel() + ". First alarm: " : "Alarm set for ")
                + TimeText.dayLabel(r.startMillis) + ", " + TimeText.time(r.startMillis), Toast.LENGTH_LONG).show();

        // Go back to the dashboard (alarm list), like the Clock app
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    /** Spinner listener that only cares about the selected position. */
    private static final class SimpleSelect implements AdapterView.OnItemSelectedListener {
        interface OnPick {
            void pick(int position);
        }

        private final OnPick onPick;

        SimpleSelect(OnPick onPick) {
            this.onPick = onPick;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            onPick.pick(position);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }
}
