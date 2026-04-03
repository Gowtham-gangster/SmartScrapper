package com.smartscraper.controller;

import com.smartscraper.entity.SavedContent;
import com.smartscraper.entity.User;
import com.smartscraper.service.SavedContentService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/saved")
public class SavedContentController {

    private static final Logger logger = LoggerFactory.getLogger(SavedContentController.class);

    private final UserService userService;
    private final SavedContentService savedContentService;

    @Autowired
    public SavedContentController(UserService userService, SavedContentService savedContentService) {
        this.userService = userService;
        this.savedContentService = savedContentService;
    }

    /**
     * Display saved content page.
     */
    @GetMapping
    public String showSavedContent(
            Principal principal,
            Model model,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size) {
        
        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) accessed saved content page", 
                currentUser.getUsername(), currentUser.getId());

        int safeSize = size < 1 ? 10 : size;
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "savedAt"));

        // Get paginated saved content
        Page<SavedContent> savedPage = savedContentService.getSavedContentWithDetails(currentUser.getId(), pageable);
        List<SavedContent> savedContentList = savedContentService.getSavedContentListWithDetails(
                currentUser.getId(), savedPage);

        logger.info("Found {} saved items (page {}, size {}) for user {}",
                savedPage.getTotalElements(), safePage, safeSize, currentUser.getId());

        // Add data to model
        model.addAttribute("user", currentUser);
        model.addAttribute("userId", currentUser.getId());
        model.addAttribute("username", currentUser.getUsername());
        model.addAttribute("savedContentList", savedContentList);
        model.addAttribute("savedCount", savedPage.getTotalElements());
        model.addAttribute("currentPage", safePage);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("totalPages", savedPage.getTotalPages());

        return "saved";
    }

    /**
     * Delete a single saved content item.
     */
    @PostMapping("/{savedId}/delete")
    public String deleteSavedContent(
            @PathVariable Long savedId,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) attempting to delete saved content ID: {}",
                currentUser.getUsername(), currentUser.getId(), savedId);

        boolean deleted = savedContentService.unsaveContent(currentUser.getId(), savedId);
        
        if (deleted) {
            redirectAttributes.addFlashAttribute("successMessage", "Content removed from saved items");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete content");
        }

        return "redirect:/saved";
    }

    /**
     * Delete multiple saved content items at once (bulk delete).
     */
    @PostMapping("/delete-selected")
    public String deleteSelected(
            @RequestParam(value = "savedIds", required = false) Long[] savedIds,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) attempting to delete {} selected items",
                currentUser.getUsername(), currentUser.getId(),
                savedIds != null ? savedIds.length : 0);

        if (savedIds == null || savedIds.length == 0) {
            logger.warn("No saved content IDs provided for bulk delete");
            redirectAttributes.addFlashAttribute("errorMessage", "Please select at least one item to delete");
            return "redirect:/saved";
        }

        List<Long> idList = Arrays.stream(savedIds).collect(Collectors.toList());
        int deletedCount = savedContentService.deleteSavedContent(currentUser.getId(), idList);

        if (deletedCount > 0) {
            redirectAttributes.addFlashAttribute("successMessage", 
                    deletedCount + " item(s) deleted successfully");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete selected items");
        }

        return "redirect:/saved";
    }

    /**
     * Delete all saved content for the current user.
     */
    @PostMapping("/delete-all")
    public String deleteAll(Principal principal, RedirectAttributes redirectAttributes) {
        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) attempting to delete all saved content",
                currentUser.getUsername(), currentUser.getId());

        long count = savedContentService.deleteAllSavedContent(currentUser.getId());
        
        if (count == 0) {
            redirectAttributes.addFlashAttribute("infoMessage", "No saved content to delete");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", count + " item(s) deleted successfully");
        }

        return "redirect:/saved";
    }
}
