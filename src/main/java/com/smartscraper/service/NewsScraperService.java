package com.smartscraper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscraper.entity.News;
import com.smartscraper.exception.ScrapingException;
import com.smartscraper.repository.NewsRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Real-time news scraper service.
 * Scrapes news from actual news websites with platform-specific extraction.
 * Expects query format: {@code <searchHistoryId>|<keyword>}
 */
@Service
public class NewsScraperService implements ScraperService {

    private static final Logger logger = LoggerFactory.getLogger(NewsScraperService.class);
    private static final int TIMEOUT_MS = 10000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final String PLACEHOLDER_IMAGE = "/images/news-placeholder.jpg";
    private static final String PUPPETEER_API_BASE_URL = System.getenv().getOrDefault("PUPPETEER_API_BASE_URL", "http://localhost:3001") + "/scrape";
    
    // Multithreading configuration
    private static final int THREAD_POOL_SIZE = 5;
    private static final int SCRAPING_TIMEOUT_SECONDS = 10;

    private final NewsRepository newsRepository;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;

    public NewsScraperService(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    @Override
    public ScrapeResult scrape(String query) {
        try {
            ParsedQuery parsed = ParsedQuery.parse(query);
            logger.info("Real-time news scraping started (searchHistoryId={}, keyword='{}')",
                    parsed.searchHistoryId, parsed.keyword);
            
            List<News> savedNews = scrapeFromAllSources(parsed.searchHistoryId, parsed.keyword);
            
            return new ScrapeResult(savedNews);
        } catch (Exception e) {
            throw new ScrapingException("Real-time news scraping failed", e);
        }
    }

    /**
     * Main news scraping method - scrapes news from all platforms.
     * 
     * Steps:
     * 1. Call all platform scraping methods in parallel
     * 2. Combine all news results
     * 3. Remove duplicates
     * 4. Filter by keyword relevance
     * 5. Calculate relevance score
     * 6. Sort by score and latest date
     * 7. Limit results to top 50 news
     * 8. Return final list
     * 
     * @param keyword The search keyword
     * @return List of top 50 relevant news articles
     */
    public List<News> scrapeNews(String keyword) {
        logger.info("Starting news scraping for keyword: '{}'", keyword);
        long startTime = System.currentTimeMillis();
        
        // Step 1: Call all platform scraping methods in parallel
        List<Callable<List<News>>> scrapingTasks = new ArrayList<>();
        
        // Global sources
        scrapingTasks.add(() -> scrapeGoogleNews(keyword));
        scrapingTasks.add(() -> scrapeBBCNews(keyword));
        scrapingTasks.add(() -> scrapeReutersNews(keyword));
        
        // Indian sources
        scrapingTasks.add(() -> scrapeTOINews(keyword));
        scrapingTasks.add(() -> scrapeHinduNews(keyword));
        scrapingTasks.add(() -> scrapeNDTVNews(keyword));
        
        // Telugu sources (Puppeteer API)
        scrapingTasks.add(() -> scrapeEenaduNews(keyword));
        scrapingTasks.add(() -> scrapeSakshiNews(keyword));
        
        // Step 2: Combine all news results
        List<News> allNews = new ArrayList<>();
        List<Future<List<News>>> futures = new ArrayList<>();
        
        try {
            // Submit all tasks
            for (Callable<List<News>> task : scrapingTasks) {
                Future<List<News>> future = executorService.submit(task);
                futures.add(future);
            }
            
            // Collect results with timeout
            int successCount = 0;
            int failureCount = 0;
            
            for (Future<List<News>> future : futures) {
                try {
                    List<News> newsFromSource = future.get(SCRAPING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (newsFromSource != null && !newsFromSource.isEmpty()) {
                        allNews.addAll(newsFromSource);
                        successCount++;
                    }
                } catch (TimeoutException e) {
                    logger.warn("Scraping task timed out after {} seconds", SCRAPING_TIMEOUT_SECONDS);
                    future.cancel(true);
                    failureCount++;
                } catch (ExecutionException e) {
                    logger.warn("Scraping task failed: {}", e.getCause().getMessage());
                    failureCount++;
                } catch (InterruptedException e) {
                    logger.warn("Scraping task interrupted: {}", e.getMessage());
                    Thread.currentThread().interrupt();
                    failureCount++;
                }
            }
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            logger.info("Parallel scraping completed in {}ms - Success: {}, Failed: {}, Total articles: {}", 
                       elapsedTime, successCount, failureCount, allNews.size());
            
        } catch (Exception e) {
            logger.error("Error during parallel scraping: {}", e.getMessage(), e);
        }

        // Step 3: Remove duplicates
        int beforeDedup = allNews.size();
        List<News> uniqueNews = removeDuplicateNews(allNews);
        int afterDedup = uniqueNews.size();
        logger.info("After deduplication: {} unique articles (removed {} duplicates)", afterDedup, beforeDedup - afterDedup);

        // Step 4: Filter by keyword relevance
        List<News> filteredNews = filterNewsByKeyword(uniqueNews, keyword);
        logger.info("After keyword filtering: {} relevant articles", filteredNews.size());

        // Step 5: Calculate relevance score
        for (News news : filteredNews) {
            int score = calculateRelevanceScore(news, keyword);
            news.setRelevanceScore(score);
        }
        logger.info("Calculated relevance scores for {} articles", filteredNews.size());

        // Step 6: Sort by score (descending) and then by latest date
        filteredNews.sort((n1, n2) -> {
            // First compare by score (higher score first)
            int scoreCompare = Integer.compare(
                n2.getRelevanceScore() != null ? n2.getRelevanceScore() : 0,
                n1.getRelevanceScore() != null ? n1.getRelevanceScore() : 0
            );
            
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            
            // If scores are equal, compare by date (latest first)
            if (n1.getPublishedDate() != null && n2.getPublishedDate() != null) {
                return n2.getPublishedDate().compareTo(n1.getPublishedDate());
            }
            
            return 0;
        });
        logger.info("Sorted articles by relevance score and date");

        // Step 7: Limit results to top 50 news
        List<News> top50News = filteredNews.stream()
                .limit(50)
                .collect(Collectors.toList());
        
        long totalTime = System.currentTimeMillis() - startTime;
        logger.info("News scraping completed in {}ms. Returning top {} articles", totalTime, top50News.size());

        // Step 8: Return final list
        return top50News;
    }

    @Transactional
    public List<News> scrapeFromAllSources(Long searchHistoryId, String keyword) {
        logger.info("Starting news scraping with searchHistoryId: {}, keyword: '{}'", searchHistoryId, keyword);
        
        // Use the main scrapeNews method to get top 50 articles
        List<News> top50News = scrapeNews(keyword);
        
        // Set searchHistoryId for all news items
        for (News news : top50News) {
            news.setSearchHistoryId(searchHistoryId);
        }

        // Save to database
        List<News> savedNews = new ArrayList<>();
        for (News news : top50News) {
            try {
                News saved = newsRepository.save(news);
                savedNews.add(saved);
            } catch (Exception e) {
                logger.warn("Failed to save news from {}: {}", news.getSource(), e.getMessage());
            }
        }

        logger.info("Real-time news scraping completed. Saved: {} articles", savedNews.size());
        return savedNews;
    }

    // ==================== FILTERING AND DEDUPLICATION ====================

    private List<News> filterNewsByKeyword(List<News> newsList, String keyword) {
        String keywordLower = keyword.toLowerCase();
        String[] keywords = keywordLower.split("\\s+");
        
        return newsList.stream()
                .filter(news -> isValidNews(news, keywords))
                .collect(Collectors.toList());
    }

    private boolean isValidNews(News news, String[] keywords) {
        // Check if keyword is in title or summary
        boolean hasKeyword = containsKeyword(news.getTitle(), news.getSummary(), keywords);
        if (!hasKeyword) {
            return false;
        }

        // Check if URL is valid
        if (news.getLink() == null || news.getLink().isEmpty()) {
            return false;
        }
        if (!news.getLink().startsWith("http://") && !news.getLink().startsWith("https://")) {
            return false;
        }

        // Check if title is not empty
        if (news.getTitle() == null || news.getTitle().trim().isEmpty()) {
            return false;
        }

        return true;
    }

    private boolean containsKeyword(String title, String summary, String[] keywords) {
        String titleLower = title != null ? title.toLowerCase() : "";
        String summaryLower = summary != null ? summary.toLowerCase() : "";
        
        for (String kw : keywords) {
            if (kw.length() > 2 && (titleLower.contains(kw) || summaryLower.contains(kw))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove duplicate news articles.
     * If multiple platforms return same news title:
     * - Keep only one article
     * - Prefer article with image (not placeholder)
     * - Prefer latest published date
     * 
     * @param newsList List of news articles
     * @return Deduplicated list
     */
    private List<News> removeDuplicateNews(List<News> newsList) {
        Map<String, News> uniqueMap = new LinkedHashMap<>();
        
        for (News news : newsList) {
            // Use normalized title as key for deduplication
            String key = normalizeTitle(news.getTitle());
            
            if (!uniqueMap.containsKey(key)) {
                // First occurrence - add it
                uniqueMap.put(key, news);
            } else {
                // Duplicate found - decide which one to keep
                News existing = uniqueMap.get(key);
                News better = chooseBetterArticle(existing, news);
                uniqueMap.put(key, better);
            }
        }
        
        return new ArrayList<>(uniqueMap.values());
    }

    /**
     * Choose the better article between two duplicates.
     * Priority:
     * 1. Article with real image (not placeholder)
     * 2. Article with latest published date
     * 3. First article (if both equal)
     * 
     * @param article1 First article
     * @param article2 Second article
     * @return The better article
     */
    private News chooseBetterArticle(News article1, News article2) {
        // Check if either has a real image (not placeholder)
        boolean article1HasImage = hasRealImage(article1);
        boolean article2HasImage = hasRealImage(article2);
        
        // Prefer article with real image
        if (article1HasImage && !article2HasImage) {
            return article1;
        }
        if (article2HasImage && !article1HasImage) {
            return article2;
        }
        
        // Both have images or both don't - compare dates
        if (article1.getPublishedDate() != null && article2.getPublishedDate() != null) {
            // Prefer latest (newer) date
            if (article2.getPublishedDate().isAfter(article1.getPublishedDate())) {
                return article2;
            } else {
                return article1;
            }
        }
        
        // If one has date and other doesn't, prefer the one with date
        if (article1.getPublishedDate() != null && article2.getPublishedDate() == null) {
            return article1;
        }
        if (article2.getPublishedDate() != null && article1.getPublishedDate() == null) {
            return article2;
        }
        
        // Both equal - keep first one
        return article1;
    }

    /**
     * Check if article has a real image (not placeholder)
     */
    private boolean hasRealImage(News news) {
        String imageUrl = news.getImageUrl();
        return imageUrl != null 
                && !imageUrl.isEmpty() 
                && !imageUrl.equals(PLACEHOLDER_IMAGE)
                && !imageUrl.contains("placeholder");
    }

    /**
     * Calculate relevance score for news article.
     * 
     * Scoring rules:
     * - Keyword in title → +40
     * - Keyword in summary → +30
     * - Keyword frequency → +10 per occurrence (max 30)
     * - Latest news (today) → +20
     * 
     * @param news The news article
     * @param keyword The search keyword
     * @return Relevance score (0-120)
     */
    private int calculateRelevanceScore(News news, String keyword) {
        int score = 0;
        
        String title = news.getTitle() != null ? news.getTitle().toLowerCase() : "";
        String summary = news.getSummary() != null ? news.getSummary().toLowerCase() : "";
        String[] keywords = keyword.toLowerCase().split("\\s+");
        
        // Check keyword in title (+40)
        boolean keywordInTitle = false;
        for (String kw : keywords) {
            if (kw.length() > 2 && title.contains(kw)) {
                keywordInTitle = true;
                break;
            }
        }
        if (keywordInTitle) {
            score += 40;
            logger.debug("Article '{}' - Keyword in title: +40", news.getTitle());
        }
        
        // Check keyword in summary (+30)
        boolean keywordInSummary = false;
        for (String kw : keywords) {
            if (kw.length() > 2 && summary.contains(kw)) {
                keywordInSummary = true;
                break;
            }
        }
        if (keywordInSummary) {
            score += 30;
            logger.debug("Article '{}' - Keyword in summary: +30", news.getTitle());
        }
        
        // Calculate keyword frequency (+10 per occurrence, max 30)
        int frequency = 0;
        String combinedText = title + " " + summary;
        for (String kw : keywords) {
            if (kw.length() > 2) {
                int count = countOccurrences(combinedText, kw);
                frequency += count;
            }
        }
        int frequencyScore = Math.min(frequency * 10, 30);
        score += frequencyScore;
        if (frequencyScore > 0) {
            logger.debug("Article '{}' - Keyword frequency ({}): +{}", news.getTitle(), frequency, frequencyScore);
        }
        
        // Check if latest news (today) (+20)
        if (news.getPublishedDate() != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime articleDate = news.getPublishedDate();
            
            // Check if article is from today
            if (articleDate.toLocalDate().equals(now.toLocalDate())) {
                score += 20;
                logger.debug("Article '{}' - Published today: +20", news.getTitle());
            }
        }
        
        logger.debug("Article '{}' - Total relevance score: {}", news.getTitle(), score);
        return score;
    }

    /**
     * Count occurrences of keyword in text
     */
    private int countOccurrences(String text, String keyword) {
        if (text == null || keyword == null || keyword.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        int index = 0;
        
        while ((index = text.indexOf(keyword, index)) != -1) {
            count++;
            index += keyword.length();
        }
        
        return count;
    }

    private String normalizeTitle(String title) {
        if (title == null) return "";
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // ==================== IMAGE EXTRACTION ====================

    /**
     * Extract image URL from article with 3-tier fallback.
     * Guarantees a non-null, non-empty image URL.
     * 
     * Priority:
     * 1. <img> tag inside article (tries abs:src, src, data-src, data-lazy-src)
     * 2. <meta property="og:image"> from document
     * 3. Placeholder image
     * 
     * @param article The article element
     * @param doc The full document
     * @return Non-empty image URL (never null or empty)
     */
    private String extractImageUrl(Element article, Document doc) {
        String imageUrl = null;
        
        // 1. Try to extract from <img> tag inside article
        Element img = article.select("img").first();
        if (img != null) {
            // Try absolute src first
            imageUrl = img.attr("abs:src");
            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                imageUrl = img.attr("src");
            }
            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                imageUrl = img.attr("data-src");
            }
            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                imageUrl = img.attr("data-lazy-src");
            }
            // Clean up the URL
            if (imageUrl != null) {
                imageUrl = imageUrl.trim();
            }
        }
        
        // 2. If not found, try meta tag og:image
        if (imageUrl == null || imageUrl.isEmpty()) {
            Element metaOg = doc.select("meta[property=og:image]").first();
            if (metaOg != null) {
                imageUrl = metaOg.attr("content");
                if (imageUrl != null) {
                    imageUrl = imageUrl.trim();
                }
            }
        }
        
        // 3. Use placeholder if still not found or empty
        if (imageUrl == null || imageUrl.isEmpty()) {
            imageUrl = PLACEHOLDER_IMAGE;
        }
        
        // Final safety check - ensure we never return null or empty
        return (imageUrl != null && !imageUrl.isEmpty()) ? imageUrl : PLACEHOLDER_IMAGE;
    }

    // ==================== PLATFORM-SPECIFIC SCRAPERS ====================

    /**
     * Scrape news from Google News
     */
    private List<News> scrapeGoogleNews(String keyword) {
        List<News> newsList = new ArrayList<>();
        try {
            String url = "https://news.google.com/search?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            logger.debug("Scraping Google News: {}", url);
            
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements articleElements = doc.select("article");
            for (Element article : articleElements) {
                String title = article.select("h3, h4, a").first() != null 
                    ? article.select("h3, h4, a").first().text() 
                    : "";
                String link = article.select("a").attr("abs:href");
                String imageUrl = extractImageUrl(article, doc);
                
                // Validate title length (max 200 characters for individual articles)
                if (title.length() > 200) {
                    logger.debug("Skipping article with too long title ({}chars)", title.length());
                    continue;
                }
                
                if (!title.isEmpty() && !link.isEmpty()) {
                    // Extract summary from article page
                    String summary = extractSummaryFromArticle(link, keyword);
                    
                    // Skip if summary doesn't contain keyword
                    if (summary.isEmpty()) {
                        logger.debug("Skipping article (no keyword in summary): {}", title);
                        continue;
                    }
                    
                    News news = new News();
                    news.setTitle(title);
                    news.setSummary(summary);
                    news.setLink(link);
                    // Ensure imageUrl is never null or empty
                    news.setImageUrl(imageUrl != null && !imageUrl.isEmpty() ? imageUrl : PLACEHOLDER_IMAGE);
                    news.setSource("Google News");
                    news.setPublishedDate(LocalDateTime.now());
                    news.setLanguage("English");
                    news.setCountry("Global");
                    newsList.add(news);
                    
                    if (newsList.size() >= 10) break;
                }
            }
            logger.info("Scraped {} articles from Google News", newsList.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape Google News: {}", e.getMessage());
        }
        return newsList;
    }

    /**
     * Scrape news from Times of India
     */
    private List<News> scrapeTOINews(String keyword) {
        List<News> newsList = new ArrayList<>();
        try {
            String url = "https://timesofindia.indiatimes.com/topic/" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            logger.debug("Scraping Times of India: {}", url);
            
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements articleElements = doc.select(".uwU81, article, .content");
            for (Element article : articleElements) {
                String title = article.select("h2, h3, .title").text();
                String link = article.select("a").attr("abs:href");
                String imageUrl = extractImageUrl(article, doc);
                String dateStr = article.select("time, .date, .time").text();
                
                if (!title.isEmpty() && !link.isEmpty()) {
                    // Extract summary from article page
                    String summary = extractSummaryFromArticle(link, keyword);
                    
                    // Skip if summary doesn't contain keyword
                    if (summary.isEmpty()) {
                        logger.debug("Skipping article (no keyword in summary): {}", title);
                        continue;
                    }
                    
                    News news = new News();
                    news.setTitle(title);
                    news.setSummary(summary);
                    news.setLink(link);
                    // Ensure imageUrl is never null or empty
                    news.setImageUrl(imageUrl != null && !imageUrl.isEmpty() ? imageUrl : PLACEHOLDER_IMAGE);
                    news.setSource("Times of India");
                    news.setPublishedDate(parseDate(dateStr));
                    news.setLanguage("English");
                    news.setCountry("India");
                    newsList.add(news);
                    
                    if (newsList.size() >= 10) break;
                }
            }
            logger.info("Scraped {} articles from Times of India", newsList.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape Times of India: {}", e.getMessage());
        }
        return newsList;
    }

    /**
     * Scrape news from The Hindu
     */
    private List<News> scrapeHinduNews(String keyword) {
        List<News> newsList = new ArrayList<>();
        try {
            String url = "https://www.thehindu.com/search/?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            logger.debug("Scraping The Hindu: {}", url);
            
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements articleElements = doc.select(".story-card, article");
            for (Element article : articleElements) {
                String title = article.select("h3, h2, .title").text();
                String link = article.select("a").attr("abs:href");
                String imageUrl = extractImageUrl(article, doc);
                String dateStr = article.select("time, .date").text();
                
                if (!title.isEmpty() && !link.isEmpty()) {
                    // Extract summary from article page
                    String summary = extractSummaryFromArticle(link, keyword);
                    
                    // Skip if summary doesn't contain keyword
                    if (summary.isEmpty()) {
                        logger.debug("Skipping article (no keyword in summary): {}", title);
                        continue;
                    }
                    
                    News news = new News();
                    news.setTitle(title);
                    news.setSummary(summary);
                    news.setLink(link);
                    // Ensure imageUrl is never null or empty
                    news.setImageUrl(imageUrl != null && !imageUrl.isEmpty() ? imageUrl : PLACEHOLDER_IMAGE);
                    news.setSource("The Hindu");
                    news.setPublishedDate(parseDate(dateStr));
                    news.setLanguage("English");
                    news.setCountry("India");
                    newsList.add(news);
                    
                    if (newsList.size() >= 10) break;
                }
            }
            logger.info("Scraped {} articles from The Hindu", newsList.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape The Hindu: {}", e.getMessage());
        }
        return newsList;
    }

    /**
     * Scrape news from NDTV
     */
    private List<News> scrapeNDTVNews(String keyword) {
        List<News> newsList = new ArrayList<>();
        try {
            String url = "https://www.ndtv.com/search?searchtext=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            logger.debug("Scraping NDTV: {}", url);
            
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements articleElements = doc.select(".src_itm, article, .news_Itm");
            for (Element article : articleElements) {
                String title = article.select("h2, h3, .src_itm-ttl, a").first() != null 
                    ? article.select("h2, h3, .src_itm-ttl, a").first().text() 
                    : "";
                
                // Validate and truncate title
                title = validateAndTruncateTitle(title);
                if (title == null) {
                    continue;
                }
                
                String link = article.select("a").attr("abs:href");
                String imageUrl = extractImageUrl(article, doc);
                String dateStr = article.select("time, .src_itm-stx span").text();
                
                if (!title.isEmpty() && !link.isEmpty()) {
                    // Extract summary from article page
                    String summary = extractSummaryFromArticle(link, keyword);
                    
                    // Skip if summary doesn't contain keyword
                    if (summary.isEmpty()) {
                        logger.debug("Skipping article (no keyword in summary): {}", title);
                        continue;
                    }
                    
                    News news = new News();
                    news.setTitle(title);
                    news.setSummary(summary);
                    news.setLink(link);
                    // Ensure imageUrl is never null or empty
                    news.setImageUrl(imageUrl != null && !imageUrl.isEmpty() ? imageUrl : PLACEHOLDER_IMAGE);
                    news.setSource("NDTV");
                    news.setPublishedDate(parseDate(dateStr));
                    news.setLanguage("English");
                    news.setCountry("India");
                    newsList.add(news);
                    
                    if (newsList.size() >= 10) break;
                }
            }
            logger.info("Scraped {} articles from NDTV", newsList.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape NDTV: {}", e.getMessage());
        }
        return newsList;
    }

    /**
     * Scrape news from BBC
     */
    private List<News> scrapeBBCNews(String keyword) {
        List<News> newsList = new ArrayList<>();
        try {
            String url = "https://www.bbc.com/search?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            logger.debug("Scraping BBC: {}", url);
            
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements articleElements = doc.select("article, .ssrcss-1mrs5ns-Stack");
            for (Element article : articleElements) {
                String title = article.select("h3, .ssrcss-1q0x1qg-Headline").text();
                String link = article.select("a").attr("abs:href");
                String imageUrl = extractImageUrl(article, doc);
                String dateStr = article.select("time").text();
                
                if (!title.isEmpty() && !link.isEmpty()) {
                    // Extract summary from article page
                    String summary = extractSummaryFromArticle(link, keyword);
                    
                    // Skip if summary doesn't contain keyword
                    if (summary.isEmpty()) {
                        logger.debug("Skipping article (no keyword in summary): {}", title);
                        continue;
                    }
                    
                    News news = new News();
                    news.setTitle(title);
                    news.setSummary(summary);
                    news.setLink(link);
                    // Ensure imageUrl is never null or empty
                    news.setImageUrl(imageUrl != null && !imageUrl.isEmpty() ? imageUrl : PLACEHOLDER_IMAGE);
                    news.setSource("BBC");
                    news.setPublishedDate(parseDate(dateStr));
                    news.setLanguage("English");
                    news.setCountry("UK");
                    newsList.add(news);
                    
                    if (newsList.size() >= 10) break;
                }
            }
            logger.info("Scraped {} articles from BBC", newsList.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape BBC: {}", e.getMessage());
        }
        return newsList;
    }

    /**
     * Scrape news from Reuters
     */
    private List<News> scrapeReutersNews(String keyword) {
        List<News> newsList = new ArrayList<>();
        try {
            String url = "https://www.reuters.com/site-search/?query=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            logger.debug("Scraping Reuters: {}", url);
            
            Document doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
            
            Elements articleElements = doc.select("article, .search-result-indiv");
            for (Element article : articleElements) {
                String title = article.select("h3, .search-result-title").text();
                String link = article.select("a").attr("abs:href");
                String imageUrl = extractImageUrl(article, doc);
                String dateStr = article.select("time").text();
                
                if (!title.isEmpty() && !link.isEmpty()) {
                    // Extract summary from article page
                    String summary = extractSummaryFromArticle(link, keyword);
                    
                    // Skip if summary doesn't contain keyword
                    if (summary.isEmpty()) {
                        logger.debug("Skipping article (no keyword in summary): {}", title);
                        continue;
                    }
                    
                    News news = new News();
                    news.setTitle(title);
                    news.setSummary(summary);
                    news.setLink(link);
                    // Ensure imageUrl is never null or empty
                    news.setImageUrl(imageUrl != null && !imageUrl.isEmpty() ? imageUrl : PLACEHOLDER_IMAGE);
                    news.setSource("Reuters");
                    news.setPublishedDate(parseDate(dateStr));
                    news.setLanguage("English");
                    news.setCountry("UK");
                    newsList.add(news);
                    
                    if (newsList.size() >= 10) break;
                }
            }
            logger.info("Scraped {} articles from Reuters", newsList.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape Reuters: {}", e.getMessage());
        }
        return newsList;
    }

    /**
     * Scrape news from Eenadu (Telugu) - using Puppeteer API
     */
    private List<News> scrapeEenaduNews(String keyword) {
        List<News> newsList = new ArrayList<>();
        try {
            // Use the generic /news endpoint with Eenadu URL
            String eenaduUrl = "https://www.eenadu.net/telugu-news/search?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String apiUrl = PUPPETEER_API_BASE_URL + "/news?query=" + URLEncoder.encode(eenaduUrl, StandardCharsets.UTF_8);
            logger.debug("Calling Puppeteer API for Eenadu: {}", apiUrl);
            
            String jsonResponse = callPuppeteerApi(apiUrl);
            newsList = parsePuppeteerNewsResponse(jsonResponse, "Eenadu", "Telugu", keyword);
            
            logger.info("Scraped {} articles from Eenadu via Puppeteer", newsList.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape Eenadu via Puppeteer: {}", e.getMessage());
        }
        return newsList;
    }

    /**
     * Scrape news from Sakshi (Telugu) - using Puppeteer API
     */
    private List<News> scrapeSakshiNews(String keyword) {
        List<News> newsList = new ArrayList<>();
        try {
            // Use the generic /news endpoint with Sakshi URL
            String sakshiUrl = "https://www.sakshi.com/search?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String apiUrl = PUPPETEER_API_BASE_URL + "/news?query=" + URLEncoder.encode(sakshiUrl, StandardCharsets.UTF_8);
            logger.debug("Calling Puppeteer API for Sakshi: {}", apiUrl);
            
            String jsonResponse = callPuppeteerApi(apiUrl);
            newsList = parsePuppeteerNewsResponse(jsonResponse, "Sakshi", "Telugu", keyword);
            
            logger.info("Scraped {} articles from Sakshi via Puppeteer", newsList.size());
        } catch (Exception e) {
            logger.warn("Failed to scrape Sakshi via Puppeteer: {}", e.getMessage());
        }
        return newsList;
    }

    /**
     * Call Puppeteer API and get JSON response
     */
    private String callPuppeteerApi(String apiUrl) throws Exception {
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000); // 30 seconds
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "application/json");
        
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("Puppeteer API returned status code: " + responseCode);
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        
        reader.close();
        connection.disconnect();
        
        return response.toString();
    }

    /**
     * Parse Puppeteer API JSON response for news and convert to News objects
     * Expected JSON format from Puppeteer /scrape/news endpoint:
     * {"items": [{"title": "", "source": "", "date": "", "link": "", "summary": ""}], "page": 1, "returnedCount": 10}
     */
    private List<News> parsePuppeteerNewsResponse(String jsonResponse, String source, String language, String keyword) {
        List<News> newsList = new ArrayList<>();
        
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // Check if response has "items" array
            JsonNode itemsNode = rootNode.has("items") ? rootNode.get("items") : rootNode;
            
            if (!itemsNode.isArray()) {
                logger.warn("Puppeteer API response does not contain items array");
                return newsList;
            }
            
            for (JsonNode articleNode : itemsNode) {
                try {
                    String title = articleNode.has("title") ? articleNode.get("title").asText() : "";
                    String summary = articleNode.has("summary") ? articleNode.get("summary").asText() : "";
                    String articleUrl = articleNode.has("link") ? articleNode.get("link").asText() : "";
                    String dateStr = articleNode.has("date") ? articleNode.get("date").asText() : "";
                    
                    // Skip if title or URL is empty
                    if (title.isEmpty() || articleUrl.isEmpty()) {
                        continue;
                    }
                    
                    // Check if summary or title contains keyword
                    if (!containsKeywordIgnoreCase(summary, keyword) && !containsKeywordIgnoreCase(title, keyword)) {
                        logger.debug("Skipping article (no keyword): {}", title);
                        continue;
                    }
                    
                    // If summary is empty, try to extract from article page
                    if (summary.isEmpty()) {
                        summary = extractSummaryFromArticle(articleUrl, keyword);
                    }
                    
                    // Skip if still no summary
                    if (summary.isEmpty()) {
                        logger.debug("Skipping article (no summary): {}", title);
                        continue;
                    }
                    
                    // Limit summary to 250 characters
                    if (summary.length() > 250) {
                        summary = summary.substring(0, 247) + "...";
                    }
                    
                    News news = new News();
                    news.setTitle(title);
                    news.setSummary(summary);
                    news.setLink(articleUrl);
                    // Use placeholder image for Telugu sites (will be extracted if needed)
                    news.setImageUrl(PLACEHOLDER_IMAGE);
                    news.setSource(source);
                    news.setPublishedDate(parseDate(dateStr));
                    news.setLanguage(language);
                    news.setCountry("India");
                    
                    newsList.add(news);
                    
                    if (newsList.size() >= 10) break;
                    
                } catch (Exception e) {
                    logger.warn("Failed to parse article from Puppeteer response: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to parse Puppeteer API response: {}", e.getMessage());
        }
        
        return newsList;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Validate and truncate title to safe length.
     * Returns null if title is too long (likely a scraping error).
     * 
     * @param title The article title
     * @return Validated title (max 400 chars) or null if invalid
     */
    private String validateAndTruncateTitle(String title) {
        if (title == null || title.isEmpty()) {
            return null;
        }
        
        // If title is suspiciously long (>200 chars), it's likely multiple titles concatenated
        // This indicates a scraping error - skip this article
        if (title.length() > 200) {
            logger.debug("Skipping article with too long title ({}chars) - likely scraping error", title.length());
            return null;
        }
        
        // Truncate to 400 chars max (database limit is 500)
        if (title.length() > 400) {
            return title.substring(0, 397) + "...";
        }
        
        return title;
    }

    /**
     * Extract summary from article page.
     * Fetches the article page and extracts the first paragraph.
     * 
     * Rules:
     * - Extract first paragraph from article page
     * - Limit to 2-3 lines (max 250 characters)
     * - Remove ads, navigation, and unrelated content
     * - Must contain keyword
     * 
     * @param articleUrl The article URL
     * @param keyword The search keyword
     * @return Summary text (max 250 chars) or empty if keyword not found
     */
    private String extractSummaryFromArticle(String articleUrl, String keyword) {
        if (articleUrl == null || articleUrl.isEmpty()) {
            return "";
        }
        
        try {
            // Fetch the article page
            Document articleDoc = Jsoup.connect(articleUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            
            // Try multiple selectors to find article content
            String[] contentSelectors = {
                "article p",           // Standard article paragraphs
                ".article-content p",  // Common article content class
                ".story-content p",    // Story content
                ".post-content p",     // Post content
                ".entry-content p",    // Entry content
                "main p",              // Main content area
                ".content p",          // Generic content
                "p"                    // Fallback to any paragraph
            };
            
            String summary = "";
            for (String selector : contentSelectors) {
                Elements paragraphs = articleDoc.select(selector);
                
                for (Element p : paragraphs) {
                    String text = p.text().trim();
                    
                    // Skip if too short (likely navigation or ads)
                    if (text.length() < 50) {
                        continue;
                    }
                    
                    // Skip common ad/navigation patterns
                    if (isNavigationOrAd(text)) {
                        continue;
                    }
                    
                    // Check if contains keyword
                    if (containsKeywordIgnoreCase(text, keyword)) {
                        summary = text;
                        break;
                    }
                }
                
                if (!summary.isEmpty()) {
                    break;
                }
            }
            
            // Limit to 250 characters (2-3 lines)
            if (summary.length() > 250) {
                summary = summary.substring(0, 247) + "...";
            }
            
            return summary;
            
        } catch (Exception e) {
            logger.debug("Failed to extract summary from article {}: {}", articleUrl, e.getMessage());
            return "";
        }
    }

    /**
     * Check if text is navigation, ad, or unrelated content
     */
    private boolean isNavigationOrAd(String text) {
        String lowerText = text.toLowerCase();
        
        // Common navigation/ad patterns
        String[] patterns = {
            "click here",
            "read more",
            "subscribe",
            "sign up",
            "advertisement",
            "sponsored",
            "follow us",
            "share this",
            "related articles",
            "you may also like",
            "trending now",
            "most popular",
            "cookie policy",
            "privacy policy",
            "terms of service",
            "all rights reserved"
        };
        
        for (String pattern : patterns) {
            if (lowerText.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Check if text contains keyword (case-insensitive)
     */
    private boolean containsKeywordIgnoreCase(String text, String keyword) {
        if (text == null || keyword == null) {
            return false;
        }
        
        String textLower = text.toLowerCase();
        String[] keywords = keyword.toLowerCase().split("\\s+");
        
        for (String kw : keywords) {
            if (kw.length() > 2 && textLower.contains(kw)) {
                return true;
            }
        }
        
        return false;
    }

    private String extractFirstSentences(String text, int count) {
        if (text == null || text.isEmpty()) return "";
        String[] sentences = text.split("\\. ");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Math.min(count, sentences.length); i++) {
            result.append(sentences[i]);
            if (i < sentences.length - 1 && i < count - 1) result.append(". ");
        }
        return result.toString();
    }

    private String limitToLines(String text, int lines) {
        if (text == null || text.isEmpty()) return "";
        String[] parts = text.split("\\. ");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Math.min(lines, parts.length); i++) {
            result.append(parts[i]);
            if (i < parts.length - 1 && i < lines - 1) result.append(". ");
        }
        String limited = result.toString();
        if (limited.length() > 200) {
            limited = limited.substring(0, 197) + "...";
        }
        return limited;
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return LocalDateTime.now();
        }
        
        try {
            // Try common date formats
            DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("dd MMM yyyy"),
                DateTimeFormatter.ofPattern("MMM dd, yyyy"),
                DateTimeFormatter.ISO_DATE_TIME,
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
            };
            
            for (DateTimeFormatter formatter : formatters) {
                try {
                    return LocalDateTime.parse(dateStr, formatter);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to parse date: {}", dateStr);
        }
        
        return LocalDateTime.now();
    }

    // ==================== INNER CLASSES ====================

    private static class ParsedQuery {
        private final Long searchHistoryId;
        private final String keyword;

        private ParsedQuery(Long searchHistoryId, String keyword) {
            this.searchHistoryId = searchHistoryId;
            this.keyword = keyword;
        }

        private static ParsedQuery parse(String query) {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("Query is empty");
            }
            String[] parts = query.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Query must be formatted as <searchHistoryId>|<keyword>");
            }
            Long searchHistoryId = Long.parseLong(parts[0]);
            String keyword = parts[1];
            return new ParsedQuery(searchHistoryId, keyword);
        }
    }
}
