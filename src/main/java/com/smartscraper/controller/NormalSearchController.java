package com.smartscraper.controller;

import com.smartscraper.dto.SearchResultDto;
import com.smartscraper.entity.ModuleType;
import com.smartscraper.entity.SearchHistory;
import com.smartscraper.entity.User;
import com.smartscraper.repository.SearchHistoryRepository;
import com.smartscraper.service.UserService;
import com.smartscraper.service.WebSearchService;
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
import java.util.List;

/**
 * Controller for normal search module.
 * Performs keyword-based web search with minimal filtering.
 */
@Controller
@RequestMapping("/search")
public class NormalSearchController {

    private static final Logger logger = LoggerFactory.getLogger(NormalSearchController.class);

    private final UserService userService;
    private final WebSearchService webSearchService;
    private final SearchHistoryRepository searchHistoryRepository;

    @Autowired
    public NormalSearchController(UserService userService,
                                 WebSearchService webSearchService,
                                 SearchHistoryRepository searchHistoryRepository) {
        this.userService = userService;
        this.webSearchService = webSearchService;
        this.searchHistoryRepository = searchHistoryRepository;
    }

    /**
     * Display normal search page.
     * 
     * @param principal Current logged-in user
     * @param model Spring MVC model
     * @return search template
     */
    @GetMapping
    public String showSearchPage(Principal principal, Model model) {
        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) accessed normal search page",
                currentUser.getUsername(), currentUser.getId());

        // Add user information
        model.addAttribute("user", currentUser);
        model.addAttribute("userId", currentUser.getId());
        model.addAttribute("username", currentUser.getUsername());

        return "search";
    }

    /**
     * Perform web search based on keywords.
     * Extracts snippets and links with minimal filtering.
     * 
     * @param query Search query
     * @param principal Current logged-in user
     * @param model Spring MVC model
     * @param redirectAttributes For flash messages
     * @return search-results template or redirect
     */
    @PostMapping("/execute")
    public String executeSearch(
            @RequestParam(value = "query", required = false) String query,
            Principal principal,
            Model model,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) executing search with query: {}",
                currentUser.getUsername(), currentUser.getId(), query);

        // Validate query
        if (query == null || query.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Please enter a search query.");
            return "redirect:/search";
        }

        String trimmedQuery = query.trim();

        try {
            // Save search history
            SearchHistory searchHistory = new SearchHistory(
                    currentUser.getId(),
                    ModuleType.SEARCH,
                    trimmedQuery
            );
            SearchHistory savedSearch = searchHistoryRepository.save(searchHistory);
            logger.info("Saved normal search history ID: {}", savedSearch.getId());

            // Perform web search
            logger.info("Scraping started userId={} module={} searchHistoryId={} query='{}'",
                    currentUser.getId(), ModuleType.SEARCH, savedSearch.getId(), trimmedQuery);
            List<SearchResultDto> searchResults = webSearchService.search(trimmedQuery);

            // Add data to model
            model.addAttribute("user", currentUser);
            model.addAttribute("userId", currentUser.getId());
            model.addAttribute("username", currentUser.getUsername());
            model.addAttribute("query", trimmedQuery);
            model.addAttribute("searchResults", searchResults);
            model.addAttribute("resultCount", searchResults.size());
            model.addAttribute("searchHistory", savedSearch);

            logger.info("Scraping completed userId={} module={} searchHistoryId={} results={}: query='{}'",
                    currentUser.getId(), ModuleType.SEARCH, savedSearch.getId(), searchResults.size(), trimmedQuery);

            return "search-results";

        } catch (Exception e) {
            logger.error("Scraping error userId={} module={} query='{}': {}",
                    currentUser.getId(), ModuleType.SEARCH, trimmedQuery, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An error occurred while searching. Please try again.");
            return "redirect:/search";
        }
    }

    /**
     * View search results from search history.
     * 
     * @param searchId Search history ID
     * @param principal Current logged-in user
     * @param model Spring MVC model
     * @param redirectAttributes For flash messages
     * @return search-results template or redirect
     */
    @GetMapping("/results")
    public String viewSearchResults(
            @RequestParam(value = "searchId", required = false) Long searchId,
            Principal principal,
            Model model,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser(principal);

        if (searchId == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Invalid search ID.");
            return "redirect:/search";
        }

        // Fetch search history
        SearchHistory searchHistory = searchHistoryRepository.findById(searchId)
                .orElse(null);

        if (searchHistory == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Search history not found.");
            return "redirect:/search";
        }

        // Verify ownership
        if (!searchHistory.getUserId().equals(currentUser.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "You don't have permission to view this search.");
            return "redirect:/search";
        }

        logger.info("User {} (ID: {}) viewing search results for search ID: {}",
                currentUser.getUsername(), currentUser.getId(), searchId);

        try {
            // Re-execute search with the same query
            List<SearchResultDto> searchResults = webSearchService.search(
                    searchHistory.getQueryText());

            // Add data to model
            model.addAttribute("user", currentUser);
            model.addAttribute("userId", currentUser.getId());
            model.addAttribute("username", currentUser.getUsername());
            model.addAttribute("query", searchHistory.getQueryText());
            model.addAttribute("searchResults", searchResults);
            model.addAttribute("resultCount", searchResults.size());
            model.addAttribute("searchHistory", searchHistory);

            return "search-results";

        } catch (Exception e) {
            logger.error("Error fetching search results: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An error occurred while fetching results. Please try again.");
            return "redirect:/search";
        }
    }
}
