package com.smartscraper.controller;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.smartscraper.entity.ExportHistory;
import com.smartscraper.entity.ExportHistoryItem;
import com.smartscraper.entity.ModuleType;
import com.smartscraper.entity.ScrapedContent;
import com.smartscraper.entity.SearchHistory;
import com.smartscraper.entity.User;
import com.smartscraper.repository.ExportHistoryItemRepository;
import com.smartscraper.repository.ExportHistoryRepository;
import com.smartscraper.repository.ScrapedContentRepository;
import com.smartscraper.repository.SearchHistoryRepository;
import com.smartscraper.service.ExportHistoryService;
import com.smartscraper.service.UserService;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/export/selected")
public class ExportSelectedController {

    private static final Logger logger = LoggerFactory.getLogger(ExportSelectedController.class);
    private static final DateTimeFormatter FILENAME_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final UserService userService;
    private final ScrapedContentRepository scrapedContentRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final ExportHistoryService exportHistoryService;
    private final ExportHistoryItemRepository exportHistoryItemRepository;
    private final ExportHistoryRepository exportHistoryRepository;

    private final Path exportsDir;

    public ExportSelectedController(
            UserService userService,
            ScrapedContentRepository scrapedContentRepository,
            SearchHistoryRepository searchHistoryRepository,
            ExportHistoryService exportHistoryService,
            ExportHistoryItemRepository exportHistoryItemRepository,
            ExportHistoryRepository exportHistoryRepository,
            @Value("${app.exports.dir:exports}") String exportsDir) {
        this.userService = userService;
        this.scrapedContentRepository = scrapedContentRepository;
        this.searchHistoryRepository = searchHistoryRepository;
        this.exportHistoryService = exportHistoryService;
        this.exportHistoryItemRepository = exportHistoryItemRepository;
        this.exportHistoryRepository = exportHistoryRepository;
        this.exportsDir = Path.of(exportsDir);
    }

    @PostMapping
    public ResponseEntity<?> exportSelected(
            @RequestParam(value = "selectedIds", required = false) Long[] selectedIds,
            @RequestParam(value = "contentIds", required = false) Long[] contentIds,
            java.security.Principal principal) {

        User currentUser = userService.getCurrentUser(principal);

        List<Long> ids = extractIds(selectedIds, contentIds);
        if (ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No selectedIds provided"));
        }

        logger.info("Export operations started userId={} selectedCount={}", currentUser.getId(), ids.size());

        try {
            // 1) Fetch selected records (user-wise ownership enforced)
            List<ScrapedContent> selectedContents =
                    scrapedContentRepository.findByIdInAndSearchHistory_UserId(ids, currentUser.getId());

            if (selectedContents.size() != ids.size()) {
                // Avoid leaking which ids are invalid; treat as forbidden.
                return ResponseEntity.status(403).body(Map.of("error", "One or more selectedIds are not allowed"));
            }

            // 2) Determine keyword + module from the first item's search history
            ScrapedContent first = selectedContents.get(0);
            SearchHistory sh = searchHistoryRepository.findById(first.getSearchId())
                    .orElseThrow(() -> new IllegalArgumentException("SearchHistory not found for selected content"));

            String keyword = sh.getQueryText();
            ModuleType moduleType = sh.getModuleType();
            logger.info("Export context resolved userId={} module={} keyword='{}' selectedCount={}",
                    currentUser.getId(), moduleType, keyword, selectedContents.size());

            // 3) Ensure exports folder exists
            Files.createDirectories(exportsDir);

            // 4) Generate + save CSV
            String csvBaseName = "selected_export_" + FILENAME_DATE_FORMAT.format(LocalDateTime.now());
            String csvFileName = csvBaseName + ".csv";
            Path csvPath = exportsDir.resolve(csvFileName);
            generateCsv(selectedContents, csvPath);

            ExportHistory csvHistory = exportHistoryService.createExportHistory(
                    currentUser.getId(),
                    moduleType,
                    "CSV",
                    keyword,
                    csvFileName
            );
            persistExportItems(csvHistory.getId(), selectedContents);

            // 5) Generate + save PDF
            String pdfFileName = csvBaseName + ".pdf";
            Path pdfPath = exportsDir.resolve(pdfFileName);
            generatePdf(selectedContents, sh, pdfPath);

            ExportHistory pdfHistory = exportHistoryService.createExportHistory(
                    currentUser.getId(),
                    moduleType,
                    "PDF",
                    keyword,
                    pdfFileName
            );
            persistExportItems(pdfHistory.getId(), selectedContents);

            logger.info("Export operations completed userId={} csvExportHistoryId={} csvFile={} pdfExportHistoryId={} pdfFile={}",
                    currentUser.getId(),
                    csvHistory.getId(), csvFileName,
                    pdfHistory.getId(), pdfFileName);

            // 6) Return download links
            String csvUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/export/selected/download/")
                    .path(csvHistory.getId().toString())
                    .toUriString();

            String pdfUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/export/selected/download/")
                    .path(pdfHistory.getId().toString())
                    .toUriString();

            return ResponseEntity.ok(Map.of(
                    "csvDownloadUrl", csvUrl,
                    "pdfDownloadUrl", pdfUrl,
                    "csvExportHistoryId", csvHistory.getId(),
                    "pdfExportHistoryId", pdfHistory.getId()
            ));
        } catch (IllegalArgumentException e) {
            logger.warn("Export selected failed for user {}: {}", currentUser.getId(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Export selected failed for user {}: {}", currentUser.getId(), e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Export failed"));
        }
    }

