package com.smartscraper.controller;

import com.smartscraper.entity.ModuleType;
import com.smartscraper.entity.News;
import com.smartscraper.entity.SearchHistory;
import com.smartscraper.entity.User;
import com.smartscraper.repository.NewsRepository;
import com.smartscraper.repository.SearchHistoryRepository;
import com.smartscraper.service.RssFeedService;
import com.smartscraper.service.UserService;
import com.smartscraper.service.ScrapeResult;
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
import java.util.List;

/**
 * Controller for news scraping module.
 * Fetches news from RSS feeds and displays headlines with summaries.
 */
@Controller
@RequestMapping("/news")
public class NewsController {

    private static final Logger logger = LoggerFactory.getLogger(NewsController.class);

    private final UserService userService;
    private final RssFeedService rssFeedService;
    private final NewsRepository newsRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final ScraperFactory scraperFactory;

    @Autowired
    public NewsController(UserService userService,
                           RssFeedService rssFeedService,
                           NewsRepository newsRepository,
                           SearchHistoryRepository searchHistoryRepository,
                           ScraperFactory scraperFactory) {
        this.userService = userService;
        this.rssFeedService = rssFeedService;
        this.newsRepository = newsRepository;
        this.searchHistoryRepository = searchHistoryRepository;
        this.scraperFactory = scraperFactory;
    }

    /**
     * Display news search page.
     * 
     * @param principal Current logged-in user
     * @param model Spring MVC model
     * @return news template
     */
    @GetMapping
    public String showNewsPage(Principal principal, Model model) {
        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) accessed news page",
                currentUser.getUsername(), currentUser.getId());

        // Add user information
        model.addAttribute("user", currentUser);
        model.addAttribute("userId", currentUser.getId());
        model.addAttribute("username", currentUser.getUsername());

        // Add available RSS feeds
        model.addAttribute("availableFeeds", rssFeedService.getAvailableFeeds());

        return "news";
    }

    /**
     * Search for news based on query.
     * Fetches from RSS feeds and saves search history.
     * 
     * @param query Search query
     * @param principal Current logged-in user
     * @param model Spring MVC model
     * @param redirectAttributes For flash messages
     * @return news-results template or redirect
     */
    @PostMapping("/search")
    public String searchNews(
            @RequestParam(value = "query", required = false) String query,
            Principal principal,
            Model model,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) searching news with query: {}",
                currentUser.getUsername(), currentUser.getId(), query);

        // Validate query
        if (query == null || query.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Please enter a search query.");
            return "redirect:/news";
        }

        String trimmedQuery = query.trim();

        try {
            // Save search history
            SearchHistory searchHistory = new SearchHistory(
                    currentUser.getId(),
                    ModuleType.NEWS,
                    trimmedQuery
            );
            SearchHistory savedSearch = searchHistoryRepository.save(searchHistory);
            logger.info("Saved news search history ID: {}", savedSearch.getId());

            // Scrape + persist via module scraper
            logger.info("Scraping started userId={} module={} searchHistoryId={}",
                    currentUser.getId(), ModuleType.NEWS, savedSearch.getId());
            ScrapeResult result = scraperFactory
                    .getScraper(ModuleType.NEWS)
                    .scrape(savedSearch.getId() + "|" + trimmedQuery);
            @SuppressWarnings("unchecked")
            List<News> newsEntities = (List<News>) result.payload();

            // Add data to model
            model.addAttribute("user", currentUser);
            model.addAttribute("userId", currentUser.getId());
            model.addAttribute("username", currentUser.getUsername());
            model.addAttribute("query", trimmedQuery);
            model.addAttribute("newsItems", newsEntities);
            model.addAttribute("resultCount", newsEntities.size());
            model.addAttribute("searchHistory", searchHistory);

            logger.info("Scraping completed userId={} module={} searchHistoryId={} results={}: query='{}'",
                    currentUser.getId(), ModuleType.NEWS, savedSearch.getId(), newsEntities.size(), trimmedQuery);

            return "news-results";

        } catch (Exception e) {
            logger.error("Scraping error userId={} module={} query='{}': {}",
                    currentUser.getId(), ModuleType.NEWS, trimmedQuery, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An error occurred while fetching news. Please try again.");
            return "redirect:/news";
        }
    }

    /**
     * View news results from search history.
     * 
     * @param searchId Search history ID
     * @param principal Current logged-in user
     * @param model Spring MVC model
     * @param redirectAttributes For flash messages
     * @return news-results template or redirect
     */
    @GetMapping("/results")
    public String viewNewsResults(
            @RequestParam(value = "searchId", required = false) Long searchId,
            Principal principal,
            Model model,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser(principal);

        if (searchId == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Invalid search ID.");
            return "redirect:/news";
        }

        // Fetch search history
        SearchHistory searchHistory = searchHistoryRepository.findById(searchId)
                .orElse(null);

        if (searchHistory == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Search history not found.");
            return "redirect:/news";
        }

        // Verify ownership
        if (!searchHistory.getUserId().equals(currentUser.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "You don't have permission to view this search.");
            return "redirect:/news";
        }

        logger.info("User {} (ID: {}) viewing news results for search ID: {}",
                currentUser.getUsername(), currentUser.getId(), searchId);

        try {
            // Load persisted news items for this search
            List<News> newsItems = newsRepository.findBySearchHistoryIdOrderByPublishedDateDesc(searchId);

            // Add data to model
            model.addAttribute("user", currentUser);
            model.addAttribute("userId", currentUser.getId());
            model.addAttribute("username", currentUser.getUsername());
            model.addAttribute("query", searchHistory.getQueryText());
            model.addAttribute("newsItems", newsItems);
            model.addAttribute("resultCount", newsItems.size());
            model.addAttribute("searchHistory", searchHistory);

            return "news-results";

        } catch (Exception e) {
            logger.error("Error fetching news results: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An error occurred while fetching news. Please try again.");
            return "redirect:/news";
        }
    }
}
