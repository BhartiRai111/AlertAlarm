package com.bharti.alertalarm;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;

/** One alarm: time, task, repeat rule, next occurrences, on/off, edit and delete. */
public class DetailActivity extends AppCompatActivity {

    static final String EXTRA_ID = "id";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 30_000L);
        }
    };

    private long id;
    private MaterialSwitch onOff;
    private TextView repeatPill;
    private TextView time;
    private TextView task;
    private TextView status;
    private TextView end;
    private TextView duration;
    private TextView nextLabel;
    private LinearLayout nextList;
    private TextView lock;
    private TextView deleteButton;
    private TextView editButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        id = getIntent().getLongExtra(EXTRA_ID, -1);

        onOff = findViewById(R.id.detailSwitch);
        repeatPill = findViewById(R.id.detailRepeat);
        time = findViewById(R.id.detailTime);
        task = findViewById(R.id.detailTask);
        status = findViewById(R.id.detailStatus);
        end = findViewById(R.id.detailEnd);
        duration = findViewById(R.id.detailDuration);
        nextLabel = findViewById(R.id.nextLabel);
        nextList = findViewById(R.id.nextList);
        lock = findViewById(R.id.detailLock);
        deleteButton = findViewById(R.id.deleteButton);
        editButton = findViewById(R.id.editButton);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
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

    private Reminder find() {
        ReminderStore.markExpiredAndGetActive(this, System.currentTimeMillis());
        for (Reminder r : ReminderStore.getAll(this)) {
            if (r.id == id) {
                return r;
            }
        }
        return null;
    }

    private void render() {
        Reminder r = find();
        if (r == null) {
            finish();
            return;
        }
        long now = System.currentTimeMillis();
        boolean active = r.isActive(now);
        long length = r.endMillis - r.startMillis;

        repeatPill.setText(r.repeatLabel());
        Ui.tintStart(repeatPill, r.isRepeating() ? R.drawable.ic_repeat : R.drawable.ic_clock, R.color.accent);
        time.setText(Ui.bigTime(r.startMillis));
        task.setText(r.task);
        end.setText(TimeText.time(r.endMillis));
        duration.setText(TimeText.duration(length));

        if (r.done) {
            status.setText("Done · rang " + TimeText.dayLabel(r.startMillis) + ", " + TimeText.time(r.startMillis)
                    + TaskCheck.label(r));
        } else if (active) {
            status.setText("Ringing now · locked until " + TimeText.time(r.endMillis));
        } else if (!r.enabled) {
            status.setText("Off. It will not ring until you turn it on.");
        } else {
            status.setText("Rings " + TimeText.dayLabel(r.startMillis) + ", in " + TimeText.until(r.startMillis));
        }

        // Next occurrences
        List<Long> starts = Ui.nextStarts(r, 3);
        nextLabel.setText(r.isRepeating() ? "Next 3 alarms" : "Date");
        nextList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < starts.size(); i++) {
            long s = starts.get(i);
            View row = inflater.inflate(R.layout.item_next, nextList, false);
            row.findViewById(R.id.nextDivider).setVisibility(i == 0 ? View.GONE : View.VISIBLE);
            ((TextView) row.findViewById(R.id.nextDay)).setText(TimeText.dayLabel(s));
            ((TextView) row.findViewById(R.id.nextTime)).setText(
                    TimeText.time(s) + " – " + TimeText.time(s + length));
            nextList.addView(row);
        }

        lock.setText("Locked alarm. Once it starts, it speaks your task out loud and keeps ringing until "
                + TimeText.time(r.endMillis) + ". Nobody can stop it before that.");

        // On/off switch (hidden for finished one-time alarms)
        onOff.setOnCheckedChangeListener(null);
        onOff.setVisibility(r.done ? View.INVISIBLE : View.VISIBLE);
        onOff.setChecked(r.enabled && !r.done);
        onOff.setEnabled(!active);
        onOff.setOnCheckedChangeListener((b, checked) -> {
            Alarms.toggle(this, r, checked);
            render();
        });

        // Edit / delete are locked while it rings
        deleteButton.setEnabled(!active);
        editButton.setEnabled(!active);
        deleteButton.setAlpha(active ? 0.4f : 1f);
        editButton.setAlpha(active ? 0.4f : 1f);
        deleteButton.setOnClickListener(v -> Alarms.confirmDelete(this, r, this::finish));
        editButton.setText(r.done ? "Set again" : "Edit alarm");
        editButton.setOnClickListener(v -> startActivity(new Intent(this, AddReminderActivity.class)
                .putExtra(AddReminderActivity.EXTRA_ID, r.id)));
    }
}
