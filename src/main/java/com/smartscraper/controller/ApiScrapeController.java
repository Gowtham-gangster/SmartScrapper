package com.smartscraper.controller;

import com.smartscraper.dto.ScrapeRequestDto;
import com.smartscraper.dto.ScrapeResponseDto;
import com.smartscraper.entity.ModuleType;
import com.smartscraper.entity.SearchHistory;
import com.smartscraper.entity.User;
import com.smartscraper.repository.SearchHistoryRepository;
import com.smartscraper.service.ScrapeResult;
import com.smartscraper.service.ScraperFactory;
import com.smartscraper.service.ScraperService;
import com.smartscraper.service.UserService;
import com.smartscraper.service.WebSearchService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@RestController
public class ApiScrapeController {

    private static final Logger logger = LoggerFactory.getLogger(ApiScrapeController.class);

    private final UserService userService;
    private final SearchHistoryRepository searchHistoryRepository;
    private final ScraperFactory scraperFactory;
    private final WebSearchService webSearchService;

    public ApiScrapeController(
            UserService userService,
            SearchHistoryRepository searchHistoryRepository,
            ScraperFactory scraperFactory,
            WebSearchService webSearchService) {
        this.userService = userService;
        this.searchHistoryRepository = searchHistoryRepository;
        this.scraperFactory = scraperFactory;
        this.webSearchService = webSearchService;
    }

    /**
     * POST /api/scrape
     * Body: { "module": "NEWS|ECOMMERCE|RESEARCH|SEARCH", "query": "..." }
     */
    @PostMapping("/api/scrape")
    @Transactional
    public ResponseEntity<ScrapeResponseDto> scrape(
            @Valid @RequestBody ScrapeRequestDto request,
            java.security.Principal principal) {

        User currentUser = userService.getCurrentUser(principal);

        String query = request.getQuery() == null ? "" : request.getQuery().trim();
        if (query.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be empty");
        }

        ModuleType moduleType = parseModuleType(request.getModule());
        if (moduleType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid module");
        }

        SearchHistory searchHistory = new SearchHistory(
                currentUser.getId(),
                moduleType,
                query
        );
        SearchHistory savedSearch = searchHistoryRepository.save(searchHistory);

        int resultsCount = 0;
        try {
            if (moduleType == ModuleType.SEARCH) {
                List<?> results = webSearchService.search(query);
                resultsCount = results.size();
                logger.info("REST scrape SEARCH completed searchHistoryId={} results={}", savedSearch.getId(), resultsCount);
                return ResponseEntity.ok(new ScrapeResponseDto(
                        savedSearch.getId(),
                        moduleType.name(),
                        query,
                        "COMPLETED",
                        resultsCount
                ));
            }

            ScraperService scraper = scraperFactory.getScraper(moduleType);
            ScrapeResult scrapeResult = scraper.scrape(savedSearch.getId() + "|" + query);

            Object payload = scrapeResult == null ? null : scrapeResult.payload();
            if (payload instanceof List<?> list) {
                resultsCount = list.size();
            }

            logger.info("REST scrape completed searchHistoryId={} module={} results={}",
                    savedSearch.getId(), moduleType.name(), resultsCount);

            return ResponseEntity.ok(new ScrapeResponseDto(
                    savedSearch.getId(),
                    moduleType.name(),
                    query,
                    "COMPLETED",
                    resultsCount
            ));

        } catch (Exception e) {
            logger.error("REST scrape failed searchHistoryId={} module={} query='{}': {}",
                    savedSearch.getId(), moduleType, query, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Scraping failed");
        }
    }

    private ModuleType parseModuleType(String module) {
        if (module == null || module.isBlank()) return null;
        String s = module.trim();
        String norm = s.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);

        // Accept enum names and display names.
        for (ModuleType mt : ModuleType.values()) {
            if (mt.name().equals(norm)) return mt;
            if (mt.getDisplayName().equalsIgnoreCase(s)) return mt;
        }

        // Accept "Normal" -> SEARCH
        if (s.equalsIgnoreCase("Normal") || s.equalsIgnoreCase("Search")) {
            return ModuleType.SEARCH;
        }

        return null;
    }
}

