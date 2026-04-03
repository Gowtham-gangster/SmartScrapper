package com.smartscraper.controller;

import com.smartscraper.entity.Product;
import com.smartscraper.entity.ModuleType;
import com.smartscraper.entity.SearchHistory;
import com.smartscraper.entity.User;
import com.smartscraper.repository.SearchHistoryRepository;
import com.smartscraper.repository.ProductRepository;
import com.smartscraper.service.ImprovedProductScraperService;
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
 * Controller for e-commerce product search module.
 * Searches products across multiple e-commerce sites.
 */
@Controller
@RequestMapping("/ecommerce")
public class EcommerceController {

    private static final Logger logger = LoggerFactory.getLogger(EcommerceController.class);

    private final UserService userService;
    private final ImprovedProductScraperService productScraperService;
    private final SearchHistoryRepository searchHistoryRepository;
    private final ProductRepository productRepository;
    private final ScraperFactory scraperFactory;

    @Autowired
    public EcommerceController(UserService userService,
                              ImprovedProductScraperService productScraperService,
                              SearchHistoryRepository searchHistoryRepository,
                              ProductRepository productRepository,
                              ScraperFactory scraperFactory) {
        this.userService = userService;
        this.productScraperService = productScraperService;
        this.searchHistoryRepository = searchHistoryRepository;
        this.productRepository = productRepository;
        this.scraperFactory = scraperFactory;
    }

    /**
     * Display e-commerce search page.
     * 
     * @param principal Current logged-in user
     * @param model Spring MVC model
     * @return ecommerce template
     */
    @GetMapping
    public String showEcommercePage(Principal principal, Model model) {
        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) accessed e-commerce page",
                currentUser.getUsername(), currentUser.getId());

        // Add user information
        model.addAttribute("user", currentUser);
        model.addAttribute("userId", currentUser.getId());
        model.addAttribute("username", currentUser.getUsername());

        // Add supported sites
        model.addAttribute("supportedSites", productScraperService.getSupportedSites());

        return "ecommerce";
    }

    /**
     * Search for products across e-commerce sites.
     * Extracts product name, price, and source, then sorts by price.
     * 
     * @param query Search query
     * @param principal Current logged-in user
     * @param model Spring MVC model
     * @param redirectAttributes For flash messages
     * @return ecommerce-results template or redirect
     */
    @PostMapping("/search")
    public String searchProducts(
            @RequestParam(value = "query", required = false) String query,
            Principal principal,
            Model model,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) searching products with query: {}",
                currentUser.getUsername(), currentUser.getId(), query);

        // Validate query
        if (query == null || query.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Please enter a product search query.");
            return "redirect:/ecommerce";
        }

        String trimmedQuery = query.trim();

        try {
            // Save search history
            SearchHistory searchHistory = new SearchHistory(
                    currentUser.getId(),
                    ModuleType.ECOMMERCE,
                    trimmedQuery
            );
            SearchHistory savedSearch = searchHistoryRepository.save(searchHistory);
            logger.info("Saved e-commerce search history ID: {}", savedSearch.getId());

            // Scrape + persist via module scraper
            logger.info("Scraping started userId={} module={} searchHistoryId={}",
                    currentUser.getId(), ModuleType.ECOMMERCE, savedSearch.getId());
            ScrapeResult result = scraperFactory
                    .getScraper(ModuleType.ECOMMERCE)
                    .scrape(savedSearch.getId() + "|" + trimmedQuery);
            @SuppressWarnings("unchecked")
            List<Product> productEntities = (List<Product>) result.payload();

            // Add data to model
            model.addAttribute("user", currentUser);
            model.addAttribute("userId", currentUser.getId());
            model.addAttribute("username", currentUser.getUsername());
            model.addAttribute("query", trimmedQuery);
            model.addAttribute("products", productEntities);
            model.addAttribute("resultCount", productEntities.size());
            model.addAttribute("searchHistory", savedSearch);

            logger.info("Scraping completed userId={} module={} searchHistoryId={} results={}: query='{}'",
                    currentUser.getId(), ModuleType.ECOMMERCE, savedSearch.getId(), productEntities.size(), trimmedQuery);

            return "ecommerce-results";

        } catch (Exception e) {
            logger.error("Scraping error userId={} module={} query='{}': {}",
                    currentUser.getId(), ModuleType.ECOMMERCE, trimmedQuery, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An error occurred while searching products. Please try again.");
            return "redirect:/ecommerce";
        }
    }

    /**
     * View product results from search history.
     * 
     * @param searchId Search history ID
     * @param principal Current logged-in user
     * @param model Spring MVC model
     * @param redirectAttributes For flash messages
     * @return ecommerce-results template or redirect
     */
    @GetMapping("/results")
    public String viewProductResults(
            @RequestParam(value = "searchId", required = false) Long searchId,
            Principal principal,
            Model model,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser(principal);

        if (searchId == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Invalid search ID.");
            return "redirect:/ecommerce";
        }

        // Fetch search history
        SearchHistory searchHistory = searchHistoryRepository.findById(searchId)
                .orElse(null);

        if (searchHistory == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Search history not found.");
            return "redirect:/ecommerce";
        }

        // Verify ownership
        if (!searchHistory.getUserId().equals(currentUser.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "You don't have permission to view this search.");
            return "redirect:/ecommerce";
        }

        logger.info("User {} (ID: {}) viewing product results for search ID: {}",
                currentUser.getUsername(), currentUser.getId(), searchId);

        try {
            // Load persisted products for this search
            List<Product> products = productRepository.findBySearchHistoryIdOrderByPriceAsc(searchId);

            // Add data to model
            model.addAttribute("user", currentUser);
            model.addAttribute("userId", currentUser.getId());
            model.addAttribute("username", currentUser.getUsername());
            model.addAttribute("query", searchHistory.getQueryText());
            model.addAttribute("products", products);
            model.addAttribute("resultCount", products.size());
            model.addAttribute("searchHistory", searchHistory);

            return "ecommerce-results";

        } catch (Exception e) {
            logger.error("Error fetching product results: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An error occurred while fetching products. Please try again.");
            return "redirect:/ecommerce";
        }
    }
}
