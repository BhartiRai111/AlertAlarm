package com.bharti.alertalarm.imports;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a small .xlsx file (the import sample). Cells are text-formatted so typed times like "11:00 AM"
 * stay as text. Sheet 1 = the table to fill, sheet 2 = how to fill it.
 */
public final class XlsxWriter {

    private XlsxWriter() {
    }

    /** rows[0] is the header (bold). emptyRows extra text-formatted rows are added for filling in. */
    public static void write(OutputStream out, List<String[]> rows, int emptyRows, List<String> help) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out);
        put(zip, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                        + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                        + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                        + "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                        + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                        + "</Types>");
        put(zip, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                        + "</Relationships>");
        put(zip, "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                        + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                        + "<sheets><sheet name=\"Tasks\" sheetId=\"1\" r:id=\"rId1\"/>"
                        + "<sheet name=\"How to fill\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>");
        put(zip, "xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                        + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>"
                        + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                        + "</Relationships>");
        // Style 1 = bold header with a light teal fill, style 2 = text format (@) for the cells to fill
        put(zip, "xl/styles.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                        + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                        + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                        + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill>"
                        + "<fill><patternFill patternType=\"gray125\"/></fill>"
                        + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFCCF5EF\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                        + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                        + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                        + "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                        + "<xf numFmtId=\"49\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyNumberFormat=\"1\"/>"
                        + "<xf numFmtId=\"49\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs>"
                        + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                        + "</styleSheet>");

        StringBuilder sheet = new StringBuilder();
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
                .append("<cols><col min=\"1\" max=\"1\" width=\"14\" customWidth=\"1\"/>")
                .append("<col min=\"2\" max=\"3\" width=\"12\" customWidth=\"1\"/>")
                .append("<col min=\"4\" max=\"4\" width=\"11\" customWidth=\"1\"/>")
                .append("<col min=\"5\" max=\"5\" width=\"40\" customWidth=\"1\"/></cols><sheetData>");
        int columns = rows.get(0).length;
        int r = 1;
        for (String[] row : rows) {
            appendRow(sheet, r, row, r == 1 ? 1 : 2, columns);
            r++;
        }
        for (int i = 0; i < emptyRows; i++) {
            appendRow(sheet, r++, new String[0], 2, columns);
        }
        sheet.append("</sheetData></worksheet>");
        put(zip, "xl/worksheets/sheet1.xml", sheet.toString());

        StringBuilder helpSheet = new StringBuilder();
        helpSheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<cols><col min=\"1\" max=\"1\" width=\"90\" customWidth=\"1\"/></cols><sheetData>");
        r = 1;
        for (String line : help) {
            appendRow(helpSheet, r, new String[]{line}, r == 1 ? 1 : 0, 1);
            r++;
        }
        helpSheet.append("</sheetData></worksheet>");
        put(zip, "xl/worksheets/sheet2.xml", helpSheet.toString());
        zip.finish();
    }

    private static void appendRow(StringBuilder sb, int r, String[] values, int style, int columns) {
        sb.append("<row r=\"").append(r).append("\">");
        for (int c = 0; c < columns; c++) {
            String ref = (char) ('A' + c) + String.valueOf(r);
            String v = c < values.length ? values[c] : null;
            if (v == null || v.isEmpty()) {
                sb.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\"/>");
            } else {
                sb.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escape(v)).append("</t></is></c>");
            }
        }
        sb.append("</row>");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
