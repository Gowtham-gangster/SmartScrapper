package com.smartscraper.controller;

import com.smartscraper.entity.*;
import com.smartscraper.repository.SavedContentRepository;
import com.smartscraper.repository.ScrapedContentRepository;
import com.smartscraper.repository.SearchHistoryRepository;
import com.smartscraper.service.UserService;
import com.smartscraper.service.ScraperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/research")
public class ResearchController {

    private static final Logger logger = LoggerFactory.getLogger(ResearchController.class);

    private final UserService userService;
    private final SearchHistoryRepository searchHistoryRepository;
    private final ScrapedContentRepository scrapedContentRepository;
    private final SavedContentRepository savedContentRepository;
    private final ScraperFactory scraperFactory;

    @Autowired
    public ResearchController(
            UserService userService,
            SearchHistoryRepository searchHistoryRepository,
            ScrapedContentRepository scrapedContentRepository,
            SavedContentRepository savedContentRepository,
            ScraperFactory scraperFactory) {
        this.userService = userService;
        this.searchHistoryRepository = searchHistoryRepository;
        this.scrapedContentRepository = scrapedContentRepository;
        this.savedContentRepository = savedContentRepository;
        this.scraperFactory = scraperFactory;
    }

    /**
     * Display research search page.
     */
    @GetMapping
    public String researchPage(Principal principal, Model model) {
        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) accessed research page", currentUser.getUsername(), currentUser.getId());

        // Add user information to model
        model.addAttribute("user", currentUser);
        model.addAttribute("username", currentUser.getUsername());

        // Get recent research searches for this user
        List<SearchHistory> recentSearches = searchHistoryRepository
                .findTop10ByUserIdAndModuleTypeOrderBySearchedAtDesc(currentUser.getId(), ModuleType.RESEARCH);
        model.addAttribute("recentSearches", recentSearches);

