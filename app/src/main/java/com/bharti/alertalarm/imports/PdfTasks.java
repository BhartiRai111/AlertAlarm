package com.bharti.alertalarm.imports;

import android.content.Context;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceCharacteristicsDictionary;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The fillable PDF sample, and reading tasks back out of any PDF (form boxes first, else its text). */
public final class PdfTasks {

    static final int ROWS = 20;
    private static final String[] COLUMNS = {"date", "start", "end", "repeat", "task"};
    private static final String[] HEADERS = {"Date", "Start", "End", "Repeat", "Task"};
    private static final float[] WIDTHS = {82, 66, 66, 62, 247};

    private PdfTasks() {
    }

    // ================================================================== sample form

    /** A4 page with a table of fill-in boxes; the first rows hold examples. */
    public static void writeSample(Context c, OutputStream out, List<String[]> examples, List<String> help)
            throws IOException {
        PDFBoxResourceLoader.init(c.getApplicationContext());
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDFont regular = PDType1Font.HELVETICA;
            PDFont bold = PDType1Font.HELVETICA_BOLD;

            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("Helv"), regular);
            PDAcroForm form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            form.setDefaultResources(resources);
            form.setDefaultAppearance("/Helv 10 Tf 0 g");

            float left = 36;
            float top = 806;
            float rowH = 24;
            float headerH = 22;
            float tableW = 0;
            for (float w : WIDTHS) {
                tableW += w;
            }
            PDColor teal = rgb(0.18f, 0.83f, 0.75f);
            PDColor lightTeal = rgb(0.80f, 0.96f, 0.94f);
            PDColor dark = rgb(0.05f, 0.07f, 0.08f);
            PDColor grey = rgb(0.40f, 0.44f, 0.47f);
            PDColor line = rgb(0.75f, 0.78f, 0.80f);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Title + help
                cs.setNonStrokingColor(dark);
                text(cs, bold, 18, left, top, "AlertAlarm - Task plan");
                float y = top - 22;
                for (String h : help) {
                    cs.setNonStrokingColor(grey);
                    text(cs, regular, 9, left, y, h);
                    y -= 13;
                }

                // Header row
                float tableTop = y - 8;
                cs.setNonStrokingColor(lightTeal);
                cs.addRect(left, tableTop - headerH, tableW, headerH);
                cs.fill();
                cs.setNonStrokingColor(dark);
                float x = left;
                for (int i = 0; i < HEADERS.length; i++) {
                    text(cs, bold, 10, x + 6, tableTop - 15, HEADERS[i]);
                    x += WIDTHS[i];
                }

                // Grid
                float tableBottom = tableTop - headerH - ROWS * rowH;
                cs.setStrokingColor(line);
                cs.setLineWidth(0.6f);
                cs.addRect(left, tableBottom, tableW, tableTop - tableBottom);
                cs.stroke();
                for (int r = 0; r <= ROWS; r++) {
                    float ly = tableTop - headerH - r * rowH;
                    cs.moveTo(left, ly);
                    cs.lineTo(left + tableW, ly);
                }
                x = left;
                for (int i = 0; i < WIDTHS.length - 1; i++) {
                    x += WIDTHS[i];
                    cs.moveTo(x, tableTop);
                    cs.lineTo(x, tableBottom);
                }
                cs.stroke();

                // Fill-in boxes
                for (int r = 0; r < ROWS; r++) {
                    float rowBottom = tableTop - headerH - (r + 1) * rowH;
                    x = left;
                    String[] example = r < examples.size() ? examples.get(r) : null;
                    for (int col = 0; col < COLUMNS.length; col++) {
                        PDTextField field = new PDTextField(form);
                        field.setPartialName(COLUMNS[col] + "_" + (r + 1));
                        field.setDefaultAppearance("/Helv 10 Tf 0 g");
                        form.getFields().add(field);
                        PDAnnotationWidget widget = field.getWidgets().get(0);
                        widget.setRectangle(new PDRectangle(x + 2, rowBottom + 2, WIDTHS[col] - 4, rowH - 4));
                        widget.setPage(page);
                        PDAppearanceCharacteristicsDictionary look =
                                new PDAppearanceCharacteristicsDictionary(new COSDictionary());
                        look.setBorderColour(teal);
                        widget.setAppearanceCharacteristics(look);
                        widget.setPrinted(true);
                        page.getAnnotations().add(widget);
                        if (example != null && col < example.length && !example[col].isEmpty()) {
                            field.setValue(example[col]);
                        }
                        x += WIDTHS[col];
                    }
                }

                cs.setNonStrokingColor(grey);
                text(cs, regular, 8, left, tableBottom - 16,
                        "Tip: open this file in Google Drive or Adobe Acrobat to type in the boxes, then import it in AlertAlarm.");
            }
            form.setNeedAppearances(true);
            doc.save(out);
        }
    }

    private static void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String s)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    private static PDColor rgb(float r, float g, float b) {
        return new PDColor(new float[]{r, g, b}, PDDeviceRGB.INSTANCE);
    }

    // ================================================================== reading

    /** Tasks from a PDF: the sample's boxes if it has them, otherwise every line of text. */
    public static List<ImportItem> read(Context c, byte[] pdf, Calendar today) throws IOException {
        PDFBoxResourceLoader.init(c.getApplicationContext());
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
            List<List<String>> rows = formRows(doc);
            if (rows != null) {
                List<ImportItem> items = TaskParser.parseRows(rows, today);
                if (!items.isEmpty()) {
                    return items;
                }
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return TaskParser.parseText(stripper.getText(doc), today);
        }
    }

    /** Rows from the boxes named date_1, start_1, ... (our sample), or null if the PDF has none. */
    private static List<List<String>> formRows(PDDocument doc) {
        PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
        if (form == null) {
            return null;
        }
        Map<String, String> values = new HashMap<>();
        for (PDField f : form.getFieldTree()) {
            String v = f.getValueAsString();
            values.put(f.getFullyQualifiedName(), v == null ? "" : v);
        }
        if (!values.containsKey("task_1")) {
            return null;
        }
        List<List<String>> rows = new ArrayList<>();
        rows.add(Arrays.asList(HEADERS));
        for (int r = 1; values.containsKey("task_" + r); r++) {
            List<String> row = new ArrayList<>();
            for (String col : COLUMNS) {
                String v = values.get(col + "_" + r);
                row.add(v == null ? "" : v);
            }
            rows.add(row);
        }
        return rows;
    }
}
