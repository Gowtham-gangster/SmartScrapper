package com.smartscraper.service;

import com.smartscraper.entity.ExportHistory;
import com.smartscraper.entity.ModuleType;
import com.smartscraper.repository.ExportHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ExportHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(ExportHistoryService.class);

    private final ExportHistoryRepository exportHistoryRepository;

    public ExportHistoryService(ExportHistoryRepository exportHistoryRepository) {
        this.exportHistoryRepository = exportHistoryRepository;
    }

    /**
     * Persist an export event into {@code export_history}.
     */
    public ExportHistory createExportHistory(
            Long userId,
            ModuleType module,
            String exportType,
            String keyword,
            String fileName) {

        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (module == null) throw new IllegalArgumentException("module is required");
        if (exportType == null || exportType.isBlank()) throw new IllegalArgumentException("exportType is required");
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName is required");

        String normalizedExportType = exportType.trim().toUpperCase(Locale.ROOT);
        if (!normalizedExportType.equals("CSV") && !normalizedExportType.equals("PDF")) {
            throw new IllegalArgumentException("exportType must be CSV or PDF");
        }

        String safeKeyword = keyword == null ? "" : keyword.trim();
        if (safeKeyword.isEmpty()) {
            // Requirement says `keyword` exists; we store empty string if unknown.
            logger.debug("ExportHistory keyword not provided; storing empty string");
        }

        String normalizedModule = normalizeModule(module);

        ExportHistory history = new ExportHistory(
                userId,
                normalizedModule,
                normalizedExportType,
                safeKeyword,
                fileName.trim()
        );

        ExportHistory saved = exportHistoryRepository.save(history);
        logger.info("Created export history ID={} for user={} module={} type={} file={}",
                saved.getId(), userId, module, normalizedExportType, fileName);
        return saved;
    }

    private String normalizeModule(ModuleType moduleType) {
        return switch (moduleType) {
            case NEWS -> "News";
            case ECOMMERCE -> "Ecommerce";
            case RESEARCH -> "Research";
            case SEARCH -> "Normal";
        };
    }
}

