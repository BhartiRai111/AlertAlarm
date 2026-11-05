package com.bharti.alertalarm;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.Paint;
import android.text.InputType;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Locale;

/** Full-screen alarm screen. The stop button stays disabled until the end time. */
public class RingingActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView taskView;
    private TextView countdownView;
    private TextView untilView;
    private TextView stopButton;
    private TextView lockPill;
    private TextView leftLabel;
    private RingProgressView ring;
    private View root;
    private View doneRow;
    private TextView emergencyButton;
    private TextView footer;
    /** The tasks that were ringing, so we can ask "Did you finish it?" when they end. */
    private final List<TaskCheck.Rang> rang = new ArrayList<>();
    private final Random random = new Random();
    private ValueAnimator colors;

    /** Colours the alarm screen cycles through while it rings (last = first, so the loop is smooth). */
    private static final int[] RING_COLORS = {
            0xFF0F766E, 0xFF1D4ED8, 0xFF7C3AED, 0xFFBE185D, 0xFFC2410C, 0xFF15803D, 0xFF0F766E};
    /** Time to fade from one colour to the next. */
    private static final long COLOR_STEP_MS = 600L;
    private long lockedUntil = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, 1_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_ringing);

        taskView = findViewById(R.id.taskText);
        countdownView = findViewById(R.id.countdownText);
        untilView = findViewById(R.id.untilText);
        stopButton = findViewById(R.id.stopButton);
        lockPill = findViewById(R.id.lockPill);
        leftLabel = findViewById(R.id.leftLabel);
        ring = findViewById(R.id.ringView);
        root = findViewById(R.id.ringRoot);
        doneRow = findViewById(R.id.doneRow);
        emergencyButton = findViewById(R.id.emergencyButton);
        footer = findViewById(R.id.footerText);
        ((TextView) findViewById(R.id.quoteText)).setText("“" + Quotes.random(this) + "”");
        emergencyButton.setPaintFlags(emergencyButton.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        emergencyButton.setOnClickListener(v -> askEmergency());
        findViewById(R.id.doneButton).setOnClickListener(v -> answer(TaskCheck.COMPLETED));
        findViewById(R.id.notDoneButton).setOnClickListener(v -> answer(TaskCheck.NOT_DONE));
        stopButton.setOnClickListener(v -> finish());

        // Back button cannot close the alarm screen while it is locked.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (System.currentTimeMillis() >= lockedUntil) {
                    finish();
                } else {
                    Toast.makeText(RingingActivity.this,
                            "This alarm cannot be stopped before " + TimeText.time(lockedUntil),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        stopColors();
    }

    /** Smoothly changes the background colour again and again while the alarm rings. */
    private void startColors() {
        if (colors != null) {
            return;
        }
        colors = ValueAnimator.ofObject(new ArgbEvaluator(), (Object[]) boxed(RING_COLORS));
        colors.setDuration(COLOR_STEP_MS * (RING_COLORS.length - 1));
        colors.setInterpolator(new LinearInterpolator());
        colors.setRepeatCount(ValueAnimator.INFINITE);
        colors.addUpdateListener(a -> paint((Integer) a.getAnimatedValue()));
        colors.start();
    }

    private void stopColors() {
        if (colors != null) {
            colors.cancel();
            colors = null;
        }
    }

    private void paint(int color) {
        root.setBackgroundColor(color);
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);
    }

    private static Integer[] boxed(int[] values) {
        Integer[] out = new Integer[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[i];
        }
        return out;
    }

    private void update() {
        long now = System.currentTimeMillis();
        List<Reminder> active = ReminderStore.getActive(this, now);

        if (active.isEmpty()) {
            lockedUntil = 0;
            lockPill.setText("Unlocked");
            Ui.tintStart(lockPill, R.drawable.ic_check, R.color.white);
            countdownText("Done");
            leftLabel.setText("time's up");
            ring.setProgress(0f);
            emergencyButton.setVisibility(View.GONE);
            stopColors();
            paint(Ui.color(this, R.color.bg));
            if (!rang.isEmpty()) {
                // Ask "Did you finish it?" right here
                untilView.setText("Did you finish \"" + rang.get(0).task + "\"" + (rang.size() > 1 ? " and the others" : "") + "?");
                stopButton.setVisibility(View.GONE);
                footer.setVisibility(View.GONE);
                doneRow.setVisibility(View.VISIBLE);
            } else {
                untilView.setText("The alarm has stopped. Well done!");
                stopButton.setEnabled(true);
                stopButton.setText("OK");
            }
            return;
        }
        rang.clear();
        for (Reminder r : active) {
            rang.add(new TaskCheck.Rang(r.id, r.startMillis, r.task));
        }
        doneRow.setVisibility(View.GONE);
        stopButton.setVisibility(View.VISIBLE);
        emergencyButton.setVisibility(View.VISIBLE);
        footer.setVisibility(View.VISIBLE);

        AlarmService.ensureRunning(this);
        startColors();

        StringBuilder tasks = new StringBuilder();
        long start = Long.MAX_VALUE;
        long end = 0;
        for (Reminder r : active) {
            if (tasks.length() > 0) {
                tasks.append('\n');
            }
            tasks.append(r.task);
            start = Math.min(start, r.startMillis);
            end = Math.max(end, r.endMillis);
        }
        lockedUntil = end;

        long left = Math.max(0, end - now) / 1000;
        String endText = TimeText.time(end);
        taskView.setText(tasks);
        countdownText(left >= 3600
                ? String.format(Locale.ENGLISH, "%d:%02d:%02d", left / 3600, (left % 3600) / 60, left % 60)
                : String.format(Locale.ENGLISH, "%02d:%02d", left / 60, left % 60));
        leftLabel.setText("left to ring");
        ring.setProgress(end > start ? (float) (end - now) / (end - start) : 0f);
        lockPill.setText("Locked until " + endText);
        Ui.tintStart(lockPill, R.drawable.ic_lock, R.color.white);
        if (VolumeManager.isMeetingNow(this)) {
            untilView.setText("Meeting mode is on, so the alarm is silent: vibration only.");
        } else if (!AppSettings.voiceOn(this)) {
            untilView.setText("Voice announcements are off (Settings)."
                    + (AppSettings.volumeLock(this) ? " Volume stays at max." : ""));
        } else {
            untilView.setText("\"" + active.get(0).task + "\" is spoken every " + AppSettings.voiceEvery(this)
                    + " seconds." + (AppSettings.volumeLock(this) ? " Volume stays at max." : ""));
        }
        stopButton.setEnabled(false);
        stopButton.setText("Stop (available at " + endText + ")");
    }

    private void countdownText(String text) {
        countdownView.setText(text);
    }

    private void answer(int result) {
        for (TaskCheck.Rang t : rang) {
            TaskCheck.record(this, t.id, t.start, result);
        }
        Toast.makeText(this, result == TaskCheck.COMPLETED ? "Great job! ✅" : "Okay, next time! 💪", Toast.LENGTH_SHORT).show();
        finish();
    }

    // ------------------------------------------------------------------ emergency stop

    /** Real emergency: solving a hard multiplication stops the alarm early. Every use is written in the log. */
    private void askEmergency() {
        int a = 12 + random.nextInt(87);
        int b = 12 + random.nextInt(87);
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Answer");
        int pad = (int) (22 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, pad / 3, pad, 0);
        box.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Emergency stop")
                .setMessage("Solve this to stop the alarm early:\n\n" + a + " × " + b + " = ?")
                .setView(box)
                .setPositiveButton("Stop alarm", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.equals(String.valueOf(a * b))) {
                dialog.dismiss();
                emergencyStop();
            } else {
                dialog.dismiss();
                Toast.makeText(this, "Wrong answer. Try a new one.", Toast.LENGTH_SHORT).show();
                askEmergency();
            }
        }));
        dialog.show();
    }

    private void emergencyStop() {
        List<Reminder> stopped = ReminderStore.stopNow(this, System.currentTimeMillis());
        StringBuilder names = new StringBuilder();
        for (Reminder r : stopped) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(r.task);
        }
        AlarmLog.add(this, "🚨 Emergency stop used: " + names);
        update();
    }
}
