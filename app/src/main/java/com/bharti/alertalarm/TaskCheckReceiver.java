package com.bharti.alertalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** The Yes / No buttons on the "Did you finish it?" notification. */
public class TaskCheckReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TaskCheck.ACTION_ANSWER.equals(intent.getAction())) {
            return;
        }
        TaskCheck.record(context, intent.getLongExtra(TaskCheck.EXTRA_ID, 0),
                intent.getLongExtra(TaskCheck.EXTRA_START, 0), intent.getIntExtra(TaskCheck.EXTRA_RESULT, 0));
    }
}
