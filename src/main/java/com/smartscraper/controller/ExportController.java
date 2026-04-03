package com.smartscraper.controller;

import com.smartscraper.entity.User;
import com.smartscraper.entity.ExportHistory;
import com.smartscraper.entity.ExportHistoryItem;
import com.smartscraper.entity.ModuleType;
import com.smartscraper.entity.SearchHistory;
import com.smartscraper.entity.ScrapedContent;
import com.smartscraper.service.ExportService;
import com.smartscraper.service.ExportHistoryService;
import com.smartscraper.service.UserService;
import com.smartscraper.repository.ExportHistoryItemRepository;
import com.smartscraper.repository.ScrapedContentRepository;
import com.smartscraper.repository.SearchHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controller for exporting content to various formats.
 * Supports PDF and CSV exports of selected content.
 */
@Controller
@RequestMapping("/export")
public class ExportController {

    private static final Logger logger = LoggerFactory.getLogger(ExportController.class);
    private static final DateTimeFormatter FILENAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ExportService exportService;
    private final UserService userService;
    private final ScrapedContentRepository scrapedContentRepository;
    private final ExportHistoryItemRepository exportHistoryItemRepository;
    private final ExportHistoryService exportHistoryService;
    private final SearchHistoryRepository searchHistoryRepository;

    @Autowired
    public ExportController(
            ExportService exportService,
            UserService userService,
            ScrapedContentRepository scrapedContentRepository,
            ExportHistoryItemRepository exportHistoryItemRepository,
            ExportHistoryService exportHistoryService,
            SearchHistoryRepository searchHistoryRepository) {
        this.exportService = exportService;
        this.userService = userService;
        this.scrapedContentRepository = scrapedContentRepository;
        this.exportHistoryItemRepository = exportHistoryItemRepository;
        this.exportHistoryService = exportHistoryService;
        this.searchHistoryRepository = searchHistoryRepository;
    }

    /**
     * Export selected content to PDF format.
     * 
     * @param contentIds Array of content IDs to export
     * @param principal Current logged-in user
     * @param redirectAttributes For flash messages
     * @return PDF file download or redirect on error
     */
    @PostMapping("/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(value = "contentIds", required = false) Long[] contentIds,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) exporting {} items to PDF",
                currentUser.getUsername(), currentUser.getId(),
                contentIds != null ? contentIds.length : 0);

        // Validate input
        if (contentIds == null || contentIds.length == 0) {
            logger.warn("No content IDs provided for PDF export");
            return ResponseEntity.badRequest().build();
        }

        try {
            List<Long> requestedIds = Arrays.asList(contentIds);
            List<ScrapedContent> eligibleContents = scrapedContentRepository
                    .findByIdInAndSearchHistory_UserId(requestedIds, currentUser.getId());
            List<Long> eligibleIds = eligibleContents.stream()
                    .map(ScrapedContent::getId)
                    .toList();

            if (eligibleIds.isEmpty() || eligibleIds.size() != requestedIds.size()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Long[] eligibleArray = eligibleIds.toArray(new Long[0]);

            // Generate PDF
            byte[] pdfBytes = exportService.exportToPdf(eligibleArray);

            // Generate filename
            String filename = "research_export_" + 
                    LocalDateTime.now().format(FILENAME_DATE_FORMAT) + ".pdf";

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(pdfBytes.length);

            // Compute keyword from the first eligible content's search history topic
            String keyword = "";
            if (!eligibleContents.isEmpty()) {
                SearchHistory sh = searchHistoryRepository.findById(eligibleContents.get(0).getSearchId())
                        .orElse(null);
                if (sh != null && sh.getQueryText() != null) {
                    keyword = sh.getQueryText();
                }
            }

            // Persist export history
            ExportHistory exportHistory = exportHistoryService.createExportHistory(
                    currentUser.getId(),
                    ModuleType.RESEARCH,
                    "PDF",
                    keyword,
                    filename
            );

            List<ExportHistoryItem> items = eligibleIds.stream()
                    .map(id -> new ExportHistoryItem(exportHistory.getId(), id))
                    .toList();
            if (!items.isEmpty()) {
                exportHistoryItemRepository.saveAll(items);
            }

            logger.info("PDF export successful: {} bytes, {} items", pdfBytes.length, eligibleArray.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid content IDs for PDF export: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error exporting to PDF: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Export selected content to CSV format.
     * 
     * @param contentIds Array of content IDs to export
     * @param principal Current logged-in user
     * @param redirectAttributes For flash messages
     * @return CSV file download or redirect on error
     */
    @PostMapping("/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(value = "contentIds", required = false) Long[] contentIds,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) exporting {} items to CSV",
                currentUser.getUsername(), currentUser.getId(),
                contentIds != null ? contentIds.length : 0);

        // Validate input
        if (contentIds == null || contentIds.length == 0) {
            logger.warn("No content IDs provided for CSV export");
            return ResponseEntity.badRequest().build();
        }

        try {
            List<Long> requestedIds = Arrays.asList(contentIds);
            List<ScrapedContent> eligibleContents = scrapedContentRepository
                    .findByIdInAndSearchHistory_UserId(requestedIds, currentUser.getId())
                    ;

            List<Long> eligibleIds = eligibleContents.stream()
                    .map(ScrapedContent::getId)
                    .toList();

            if (eligibleIds.isEmpty() || eligibleIds.size() != requestedIds.size()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Long[] eligibleArray = eligibleIds.toArray(new Long[0]);

            // Generate CSV
            byte[] csvBytes = exportService.exportToCsv(eligibleArray);

            // Generate filename
            String filename = "research_export_" + 
                    LocalDateTime.now().format(FILENAME_DATE_FORMAT) + ".csv";

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("text", "csv"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(csvBytes.length);

            // Persist export history
            String keyword = "";
            if (!eligibleContents.isEmpty()) {
                SearchHistory sh = searchHistoryRepository.findById(eligibleContents.get(0).getSearchId())
                        .orElse(null);
                if (sh != null && sh.getQueryText() != null) {
                    keyword = sh.getQueryText();
                }
            }

            ExportHistory exportHistory = exportHistoryService.createExportHistory(
                    currentUser.getId(),
                    ModuleType.RESEARCH,
                    "CSV",
                    keyword,
                    filename
            );

            List<ExportHistoryItem> items = eligibleIds.stream()
                    .map(id -> new ExportHistoryItem(exportHistory.getId(), id))
                    .toList();
            if (!items.isEmpty()) {
                exportHistoryItemRepository.saveAll(items);
            }

            logger.info("CSV export successful: {} bytes, {} items", csvBytes.length, eligibleArray.length);

            return new ResponseEntity<>(csvBytes, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid content IDs for CSV export: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error exporting to CSV: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