    @GetMapping("/download/{exportHistoryId}")
    public ResponseEntity<Resource> download(
            @PathVariable Long exportHistoryId,
            java.security.Principal principal) {

        User currentUser = userService.getCurrentUser(principal);

        ExportHistory history = exportHistoryRepository.findById(exportHistoryId)
                .orElse(null);
        if (history == null) {
            return ResponseEntity.notFound().build();
        }

        if (!Objects.equals(history.getUserId(), currentUser.getId())) {
            return ResponseEntity.status(403).build();
        }

        try {
            Path filePath = exportsDir.resolve(history.getFileName()).normalize();

            if (!filePath.startsWith(exportsDir.normalize()) || !Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(filePath);
            String exportTypeUpper = history.getExportType() == null ? "" : history.getExportType().toUpperCase(Locale.ROOT);

            MediaType contentType = exportTypeUpper.equals("CSV")
                    ? new MediaType("text", "csv", StandardCharsets.UTF_8)
                    : MediaType.APPLICATION_PDF;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            headers.setContentDispositionFormData("attachment", history.getFileName());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);

        } catch (Exception e) {
            logger.error("Download failed for user {} exportHistoryId {}: {}",
                    currentUser.getId(), exportHistoryId, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    private List<Long> extractIds(Long[] selectedIds, Long[] contentIds) {
        Long[] src = (selectedIds != null && selectedIds.length > 0) ? selectedIds : contentIds;
        if (src == null) return List.of();
        return Arrays.stream(src)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void persistExportItems(Long exportHistoryId, List<ScrapedContent> selectedContents) {
        List<ExportHistoryItem> items = selectedContents.stream()
                .map(sc -> new ExportHistoryItem(exportHistoryId, sc.getId()))
                .toList();
        if (!items.isEmpty()) {
            exportHistoryItemRepository.saveAll(items);
        }
    }

    private void generateCsv(List<ScrapedContent> selectedContents, Path csvPath) throws Exception {
        // Requirement: use OpenCSV
        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(Files.newOutputStream(csvPath), StandardCharsets.UTF_8)
        )) {
            writer.writeNext(new String[]{
                    "ContentId",
                    "SearchId",
                    "RelevanceScore",
                    "SourceDomain",
                    "SourceUrl",
                    "ScrapedAt",
                    "ContentText"
            });

            for (ScrapedContent c : selectedContents) {
                writer.writeNext(new String[]{
                        String.valueOf(c.getId()),
                        String.valueOf(c.getSearchId()),
                        c.getRelevanceScore() == null ? "" : c.getRelevanceScore().toString(),
                        c.getScrapedSource() == null ? "" : c.getScrapedSource().getDomainName(),
                        c.getScrapedSource() == null ? "" : c.getScrapedSource().getSourceUrl(),
                        c.getCreatedAt() == null ? "" : c.getCreatedAt().toString(),
                        c.getContentText() == null ? "" : c.getContentText()
                });
            }
        }
    }

    private void generatePdf(List<ScrapedContent> selectedContents, SearchHistory sh, Path pdfPath) throws Exception {
        // Requirement: use iText (project already uses iText-compatible OpenPDF)
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        try (var out = Files.newOutputStream(pdfPath)) {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Paragraph title = new Paragraph("SmartScraper Export", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(18);
            document.add(title);

            Font metaFont = new Font(Font.HELVETICA, 10, Font.NORMAL, java.awt.Color.GRAY);
            Paragraph meta = new Paragraph(
                    "Exported: " + LocalDateTime.now().toString() +
                            "\nKeyword: " + (sh.getQueryText() == null ? "" : sh.getQueryText()) +
                            "\nModule: " + (sh.getModuleType() == null ? "" : sh.getModuleType().toString()) +
                            "\nItems: " + selectedContents.size(),
                    metaFont
            );
            meta.setAlignment(Element.ALIGN_LEFT);
            meta.setSpacingAfter(14);
            document.add(meta);

            Font contentFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
            Font sourceFont = new Font(Font.HELVETICA, 9, Font.ITALIC, java.awt.Color.BLUE);

            int idx = 1;
            for (ScrapedContent c : selectedContents) {
                Paragraph header = new Paragraph("Item " + idx++,
                        new Font(Font.HELVETICA, 12, Font.BOLD));
                header.setSpacingBefore(10);
                header.setSpacingAfter(6);
                document.add(header);

                String contentText = c.getContentText() == null ? "" : c.getContentText();
                Paragraph contentPara = new Paragraph(contentText, contentFont);
                contentPara.setSpacingAfter(6);
                document.add(contentPara);

                String sourceUrl = (c.getScrapedSource() == null) ? "" : c.getScrapedSource().getSourceUrl();
                Paragraph src = new Paragraph("Source: " + sourceUrl, sourceFont);
                src.setSpacingAfter(10);
                document.add(src);
            }

            document.close();
        }
    }
}

