package com.smartscraper.service;

import com.smartscraper.entity.ModuleType;
import com.smartscraper.entity.ScheduledJob;
import com.smartscraper.entity.SearchHistory;
import com.smartscraper.entity.User;
import com.smartscraper.repository.NewsRepository;
import com.smartscraper.repository.ProductRepository;
import com.smartscraper.repository.ScheduledJobRepository;
import com.smartscraper.repository.SearchHistoryRepository;
import com.smartscraper.repository.UserRepository;
import com.smartscraper.service.ScrapeResult;
import com.smartscraper.service.ScraperFactory;
import com.smartscraper.service.ScraperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScheduledScrapingService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledScrapingService.class);

    private final ScraperFactory scraperFactory;
    private final SearchHistoryRepository searchHistoryRepository;
    private final ScheduledJobRepository scheduledJobRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NewsRepository newsRepository;
    private final ProductRepository productRepository;

    private final String schedulerUsername;
    private final String schedulerEmail;

    private final String newsQuery;
    private final String productsQuery;

    // Simple locks to avoid overlapping runs for the same module
    private final Object newsLock = new Object();
    private final Object productsLock = new Object();

    public ScheduledScrapingService(
            ScraperFactory scraperFactory,
            SearchHistoryRepository searchHistoryRepository,
            ScheduledJobRepository scheduledJobRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            NewsRepository newsRepository,
            ProductRepository productRepository,
            @Value("${app.scheduled.scheduler-username:scheduler}") String schedulerUsername,
            @Value("${app.scheduled.scheduler-email:scheduler@smartscraper.local}") String schedulerEmail,
            @Value("${app.scheduled.news.query:latest}") String newsQuery,
            @Value("${app.scheduled.products.query:phone}") String productsQuery) {
        this.scraperFactory = scraperFactory;
        this.searchHistoryRepository = searchHistoryRepository;
        this.scheduledJobRepository = scheduledJobRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.newsRepository = newsRepository;
        this.productRepository = productRepository;
        this.schedulerUsername = schedulerUsername;
        this.schedulerEmail = schedulerEmail;
        this.newsQuery = newsQuery;
        this.productsQuery = productsQuery;
    }

    /**
     * Scrape news daily at 9 AM.
     * Default cron: 0 0 9 * * * (Spring cron uses 6 fields by default)
     */
    @Scheduled(cron = "${app.scheduled.news.cron:0 0 9 * * *}")
    @Transactional
    public void scrapeNewsDaily() {
        synchronized (newsLock) {
            runScrapeJob(ModuleType.NEWS, newsQuery, "NEWS_DAILY");
        }
    }

    /**
     * Scrape products every 6 hours.
     * Default cron: 0 0 (every 6 hours) * * *
     */
    @Scheduled(cron = "${app.scheduled.products.cron:0 0 */6 * * *}")
    @Transactional
    public void scrapeProductsEvery6Hours() {
        synchronized (productsLock) {
            runScrapeJob(ModuleType.ECOMMERCE, productsQuery, "PRODUCTS_EVERY_6H");
        }
    }

    private void runScrapeJob(ModuleType moduleType, String query, String jobName) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            logger.warn("Skipping scheduled scrape {}: missing query (module={})", jobName, moduleType);
            ScheduledJob skipped = new ScheduledJob(moduleType, "", "SKIPPED");
            skipped.setFinishedAt(LocalDateTime.now());
            skipped.setResultCount(0);
            skipped.setErrorMessage("Missing scheduled query");
            scheduledJobRepository.save(skipped);
            return;
        }

        User schedulerUser = ensureSchedulerUser();

        ScheduledJob job = new ScheduledJob(moduleType, normalizedQuery, "RUNNING");
        job = scheduledJobRepository.save(job);

        try {
            logger.info("Starting scheduled scrape jobId={} jobName={} module={} query='{}'",
                    job.getId(), jobName, moduleType, normalizedQuery);

            // Create SearchHistory so module scrapers can persist results with a search_history_id.
            SearchHistory searchHistory = new SearchHistory(
                    schedulerUser.getId(),
                    moduleType,
                    normalizedQuery
            );
            SearchHistory savedSearch = searchHistoryRepository.save(searchHistory);

            ScraperService scraper = scraperFactory.getScraper(moduleType);
            ScrapeResult result = scraper.scrape(savedSearch.getId() + "|" + normalizedQuery);

            int count = 0;
            if (result != null && result.payload() instanceof List<?> list) {
                count = list.size();
            }

            // Best-effort: if payload parsing fails, fall back to repository size.
            if (count == 0) {
                if (moduleType == ModuleType.NEWS) {
                    count = newsRepository.findBySearchHistoryIdOrderByPublishedDateDesc(savedSearch.getId()).size();
                } else if (moduleType == ModuleType.ECOMMERCE) {
                    count = productRepository.findBySearchHistoryIdOrderByPriceAsc(savedSearch.getId()).size();
                }
            }

            job.setSearchHistoryId(savedSearch.getId());
            job.setResultCount(count);
            job.setStatus("SUCCESS");
            job.setFinishedAt(LocalDateTime.now());
            job.setErrorMessage(null);
            scheduledJobRepository.save(job);

            logger.info("Scheduled scrape SUCCESS jobId={} module={} query='{}' savedCount={}",
                    job.getId(), moduleType, normalizedQuery, count);

        } catch (Exception e) {
            logger.error("Scheduled scrape FAILED jobId={} module={} query='{}': {}",
                    job.getId(), moduleType, normalizedQuery, e.getMessage(), e);
            job.setStatus("FAILED");
            job.setFinishedAt(LocalDateTime.now());
            job.setErrorMessage(truncate(e.getMessage(), 2000));
            scheduledJobRepository.save(job);
        }
    }

    private User ensureSchedulerUser() {
        return userRepository.findByUsername(schedulerUsername)
                .orElseGet(() -> {
                    String rawPassword = "ChangeMe-" + schedulerUsername;
                    String passwordHash = passwordEncoder.encode(rawPassword);
                    logger.warn("Creating scheduler user '{}' for scheduled scraping", schedulerUsername);
                    User user = new User(schedulerUsername, schedulerEmail, passwordHash);
                    return userRepository.save(user);
                });
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() <= maxLen) return t;
        return t.substring(0, maxLen);
    }
}

