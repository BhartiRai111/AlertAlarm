package com.bharti.alertalarm.imports;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/** Reads the first sheet of an .xlsx file as rows of text. No library needed: .xlsx is a zip of XML files. */
public final class XlsxReader {

    private static final long MAX_ENTRY_BYTES = 20L * 1024 * 1024;

    private XlsxReader() {
    }

    public static List<List<String>> read(InputStream in) throws IOException {
        Map<String, byte[]> files = unzip(in);
        List<String> shared = files.containsKey("xl/sharedStrings.xml")
                ? sharedStrings(files.get("xl/sharedStrings.xml")) : new ArrayList<>();
        byte[] sheet = files.get(firstSheetPath(files));
        if (sheet == null) {
            throw new IOException("No sheet found in this Excel file");
        }
        return sheetRows(sheet, shared);
    }

    private static Map<String, byte[]> unzip(InputStream in) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zip.getNextEntry()) != null) {
                String name = e.getName();
                if (!name.endsWith(".xml") && !name.endsWith(".rels")) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int n;
                long total = 0;
                while ((n = zip.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_ENTRY_BYTES) {
                        throw new IOException("Excel file is too big");
                    }
                    out.write(buf, 0, n);
                }
                files.put(name.startsWith("/") ? name.substring(1) : name, out.toByteArray());
            }
        }
        return files;
    }

    /** workbook.xml lists the sheets; its .rels file says which XML file each one is. */
    private static String firstSheetPath(Map<String, byte[]> files) {
        String fallback = "xl/worksheets/sheet1.xml";
        byte[] workbook = files.get("xl/workbook.xml");
        byte[] rels = files.get("xl/_rels/workbook.xml.rels");
        if (workbook == null || rels == null) {
            return fallback;
        }
        try {
            final String[] firstId = {null};
            parse(workbook, new DefaultHandler() {
                @Override
                public void startElement(String uri, String local, String qName, Attributes a) {
                    if (firstId[0] == null && name(local, qName).equals("sheet")) {
                        for (int i = 0; i < a.getLength(); i++) {
                            String q = a.getQName(i);
                            if (q.endsWith(":id") || a.getLocalName(i).equals("id") && !q.equals("sheetId")) {
                                firstId[0] = a.getValue(i);
                            }
                        }
                    }
                }
            });
            final Map<String, String> targets = new HashMap<>();
            parse(rels, new DefaultHandler() {
                @Override
                public void startElement(String uri, String local, String qName, Attributes a) {
                    if (name(local, qName).equals("Relationship")) {
                        targets.put(a.getValue("Id"), a.getValue("Target"));
                    }
                }
            });
            String target = firstId[0] == null ? null : targets.get(firstId[0]);
            if (target == null) {
                return fallback;
            }
            String path = target.startsWith("/") ? target.substring(1) : "xl/" + target;
            return files.containsKey(path) ? path : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static List<String> sharedStrings(byte[] xml) throws IOException {
        final List<String> list = new ArrayList<>();
        parse(xml, new DefaultHandler() {
            private StringBuilder item;
            private boolean inText;
            private int phonetic;

            @Override
            public void startElement(String uri, String local, String qName, Attributes a) {
                String n = name(local, qName);
                if (n.equals("si")) {
                    item = new StringBuilder();
                } else if (n.equals("rPh")) {
                    phonetic++;
                } else if (n.equals("t") && phonetic == 0) {
                    inText = true;
                }
            }

            @Override
            public void endElement(String uri, String local, String qName) {
                String n = name(local, qName);
                if (n.equals("si")) {
                    list.add(item.toString());
                    item = null;
                } else if (n.equals("rPh")) {
                    phonetic--;
                } else if (n.equals("t")) {
                    inText = false;
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) {
                if (inText && item != null) {
                    item.append(ch, start, length);
                }
            }
        });
        return list;
    }

    private static List<List<String>> sheetRows(byte[] xml, final List<String> shared) throws IOException {
        final List<List<String>> rows = new ArrayList<>();
        parse(xml, new DefaultHandler() {
            private List<String> row;
            private int col;
            private String type;
            private StringBuilder value;
            private boolean collect;

            @Override
            public void startElement(String uri, String local, String qName, Attributes a) {
                String n = name(local, qName);
                switch (n) {
                    case "row":
                        row = new ArrayList<>();
                        col = 0;
                        break;
                    case "c":
                        String ref = a.getValue("r");
                        if (ref != null) {
                            col = column(ref);
                        }
                        type = a.getValue("t");
                        value = new StringBuilder();
                        break;
                    case "v":
                    case "t":
                        collect = value != null;
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void endElement(String uri, String local, String qName) {
                String n = name(local, qName);
                if (n.equals("v") || n.equals("t")) {
                    collect = false;
                } else if (n.equals("c") && row != null && value != null) {
                    String v = value.toString();
                    if ("s".equals(type)) {
                        try {
                            int idx = Integer.parseInt(v.trim());
                            v = idx >= 0 && idx < shared.size() ? shared.get(idx) : "";
                        } catch (NumberFormatException e) {
                            v = "";
                        }
                    } else if ("b".equals(type)) {
                        v = "1".equals(v.trim()) ? "TRUE" : "FALSE";
                    } else if ("e".equals(type)) {
                        v = "";
                    }
                    while (row.size() < col) {
                        row.add("");
                    }
                    if (row.size() == col) {
                        row.add(v);
                    } else {
                        row.set(col, v);
                    }
                    col++;
                    value = null;
                } else if (n.equals("row") && row != null) {
                    rows.add(row);
                    row = null;
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) {
                if (collect && value != null) {
                    value.append(ch, start, length);
                }
            }
        });
        return rows;
    }

    /** "C12" -> 2 (columns start at 0). */
    static int column(String ref) {
        int col = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                col = col * 26 + (c - 'A' + 1);
            } else if (c >= 'a' && c <= 'z') {
                col = col * 26 + (c - 'a' + 1);
            } else {
                break;
            }
        }
        return Math.max(0, col - 1);
    }

    private static String name(String local, String qName) {
        String n = local != null && !local.isEmpty() ? local : qName;
        int colon = n.indexOf(':');
        return colon >= 0 ? n.substring(colon + 1) : n;
    }

    private static void parse(byte[] xml, DefaultHandler handler) throws IOException {
        try {
            SAXParserFactory f = SAXParserFactory.newInstance();
            f.setNamespaceAware(true);
            SAXParser p = f.newSAXParser();
            p.parse(new ByteArrayInputStream(xml), handler);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Could not read this Excel file", e);
        }
    }
}
