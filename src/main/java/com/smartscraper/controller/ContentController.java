package com.smartscraper.controller;

import com.smartscraper.entity.SavedContent;
import com.smartscraper.entity.User;
import com.smartscraper.repository.SavedContentRepository;
import com.smartscraper.repository.ScrapedContentRepository;
import com.smartscraper.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/**
 * Controller for managing saved content.
 * Handles save and unsave operations.
 */
@Controller
@RequestMapping("/content")
public class ContentController {

    private static final Logger logger = LoggerFactory.getLogger(ContentController.class);

    private final UserService userService;
    private final SavedContentRepository savedContentRepository;
    private final ScrapedContentRepository scrapedContentRepository;

    @Autowired
    public ContentController(
            UserService userService,
            SavedContentRepository savedContentRepository,
            ScrapedContentRepository scrapedContentRepository) {
        this.userService = userService;
        this.savedContentRepository = savedContentRepository;
        this.scrapedContentRepository = scrapedContentRepository;
    }

    /**
     * Save content for later reference.
     * 
     * @param contentId Scraped content ID
     * @param principal Current logged-in user
     * @param redirectAttributes For flash messages
     * @return Redirect back to previous page
     */
    @PostMapping("/{contentId}/save")
    public String saveContent(
            @PathVariable Long contentId,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) attempting to save content ID: {}",
                currentUser.getUsername(), currentUser.getId(), contentId);

        try {
            // Check if content exists
            if (!scrapedContentRepository.existsByIdAndSearchHistory_UserId(contentId, currentUser.getId())) {
                logger.warn("Content not found: {}", contentId);
                redirectAttributes.addFlashAttribute("errorMessage", "Content not found");
                return "redirect:/dashboard";
            }

            // Check if already saved
            if (savedContentRepository.existsByUserIdAndContentId(currentUser.getId(), contentId)) {
                logger.info("Content {} already saved by user {}", contentId, currentUser.getId());
                redirectAttributes.addFlashAttribute("infoMessage", "Content already saved");
                return getPreviousPage(redirectAttributes);
            }

            // Save content
            SavedContent savedContent = new SavedContent(currentUser.getId(), contentId);
            savedContentRepository.save(savedContent);

            logger.info("User {} successfully saved content {}", currentUser.getId(), contentId);
            redirectAttributes.addFlashAttribute("successMessage", "Content saved successfully");

        } catch (Exception e) {
            logger.error("Error saving content: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to save content");
        }

        return getPreviousPage(redirectAttributes);
    }

    /**
     * Unsave (remove) previously saved content.
     * 
     * @param contentId Scraped content ID
     * @param principal Current logged-in user
     * @param redirectAttributes For flash messages
     * @return Redirect back to previous page
     */
    @PostMapping("/{contentId}/unsave")
    public String unsaveContent(
            @PathVariable Long contentId,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) attempting to unsave content ID: {}",
                currentUser.getUsername(), currentUser.getId(), contentId);

        try {
            // Check if saved
            if (!savedContentRepository.existsByUserIdAndContentId(currentUser.getId(), contentId)) {
                logger.info("Content {} not saved by user {}", contentId, currentUser.getId());
                redirectAttributes.addFlashAttribute("infoMessage", "Content was not saved");
                return getPreviousPage(redirectAttributes);
            }

            // Remove saved content
            savedContentRepository.deleteByUserIdAndContentId(currentUser.getId(), contentId);

            logger.info("User {} successfully unsaved content {}", currentUser.getId(), contentId);
            redirectAttributes.addFlashAttribute("successMessage", "Content removed from saved items");

        } catch (Exception e) {
            logger.error("Error unsaving content: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to remove content");
        }

        return getPreviousPage(redirectAttributes);
    }

    /**
     * Save multiple content items at once (bulk save).
     * 
     * @param contentIds Array of content IDs to save
     * @param principal Current logged-in user
     * @param redirectAttributes For flash messages
     * @return Redirect back to previous page
     */
    @PostMapping("/save-selected")
    public String saveSelected(
            @RequestParam(value = "contentIds", required = false) Long[] contentIds,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) attempting to save {} selected items",
                currentUser.getUsername(), currentUser.getId(), 
                contentIds != null ? contentIds.length : 0);

        // Validate input
        if (contentIds == null || contentIds.length == 0) {
            logger.warn("No content IDs provided for bulk save");
            redirectAttributes.addFlashAttribute("errorMessage", "Please select at least one item to save");
            return "redirect:/dashboard";
        }

        try {
            int savedCount = 0;
            int alreadySavedCount = 0;
            int errorCount = 0;

            for (Long contentId : contentIds) {
                try {
                    // Check if content exists
                    if (!scrapedContentRepository.existsByIdAndSearchHistory_UserId(contentId, currentUser.getId())) {
                        logger.warn("Content not found: {}", contentId);
                        errorCount++;
                        continue;
                    }

                    // Check if already saved
                    if (savedContentRepository.existsByUserIdAndContentId(currentUser.getId(), contentId)) {
                        logger.debug("Content {} already saved by user {}", contentId, currentUser.getId());
                        alreadySavedCount++;
                        continue;
                    }

                    // Save content
                    SavedContent savedContent = new SavedContent(currentUser.getId(), contentId);
                    savedContentRepository.save(savedContent);
                    savedCount++;

                } catch (Exception e) {
                    logger.error("Error saving content {}: {}", contentId, e.getMessage());
                    errorCount++;
                }
            }

            // Build success message
            StringBuilder message = new StringBuilder();
            if (savedCount > 0) {
                message.append(savedCount).append(" item(s) saved successfully");
            }
            if (alreadySavedCount > 0) {
                if (message.length() > 0) message.append(". ");
                message.append(alreadySavedCount).append(" item(s) already saved");
            }
            if (errorCount > 0) {
                if (message.length() > 0) message.append(". ");
                message.append(errorCount).append(" item(s) failed to save");
            }

            logger.info("Bulk save completed for user {}: Saved: {}, Already saved: {}, Errors: {}",
                    currentUser.getId(), savedCount, alreadySavedCount, errorCount);

            if (savedCount > 0 || alreadySavedCount > 0) {
                redirectAttributes.addFlashAttribute("successMessage", message.toString());
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Failed to save selected items");
            }

        } catch (Exception e) {
            logger.error("Error during bulk save: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An error occurred while saving");
        }

        return "redirect:/dashboard";
    }

    /**
     * Get redirect URL for previous page.
     * Defaults to dashboard if referer is not available.
     * 
     * @param redirectAttributes Redirect attributes
     * @return Redirect URL
     */
    private String getPreviousPage(RedirectAttributes redirectAttributes) {
        // For now, redirect to dashboard
        // In production, you could use HTTP Referer header
        return "redirect:/dashboard";
    }
}
