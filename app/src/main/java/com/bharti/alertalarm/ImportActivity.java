package com.bharti.alertalarm;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bharti.alertalarm.imports.FileImporter;
import com.bharti.alertalarm.imports.ImportItem;
import com.bharti.alertalarm.imports.SampleFiles;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Import tasks from a file:
 * 1. download a sample (PDF / Excel / Notes) to fill in,
 * 2. choose the filled file (or share text / a file to AlertAlarm),
 * 3. check the preview, edit or untick tasks, then "Set alarms".
 */
public class ImportActivity extends AppCompatActivity {

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<ImportItem> items = new ArrayList<>();

    private View introSection;
    private TextView statusText;
    private View previewSection;
    private TextView previewTitle;
    private TextView previewSubtitle;
    private LinearLayout previewList;
    private View previewButtons;
    private TextView setAllButton;

    /** Sample waiting to be saved (Android 9 and older ask where to save it). */
    private SampleFiles.Type pendingSample;
    private byte[] pendingBytes;

    private final ActivityResultLauncher<String[]> pickFile =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importUri(uri);
                }
            });

    private ActivityResultLauncher<String> saveAs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import);

        introSection = findViewById(R.id.introSection);
        statusText = findViewById(R.id.statusText);
        previewSection = findViewById(R.id.previewSection);
        previewTitle = findViewById(R.id.previewTitle);
        previewSubtitle = findViewById(R.id.previewSubtitle);
        previewList = findViewById(R.id.previewList);
        previewButtons = findViewById(R.id.previewButtons);
        setAllButton = findViewById(R.id.setAllButton);

        saveAs = registerForActivityResult(new ActivityResultContracts.CreateDocument("*/*"), uri -> {
            if (uri != null && pendingBytes != null) {
                writeSample(uri, pendingSample, pendingBytes);
            }
        });

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.samplePdf).setOnClickListener(v -> downloadSample(SampleFiles.Type.PDF));
        findViewById(R.id.sampleExcel).setOnClickListener(v -> downloadSample(SampleFiles.Type.EXCEL));
        findViewById(R.id.sampleNotes).setOnClickListener(v -> downloadSample(SampleFiles.Type.NOTES));
        findViewById(R.id.chooseFile).setOnClickListener(v -> {
            try {
                pickFile.launch(new String[]{"*/*"});
            } catch (ActivityNotFoundException e) {
                showStatus("No file picker found on this phone.");
            }
        });
        findViewById(R.id.cancelButton).setOnClickListener(v -> showIntro());
        setAllButton.setOnClickListener(v -> setAlarms());

        handleShare(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShare(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }

    /** Text or a file shared from another app ("Share > AlertAlarm"). */
    private void handleShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            return;
        }
        Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (stream != null) {
            importUri(stream);
            return;
        }
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text != null) {
            showResult(FileImporter.parseSharedText(text.toString()), "Shared text");
        }
    }

    // ================================================================== samples

    private void downloadSample(SampleFiles.Type type) {
        showStatus("Making the " + label(type) + " sample…");
        worker.execute(() -> {
            try {
                byte[] bytes = SampleFiles.build(getApplicationContext(), type);
                runOnUiThread(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveToDownloads(type, bytes);
                    } else {
                        pendingSample = type;
                        pendingBytes = bytes;
                        saveAs.launch(type.fileName);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> showStatus("Could not make the sample: " + e.getMessage()));
            }
        });
    }

    /** Android 10+: save straight into Downloads, no permission needed. */
    private void saveToDownloads(SampleFiles.Type type, byte[] bytes) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, type.fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, type.mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            showStatus("Could not save the sample in Downloads.");
            return;
        }
        if (writeSample(uri, type, bytes)) {
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
        }
    }

    private boolean writeSample(Uri uri, SampleFiles.Type type, byte[] bytes) {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) {
                throw new IOException("no output");
            }
            out.write(bytes);
        } catch (IOException e) {
            showStatus("Could not save the sample: " + e.getMessage());
            return false;
        }
        hideStatus();
        String where = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? "in your Downloads folder" : "where you chose";
        new AlertDialog.Builder(this)
                .setTitle(label(type) + " sample saved")
                .setMessage(type.fileName + " is saved " + where + ".\n\nFill in your tasks, save it, then come back here and tap \"Choose file\"."
                        + (type == SampleFiles.Type.PDF ? "\n\nTip: open the PDF in Google Drive or Adobe Acrobat to type in the boxes." : ""))
                .setPositiveButton("Open", (d, w) -> openFile(uri, type.mime))
                .setNegativeButton("OK", null)
                .show();
        return true;
    }

    private void openFile(Uri uri, String mime) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to open this file. Find it in your Downloads folder.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private static String label(SampleFiles.Type type) {
        return type == SampleFiles.Type.PDF ? "PDF" : type == SampleFiles.Type.EXCEL ? "Excel" : "Notes";
    }

    // ================================================================== import

    private void importUri(Uri uri) {
        showStatus("Reading the file…");
        worker.execute(() -> {
            String name = FileImporter.displayName(getApplicationContext(), uri);
            try {
                List<ImportItem> found = FileImporter.read(getApplicationContext(), uri);
                runOnUiThread(() -> showResult(found, name));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                runOnUiThread(() -> showStatus("Could not read " + name + ".\n" + msg));
            }
        });
    }

    private void showResult(List<ImportItem> found, String source) {
        if (found.isEmpty()) {
            showIntro();
            showStatus("No tasks found in " + source + ".\nEach task needs a start time and a name, for example:\n"
                    + "11:00 AM - 12:00 PM LeetCode\nDownload a sample above to see the format.");
            return;
        }
        items.clear();
        items.addAll(found);
        Importer.prepare(items, System.currentTimeMillis());
        hideStatus();
        introSection.setVisibility(View.GONE);
        previewSection.setVisibility(View.VISIBLE);
        previewButtons.setVisibility(View.VISIBLE);
        previewTitle.setText("Found " + items.size() + (items.size() == 1 ? " task" : " tasks"));
        previewSubtitle.setText("From " + source + ". Untick what you don't want, tap a task to change it.");
        renderPreview();
    }

    private void showIntro() {
        items.clear();
        introSection.setVisibility(View.VISIBLE);
        previewSection.setVisibility(View.GONE);
        previewButtons.setVisibility(View.GONE);
        hideStatus();
    }

    private void renderPreview() {
        long now = System.currentTimeMillis();
        previewList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ImportItem item : items) {
            View row = inflater.inflate(R.layout.item_import, previewList, false);
            CheckBox check = row.findViewById(R.id.importCheck);
            ((TextView) row.findViewById(R.id.importTask)).setText(item.task);
            ((TextView) row.findViewById(R.id.importMeta)).setText(Importer.describe(item, now));
            TextView note = row.findViewById(R.id.importNote);
            note.setVisibility(item.note == null ? View.GONE : View.VISIBLE);
            note.setText(item.note);
            check.setChecked(item.checked);
            check.setOnCheckedChangeListener((b, on) -> {
                item.checked = on;
                updateSetAll();
            });
            row.setOnClickListener(v -> edit(item));
            previewList.addView(row);
        }
        updateSetAll();
    }

    private void updateSetAll() {
        int n = 0;
        for (ImportItem item : items) {
            if (item.checked) {
                n++;
            }
        }
        setAllButton.setEnabled(n > 0);
        setAllButton.setText(n == 0 ? "Nothing ticked" : "Set " + n + (n == 1 ? " alarm" : " alarms"));
    }

    private void setAlarms() {
        long now = System.currentTimeMillis();
        long id = now;
        int count = 0;
        for (ImportItem item : items) {
            if (!item.checked) {
                continue;
            }
            Reminder r = Importer.toReminder(item, id++, now);
            ReminderStore.save(this, r);
            AlarmScheduler.schedule(this, r);
            count++;
        }
        Toast.makeText(this, count + (count == 1 ? " alarm set" : " alarms set"), Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    // ================================================================== edit one task

    private void edit(ImportItem original) {
        // Edit a copy; only "Save" changes the real task
        ImportItem item = copy(original);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_import_edit, null);
        EditText task = view.findViewById(R.id.editTask);
        TextView date = view.findViewById(R.id.editDate);
        TextView start = view.findViewById(R.id.editStart);
        TextView end = view.findViewById(R.id.editEnd);
        TextView[] repeats = {view.findViewById(R.id.editOnce), view.findViewById(R.id.editDaily),
                view.findViewById(R.id.editMonthly), view.findViewById(R.id.editYearly)};
        task.setText(item.task);

        Runnable refresh = () -> {
            for (int i = 0; i < repeats.length; i++) {
                Ui.segment(repeats[i], ImportItem.Repeat.values()[i] == item.repeat);
            }
            Calendar c = Calendar.getInstance();
            c.set(item.year, item.month, item.day);
            date.setVisibility(item.repeat == ImportItem.Repeat.DAILY ? View.GONE : View.VISIBLE);
            date.setText("Date: " + new SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH).format(c.getTime()));
            start.setText("Start " + clock(item.startMinutes));
            end.setText(item.endMinutes < 0 ? "End: 30 min" : "End " + clock(item.endMinutes));
        };
        for (int i = 0; i < repeats.length; i++) {
            ImportItem.Repeat value = ImportItem.Repeat.values()[i];
            repeats[i].setOnClickListener(v -> {
                item.repeat = value;
                refresh.run();
            });
        }
        date.setOnClickListener(v -> new DatePickerDialog(this, (p, y, m, d) -> {
            item.year = y;
            item.month = m;
            item.day = d;
            refresh.run();
        }, item.year, item.month, item.day).show());
        start.setOnClickListener(v -> new TimePickerDialog(this, (p, h, m) -> {
            item.startMinutes = h * 60 + m;
            refresh.run();
        }, item.startMinutes / 60, item.startMinutes % 60, false).show());
        end.setOnClickListener(v -> {
            int base = item.endMinutes < 0 ? item.startMinutes + 30 : item.endMinutes;
            new TimePickerDialog(this, (p, h, m) -> {
                item.endMinutes = h * 60 + m;
                refresh.run();
            }, (base / 60) % 24, base % 60, false).show();
        });
        refresh.run();

        new AlertDialog.Builder(this)
                .setTitle("Change task")
                .setView(view)
                .setPositiveButton("Save", (d, w) -> {
                    String t = task.getText().toString().trim();
                    original.task = t.isEmpty() ? original.task : t;
                    original.repeat = item.repeat;
                    original.year = item.year;
                    original.month = item.month;
                    original.day = item.day;
                    original.startMinutes = item.startMinutes;
                    original.endMinutes = item.endMinutes;
                    original.checked = true;
                    long now = System.currentTimeMillis();
                    Calendar today = Calendar.getInstance();
                    Importer.check(original, today, now, true);
                    renderPreview();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static ImportItem copy(ImportItem from) {
        ImportItem to = new ImportItem();
        to.task = from.task;
        to.repeat = from.repeat;
        to.year = from.year;
        to.month = from.month;
        to.day = from.day;
        to.startMinutes = from.startMinutes;
        to.endMinutes = from.endMinutes;
        return to;
    }

    private static String clock(int minutes) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, (minutes / 60) % 24);
        c.set(Calendar.MINUTE, minutes % 60);
        return new SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(c.getTime());
    }

    // ================================================================== status

    private void showStatus(String text) {
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(text);
    }

    private void hideStatus() {
        statusText.setVisibility(View.GONE);
    }
}
