package com.smartscraper.controller;

import com.smartscraper.entity.ScrapedContent;
import com.smartscraper.entity.SearchHistory;
import com.smartscraper.entity.User;
import com.smartscraper.repository.ExportHistoryRepository;
import com.smartscraper.repository.SearchHistoryRepository;
import com.smartscraper.repository.SavedContentRepository;
import com.smartscraper.repository.ScrapedContentRepository;
import com.smartscraper.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.data.domain.PageRequest;

import java.security.Principal;
import java.util.List;

@Controller
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    private final UserService userService;
    private final SearchHistoryRepository searchHistoryRepository;
    private final SavedContentRepository savedContentRepository;
    private final ExportHistoryRepository exportHistoryRepository;
    private final ScrapedContentRepository scrapedContentRepository;

    @Autowired
    public HomeController(
            UserService userService,
            SearchHistoryRepository searchHistoryRepository,
            SavedContentRepository savedContentRepository,
            ExportHistoryRepository exportHistoryRepository,
            ScrapedContentRepository scrapedContentRepository) {
        this.userService = userService;
        this.searchHistoryRepository = searchHistoryRepository;
        this.savedContentRepository = savedContentRepository;
        this.exportHistoryRepository = exportHistoryRepository;
        this.scrapedContentRepository = scrapedContentRepository;
    }

    @GetMapping("/")
    public String home(Authentication authentication) {
        // Redirect logged-in users to dashboard
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/dashboard";
        }
        return "index";
    }

    @GetMapping("/login")
    public String login(Authentication authentication) {
        // Redirect logged-in users to dashboard
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/dashboard";
        }
        return "login";
    }

    @GetMapping("/signup")
    public String signup(Authentication authentication) {
        // Redirect logged-in users to dashboard
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/dashboard";
        }
        return "signup";
    }

    @GetMapping("/dashboard")
    public String dashboard(Principal principal, Model model) {
        // Get current logged-in user
        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) accessed dashboard", currentUser.getUsername(), currentUser.getId());

        Long userId = currentUser.getId();

        // Totals
        long totalSearches = searchHistoryRepository.countByUserId(userId);
        long totalSavedContent = savedContentRepository.countByUserId(userId);
        long totalExports = exportHistoryRepository
                .findByUserIdOrderByExportedAtDesc(userId, PageRequest.of(0, 1))
                .getTotalElements();

        // Latest lists
        List<SearchHistory> latestSearches = searchHistoryRepository.findTop10ByUserIdOrderBySearchedAtDesc(userId);
        if (latestSearches.size() > 5) {
            latestSearches = latestSearches.subList(0, 5);
        }

        List<ScrapedContent> latestScrapedData =
                scrapedContentRepository.findLatestByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 5));

        // Add user information to model
        model.addAttribute("user", currentUser);
        model.addAttribute("userId", currentUser.getId());
        model.addAttribute("username", currentUser.getUsername());

        model.addAttribute("totalSearches", totalSearches);
        model.addAttribute("totalSavedContent", totalSavedContent);
        model.addAttribute("totalExports", totalExports);
        model.addAttribute("latestSearches", latestSearches);
        model.addAttribute("latestScrapedData", latestScrapedData);

        return "dashboard";
    }
}
