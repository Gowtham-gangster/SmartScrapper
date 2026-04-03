package com.smartscraper.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.smartscraper.dto.NewsItem;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Service for fetching and parsing RSS feeds.
 */
@Service
public class RssFeedService {

    private static final Logger logger = LoggerFactory.getLogger(RssFeedService.class);

    // Popular RSS feed URLs
    private static final Map<String, String> RSS_FEEDS = new HashMap<>();

    static {
        // Technology News - Working RSS Feeds
        RSS_FEEDS.put("TechCrunch", "https://techcrunch.com/feed/");
        RSS_FEEDS.put("Hacker News", "https://hnrss.org/frontpage");
        RSS_FEEDS.put("Ars Technica", "http://feeds.arstechnica.com/arstechnica/index");
        RSS_FEEDS.put("The Verge", "https://www.theverge.com/rss/index.xml");
        
        // General News - Reliable Feeds
        RSS_FEEDS.put("NPR News", "https://feeds.npr.org/1001/rss.xml");
        RSS_FEEDS.put("BBC World", "http://feeds.bbci.co.uk/news/world/rss.xml");
        RSS_FEEDS.put("Reuters Top News", "https://www.reutersagency.com/feed/?taxonomy=best-topics&post_type=best");
        RSS_FEEDS.put("Associated Press", "https://rsshub.app/apnews/topics/apf-topnews");
        
        // Science & Tech
        RSS_FEEDS.put("Science Daily", "https://www.sciencedaily.com/rss/all.xml");
        RSS_FEEDS.put("MIT News", "https://news.mit.edu/rss/feed");
        RSS_FEEDS.put("NASA", "https://www.nasa.gov/rss/dyn/breaking_news.rss");
        
        // Business
        RSS_FEEDS.put("Forbes", "https://www.forbes.com/real-time/feed2/");
        RSS_FEEDS.put("Business Insider", "https://www.businessinsider.com/rss");
    }

    /**
     * Fetch news items based on search query.
     * Searches across multiple RSS feeds and filters by query.
     * 
     * @param query Search query
     * @return List of news items matching the query
     */
    public List<NewsItem> fetchNews(String query) {
        logger.info("Fetching news for query: {}", query);
        
        List<NewsItem> allNews = new ArrayList<>();
        String lowerQuery = query != null ? query.toLowerCase() : "";

        int successCount = 0;
        int failCount = 0;

        // Fetch from all RSS feeds
        for (Map.Entry<String, String> feed : RSS_FEEDS.entrySet()) {
            try {
                logger.debug("Fetching from {}: {}", feed.getKey(), feed.getValue());
                List<NewsItem> feedNews = fetchFromFeed(feed.getValue(), feed.getKey());
                
                // Filter by query if provided
                if (query == null || query.trim().isEmpty()) {
                    // No filter - add all news
                    allNews.addAll(feedNews);
                } else {
                    // Filter by query
                    for (NewsItem item : feedNews) {
                        if (matchesQuery(item, lowerQuery)) {
                            allNews.add(item);
                        }
                    }
                }
                
                successCount++;
                logger.info("Successfully fetched {} items from {}", feedNews.size(), feed.getKey());
                
            } catch (Exception e) {
                failCount++;
                logger.warn("Failed to fetch from {}: {}", feed.getKey(), e.getMessage());
            }
        }

        logger.info("RSS Feed fetch complete: Success: {}, Failed: {}", successCount, failCount);

        // If no news found, add sample news items
        if (allNews.isEmpty()) {
            logger.warn("No news items fetched from any source. Adding sample items.");
            allNews.addAll(createSampleNews(query));
        }

        // Sort by published date (newest first)
        allNews.sort((a, b) -> {
            if (a.getPublishedDate() == null) return 1;
            if (b.getPublishedDate() == null) return -1;
            return b.getPublishedDate().compareTo(a.getPublishedDate());
        });

        // Limit to 50 items
        if (allNews.size() > 50) {
            allNews = allNews.subList(0, 50);
        }

        logger.info("Returning {} news items for query: {}", allNews.size(), query);
        return allNews;
    }

