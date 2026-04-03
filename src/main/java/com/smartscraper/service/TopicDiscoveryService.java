package com.smartscraper.service;

import com.smartscraper.service.UrlUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for discovering URLs related to a research topic.
 * Searches multiple sources and ensures domain diversity.
 */
@Service
public class TopicDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(TopicDiscoveryService.class);
    private static final int TIMEOUT_MS = 10000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int MAX_URLS_PER_DOMAIN = 2;
    private static final int TARGET_URL_COUNT = 20;

    /**
     * Discover URLs for a given topic from multiple sources.
     * Ensures domain diversity by limiting URLs per domain.
     * 
     * @param topic Research topic
     * @return List of discovered URLs from diverse domains
     */
    public List<String> discoverUrls(String topic) {
        logger.info("Starting URL discovery for topic: '{}'", topic);

        Set<String> discoveredUrls = new LinkedHashSet<>();
        Map<String, Integer> domainCount = new HashMap<>();

        try {
            // Strategy 1: DuckDuckGo search (no API key required)
            List<String> duckDuckGoUrls = searchDuckDuckGo(topic);
            addUrlsWithDomainLimit(discoveredUrls, duckDuckGoUrls, domainCount);
            logger.info("DuckDuckGo search found {} URLs", duckDuckGoUrls.size());

            // Strategy 2: Wikipedia search
            List<String> wikipediaUrls = searchWikipedia(topic);
            addUrlsWithDomainLimit(discoveredUrls, wikipediaUrls, domainCount);
            logger.info("Wikipedia search found {} URLs", wikipediaUrls.size());

            // Strategy 3: Direct domain searches
            List<String> directUrls = searchDirectDomains(topic);
            addUrlsWithDomainLimit(discoveredUrls, directUrls, domainCount);
            logger.info("Direct domain search found {} URLs", directUrls.size());

            // Strategy 4: Related content discovery
            if (discoveredUrls.size() < TARGET_URL_COUNT) {
                List<String> relatedUrls = discoverRelatedContent(new ArrayList<>(discoveredUrls), topic);
                addUrlsWithDomainLimit(discoveredUrls, relatedUrls, domainCount);
                logger.info("Related content discovery found {} additional URLs", relatedUrls.size());
            }

            // Strategy 5: Fallback - Add sample URLs if discovery failed
            if (discoveredUrls.isEmpty()) {
                logger.warn("No URLs discovered from any source. Adding fallback URLs.");
                List<String> fallbackUrls = createFallbackUrls(topic);
                addUrlsWithDomainLimit(discoveredUrls, fallbackUrls, domainCount);
            }

        } catch (Exception e) {
            logger.error("Error during URL discovery: {}", e.getMessage(), e);
            // Add fallback URLs on error
            List<String> fallbackUrls = createFallbackUrls(topic);
            addUrlsWithDomainLimit(discoveredUrls, fallbackUrls, domainCount);
        }

        List<String> result = new ArrayList<>(discoveredUrls);
        logger.info("URL discovery completed. Total URLs: {}, Unique domains: {}", 
                result.size(), domainCount.size());
        
        return result;
    }

    /**
     * Create fallback URLs when discovery fails.
     * Uses reliable educational and informational sources.
     * 
     * @param topic Research topic
     * @return List of fallback URLs
     */
    private List<String> createFallbackUrls(String topic) {
        List<String> urls = new ArrayList<>();
        String encodedTopic = java.net.URLEncoder.encode(topic, java.nio.charset.StandardCharsets.UTF_8);
        
        // Wikipedia
        urls.add("https://en.wikipedia.org/wiki/" + encodedTopic.replace("+", "_"));
        
        // Britannica
        urls.add("https://www.britannica.com/search?query=" + encodedTopic);
        
        // Khan Academy (for educational topics)
        urls.add("https://www.khanacademy.org/search?page_search_query=" + encodedTopic);
        
        // MIT OpenCourseWare
        urls.add("https://ocw.mit.edu/search/?q=" + encodedTopic);
        
        // Stanford Encyclopedia of Philosophy
        urls.add("https://plato.stanford.edu/search/searcher.py?query=" + encodedTopic);
        
        // National Geographic
        urls.add("https://www.nationalgeographic.com/search?q=" + encodedTopic);
        
        // Smithsonian
        urls.add("https://www.si.edu/search?edan_q=" + encodedTopic);
        
        // BBC
        urls.add("https://www.bbc.com/search?q=" + encodedTopic);
        
        logger.info("Created {} fallback URLs for topic: {}", urls.size(), topic);
        return urls;
    }

    /**
     * Search DuckDuckGo for topic-related URLs.
     * Uses HTML scraping since DuckDuckGo doesn't require API keys.
     * 
     * @param topic Search topic
     * @return List of URLs
     */
    private List<String> searchDuckDuckGo(String topic) {
        List<String> urls = new ArrayList<>();
        
        try {
            String searchUrl = "https://html.duckduckgo.com/html/?q=" + 
                    java.net.URLEncoder.encode(topic, "UTF-8");
            
            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            // Extract result links
            Elements results = doc.select("a.result__a");
            for (Element result : results) {
                String url = result.attr("href");
                if (url != null && !url.isEmpty() && UrlUtils.isValidUrl(url)) {
                    urls.add(url);
                }
            }

            // Also try extracting from uddg parameter (DuckDuckGo redirect)
            Elements links = doc.select("a[href*=uddg]");
            for (Element link : links) {
                String href = link.attr("href");
                if (href.contains("uddg=")) {
                    String extractedUrl = extractUddgUrl(href);
                    if (extractedUrl != null && UrlUtils.isValidUrl(extractedUrl)) {
                        urls.add(extractedUrl);
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("DuckDuckGo search failed: {}", e.getMessage());
        }

        return urls.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Extract URL from DuckDuckGo's uddg parameter.
     * 
     * @param href Link with uddg parameter
     * @return Extracted URL or null
     */
    private String extractUddgUrl(String href) {
        try {
            int uddgIndex = href.indexOf("uddg=");
            if (uddgIndex != -1) {
                String encoded = href.substring(uddgIndex + 5);
                int ampIndex = encoded.indexOf("&");
                if (ampIndex != -1) {
                    encoded = encoded.substring(0, ampIndex);
                }
                return java.net.URLDecoder.decode(encoded, "UTF-8");
            }
        } catch (Exception e) {
            logger.debug("Failed to extract uddg URL: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Search Wikipedia for topic-related articles.
     * 
     * @param topic Search topic
     * @return List of Wikipedia URLs
     */
    private List<String> searchWikipedia(String topic) {
        List<String> urls = new ArrayList<>();

        try {
            // Wikipedia search
            String searchUrl = "https://en.wikipedia.org/w/index.php?search=" + 
                    java.net.URLEncoder.encode(topic, "UTF-8");

            Document doc = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            // Get main article if redirected
            String currentUrl = doc.location();
            if (currentUrl.contains("/wiki/") && !currentUrl.contains("Special:")) {
                urls.add(currentUrl);
            }

            // Get search results
            Elements searchResults = doc.select(".mw-search-result-heading a");
            for (Element result : searchResults) {
                String href = result.attr("abs:href");
                if (href != null && !href.isEmpty() && href.contains("/wiki/")) {
                    urls.add(href);
                }
            }

            // Get related articles from the main page
            if (!urls.isEmpty()) {
                try {
                    Document articleDoc = Jsoup.connect(urls.get(0))
                            .userAgent(USER_AGENT)
                            .timeout(TIMEOUT_MS)
                            .get();

                    Elements relatedLinks = articleDoc.select("#mw-content-text a[href^='/wiki/']");
                    int count = 0;
                    for (Element link : relatedLinks) {
                        if (count >= 5) break;
                        String href = link.attr("abs:href");
                        if (href != null && !href.contains("Special:") && 
                            !href.contains("File:") && !href.contains("Help:")) {
                            urls.add(href);
                            count++;
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Failed to get related Wikipedia articles: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.warn("Wikipedia search failed: {}", e.getMessage());
        }

        return urls.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Search specific domains directly for topic-related content.
     * Includes news sites, educational resources, and general information sites.
     * 
     * @param topic Search topic
     * @return List of URLs from various domains
     */
    private List<String> searchDirectDomains(String topic) {
        List<String> urls = new ArrayList<>();

        // List of diverse domains to search
        String[] domains = {
            "medium.com",
            "reddit.com",
            "stackoverflow.com",
            "github.com",
            "bbc.com",
            "cnn.com",
            "theguardian.com",
            "forbes.com",
            "techcrunch.com",
            "wired.com",
            "nature.com",
            "sciencedaily.com",
            "nationalgeographic.com"
        };

        for (String domain : domains) {
            try {
                // Use DuckDuckGo site search
                String siteSearchUrl = "https://html.duckduckgo.com/html/?q=" + 
                        java.net.URLEncoder.encode(topic + " site:" + domain, "UTF-8");

                Document doc = Jsoup.connect(siteSearchUrl)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .get();

                Elements results = doc.select("a.result__a");
                for (Element result : results) {
                    String url = result.attr("href");
                    if (url != null && url.contains(domain)) {
                        urls.add(url);
                        break; // Only get first result per domain
                    }
                }

                // Small delay to avoid rate limiting
                Thread.sleep(500);

            } catch (Exception e) {
                logger.debug("Failed to search domain {}: {}", domain, e.getMessage());
            }
        }

        return urls.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Discover related content by analyzing existing URLs.
     * Extracts links from discovered pages to find more relevant content.
     * 
     * @param seedUrls Initial URLs to analyze
     * @param topic Original topic for relevance checking
     * @return List of related URLs
     */
    private List<String> discoverRelatedContent(List<String> seedUrls, String topic) {
        List<String> relatedUrls = new ArrayList<>();
        int maxSeedsToAnalyze = Math.min(3, seedUrls.size());

        for (int i = 0; i < maxSeedsToAnalyze; i++) {
            try {
                String seedUrl = seedUrls.get(i);
                Document doc = Jsoup.connect(seedUrl)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .get();

                // Extract external links
                Elements links = doc.select("a[href]");
                int count = 0;
                for (Element link : links) {
                    if (count >= 5) break;

                    String href = link.attr("abs:href");
                    if (href != null && UrlUtils.isValidUrl(href)) {
                        // Check if link text is relevant to topic
                        String linkText = link.text().toLowerCase();
                        String topicLower = topic.toLowerCase();
                        
                        if (linkText.contains(topicLower) || isRelevantLink(linkText, topicLower)) {
                            relatedUrls.add(href);
                            count++;
                        }
                    }
                }

            } catch (Exception e) {
                logger.debug("Failed to discover related content: {}", e.getMessage());
            }
        }

        return relatedUrls.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Check if a link is relevant to the topic.
     * 
     * @param linkText Link text
     * @param topic Topic (lowercase)
     * @return true if relevant
     */
    private boolean isRelevantLink(String linkText, String topic) {
        String[] topicWords = topic.split("\\s+");
        int matches = 0;
        
        for (String word : topicWords) {
            if (word.length() > 3 && linkText.contains(word)) {
                matches++;
            }
        }
        
        return matches >= Math.min(2, topicWords.length);
    }

    /**
     * Add URLs to the discovered set while respecting domain limits.
     * Ensures no single domain dominates the results.
     * 
     * @param discoveredUrls Set of discovered URLs
     * @param newUrls New URLs to add
     * @param domainCount Map tracking URLs per domain
     */
    private void addUrlsWithDomainLimit(
            Set<String> discoveredUrls, 
            List<String> newUrls, 
            Map<String, Integer> domainCount) {

        for (String url : newUrls) {
            if (discoveredUrls.size() >= TARGET_URL_COUNT) {
                break;
            }

            String domain = UrlUtils.extractDomain(url);
            if (domain == null || domain.isEmpty()) {
                continue;
            }

            int currentCount = domainCount.getOrDefault(domain, 0);
            
            // Allow up to MAX_URLS_PER_DOMAIN per domain
            if (currentCount < MAX_URLS_PER_DOMAIN) {
                if (discoveredUrls.add(url)) {
                    domainCount.put(domain, currentCount + 1);
                    logger.debug("Added URL from domain {}: {}", domain, url);
                }
            } else {
                logger.debug("Skipping URL from domain {} (limit reached): {}", domain, url);
            }
        }
    }

    /**
     * Get statistics about discovered URLs.
     * 
     * @param urls List of URLs
     * @return Map with statistics
     */
    public Map<String, Object> getDiscoveryStats(List<String> urls) {
        Map<String, Object> stats = new HashMap<>();
        
        // Count URLs per domain
        Map<String, Long> domainCounts = urls.stream()
                .collect(Collectors.groupingBy(
                        UrlUtils::extractDomain,
                        Collectors.counting()
                ));

        stats.put("totalUrls", urls.size());
        stats.put("uniqueDomains", domainCounts.size());
        stats.put("domainDistribution", domainCounts);
        stats.put("averageUrlsPerDomain", 
                domainCounts.isEmpty() ? 0 : (double) urls.size() / domainCounts.size());

        return stats;
    }

    /**
     * Filter URLs by domain diversity.
     * Ensures no domain has more than the specified limit.
     * 
     * @param urls List of URLs
     * @param maxPerDomain Maximum URLs per domain
     * @return Filtered list
     */
    public List<String> filterByDomainDiversity(List<String> urls, int maxPerDomain) {
        Map<String, Integer> domainCount = new HashMap<>();
        List<String> filtered = new ArrayList<>();

        for (String url : urls) {
            String domain = UrlUtils.extractDomain(url);
            int count = domainCount.getOrDefault(domain, 0);
            
            if (count < maxPerDomain) {
                filtered.add(url);
                domainCount.put(domain, count + 1);
            }
        }

        return filtered;
    }
}
