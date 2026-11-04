package com.bharti.alertalarm.imports;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** The three sample files (PDF form, Excel, notes) with two example tasks. */
public final class SampleFiles {

    public enum Type {
        PDF("AlertAlarm-sample.pdf", "application/pdf"),
        EXCEL("AlertAlarm-sample.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        NOTES("AlertAlarm-sample.txt", "text/plain");

        public final String fileName;
        public final String mime;

        Type(String fileName, String mime) {
            this.fileName = fileName;
            this.mime = mime;
        }
    }

    private SampleFiles() {
    }

    private static List<String[]> examples() {
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);
        String date = new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(tomorrow.getTime());
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{date, "11:00 AM", "12:00 PM", "Once", "LeetCode - 2 problems"});
        rows.add(new String[]{"", "07:00 PM", "07:20 PM", "Daily", "Apply to 5 jobs"});
        return rows;
    }

    private static List<String> help() {
        return Arrays.asList(
                "Write one task per row. Start time and Task are required; the rest is optional.",
                "Date: DD-MM-YYYY (empty = today).  Start / End: like 11:00 AM or 14:30 (empty End = 30 minutes).",
                "Repeat: Once, Daily, Monthly or Yearly (empty = Once).  Replace or delete the two example rows.");
    }

    public static byte[] build(Context c, Type type) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        switch (type) {
            case PDF:
                PdfTasks.writeSample(c, out, examples(), help());
                break;
            case EXCEL: {
                List<String[]> rows = new ArrayList<>();
                rows.add(new String[]{"Date", "Start", "End", "Repeat", "Task"});
                rows.addAll(examples());
                List<String> sheetHelp = new ArrayList<>();
                sheetHelp.add("How to fill the Tasks sheet");
                sheetHelp.addAll(help());
                sheetHelp.add("Then open AlertAlarm > Tasks > Import and choose this file.");
                XlsxWriter.write(out, rows, 40, sheetHelp);
                break;
            }
            default: {
                StringBuilder sb = new StringBuilder();
                sb.append("# AlertAlarm - task plan\n");
                sb.append("# One task per line:  Date | Start | End | Repeat | Task\n");
                for (String h : help()) {
                    sb.append("# ").append(h).append('\n');
                }
                sb.append("# Lines starting with # are ignored.\n\n");
                for (String[] row : examples()) {
                    sb.append(String.join(" | ", row)).append('\n');
                }
                out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                break;
            }
        }
        return out.toByteArray();
    }
}
