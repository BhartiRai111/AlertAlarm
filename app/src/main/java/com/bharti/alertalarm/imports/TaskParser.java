package com.bharti.alertalarm.imports;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns text into tasks. Understands:
 * - the sample format:  05-11-2026 | 11:00 AM | 12:00 PM | Once | LeetCode
 * - free lines:         11:00 AM - 12:00 PM LeetCode,  Mon 8 Nov 5:15 PM to 8:15 PM Interview prep (daily)
 * - spreadsheet rows (Excel / CSV), with or without a header row (Date, Start, End, Repeat, Task)
 * Lines without a time are ignored, and so are lines starting with #.
 */
public final class TaskParser {

    private TaskParser() {
    }

    // ================================================================== whole texts / tables

    /** Notes, CSV-less text and text taken out of a PDF: one task per line. */
    public static List<ImportItem> parseText(String text, Calendar today) {
        List<ImportItem> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String raw : text.split("\\r?\\n|\\r")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            ImportItem item = parseLine(line, today);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    /** Excel / CSV / PDF form rows. A header row (with a "Task" column) is used to find the columns. */
    public static List<ImportItem> parseRows(List<List<String>> rows, Calendar today) {
        List<ImportItem> out = new ArrayList<>();
        int[] map = null;
        for (List<String> row : rows) {
            if (isBlank(row)) {
                continue;
            }
            if (row.size() > 0 && row.get(0) != null && row.get(0).trim().startsWith("#")) {
                continue;
            }
            int[] header = headerMap(row);
            if (header != null) {
                map = header;
                continue;
            }
            ImportItem item = map != null ? parseMapped(row, map, today) : parseCells(row, today);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    /** Column positions of date, start, end, repeat, task (-1 = missing), or null if this is not a header row. */
    static int[] headerMap(List<String> row) {
        int[] map = {-1, -1, -1, -1, -1};
        for (int i = 0; i < row.size(); i++) {
            String h = norm(row.get(i));
            if (h.isEmpty()) {
                continue;
            }
            if (map[4] < 0 && (h.equals("task") || h.startsWith("task") || h.equals("work") || h.equals("title"))) {
                map[4] = i;
            } else if (map[0] < 0 && (h.equals("date") || h.equals("day") || h.startsWith("date"))) {
                map[0] = i;
            } else if (map[1] < 0 && (h.startsWith("start") || h.equals("from") || h.equals("time") || h.equals("begin"))) {
                map[1] = i;
            } else if (map[2] < 0 && (h.startsWith("end") || h.equals("to") || h.equals("until") || h.equals("till")
                    || h.startsWith("rings until"))) {
                map[2] = i;
            } else if (map[3] < 0 && (h.startsWith("repeat") || h.equals("frequency"))) {
                map[3] = i;
            }
        }
        return map[4] >= 0 && map[1] >= 0 ? map : null;
    }

    private static ImportItem parseMapped(List<String> row, int[] map, Calendar today) {
        String task = cell(row, map[4]);
        Time start = timeCell(cell(row, map[1]));
        if (task.isEmpty() || start == null) {
            return null;
        }
        ImportItem item = new ImportItem();
        item.task = task;
        Time end = timeCell(cell(row, map[2]));
        applyTimes(item, start, end);
        int[] date = dateCell(cell(row, map[0]), today);
        if (date == null) {
            // A full date+time in the start cell (e.g. Excel "05-11-2026 11:00")
            date = dateCell(cell(row, map[1]), today);
            if (date != null && !looksLikeSerialWithDate(cell(row, map[1]))) {
                date = null;
            }
        }
        setDate(item, date);
        ImportItem.Repeat repeat = repeatWord(cell(row, map[3]));
        item.repeat = repeat != null ? repeat : ImportItem.Repeat.ONCE;
        return item;
    }

    /** Row without a header: works out which cell is the date, the times, the repeat and the task. */
    static ImportItem parseCells(List<String> cells, Calendar today) {
        Time start = null;
        Time end = null;
        int[] date = null;
        ImportItem.Repeat repeat = null;
        StringBuilder task = new StringBuilder();
        for (String raw : cells) {
            String c = raw == null ? "" : raw.trim();
            if (c.isEmpty()) {
                continue;
            }
            ImportItem.Repeat r = repeat == null ? repeatWord(c) : null;
            Time t = timeCell(c);
            int[] d = date == null ? dateCell(c, today) : null;
            if (r != null) {
                repeat = r;
            } else if (t != null && start == null) {
                start = t;
            } else if (t != null && end == null) {
                end = t;
            } else if (d != null) {
                date = d;
            } else {
                if (task.length() > 0) {
                    task.append(' ');
                }
                task.append(c);
            }
        }
        if (start == null || task.length() == 0) {
            return null;
        }
        ImportItem item = new ImportItem();
        item.task = task.toString();
        applyTimes(item, start, end);
        setDate(item, date);
        item.repeat = repeat != null ? repeat : ImportItem.Repeat.ONCE;
        return item;
    }

    // ================================================================== one line

    private static final String AMPM = "(a\\.?\\s?m\\.?|p\\.?\\s?m\\.?)";
    /** A time inside a sentence: needs minutes ("11:30") or am/pm ("11 am"). */
    private static final Pattern TIME_IN_TEXT = Pattern.compile(
            "(?<![\\d:.])(\\d{1,2})(?:[:.](\\d{2}))?(?::\\d{2})?(?![\\d:])(?!\\.\\d)\\s*" + AMPM + "?(?![a-z])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_NUMERIC = Pattern.compile(
            "(?<!\\d)(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4}|\\d{2})(?!\\d)");
    private static final Pattern DATE_ISO = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)");
    private static final String MONTHS = "(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?";
    private static final String WEEKDAY = "(?:(?:mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)[a-z]*,?\\s+)?";
    private static final Pattern DATE_DAY_MONTH = Pattern.compile(
            "\\b" + WEEKDAY + "(\\d{1,2})(?:st|nd|rd|th)?\\s+" + MONTHS + ",?(?:\\s+(\\d{4}))?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_MONTH_DAY = Pattern.compile(
            "\\b" + WEEKDAY + MONTHS + "\\s+(\\d{1,2})(?:st|nd|rd|th)?,?(?:\\s+(\\d{4}))?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_WORD = Pattern.compile("\\b(today|tomorrow)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPEAT_IN_TEXT = Pattern.compile(
            "\\(?\\b(every\\s*day|everyday|daily|every\\s+month|monthly|every\\s+year|yearly|annually|once|one\\s+time)\\b\\)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONNECTORS = Pattern.compile(
            "^(?:[\\s\\-–—|:,.;•*·>~]+|(?:to|se|till|until|from|at|and)\\b)+|(?:[\\s\\-–—|:,.;•*·>~(]+|\\b(?:to|se|till|until|from|at|and))+$",
            Pattern.CASE_INSENSITIVE);

    /** One line of text, in the sample format or free text. Null if it has no time. */
    public static ImportItem parseLine(String line, Calendar today) {
        if (line.indexOf('|') >= 0 || line.indexOf('\t') >= 0) {
            List<String> cells = new ArrayList<>();
            for (String c : line.split("[|\\t]")) {
                cells.add(c.trim());
            }
            ImportItem item = parseCells(cells, today);
            if (item != null) {
                return item;
            }
        }
        return parseFree(line, today);
    }

    static ImportItem parseFree(String line, Calendar today) {
        // "2 to 4 pm" / "2-4 pm": both are times, the first gets ":00" so it is recognised
        line = line.replaceAll("(?i)(?<![\\d:./-])(\\d{1,2})\\s*(?:-|–|to)\\s*(\\d{1,2})\\s*(a\\.?\\s?m\\.?|p\\.?\\s?m\\.?)(?![a-z])",
                "$1:00 to $2:00 $3");
        StringBuilder rest = new StringBuilder(line);

        // Repeat words
        ImportItem.Repeat repeat = null;
        Matcher rm = REPEAT_IN_TEXT.matcher(rest);
        if (rm.find()) {
            repeat = repeatWord(rm.group(1));
            blank(rest, rm.start(), rm.end());
        }

        // Date
        int[] date = null;
        Pattern[] datePatterns = {DATE_ISO, DATE_NUMERIC, DATE_DAY_MONTH, DATE_MONTH_DAY, DATE_WORD};
        for (Pattern p : datePatterns) {
            Matcher m = p.matcher(rest);
            if (m.find()) {
                int[] d = dateCell(m.group(0), today);
                if (d != null) {
                    date = d;
                    blank(rest, m.start(), m.end());
                    break;
                }
            }
        }

        // Times: the first two
        Time start = null;
        Time end = null;
        Matcher tm = TIME_IN_TEXT.matcher(rest);
        while (tm.find() && end == null) {
            boolean hasMinutes = tm.group(2) != null;
            boolean hasAmPm = tm.group(3) != null;
            if (!hasMinutes && !hasAmPm) {
                continue;
            }
            Time t = time(tm.group(1), tm.group(2), tm.group(3));
            if (t == null) {
                continue;
            }
            if (start == null) {
                start = t;
            } else {
                end = t;
            }
            blank(rest, tm.start(), tm.end());
        }
        if (start == null) {
            return null;
        }

        String task = rest.toString().replaceAll("\\s+", " ").trim();
        String previous;
        do {
            previous = task;
            task = CONNECTORS.matcher(task).replaceAll("").trim();
            task = task.replaceAll("^\\(\\s*\\)|\\(\\s*\\)$", "").trim();
        } while (!task.equals(previous));
        task = task.replaceAll("\\s*[-–—]\\s*[-–—]\\s*", " - ").replaceAll("\\s+", " ").trim();
        // "Meeting at . Bring notes" -> "Meeting. Bring notes"
        task = task.replaceAll("(?i)\\s+\\b(?:at|to|from|till|until|se)\\s*(?=[.,;:])", "")
                .replaceAll("\\s+([.,;:])", "$1").trim();
        if (task.isEmpty()) {
            return null;
        }

        ImportItem item = new ImportItem();
        item.task = task;
        applyTimes(item, start, end);
        setDate(item, date);
        item.repeat = repeat != null ? repeat : ImportItem.Repeat.ONCE;
        return item;
    }

    // ================================================================== cells

    /** A parsed time; explicit = am/pm was written, so "2:00" vs "2:00 PM" can be told apart. */
    static final class Time {
        final int minutes;
        final boolean explicit;

        Time(int minutes, boolean explicit) {
            this.minutes = minutes;
            this.explicit = explicit;
        }
    }

    private static final Pattern TIME_CELL = Pattern.compile(
            "^(\\d{1,2})(?:[:.](\\d{2}))?(?::\\d{2})?\\s*" + AMPM + "?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("^\\d+(?:\\.\\d+)?(?:[eE]-?\\d+)?$");

    /** "11:00 AM", "11 am", "14:30", "11.30", or an Excel time number (0.4583 = 11:00). */
    static Time timeCell(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        Matcher m = TIME_CELL.matcher(s);
        if (m.matches()) {
            if (m.group(2) == null && m.group(3) == null && s.length() > 2) {
                return null;
            }
            return time(m.group(1), m.group(2), m.group(3));
        }
        // Excel: a time is a fraction of a day; a date+time is days + fraction
        if (NUMBER.matcher(s).matches() && s.contains(".")) {
            double v = Double.parseDouble(s);
            double fraction = v - Math.floor(v);
            if (v >= 0 && (v < 1 || v > 20000)) {
                int minutes = (int) Math.round(fraction * 24 * 60) % (24 * 60);
                return new Time(minutes, true);
            }
        }
        // "05-11-2026 11:00 AM" in one cell: hide the date first so its numbers are not read as a time
        String noDate = DATE_ISO.matcher(s).replaceAll(" ");
        noDate = DATE_NUMERIC.matcher(noDate).replaceAll(" ");
        Matcher tm = TIME_IN_TEXT.matcher(noDate);
        while (tm.find()) {
            if (tm.group(2) != null || tm.group(3) != null) {
                return time(tm.group(1), tm.group(2), tm.group(3));
            }
        }
        return null;
    }

    private static Time time(String h, String m, String ampm) {
        int hour = Integer.parseInt(h);
        int minute = m == null ? 0 : Integer.parseInt(m);
        if (minute > 59) {
            return null;
        }
        boolean explicit = ampm != null;
        if (explicit) {
            if (hour < 1 || hour > 12) {
                return null;
            }
            boolean pm = ampm.toLowerCase(Locale.ENGLISH).startsWith("p");
            hour = hour % 12 + (pm ? 12 : 0);
        } else if (hour > 23) {
            return null;
        }
        return new Time(hour * 60 + minute, explicit || hour >= 13 || hour == 0);
    }

    private static final String[] MONTH_KEYS = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};

    /** Date as {year, month 0-11, day}, or null. DD-MM-YYYY (Indian order), YYYY-MM-DD, "8 Nov", "Nov 8", today, tomorrow, Excel numbers. */
    static int[] dateCell(String raw, Calendar today) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ENGLISH);
        if (s.isEmpty()) {
            return null;
        }
        if (s.equals("today")) {
            return ymd(today);
        }
        if (s.equals("tomorrow")) {
            Calendar c = (Calendar) today.clone();
            c.add(Calendar.DAY_OF_MONTH, 1);
            return ymd(c);
        }
        Matcher m = DATE_ISO.matcher(s);
        if (m.find()) {
            return valid(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(3)));
        }
        m = DATE_NUMERIC.matcher(s);
        if (m.find()) {
            int y = Integer.parseInt(m.group(3));
            if (y < 100) {
                y += 2000;
            }
            return valid(y, Integer.parseInt(m.group(2)) - 1, Integer.parseInt(m.group(1)));
        }
        m = DATE_DAY_MONTH.matcher(s);
        if (m.find()) {
            return textDate(m.group(1), m.group(2), m.group(3), today);
        }
        m = DATE_MONTH_DAY.matcher(s);
        if (m.find()) {
            return textDate(m.group(2), m.group(1), m.group(3), today);
        }
        // Excel date number: days since 30 Dec 1899
        if (NUMBER.matcher(s).matches()) {
            double v = Double.parseDouble(s);
            if (v > 20000 && v < 80000) {
                Calendar c = Calendar.getInstance();
                c.clear();
                c.set(1899, Calendar.DECEMBER, 30);
                c.add(Calendar.DAY_OF_MONTH, (int) Math.floor(v));
                return ymd(c);
            }
        }
        return null;
    }

