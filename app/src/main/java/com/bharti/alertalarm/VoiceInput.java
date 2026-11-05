package com.bharti.alertalarm;

import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;

import androidx.activity.result.ActivityResult;

import com.bharti.alertalarm.imports.ImportItem;
import com.bharti.alertalarm.imports.VoiceParser;

import java.util.ArrayList;
import java.util.Calendar;

/** "Say it": the phone's own speech-to-text, then the spoken words become a task. */
final class VoiceInput {

    private VoiceInput() {
    }

    static Intent recognizeIntent() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your task, e.g. \"Kal shaam 6 baje gym 1 ghanta\"");
    }

    /** The words heard, or null. */
    static String heard(ActivityResult result) {
        if (result.getResultCode() != android.app.Activity.RESULT_OK || result.getData() == null) {
            return null;
        }
        ArrayList<String> list = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    /** Parsed task with a real start time (a time already passed today moves to tomorrow), or null. */
    static ImportItem parse(String spoken) {
        long now = System.currentTimeMillis();
        Calendar today = Calendar.getInstance();
        ImportItem item = VoiceParser.parse(spoken, today);
        if (item == null) {
            return null;
        }
        boolean hadDate = item.hasDate();
        if (!hadDate) {
            item.setDate(today);
            if (item.repeat == ImportItem.Repeat.ONCE && item.startMillis(now) < now - 60_000L) {
                Calendar tomorrow = (Calendar) today.clone();
                tomorrow.add(Calendar.DAY_OF_MONTH, 1);
                item.setDate(tomorrow);
            }
        }
        return item;
    }

    /** Opens "New alarm" filled in from the spoken words, to check and save. */
    static Intent addScreenFor(Context c, String spoken) {
        Intent i = new Intent(c, AddReminderActivity.class).putExtra(AddReminderActivity.EXTRA_SPOKEN, spoken);
        ImportItem item = parse(spoken);
        if (item == null) {
            return i.putExtra(AddReminderActivity.EXTRA_TASK, spoken);
        }
        return i.putExtra(AddReminderActivity.EXTRA_TASK, item.task)
                .putExtra(AddReminderActivity.EXTRA_START, item.startMillis(System.currentTimeMillis()))
                .putExtra(AddReminderActivity.EXTRA_DURATION, item.durationMs())
                .putExtra(AddReminderActivity.EXTRA_REPEAT, item.repeat.name());
    }
}
