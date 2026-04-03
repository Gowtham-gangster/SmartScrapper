package com.smartscraper.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.smartscraper.entity.ModuleType;
import com.smartscraper.entity.ScrapedContent;
import com.smartscraper.entity.ScrapedData;
import com.smartscraper.entity.ExportHistory;
import com.smartscraper.repository.ScrapedDataRepository;
import com.smartscraper.repository.ScrapedContentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Service for exporting scraped content to various formats.
 * Supports PDF and CSV exports of selected content.
 */
@Service
public class ExportService {

    private static final Logger logger = LoggerFactory.getLogger(ExportService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    private final ScrapedContentRepository scrapedContentRepository;
    private final ScrapedDataRepository scrapedDataRepository;
    private final ScrapedDataCsvExportService scrapedDataCsvExportService;
    private final ScrapedDataPdfExportService scrapedDataPdfExportService;
    private final ExportHistoryService exportHistoryService;

    @Autowired
    public ExportService(
            ScrapedContentRepository scrapedContentRepository,
            ScrapedDataRepository scrapedDataRepository,
            ScrapedDataCsvExportService scrapedDataCsvExportService,
            ScrapedDataPdfExportService scrapedDataPdfExportService,
            ExportHistoryService exportHistoryService) {
        this.scrapedContentRepository = scrapedContentRepository;
        this.scrapedDataRepository = scrapedDataRepository;
        this.scrapedDataCsvExportService = scrapedDataCsvExportService;
        this.scrapedDataPdfExportService = scrapedDataPdfExportService;
        this.exportHistoryService = exportHistoryService;
    }

    /**
     * Export selected content to PDF format.
     * 
     * @param contentIds Array of content IDs to export
     * @return PDF file as byte array
     * @throws Exception if export fails
     */
    public byte[] exportToPdf(Long[] contentIds) throws Exception {
        logger.info("Exporting {} items to PDF", contentIds.length);

        // Fetch content
        List<ScrapedContent> contentList = scrapedContentRepository.findAllById(List.of(contentIds));

        if (contentList.isEmpty()) {
            throw new IllegalArgumentException("No content found for export");
        }

        // Create PDF document
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        PdfWriter.getInstance(document, outputStream);
        document.open();

        // Add title
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Paragraph title = new Paragraph("Research Content Export", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        // Add export info
        Font infoFont = new Font(Font.HELVETICA, 10, Font.NORMAL, java.awt.Color.GRAY);
        Paragraph info = new Paragraph("Exported: " + java.time.LocalDateTime.now().format(DATE_FORMATTER), infoFont);
        info.setAlignment(Element.ALIGN_CENTER);
        info.setSpacingAfter(30);
        document.add(info);

        // Add each content item
        Font contentFont = new Font(Font.HELVETICA, 11, Font.NORMAL);
        Font sourceFont = new Font(Font.HELVETICA, 9, Font.ITALIC, java.awt.Color.BLUE);
        Font dateFont = new Font(Font.HELVETICA, 8, Font.NORMAL, java.awt.Color.GRAY);

        int itemNumber = 1;
        for (ScrapedContent content : contentList) {
            // Item number
            Paragraph itemHeader = new Paragraph("Item " + itemNumber++, new Font(Font.HELVETICA, 12, Font.BOLD));
            itemHeader.setSpacingBefore(15);
            itemHeader.setSpacingAfter(5);
            document.add(itemHeader);

            // Content text (preserve line breaks)
            String contentText = content.getContentText();
            Paragraph contentPara = new Paragraph(contentText, contentFont);
            contentPara.setAlignment(Element.ALIGN_JUSTIFIED);
            contentPara.setSpacingAfter(10);
            document.add(contentPara);

            // Source URL
            Paragraph sourcePara = new Paragraph("Source: " + content.getScrapedSource().getSourceUrl(), sourceFont);
            sourcePara.setSpacingAfter(5);
            document.add(sourcePara);

            // Date
            Paragraph datePara = new Paragraph("Scraped: " + content.getCreatedAt().format(DATE_FORMATTER), dateFont);
            datePara.setSpacingAfter(10);
            document.add(datePara);

            // Separator line
            if (itemNumber <= contentList.size()) {
                LineSeparator line = new LineSeparator();
                document.add(new Chunk(line));
            }
        }

        document.close();
        logger.info("PDF export completed successfully");

        return outputStream.toByteArray();
    }

    /**
     * Export selected content to CSV format.
     * 
     * @param contentIds Array of content IDs to export
     * @return CSV file as byte array
     * @throws Exception if export fails
     */
    public byte[] exportToCsv(Long[] contentIds) throws Exception {
        logger.info("Exporting {} items to CSV", contentIds.length);

        // Fetch content
        List<ScrapedContent> contentList = scrapedContentRepository.findAllById(List.of(contentIds));

        if (contentList.isEmpty()) {
            throw new IllegalArgumentException("No content found for export");
        }

        StringBuilder csv = new StringBuilder();

        // CSV Header
        csv.append("ID,Content,Source Domain,Source URL,Scraped Date\n");

        // Add each content item
        for (ScrapedContent content : contentList) {
            csv.append(escapeCsv(content.getId().toString())).append(",");
            csv.append(escapeCsv(content.getContentText())).append(",");
            csv.append(escapeCsv(content.getScrapedSource().getDomainName())).append(",");
            csv.append(escapeCsv(content.getScrapedSource().getSourceUrl())).append(",");
            csv.append(escapeCsv(content.getCreatedAt().format(DATE_FORMATTER)));
            csv.append("\n");
        }

        logger.info("CSV export completed successfully");

        return csv.toString().getBytes("UTF-8");
    }

    /**
     * Escape CSV field value.
     * Handles quotes, commas, and newlines.
     * 
     * @param value Field value
     * @return Escaped value
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        // If value contains comma, quote, or newline, wrap in quotes and escape quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            // Escape quotes by doubling them
            value = value.replace("\"", "\"\"");
            // Wrap in quotes
            return "\"" + value + "\"";
        }

        return value;
    }

    /**
     * Get export statistics.
     * 
     * @param contentIds Array of content IDs
     * @return Statistics map
     */
    public ExportStats getExportStats(Long[] contentIds) {
        List<ScrapedContent> contentList = scrapedContentRepository.findAllById(List.of(contentIds));

        ExportStats stats = new ExportStats();
        stats.totalItems = contentList.size();
        stats.totalCharacters = contentList.stream()
                .mapToInt(c -> c.getContentText().length())
                .sum();
        stats.uniqueSources = contentList.stream()
                .map(c -> c.getScrapedSource().getDomainName())
                .distinct()
                .count();

        return stats;
    }

    /**
     * Export statistics class.
     */
    public static class ExportStats {
        public int totalItems;
        public int totalCharacters;
        public long uniqueSources;

        @Override
        public String toString() {
            return "ExportStats{" +
                    "totalItems=" + totalItems +
                    ", totalCharacters=" + totalCharacters +
                    ", uniqueSources=" + uniqueSources +
                    '}';
        }
    }

    /**
     * Export legacy {@link ScrapedData} rows selected by the given user.
     *
     * Steps:
     * 1) Fetch selected records (user-wise ownership enforced)
     * 2) If CSV -> CSV export
     * 3) If PDF -> PDF export
     * 4) Store export record in {@code export_history}
     * 5) Return the generated file path
     */
    public Path exportSelected(List<Long> selectedIds, String exportType, Long userId) throws Exception {
        if (selectedIds == null || selectedIds.isEmpty()) {
            throw new IllegalArgumentException("selectedIds must not be null/empty");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (exportType == null || exportType.isBlank()) {
            throw new IllegalArgumentException("exportType is required");
        }

        List<Long> ids = selectedIds.stream()
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("selectedIds must contain at least one valid id");
        }

        String normalizedExportType = exportType.trim().toUpperCase(Locale.ROOT);
        if (!normalizedExportType.equals("CSV") && !normalizedExportType.equals("PDF")) {
            throw new IllegalArgumentException("exportType must be CSV or PDF");
        }

        logger.info("Export selected (legacy) started userId={} exportType={} selectedCount={}",
                userId, normalizedExportType, ids.size());

        // Ownership-aware fetch
        List<ScrapedData> data = scrapedDataRepository.findByIdInAndUser_Id(ids, userId);
        if (data.size() != ids.size()) {
            // If any ids were not owned by the user, block the export.
            throw new SecurityException("One or more selectedIds are not allowed");
        }

        // We don't have module/keyword in ScrapedData legacy entity, so:
        // - module: Normal (SEARCH)
        // - keyword: use first title (or empty)
        String keyword = data.get(0).getTitle();
        if (keyword == null) keyword = "";

        ModuleType moduleType = ModuleType.SEARCH;

        Path exportedPath;
        if (normalizedExportType.equals("CSV")) {
            exportedPath = scrapedDataCsvExportService.exportSelectedToCSV(data);
        } else {
            exportedPath = scrapedDataPdfExportService.exportSelectedToPDF(data);
        }

        // Save export history
        String fileName = exportedPath.getFileName().toString();
        ExportHistory history = exportHistoryService.createExportHistory(
                userId,
                moduleType,
                normalizedExportType,
                keyword,
                fileName
        );
        logger.info("Export selected (legacy) completed userId={} exportType={} exportHistoryId={} file={}",
                userId, normalizedExportType, history.getId(), fileName);

        return exportedPath;
    }
}
