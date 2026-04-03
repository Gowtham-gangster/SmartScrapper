package com.smartscraper.controller;

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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
@RequestMapping("/export")
public class ExportHistoryController {

    private static final Logger logger = LoggerFactory.getLogger(ExportHistoryController.class);

    private final UserService userService;
    private final ExportHistoryRepository exportHistoryRepository;

    public ExportHistoryController(UserService userService, ExportHistoryRepository exportHistoryRepository) {
        this.userService = userService;
        this.exportHistoryRepository = exportHistoryRepository;
    }

    @GetMapping("/history")
    public String exportHistoryPage(
            Principal principal,
            Model model,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size) {

        User currentUser = userService.getCurrentUser(principal);

        int safeSize = size < 1 ? 10 : size;
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "exportedAt"));

        Page<ExportHistory> exportHistoryPage = exportHistoryRepository
                .findByUserIdOrderByExportedAtDesc(currentUser.getId(), pageable);

        logger.info("User {} viewed export history page {}/{}",
                currentUser.getId(), safePage, exportHistoryPage.getTotalPages());

        model.addAttribute("user", currentUser);
        model.addAttribute("username", currentUser.getUsername());
        model.addAttribute("exportHistoryPage", exportHistoryPage);
        model.addAttribute("exportHistoryList", exportHistoryPage.getContent());
        model.addAttribute("currentPage", safePage);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("totalPages", exportHistoryPage.getTotalPages());

        return "export-history";
    }
}

