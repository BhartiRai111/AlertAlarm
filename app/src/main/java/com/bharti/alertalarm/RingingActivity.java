package com.bharti.alertalarm;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
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
    }

    private void update() {
        long now = System.currentTimeMillis();
        List<Reminder> active = ReminderStore.getActive(this, now);

        if (active.isEmpty()) {
            lockedUntil = 0;
            lockPill.setText("Unlocked");
            Ui.tintStart(lockPill, R.drawable.ic_check, R.color.accent);
            countdownText("Done");
            leftLabel.setText("time's up");
            ring.setProgress(0f);
            untilView.setText("The alarm has stopped. Well done!");
            stopButton.setEnabled(true);
            stopButton.setText("OK");
            return;
        }

        AlarmService.ensureRunning(this);

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
        Ui.tintStart(lockPill, R.drawable.ic_lock, R.color.accent);
        untilView.setText("\"" + active.get(0).task + "\" is spoken every 20 seconds. Volume stays at max.");
        stopButton.setEnabled(false);
        stopButton.setText("Stop (available at " + endText + ")");
    }

    private void countdownText(String text) {
        countdownView.setText(text);
    }
}
