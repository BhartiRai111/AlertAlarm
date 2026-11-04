package com.bharti.alertalarm.imports;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Opens any picked or shared file and finds the tasks in it. The file type is detected from its content. */
public final class FileImporter {

    private static final int MAX_BYTES = 20 * 1024 * 1024;

    private FileImporter() {
    }

    public static String displayName(Context c, Uri uri) {
        try (Cursor cursor = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null) {
                    return name;
                }
            }
        } catch (Exception ignored) {
            // some apps share files without a name
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "file";
    }

    public static List<ImportItem> read(Context c, Uri uri) throws IOException {
        byte[] data;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Could not open this file");
            }
            data = readAll(in);
        }
        String name = displayName(c, uri).toLowerCase(Locale.ENGLISH);
        String mime = c.getContentResolver().getType(uri);
        return parse(c, data, name, mime == null ? "" : mime.toLowerCase(Locale.ENGLISH));
    }

    static List<ImportItem> parse(Context c, byte[] data, String name, String mime) throws IOException {
        Calendar today = Calendar.getInstance();
        if (startsWith(data, '%', 'P', 'D', 'F')) {
            return PdfTasks.read(c, data, today);
        }
        if (startsWith(data, 'P', 'K')) {
            return TaskParser.parseRows(XlsxReader.read(new ByteArrayInputStream(data)), today);
        }
        if (data.length >= 4 && (data[0] & 0xFF) == 0xD0 && (data[1] & 0xFF) == 0xCF) {
            throw new IOException("Old Excel files (.xls) are not supported. Save it as .xlsx or CSV and try again.");
        }
        String text = new String(data, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        boolean csv = name.endsWith(".csv") || mime.contains("csv") || mime.contains("comma-separated");
        List<ImportItem> items = csv ? TaskParser.parseRows(CsvReader.read(text), today)
                : TaskParser.parseText(text, today);
        if (items.isEmpty() && !csv) {
            items = TaskParser.parseRows(CsvReader.read(text), today);
        }
        return items;
    }

    public static List<ImportItem> parseSharedText(String text) {
        return TaskParser.parseText(text, Calendar.getInstance());
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > MAX_BYTES) {
                throw new IOException("This file is too big (more than 20 MB)");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static boolean startsWith(byte[] data, char... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != (byte) prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
