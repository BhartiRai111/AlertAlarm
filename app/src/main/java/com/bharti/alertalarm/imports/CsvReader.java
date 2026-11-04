package com.bharti.alertalarm.imports;

import java.util.ArrayList;
import java.util.List;

/** Simple CSV reader (Google Sheets / Excel "Save as CSV"): commas or semicolons, quoted cells. */
public final class CsvReader {

    private CsvReader() {
    }

    public static List<List<String>> read(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return rows;
        }
        if (text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        char sep = guessSeparator(text);
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == sep) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private static char guessSeparator(String text) {
        int end = text.indexOf('\n');
        String first = end > 0 ? text.substring(0, end) : text;
        int commas = first.length() - first.replace(",", "").length();
        int semis = first.length() - first.replace(";", "").length();
        return semis > commas ? ';' : ',';
    }
}