        return "research";
    }

    /**
     * Handle research search submission.
     * 
     * @param topicName The research topic to search for
     * @param principal Current logged-in user
     * @param redirectAttributes For flash messages
     * @return Redirect to results page
     */
    @PostMapping("/search")
    public String searchResearch(
            @RequestParam("topicName") String topicName,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) initiated research search for topic: '{}'",
                currentUser.getUsername(), currentUser.getId(), topicName);

        // Validation: Check if topic name is empty
        if (topicName == null || topicName.trim().isEmpty()) {
            logger.warn("Research search failed: Empty topic name");
            redirectAttributes.addFlashAttribute("errorMessage", "Please enter a research topic");
            return "redirect:/research";
        }

        try {
            // Step 1: Save search history
            SearchHistory searchHistory = new SearchHistory(
                    currentUser.getId(),
                    ModuleType.RESEARCH,
                    topicName.trim()
            );
            SearchHistory savedSearch = searchHistoryRepository.save(searchHistory);
            logger.info("Search history saved with ID: {}", savedSearch.getId());

            // Step 2: Trigger research scraping
            logger.info("Scraping started userId={} module={} searchHistoryId={} topic='{}'",
                    currentUser.getId(), ModuleType.RESEARCH, savedSearch.getId(), topicName.trim());
            scraperFactory
                    .getScraper(ModuleType.RESEARCH)
                    .scrape(savedSearch.getId() + "|" + topicName.trim());

            long savedCount = scrapedContentRepository.countBySearchId(savedSearch.getId());
            logger.info("Scraping completed userId={} module={} searchHistoryId={} savedCount={}",
                    currentUser.getId(), ModuleType.RESEARCH, savedSearch.getId(), savedCount);

            // Step 3: Redirect to results page
            redirectAttributes.addFlashAttribute("successMessage",
                    "Research completed for topic: " + topicName);
            return "redirect:/research/results?searchId=" + savedSearch.getId();

        } catch (Exception e) {
            logger.error("Scraping error userId={} module={} topic='{}': {}",
                    currentUser.getId(), ModuleType.RESEARCH, topicName, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An error occurred during research. Please try again.");
            return "redirect:/research";
        }
    }

    /**
     * Display research results.
     * 
     * @param searchId The search history ID
     * @param principal Current logged-in user
     * @param model Model for view
     * @return Results view
     */
    @GetMapping("/results")
    public String viewResults(
            @RequestParam("searchId") Long searchId,
            Principal principal,
            Model model) {

        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) viewing research results for search ID: {}",
                currentUser.getUsername(), currentUser.getId(), searchId);

        try {
            // Get search history
            SearchHistory searchHistory = searchHistoryRepository.findById(searchId)
                    .orElseThrow(() -> new IllegalArgumentException("Search not found"));

            // Verify user owns this search
            if (!searchHistory.getUserId().equals(currentUser.getId())) {
                logger.warn("User {} attempted to access search {} owned by user {}",
                        currentUser.getId(), searchId, searchHistory.getUserId());
                model.addAttribute("errorMessage", "Access denied");
                return "error";
            }

            // Get scraped content for this search (ordered by relevance)
            List<ScrapedContent> results = scrapedContentRepository
                    .findBySearchIdOrderByRelevanceScoreDesc(searchId);

            // Parse and enrich results with structured data
            List<EnrichedResult> enrichedResults = results.stream()
                    .map(this::enrichResult)
                    .collect(Collectors.toList());

            // Get saved content IDs for this user
            List<SavedContent> userSavedContent = savedContentRepository.findByUserId(currentUser.getId());
            Set<Long> savedContentIds = userSavedContent.stream()
                    .map(SavedContent::getContentId)
                    .collect(Collectors.toSet());

            // Create a map to track which results are saved
            Map<Long, Boolean> savedStatusMap = new HashMap<>();
            for (ScrapedContent content : results) {
                savedStatusMap.put(content.getId(), savedContentIds.contains(content.getId()));
            }

            // Add data to model
            model.addAttribute("user", currentUser);
            model.addAttribute("username", currentUser.getUsername());
            model.addAttribute("searchHistory", searchHistory);
            model.addAttribute("topicName", searchHistory.getQueryText());
            model.addAttribute("results", enrichedResults);
            model.addAttribute("resultCount", enrichedResults.size());
            model.addAttribute("savedStatusMap", savedStatusMap);

            logger.info("Displaying {} research results for search ID: {}", enrichedResults.size(), searchId);
            return "research-results";

        } catch (IllegalArgumentException e) {
            logger.error("Search not found: {}", searchId);
            model.addAttribute("errorMessage", "Search not found");
            return "error";
        } catch (Exception e) {
            logger.error("Error loading research results: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Error loading results");
            return "error";
        }
    }

    /**
     * Enrich ScrapedContent with parsed structured data.
     */
    private EnrichedResult enrichResult(ScrapedContent content) {
        EnrichedResult result = new EnrichedResult();
        result.setId(content.getId());
        result.setCreatedAt(content.getCreatedAt());
        result.setRelevanceScoreInt(content.getRelevanceScoreInt() != null ? content.getRelevanceScoreInt() : 0);
        result.setScrapedSource(content.getScrapedSource());
        result.setContentText(content.getContentText());

        // Parse contentText to extract structured fields
        String contentText = content.getContentText();
        if (contentText != null) {
            // Extract platform
            String platform = extractField(contentText, "Platform:", "\n");
            result.setPlatform(platform);

            // Extract title
            String title = extractField(contentText, "Title:", "\n");
            result.setTitle(title != null ? title : (content.getScrapedSource() != null ? content.getScrapedSource().getTitle() : "Research Result"));

            // Extract introduction
            String introduction = extractField(contentText, "Introduction:\n", "\n\nKey Points:");
            result.setIntroduction(introduction);

            // Extract key points
            String keyPointsText = extractField(contentText, "Key Points:\n", "\n\nSource:");
            if (keyPointsText != null && !keyPointsText.trim().isEmpty()) {
                List<String> keyPoints = parseKeyPoints(keyPointsText);
                result.setKeyPoints(keyPoints);
            }

            // Extract source URL
            String sourceUrl = extractField(contentText, "Source:", null);
            result.setSourceUrl(sourceUrl != null ? sourceUrl.trim() : (content.getScrapedSource() != null ? content.getScrapedSource().getSourceUrl() : ""));
        }

        return result;
    }

    /**
     * Extract a field value from content text.
     */
    private String extractField(String text, String startMarker, String endMarker) {
        if (text == null) return null;

        int startIndex = text.indexOf(startMarker);
        if (startIndex == -1) return null;

        startIndex += startMarker.length();

        int endIndex;
        if (endMarker == null) {
            endIndex = text.length();
        } else {
            endIndex = text.indexOf(endMarker, startIndex);
            if (endIndex == -1) {
                endIndex = text.length();
            }
        }

        return text.substring(startIndex, endIndex).trim();
    }

    /**
     * Parse key points from text into a list.
     */
    private List<String> parseKeyPoints(String keyPointsText) {
        if (keyPointsText == null || keyPointsText.trim().isEmpty()) {
            return List.of();
        }

        // Split by bullet points (•) or newlines
        String[] points = keyPointsText.split("[•\n]");
        return java.util.Arrays.stream(points)
                .map(String::trim)
                .filter(s -> !s.isEmpty() && s.length() > 3)
                .collect(Collectors.toList());
    }

    /**
     * Inner class to hold enriched result data for the view.
     */
    public static class EnrichedResult {
        private Long id;
        private String title;
        private String platform;
        private Integer relevanceScoreInt;
        private String introduction;
        private List<String> keyPoints;
        private String sourceUrl;
        private LocalDateTime createdAt;
        private ScrapedSource scrapedSource;
        private String contentText;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }

        public Integer getRelevanceScoreInt() { return relevanceScoreInt; }
        public void setRelevanceScoreInt(Integer relevanceScoreInt) { this.relevanceScoreInt = relevanceScoreInt; }

        public String getIntroduction() { return introduction; }
        public void setIntroduction(String introduction) { this.introduction = introduction; }

        public List<String> getKeyPoints() { return keyPoints; }
        public void setKeyPoints(List<String> keyPoints) { this.keyPoints = keyPoints; }

        public String getSourceUrl() { return sourceUrl; }
        public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public ScrapedSource getScrapedSource() { return scrapedSource; }
        public void setScrapedSource(ScrapedSource scrapedSource) { this.scrapedSource = scrapedSource; }

        public String getContentText() { return contentText; }
        public void setContentText(String contentText) { this.contentText = contentText; }
    }
}
