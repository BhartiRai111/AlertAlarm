package com.bharti.alertalarm.imports;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Understands a spoken task in English or Hinglish, e.g.
 * "kal shaam 6 baje gym 1 ghanta", "tomorrow 6 pm gym for one hour", "aaj 3 se 5 baje Docker padhna".
 * Turns Hinglish words into English ones, then uses {@link TaskParser}.
 */
public final class VoiceParser {

    private VoiceParser() {
    }

    private static final Pattern PM_WORDS = Pattern.compile(
            "\\b(shaam|sham|shām|evening|raat|rat|night|dopahar|dopehar|dophar|afternoon)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AM_WORDS = Pattern.compile("\\b(subah|subha|savere|sawere|morning)\\b", Pattern.CASE_INSENSITIVE);
    private static final String NUM = "(\\d+(?:\\.\\d+)?|ek|one|do|two|teen|three|char|chaar|four|paanch|panch|five|"
            + "aadha|adha|half|dedh|dhai|an|a)";
    private static final Pattern DURATION = Pattern.compile(
            "\\b(?:for\\s+)?" + NUM + "\\s*(ghanta|ghante|ghanton|hours?|hrs?|minutes?|mins?|minat|minute)\\b(?:\\s+(?:ke\\s+liye|tak|ka|ki))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern BAJE = Pattern.compile("\\b(\\d{1,2})(?:[:.](\\d{2}))?\\s*(?:baje|bje|bajkar|o'?clock)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SADE = Pattern.compile("\\b(?:sade|saade|saadhe|sadhe)\\s+(\\d{1,2})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_TIME = Pattern.compile("(?<![\\d:])(\\d{1,2}):(\\d{2})(?!\\s*(?:am|pm|a\\.m|p\\.m))", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILLER = Pattern.compile(
            "\\b(remind me to|remind me|set an alarm for|set alarm for|set alarm|alarm laga do|alarm lagao|alarm set karo|"
                    + "alarm|mujhe|yaad dilana|yaad dila dena|please|karna hai|karna|hai|ko|ke liye|lagao|laga do|set karo)\\b",
            Pattern.CASE_INSENSITIVE);

    /** The task, or null when no time was heard. */
    public static ImportItem parse(String spoken, Calendar today) {
        if (spoken == null || spoken.trim().isEmpty()) {
            return null;
        }
        // Keep the user's capital letters (e.g. "LeetCode"); all patterns ignore case
        String s = " " + spoken.replace(',', ' ') + " ";

        // Days
        Calendar dayAfter = (Calendar) today.clone();
        dayAfter.add(Calendar.DAY_OF_MONTH, 2);
        String dayAfterText = new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(dayAfter.getTime());
        s = s.replaceAll("(?i)\\b(parso|parson|parsoon|day after tomorrow)\\b", " " + dayAfterText + " ");
        s = s.replaceAll("(?i)\\b(kal|kl)\\b", " tomorrow ");
        s = s.replaceAll("(?i)\\b(aaj|aj)\\b", " today ");

        // Part of the day decides AM / PM
        boolean pm = PM_WORDS.matcher(s).find();
        boolean am = !pm && AM_WORDS.matcher(s).find();
        s = PM_WORDS.matcher(s).replaceAll(" ");
        s = AM_WORDS.matcher(s).replaceAll(" ");

        // How long: "1 ghanta", "for 30 minutes", "aadha ghanta"
        int durationMinutes = -1;
        Matcher dm = DURATION.matcher(s);
        if (dm.find()) {
            double n = number(dm.group(1).toLowerCase(Locale.ENGLISH));
            String unit = dm.group(2).toLowerCase(Locale.ENGLISH);
            boolean hours = unit.startsWith("g") || unit.startsWith("h");
            durationMinutes = (int) Math.round(hours ? n * 60 : n);
            s = s.substring(0, dm.start()) + " " + s.substring(dm.end());
        }

        // "saade 6" = 6:30, "6 baje" = 6:00, "3 se 5" = 3 to 5
        s = SADE.matcher(s).replaceAll("$1:30");
        Matcher bm = BAJE.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (bm.find()) {
            String minutes = bm.group(2) != null ? bm.group(2) : "00";
            bm.appendReplacement(sb, bm.group(1) + ":" + minutes);
        }
        bm.appendTail(sb);
        s = sb.toString();
        s = s.replaceAll("(?i)\\b(\\d{1,2}(?::\\d{2})?)\\s+se\\s+(\\d{1,2}(?::\\d{2})?)\\b", "$1 to $2");
        s = s.replaceAll("\\b(\\d{1,2})\\s+to\\s+(\\d{1,2}:\\d{2})", "$1:00 to $2");
        s = s.replaceAll("(?i)\\btak\\b", " ");

        // Times without am/pm get it from the part of the day, or a guess (1-6 o'clock = PM)
        Matcher tm = BARE_TIME.matcher(s);
        sb = new StringBuffer();
        while (tm.find()) {
            int hour = Integer.parseInt(tm.group(1));
            String suffix = "";
            if (hour >= 1 && hour <= 12) {
                if (pm) {
                    suffix = " pm";
                } else if (am) {
                    suffix = " am";
                } else if (hour <= 6) {
                    suffix = " pm";
                }
            }
            tm.appendReplacement(sb, tm.group(1) + ":" + tm.group(2) + suffix);
        }
        tm.appendTail(sb);
        s = sb.toString();

        s = FILLER.matcher(s).replaceAll(" ");
        ImportItem item = TaskParser.parseFree(s.replaceAll("\\s+", " ").trim(), today);
        if (item == null) {
            return null;
        }
        if (item.endMinutes < 0 && durationMinutes > 0) {
            item.endMinutes = (item.startMinutes + durationMinutes) % (24 * 60);
        }
        item.task = capitalise(item.task);
        return item;
    }

    private static double number(String w) {
        switch (w) {
            case "ek":
            case "one":
            case "an":
            case "a":
                return 1;
            case "do":
            case "two":
                return 2;
            case "teen":
            case "three":
                return 3;
            case "char":
            case "chaar":
            case "four":
                return 4;
            case "paanch":
            case "panch":
            case "five":
                return 5;
            case "aadha":
            case "adha":
            case "half":
                return 0.5;
            case "dedh":
                return 1.5;
            case "dhai":
                return 2.5;
            default:
                try {
                    return Double.parseDouble(w);
                } catch (NumberFormatException e) {
                    return 1;
                }
        }
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
