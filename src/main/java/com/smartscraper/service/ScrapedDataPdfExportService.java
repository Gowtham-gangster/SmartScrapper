package com.smartscraper.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.smartscraper.entity.ScrapedData;
import com.smartscraper.service.UrlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ScrapedDataPdfExportService {

    private static final Logger logger = LoggerFactory.getLogger(ScrapedDataPdfExportService.class);

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    private final Path exportsDir;

    public ScrapedDataPdfExportService(@Value("${app.exports.dir:exports}") String exportsDir) {
        this.exportsDir = Path.of(exportsDir);
    }

    /**
     * Exports the given {@link ScrapedData} rows into a PDF using iText/OpenPDF style APIs.
     *
     * The PDF contains headings and, for each row, fields:
     * Title, Description, URL, Source, Date.
     *
     * @return the generated PDF file path
     */
    public Path exportSelectedToPDF(List<ScrapedData> data) throws Exception {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("data must not be null/empty");
        }

        Files.createDirectories(exportsDir);

        String timestamp = LocalDateTime.now().format(TS_FORMAT);
        String fileName = "export_" + timestamp + ".pdf";
        Path target = exportsDir.resolve(fileName);

        logger.info("Exporting {} ScrapedData rows to PDF: {}", data.size(), target);

        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        try (OutputStream os = Files.newOutputStream(target)) {
            PdfWriter.getInstance(document, os);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Font headingFont = new Font(Font.HELVETICA, 12, Font.BOLD, Color.DARK_GRAY);
            Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.GRAY);
            Font bodyFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
            Font linkFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLUE);

            Paragraph docTitle = new Paragraph("SmartScraper Export", titleFont);
            docTitle.setAlignment(Element.ALIGN_CENTER);
            docTitle.setSpacingAfter(18);
            document.add(docTitle);

            Paragraph meta = new Paragraph(
                    "Exported: " + LocalDateTime.now().format(DATE_FORMAT) + "\nItems: " + data.size(),
                    new Font(Font.HELVETICA, 10, Font.NORMAL, Color.GRAY)
            );
            meta.setAlignment(Element.ALIGN_LEFT);
            meta.setSpacingAfter(22);
            document.add(meta);

            int idx = 1;
            for (ScrapedData row : data) {
                document.add(new Paragraph("Item " + idx++, headingFont));
                document.add(new Paragraph(" ")); // spacing

                addField(document, "Title", safe(row.getTitle()), labelFont, bodyFont);
                addField(document, "Description", safe(row.getContent()), labelFont, bodyFont);

                String url = safe(row.getSourceUrl());
                addField(document, "URL", url, labelFont, url.isEmpty() ? bodyFont : linkFont);

                String source = UrlUtils.extractDomain(row.getSourceUrl());
                addField(document, "Source", source == null ? "" : source, labelFont, bodyFont);

                String date = row.getScrapedAt() == null ? "" : row.getScrapedAt().format(DATE_FORMAT);
                addField(document, "Date", date, labelFont, bodyFont);

                document.add(new Paragraph(" "));
                document.add(new Paragraph("------------------------------------------------------------"));
                document.add(new Paragraph(" "));
            }
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }

        return target;
    }

    private void addField(Document document, String label, String value, Font labelFont, Font valueFont) throws Exception {
        String safeValue = value == null ? "" : value;

        Paragraph p = new Paragraph();
        p.add(new Paragraph(label + ": ", labelFont));
        // New paragraph for value to preserve spacing for long text
        Paragraph valuePara = new Paragraph(safeValue, valueFont);
        valuePara.setSpacingAfter(10);

        document.add(p);
        document.add(valuePara);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}

