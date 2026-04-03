package com.smartscraper.controller;

import com.smartscraper.dto.ExportHistoryDto;
import com.smartscraper.dto.PagedResponse;
import com.smartscraper.entity.ExportHistory;
import com.smartscraper.entity.User;
import com.smartscraper.repository.ExportHistoryRepository;
import com.smartscraper.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import java.security.Principal;

@RestController
@RequestMapping("/api")
public class ApiExportController {

    private static final Logger logger = LoggerFactory.getLogger(ApiExportController.class);

    private final UserService userService;
    private final ExportHistoryRepository exportHistoryRepository;
    private final Path exportsDir;

    public ApiExportController(UserService userService,
                                ExportHistoryRepository exportHistoryRepository,
                                @org.springframework.beans.factory.annotation.Value("${app.exports.dir:exports}") String exportsDir) {
        this.userService = userService;
        this.exportHistoryRepository = exportHistoryRepository;
        this.exportsDir = Path.of(exportsDir);
    }

    @GetMapping("/export")
    @Transactional(readOnly = true)
    public PagedResponse<ExportHistoryDto> export(
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size,
            Principal principal) {

        User currentUser = userService.getCurrentUser(principal);

        if (page < 0) page = 0;
        if (size < 1) size = 10;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "exportedAt"));
        Page<ExportHistory> exportHistoryPage = exportHistoryRepository
                .findByUserIdOrderByExportedAtDesc(currentUser.getId(), pageable);

        List<ExportHistoryDto> items = exportHistoryPage.getContent().stream().map(h -> {
            ExportHistoryDto dto = new ExportHistoryDto();
            dto.setId(h.getId());
            dto.setUserId(h.getUserId());
            dto.setExportType(h.getExportType());
            dto.setModule(h.getModule());
            dto.setKeyword(h.getKeyword());
            dto.setFileName(h.getFileName());
            dto.setExportedAt(h.getExportedAt());

            // Provide download URL only if the file exists on disk
            if (h.getFileName() != null) {
                Path filePath = exportsDir.resolve(h.getFileName()).normalize();
                if (filePath.startsWith(exportsDir.normalize()) && Files.exists(filePath)) {
                    dto.setDownloadUrl("/export/selected/download/" + h.getId());
                }
            }
            return dto;
        }).toList();

        logger.info("REST /api/export user={} page={} size={}", currentUser.getId(), page, size);
        return new PagedResponse<>(items, exportHistoryPage.getTotalElements(), exportHistoryPage.getTotalPages(), page, size);
    }
}

