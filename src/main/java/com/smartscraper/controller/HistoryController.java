package com.smartscraper.controller;

import com.smartscraper.entity.ModuleType;
import com.smartscraper.entity.SearchHistory;
import com.smartscraper.entity.User;
import com.smartscraper.service.HistoryService;
import com.smartscraper.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing search history.
 * Displays user's search history organized by module type.
 */
@Controller
@RequestMapping("/history")
public class HistoryController {

    private static final Logger logger = LoggerFactory.getLogger(HistoryController.class);

    private final UserService userService;
    private final HistoryService historyService;

    @Autowired
    public HistoryController(UserService userService, HistoryService historyService) {
        this.userService = userService;
        this.historyService = historyService;
    }

    /**
     * Display search history page.
     */
    @GetMapping
    public String showHistory(
            Principal principal,
            @RequestParam(value = "module", required = false) String moduleType,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size,
            Model model) {

        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) accessed history page with filter: {}",
                currentUser.getUsername(), currentUser.getId(), moduleType);

        // Add user information
        model.addAttribute("user", currentUser);
        model.addAttribute("userId", currentUser.getId());
        model.addAttribute("username", currentUser.getUsername());

        int safeSize = size < 1 ? 10 : size;
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "searchedAt"));

        // Fetch search history based on filter
        Page<SearchHistory> historyPage;
        String activeFilter = "ALL";

        if (moduleType != null && !moduleType.isEmpty() && !moduleType.equalsIgnoreCase("ALL")) {
            try {
                ModuleType module = ModuleType.valueOf(moduleType.toUpperCase());
                historyPage = historyService.getSearchHistoryByModule(currentUser.getId(), module, pageable);
                activeFilter = moduleType.toUpperCase();
                logger.info("Fetched {} history entries for module: {}", historyPage.getTotalElements(), module);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid module type: {}, showing all history", moduleType);
                historyPage = historyService.getSearchHistory(currentUser.getId(), pageable);
            }
        } else {
            historyPage = historyService.getSearchHistory(currentUser.getId(), pageable);
            logger.info("Fetched {} total history entries", historyPage.getTotalElements());
        }

        List<SearchHistory> historyList = historyPage.getContent();

        // Get statistics by module type
        Map<String, Long> moduleStats = historyService.getModuleStatistics(currentUser.getId());

        // Add data to model
        model.addAttribute("historyList", historyList);
        model.addAttribute("historyCount", historyPage.getTotalElements());
        model.addAttribute("activeFilter", activeFilter);
        model.addAttribute("moduleStats", moduleStats);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("totalPages", historyPage.getTotalPages());

        return "history";
    }

    @PostMapping("/{historyId}/delete")
    public String deleteHistoryEntry(
            @RequestParam(value = "module", required = false) String moduleType,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size,
            @RequestParam(value = "confirm", required = false) String confirm,
            @org.springframework.web.bind.annotation.PathVariable("historyId") Long historyId,
            Principal principal,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser(principal);
        
        if (confirm == null || !confirm.equals("yes")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Confirmation required to delete history.");
            return buildRedirect(moduleType, page, size);
        }

        boolean deleted = historyService.deleteSearchHistory(currentUser.getId(), historyId);
        
        if (deleted) {
            redirectAttributes.addFlashAttribute("successMessage", "History item deleted.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete history item.");
        }

        return buildRedirect(moduleType, page, size);
    }

    private String buildRedirect(String moduleType, int page, int size) {
        String modulePart = (moduleType != null && !moduleType.isBlank() && !moduleType.equalsIgnoreCase("ALL"))
                ? "&module=" + moduleType
                : "";
        return "redirect:/history?page=" + page + "&size=" + size + modulePart;
    }
}