    private static boolean looksLikeSerialWithDate(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        if (NUMBER.matcher(t).matches()) {
            return Double.parseDouble(t) > 20000;
        }
        return DATE_NUMERIC.matcher(t).find() || DATE_ISO.matcher(t).find();
    }

    private static int[] textDate(String day, String month, String year, Calendar today) {
        String key = month.toLowerCase(Locale.ENGLISH).substring(0, 3);
        for (int i = 0; i < 12; i++) {
            if (MONTH_KEYS[i].equals(key)) {
                int y = year != null ? Integer.parseInt(year) : today.get(Calendar.YEAR);
                return valid(y, i, Integer.parseInt(day));
            }
        }
        return null;
    }

    private static int[] valid(int y, int m, int d) {
        if (y < 2000 || y > 2100 || m < 0 || m > 11 || d < 1) {
            return null;
        }
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m, 1);
        if (d > c.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            return null;
        }
        return new int[]{y, m, d};
    }

    private static int[] ymd(Calendar c) {
        return new int[]{c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)};
    }

    /** "Daily", "Every day", "Monthly", "Yearly", "Once" (any case). Null if it is not a repeat word. */
    static ImportItem.Repeat repeatWord(String raw) {
        String s = norm(raw).replaceAll("[()]", "").replaceAll("\\s+", " ");
        switch (s) {
            case "once":
            case "one time":
            case "onetime":
            case "no repeat":
            case "none":
                return ImportItem.Repeat.ONCE;
            case "daily":
            case "every day":
            case "everyday":
                return ImportItem.Repeat.DAILY;
            case "monthly":
            case "every month":
                return ImportItem.Repeat.MONTHLY;
            case "yearly":
            case "every year":
            case "annually":
                return ImportItem.Repeat.YEARLY;
            default:
                return null;
        }
    }

    // ================================================================== helpers

    private static void applyTimes(ImportItem item, Time start, Time end) {
        int s = start.minutes;
        int e = end == null ? -1 : end.minutes;
        if (end != null) {
            // "5:15 - 8:15 PM": the start is PM too
            if (!start.explicit && end.explicit && e >= 12 * 60 && s < 12 * 60 && s + 12 * 60 < e) {
                s += 12 * 60;
            }
            // "12:00 - 2:00": the end is 2 PM, not 2 AM
            if (!end.explicit && e <= s && e + 12 * 60 > s && e < 12 * 60) {
                e += 12 * 60;
            }
        }
        item.startMinutes = s;
        item.endMinutes = e;
    }

    private static void setDate(ImportItem item, int[] date) {
        if (date != null) {
            item.year = date[0];
            item.month = date[1];
            item.day = date[2];
        }
    }

    private static void blank(StringBuilder sb, int start, int end) {
        for (int i = start; i < end; i++) {
            sb.setCharAt(i, ' ');
        }
    }

    private static String cell(List<String> row, int index) {
        if (index < 0 || index >= row.size() || row.get(index) == null) {
            return "";
        }
        return row.get(index).trim();
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ENGLISH);
    }

    private static boolean isBlank(List<String> row) {
        for (String c : row) {
            if (c != null && !c.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