    /**
     * Create sample news items when real feeds fail.
     * 
     * @param query Search query
     * @return List of sample news items
     */
    private List<NewsItem> createSampleNews(String query) {
        List<NewsItem> samples = new ArrayList<>();
        
        NewsItem item1 = new NewsItem();
        item1.setTitle("Sample News: " + (query != null ? query : "Latest Updates"));
        item1.setSummary("This is a sample news item. Real-time news fetching from RSS feeds may be temporarily unavailable. Please check your internet connection or try again later.");
        item1.setLink("https://example.com");
        item1.setSource("Sample Source");
        item1.setPublishedDate(LocalDateTime.now());
        samples.add(item1);
        
        NewsItem item2 = new NewsItem();
        item2.setTitle("News Aggregation Service");
        item2.setSummary("SmartScraper aggregates news from multiple sources including TechCrunch, BBC, CNN, and more. If you're seeing this message, the RSS feeds may be temporarily inaccessible.");
        item2.setLink("https://example.com");
        item2.setSource("System");
        item2.setPublishedDate(LocalDateTime.now().minusHours(1));
        samples.add(item2);
        
        return samples;
    }

    /**
     * Fetch news items from a specific RSS feed.
     * 
     * @param feedUrl RSS feed URL
     * @param sourceName Source name
     * @return List of news items
     * @throws Exception if feed cannot be fetched
     */
    private List<NewsItem> fetchFromFeed(String feedUrl, String sourceName) throws Exception {
        List<NewsItem> newsItems = new ArrayList<>();

        try {
            // Set connection timeout and user agent
            URL url = new URL(feedUrl);
            XmlReader reader = new XmlReader(url);
            
            SyndFeed feed = new SyndFeedInput().build(reader);
            
            List<SyndEntry> entries = feed.getEntries();
            logger.debug("Feed {} has {} entries", sourceName, entries.size());
            
            for (SyndEntry entry : entries) {
                try {
                    NewsItem item = new NewsItem();
                    
                    // Title
                    item.setTitle(entry.getTitle() != null ? entry.getTitle() : "No Title");
                    
                    // Summary (clean HTML tags)
                    String description = "";
                    if (entry.getDescription() != null && entry.getDescription().getValue() != null) {
                        description = entry.getDescription().getValue();
                    } else if (entry.getContents() != null && !entry.getContents().isEmpty()) {
                        description = entry.getContents().get(0).getValue();
                    }
                    item.setSummary(cleanHtml(description));
                    
                    // Link
                    item.setLink(entry.getLink() != null ? entry.getLink() : entry.getUri());
                    
                    // Source
                    item.setSource(sourceName);
                    
                    // Published Date
                    if (entry.getPublishedDate() != null) {
                        item.setPublishedDate(
                            LocalDateTime.ofInstant(
                                entry.getPublishedDate().toInstant(),
                                ZoneId.systemDefault()
                            )
                        );
                    } else if (entry.getUpdatedDate() != null) {
                        item.setPublishedDate(
                            LocalDateTime.ofInstant(
                                entry.getUpdatedDate().toInstant(),
                                ZoneId.systemDefault()
                            )
                        );
                    } else {
                        item.setPublishedDate(LocalDateTime.now());
                    }
                    
                    newsItems.add(item);
                } catch (Exception e) {
                    logger.debug("Error parsing entry from {}: {}", sourceName, e.getMessage());
                }
            }
            
            reader.close();
        } catch (Exception e) {
            logger.warn("Error fetching feed from {}: {}", sourceName, e.getMessage());
            throw e;
        }

        logger.info("Successfully fetched {} items from {}", newsItems.size(), sourceName);
        return newsItems;
    }

    /**
     * Check if news item matches the search query.
     * 
     * @param item News item
     * @param query Search query (lowercase)
     * @return true if matches, false otherwise
     */
    private boolean matchesQuery(NewsItem item, String query) {
        if (query == null || query.trim().isEmpty()) {
            return true; // No filter, return all
        }

        String lowerTitle = item.getTitle().toLowerCase();
        String lowerSummary = item.getSummary().toLowerCase();

        // Check if query words appear in title or summary
        String[] queryWords = query.split("\\s+");
        for (String word : queryWords) {
            if (lowerTitle.contains(word) || lowerSummary.contains(word)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Clean HTML tags from text.
     * 
     * @param html HTML text
     * @return Plain text
     */
    private String cleanHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        
        // Use Jsoup to parse and extract text
        String text = Jsoup.parse(html).text();
        
        // Limit summary length
        if (text.length() > 300) {
            text = text.substring(0, 297) + "...";
        }
        
        return text;
    }

    /**
     * Get list of available RSS feed sources.
     * 
     * @return Map of source names to feed URLs
     */
    public Map<String, String> getAvailableFeeds() {
        return new HashMap<>(RSS_FEEDS);
    }
}
